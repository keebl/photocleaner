package com.guus.photocleaner.billing

import com.guus.photocleaner.service.EntitlementStore
import com.guus.photocleaner.service.ProState
import com.guus.photocleaner.service.PurchaseStatus
import com.guus.photocleaner.service.StorePurchase
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Entitlement transitions through [FakeBilling] and the shared tracker (specs/pro-unlock.md AC 10–16). */
class BillingTransitionsTest {

    private lateinit var dir: File
    private val file get() = File(dir, "entitlement.json")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("billing").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun billing() = FakeBilling(EntitlementTracker(EntitlementStore(file)))

    @Test
    fun startsFree_withPriceAfterRefresh() {
        val billing = billing()
        assertEquals(ProState.FREE, billing.entitlement.value)
        assertEquals(ProductPrice.Loading, billing.price.value)

        billing.refresh()
        assertEquals(ProductPrice.Available("€ 4,99"), billing.price.value)
    }

    @Test
    fun storeUnreachable_priceUnavailable() {
        val billing = billing().apply { reachable = false }
        billing.refresh()
        assertEquals(ProductPrice.Unavailable, billing.price.value)
    }

    @Test
    fun purchase_unlocksImmediatelyAndAcknowledges() {
        val billing = billing()
        billing.refresh()
        billing.purchase()

        assertEquals(ProState.PRO, billing.entitlement.value)
        assertEquals(listOf("token-1"), billing.acknowledged)
        assertTrue(billing.owned.single().acknowledged)
    }

    @Test
    fun cancel_changesNothing() {
        val billing = billing().apply { nextOutcome = FakeBilling.Outcome.CANCEL }
        billing.purchase()

        assertEquals(ProState.FREE, billing.entitlement.value)
        assertTrue(billing.owned.isEmpty())
    }

    @Test
    fun pending_staysLockedUntilPurchased() {
        val billing = billing().apply { nextOutcome = FakeBilling.Outcome.PENDING }
        billing.purchase()
        assertEquals(ProState.PENDING, billing.entitlement.value)
        assertTrue(billing.acknowledged.isEmpty())

        billing.completePending()
        billing.refresh()
        assertEquals(ProState.PRO, billing.entitlement.value)
        assertEquals(listOf("token-1"), billing.acknowledged)
    }

    @Test
    fun restore_unlocksAPurchaseFromAnotherDevice() {
        val billing = billing()
        billing.owned += StorePurchase(
            listOf("pro_lifetime"),
            PurchaseStatus.PURCHASED,
            "other-device",
            acknowledged = true,
        )
        billing.restore()

        assertEquals(ProState.PRO, billing.entitlement.value)
        assertTrue(billing.acknowledged.isEmpty())
    }

    @Test
    fun refund_locksProAgainAtTheNextQuery() {
        val billing = billing()
        billing.purchase()
        billing.refund()
        assertEquals(ProState.PRO, billing.entitlement.value) // until Play is asked again

        billing.refresh()
        assertEquals(ProState.FREE, billing.entitlement.value)
        assertEquals(ProState.FREE, EntitlementStore(file).state)
    }

    @Test
    fun offlineStart_usesTheCachedEntitlement() {
        billing().purchase()

        val restarted = billing().apply { reachable = false }
        restarted.refresh()
        assertEquals(ProState.PRO, restarted.entitlement.value)
    }

    @Test
    fun staleQueryAfterPurchase_doesNotLockProAgain() {
        val tracker = EntitlementTracker(EntitlementStore(file))
        val startedAt = tracker.generation // query sent on resume, before the dialog result
        tracker.onUpdate(listOf(StorePurchase(listOf("pro_lifetime"), PurchaseStatus.PURCHASED, "t", acknowledged = false)))
        tracker.onQuery(emptyList(), startedAt)

        assertEquals(ProState.PRO, tracker.state.value)
    }

    @Test
    fun pendingSecondAttempt_doesNotLockOwnedPro() {
        val billing = billing()
        billing.purchase()
        billing.nextOutcome = FakeBilling.Outcome.PENDING
        billing.purchase()

        assertEquals(ProState.PRO, billing.entitlement.value)
    }
}
