package com.guus.photocleaner.ui.media

import com.guus.photocleaner.model.MediaKind
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.model.PhotoCategory
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSelectionTest {

    private val zone = ZoneOffset.UTC

    private fun item(
        id: String,
        date: String?,
        kind: MediaKind = MediaKind.PHOTO,
        categories: Set<PhotoCategory> = emptySet(),
    ) = PhotoAsset(
        id = id,
        kind = kind,
        capturedAtMillis = date?.let { LocalDate.parse(it).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() },
        modifiedAtMillis = null,
        pixelWidth = 10,
        pixelHeight = 10,
        byteSize = 1,
        filename = null,
        categories = categories,
    )

    private val library = listOf(
        item("a", "2019-11-10"),
        item("b", "2023-11-10", categories = setOf(PhotoCategory.SCREENSHOT)),
        item("c", "2023-11-25"),
        item("d", "2024-02-03", categories = setOf(PhotoCategory.FAVORITE)),
        item("v", "2022-11-10", kind = MediaKind.VIDEO),
        item("noDate", null),
    )

    private fun select(
        kind: MediaTab = MediaTab.PHOTOS,
        browse: Browse = Browse.DAY,
        category: CategoryFilter = CategoryFilter.ALL,
        date: String = "2026-11-10",
        oldestFirst: Boolean = false,
        seed: Long = 1,
    ) = MediaSelection.select(library, kind, browse, category, LocalDate.parse(date), zone, oldestFirst, seed)
        .map { it.id }

    @Test
    fun day_matchesMonthAndDayInAnyYear_newestFirst() {
        assertEquals(listOf("b", "a"), select())
    }

    @Test
    fun oldestFirst_reversesTheOrder() {
        assertEquals(listOf("a", "b"), select(oldestFirst = true))
    }

    @Test
    fun month_matchesTheMonthInAnyYear() {
        assertEquals(listOf("c", "b", "a"), select(browse = Browse.MONTH))
    }

    @Test
    fun year_matchesTheYear() {
        assertEquals(listOf("c", "b"), select(browse = Browse.YEAR, date = "2023-01-01"))
    }

    @Test
    fun videosTab_showsOnlyVideos() {
        assertEquals(listOf("v"), select(kind = MediaTab.VIDEOS))
    }

    @Test
    fun category_filtersPhotos_andAllShowsEverything() {
        assertEquals(listOf("b"), select(category = CategoryFilter.SCREENSHOTS))
        assertEquals(listOf("d"), select(browse = Browse.RANDOM, category = CategoryFilter.FAVORITES))
        assertEquals(emptyList<String>(), select(category = CategoryFilter.PANORAMAS))
    }

    @Test
    fun category_isIgnoredForVideos() {
        assertEquals(listOf("v"), select(kind = MediaTab.VIDEOS, category = CategoryFilter.SCREENSHOTS))
    }

    @Test
    fun random_containsAllPhotosIncludingUndated_andIsStablePerSeed() {
        val first = select(browse = Browse.RANDOM, seed = 7)
        assertEquals(setOf("a", "b", "c", "d", "noDate"), first.toSet())
        assertEquals(first, select(browse = Browse.RANDOM, seed = 7))
    }

    @Test
    fun nextDay_findsTheNextDayWithUndecidedItems_wrappingAroundTheYear() {
        val photos = library.filter { !it.isVideo }
        val next = MediaSelection.nextPeriod(photos, emptySet(), Browse.DAY, LocalDate.parse("2026-11-10"), zone)
        assertEquals(LocalDate.parse("2026-11-25"), next)

        val wrapped = MediaSelection.nextPeriod(photos, emptySet(), Browse.DAY, LocalDate.parse("2026-11-25"), zone)
        assertEquals(LocalDate.parse("2027-02-03"), wrapped)
    }

    @Test
    fun nextPeriod_skipsDecidedItems() {
        val photos = library.filter { !it.isVideo }
        val next = MediaSelection.nextPeriod(photos, setOf("c"), Browse.DAY, LocalDate.parse("2026-11-10"), zone)
        assertEquals(LocalDate.parse("2027-02-03"), next)
    }

    @Test
    fun nextMonth_andNextYear() {
        val photos = library.filter { !it.isVideo }
        assertEquals(
            LocalDate.parse("2027-02-10"),
            MediaSelection.nextPeriod(photos, emptySet(), Browse.MONTH, LocalDate.parse("2026-11-10"), zone),
        )
        assertEquals(
            LocalDate.parse("2024-11-10"),
            MediaSelection.nextPeriod(photos, emptySet(), Browse.YEAR, LocalDate.parse("2023-11-10"), zone),
        )
        // No later year left: wrap to the earliest year.
        assertEquals(
            LocalDate.parse("2019-11-10"),
            MediaSelection.nextPeriod(photos, emptySet(), Browse.YEAR, LocalDate.parse("2024-11-10"), zone),
        )
    }

    @Test
    fun nextPeriod_isNullWhenNothingIsLeftOrInRandom() {
        val photos = library.filter { !it.isVideo }
        assertNull(MediaSelection.nextPeriod(photos, photos.map { it.id }.toSet(), Browse.DAY, LocalDate.parse("2026-11-10"), zone))
        assertNull(MediaSelection.nextPeriod(photos, emptySet(), Browse.RANDOM, LocalDate.parse("2026-11-10"), zone))
    }

    @Test
    fun shift_movesByTheBrowseUnit() {
        val d = LocalDate.parse("2026-01-31")
        assertEquals(LocalDate.parse("2026-01-30"), MediaSelection.shift(d, Browse.DAY, -1))
        assertEquals(LocalDate.parse("2026-02-28"), MediaSelection.shift(d, Browse.MONTH, 1))
        assertEquals(LocalDate.parse("2027-01-31"), MediaSelection.shift(d, Browse.YEAR, 1))
    }

    @Test
    fun isCurrentPeriod_comparesTheBrowseUnit() {
        val today = LocalDate.parse("2026-11-10")
        assertTrue(MediaSelection.isCurrentPeriod(LocalDate.parse("2026-11-10"), Browse.DAY, today))
        assertFalse(MediaSelection.isCurrentPeriod(LocalDate.parse("2026-11-11"), Browse.DAY, today))
        assertTrue(MediaSelection.isCurrentPeriod(LocalDate.parse("2026-11-30"), Browse.MONTH, today))
        assertFalse(MediaSelection.isCurrentPeriod(LocalDate.parse("2025-11-30"), Browse.YEAR, today))
        assertTrue(MediaSelection.isCurrentPeriod(LocalDate.parse("2020-01-01"), Browse.RANDOM, today))
    }
}
