package com.guus.photocleaner.source

import com.guus.photocleaner.model.PhotoAsset
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnThisDayTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun item(id: String, captured: String?, added: String? = null) = PhotoAsset(
        id = id,
        capturedAtMillis = captured?.let(::millis),
        addedAtMillis = added?.let(::millis),
        modifiedAtMillis = null,
        pixelWidth = 10,
        pixelHeight = 10,
        byteSize = 1,
        filename = null,
    )

    @Test
    fun sameMonthAndDayInEarlierYears_match() {
        assertTrue(OnThisDay.matches(millis("2019-08-12T09:00:00Z"), 8, 12, utc))
        assertTrue(OnThisDay.matches(millis("2023-08-12T23:59:59Z"), 8, 12, utc))
    }

    @Test
    fun otherDays_doNotMatch() {
        assertFalse(OnThisDay.matches(millis("2019-08-11T23:59:59Z"), 8, 12, utc))
        assertFalse(OnThisDay.matches(millis("2019-08-13T00:00:00Z"), 8, 12, utc))
        assertFalse(OnThisDay.matches(millis("2019-07-12T12:00:00Z"), 8, 12, utc))
    }

    @Test
    fun matching_usesTheDeviceTimeZone() {
        // 23:30 UTC on 1 March is already 2 March in UTC+2.
        val t = millis("2020-03-01T23:30:00Z")
        val plusTwo = ZoneOffset.ofHours(2)

        assertTrue(OnThisDay.matches(t, 3, 2, plusTwo))
        assertFalse(OnThisDay.matches(t, 3, 1, plusTwo))
    }

    @Test
    fun leapDayPhotos_matchOnlyOnLeapDay() {
        // Same rule as iOS: exact month/day, so 29 February only shows on 29 February.
        val leap = millis("2024-02-29T12:00:00Z")

        assertTrue(OnThisDay.matches(leap, 2, 29, utc))
        assertFalse(OnThisDay.matches(leap, 2, 28, utc))
        assertFalse(OnThisDay.matches(leap, 3, 1, utc))
    }

    @Test
    fun filter_usesCaptureDateThenDateAddedAndKeepsOrder() {
        val items = listOf(
            item("captured", captured = "2021-05-04T10:00:00Z"),
            item("other", captured = "2021-05-05T10:00:00Z"),
            item("addedOnly", captured = null, added = "2018-05-04T10:00:00Z"),
            item("capturedWins", captured = "2020-01-01T10:00:00Z", added = "2020-05-04T10:00:00Z"),
            item("noDate", captured = null),
        )

        val result = OnThisDay.filter(items, month = 5, day = 4, zone = utc)

        assertEquals(listOf("captured", "addedOnly"), result.map { it.id })
    }
}
