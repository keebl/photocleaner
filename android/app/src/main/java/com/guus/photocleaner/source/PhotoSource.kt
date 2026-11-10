package com.guus.photocleaner.source

import android.app.PendingIntent
import com.guus.photocleaner.model.PhotoAsset
import kotlinx.coroutines.flow.Flow

/**
 * Access to a photo collection, independent of where the photos live (ADR-0002). Port of
 * iOS `Sources/PhotoSource.swift`; v1 has one implementation, [MediaStoreSource].
 *
 * Differences from iOS: thumbnails are loaded by the image loader from [imageModel] instead
 * of by the source; file sizes come with every query, so there is no separate byte-size
 * call; deleting needs a system confirmation dialog, so the source returns the request
 * for the UI to launch. Video and share support are added with the zoom screen.
 */
interface PhotoSource {

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

    /** Perceptual hash (dHash) for finding similar photos; null for videos or unreadable images. */
    suspend fun perceptualHash(asset: PhotoAsset): Long?

    /**
     * Request to move these items to the system trash. The UI launches it so Android can ask
     * for confirmation. Null means no confirmation is needed: none of the items still exist,
     * or the source removed them directly (test fake).
     */
    suspend fun createTrashRequest(assets: List<PhotoAsset>): PendingIntent?
}
