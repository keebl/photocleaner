package com.guus.photocleaner.service

/** Whether the user owns PhotoCleaner Pro (specs/pro-unlock.md). */
enum class ProState { FREE, PENDING, PRO }

/** Purchase state as the store reports it, independent of the Play Billing classes. */
enum class PurchaseStatus { PURCHASED, PENDING, UNSPECIFIED }

/** One purchase from the store, reduced to what the entitlement needs. */
data class StorePurchase(
    val productIds: List<String>,
    val status: PurchaseStatus,
    /** Play's purchase token; needed to acknowledge the purchase. */
    val token: String,
    val acknowledged: Boolean,
)

/** Pure rules from a store purchase list to the Pro entitlement. */
object ProEntitlement {
    /** The one-time, non-consumable product in Play Console. */
    const val PRODUCT_ID = "pro_lifetime"

    /**
     * PRO when the product is purchased, PENDING while a payment is still open, FREE
     * otherwise: a refunded or revoked purchase is simply absent from the list.
     */
    fun resolve(purchases: List<StorePurchase>): ProState {
        val ours = purchases.filter { PRODUCT_ID in it.productIds }
        return when {
            ours.any { it.status == PurchaseStatus.PURCHASED } -> ProState.PRO
            ours.any { it.status == PurchaseStatus.PENDING } -> ProState.PENDING
            else -> ProState.FREE
        }
    }

    /** Purchased but not yet acknowledged: Play refunds these after three days. */
    fun toAcknowledge(purchases: List<StorePurchase>): List<StorePurchase> =
        purchases.filter { PRODUCT_ID in it.productIds && it.status == PurchaseStatus.PURCHASED && !it.acknowledged }

    /** Token of the purchase that grants Pro, for the cache. */
    fun proToken(purchases: List<StorePurchase>): String? =
        purchases.firstOrNull { PRODUCT_ID in it.productIds && it.status == PurchaseStatus.PURCHASED }?.token
}
