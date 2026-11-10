package com.guus.photocleaner.service

import android.content.Context
import androidx.core.content.edit

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

    private companion object {
        const val KEY_DID_SEE_SWIPE_HINT = "didSeeSwipeHint"
        const val KEY_DID_ONBOARD = "didOnboard"
        const val KEY_REQUESTED_ACCESS = "requestedMediaAccess"
    }
}
