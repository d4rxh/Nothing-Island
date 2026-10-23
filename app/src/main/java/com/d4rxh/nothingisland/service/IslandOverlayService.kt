package com.d4rxh.nothingisland.service

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.IBinder

class IslandOverlayService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        IslandOverlayManager.showOverlay(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        IslandOverlayManager.showOverlay(this)
        return START_STICKY
    }

    fun updateAppearance(
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        cornerRadius: Int,
        cutoutSize: Int
    ) {
        IslandOverlayManager.updateAppearance(xOffset, yOffset, width, height, cornerRadius, cutoutSize)
    }

    fun onMediaStateChanged(
        packageName: String,
        appName: String,
        title: String,
        artist: String,
        isPlaying: Boolean,
        artwork: Bitmap?,
        appIcon: Drawable?
    ) {
        IslandOverlayManager.onMediaStateChanged(
            packageName, appName, title, artist, isPlaying, artwork, appIcon
        )
    }

    fun onChargingChanged(isCharging: Boolean, batteryPercent: Int) {
        IslandOverlayManager.onChargingChanged(isCharging, batteryPercent)
    }

    fun onNotificationReceived(
        appName: String,
        title: String,
        content: String,
        appIcon: Drawable?
    ) {
        IslandOverlayManager.onNotificationReceived(appName, title, content, appIcon)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        var instance: IslandOverlayService? = null
            private set
    }
}
