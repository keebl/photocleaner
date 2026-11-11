package com.guus.photocleaner.service

import com.guus.photocleaner.model.DuplicateGroup
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.source.ImageHashing

/**
 * Finds photos that *look* alike (edited, recompressed or re-exported versions) by
 * clustering dHash values whose Hamming distance is at most `maxDistance`. Port of iOS
 * `Services/PerceptualDetector.swift`.
 */
object PerceptualDetector {

    /** Threshold used by the iOS app (`DuplicatesView`). */
    const val DEFAULT_MAX_DISTANCE = 8

    fun group(
        items: List<Pair<PhotoAsset, Long>>,
        maxDistance: Int = DEFAULT_MAX_DISTANCE,
    ): List<DuplicateGroup> {
        val n = items.size
        if (n < 2) return emptyList()

        // Union-find clusters similar photos transitively.
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) {
                parent[root] = parent[parent[root]]
                root = parent[root]
            }
            return root
        }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (ImageHashing.hammingDistance(items[i].second, items[j].second) <= maxDistance) {
                    val ri = find(i)
                    val rj = find(j)
                    if (ri != rj) parent[ri] = rj
                }
            }
        }

        val buckets = (0 until n).groupBy(::find) { items[it].first }
        return DuplicateGroup.fromBuckets(buckets.values)
    }
}
