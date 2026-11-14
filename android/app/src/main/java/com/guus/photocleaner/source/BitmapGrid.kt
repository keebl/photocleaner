package com.guus.photocleaner.source

import android.graphics.Bitmap
import android.graphics.Color

/** Scales a thumbnail to the 9×8 dHash grid and converts it to grey values. */
internal fun greyGrid(source: Bitmap): IntArray {
    val software = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
    val w = ImageHashing.GRID_WIDTH
    val h = ImageHashing.GRID_HEIGHT
    val small = Bitmap.createScaledBitmap(software, w, h, true)
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    return IntArray(pixels.size) { i ->
        val p = pixels[i]
        (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
    }
}
