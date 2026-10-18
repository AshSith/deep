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
        set(value) = prefs.edit().putFloat(KEY_SCALE, value.coerceIn(0.4f, 3f)).apply()

    var overlayX: Int
        get() = prefs.getInt(KEY_X, -1)
        set(value) = prefs.edit().putInt(KEY_X, value).apply()

    var overlayY: Int
        get() = prefs.getInt(KEY_Y, -1)
        set(value) = prefs.edit().putInt(KEY_Y, value).apply()

    var overlayBrightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, 1f)
        set(value) = prefs.edit().putFloat(KEY_BRIGHTNESS, value.coerceIn(0.5f, 1.5f)).apply()

    var overlayFlipped: Boolean
        get() = prefs.getBoolean(KEY_FLIPPED, false)
        set(value) = prefs.edit().putBoolean(KEY_FLIPPED, value).apply()

    var tapResponds: Boolean
        get() = prefs.getBoolean(KEY_TAP_RESPONDS, true)
        set(value) = prefs.edit().putBoolean(KEY_TAP_RESPONDS, value).apply()

    var chatBackgroundColor: Int
        get() = prefs.getInt(KEY_CHAT_BG_COLOR, 0).let {
            if (it == 0xFF0B1020.toInt()) 0 else it
        }
        set(value) = prefs.edit().putInt(KEY_CHAT_BG_COLOR, value).apply()

    var themeColor: Int
        get() = prefs.getInt(KEY_THEME_COLOR, 0xFF8AB4FF.toInt())
        set(value) = prefs.edit().putInt(KEY_THEME_COLOR, value).apply()

    var chatBackgroundImage: String?
        get() = prefs.getString(KEY_CHAT_BG_IMAGE, null)
        set(value) = prefs.edit().putString(KEY_CHAT_BG_IMAGE, value).apply()

    var bubbleColor: Int
        get() = prefs.getInt(KEY_BUBBLE_COLOR, 0xF21B2438.toInt())
        set(value) = prefs.edit().putInt(KEY_BUBBLE_COLOR, value).apply()

    var bubbleTextColor: Int
        get() = prefs.getInt(KEY_BUBBLE_TEXT_COLOR, 0xFFEAF2FF.toInt())
        set(value) = prefs.edit().putInt(KEY_BUBBLE_TEXT_COLOR, value).apply()

    var hasSeenIntro: Boolean
        get() = prefs.getBoolean(KEY_INTRO, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRO, value).apply()

    private companion object {
        const val KEY_ACTIVE = "active_persona"
        const val KEY_OVERLAY = "overlay_enabled"
        const val KEY_SCALE = "overlay_scale"
        const val KEY_X = "overlay_x"
        const val KEY_Y = "overlay_y"
        const val KEY_BRIGHTNESS = "overlay_brightness"
        const val KEY_FLIPPED = "overlay_flipped"
        const val KEY_TAP_RESPONDS = "tap_responds"
        const val KEY_CHAT_BG_COLOR = "chat_bg_color"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_CHAT_BG_IMAGE = "chat_bg_image"
        const val KEY_BUBBLE_COLOR = "bubble_color"
        const val KEY_BUBBLE_TEXT_COLOR = "bubble_text_color"
        const val KEY_INTRO = "seen_intro"
    }
}
