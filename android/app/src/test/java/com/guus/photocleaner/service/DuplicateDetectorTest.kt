package com.guus.photocleaner.service

import com.guus.photocleaner.asset
import com.guus.photocleaner.model.DuplicateGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun sameSecondAndDimensions_formOneGroup() {
        val a = asset("a", capturedAtMillis = 1_600_000_000_100L)
        val b = asset("b", capturedAtMillis = 1_600_000_000_900L)

        val groups = DuplicateDetector.findDuplicates(listOf(a, b))

        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups[0].all.map { it.id }.toSet())
    }

    @Test
    fun differentSeconds_areNotGrouped() {
        val a = asset("a", capturedAtMillis = 1_600_000_000_900L)
        val b = asset("b", capturedAtMillis = 1_600_000_001_000L)

        assertTrue(DuplicateDetector.findDuplicates(listOf(a, b)).isEmpty())
    }

    @Test
    fun differentDimensions_areNotGrouped() {
        val a = asset("a", width = 4000, height = 3000)
        val b = asset("b", width = 3000, height = 4000)

        assertTrue(DuplicateDetector.findDuplicates(listOf(a, b)).isEmpty())
    }

    @Test
    fun unknownDimensions_areSplitByByteSize() {
        // NAS items (specs/android-nas.md AC 17): no pixel size, so the file size decides.
        val a = asset("a", width = 0, height = 0, bytes = 500)
        val b = asset("b", width = 0, height = 0, bytes = 500)
        val c = asset("c", width = 0, height = 0, bytes = 501)

        val groups = DuplicateDetector.findDuplicates(listOf(a, b, c))

        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups[0].all.map { it.id }.toSet())
    }

    @Test
    fun itemsWithoutCaptureDate_areNeverGrouped() {
        val a = asset("a", capturedAtMillis = null)
        val b = asset("b", capturedAtMillis = null)

        assertTrue(DuplicateDetector.findDuplicates(listOf(a, b)).isEmpty())
    }

    @Test
    fun equalPixels_keepsLargestFile() {
        val small = asset("small", bytes = 500_000)
        val large = asset("large", bytes = 2_000_000)

        val group = DuplicateDetector.findDuplicates(listOf(small, large)).single()

        assertEquals("large", group.keep.id)
        assertEquals(listOf("small"), group.duplicates.map { it.id })
        assertEquals(500_000L, group.reclaimableBytes)
    }

    @Test
    fun groupMake_keepsMostPixelsBeforeFileSize() {
        val bigFile = asset("bigFile", width = 1000, height = 1000, bytes = 9_000_000)
        val morePixels = asset("morePixels", width = 2000, height = 1000, bytes = 1_000)

        val group = DuplicateGroup.make(listOf(bigFile, morePixels))

        assertEquals("morePixels", group.keep.id)
        assertEquals(2, group.count)
    }

    @Test
    fun groups_areSortedByReclaimableBytesDescending() {
        val t1 = 1_600_000_000_000L
        val t2 = 1_700_000_000_000L
        val smallGroup = listOf(asset("s1", t1, bytes = 10), asset("s2", t1, bytes = 20))
        val bigGroup = listOf(asset("b1", t2, bytes = 1_000), asset("b2", t2, bytes = 2_000))

        val groups = DuplicateDetector.findDuplicates(smallGroup + bigGroup)

        assertEquals(listOf("b2", "s2"), groups.map { it.keep.id })
    }

    @Test
    fun singleItems_produceNoGroups() {
        val groups = DuplicateDetector.findDuplicates(
            listOf(asset("a", 1_000_000L), asset("b", 2_000_000L)),
        )

        assertTrue(groups.isEmpty())
    }
}
