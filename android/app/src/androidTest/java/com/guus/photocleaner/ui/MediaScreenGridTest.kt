package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.model.PhotoCategory
import com.guus.photocleaner.service.KeepStore
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.FakePhotoSource
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

/** Grid review (spec AC 8) and the category filter (AC 9) against [FakePhotoSource]. */
@RunWith(AndroidJUnit4::class)
class MediaScreenGridTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 10)
    private lateinit var dir: File
    private lateinit var trash: TrashStore
    private lateinit var keep: KeepStore

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun item(id: String, year: Int, categories: Set<PhotoCategory> = emptySet()) = PhotoAsset(
        id = id,
        capturedAtMillis = LocalDate.of(year, 11, 10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        modifiedAtMillis = null,
        pixelWidth = 400,
        pixelHeight = 300,
        byteSize = 1_000,
        filename = "$id.jpg",
        categories = categories,
    )

    private val library = listOf(
        item("y2025", 2025),
        item("y2023", 2023, setOf(PhotoCategory.SCREENSHOT)),
        item("y2021", 2021),
        item("y2019", 2019),
    )

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "grid-test-${System.nanoTime()}").apply { mkdirs() }
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
            PhotoCleanerTheme(darkTheme = false) {
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

    private fun openGrid() {
        compose.onNodeWithContentDescription(text(R.string.media_show_grid)).performClick()
        compose.waitForIdle()
    }

    private fun selectButtons() = compose.onAllNodesWithContentDescription(text(R.string.grid_select))

    @Test
    fun bulkDiscard_movesAllSelectedItemsToTheTrashInOneAction() {
        show()
        openGrid()
        selectButtons().assertCountEquals(4)

        selectButtons()[0].performClick()
        selectButtons()[0].performClick() // the first unselected one is now the second tile
        compose.onNodeWithText(text(R.string.grid_selected_count, 2, 4)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.grid_discard_n, 2)).performClick()
        compose.waitForIdle()

        assertEquals(setOf("y2025", "y2023"), trash.trashedIds)
        assertEquals(2, trash.totalCleaned.value)
        selectButtons().assertCountEquals(2)
    }

    @Test
    fun undo_restoresTheLastBulkAction() {
        show()
        openGrid()
        selectButtons()[0].performClick()
        compose.onNodeWithText(text(R.string.grid_discard_n, 1)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.grid_undo)).performClick()
        compose.waitForIdle()

        assertTrue(trash.items.value.isEmpty())
        selectButtons().assertCountEquals(4)
    }

    @Test
    fun selectAllAndKeep_keepsEverythingAndShowsTheEmptyState() {
        show()
        openGrid()

        compose.onNodeWithText(text(R.string.grid_select_all)).performClick()
        compose.onNodeWithText(text(R.string.grid_keep_n, 4)).performClick()
        compose.waitForIdle()

        assertEquals(setOf("y2025", "y2023", "y2021", "y2019"), keep.ids.value)
        compose.onNodeWithText(text(R.string.media_empty_title)).assertIsDisplayed()
    }

    @Test
    fun categoryFilter_showsOnlyThatCategory() {
        show()

        compose.onNodeWithText(text(R.string.media_category_all)).performClick()
        compose.onNodeWithText(text(R.string.media_category_screenshots)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.deck_progress, 1, 1)).assertIsDisplayed()
    }
}
