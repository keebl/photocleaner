package com.guus.photocleaner.source.nas

import com.guus.photocleaner.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NasPathsTest {

    private val config = NasConfig(host = "192.168.1.10", share = "photo", folder = "/2022/ ", username = "u")

    @Test
    fun ids_roundTrip_andIncludeServerAndShare() {
        val id = NasPaths.id(config, "2022/a.jpg")
        assertEquals("nas://192.168.1.10:445/photo/2022/a.jpg", id)
        assertTrue(NasPaths.isNasId(id))
        assertEquals("2022/a.jpg", NasPaths.path(config, id))
        assertNull(NasPaths.path(config.copy(share = "other"), id))
        assertNull(NasPaths.path(config, "12345"))
        assertFalse(NasPaths.isNasId("12345"))
    }

    @Test
    fun folderAndDisplayName() {
        assertEquals("2022", config.normalizedFolder)
        assertEquals("photo op 192.168.1.10/2022", config.displayName)
        assertEquals("photo op nas.local:1445", NasConfig(host = "nas.local", port = 1445, share = "photo").displayName)
        assertEquals("2022/sub", NasPaths.parentFolder("2022/sub/a.jpg"))
        assertNull(NasPaths.parentFolder("a.jpg"))
        assertEquals("a\\b\\c.jpg", NasPaths.smbPath("/a/b/c.jpg"))
        assertEquals("a/b", NasPaths.join("a", "b"))
        assertEquals("b", NasPaths.join("", "b"))
    }

    @Test
    fun kinds_followTheIosExtensionLists() {
        assertEquals(MediaKind.PHOTO, NasPaths.kind("IMG_1.JPG"))
        assertEquals(MediaKind.PHOTO, NasPaths.kind("x.heic"))
        assertEquals(MediaKind.PHOTO, NasPaths.kind("raw.CR3"))
        assertEquals(MediaKind.VIDEO, NasPaths.kind("clip.mov"))
        assertNull(NasPaths.kind("notes.txt"))
        assertNull(NasPaths.kind("noextension"))
    }

    @Test
    fun hiddenAndSystemFolders_areSkipped() {
        for (name in listOf(".hidden", "@eaDir", "#recycle", "#snapshot", "@tmp", "@sharesnap", "lost+found")) {
            assertTrue(name, NasPaths.isSkippableFolder(name))
        }
        assertFalse(NasPaths.isSkippableFolder("2022"))
    }

    @Test
    fun parseServer() {
        assertEquals("nas.local" to 445, NasPaths.parseServer(" nas.local "))
        assertEquals("10.0.2.2" to 1445, NasPaths.parseServer("10.0.2.2:1445"))
        assertEquals("nas" to 445, NasPaths.parseServer("smb://nas/"))
        assertEquals("fe80::1" to 445, NasPaths.parseServer("fe80::1"))
        assertEquals("fd00::2" to 4450, NasPaths.parseServer("[fd00::2]:4450"))
        assertNull(NasPaths.parseServer(""))
        assertNull(NasPaths.parseServer("nas:99999"))
        assertNull(NasPaths.parseServer("nas:abc"))
        assertNull(NasPaths.parseServer("bad host/share"))
        assertNull(NasPaths.parseServer("[fd00::2]x"))
    }
}
