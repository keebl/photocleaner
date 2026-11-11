package com.guus.photocleaner.model

/** A group of items considered duplicates. Port of iOS `DuplicateGroup`. */
data class DuplicateGroup(
    /** The suggested item to keep (most pixels, then largest file). */
    val keep: PhotoAsset,
    /** The remaining items — candidates to discard. */
    val duplicates: List<PhotoAsset>,
) {
    /** Stable id based on the kept item, so list rows are reused between scans. */
    val id: String get() = keep.id
    val all: List<PhotoAsset> get() = listOf(keep) + duplicates
    val count: Int get() = 1 + duplicates.size

    /** Space reclaimed by discarding the duplicates. */
    val reclaimableBytes: Long get() = duplicates.sumOf { it.byteSize }

    companion object {
        private val keepOrder = compareByDescending<PhotoAsset> { it.pixelCount }
            .thenByDescending { it.byteSize }

        /** Keeps the item with most pixels (tie: largest file); the rest are duplicates. */
        fun make(assets: List<PhotoAsset>): DuplicateGroup {
            require(assets.isNotEmpty()) { "A duplicate group needs at least one item" }
            val sorted = assets.sortedWith(keepOrder)
            return DuplicateGroup(keep = sorted.first(), duplicates = sorted.drop(1))
        }

        /** Builds groups from buckets of 2+ items, largest reclaimable space first. */
        internal fun fromBuckets(buckets: Collection<List<PhotoAsset>>): List<DuplicateGroup> =
            buckets.filter { it.size > 1 }
                .map(::make)
                .sortedByDescending { it.reclaimableBytes }
    }
}
