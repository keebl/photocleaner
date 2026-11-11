package com.guus.photocleaner.ui.media

import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.model.PhotoCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** What the Media tab shows. Port of iOS `MediaTab`. */
enum class MediaTab { PHOTOS, VIDEOS, DUPLICATES }

/** How to browse photos and videos. Port of iOS `Browse`. */
enum class Browse { RANDOM, DAY, MONTH, YEAR }

/** Category filter for photos (spec: no Selfies on Android). Port of iOS `CategoryFilter`. */
enum class CategoryFilter(val category: PhotoCategory?) {
    ALL(null),
    SCREENSHOTS(PhotoCategory.SCREENSHOT),
    FAVORITES(PhotoCategory.FAVORITE),
    PANORAMAS(PhotoCategory.PANORAMA),
}

/** Pure selection logic behind the Media tab, ported from iOS `MediaView`. */
object MediaSelection {

    /**
     * Items for the deck or grid: photos or videos, the category (photos only), then either a
     * shuffle for [Browse.RANDOM] or the period around [date], newest first unless [oldestFirst].
     * Items without any date only appear in random browsing.
     */
    fun select(
        all: List<PhotoAsset>,
        tab: MediaTab,
        browse: Browse,
        category: CategoryFilter,
        date: LocalDate,
        zone: ZoneId,
        oldestFirst: Boolean,
        seed: Long,
    ): List<PhotoAsset> {
        val base = base(all, tab, category)
        if (browse == Browse.RANDOM) return base.shuffled(Random(seed))
        val inPeriod = base.filter { asset ->
            val d = asset.localDate(zone) ?: return@filter false
            when (browse) {
                Browse.DAY -> d.monthValue == date.monthValue && d.dayOfMonth == date.dayOfMonth
                Browse.MONTH -> d.monthValue == date.monthValue
                Browse.YEAR -> d.year == date.year
                Browse.RANDOM -> true
            }
        }
        val sorted = inPeriod.sortedBy { it.browseDateMillis }
        return if (oldestFirst) sorted else sorted.reversed()
    }

    /** Photos or videos (empty for duplicates), with the category applied to photos. */
    fun base(all: List<PhotoAsset>, tab: MediaTab, category: CategoryFilter): List<PhotoAsset> {
        val wanted = category.category
        return when (tab) {
            MediaTab.PHOTOS -> all.filter { !it.isVideo && (wanted == null || wanted in it.categories) }
            MediaTab.VIDEOS -> all.filter { it.isVideo }
            MediaTab.DUPLICATES -> emptyList()
        }
    }

    /**
     * The next period after [date] that still has undecided items in [base], wrapping around
     * the calendar (day, month) or to the earliest year; null when nothing is left.
     */
    fun nextPeriod(
        base: List<PhotoAsset>,
        decidedIds: Set<String>,
        browse: Browse,
        date: LocalDate,
        zone: ZoneId,
    ): LocalDate? {
        val dates = base.filter { it.id !in decidedIds }.mapNotNull { it.localDate(zone) }
        if (dates.isEmpty()) return null
        return when (browse) {
            Browse.RANDOM -> null
            Browse.DAY -> {
                val keys = dates.mapTo(HashSet()) { it.monthValue * 100 + it.dayOfMonth }
                (1..366).asSequence()
                    .map { date.plusDays(it.toLong()) }
                    .firstOrNull { it.monthValue * 100 + it.dayOfMonth in keys }
            }
            Browse.MONTH -> {
                val months = dates.mapTo(HashSet()) { it.monthValue }
                (1..12).asSequence()
                    .map { date.plusMonths(it.toLong()) }
                    .firstOrNull { it.monthValue in months }
            }
            Browse.YEAR -> {
                val years = dates.mapTo(HashSet()) { it.year }
                val target = years.filter { it > date.year }.minOrNull()
                    ?: years.filter { it < date.year }.minOrNull()
                    ?: return null
                date.withYear(target)
            }
        }
    }

    /** Moves [date] by [delta] days, months or years. */
    fun shift(date: LocalDate, browse: Browse, delta: Int): LocalDate = when (browse) {
        Browse.DAY, Browse.RANDOM -> date.plusDays(delta.toLong())
        Browse.MONTH -> date.plusMonths(delta.toLong())
        Browse.YEAR -> date.plusYears(delta.toLong())
    }

    /** Whether [date] is in the same day/month/year as [today] (the "back to now" check). */
    fun isCurrentPeriod(date: LocalDate, browse: Browse, today: LocalDate): Boolean = when (browse) {
        Browse.DAY -> date == today
        Browse.MONTH -> date.year == today.year && date.monthValue == today.monthValue
        Browse.YEAR -> date.year == today.year
        Browse.RANDOM -> true
    }

    private fun PhotoAsset.localDate(zone: ZoneId): LocalDate? =
        browseDateMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
}
