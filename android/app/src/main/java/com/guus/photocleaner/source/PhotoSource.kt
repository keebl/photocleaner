package com.guus.photocleaner.source

import android.app.PendingIntent
import android.net.Uri
import com.guus.photocleaner.model.PhotoAsset
import kotlinx.coroutines.flow.Flow

/**
 * Access to a photo collection, independent of where the photos live (ADR-0002). Port of
 * iOS `Sources/PhotoSource.swift`; implementations: [MediaStoreSource] (the phone library)
 * and `NasSource` (a NAS folder over SMB, `specs/android-nas.md`).
 *
 * Differences from iOS: thumbnails are loaded by the image loader from [imageModel] instead
 * of by the source; file sizes come with every query, so there is no separate byte-size
 * call; deleting needs a system confirmation dialog, so the source returns the request
 * for the UI to launch; sharing and video playback use [contentUri].
 */
interface PhotoSource {

    val kind: SourceKind get() = SourceKind.DEVICE

    /**
     * How items leave the device after the in-app trash: through Android's system trash, or
     * deleted permanently after the app's own confirmation (NAS: no system trash, ADR-0003).
     */
    val deletion: Deletion get() = Deletion.SYSTEM_TRASH

    /**
     * Whether a stored trash or keep id belongs to this source. Trash and keep lists hold ids
     * of every source; each source shows, deletes and reconciles only its own (spec AC 21).
     */
    fun owns(id: String): Boolean

    /** Emits when the underlying library changed (bursts may arrive; debounce in the UI). */
    fun changes(): Flow<Unit>

    /** All photos and videos, newest first. May be cached until [invalidateCache]. */
    suspend fun fetchAll(): List<PhotoAsset>

    /** Only the items with these ids (e.g. for the trash); missing ids are left out. */
    suspend fun assets(ids: Collection<String>): List<PhotoAsset>

    /** Items taken on [month]/[day] in any year, newest first. */
    suspend fun fetchOnThisDay(month: Int, day: Int): List<PhotoAsset>

    /** Drops the in-memory list after changes or deletions. */
    fun invalidateCache()

    /** Writes in-memory caches (such as computed hashes) to disk. */
    fun flushCaches()

    /** Data the image loader can render for this item (a content URI for MediaStore). */
    fun imageModel(asset: PhotoAsset): Any

    /**
     * Content URI to share or play this item, readable by other apps once granted; null when
     * the source cannot provide one (test fake), in which case share and video play are hidden.
     */
    fun contentUri(asset: PhotoAsset): Uri?

    /**
     * URI to share or play this item once it is available on the device; for a NAS item this
     * downloads a temporary local copy, reporting progress from 0 to 1. Null when unavailable.
     */
    suspend fun localUri(asset: PhotoAsset, onProgress: (Float) -> Unit = {}): Uri? = contentUri(asset)

    /** Perceptual hash (dHash) for finding similar photos; null for videos or unreadable images. */
    suspend fun perceptualHash(asset: PhotoAsset): Long?

    /**
     * Request to move these items to the system trash. The UI launches it so Android can ask
     * for confirmation. Null means no confirmation is needed: none of the items still exist,
     * or the source removed them directly (test fake).
     */
    suspend fun createTrashRequest(assets: List<PhotoAsset>): PendingIntent?

    /** Deletes the items for good; only for [Deletion.PERMANENT] sources. */
    suspend fun deletePermanently(assets: List<PhotoAsset>): Unit =
        throw UnsupportedOperationException("This source deletes through the system trash")
}

enum class SourceKind { DEVICE, NAS }

enum class Deletion { SYSTEM_TRASH, PERMANENT }
