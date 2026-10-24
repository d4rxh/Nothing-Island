package com.d4rxh.nothingisland.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.d4rxh.nothingisland.MainActivity
import com.d4rxh.nothingisland.R

class IslandOverlayService : Service() {

    private val CHANNEL_ID = "nothing_island_service_channel"
    private val NOTIFICATION_ID = 1001

    private var telephonyReceiver: android.content.BroadcastReceiver? = null
    private var volumeObserver: android.database.ContentObserver? = null
    private var lastVolume: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundSafely()
        IslandOverlayManager.registerBatteryReceiver(this)
        registerTelephonyReceiver()
        registerVolumeObserver()
        IslandOverlayManager.showOverlay(this)
    }

    private fun registerVolumeObserver() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            lastVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)

            volumeObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    val prefs = com.d4rxh.nothingisland.data.IslandPreferences(this@IslandOverlayService)
                    if (!prefs.volumeHudEnabled) return

                    val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    if (currentVol != lastVolume) {
                        lastVolume = currentVol
                        val percent = if (maxVol > 0) (currentVol * 100) / maxVol else 0
                        val isMuted = currentVol == 0
                        IslandOverlayManager.onVolumeChanged(percent, isMuted)
                    }
                }
            }

            contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )
            Log.i("IslandOverlayService", "Volume observer registered successfully")
        } catch (e: Exception) {
            Log.w("IslandOverlayService", "Could not register volume observer: ${e.message}")
        }
    }

    private fun registerTelephonyReceiver() {
        try {
            telephonyReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || intent.action != android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
                    val state = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE) ?: return
                    val incomingNumber = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

                    when (state) {
                        android.telephony.TelephonyManager.EXTRA_STATE_RINGING -> {
                            if (IslandCallManager.currentState != IslandCallManager.CallState.INCOMING) {
                                val name = if (incomingNumber.isNotEmpty()) incomingNumber else "Incoming Call"
                                IslandCallManager.onIncomingCall(
                                    name = name,
                                    number = incomingNumber,
                                    sourceAppName = "PHONE"
                                )
                            }
                        }
                        android.telephony.TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                            if (IslandCallManager.currentState != IslandCallManager.CallState.ACTIVE) {
                                IslandCallManager.onCallActive(
                                    name = "Active Call",
                                    number = incomingNumber,
                                    sourceAppName = "PHONE"
                                )
                            }
                        }
                        android.telephony.TelephonyManager.EXTRA_STATE_IDLE -> {
                            IslandCallManager.onCallEnded()
                        }
                    }
                }
            }
            val filter = android.content.IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            registerReceiver(telephonyReceiver, filter)
            Log.i("IslandOverlayService", "Telephony receiver registered successfully")
        } catch (e: Exception) {
            Log.w("IslandOverlayService", "Could not register telephony receiver: ${e.message}")
        }
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

    fun onChargingChanged(isCharging: Boolean, batteryPercent: Int, isFirstConnect: Boolean = false) {
        IslandOverlayManager.onChargingChanged(isCharging, batteryPercent, isFirstConnect)
    }

    fun onNotificationReceived(
        appName: String,
        title: String,
        content: String,
        appIcon: Drawable?,
        packageName: String = "",
        contentIntent: PendingIntent? = null
    ) {
        IslandOverlayManager.onNotificationReceived(appName, title, content, appIcon, packageName, contentIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            telephonyReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignore
        }
        try {
            volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
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
