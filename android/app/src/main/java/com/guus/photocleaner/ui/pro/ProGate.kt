package com.guus.photocleaner.ui.pro

import com.guus.photocleaner.service.ProState
import com.guus.photocleaner.ui.MediaSettings
import com.guus.photocleaner.ui.media.Browse
import com.guus.photocleaner.ui.media.MediaTab

/**
 * Which Media controls a free user may use (specs/pro-unlock.md). Free is "Op deze dag" for
 * today, photos only, as a deck; everything else needs Pro. A pending payment is not Pro.
 */
object ProGate {
    fun isPro(state: ProState): Boolean = state == ProState.PRO

    fun allowsTab(state: ProState, tab: MediaTab): Boolean = isPro(state) || tab != MediaTab.VIDEOS

    fun allowsBrowse(state: ProState, browse: Browse): Boolean = isPro(state) || browse == Browse.DAY

    /** Previous/next period, the date picker and "Volgende" after finishing a set. */
    fun allowsPeriodNavigation(state: ProState): Boolean = isPro(state)

    fun allowsGrid(state: ProState): Boolean = isPro(state)

    /** Discarding from Dubbelen; the scan and its results stay free. */
    fun allowsDuplicateDiscard(state: ProState): Boolean = isPro(state)

    /**
     * The settings the Media tab actually uses: unchanged for Pro; for a free user (or after a
     * refund) videos fall back to photos and every browse mode to "Op deze dag". Category and
     * sort order stay free.
     */
    fun effectiveSettings(state: ProState, settings: MediaSettings): MediaSettings {
        if (isPro(state)) return settings
        return settings.copy(
            tab = if (allowsTab(state, settings.tab)) settings.tab else MediaTab.PHOTOS,
            browse = Browse.DAY,
        )
    }
}
