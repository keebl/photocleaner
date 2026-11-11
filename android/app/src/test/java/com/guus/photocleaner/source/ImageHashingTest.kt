package com.guus.photocleaner.source

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageHashingTest {

    private val w = ImageHashing.GRID_WIDTH
    private val h = ImageHashing.GRID_HEIGHT

    private fun grid(value: (row: Int, col: Int) -> Int): IntArray =
        IntArray(w * h) { i -> value(i / w, i % w) }

    @Test
    fun gridIs9By8() {
        assertEquals(9, w)
        assertEquals(8, h)
    }

    @Test
    fun brightnessFallingLeftToRight_setsAllBits() {
        val hash = ImageHashing.dHash(grid { _, col -> 255 - col * 20 })

        assertEquals(-1L, hash)
    }

    @Test
    fun brightnessRisingLeftToRight_setsNoBits() {
        assertEquals(0L, ImageHashing.dHash(grid { _, col -> col * 20 }))
    }

    @Test
    fun uniformImage_setsNoBits() {
        assertEquals(0L, ImageHashing.dHash(grid { _, _ -> 128 }))
    }

    @Test
    fun bitOrder_isRowMajorWithEightBitsPerRow() {
        // Only pixel (row 1, col 0) is brighter than its right neighbour -> bit 1*8+0.
        val hash = ImageHashing.dHash(grid { row, col -> if (row == 1 && col == 0) 200 else 0 })

        assertEquals(1L shl 8, hash)
    }

    @Test
    fun lastRowLastComparison_setsBit63() {
        val hash = ImageHashing.dHash(grid { row, col -> if (row == 7 && col == 7) 200 else 0 })

        assertEquals(Long.MIN_VALUE, hash)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongGridSize_isRejected() {
        ImageHashing.dHash(IntArray(64))
    }

    @Test
    fun hammingDistance_countsDifferingBits() {
        assertEquals(0, ImageHashing.hammingDistance(42L, 42L))
        assertEquals(3, ImageHashing.hammingDistance(0b000L, 0b111L))
        assertEquals(64, ImageHashing.hammingDistance(0L, -1L))
    }
}
