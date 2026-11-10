package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.KeepStore
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.FakePhotoSource
import com.guus.photocleaner.ui.deck.DECK_CARD_TAG
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deck behaviour against [FakePhotoSource] with real stores (spec step 6, AC 4–7).
 * Today is fixed at 10 November 2026; the library has one item per earlier year.
 */
@RunWith(AndroidJUnit4::class)
class MediaScreenDeckTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 10)
    private lateinit var dir: File
    private lateinit var trash: TrashStore
    private lateinit var keep: KeepStore

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun yearsAgo(n: Int): String =
        context.resources.getQuantityString(R.plurals.deck_years_ago, n, n, 2026 - n)

    private fun item(id: String, year: Int, month: Int = 11, day: Int = 10) = PhotoAsset(
        id = id,
        capturedAtMillis = LocalDate.of(year, month, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        modifiedAtMillis = null,
        pixelWidth = 400,
        pixelHeight = 300,
        byteSize = 2_000_000,
        filename = "$id.jpg",
    )

    // Newest first: 2025 (1 year ago), 2023 (3), 2019 (7); one item on another day.
    private val library = listOf(
        item("y2025", 2025),
        item("y2023", 2023),
        item("y2019", 2019),
        item("otherDay", 2024, day = 11),
    )

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "deck-test-${System.nanoTime()}").apply { mkdirs() }
        trash = TrashStore(File(dir, "trash.json"))
        keep = KeepStore(File(dir, "kept.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun show(hintSeen: Boolean = true) {
        val source = FakePhotoSource(library, zone)
        compose.setContent {
            var seen by remember { mutableStateOf(hintSeen) }
            PhotoCleanerTheme(darkTheme = false) {
                MediaScreen(
                    source = source,
                    trash = trash,
                    keep = keep,
                    today = today,
                    zone = zone,
                    swipeHintSeen = seen,
                    onSwipeHintSeen = { seen = true },
                )
            }
        }
    }

    @Test
    fun deck_showsOnlyItemsFromThisDayInEarlierYears() {
        show()

        compose.onNodeWithText(yearsAgo(1)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.deck_progress, 1, 3)).assertIsDisplayed()
    }

    @Test
    fun swipeRight_keepsTheItemAndShowsTheNext() {
        show()

        compose.onNodeWithTag(DECK_CARD_TAG).performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertTrue(keep.contains("y2025"))
        assertFalse(trash.contains("y2025"))
        compose.onNodeWithText(yearsAgo(3)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.deck_progress, 2, 3)).assertIsDisplayed()
    }

    @Test
    fun swipeLeft_discardsTheItemIntoTheTrash() {
        show()

        compose.onNodeWithTag(DECK_CARD_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertTrue(trash.contains("y2025"))
        assertEquals(1, trash.totalCleaned.value)
        assertFalse(keep.contains("y2025"))
        compose.onNodeWithText(yearsAgo(3)).assertIsDisplayed()
    }

    @Test
    fun undo_revertsTheLastDecisionAndTheCardReturns() {
        show()
        compose.onNodeWithTag(DECK_CARD_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.deck_undo_discarded)).performClick()
        compose.waitForIdle()

        assertFalse(trash.contains("y2025"))
        assertEquals(0, trash.totalCleaned.value)
        compose.onNodeWithText(yearsAgo(1)).assertIsDisplayed()
    }

    @Test
    fun decidedItems_doNotReappear() {
        keep.keep("y2025")
        trash.mark(library[1], "deck")

        show()

        compose.onNodeWithText(yearsAgo(7)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.deck_progress, 1, 1)).assertIsDisplayed()
    }

    @Test
    fun allDecided_showsDoneSummary() {
        show()
        repeat(3) {
            compose.onNodeWithTag(DECK_CARD_TAG).performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }

        compose.onNodeWithText(text(R.string.deck_done_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.deck_done_summary, 0, 3)).assertIsDisplayed()
    }

    @Test
    fun swipeHint_isShownOnceUntilDismissed() {
        show(hintSeen = false)

        compose.onNodeWithText(text(R.string.deck_hint_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.deck_hint_ok)).performClick()

        compose.onNodeWithText(text(R.string.deck_hint_title)).assertDoesNotExist()
    }
}
