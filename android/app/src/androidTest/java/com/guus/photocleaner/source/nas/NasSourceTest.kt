package com.guus.photocleaner.source.nas

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.model.MediaKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The NAS source against the local Samba test server (`scripts/nas-test-server.sh start`),
 * reached from the emulator at 10.0.2.2:1445. Skipped when no credentials are passed:
 * `./gradlew :app:connectedDebugAndroidTest $(scripts/nas-test-server.sh env)`.
 */
@RunWith(AndroidJUnit4::class)
class NasSourceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val user = args.getString("nasUser").orEmpty()
    private val password = args.getString("nasPassword").orEmpty()
    private val config get() = NasConfig(host = HOST, port = PORT, share = "photos", folder = "", username = user)
    private lateinit var source: NasSource

    @Before
    fun setUp() {
        assumeTrue("NAS test server credentials not passed", user.isNotEmpty())
        source = NasSource(context)
        runBlocking { source.unlink() }
    }

    @After
    fun tearDown() {
        if (::source.isInitialized) runBlocking { source.unlink() }
    }

    @Test
    fun listsDiskShares_withoutAdminShares() = runBlocking {
        val server = NasServer(HOST, PORT, user, password)
        val shares = server.shareNames()
        server.close()
        assertEquals(listOf("other", "photos"), shares)
    }

    @Test
    fun wrongPassword_isAuthFailed_andNothingIsStored() = runBlocking {
        try {
            source.link(config, "wrong-password")
            fail("expected NasException")
        } catch (e: NasException) {
            assertEquals(NasError.AUTH_FAILED, e.error)
        }
        assertEquals(null, source.config.value)
        assertEquals(null, NasConfigStore(context).load())
    }

    @Test
    fun publicAddress_isRefusedBeforeConnecting() = runBlocking {
        try {
            NasServer("8.8.8.8", PORT, user, password).shareNames()
            fail("expected NasException")
        } catch (e: NasException) {
            assertEquals(NasError.NOT_LOCAL, e.error)
        }
    }

    @Test
    fun scan_findsPhotosAndVideosRecursively_skipsSystemFoldersAndOtherFiles() = runBlocking {
        source.link(config, password)
        val items = source.fetchAll()

        assertTrue(items.isNotEmpty())
        assertTrue(items.none { it.filename == "notes.txt" })
        assertTrue(items.none { it.folder?.contains("@eaDir") == true || it.folder?.startsWith(".") == true })
        assertTrue(items.any { it.folder == "Screenshots" })
        assertTrue(items.any { it.kind == MediaKind.VIDEO })
        // Newest first by modification time (the EXIF date the script copied there).
        val dates = items.mapNotNull { it.browseDateMillis }
        assertEquals(dates.sortedDescending(), dates)
        // A reopened source uses the cached listing (AC 11).
        val reopened = NasSource(context)
        assertEquals(items.map { it.id }, reopened.fetchAll().map { it.id })
    }

    @Test
    fun previews_forPhotoAndVideo_areCachedOnDisk() = runBlocking {
        source.link(config, password)
        val items = source.fetchAll()
        val photo = items.first { !it.isVideo }
        val video = items.first { it.isVideo }

        assertNotNull(source.thumbnails.preview(photo, 256))
        assertTrue(source.thumbnails.isCached(photo, 256))
        assertNotNull(source.thumbnails.preview(video, 256))
        assertNotNull(source.perceptualHash(photo))
    }

    /**
     * Regression: after ~15 s without traffic a socket timeout closed the share, and the next
     * call failed or hung. Guards the behaviour (fast and working after idling); the cause, the
     * removed `withSoTimeout`, is also covered by NasServer's reconnect.
     */
    @Test
    fun idleConnection_staysUsable() = runBlocking {
        source.link(config, password)
        val photos = source.fetchAll().filter { !it.isVideo }
        assertNotNull(source.thumbnails.preview(photos[0], 256))

        Thread.sleep(20_000)

        val started = System.currentTimeMillis()
        assertNotNull(source.thumbnails.preview(photos[1], 256))
        assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 5_000)
    }

    @Test
    fun localUri_downloadsACopy() = runBlocking {
        source.link(config, password)
        val photo = source.fetchAll().first { !it.isVideo }
        var last = 0f
        val uri = source.localUri(photo) { last = it }

        assertNotNull(uri)
        assertEquals(1f, last)
        context.contentResolver.openInputStream(uri!!).use { assertTrue(it!!.readBytes().size.toLong() == photo.byteSize) }
    }

    @Test
    fun deletePermanently_removesTheFile() = runBlocking {
        source.link(config, password)
        val victim = source.fetchAll().first { it.filename == "random_11.jpg" }

        source.deletePermanently(listOf(victim))

        assertFalse(source.fetchAll().any { it.id == victim.id })
        assertTrue(source.assets(listOf(victim.id)).isEmpty())
        // Restores the seed for later runs: scripts/nas-test-server.sh start re-seeds.
    }

    private companion object {
        const val HOST = "10.0.2.2"
        const val PORT = 1445
    }
}
