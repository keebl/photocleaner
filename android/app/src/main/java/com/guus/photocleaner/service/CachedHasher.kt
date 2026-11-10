package com.guus.photocleaner.service

import com.guus.photocleaner.model.PhotoAsset
import java.util.concurrent.atomic.AtomicInteger

/**
 * Perceptual hashing backed by [HashCache]: a hash is computed only when the cache has no
 * valid entry (spec AC 12), and new hashes are written to disk every [flushEvery] items so a
 * scan interrupted by process death keeps its progress.
 */
class CachedHasher(
    private val cache: HashCache,
    private val flushEvery: Int = DEFAULT_FLUSH_EVERY,
    private val compute: suspend (PhotoAsset) -> Long?,
) {
    private val sinceFlush = AtomicInteger(0)

    /** Cached or freshly computed hash; null for unreadable images (never cached). */
    suspend fun hash(asset: PhotoAsset): Long? {
        cache.hash(asset.id, asset.modifiedAtMillis)?.let { return it }
        val hash = compute(asset) ?: return null
        cache.set(asset.id, hash, asset.modifiedAtMillis)
        if (sinceFlush.incrementAndGet() >= flushEvery) {
            sinceFlush.set(0)
            cache.flush()
        }
        return hash
    }

    fun flush() = cache.flush()

    private companion object {
        const val DEFAULT_FLUSH_EVERY = 500
    }
}
