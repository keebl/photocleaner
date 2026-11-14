package com.guus.photocleaner.source.nas

import com.guus.photocleaner.model.MediaKind
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.JsonFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * The last complete folder scan on disk, one file per linked folder (spec AC 11), so
 * reopening the NAS source is instant. A partial (cancelled or failed) scan is never saved.
 */
class NasListingCache(private val dir: File) {

    @Serializable
    private data class Entry(
        val id: String,
        val video: Boolean,
        val modified: Long?,
        val size: Long,
        val name: String?,
        val folder: String?,
    )

    private val serializer = ListSerializer(Entry.serializer())

    fun load(config: NasConfig): List<PhotoAsset>? =
        JsonFile(file(config), serializer).read()?.takeIf { it.isNotEmpty() }?.map { it.toAsset() }

    fun save(config: NasConfig, assets: List<PhotoAsset>) {
        if (assets.isEmpty()) return
        JsonFile(file(config), serializer).write(assets.map { it.toEntry() })
    }

    fun clear(config: NasConfig) {
        file(config).delete()
    }

    fun clearAll() {
        dir.listFiles { f -> f.name.startsWith(PREFIX) }?.forEach { it.delete() }
    }

    private fun file(config: NasConfig): File {
        val raw = "${config.host}|${config.port}|${config.share}|${config.normalizedFolder}"
        val safe = raw.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        return File(dir, "$PREFIX$safe.json")
    }

    private fun PhotoAsset.toEntry() = Entry(id, isVideo, modifiedAtMillis, byteSize, filename, folder)

    private fun Entry.toAsset() = nasAsset(id, video, modified, size, name, folder)

    private companion object {
        const val PREFIX = "nasListing-"
    }
}

/**
 * A NAS item: its date is the file's modification time (iOS parity), and the pixel size is
 * unknown without reading the file.
 */
internal fun nasAsset(id: String, video: Boolean, modified: Long?, size: Long, name: String?, folder: String?) =
    PhotoAsset(
        id = id,
        kind = if (video) MediaKind.VIDEO else MediaKind.PHOTO,
        capturedAtMillis = modified,
        modifiedAtMillis = modified,
        pixelWidth = 0,
        pixelHeight = 0,
        byteSize = size,
        filename = name,
        folder = folder,
    )
