package com.guus.photocleaner.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.guus.photocleaner.service.ProEntitlement
import com.guus.photocleaner.service.ProState
import com.guus.photocleaner.service.PurchaseStatus
import com.guus.photocleaner.service.StorePurchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play Billing for the one-time `pro_lifetime` product (specs/pro-unlock.md). No
 * backend: Play's purchase list is the source of truth, cached by [EntitlementTracker].
 * Callers ask for [refresh] at start, on resume and when the Pro sheet opens; a dropped
 * connection is re-established then (and by the client's automatic reconnection).
 */
class PlayBilling(context: Context, private val tracker: EntitlementTracker) : Billing, PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _price = MutableStateFlow<ProductPrice>(ProductPrice.Loading)
    override val price: StateFlow<ProductPrice> = _price.asStateFlow()
    override val entitlement: StateFlow<ProState> get() = tracker.state

    @Volatile private var product: ProductDetails? = null
    @Volatile private var connecting = false

    override fun refresh() {
        if (client.isReady) {
            query()
            return
        }
        if (connecting) return
        connecting = true
        if (product == null) _price.value = ProductPrice.Loading
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.ok) query() else _price.value = ProductPrice.Unavailable
            }

            // Nothing to do: the next refresh() reconnects. The cached entitlement stays.
            override fun onBillingServiceDisconnected() {
                connecting = false
            }
        })
    }

    private fun query() {
        val productParams = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(ProEntitlement.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(productParams)).build(),
        ) { result, details ->
            val found = details.productDetailsList.firstOrNull { it.productId == ProEntitlement.PRODUCT_ID }
            if (result.ok) product = found
            val formatted = found?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            _price.value = if (result.ok && formatted != null) ProductPrice.Available(formatted) else ProductPrice.Unavailable
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            // Only a successful answer is authoritative; otherwise the cache stays (offline, AC 16).
            if (result.ok) acknowledge(tracker.onQuery(purchases.map { it.toStore() }))
        }
    }

    override fun launchPurchase(activity: Activity) {
        val details = product
        if (details == null || !client.isReady) {
            refresh()
            return
        }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let(params::setOfferToken)
        client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build(),
        )
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                acknowledge(tracker.onUpdate(purchases.orEmpty().map { it.toStore() }))
            // Bought earlier (another device, before a reinstall): read the owned purchases.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> query()
            // USER_CANCELED and errors: nothing changes and no message (AC 12).
            else -> Unit
        }
    }

    /** A failed acknowledgement is retried at the next query (still unacknowledged then). */
    private fun acknowledge(purchases: List<StorePurchase>) {
        purchases.forEach { purchase ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build(),
            ) { }
        }
    }

    private val BillingResult.ok: Boolean get() = responseCode == BillingClient.BillingResponseCode.OK

    private fun Purchase.toStore() = StorePurchase(
        productIds = products,
        status = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseStatus.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseStatus.PENDING
            else -> PurchaseStatus.UNSPECIFIED
        },
        token = purchaseToken,
        acknowledged = isAcknowledged,
    )
}
