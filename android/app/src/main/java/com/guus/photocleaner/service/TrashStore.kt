package com.guus.photocleaner.service

import com.guus.photocleaner.model.PhotoAsset
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** One item the user discarded that is still within the retention period. */
@Serializable
data class TrashItem(
    /** [PhotoAsset.id]. */
    val id: String,
    val markedAtMillis: Long,
    /** Where it was discarded, e.g. "deck" or "duplicates". */
    val reason: String,
)

/**
 * In-app trash with retention. Port of iOS `Services/TrashStore.swift`: discarded items
 * stay on the device but disappear from review views; only after [RETENTION_DAYS] (or
 * "empty now") are they moved to Android's system trash.
 *
 * Unlike iOS, the lifetime "cleaned" counter is stored in the same JSON file as the items,
 * so both are written atomically together.
 */
class TrashStore(
    file: File,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    @Serializable
    private data class Snapshot(
        val items: List<TrashItem> = emptyList(),
        val totalCleaned: Int = 0,
    )

    private val storage = JsonFile(file, Snapshot.serializer())
    private val loaded = storage.read() ?: Snapshot()

    private val _items = MutableStateFlow(loaded.items)
    val items: StateFlow<List<TrashItem>> = _items.asStateFlow()

    /** Lifetime count of cleaned items (discarded minus restored); survives purging. */
    private val _totalCleaned = MutableStateFlow(loaded.totalCleaned)
    val totalCleaned: StateFlow<Int> = _totalCleaned.asStateFlow()

    fun contains(id: String): Boolean = _items.value.any { it.id == id }

    /** All trashed ids, to filter them out of other views. */
    val trashedIds: Set<String> get() = _items.value.mapTo(HashSet()) { it.id }

    fun expiresAt(item: TrashItem): ZonedDateTime =
        Instant.ofEpochMilli(item.markedAtMillis).atZone(clock.zone).plusDays(RETENTION_DAYS)

    /** Whole days until final removal (0 = expired). */
    fun daysLeft(item: TrashItem): Int {
        val days = ChronoUnit.DAYS.between(ZonedDateTime.now(clock), expiresAt(item))
        return days.coerceAtLeast(0).toInt()
    }

    fun expiredItems(): List<TrashItem> {
        val now = ZonedDateTime.now(clock)
        return _items.value.filter { !expiresAt(it).isAfter(now) }
    }

    fun mark(asset: PhotoAsset, reason: String) = markAll(listOf(asset), reason)

    /** Adds several items in one action (grid review bulk discard). */
    @Synchronized
    fun markAll(assets: List<PhotoAsset>, reason: String) {
        val now = clock.millis()
        val present = trashedIds.toMutableSet()
        val added = assets.filter { present.add(it.id) }
            .map { TrashItem(id = it.id, markedAtMillis = now, reason = reason) }
        if (added.isEmpty()) return
        update(_items.value + added, _totalCleaned.value + added.size)
    }

    @Synchronized
    fun restore(id: String) {
        if (!contains(id)) return
        update(
            _items.value.filterNot { it.id == id },
            (_totalCleaned.value - 1).coerceAtLeast(0),
        )
    }

    /**
     * Drops these ids from the trash list without touching the counter: call after they
     * were moved to the system trash, or when they were deleted outside the app.
     */
    @Synchronized
    fun forget(ids: Collection<String>) {
        val drop = ids.toSet()
        val remaining = _items.value.filterNot { it.id in drop }
        if (remaining.size == _items.value.size) return
        update(remaining, _totalCleaned.value)
    }

    private fun update(items: List<TrashItem>, totalCleaned: Int) {
        storage.write(Snapshot(items, totalCleaned))
        _items.value = items
        _totalCleaned.value = totalCleaned
    }

    companion object {
        const val RETENTION_DAYS = 30L
    }
}
