package com.guus.photocleaner.service

import com.guus.photocleaner.asset
import com.guus.photocleaner.model.MediaKind
import com.guus.photocleaner.source.FakePhotoSource
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateScannerTest {

    private val t = 1_600_000_000_000L

    private val dupA = asset("dupA", t, bytes = 2_000)
    private val dupB = asset("dupB", t, bytes = 1_000)
    private val trashedDup = asset("trashedDup", t, bytes = 500)
    private val videoA = asset("videoA", t + 5_000).copy(kind = MediaKind.VIDEO)
    private val videoB = asset("videoB", t + 5_000).copy(kind = MediaKind.VIDEO)
    private val simA = asset("simA", t + 10_000, width = 100, height = 100)
    private val simB = asset("simB", t + 20_000, width = 200, height = 200)
    private val other = asset("other", t + 30_000)

    private val library = listOf(dupA, dupB, trashedDup, videoA, videoB, simA, simB, other)
    private val hashes = mapOf(
        "simA" to 0L,
        "simB" to 1L,
        "other" to -1L,
        "dupA" to 0x0F0F_0F0F_0F0F_0F0FL,
        "dupB" to 0x0F0F_0F0F_0F0F_0F0FL,
        // Videos would match the photos if they were hashed; they must be skipped.
        "videoA" to 0L,
        "videoB" to 0L,
    )
    private val source = FakePhotoSource(library, ZoneOffset.UTC, hashes)
    private val scanner = DuplicateScanner(source)

    @Test
    fun exact_includesPhotosAndVideos_andSkipsTrashedItems() = runBlocking {
        val groups = scanner.exact(excluding = setOf("trashedDup"))

        assertEquals(
            setOf(setOf("dupA", "dupB"), setOf("videoA", "videoB")),
            groups.map { g -> g.all.map { it.id }.toSet() }.toSet(),
        )
        assertEquals("dupA", groups.first { "dupA" in it.all.map { a -> a.id } }.keep.id)
    }

    @Test
    fun similar_groupsPhotosOnly_andReportsProgressToTheEnd() = runBlocking {
        val progress = mutableListOf<Float>()

        val groups = scanner.similar(excluding = setOf("trashedDup")) { progress += it }

        assertEquals(
            setOf(setOf("simA", "simB"), setOf("dupA", "dupB")),
            groups.map { g -> g.all.map { it.id }.toSet() }.toSet(),
        )
        assertEquals("simB", groups.first { "simA" in it.all.map { a -> a.id } }.keep.id)
        assertEquals(1f, progress.last())
    }

    @Test
    fun similar_canBeCancelled() = runBlocking {
        val job = async { scanner.similar(excluding = emptySet()) { yieldCheck() } }
        job.cancel()

        assertTrue(job.isCancelled)
    }

    private suspend fun yieldCheck() = yield()
}
