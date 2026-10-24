package com.d4rxh.nothingisland.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.d4rxh.nothingisland.MainActivity
import com.d4rxh.nothingisland.R
import com.d4rxh.nothingisland.ui.LockscreenOverlayActivity

class IslandOverlayService : Service() {

    private val CHANNEL_ID = "nothing_island_service_channel"
    private val NOTIFICATION_ID = 1001

    private val lockscreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    try {
                        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                        if (km.isKeyguardLocked) {
                            val lockIntent = Intent(context, LockscreenOverlayActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                                )
                            }
                            context.startActivity(lockIntent)
                        }
                    } catch (e: Exception) {
                        Log.e("IslandOverlayService", "Error launching lockscreen overlay: ${e.message}")
                    }
                }
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT -> {
                    LockscreenOverlayActivity.dismiss()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundSafely()
        IslandOverlayManager.showOverlay(this)

        // Register lockscreen observer
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(lockscreenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        IslandOverlayManager.showOverlay(this)
        return START_STICKY
    }

    private fun startForegroundSafely() {
        try {
            createNotificationChannel()

            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_island)
                .setContentTitle("(NOT) ISLAND ACTIVE")
                .setContentText("Always-on punch-hole dynamic overlay")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i("IslandOverlayService", "startForeground executed successfully with specialUse")
        } catch (e: Exception) {
            Log.e("IslandOverlayService", "startForeground error: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "(NOT) Island Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Nothing Island active 24/7 around camera punch-hole"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
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
        try {
            unregisterReceiver(lockscreenReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        instance = null
    }

    companion object {
        var instance: IslandOverlayService? = null
            private set
    }
}
