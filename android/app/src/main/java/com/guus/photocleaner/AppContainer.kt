package com.guus.photocleaner

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.guus.photocleaner.service.AppPreferences
import com.guus.photocleaner.service.HashCache
import com.guus.photocleaner.service.KeepStore
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.MediaStoreSource
import com.guus.photocleaner.source.PhotoSource
import java.io.File

/** App-wide singletons: one source and one set of stores per process. */
class AppContainer(context: Context) {
    private val files: File = context.filesDir

    val preferences = AppPreferences(context)
    val trash = TrashStore(File(files, "trash.json"))
    val keep = KeepStore(File(files, "kept.json"))
    val hashCache = HashCache(File(files, "perceptualHashes.json"))
    val source: PhotoSource = MediaStoreSource(context, hashCache)
}

class PhotoCleanerApplication : Application(), SingletonImageLoader.Factory {
    val container: AppContainer by lazy { AppContainer(this) }

    /** Coil with video frame decoding, so videos get thumbnails in the deck and grid. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
