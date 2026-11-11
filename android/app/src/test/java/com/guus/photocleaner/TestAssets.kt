package com.guus.photocleaner

import com.guus.photocleaner.model.PhotoAsset

/** Builds a [PhotoAsset] with sensible defaults for unit tests. */
fun asset(
    id: String,
    capturedAtMillis: Long? = 1_600_000_000_000L,
    width: Int = 4000,
    height: Int = 3000,
    bytes: Long = 1_000_000L,
): PhotoAsset = PhotoAsset(
    id = id,
    capturedAtMillis = capturedAtMillis,
    modifiedAtMillis = capturedAtMillis,
    pixelWidth = width,
    pixelHeight = height,
    byteSize = bytes,
    filename = "$id.jpg",
)
