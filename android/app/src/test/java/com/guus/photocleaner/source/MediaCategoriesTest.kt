package com.guus.photocleaner.source

import com.guus.photocleaner.model.MediaKind
import com.guus.photocleaner.model.PhotoCategory.FAVORITE
import com.guus.photocleaner.model.PhotoCategory.PANORAMA
import com.guus.photocleaner.model.PhotoCategory.SCREENSHOT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCategoriesTest {

    private fun photo(
        path: String? = "DCIM/Camera/",
        favorite: Boolean = false,
        width: Int = 4000,
        height: Int = 3000,
        kind: MediaKind = MediaKind.PHOTO,
    ) = MediaCategories.categorize(kind, path, favorite, width, height)

    @Test
    fun ordinaryCameraPhoto_hasNoCategory() {
        assertTrue(photo().isEmpty())
    }

    @Test
    fun screenshotFolders_areDetected() {
        assertEquals(setOf(SCREENSHOT), photo(path = "Pictures/Screenshots/"))
        assertEquals(setOf(SCREENSHOT), photo(path = "DCIM/Screenshots/"))
        assertEquals(setOf(SCREENSHOT), photo(path = "pictures/screenshots"))
    }

    @Test
    fun screenshotMatch_isOnAWholeFolderName() {
        assertTrue(photo(path = "Pictures/MyScreenshotsBackup/").isEmpty())
        assertTrue(photo(path = null).isEmpty())
    }

    @Test
    fun favoriteFlag_mapsToFavorite() {
        assertEquals(setOf(FAVORITE), photo(favorite = true))
    }

    @Test
    fun aspectRatioOfAtLeastTwoToOne_isPanorama() {
        assertEquals(setOf(PANORAMA), photo(width = 4000, height = 2000))
        assertEquals(setOf(PANORAMA), photo(width = 8000, height = 1500))
        assertEquals(setOf(PANORAMA), photo(width = 1500, height = 8000))
        assertTrue(photo(width = 4000, height = 2001).isEmpty())
    }

    @Test
    fun videosAndUnknownSizes_areNeverPanoramas() {
        assertTrue(photo(width = 4000, height = 1000, kind = MediaKind.VIDEO).isEmpty())
        assertTrue(photo(width = 0, height = 0).isEmpty())
        assertTrue(photo(width = 4000, height = 0).isEmpty())
    }

    @Test
    fun tallPhoneScreenshots_areNotPanoramas() {
        // 20:9 screens exceed 2:1, but a screenshot is never a panorama.
        assertEquals(setOf(SCREENSHOT), photo(path = "Pictures/Screenshots/", width = 1080, height = 2400))
    }

    @Test
    fun categories_combine() {
        assertEquals(
            setOf(SCREENSHOT, FAVORITE),
            photo(path = "Pictures/Screenshots/", favorite = true, width = 3000, height = 1000),
        )
        assertEquals(setOf(FAVORITE, PANORAMA), photo(favorite = true, width = 3000, height = 1000))
    }
}
