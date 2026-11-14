package com.guus.photocleaner.service

import android.content.Context
import androidx.core.content.edit
import com.guus.photocleaner.source.SourceKind
import com.guus.photocleaner.ui.MediaSettings

/** Small app preferences in SharedPreferences (spec: preferences in SharedPreferences). */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

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

    /** Active photo source: phone library or NAS (specs/android-nas.md). */
    var sourceKind: SourceKind
        get() = enumOrDefault(prefs.getString(KEY_SOURCE_KIND, null), SourceKind.DEVICE)
        set(value) = prefs.edit { putString(KEY_SOURCE_KIND, value.name) }

    var theme: AppTheme
        get() = enumOrDefault(prefs.getString(KEY_THEME, null), AppTheme.DARK)
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    var reminder: ReminderSettings
        get() {
            val defaults = ReminderSettings()
            return ReminderSettings(
                enabled = prefs.getBoolean(KEY_REMINDER_ENABLED, defaults.enabled),
                hour = prefs.getInt(KEY_REMINDER_HOUR, defaults.hour),
                minute = prefs.getInt(KEY_REMINDER_MINUTE, defaults.minute),
            )
        }
        set(value) = prefs.edit {
            putBoolean(KEY_REMINDER_ENABLED, value.enabled)
            putInt(KEY_REMINDER_HOUR, value.hour)
            putInt(KEY_REMINDER_MINUTE, value.minute)
        }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        const val FILE_NAME = "settings"
        private const val KEY_THEME = "appTheme"
        private const val KEY_SOURCE_KIND = "sourceKind"
        private const val KEY_REMINDER_ENABLED = "reminderEnabled"
        private const val KEY_REMINDER_HOUR = "reminderHour"
        private const val KEY_REMINDER_MINUTE = "reminderMinute"
        private const val KEY_MEDIA_TAB = "mediaTab"
        private const val KEY_BROWSE = "browse"
        private const val KEY_CATEGORY = "category"
        private const val KEY_OLDEST_FIRST = "sortOldFirst"
        private const val KEY_DID_SEE_SWIPE_HINT = "didSeeSwipeHint"
        private const val KEY_DID_ONBOARD = "didOnboard"
        private const val KEY_REQUESTED_ACCESS = "requestedMediaAccess"
    }
}
