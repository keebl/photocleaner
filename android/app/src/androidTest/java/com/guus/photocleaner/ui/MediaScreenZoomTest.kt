package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.guus.photocleaner.ui.zoom.ZOOM_SCREEN_TAG
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Zoom view opened from the deck and the grid; deciding there counts like a swipe (step 8). */
@RunWith(AndroidJUnit4::class)
class MediaScreenZoomTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 10)
    private lateinit var dir: File
    private lateinit var trash: TrashStore
    private lateinit var keep: KeepStore

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun item(id: String, year: Int) = PhotoAsset(
        id = id,
        capturedAtMillis = LocalDate.of(year, 11, 10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        modifiedAtMillis = null,
        pixelWidth = 400,
        pixelHeight = 300,
        byteSize = 1_000,
        filename = "$id.jpg",
    )

    private val library = listOf(item("y2025", 2025), item("y2023", 2023))

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "zoom-test-${System.nanoTime()}").apply { mkdirs() }
        trash = TrashStore(File(dir, "trash.json"))
        keep = KeepStore(File(dir, "kept.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun show() {
        val source = FakePhotoSource(library, zone)
        compose.setContent {
            PhotoCleanerTheme(darkTheme = true) {
                MediaScreen(
                    source = source,
                    trash = trash,
                    keep = keep,
                    swipeHintSeen = true,
                    onSwipeHintSeen = {},
                    today = today,
                    zone = zone,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun tapOnCard_opensZoom_andCloseReturnsWithoutDeciding() {
        show()

        compose.onNodeWithTag(DECK_CARD_TAG).performClick()
        compose.onNodeWithTag(ZOOM_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.zoom_close)).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(ZOOM_SCREEN_TAG).assertDoesNotExist()
        assertTrue(keep.ids.value.isEmpty())
        assertTrue(trash.items.value.isEmpty())
    }

    @Test
    fun swipeRightInZoom_keepsAndTheDeckMovesOn_withUndo() {
        show()

        compose.onNodeWithTag(DECK_CARD_TAG).performClick()
        compose.onNodeWithTag(ZOOM_SCREEN_TAG).performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.onNodeWithTag(ZOOM_SCREEN_TAG).assertDoesNotExist()
        assertTrue(keep.contains("y2025"))
        compose.onNodeWithText(text(R.string.deck_progress, 2, 2)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.deck_undo_kept)).assertIsDisplayed()
    }

    @Test
    fun tapOnGridTile_opensZoom_andSwipeLeftDiscards() {
        show()
        compose.onNodeWithContentDescription(text(R.string.media_show_grid)).performClick()
        compose.waitForIdle()

        compose.onAllNodesWithContentDescription(text(R.string.grid_open))[0].performClick()
        compose.onNodeWithTag(ZOOM_SCREEN_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(setOf("y2025"), trash.trashedIds)
        compose.onNodeWithTag(ZOOM_SCREEN_TAG).assertDoesNotExist()
    }
}
