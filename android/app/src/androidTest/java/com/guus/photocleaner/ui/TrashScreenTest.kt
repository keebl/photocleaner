package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.FakePhotoSource
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import com.guus.photocleaner.ui.trash.ExpiredTrashPrompt
import com.guus.photocleaner.ui.trash.SystemTrash
import com.guus.photocleaner.ui.trash.TrashScreen
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Trash: restore (AC 13), empty with confirm and cancel (AC 14, 15). */
@RunWith(AndroidJUnit4::class)
class TrashScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val start: Instant = Instant.parse("2026-10-01T12:00:00Z")
    private lateinit var dir: File

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun item(id: String) = PhotoAsset(
        id = id,
        capturedAtMillis = start.toEpochMilli(),
        modifiedAtMillis = null,
        pixelWidth = 10,
        pixelHeight = 10,
        byteSize = 1,
        filename = null,
    )

    private val a = item("a")
    private val b = item("b")
    private val source = FakePhotoSource(listOf(a, b))

    /** System trash stand-in: records requests and answers confirmed or cancelled. */
    private val requested = mutableListOf<List<String>>()
    private fun systemTrash(confirm: Boolean) = SystemTrash { assets ->
        requested += assets.map { it.id }
        confirm
    }

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "trash-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun store(now: Instant) = TrashStore(File(dir, "trash.json"), Clock.fixed(now, ZoneOffset.UTC))

    private fun show(trash: TrashStore, confirm: Boolean = true) {
        compose.setContent {
            PhotoCleanerTheme(darkTheme = false) {
                TrashScreen(source = source, trash = trash, systemTrash = systemTrash(confirm))
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun restore_removesTheItemAndLowersTheCounter() {
        store(start).markAll(listOf(a, b), "on_this_day")
        val trash = store(start)
        show(trash)

        compose.onAllNodesWithText(text(R.string.trash_restore))[0].performClick()
        compose.waitForIdle()

        assertEquals(1, trash.items.value.size)
        assertEquals(1, trash.totalCleaned.value)
    }

    @Test
    fun emptyAll_confirmed_movesEverythingToTheSystemTrash() {
        store(start).markAll(listOf(a, b), "on_this_day")
        val trash = store(start)
        show(trash, confirm = true)

        compose.onNodeWithContentDescription(text(R.string.trash_more)).performClick()
        compose.onNodeWithText(text(R.string.trash_delete_all)).performClick()
        compose.onNodeWithText(text(R.string.trash_delete_confirm)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(listOf("a", "b")), requested)
        assertTrue(trash.items.value.isEmpty())
        assertEquals(2, trash.totalCleaned.value)
        compose.onNodeWithText(text(R.string.trash_empty_title)).assertIsDisplayed()
    }

    @Test
    fun systemDialogCancelled_keepsTheItems() {
        store(start).markAll(listOf(a, b), "on_this_day")
        val trash = store(start)
        show(trash, confirm = false)

        compose.onNodeWithContentDescription(text(R.string.trash_more)).performClick()
        compose.onNodeWithText(text(R.string.trash_delete_all)).performClick()
        compose.onNodeWithText(text(R.string.trash_delete_confirm)).performClick()
        compose.waitForIdle()

        assertEquals(1, requested.size)
        assertEquals(setOf("a", "b"), trash.trashedIds)
    }

    @Test
    fun expiredItemsOnOpen_promptThenMoveOnlyExpiredOnes() {
        store(start).mark(a, "on_this_day")
        store(start.plusSeconds(20L * 86_400)).mark(b, "on_this_day")
        val trash = store(start.plusSeconds(31L * 86_400))
        compose.setContent {
            PhotoCleanerTheme(darkTheme = false) {
                ExpiredTrashPrompt(source = source, trash = trash, systemTrash = systemTrash(confirm = true))
            }
        }

        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.trash_expired_title, 1, 1)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.trash_expired_confirm)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(listOf("a")), requested)
        assertEquals(setOf("b"), trash.trashedIds)
    }

    @Test
    fun expiredPrompt_later_changesNothing() {
        store(start).mark(a, "on_this_day")
        val trash = store(start.plusSeconds(31L * 86_400))
        compose.setContent {
            PhotoCleanerTheme(darkTheme = false) {
                ExpiredTrashPrompt(source = source, trash = trash, systemTrash = systemTrash(confirm = true))
            }
        }

        compose.onNodeWithText(text(R.string.trash_expired_later)).performClick()
        compose.waitForIdle()

        assertTrue(requested.isEmpty())
        assertEquals(setOf("a"), trash.trashedIds)
    }
}
