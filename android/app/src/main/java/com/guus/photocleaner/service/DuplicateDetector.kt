package com.guus.photocleaner.service

import com.guus.photocleaner.model.DuplicateGroup
import com.guus.photocleaner.model.PhotoAsset

/**
 * Finds exact duplicates from metadata only (no pixel comparison). Port of iOS
 * `Services/DuplicateDetector.swift`.
 *
 * Signature = capture time (to the second) + pixel dimensions. This catches the classic
 * cases: the same photo imported several times, or an export next to the original.
 */
object DuplicateDetector {

    fun findDuplicates(assets: List<PhotoAsset>): List<DuplicateGroup> =
        DuplicateGroup.fromBuckets(
            assets.mapNotNull { asset -> signature(asset)?.let { it to asset } }
                .groupBy({ it.first }, { it.second })
                .values,
        )

    /** Items without a capture date are skipped: too risky to flag without a timestamp. */
    private fun signature(asset: PhotoAsset): String? {
        val millis = asset.capturedAtMillis ?: return null
        val second = Math.floorDiv(millis, 1000L)
        return "$second|${asset.pixelWidth}x${asset.pixelHeight}"
    }
}
