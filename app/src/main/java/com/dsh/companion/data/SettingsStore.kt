package com.dsh.companion.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("companion", Context.MODE_PRIVATE)

    var activePersonaId: String?
        get() = prefs.getString(KEY_ACTIVE, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE, value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY, value).apply()

    var overlayScale: Float
        get() = prefs.getFloat(KEY_SCALE, 1f)
        set(value) = prefs.edit().putFloat(KEY_SCALE, value.coerceIn(0.7f, 1.6f)).apply()

    var overlayX: Int
        get() = prefs.getInt(KEY_X, -1)
        set(value) = prefs.edit().putInt(KEY_X, value).apply()

    var overlayY: Int
        get() = prefs.getInt(KEY_Y, -1)
        set(value) = prefs.edit().putInt(KEY_Y, value).apply()

    var hasSeenIntro: Boolean
        get() = prefs.getBoolean(KEY_INTRO, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRO, value).apply()

    private companion object {
        const val KEY_ACTIVE = "active_persona"
        const val KEY_OVERLAY = "overlay_enabled"
        const val KEY_SCALE = "overlay_scale"
        const val KEY_X = "overlay_x"
        const val KEY_Y = "overlay_y"
        const val KEY_INTRO = "seen_intro"
    }
}
