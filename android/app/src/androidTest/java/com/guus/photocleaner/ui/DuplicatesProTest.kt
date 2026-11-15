package com.guus.photocleaner.ui

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.FakePhotoSource
import com.guus.photocleaner.ui.duplicates.DuplicatesScreen
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Dubbelen for a free user (specs/pro-unlock.md AC 6): results free, discarding needs Pro. */
@RunWith(AndroidJUnit4::class)
class DuplicatesProTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var trash: TrashStore
    private var lockedTaps = 0

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    // Same capture second and size: one exact-duplicate group of two.
    private fun copy(id: String) = PhotoAsset(
        id = id,
        capturedAtMillis = 1_700_000_000_000,
        modifiedAtMillis = null,
        pixelWidth = 400,
        pixelHeight = 300,
        byteSize = 3_000_000,
        filename = "$id.jpg",
    )

    private val source = FakePhotoSource(listOf(copy("a"), copy("b")))

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "dup-pro-test-${System.nanoTime()}").apply { mkdirs() }
        trash = TrashStore(File(dir, "trash.json"))
        lockedTaps = 0
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun show(locked: Boolean) {
        compose.setContent {
            PhotoCleanerTheme(darkTheme = false) {
                DuplicatesScreen(
                    source = source,
                    trash = trash,
                    dateText = { null },
                    discardLocked = locked,
                    onLocked = { lockedTaps++ },
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text(R.string.dup_discard_n, 1)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun free_showsGroupsAndSpace_discardOpensPro() {
        show(locked = true)

        val gain = Formatter.formatShortFileSize(context, 3_000_000)
        compose.onNodeWithText(text(R.string.dup_header, 2, 1, gain)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.dup_discard_n, 1)).performClick()
        compose.waitForIdle()

        assertEquals(1, lockedTaps)
        assertTrue(trash.items.value.isEmpty())
        compose.onNodeWithText(text(R.string.dup_header, 2, 1, gain)).assertIsDisplayed()
    }

    @Test
    fun pro_discardMovesTheCopyToTheTrash() {
        show(locked = false)

        compose.onNodeWithText(text(R.string.dup_discard_n, 1)).performClick()
        compose.waitForIdle()

        assertEquals(0, lockedTaps)
        assertEquals(1, trash.items.value.size)
    }
}
