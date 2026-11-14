package com.guus.photocleaner.source

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.guus.photocleaner.model.MediaKind
import com.guus.photocleaner.model.PhotoCategory
import com.guus.photocleaner.service.HashCache
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Queries the real MediaStore on a device/emulator. The test inserts its own images (an app
 * may add media without extra permissions), so it does not depend on seeded data.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreSourceTest {

    @get:Rule
    val permissions: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val inserted = mutableListOf<Uri>()
    private lateinit var cacheFile: File
    private lateinit var source: MediaStoreSource

    @Before
    fun setUp() {
        cacheFile = File(context.cacheDir, "test-hashes-${System.nanoTime()}.json")
        source = MediaStoreSource(context, HashCache(cacheFile))
    }

    @After
    fun tearDown() {
        inserted.forEach { context.contentResolver.delete(it, null, null) }
        cacheFile.delete()
    }

    @Test
    fun query_returnsInsertedItemsWithMetadataAndCategories() = runBlocking {
        val screenshot = insertImage("Pictures/Screenshots/", "pc_test_shot.png", 120, 260)
        val panorama = insertImage("Pictures/PhotoCleanerTest/", "pc_test_pano.jpg", 600, 200)

        val all = source.fetchAll().associateBy { it.id }

        val shot = all.getValue(screenshot.lastPathSegment!!)
        assertEquals(MediaKind.PHOTO, shot.kind)
        assertEquals(setOf(PhotoCategory.SCREENSHOT), shot.categories)
        assertEquals(120, shot.pixelWidth)
        assertEquals(260, shot.pixelHeight)
        assertTrue(shot.byteSize > 0)
        assertNotNull(shot.browseDateMillis)

        val pano = all.getValue(panorama.lastPathSegment!!)
        assertEquals(setOf(PhotoCategory.PANORAMA), pano.categories)
        assertEquals("PhotoCleanerTest", pano.folder)
    }

    @Test
    fun assetsById_onThisDay_hashAndTrashRequest() = runBlocking {
        val uri = insertImage("Pictures/PhotoCleanerTest/", "pc_test_hash.jpg", 300, 200)
        val id = uri.lastPathSegment!!

        val found = source.assets(listOf(id, "999999999", "not-a-number")).single()
        assertEquals(id, found.id)

        val date = Instant.ofEpochMilli(found.browseDateMillis!!).atZone(ZoneId.systemDefault())
        val onThisDay = source.fetchOnThisDay(date.monthValue, date.dayOfMonth)
        assertTrue(onThisDay.any { it.id == id })

        val hash = source.perceptualHash(found)
        assertNotNull(hash)
        source.flushCaches()
        assertEquals(hash, HashCache(cacheFile).hash(id, found.modifiedAtMillis))

        assertNotNull(source.createTrashRequest(listOf(found)))
        source.invalidateCache()
    }

    /**
     * The cached library must drop items deleted while nobody collects [MediaStoreSource.changes]
     * (e.g. emptied from the Trash tab while the media screen is not shown).
     */
    @Test
    fun fetchAll_dropsItemDeletedWhileNotCollectingChanges() = runBlocking {
        val uri = insertImage("Pictures/PhotoCleanerTest/", "pc_test_gone.jpg", 80, 60)
        val id = uri.lastPathSegment!!
        source.invalidateCache()
        assertTrue(source.fetchAll().any { it.id == id })

        context.contentResolver.delete(uri, null, null)
        inserted -= uri

        val deadline = System.currentTimeMillis() + 5_000
        while (source.fetchAll().any { it.id == id } && System.currentTimeMillis() < deadline) delay(100)
        assertFalse(source.fetchAll().any { it.id == id })
    }

    /** Inserts a gradient image owned by the test app and returns its content URI. */
    private fun insertImage(relativePath: String, name: String, width: Int, height: Int): Uri {
        val png = name.endsWith(".png")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = requireNotNull(resolver.insert(collection, values))
        inserted += uri

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            val v = 255 * x / width
            for (y in 0 until height) bitmap.setPixel(x, y, Color.rgb(v, 255 - v, (x + y) % 256))
        }
        resolver.openOutputStream(uri)!!.use { out ->
            bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, out)
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }
}
