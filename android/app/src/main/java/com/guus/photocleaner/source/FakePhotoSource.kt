package com.guus.photocleaner.source

import android.app.PendingIntent
import com.guus.photocleaner.model.PhotoAsset
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * In-memory [PhotoSource] for UI tests and Compose previews. Trashing removes items
 * directly (no system dialog) and records their ids in [trashedIds].
 */
class FakePhotoSource(
    initial: List<PhotoAsset> = emptyList(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val hashes: Map<String, Long> = emptyMap(),
) : PhotoSource {

    private val items = initial.toMutableList()
    private val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Ids removed through [createTrashRequest], in order. */
    val trashedIds = mutableListOf<String>()

    /** Replaces the library contents and signals a change. */
    fun setItems(assets: List<PhotoAsset>) {
        synchronized(items) {
            items.clear()
            items += assets
        }
        changeEvents.tryEmit(Unit)
    }

    override fun changes(): Flow<Unit> = changeEvents

    override suspend fun fetchAll(): List<PhotoAsset> =
        synchronized(items) { items.sortedByDescending { it.browseDateMillis ?: Long.MIN_VALUE } }

    override suspend fun assets(ids: Collection<String>): List<PhotoAsset> {
        val wanted = ids.toSet()
        return synchronized(items) { items.filter { it.id in wanted } }
    }

    override suspend fun fetchOnThisDay(month: Int, day: Int): List<PhotoAsset> =
        OnThisDay.filter(fetchAll(), month, day, zone)

    override fun invalidateCache() = Unit

    override fun flushCaches() = Unit

    override fun imageModel(asset: PhotoAsset): Any = asset.id

    override suspend fun perceptualHash(asset: PhotoAsset): Long? =
        if (asset.isVideo) null else hashes[asset.id]

    override suspend fun createTrashRequest(assets: List<PhotoAsset>): PendingIntent? {
        val ids = assets.map { it.id }.toSet()
        synchronized(items) { items.removeAll { it.id in ids } }
        trashedIds += assets.map { it.id }.distinct()
        changeEvents.tryEmit(Unit)
        return null
    }
}
