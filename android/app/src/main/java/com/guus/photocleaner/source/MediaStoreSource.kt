package com.guus.photocleaner.source

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.util.Size
import com.guus.photocleaner.model.MediaKind
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.CachedHasher
import com.guus.photocleaner.service.HashCache
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** [PhotoSource] over the device library through MediaStore (images and videos). */
class MediaStoreSource(
    context: Context,
    private val hashCache: HashCache,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : PhotoSource {

    private val resolver: ContentResolver = context.contentResolver
    private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val loadLock = Mutex()

    @Volatile
    private var cachedAll: List<PhotoAsset>? = null

    override fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                invalidateCache()
                trySend(Unit)
            }
        }
        for (uri in WATCHED_URIS) resolver.registerContentObserver(uri, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    override suspend fun fetchAll(): List<PhotoAsset> {
        cachedAll?.let { return it }
        return loadLock.withLock {
            cachedAll ?: query(selection = null, args = emptyArray())
                .sortedByDescending { it.browseDateMillis ?: Long.MIN_VALUE }
                .also { cachedAll = it }
        }
    }

    override suspend fun assets(ids: Collection<String>): List<PhotoAsset> {
        val numeric = ids.mapNotNull { it.toLongOrNull() }.distinct()
        return numeric.chunked(MAX_SQL_ARGS).flatMap { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            query("${FileColumns._ID} IN ($placeholders)", chunk.map(Long::toString).toTypedArray())
        }
    }

    override suspend fun fetchOnThisDay(month: Int, day: Int): List<PhotoAsset> =
        OnThisDay.filter(fetchAll(), month, day, zone())

    override fun invalidateCache() {
        cachedAll = null
    }

    override fun flushCaches() = hashCache.flush()

    override fun imageModel(asset: PhotoAsset): Any = contentUri(asset)

    private val hasher = CachedHasher(hashCache) { asset ->
        withContext(Dispatchers.IO) {
            val thumb = try {
                resolver.loadThumbnail(contentUri(asset), Size(HASH_SOURCE_PX, HASH_SOURCE_PX), null)
            } catch (_: IOException) {
                return@withContext null // corrupt or unreadable: skipped by the similar scan
            }
            ImageHashing.dHash(greyGrid(thumb))
        }
    }

    override suspend fun perceptualHash(asset: PhotoAsset): Long? =
        if (asset.isVideo) null else hasher.hash(asset)

    override suspend fun createTrashRequest(assets: List<PhotoAsset>): PendingIntent? {
        val existing = assets(assets.map { it.id })
        if (existing.isEmpty()) return null
        return MediaStore.createTrashRequest(resolver, existing.map(::contentUri), true)
    }

    private suspend fun query(selection: String?, args: Array<String>): List<PhotoAsset> =
        withContext(Dispatchers.IO) {
            val mediaFilter = "${FileColumns.MEDIA_TYPE} IN (?, ?)"
            val fullSelection = if (selection == null) mediaFilter else "$mediaFilter AND ($selection)"
            val fullArgs = arrayOf(
                FileColumns.MEDIA_TYPE_IMAGE.toString(),
                FileColumns.MEDIA_TYPE_VIDEO.toString(),
            ) + args
            resolver.query(filesUri, PROJECTION, fullSelection, fullArgs, null)
                ?.use(::readAll)
                .orEmpty()
        }

    private fun readAll(cursor: Cursor): List<PhotoAsset> {
        val id = cursor.getColumnIndexOrThrow(FileColumns._ID)
        val type = cursor.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
        val taken = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val added = cursor.getColumnIndexOrThrow(FileColumns.DATE_ADDED)
        val modified = cursor.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
        val width = cursor.getColumnIndexOrThrow(FileColumns.WIDTH)
        val height = cursor.getColumnIndexOrThrow(FileColumns.HEIGHT)
        val size = cursor.getColumnIndexOrThrow(FileColumns.SIZE)
        val name = cursor.getColumnIndexOrThrow(FileColumns.DISPLAY_NAME)
        val path = cursor.getColumnIndexOrThrow(FileColumns.RELATIVE_PATH)
        val bucket = cursor.getColumnIndexOrThrow(FileColumns.BUCKET_DISPLAY_NAME)
        val favorite = cursor.getColumnIndexOrThrow(FileColumns.IS_FAVORITE)

        val result = ArrayList<PhotoAsset>(cursor.count)
        while (cursor.moveToNext()) {
            val kind = if (cursor.getInt(type) == FileColumns.MEDIA_TYPE_VIDEO) MediaKind.VIDEO else MediaKind.PHOTO
            val w = cursor.getIntOrZero(width)
            val h = cursor.getIntOrZero(height)
            result += PhotoAsset(
                id = cursor.getLong(id).toString(),
                kind = kind,
                capturedAtMillis = cursor.getPositiveLong(taken),
                addedAtMillis = cursor.getPositiveLong(added)?.times(1000),
                modifiedAtMillis = cursor.getPositiveLong(modified)?.times(1000),
                pixelWidth = w,
                pixelHeight = h,
                byteSize = cursor.getPositiveLong(size) ?: 0L,
                filename = cursor.getStringOrNull(name),
                folder = cursor.getStringOrNull(bucket),
                categories = MediaCategories.categorize(
                    kind = kind,
                    relativePath = cursor.getStringOrNull(path),
                    isFavorite = cursor.getIntOrZero(favorite) == 1,
                    width = w,
                    height = h,
                ),
            )
        }
        return result
    }

    override fun contentUri(asset: PhotoAsset): Uri {
        val collection = if (asset.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(collection, asset.id.toLong())
    }

    /** Scales a thumbnail to the 9×8 dHash grid and converts it to grey values. */
    private fun greyGrid(source: Bitmap): IntArray {
        val software = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        val w = ImageHashing.GRID_WIDTH
        val h = ImageHashing.GRID_HEIGHT
        val small = Bitmap.createScaledBitmap(software, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }
    }

    private companion object {
        const val MAX_SQL_ARGS = 500
        const val HASH_SOURCE_PX = 64

        val WATCHED_URIS: List<Uri> = listOf(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        )

        val PROJECTION = arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            FileColumns.DATE_ADDED,
            FileColumns.DATE_MODIFIED,
            FileColumns.WIDTH,
            FileColumns.HEIGHT,
            FileColumns.SIZE,
            FileColumns.DISPLAY_NAME,
            FileColumns.RELATIVE_PATH,
            FileColumns.BUCKET_DISPLAY_NAME,
            FileColumns.IS_FAVORITE,
        )

        fun Cursor.getPositiveLong(index: Int): Long? =
            if (isNull(index)) null else getLong(index).takeIf { it > 0 }

        fun Cursor.getIntOrZero(index: Int): Int = if (isNull(index)) 0 else getInt(index)

        fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
    }
}
