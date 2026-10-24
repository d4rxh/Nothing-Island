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

    // --- dynamicSpot flagship features ---
    var notchAlignment: Int
        get() = prefs.getInt(KEY_NOTCH_ALIGNMENT, ALIGNMENT_CENTER)
        set(value) = prefs.edit().putInt(KEY_NOTCH_ALIGNMENT, value).apply()

    var isBlacklistEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLACKLIST_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BLACKLIST_ENABLED, value).apply()

    var blacklistedPackages: Set<String>
        get() = prefs.getStringSet(KEY_BLACKLISTED_PACKAGES, DEFAULT_BLACKLIST) ?: DEFAULT_BLACKLIST
        set(value) = prefs.edit().putStringSet(KEY_BLACKLISTED_PACKAGES, value).apply()

    var batteryAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_ALERTS_ENABLED, value).apply()

    var mediaSeekbarEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEDIA_SEEKBAR_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MEDIA_SEEKBAR_ENABLED, value).apply()

    var volumeHudEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_HUD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOLUME_HUD_ENABLED, value).apply()

    companion object {
        const val ALIGNMENT_CENTER = 0
        const val ALIGNMENT_LEFT = 1
        const val ALIGNMENT_RIGHT = 2

        private val DEFAULT_BLACKLIST = setOf(
            "com.google.android.youtube",
            "com.google.android.GoogleCamera",
            "com.android.camera",
            "com.dts.freefireth",
            "com.pubg.imobile",
            "com.tencent.ig"
        )

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
        private const val KEY_NOTCH_ALIGNMENT = "pref_notch_alignment"
        private const val KEY_BLACKLIST_ENABLED = "pref_blacklist_enabled"
        private const val KEY_BLACKLISTED_PACKAGES = "pref_blacklisted_packages"
        private const val KEY_BATTERY_ALERTS_ENABLED = "pref_battery_alerts_enabled"
        private const val KEY_MEDIA_SEEKBAR_ENABLED = "pref_media_seekbar_enabled"
        private const val KEY_VOLUME_HUD_ENABLED = "pref_volume_hud_enabled"
    }
}
