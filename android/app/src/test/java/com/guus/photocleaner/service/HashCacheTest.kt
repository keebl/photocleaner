package com.guus.photocleaner.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HashCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(): File = File(tmp.root, "perceptualHashes.json")

    @Test
    fun storedHash_isReturnedForSameModificationDate() {
        val cache = HashCache(file())

        cache.set("a", hash = -42L, modifiedAtMillis = 1_000_000L)

        assertEquals(-42L, cache.hash("a", modifiedAtMillis = 1_000_000L))
    }

    @Test
    fun unknownId_returnsNull() {
        assertNull(HashCache(file()).hash("nope", modifiedAtMillis = 0L))
    }

    @Test
    fun editedPhoto_invalidatesHash() {
        val cache = HashCache(file())
        cache.set("a", hash = 7L, modifiedAtMillis = 1_000_000L)

        assertEquals(7L, cache.hash("a", modifiedAtMillis = 1_000_999L)) // within 1 s
        assertNull(cache.hash("a", modifiedAtMillis = 1_001_001L))
    }

    @Test
    fun unknownModificationDate_trustsCache() {
        val cache = HashCache(file())
        cache.set("a", hash = 7L, modifiedAtMillis = 1_000_000L)

        assertEquals(7L, cache.hash("a", modifiedAtMillis = null))
    }

    @Test
    fun flush_persistsAndReloads() {
        val cache = HashCache(file())
        cache.set("a", hash = Long.MIN_VALUE, modifiedAtMillis = 5L)
        cache.set("b", hash = Long.MAX_VALUE, modifiedAtMillis = null)

        cache.flush()
        val reopened = HashCache(file())

        assertEquals(Long.MIN_VALUE, reopened.hash("a", 5L))
        assertEquals(Long.MAX_VALUE, reopened.hash("b", null))
    }

    @Test
    fun flush_writesOnlyWhenChanged() {
        val cache = HashCache(file())
        cache.flush()
        assertFalse(file().exists())

        cache.set("a", 1L, 1L)
        cache.flush()
        assertTrue(file().exists())

        file().delete()
        cache.flush()
        assertFalse(file().exists())
    }

    @Test
    fun corruptFile_startsEmpty() {
        file().writeText("garbage")

        assertNull(HashCache(file()).hash("a", null))
    }
}
