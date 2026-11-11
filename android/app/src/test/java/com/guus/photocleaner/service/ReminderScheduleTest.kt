package com.guus.photocleaner.service

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private fun at(iso: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(iso), amsterdam)

    @Test
    fun beforeTheTime_triggersToday() {
        assertEquals(at("2026-11-10T09:00"), ReminderSchedule.nextTrigger(at("2026-11-10T08:15"), 9, 0))
    }

    @Test
    fun afterOrAtTheTime_triggersTomorrow() {
        assertEquals(at("2026-11-11T09:00"), ReminderSchedule.nextTrigger(at("2026-11-10T09:30"), 9, 0))
        assertEquals(at("2026-11-11T09:00"), ReminderSchedule.nextTrigger(at("2026-11-10T09:00"), 9, 0))
    }

    @Test
    fun endOfMonthAndYear_rollOver() {
        assertEquals(at("2027-01-01T07:45"), ReminderSchedule.nextTrigger(at("2026-12-31T22:00"), 7, 45))
    }

    @Test
    fun daylightSavingChange_keepsTheLocalTime() {
        // Clocks go back on 25 October 2026 in Amsterdam; the reminder stays at 09:00 local.
        val next = ReminderSchedule.nextTrigger(at("2026-10-24T10:00"), 9, 0)
        assertEquals(at("2026-10-25T09:00"), next)
        assertEquals(9, next.hour)
    }

    @Test
    fun defaults_are9am() {
        assertEquals(ReminderSettings(enabled = false, hour = 9, minute = 0), ReminderSettings())
    }
}
