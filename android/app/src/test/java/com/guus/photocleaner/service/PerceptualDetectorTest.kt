package com.guus.photocleaner.service

import com.guus.photocleaner.asset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualDetectorTest {

    /** A hash with the lowest [bits] bits set: distance to 0 equals [bits]. */
    private fun hashWithBits(bits: Int): Long = if (bits == 0) 0L else -1L ushr (64 - bits)

    @Test
    fun distanceAtThreshold_isGrouped() {
        val items = listOf(asset("a") to 0L, asset("b") to hashWithBits(8))

        val groups = PerceptualDetector.group(items, maxDistance = 8)

        assertEquals(1, groups.size)
        assertEquals(setOf("a", "b"), groups[0].all.map { it.id }.toSet())
    }

    @Test
    fun distanceAboveThreshold_isNotGrouped() {
        val items = listOf(asset("a") to 0L, asset("b") to hashWithBits(9))

        assertTrue(PerceptualDetector.group(items, maxDistance = 8).isEmpty())
    }

    @Test
    fun similarity_isTransitive() {
        // a~b (6 bits) and b~c (6 other bits), but a and c differ by 12 bits.
        val a = 0L
        val b = hashWithBits(6)
        val c = hashWithBits(12) xor hashWithBits(6) xor b

        val groups = PerceptualDetector.group(
            listOf(asset("a") to a, asset("b") to b, asset("c") to c),
            maxDistance = 8,
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups[0].count)
    }

    @Test
    fun separateClusters_formSeparateGroups() {
        val far = -1L // 64 bits away from 0
        val items = listOf(
            asset("a1") to 0L,
            asset("a2") to 1L,
            asset("b1") to far,
            asset("b2") to (far xor 1L),
            asset("lonely") to 0x00FF_00FF_00FF_00FFL,
        )

        val groups = PerceptualDetector.group(items, maxDistance = 8)

        assertEquals(2, groups.size)
        assertEquals(
            setOf(setOf("a1", "a2"), setOf("b1", "b2")),
            groups.map { g -> g.all.map { it.id }.toSet() }.toSet(),
        )
    }

    @Test
    fun suggestedKeep_isMostPixels() {
        val items = listOf(
            asset("small", width = 100, height = 100) to 0L,
            asset("big", width = 200, height = 200) to 1L,
        )

        assertEquals("big", PerceptualDetector.group(items, maxDistance = 8).single().keep.id)
    }

    @Test
    fun fewerThanTwoItems_returnsNoGroups() {
        assertTrue(PerceptualDetector.group(emptyList(), maxDistance = 8).isEmpty())
        assertTrue(PerceptualDetector.group(listOf(asset("a") to 0L), maxDistance = 8).isEmpty())
    }

    @Test
    fun defaultMaxDistance_matchesIos() {
        assertEquals(8, PerceptualDetector.DEFAULT_MAX_DISTANCE)
    }
}
