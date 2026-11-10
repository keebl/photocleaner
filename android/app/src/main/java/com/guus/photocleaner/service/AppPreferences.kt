package com.guus.photocleaner.service

import android.content.Context
import androidx.core.content.edit
import com.guus.photocleaner.ui.MediaSettings

/** Small app preferences in SharedPreferences (spec: preferences in SharedPreferences). */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var didOnboard: Boolean
        get() = prefs.getBoolean(KEY_DID_ONBOARD, false)
        set(value) = prefs.edit { putBoolean(KEY_DID_ONBOARD, value) }

    /** Whether the media permission dialog was shown at least once. */
    var requestedMediaAccess: Boolean
        get() = prefs.getBoolean(KEY_REQUESTED_ACCESS, false)
        set(value) = prefs.edit { putBoolean(KEY_REQUESTED_ACCESS, value) }

    /** Whether the one-time swipe explanation over the first deck card was dismissed. */
    var didSeeSwipeHint: Boolean
        get() = prefs.getBoolean(KEY_DID_SEE_SWIPE_HINT, false)
        set(value) = prefs.edit { putBoolean(KEY_DID_SEE_SWIPE_HINT, value) }

    /** Media tab choices (kind, browse mode, category, order). */
    var mediaSettings: MediaSettings
        get() {
            val defaults = MediaSettings()
            return MediaSettings(
                tab = enumOrDefault(prefs.getString(KEY_MEDIA_TAB, null), defaults.tab),
                browse = enumOrDefault(prefs.getString(KEY_BROWSE, null), defaults.browse),
                category = enumOrDefault(prefs.getString(KEY_CATEGORY, null), defaults.category),
                oldestFirst = prefs.getBoolean(KEY_OLDEST_FIRST, defaults.oldestFirst),
            )
        }
        set(value) = prefs.edit {
            putString(KEY_MEDIA_TAB, value.tab.name)
            putString(KEY_BROWSE, value.browse.name)
            putString(KEY_CATEGORY, value.category.name)
            putBoolean(KEY_OLDEST_FIRST, value.oldestFirst)
        }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_MEDIA_TAB = "mediaTab"
        const val KEY_BROWSE = "browse"
        const val KEY_CATEGORY = "category"
        const val KEY_OLDEST_FIRST = "sortOldFirst"
        const val KEY_DID_SEE_SWIPE_HINT = "didSeeSwipeHint"
        const val KEY_DID_ONBOARD = "didOnboard"
        const val KEY_REQUESTED_ACCESS = "requestedMediaAccess"
    }
}
