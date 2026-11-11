package com.guus.photocleaner.service

import com.guus.photocleaner.model.DuplicateGroup
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.source.PhotoSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/**
 * The two duplicate scans behind the Dubbelen screen. Port of iOS `DuplicatesViewModel`
 * logic: exact duplicates over photos and videos, similar photos (dHash) over photos only.
 * Items already in the trash are left out.
 */
class DuplicateScanner(private val source: PhotoSource) {

    suspend fun exact(excluding: Set<String>): List<DuplicateGroup> =
        DuplicateDetector.findDuplicates(source.fetchAll().filter { it.id !in excluding })

    /**
     * Hashes every photo (cached), reporting progress from 0 to 1, then groups similar ones.
     * Cancellable between items; hashes are flushed to disk when done.
     */
    suspend fun similar(excluding: Set<String>, onProgress: suspend (Float) -> Unit = {}): List<DuplicateGroup> {
        // Video poster-frame hashes are unreliable, so only photos take part.
        val photos = source.fetchAll().filter { !it.isVideo && it.id !in excluding }
        val total = maxOf(photos.size, 1)
        val hashed = ArrayList<Pair<PhotoAsset, Long>>(photos.size)
        photos.forEachIndexed { index, asset ->
            currentCoroutineContext().ensureActive()
            source.perceptualHash(asset)?.let { hashed += asset to it }
            if (index % PROGRESS_EVERY == 0) {
                onProgress(index.toFloat() / total)
                yield()
            }
        }
        onProgress(1f)
        source.flushCaches()
        return PerceptualDetector.group(hashed)
    }

    private companion object {
        const val PROGRESS_EVERY = 25
    }
}
