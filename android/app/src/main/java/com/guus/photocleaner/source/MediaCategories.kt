package com.guus.photocleaner.source

import com.guus.photocleaner.model.MediaKind
import com.guus.photocleaner.model.PhotoCategory

/**
 * Maps MediaStore metadata to categories (decided in `specs/android-app.md`):
 * screenshots by folder name, favorites by the MediaStore flag, panoramas by an aspect
 * ratio of at least 2:1 (photos only). Screenshots are never panoramas, because 20:9 phone
 * screens already exceed 2:1.
 */
object MediaCategories {

    private const val SCREENSHOT_FOLDER = "screenshots"

    fun categorize(
        kind: MediaKind,
        relativePath: String?,
        isFavorite: Boolean,
        width: Int,
        height: Int,
    ): Set<PhotoCategory> = buildSet {
        val screenshot = isInScreenshotFolder(relativePath)
        if (screenshot) add(PhotoCategory.SCREENSHOT)
        if (isFavorite) add(PhotoCategory.FAVORITE)
        if (kind == MediaKind.PHOTO && !screenshot && isPanorama(width, height)) {
            add(PhotoCategory.PANORAMA)
        }
    }

    private fun isInScreenshotFolder(relativePath: String?): Boolean =
        relativePath?.split('/')?.any { it.equals(SCREENSHOT_FOLDER, ignoreCase = true) } == true

    private fun isPanorama(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val long = maxOf(width, height).toLong()
        val short = minOf(width, height).toLong()
        return long >= 2 * short
    }
}
