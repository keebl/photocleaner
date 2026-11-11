package com.guus.photocleaner.service

import com.guus.photocleaner.asset
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spec AC 12: a re-scan without library changes re-hashes nothing. */
class CachedHasherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun cacheFile() = File(tmp.root, "hashes.json")

    private var computed = 0
    private fun hasher(cache: HashCache = HashCache(cacheFile()), flushEvery: Int = 500) =
        CachedHasher(cache, flushEvery) { asset ->
            computed++
            asset.id.hashCode().toLong()
        }

    private val library = (1..20).map { asset("p$it", capturedAtMillis = it * 1_000L) }

    @Test
    fun secondScanAfterRestart_computesNothing() = runBlocking {
        val first = hasher()
        library.forEach { first.hash(it) }
        first.flush()
        assertEquals(20, computed)

        computed = 0
        val afterRestart = hasher() // new cache instance reads the file
        val hashes = library.map { afterRestart.hash(it) }

        assertEquals(0, computed)
        assertEquals(library.map { it.id.hashCode().toLong() }, hashes)
    }

    @Test
    fun editedPhoto_isHashedAgain() = runBlocking {
        val h = hasher()
        library.forEach { h.hash(it) }
        computed = 0

        val edited = library[3].copy(modifiedAtMillis = library[3].modifiedAtMillis!! + 60_000)
        h.hash(edited)

        assertEquals(1, computed)
    }

    @Test
    fun unreadableImage_isNotCached() = runBlocking {
        var calls = 0
        val h = CachedHasher(HashCache(cacheFile())) { calls++; null }

        assertNull(h.hash(library[0]))
        assertNull(h.hash(library[0]))
        assertEquals(2, calls)
    }

    @Test
    fun partialProgress_isFlushedPeriodically() = runBlocking {
        val h = hasher(flushEvery = 5)
        library.take(12).forEach { h.hash(it) } // no explicit flush: simulates process death

        computed = 0
        val afterRestart = hasher()
        library.take(12).forEach { afterRestart.hash(it) }

        // The first 10 were flushed (at 5 and 10); only the last 2 are hashed again.
        assertEquals(2, computed)
    }
}
