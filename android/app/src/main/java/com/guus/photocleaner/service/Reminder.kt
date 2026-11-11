package com.guus.photocleaner.service

import java.time.ZonedDateTime

/** App appearance. Port of iOS `AppTheme`; like iOS the default is dark. */
enum class AppTheme { SYSTEM, LIGHT, DARK }

/** Daily "Op deze dag" reminder preferences. Port of iOS `NotificationManager` state. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
)

object ReminderSchedule {
    /** The next moment at [hour]:[minute] local time strictly after [now]. */
    fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val today = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(hour, minute).atZone(now.zone)
    }
}
