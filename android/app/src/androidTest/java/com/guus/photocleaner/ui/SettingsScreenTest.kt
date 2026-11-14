package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.model.PhotoAsset
import com.guus.photocleaner.service.AppTheme
import com.guus.photocleaner.service.KeepStore
import com.guus.photocleaner.service.ReminderSettings
import com.guus.photocleaner.service.TrashStore
import com.guus.photocleaner.source.SourceKind
import com.guus.photocleaner.ui.settings.REMINDER_SWITCH_TAG
import com.guus.photocleaner.ui.settings.SOURCE_SEGMENT_NAS_TAG
import com.guus.photocleaner.ui.settings.SettingsScreen
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings: theme, reminder with notification permission (AC 17, 18) and keep reset. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var keep: KeepStore
    private lateinit var trash: TrashStore

    private var theme = AppTheme.DARK
    private var reminder = ReminderSettings()
    private var selectedSource: SourceKind? = null
    private var connectNas = false
    private var clearedCache = false

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "settings-test-${System.nanoTime()}").apply { mkdirs() }
        keep = KeepStore(File(dir, "kept.json"))
        trash = TrashStore(File(dir, "trash.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun show(notificationsAllowed: Boolean, nasName: String? = null) {
        compose.setContent {
            var themeState by remember { mutableStateOf(theme) }
            var reminderState by remember { mutableStateOf(reminder) }
            PhotoCleanerTheme(darkTheme = false) {
                SettingsScreen(
                    theme = themeState,
                    onTheme = {
                        themeState = it
                        theme = it
                    },
                    reminder = reminderState,
                    onReminder = {
                        reminderState = it
                        reminder = it
                    },
                    requestNotifications = { notificationsAllowed },
                    keep = keep,
                    trash = trash,
                    versionText = "1.0 (1)",
                    nasName = nasName,
                    onSelectSource = { selectedSource = it },
                    onConnectNas = { connectNas = true },
                    onClearNasCache = { clearedCache = true },
                )
            }
        }
        compose.waitForIdle()
    }

    /** The lifetime counter lives here; it survives emptying the trash. */
    @Test
    fun cleanupSection_showsTotalCleanedAfterTrashIsEmptied() {
        val photo = PhotoAsset(id = "a", capturedAtMillis = 0, modifiedAtMillis = null, pixelWidth = 1, pixelHeight = 1, byteSize = 1, filename = null)
        trash.mark(photo, "deck")
        trash.forget(listOf("a"))
        show(notificationsAllowed = true)

        compose.onNodeWithText(text(R.string.settings_cleaned_count)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosingATheme_reportsIt() {
        show(notificationsAllowed = true)

        compose.onNodeWithText(text(R.string.settings_theme_light)).performClick()

        assertEquals(AppTheme.LIGHT, theme)
    }

    @Test
    fun reminderOn_withPermission_isEnabled() {
        show(notificationsAllowed = true)

        compose.onNodeWithTag(REMINDER_SWITCH_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(REMINDER_SWITCH_TAG).assertIsOn()
        assertTrue(reminder.enabled)
        compose.onNodeWithText(text(R.string.settings_reminder_time)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reminderOn_permissionRefused_switchReturnsOffWithHint() {
        show(notificationsAllowed = false)

        compose.onNodeWithTag(REMINDER_SWITCH_TAG).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(REMINDER_SWITCH_TAG).assertIsOff()
        assertEquals(false, reminder.enabled)
        compose.onNodeWithText(text(R.string.settings_reminder_denied)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun resetKept_afterConfirm_clearsAllKeepChoices() {
        keep.keep("a")
        keep.keep("b")
        show(notificationsAllowed = true)

        compose.onNodeWithText(text(R.string.settings_reset_kept)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.settings_reset_confirm)).performClick()
        compose.waitForIdle()

        assertTrue(keep.ids.value.isEmpty())
    }

    @Test
    fun about_showsRetentionAndVersion() {
        show(notificationsAllowed = true)

        compose.onNodeWithText("1.0 (1)").performScrollTo().assertIsDisplayed()
    }

    /** specs/android-nas.md: the Fotobron section, as on iOS. */
    @Test
    fun photoSource_withoutNas_choosingNasOpensTheLinkScreen() {
        show(notificationsAllowed = true)

        compose.onNodeWithText(text(R.string.settings_nas_not_linked)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SOURCE_SEGMENT_NAS_TAG).performScrollTo().performClick()

        assertTrue(connectNas)
        assertEquals(null, selectedSource)
    }

    @Test
    fun photoSource_withNas_selectsIt_andClearsTheCache() {
        show(notificationsAllowed = true, nasName = "photo op nas.local")

        compose.onNodeWithTag(SOURCE_SEGMENT_NAS_TAG).performScrollTo().performClick()
        assertEquals(SourceKind.NAS, selectedSource)
        compose.onNodeWithText(text(R.string.settings_nas_clear_cache)).performScrollTo().performClick()
        assertTrue(clearedCache)
        compose.onNodeWithText(text(R.string.source_change_nas)).performScrollTo().assertIsDisplayed()
    }
}
