package com.guus.photocleaner.ui.deck

import java.time.Instant
import java.time.ZoneId

/** Badge on a deck card in day/month browsing: how long ago the item was taken. */
sealed interface YearLabel {
    data object ThisYear : YearLabel

    data class YearsAgo(val years: Int, val year: Int) : YearLabel

    /** Taken after the reference year (clock or metadata oddities): show the year only. */
    data class Year(val year: Int) : YearLabel

    companion object {
        /** Label for an item taken at [epochMillis], relative to [referenceYear]. */
        fun of(epochMillis: Long?, referenceYear: Int, zone: ZoneId): YearLabel? {
            if (epochMillis == null) return null
            val year = Instant.ofEpochMilli(epochMillis).atZone(zone).year
            val ago = referenceYear - year
            return when {
                ago < 0 -> Year(year)
                ago == 0 -> ThisYear
                else -> YearsAgo(ago, year)
            }
        }
    }
}
