package com.guus.photocleaner.service

import com.guus.photocleaner.service.MediaAccess.DENIED
import com.guus.photocleaner.service.MediaAccess.FULL
import com.guus.photocleaner.service.MediaAccess.PARTIAL
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAccessTest {

    private val images = "android.permission.READ_MEDIA_IMAGES"
    private val video = "android.permission.READ_MEDIA_VIDEO"
    private val selected = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    private val legacy = "android.permission.READ_EXTERNAL_STORAGE"

    private fun resolve(sdk: Int, vararg granted: String) =
        MediaAccess.resolve(sdk) { it in granted }

    @Test
    fun android11And12_useReadExternalStorage() {
        assertEquals(FULL, resolve(30, legacy))
        assertEquals(DENIED, resolve(32))
        assertEquals(listOf(legacy), MediaAccess.permissionsToRequest(31))
    }

    @Test
    fun android13_needsImagesAndVideo() {
        assertEquals(FULL, resolve(33, images, video))
        assertEquals(DENIED, resolve(33, images)) // no video: cannot review the whole library
        assertEquals(DENIED, resolve(33))
        assertEquals(listOf(images, video), MediaAccess.permissionsToRequest(33))
    }

    @Test
    fun android14_selectedPhotosOnlyIsPartial() {
        assertEquals(FULL, resolve(34, images, video, selected))
        assertEquals(PARTIAL, resolve(34, selected))
        assertEquals(DENIED, resolve(36))
        assertEquals(listOf(images, video, selected), MediaAccess.permissionsToRequest(36))
    }

    @Test
    fun onlyFullAndPartial_canReadPhotos() {
        assertEquals(true, FULL.canReadPhotos)
        assertEquals(true, PARTIAL.canReadPhotos)
        assertEquals(false, DENIED.canReadPhotos)
    }
}
