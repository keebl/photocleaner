package com.guus.photocleaner.ui.deck

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YearLabelTest {

    private val utc = ZoneOffset.UTC
    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun sameYear_isThisYear() {
        assertEquals(YearLabel.ThisYear, YearLabel.of(millis("2026-11-10T12:00:00Z"), 2026, utc))
    }

    @Test
    fun earlierYears_countYearsAgo() {
        assertEquals(YearLabel.YearsAgo(1, 2025), YearLabel.of(millis("2025-11-10T12:00:00Z"), 2026, utc))
        assertEquals(YearLabel.YearsAgo(7, 2019), YearLabel.of(millis("2019-11-10T12:00:00Z"), 2026, utc))
    }

    @Test
    fun laterYear_showsTheYearOnly() {
        assertEquals(YearLabel.Year(2027), YearLabel.of(millis("2027-01-01T12:00:00Z"), 2026, utc))
    }

    @Test
    fun unknownDate_hasNoLabel() {
        assertNull(YearLabel.of(null, 2026, utc))
    }

    @Test
    fun yearUsesTheDeviceTimeZone() {
        // 31 Dec 23:30 UTC is already next year in UTC+2.
        val t = millis("2025-12-31T23:30:00Z")
        assertEquals(YearLabel.ThisYear, YearLabel.of(t, 2026, ZoneOffset.ofHours(2)))
    }
}
