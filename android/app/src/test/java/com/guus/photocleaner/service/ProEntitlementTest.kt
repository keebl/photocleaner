package com.guus.photocleaner.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Purchase list → Pro state and acknowledgement (specs/pro-unlock.md AC 11–15). */
class ProEntitlementTest {

    private fun purchase(
        status: PurchaseStatus,
        acknowledged: Boolean = true,
        product: String = ProEntitlement.PRODUCT_ID,
        token: String = "token-$status",
    ) = StorePurchase(productIds = listOf(product), status = status, token = token, acknowledged = acknowledged)

    @Test
    fun noPurchases_isFree() {
        assertEquals(ProState.FREE, ProEntitlement.resolve(emptyList()))
    }

    @Test
    fun purchased_isPro() {
        assertEquals(ProState.PRO, ProEntitlement.resolve(listOf(purchase(PurchaseStatus.PURCHASED))))
    }

    @Test
    fun pending_isPendingAndNotPro() {
        assertEquals(ProState.PENDING, ProEntitlement.resolve(listOf(purchase(PurchaseStatus.PENDING))))
    }

    @Test
    fun unspecifiedState_isFree() {
        assertEquals(ProState.FREE, ProEntitlement.resolve(listOf(purchase(PurchaseStatus.UNSPECIFIED))))
    }

    @Test
    fun purchasedWinsOverPending() {
        val purchases = listOf(purchase(PurchaseStatus.PENDING), purchase(PurchaseStatus.PURCHASED))
        assertEquals(ProState.PRO, ProEntitlement.resolve(purchases))
    }

    @Test
    fun otherProducts_areIgnored() {
        val purchases = listOf(purchase(PurchaseStatus.PURCHASED, product = "something_else"))
        assertEquals(ProState.FREE, ProEntitlement.resolve(purchases))
        assertTrue(ProEntitlement.toAcknowledge(purchases).isEmpty())
    }

    @Test
    fun unacknowledgedPurchase_isProAndNeedsAcknowledgement() {
        val purchases = listOf(purchase(PurchaseStatus.PURCHASED, acknowledged = false, token = "t1"))
        assertEquals(ProState.PRO, ProEntitlement.resolve(purchases))
        assertEquals(listOf("t1"), ProEntitlement.toAcknowledge(purchases).map { it.token })
    }

    @Test
    fun acknowledgedOrPending_needNoAcknowledgement() {
        val purchases = listOf(
            purchase(PurchaseStatus.PURCHASED, acknowledged = true),
            purchase(PurchaseStatus.PENDING, acknowledged = false),
        )
        assertTrue(ProEntitlement.toAcknowledge(purchases).isEmpty())
    }

    @Test
    fun proToken_isThePurchasedOne() {
        val purchases = listOf(purchase(PurchaseStatus.PENDING, token = "p"), purchase(PurchaseStatus.PURCHASED, token = "b"))
        assertEquals("b", ProEntitlement.proToken(purchases))
        assertEquals(null, ProEntitlement.proToken(listOf(purchase(PurchaseStatus.PENDING))))
    }
}
