package com.guus.photocleaner.billing

import android.app.Activity
import com.guus.photocleaner.service.ProEntitlement
import com.guus.photocleaner.service.ProState
import com.guus.photocleaner.service.PurchaseStatus
import com.guus.photocleaner.service.StorePurchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory store for tests and previews: what Play would hold for this account, plus the
 * outcome of the next purchase dialog. Real purchase flows are tested by hand on Play
 * internal testing (spec Plan step 8).
 */
class FakeBilling(
    private val tracker: EntitlementTracker,
    private val formattedPrice: String = "€ 4,99",
) : Billing {

    /** What the purchase dialog does next. */
    enum class Outcome { PURCHASE, PENDING, CANCEL }

    /** Whether the store can be reached; when false, queries fail and the cache stays. */
    var reachable = true

    var nextOutcome = Outcome.PURCHASE

    /** Purchases the store holds for this account. */
    val owned = mutableListOf<StorePurchase>()

    /** Tokens acknowledged so far. */
    val acknowledged = mutableListOf<String>()

    private val _price = MutableStateFlow<ProductPrice>(ProductPrice.Loading)
    override val price: StateFlow<ProductPrice> = _price.asStateFlow()
    override val entitlement: StateFlow<ProState> get() = tracker.state

    private var tokens = 0

    override fun refresh() {
        if (!reachable) {
            _price.value = ProductPrice.Unavailable
            return
        }
        _price.value = ProductPrice.Available(formattedPrice)
        acknowledge(tracker.onQuery(owned.toList()))
    }

    override fun launchPurchase(activity: Activity) = purchase()

    /** [launchPurchase] without an activity, for JVM tests. */
    fun purchase() {
        if (!reachable) return
        val status = when (nextOutcome) {
            Outcome.CANCEL -> return
            Outcome.PURCHASE -> PurchaseStatus.PURCHASED
            Outcome.PENDING -> PurchaseStatus.PENDING
        }
        val bought = StorePurchase(listOf(ProEntitlement.PRODUCT_ID), status, "token-${++tokens}", acknowledged = false)
        owned += bought
        acknowledge(tracker.onUpdate(listOf(bought)))
    }

    /** The open payment of a pending purchase went through (store side). */
    fun completePending() {
        owned.replaceAll { if (it.status == PurchaseStatus.PENDING) it.copy(status = PurchaseStatus.PURCHASED) else it }
    }

    /** Refund or revoke from the Console (store side); seen at the next [refresh]. */
    fun refund() {
        owned.clear()
    }

    private fun acknowledge(purchases: List<StorePurchase>) {
        purchases.forEach { purchase ->
            acknowledged += purchase.token
            owned.replaceAll { if (it.token == purchase.token) it.copy(acknowledged = true) else it }
        }
    }
}
