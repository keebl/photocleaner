package com.guus.photocleaner.model

/** Port of iOS `Models/PhotoAsset.swift`. */
enum class MediaKind { PHOTO, VIDEO }

/**
 * Category flags used by the category filter. Android has no selfie flag, so unlike iOS
 * there is no SELFIE category (decided in `specs/android-app.md`).
 */
enum class PhotoCategory { SCREENSHOT, FAVORITE, PANORAMA }

/**
 * Source-independent representation of one photo or video (ADR-0002), decoupled from
 * MediaStore so the UI and cleanup logic do not depend on the concrete source.
 */
data class PhotoAsset(
    /** Stable identifier within the source (MediaStore: the row id as a string). */
    val id: String,
    val kind: MediaKind = MediaKind.PHOTO,
    /** Capture time in epoch milliseconds; null when unknown. */
    val capturedAtMillis: Long?,
    /** When the item was added to the library; the browsing fallback without a capture time. */
    val addedAtMillis: Long? = null,
    /** Last modification in epoch milliseconds; invalidates cached hashes after edits. */
    val modifiedAtMillis: Long?,
    val pixelWidth: Int,
    val pixelHeight: Int,
    /** File size in bytes; 0 when (not yet) known. */
    val byteSize: Long,
    val filename: String?,
    /** Folder (bucket) that holds the file, to show which copy is being discarded. */
    val folder: String? = null,
    val categories: Set<PhotoCategory> = emptySet(),
) {
    val pixelCount: Long get() = pixelWidth.toLong() * pixelHeight
    val megapixels: Double get() = pixelCount / 1_000_000.0
    val isVideo: Boolean get() = kind == MediaKind.VIDEO

    /**
     * Date used for browsing (on this day, day/month/year): capture time, else the date the
     * item was added. Exact-duplicate detection uses [capturedAtMillis] only.
     */
    val browseDateMillis: Long? get() = capturedAtMillis ?: addedAtMillis
}
