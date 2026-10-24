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
        get() = prefs.getInt(KEY_Y_OFFSET, 14)
        set(value) = prefs.edit().putInt(KEY_Y_OFFSET, value).apply()

    var islandWidth: Int
        get() = prefs.getInt(KEY_ISLAND_WIDTH, 110)
        set(value) = prefs.edit().putInt(KEY_ISLAND_WIDTH, value).apply()

    var islandHeight: Int
        get() = prefs.getInt(KEY_ISLAND_HEIGHT, 36)
        set(value) = prefs.edit().putInt(KEY_ISLAND_HEIGHT, value).apply()

    var cornerRadius: Int
        get() = prefs.getInt(KEY_CORNER_RADIUS, 20)
        set(value) = prefs.edit().putInt(KEY_CORNER_RADIUS, value).apply()

    var cutoutSize: Int
        get() = prefs.getInt(KEY_CUTOUT_SIZE, 28)
        set(value) = prefs.edit().putInt(KEY_CUTOUT_SIZE, value).apply()

    // Backward compatibility aliases
    var baseWidth: Int
        get() = islandWidth
        set(value) { islandWidth = value }

    var baseHeight: Int
        get() = islandHeight
        set(value) { islandHeight = value }

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

    var attachToTop: Boolean
        get() = prefs.getBoolean(KEY_ATTACH_TO_TOP, false)
        set(value) = prefs.edit().putBoolean(KEY_ATTACH_TO_TOP, value).apply()

    var hideInLandscape: Boolean
        get() = prefs.getBoolean(KEY_HIDE_IN_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_IN_LANDSCAPE, value).apply()

    companion object {
        private const val KEY_ISLAND_ENABLED = "pref_island_enabled"
        private const val KEY_X_OFFSET = "pref_x_offset"
        private const val KEY_Y_OFFSET = "pref_y_offset"
        private const val KEY_ISLAND_WIDTH = "pref_island_width"
        private const val KEY_ISLAND_HEIGHT = "pref_island_height"
        private const val KEY_CORNER_RADIUS = "pref_corner_radius"
        private const val KEY_CUTOUT_SIZE = "pref_cutout_size"
        private const val KEY_MUSIC_ENABLED = "pref_music_enabled"
        private const val KEY_CHARGING_ENABLED = "pref_charging_enabled"
        private const val KEY_NOTIFICATION_ENABLED = "pref_notification_enabled"
        private const val KEY_HAPTICS_ENABLED = "pref_haptics_enabled"
        private const val KEY_ATTACH_TO_TOP = "pref_attach_to_top"
        private const val KEY_HIDE_IN_LANDSCAPE = "pref_hide_in_landscape"
    }
}
