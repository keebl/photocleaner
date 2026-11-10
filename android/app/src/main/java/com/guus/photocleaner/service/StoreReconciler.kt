package com.guus.photocleaner.service

import com.guus.photocleaner.source.PhotoSource

/**
 * Drops trash and keep entries whose items no longer exist (deleted outside the app, or on
 * an ejected SD card) — spec AC 16. Only run with full library access: with "selected photos
 * only" every unselected item looks missing and would be dropped.
 */
class StoreReconciler(
    private val source: PhotoSource,
    private val trash: TrashStore,
    private val keep: KeepStore,
) {
    suspend fun dropMissing() {
        val ids = trash.trashedIds + keep.ids.value
        if (ids.isEmpty()) return
        val existing = source.assets(ids).mapTo(HashSet()) { it.id }
        val missing = ids - existing
        if (missing.isEmpty()) return
        trash.forget(missing)
        keep.forget(missing)
    }
}
