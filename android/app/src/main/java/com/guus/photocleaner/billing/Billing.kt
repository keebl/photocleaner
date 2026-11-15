package com.guus.photocleaner.billing

import android.app.Activity
import com.guus.photocleaner.service.ProState
import kotlinx.coroutines.flow.StateFlow

/** The Pro product's price as the store reports it. */
sealed interface ProductPrice {
    /** Still asking the store; the sheet shows without a price. */
    data object Loading : ProductPrice

    /** Localized price from the store, e.g. "€ 4,99". */
    data class Available(val formatted: String) : ProductPrice

    /** Store not reachable or product not found: Buy is disabled (AC 10). */
    data object Unavailable : ProductPrice
}

/**
 * The store boundary for the Pro unlock (specs/pro-unlock.md). [PlayBilling] talks to Google
 * Play; [FakeBilling] stands in for tests. Neither blocks the UI: [entitlement] starts from
 * the on-device cache and updates when the store answers.
 */
interface Billing {
    val entitlement: StateFlow<ProState>
    val price: StateFlow<ProductPrice>

    /** (Re)connects if needed and queries the product and the owned purchases. */
    fun refresh()

    /** Opens the store's purchase dialog; the result arrives through [entitlement]. */
    fun launchPurchase(activity: Activity)

    /** "Restore purchases": asks the store again for purchases owned by this account. */
    fun restore() = refresh()
}
