package com.guus.photocleaner.service

import java.io.File
import kotlinx.serialization.Serializable

/**
 * The last known Pro entitlement, so Pro works offline and at start-up before the store
 * answers (specs/pro-unlock.md AC 16). App-private and excluded from backup like the other
 * stores; a missing or unreadable file means FREE.
 */
class EntitlementStore(file: File) {
    @Serializable
    private data class Snapshot(
        val state: ProState = ProState.FREE,
        val purchaseToken: String? = null,
    )

    private val storage = JsonFile(file, Snapshot.serializer())
    private var snapshot = storage.read() ?: Snapshot()

    val state: ProState get() = snapshot.state

    /** Token of the purchase that granted Pro; null when free or pending. */
    val purchaseToken: String? get() = snapshot.purchaseToken

    @Synchronized
    fun save(state: ProState, purchaseToken: String?) {
        val next = Snapshot(state, purchaseToken)
        if (next == snapshot) return
        storage.write(next)
        snapshot = next
    }
}
