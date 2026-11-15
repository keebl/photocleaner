package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.model.MediaKind
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.model.PhotoCategory
import com.guus.photocleaner.service.KeepStore
import com.guus.photocleaner.service.ProState
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.FakePhotoSource
import com.guus.photocleaner.ui.deck.DECK_CARD_TAG
import com.guus.photocleaner.ui.media.Browse
import com.guus.photocleaner.ui.media.MediaTab
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The free tier on the Media tab (specs/pro-unlock.md AC 1–5, 7, 17). Today is fixed at
 * 10 November 2026; today has three photos (across years) and one video.
 */
@RunWith(AndroidJUnit4::class)
class MediaScreenProTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 10)
    private lateinit var dir: File
    private lateinit var trash: TrashStore
    private lateinit var keep: KeepStore
    private var lockedTaps = 0

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun item(
        id: String,
        year: Int,
        day: Int = 10,
        kind: MediaKind = MediaKind.PHOTO,
        categories: Set<PhotoCategory> = emptySet(),
    ) = PhotoAsset(
        id = id,
        kind = kind,
        capturedAtMillis = LocalDate.of(year, 11, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        modifiedAtMillis = null,
        pixelWidth = 400,
        pixelHeight = 300,
        byteSize = 2_000_000,
        filename = "$id.jpg",
        categories = categories,
    )

    private val library = listOf(
        item("p2025", 2025, categories = setOf(PhotoCategory.FAVORITE)),
        item("p2020", 2020),
        item("p2016", 2016),
        item("video", 2024, kind = MediaKind.VIDEO),
        item("otherDay", 2024, day = 11),
    )

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "pro-test-${System.nanoTime()}").apply { mkdirs() }
        trash = TrashStore(File(dir, "trash.json"))
        keep = KeepStore(File(dir, "kept.json"))
        lockedTaps = 0
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun show(pro: ProState = ProState.FREE, settings: MediaSettings = MediaSettings()) {
        val source = FakePhotoSource(library, zone)
        compose.setContent {
            PhotoCleanerTheme(darkTheme = false) {
                MediaScreen(
                    source = source,
                    trash = trash,
                    keep = keep,
                    today = today,
                    zone = zone,
                    swipeHintSeen = true,
                    onSwipeHintSeen = {},
                    initialSettings = settings,
                    pro = pro,
                    onLocked = { lockedTaps++ },
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.media_tab_photos)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun todayTitle(): String =
        today.format(DateTimeFormatter.ofPattern("d MMMM", context.resources.configuration.locales[0]))

    @Test
    fun free_deckShowsTodaysPhotosWithoutVideos() {
        show()
        compose.onNodeWithText(text(R.string.deck_progress, 1, 3)).assertIsDisplayed()
    }

    @Test
    fun free_savedProChoicesFallBackToOnThisDay() {
        show(settings = MediaSettings(tab = MediaTab.VIDEOS, browse = Browse.YEAR))
        compose.onNodeWithText(text(R.string.media_tab_photos)).assertIsDisplayed()
        compose.onNodeWithText(todayTitle()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.deck_progress, 1, 3)).assertIsDisplayed()
    }

    @Test
    fun free_videosTab_opensProAndKeepsPhotos() {
        show()
        compose.onNodeWithText(text(R.string.media_tab_photos)).performClick()
        compose.onNodeWithText(text(R.string.media_tab_videos)).performClick()

        assertEquals(1, lockedTaps)
        compose.onNodeWithText(text(R.string.deck_progress, 1, 3)).assertIsDisplayed()
    }

    @Test
    fun free_otherBrowseModes_openProAndKeepToday() {
        show()
        listOf(R.string.media_browse_random, R.string.media_browse_month, R.string.media_browse_year).forEachIndexed { i, label ->
            compose.onNodeWithText(text(R.string.media_browse_day)).performClick()
            compose.onNodeWithText(text(label)).performClick()
            assertEquals(i + 1, lockedTaps)
        }
        compose.onNodeWithText(text(R.string.media_browse_day)).assertIsDisplayed()
        compose.onNodeWithText(todayTitle()).assertIsDisplayed()
    }

    @Test
    fun free_periodControls_openProAndKeepToday() {
        show()
        compose.onNodeWithContentDescription(text(R.string.media_previous)).performClick()
        compose.onNodeWithContentDescription(text(R.string.media_next)).performClick()
        compose.onNodeWithText(todayTitle()).performClick()

        assertEquals(3, lockedTaps)
        compose.onNodeWithText(todayTitle()).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.media_pick_date)).assertDoesNotExist()
    }

    @Test
    fun free_gridToggle_opensProAndKeepsTheDeck() {
        show()
        compose.onNodeWithContentDescription(text(R.string.media_show_grid)).performClick()

        assertEquals(1, lockedTaps)
        compose.onNodeWithTag(DECK_CARD_TAG).assertIsDisplayed()
    }

    @Test
    fun free_finishedDay_offersProInsteadOfTheNextDay() {
        show()
        repeat(3) {
            compose.onNodeWithTag(DECK_CARD_TAG).performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }

        compose.onNodeWithText(text(R.string.pro_today_done)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.media_next_day, text(R.string.media_noun_photos))).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pro_more_days)).performClick()
        assertEquals(1, lockedTaps)
    }

    @Test
    fun free_categoryAndSortStayFree() {
        show()
        compose.onNodeWithText(text(R.string.media_category_all)).performClick()
        compose.onNodeWithText(text(R.string.media_category_favorites)).performClick()
        compose.onNodeWithText(text(R.string.deck_progress, 1, 1)).assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.media_sort)).performClick()
        compose.onNodeWithText(text(R.string.media_sort_oldest)).performClick()
        assertEquals(0, lockedTaps)
    }

    @Test
    fun free_showsLockBadges() {
        show()
        compose.onAllNodesWithContentDescription(text(R.string.pro_locked)).onFirst().assertIsDisplayed()
    }

    @Test
    fun pro_showsNoLocksAndNavigates() {
        show(pro = ProState.PRO)
        compose.onAllNodesWithContentDescription(text(R.string.pro_locked)).assertCountEquals(0)

        compose.onNodeWithContentDescription(text(R.string.media_show_grid)).performClick()
        compose.onNodeWithTag(DECK_CARD_TAG).assertDoesNotExist()
        assertEquals(0, lockedTaps)
    }

    @Test
    fun pending_isStillLocked() {
        show(pro = ProState.PENDING)
        compose.onNodeWithContentDescription(text(R.string.media_show_grid)).performClick()
        assertEquals(1, lockedTaps)
    }
}
