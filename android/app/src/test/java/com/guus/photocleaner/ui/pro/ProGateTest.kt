package com.guus.photocleaner.ui.pro

import com.guus.photocleaner.service.ProState
import com.guus.photocleaner.ui.MediaSettings
import com.guus.photocleaner.ui.media.Browse
import com.guus.photocleaner.ui.media.CategoryFilter
import com.guus.photocleaner.ui.media.MediaTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Free vs Pro controls on the Media tab (specs/pro-unlock.md AC 1–7, 15, 17). */
class ProGateTest {

    @Test
    fun free_allowsPhotosAndDuplicatesButNotVideos() {
        assertTrue(ProGate.allowsTab(ProState.FREE, MediaTab.PHOTOS))
        assertTrue(ProGate.allowsTab(ProState.FREE, MediaTab.DUPLICATES))
        assertFalse(ProGate.allowsTab(ProState.FREE, MediaTab.VIDEOS))
    }

    @Test
    fun free_allowsOnlyDayBrowsing() {
        assertTrue(ProGate.allowsBrowse(ProState.FREE, Browse.DAY))
        listOf(Browse.RANDOM, Browse.MONTH, Browse.YEAR).forEach {
            assertFalse(it.name, ProGate.allowsBrowse(ProState.FREE, it))
        }
    }

    @Test
    fun free_locksPeriodNavigationGridAndDuplicateDiscard() {
        assertFalse(ProGate.allowsPeriodNavigation(ProState.FREE))
        assertFalse(ProGate.allowsGrid(ProState.FREE))
        assertFalse(ProGate.allowsDuplicateDiscard(ProState.FREE))
    }

    @Test
    fun pending_isStillLocked() {
        assertFalse(ProGate.allowsTab(ProState.PENDING, MediaTab.VIDEOS))
        assertFalse(ProGate.allowsBrowse(ProState.PENDING, Browse.MONTH))
        assertFalse(ProGate.allowsGrid(ProState.PENDING))
    }

    @Test
    fun pro_allowsEverything() {
        MediaTab.entries.forEach { assertTrue(ProGate.allowsTab(ProState.PRO, it)) }
        Browse.entries.forEach { assertTrue(ProGate.allowsBrowse(ProState.PRO, it)) }
        assertTrue(ProGate.allowsPeriodNavigation(ProState.PRO))
        assertTrue(ProGate.allowsGrid(ProState.PRO))
        assertTrue(ProGate.allowsDuplicateDiscard(ProState.PRO))
    }

    @Test
    fun free_effectiveSettingsFallBackToOnThisDayPhotos() {
        val saved = MediaSettings(tab = MediaTab.VIDEOS, browse = Browse.YEAR, category = CategoryFilter.FAVORITES, oldestFirst = true)
        val effective = ProGate.effectiveSettings(ProState.FREE, saved)
        assertEquals(MediaSettings(MediaTab.PHOTOS, Browse.DAY, CategoryFilter.FAVORITES, oldestFirst = true), effective)
    }

    @Test
    fun free_keepsDuplicatesTab() {
        val saved = MediaSettings(tab = MediaTab.DUPLICATES, browse = Browse.MONTH)
        assertEquals(MediaTab.DUPLICATES, ProGate.effectiveSettings(ProState.FREE, saved).tab)
    }

    @Test
    fun pro_effectiveSettingsUnchanged() {
        val saved = MediaSettings(tab = MediaTab.VIDEOS, browse = Browse.RANDOM)
        assertEquals(saved, ProGate.effectiveSettings(ProState.PRO, saved))
    }
}
