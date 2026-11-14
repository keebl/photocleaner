package com.guus.photocleaner.source.nas

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The linked NAS folder (no password) in app-private SharedPreferences. */
class NasConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): NasConfig? {
        val text = prefs.getString(KEY_CONFIG, null) ?: return null
        return try {
            Json.decodeFromString(NasConfig.serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun save(config: NasConfig) = prefs.edit { putString(KEY_CONFIG, Json.encodeToString(NasConfig.serializer(), config)) }

    fun clear() = prefs.edit { remove(KEY_CONFIG) }

    private companion object {
        const val FILE_NAME = "nas"
        const val KEY_CONFIG = "config"
    }
}
