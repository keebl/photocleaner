package com.guus.photocleaner.source

import com.guus.photocleaner.model.PhotoAsset
import java.time.Instant
import java.time.ZoneId

/**
 * "On this day" date matching: same month and day of month in the device time zone, in any
 * year. Like iOS, the match is exact, so photos from 29 February only appear on 29 February.
 */
object OnThisDay {

    fun matches(epochMillis: Long, month: Int, day: Int, zone: ZoneId): Boolean {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return date.monthValue == month && date.dayOfMonth == day
    }

    /** Items whose browse date falls on [month]/[day]; items without any date are skipped. */
    fun filter(assets: List<PhotoAsset>, month: Int, day: Int, zone: ZoneId): List<PhotoAsset> =
        assets.filter { asset ->
            asset.browseDateMillis?.let { matches(it, month, day, zone) } ?: false
        }
}
