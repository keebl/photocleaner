package com.guus.photocleaner.service

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Remembers which items the user deliberately kept, so they never come back in review
 * views. Counterpart of [TrashStore]. Port of iOS `Services/KeepStore.swift`.
 */
class KeepStore(file: File) {
    private val storage = JsonFile(file, ListSerializer(String.serializer()))

    private val _ids = MutableStateFlow(storage.read()?.toSet() ?: emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun contains(id: String): Boolean = id in _ids.value

    @Synchronized
    fun keep(id: String) {
        if (id !in _ids.value) update(_ids.value + id)
    }

    @Synchronized
    fun unkeep(id: String) {
        if (id in _ids.value) update(_ids.value - id)
    }

    /** Clears all keep decisions; previously kept items return to review views. */
    @Synchronized
    fun reset() {
        if (_ids.value.isNotEmpty()) update(emptySet())
    }

    /** Drops ids of items that were deleted outside the app. */
    @Synchronized
    fun forget(ids: Collection<String>) {
        val remaining = _ids.value - ids.toSet()
        if (remaining.size != _ids.value.size) update(remaining)
    }

    private fun update(ids: Set<String>) {
        storage.write(ids.sorted())
        _ids.value = ids
    }
}
