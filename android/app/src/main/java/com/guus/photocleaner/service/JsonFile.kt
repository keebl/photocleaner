package com.guus.photocleaner.service

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads and atomically writes one JSON document in app-private storage. */
internal class JsonFile<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
) {
    /** Decodes the file; a missing, unreadable or invalid file yields null. */
    fun read(): T? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    /** Writes to a temp file first, then renames it, so a crash never leaves half a file. */
    fun write(value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
