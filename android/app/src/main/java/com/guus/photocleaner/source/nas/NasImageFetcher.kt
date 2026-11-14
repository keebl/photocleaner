package com.guus.photocleaner.source.nas

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import java.io.IOException
import kotlin.math.max
import okio.Path.Companion.toOkioPath

/** Lets Coil show NAS items: it asks [NasThumbnails] for a preview of the requested size. */
class NasImageFetcher(
    private val data: NasImage,
    private val options: Options,
    private val thumbnails: () -> NasThumbnails,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val px = max(
            options.size.width.pxOrElse { DEFAULT_PX },
            options.size.height.pxOrElse { DEFAULT_PX },
        )
        val file = thumbnails().preview(data.asset, px) ?: throw IOException("No preview for this item")
        return SourceFetchResult(
            source = ImageSource(file.toOkioPath(), options.fileSystem),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val thumbnails: () -> NasThumbnails) : Fetcher.Factory<NasImage> {
        override fun create(data: NasImage, options: Options, imageLoader: ImageLoader): Fetcher =
            NasImageFetcher(data, options, thumbnails)
    }

    /** Memory-cache key: the item and its version, not the whole asset. */
    class Key : Keyer<NasImage> {
        override fun key(data: NasImage, options: Options): String =
            "${data.asset.id}|${data.asset.modifiedAtMillis ?: 0}"
    }

    private companion object {
        const val DEFAULT_PX = 1024
    }
}
