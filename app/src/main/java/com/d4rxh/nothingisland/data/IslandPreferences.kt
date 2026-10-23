package com.d4rxh.nothingisland.data

import android.content.Context
import android.content.SharedPreferences

class IslandPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nothing_island_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ISLAND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ISLAND_ENABLED, value).apply()

    var xOffset: Int
        get() = prefs.getInt(KEY_X_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_X_OFFSET, value).apply()

    var yOffset: Int
        get() = prefs.getInt(KEY_Y_OFFSET, 18)
        set(value) = prefs.edit().putInt(KEY_Y_OFFSET, value).apply()

    var baseWidth: Int
        get() = prefs.getInt(KEY_BASE_WIDTH, 84)
        set(value) = prefs.edit().putInt(KEY_BASE_WIDTH, value).apply()

    var baseHeight: Int
        get() = prefs.getInt(KEY_BASE_HEIGHT, 34)
        set(value) = prefs.edit().putInt(KEY_BASE_HEIGHT, value).apply()

    var musicIslandEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MUSIC_ENABLED, value).apply()

    var chargingIslandEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CHARGING_ENABLED, value).apply()

    var notificationIslandEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, value).apply()

    companion object {
        private const val KEY_ISLAND_ENABLED = "pref_island_enabled"
        private const val KEY_X_OFFSET = "pref_x_offset"
        private const val KEY_Y_OFFSET = "pref_y_offset"
        private const val KEY_BASE_WIDTH = "pref_base_width"
        private const val KEY_BASE_HEIGHT = "pref_base_height"
        private const val KEY_MUSIC_ENABLED = "pref_music_enabled"
        private const val KEY_CHARGING_ENABLED = "pref_charging_enabled"
        private const val KEY_NOTIFICATION_ENABLED = "pref_notification_enabled"
        private const val KEY_HAPTICS_ENABLED = "pref_haptics_enabled"
    }
}
