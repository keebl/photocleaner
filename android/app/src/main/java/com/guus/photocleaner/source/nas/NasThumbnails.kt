package com.guus.photocleaner.source.nas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.guus.photocleaner.model.PhotoAsset
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Byte access to a NAS file, provided by [NasSource]. */
interface NasFileReader {
    suspend fun readRange(asset: PhotoAsset, offset: Long, length: Int): ByteArray
    suspend fun readAll(asset: PhotoAsset): ByteArray
}

/**
 * Previews for NAS items, generated once and kept as JPEGs on disk (spec AC 12–14; port of
 * the preview part of iOS `SMBSource.swift`).
 *
 * - Small previews (grid, hashing) first try the EXIF thumbnail in the first 512 KB of the
 *   file, so a grid of photos costs a fraction of the full files.
 * - Larger previews (deck card, zoom) decode the whole file, scaled down.
 * - Video posters read only the byte ranges `MediaMetadataRetriever` asks for.
 *
 * The disk key includes the size bucket and the modification time: an edited file gets a
 * fresh preview.
 */
class NasThumbnails(private val dir: File, private val reader: NasFileReader) {

    private val videoGate = Semaphore(MAX_PARALLEL_VIDEO)

    /** A cached or freshly generated preview file, or null when the item cannot be decoded. */
    suspend fun preview(asset: PhotoAsset, requestedPx: Int): File? {
        val bucket = bucket(requestedPx)
        val file = File(dir, key(asset, bucket) + ".jpg")
        if (file.exists()) return file
        val bitmap = try {
            if (asset.isVideo) videoPoster(asset, bucket) else photoPreview(asset, bucket)
        } catch (_: IOException) {
            null
        } ?: return null
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, "${file.name}.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            tmp.renameTo(file)
        }
        return file
    }

    /** True when a preview for this size is on disk already (no NAS access needed). */
    fun isCached(asset: PhotoAsset, requestedPx: Int): Boolean =
        File(dir, key(asset, bucket(requestedPx)) + ".jpg").exists()

    fun clear() {
        dir.deleteRecursively()
    }

    private suspend fun photoPreview(asset: PhotoAsset, maxPx: Int): Bitmap? {
        val wholeFileFitsPrefix = asset.byteSize in 1..PREFIX_BYTES.toLong()
        val prefix = reader.readRange(asset, 0, PREFIX_BYTES)
        if (wholeFileFitsPrefix || prefix.size < PREFIX_BYTES) return withContext(Dispatchers.Default) { decode(prefix, maxPx) }
        if (maxPx <= SMALL_MAX_PX) {
            withContext(Dispatchers.Default) { embeddedThumbnail(prefix, maxPx) }?.let { return it }
        }
        val all = reader.readAll(asset)
        return withContext(Dispatchers.Default) { decode(all, maxPx) ?: embeddedThumbnail(all, 0) }
    }

    /** The EXIF thumbnail when it is at least half the wanted size (or any size for [minPx] 0). */
    private fun embeddedThumbnail(bytes: ByteArray, maxPx: Int): Bitmap? = try {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        val thumb = exif.thumbnailBitmap
        if (thumb == null || max(thumb.width, thumb.height) * 2 < maxPx) {
            null
        } else {
            rotate(thumb, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        }
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    /** Full decode scaled to [maxPx]; ImageDecoder handles HEIC and the EXIF orientation. */
    private fun decode(bytes: ByteArray, maxPx: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val size = info.size
            val longest = max(size.width, size.height)
            if (longest > maxPx) {
                val scale = maxPx.toFloat() / longest
                decoder.setTargetSize(
                    (size.width * scale).toInt().coerceAtLeast(1),
                    (size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    } catch (_: IOException) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) // formats ImageDecoder rejects
    } catch (_: RuntimeException) {
        null
    }

    private suspend fun videoPoster(asset: PhotoAsset, maxPx: Int): Bitmap? {
        if (asset.byteSize <= 0) return null
        return videoGate.withPermit {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(RangeDataSource(asset))
                    retriever.getScaledFrameAtTime(
                        POSTER_TIME_US,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        maxPx,
                        maxPx,
                    )
                } catch (_: RuntimeException) {
                    null
                } finally {
                    retriever.release()
                }
            }
        }
    }

    /** Serves `MediaMetadataRetriever` the byte ranges it asks for, in 1 MB blocks. */
    private inner class RangeDataSource(private val asset: PhotoAsset) : MediaDataSource() {
        private var blockStart = -1L
        private var block = ByteArray(0)

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= asset.byteSize) return -1
            if (position < blockStart || position >= blockStart + block.size) {
                block = runBlocking { reader.readRange(asset, position, BLOCK_BYTES) }
                blockStart = position
                if (block.isEmpty()) return -1
            }
            val start = (position - blockStart).toInt()
            val n = minOf(size, block.size - start)
            System.arraycopy(block, start, buffer, offset, n)
            return n
        }

        override fun getSize(): Long = asset.byteSize

        override fun close() = Unit
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun key(asset: PhotoAsset, bucket: Int): String = stableHash("${asset.id}|$bucket|${asset.modifiedAtMillis ?: 0}")

    companion object {
        /** iOS reads the first 512 KB for an embedded thumbnail. */
        const val PREFIX_BYTES = 512 * 1024
        const val SMALL_MAX_PX = 512
        private const val BLOCK_BYTES = 1024 * 1024
        private const val JPEG_QUALITY = 85
        private const val MAX_PARALLEL_VIDEO = 2
        private const val POSTER_TIME_US = 1_000_000L

        /** Size buckets, so nearby requested sizes share one cached file. */
        fun bucket(requestedPx: Int): Int = when {
            requestedPx <= 256 -> 256
            requestedPx <= SMALL_MAX_PX -> SMALL_MAX_PX
            requestedPx <= 1024 -> 1024
            else -> 2048
        }

        /** Stable across runs (unlike `hashCode` of a composite), for file names. */
        fun stableHash(s: String): String {
            var h = -3750763034362895579L // FNV-1a 64-bit offset basis
            for (b in s.toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toLong() and 0xFF)
                h *= 1099511628211L
            }
            return java.lang.Long.toHexString(h)
        }
    }
}
