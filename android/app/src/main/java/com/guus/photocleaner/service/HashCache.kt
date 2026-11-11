package com.guus.photocleaner.service

import java.io.File
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Persists perceptual hashes so a second scan finishes almost immediately. Port of iOS
 * `Services/HashCache.swift`: an entry is valid while the photo's modification date
 * matches (within one second); an edited photo is re-hashed.
 *
 * Thread-safe, because the scan runs off the main thread. Call [flush] to persist.
 */
class HashCache(file: File) {
    @Serializable
    private data class Entry(
        val hash: Long,
        /** Modification time in epoch millis; 0 when unknown. */
        val mod: Long,
    )

    private val storage = JsonFile(file, MapSerializer(String.serializer(), Entry.serializer()))
    private val entries: MutableMap<String, Entry> = storage.read()?.toMutableMap() ?: mutableMapOf()
    private var dirty = false

    @Synchronized
    fun hash(id: String, modifiedAtMillis: Long?): Long? {
        val entry = entries[id] ?: return null
        if (modifiedAtMillis != null && abs(modifiedAtMillis - entry.mod) > TOLERANCE_MILLIS) {
            return null
        }
        return entry.hash
    }

    @Synchronized
    fun set(id: String, hash: Long, modifiedAtMillis: Long?) {
        entries[id] = Entry(hash, modifiedAtMillis ?: 0L)
        dirty = true
    }

    /** Writes changes to disk, only when something changed since the last flush. */
    fun flush() {
        val snapshot = synchronized(this) {
            if (!dirty) return
            dirty = false
            entries.toMap()
        }
        storage.write(snapshot)
    }

    private companion object {
        const val TOLERANCE_MILLIS = 1_000L
    }
}
