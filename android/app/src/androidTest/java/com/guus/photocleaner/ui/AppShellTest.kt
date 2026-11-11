package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.service.MediaAccess
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun text(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(state: ShellState) {
        compose.setContent {
            PhotoCleanerTheme(darkTheme = false) {
                AppShell(
                    state = state,
                    darkTheme = false,
                    onFinishOnboarding = { calls += "onboarded" },
                    onRequestAccess = { calls += "request" },
                    onOpenSettings = { calls += "settings" },
                )
            }
        }
    }

    @Test
    fun firstStart_showsOnboardingAndFinishesAfterThreePages() {
        show(ShellState(didOnboard = false, access = MediaAccess.DENIED, canAskAgain = true))

        compose.onNodeWithText(text(R.string.onboarding_1_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(text(R.string.onboarding_2_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(text(R.string.onboarding_3_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_start)).performClick()

        assertEquals(listOf("onboarded"), calls)
    }

    @Test
    fun skip_finishesOnboarding() {
        show(ShellState(didOnboard = false, access = MediaAccess.DENIED, canAskAgain = true))

        compose.onNodeWithText(text(R.string.onboarding_skip)).performClick()

        assertEquals(listOf("onboarded"), calls)
    }

    @Test
    fun noAccess_showsGateThatRequestsAccess() {
        show(ShellState(didOnboard = true, access = MediaAccess.DENIED, canAskAgain = true))

        compose.onNodeWithText(text(R.string.gate_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tab_trash)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.gate_grant)).performClick()

        assertEquals(listOf("request"), calls)
    }

    @Test
    fun permanentlyDenied_gateOpensSystemSettings() {
        show(ShellState(didOnboard = true, access = MediaAccess.DENIED, canAskAgain = false))

        compose.onNodeWithText(text(R.string.gate_grant)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.gate_open_settings)).performClick()

        assertEquals(listOf("settings"), calls)
    }

    @Test
    fun partialAccess_showsTabsWithFullAccessBanner() {
        show(ShellState(didOnboard = true, access = MediaAccess.PARTIAL, canAskAgain = true))

        compose.onNodeWithText(text(R.string.placeholder_media)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.partial_access_text)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.partial_access_action)).performClick()

        assertEquals(listOf("request"), calls)
    }

    @Test
    fun fullAccess_showsTabsWithoutBannerAndSwitchesTabs() {
        show(ShellState(didOnboard = true, access = MediaAccess.FULL, canAskAgain = true))

        compose.onNodeWithText(text(R.string.partial_access_text)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tab_trash)).performClick()
        compose.onNodeWithText(text(R.string.placeholder_trash)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tab_settings)).performClick()
        compose.onNodeWithText(text(R.string.placeholder_settings)).assertIsDisplayed()
    }
}
