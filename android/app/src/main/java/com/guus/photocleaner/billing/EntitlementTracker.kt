package com.guus.photocleaner.billing

import com.guus.photocleaner.service.EntitlementStore
import com.guus.photocleaner.service.ProEntitlement
import com.guus.photocleaner.service.ProState
import com.guus.photocleaner.service.StorePurchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns store answers into the entitlement and keeps the cache in step. Shared by
 * [PlayBilling] and [FakeBilling], so the state transitions are tested once (AC 11–16).
 */
class EntitlementTracker(private val store: EntitlementStore) {
    private val _state = MutableStateFlow(store.state)
    val state: StateFlow<ProState> = _state.asStateFlow()

    /**
     * A full purchase query: the store's word is final, so a refunded or revoked purchase
     * (absent from the list) locks Pro again (AC 15). Returns the purchases to acknowledge.
     */
    @Synchronized
    fun onQuery(purchases: List<StorePurchase>): List<StorePurchase> {
        apply(ProEntitlement.resolve(purchases), ProEntitlement.proToken(purchases))
        return ProEntitlement.toAcknowledge(purchases)
    }

    /**
     * Purchases from the purchase dialog: only the ones just bought, so this never lowers
     * the entitlement (a pending second attempt does not lock an owned Pro).
     */
    @Synchronized
    fun onUpdate(purchases: List<StorePurchase>): List<StorePurchase> {
        val resolved = ProEntitlement.resolve(purchases)
        if (resolved.ordinal > _state.value.ordinal) apply(resolved, ProEntitlement.proToken(purchases))
        return ProEntitlement.toAcknowledge(purchases)
    }

    private fun apply(state: ProState, token: String?) {
        store.save(state, token)
        _state.value = state
    }
}
