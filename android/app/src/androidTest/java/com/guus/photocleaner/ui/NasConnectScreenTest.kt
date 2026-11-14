package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.source.SourceKind
import com.guus.photocleaner.source.nas.NasBrowser
import com.guus.photocleaner.source.nas.NasConfig
import com.guus.photocleaner.source.nas.NasError
import com.guus.photocleaner.source.nas.NasException
import com.guus.photocleaner.ui.nas.NAS_CONNECT_BUTTON_TAG
import com.guus.photocleaner.ui.nas.NAS_PASSWORD_FIELD_TAG
import com.guus.photocleaner.ui.nas.NAS_SERVER_FIELD_TAG
import com.guus.photocleaner.ui.nas.NAS_USER_FIELD_TAG
import com.guus.photocleaner.ui.nas.NasConnectScreen
import com.guus.photocleaner.ui.nas.SOURCE_MENU_TAG
import com.guus.photocleaner.ui.nas.SourceMenu
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** specs/android-nas.md AC 1–8: the two-step link screen, and the source menu. */
@RunWith(AndroidJUnit4::class)
class NasConnectScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(@StringRes id: Int): String = context.getString(id)

    private class FakeBrowser(private val failure: NasError? = null) : NasBrowser {
        override suspend fun shareNames(): List<String> {
            failure?.let { throw NasException(it) }
            return listOf("photo", "video")
        }

        override suspend fun folders(share: String, path: String): List<String> =
            if (path.isEmpty()) listOf("2022", "2023") else emptyList()

        override fun close() = Unit
    }

    private val opened = mutableListOf<List<Any>>()
    private var linked: Pair<NasConfig, String>? = null
    private var closed = false

    private fun show(failure: NasError? = null, current: NasConfig? = null) {
        compose.setContent {
            PhotoCleanerTheme(darkTheme = false) {
                NasConnectScreen(
                    current = current,
                    openBrowser = { host, port, user, password ->
                        opened += listOf(host, port, user, password)
                        FakeBrowser(failure)
                    },
                    onLink = { config, password -> linked = config to password },
                    onUnlink = {},
                    onClose = { closed = true },
                )
            }
        }
    }

    @Test
    fun connectButton_isDisabledWithoutServer() {
        show()
        compose.onNodeWithTag(NAS_CONNECT_BUTTON_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(NAS_SERVER_FIELD_TAG).performTextInput("nas.local")
        compose.onNodeWithTag(NAS_CONNECT_BUTTON_TAG).assertIsEnabled()
    }

    @Test
    fun login_listsShares_browsesFolders_andLinksTheChosenFolder() {
        show()
        compose.onNodeWithTag(NAS_SERVER_FIELD_TAG).performTextInput("10.0.2.2:1445")
        compose.onNodeWithTag(NAS_USER_FIELD_TAG).performTextInput("guus")
        compose.onNodeWithTag(NAS_PASSWORD_FIELD_TAG).performTextInput("pw")
        compose.onNodeWithTag(NAS_CONNECT_BUTTON_TAG).performClick()

        compose.onNodeWithText(text(R.string.nas_choose_share)).assertIsDisplayed()
        assertEquals(listOf<Any>("10.0.2.2", 1445, "guus", "pw"), opened.single())
        compose.onNodeWithText("photo").performClick()
        compose.onNodeWithText(text(R.string.nas_use_share)).assertIsDisplayed()
        compose.onNodeWithText("2022").performClick()
        compose.onNodeWithText(text(R.string.nas_no_subfolders)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nas_use_folder)).performClick()

        compose.waitUntil { closed }
        assertEquals(
            NasConfig(host = "10.0.2.2", port = 1445, share = "photo", folder = "2022", username = "guus") to "pw",
            linked,
        )
    }

    @Test
    fun wrongPassword_showsTheLoginMessage_andStaysOnTheForm() {
        show(failure = NasError.AUTH_FAILED)
        compose.onNodeWithTag(NAS_SERVER_FIELD_TAG).performTextInput("nas")
        compose.onNodeWithTag(NAS_CONNECT_BUTTON_TAG).performClick()

        compose.onNodeWithText(text(R.string.nas_error_auth)).assertIsDisplayed()
        assertEquals(null, linked)
    }

    @Test
    fun invalidServer_isRejectedBeforeConnecting() {
        show()
        compose.onNodeWithTag(NAS_SERVER_FIELD_TAG).performTextInput("nas:99999")
        compose.onNodeWithTag(NAS_CONNECT_BUTTON_TAG).performClick()

        compose.onNodeWithText(text(R.string.nas_error_bad_server)).assertIsDisplayed()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun linkedNas_prefillsTheForm_andOffersUnlink() {
        show(current = NasConfig(host = "nas.local", share = "photo", username = "guus"))
        compose.onNodeWithText("nas.local").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nas_unlink)).assertIsDisplayed()
    }

    @Test
    fun sourceMenu_switchesSource_andOffersLinking() {
        var selected: SourceKind? = null
        var connect = false
        compose.setContent {
            PhotoCleanerTheme(darkTheme = false) {
                SourceMenu(
                    kind = SourceKind.DEVICE,
                    nasName = "photo op nas.local",
                    onSelect = { selected = it },
                    onConnectNas = { connect = true },
                )
            }
        }
        compose.onNodeWithTag(SOURCE_MENU_TAG).performClick()
        compose.onNodeWithText("photo op nas.local").performClick()
        assertEquals(SourceKind.NAS, selected)

        compose.onNodeWithTag(SOURCE_MENU_TAG).performClick()
        compose.onNodeWithText(text(R.string.source_change_nas)).performClick()
        assertTrue(connect)
    }
}
