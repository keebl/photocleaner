package com.guus.photocleaner.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guus.photocleaner.R
import com.guus.photocleaner.billing.EntitlementTracker
import com.guus.photocleaner.billing.FakeBilling
import com.guus.photocleaner.service.EntitlementStore
import com.guus.photocleaner.service.ProState
import com.guus.photocleaner.service.PurchaseStatus
import com.guus.photocleaner.service.StorePurchase
import com.guus.photocleaner.ui.pro.PRO_BUY_TAG
import com.guus.photocleaner.ui.pro.PRO_SHEET_TAG
import com.guus.photocleaner.ui.pro.ProSheetHost
import com.guus.photocleaner.ui.theme.PhotoCleanerTheme
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Pro sheet against [FakeBilling] (specs/pro-unlock.md AC 9–14). */
@RunWith(AndroidJUnit4::class)
class ProSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var billing: FakeBilling

    private fun text(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "pro-sheet-test-${System.nanoTime()}").apply { mkdirs() }
        billing = FakeBilling(EntitlementTracker(EntitlementStore(File(dir, "entitlement.json"))))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun show() {
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            PhotoCleanerTheme(darkTheme = false) {
                if (open) ProSheetHost(billing = billing, onClose = { open = false })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun sheet_listsFeaturesPriceAndActions() {
        show()

        compose.onNodeWithText(text(R.string.pro_subtitle)).assertIsDisplayed()
        listOf(R.string.pro_feature_periods, R.string.pro_feature_videos, R.string.pro_feature_grid, R.string.pro_feature_duplicates)
            .forEach { compose.onNodeWithText(text(it)).assertIsDisplayed() }
        compose.onNodeWithText(text(R.string.pro_buy, "€ 4,99")).assertIsDisplayed()
        compose.onNodeWithTag(PRO_BUY_TAG).assertIsEnabled()
        compose.onNodeWithText(text(R.string.pro_restore)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pro_close)).assertIsDisplayed()
    }

    @Test
    fun storeUnavailable_showsMessageAndDisablesBuy() {
        billing.reachable = false
        show()

        compose.onNodeWithText(text(R.string.pro_store_unavailable)).assertIsDisplayed()
        compose.onNodeWithTag(PRO_BUY_TAG).assertIsNotEnabled()
    }

    @Test
    fun buy_unlocksProAndClosesTheSheet() {
        show()
        compose.onNodeWithTag(PRO_BUY_TAG).performClick()
        compose.waitForIdle()

        assertEquals(ProState.PRO, billing.entitlement.value)
        assertEquals(listOf("token-1"), billing.acknowledged)
        compose.onNodeWithTag(PRO_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun cancel_keepsTheSheetWithoutAnError() {
        billing.nextOutcome = FakeBilling.Outcome.CANCEL
        show()
        compose.onNodeWithTag(PRO_BUY_TAG).performClick()
        compose.waitForIdle()

        assertEquals(ProState.FREE, billing.entitlement.value)
        compose.onNodeWithTag(PRO_BUY_TAG).assertIsEnabled()
        compose.onNodeWithText(text(R.string.pro_store_unavailable)).assertDoesNotExist()
    }

    @Test
    fun pending_showsPaymentPendingAndStaysLocked() {
        billing.nextOutcome = FakeBilling.Outcome.PENDING
        show()
        compose.onNodeWithTag(PRO_BUY_TAG).performClick()
        compose.waitForIdle()

        assertEquals(ProState.PENDING, billing.entitlement.value)
        compose.onNodeWithText(text(R.string.pro_pending)).assertIsDisplayed()
        compose.onNodeWithTag(PRO_BUY_TAG).assertIsNotEnabled()
    }

    @Test
    fun restore_unlocksAnEarlierPurchase() {
        show()
        billing.owned += StorePurchase(listOf("pro_lifetime"), PurchaseStatus.PURCHASED, "earlier", acknowledged = true)
        compose.onNodeWithText(text(R.string.pro_restore)).performClick()
        compose.waitForIdle()

        assertEquals(ProState.PRO, billing.entitlement.value)
        compose.onNodeWithTag(PRO_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun close_dismissesTheSheet() {
        show()
        compose.onNodeWithText(text(R.string.pro_close)).performClick()
        compose.waitForIdle()

        assertTrue(billing.entitlement.value == ProState.FREE)
        compose.onNodeWithTag(PRO_SHEET_TAG).assertDoesNotExist()
    }
}
