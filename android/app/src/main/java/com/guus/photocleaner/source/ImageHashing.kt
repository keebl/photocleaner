package com.guus.photocleaner.source

/**
 * Perceptual hash (dHash) on a 9×8 greyscale grid. Port of iOS `Sources/ImageHashing.swift`.
 *
 * Pure Kotlin on purpose: scaling a bitmap down to the grid is the source's job, so this
 * logic is unit-testable without Android types.
 */
object ImageHashing {
    const val GRID_WIDTH = 9
    const val GRID_HEIGHT = 8

    /**
     * Compares every pixel with its right-hand neighbour; bit `row * 8 + col` is set when
     * the left pixel is brighter. [grey] holds 72 values (0–255), row-major, top row first.
     */
    fun dHash(grey: IntArray): Long {
        require(grey.size == GRID_WIDTH * GRID_HEIGHT) {
            "Expected ${GRID_WIDTH * GRID_HEIGHT} grey values, got ${grey.size}"
        }
        var hash = 0L
        var bit = 0
        for (row in 0 until GRID_HEIGHT) {
            for (col in 0 until GRID_WIDTH - 1) {
                val i = row * GRID_WIDTH + col
                if (grey[i] > grey[i + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
