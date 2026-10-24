package com.d4rxh.nothingisland.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class IslandNotificationListener : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateMediaController(controllers?.firstOrNull())
        }

    private var currentController: MediaController? = null
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            dispatchMediaUpdate()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            dispatchMediaUpdate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val componentName = ComponentName(this, IslandNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateMediaController(controllers?.firstOrNull())

            // Ensure Dynamic Island is ALWAYS ON SCREEN
            IslandOverlayManager.showOverlay(this)
            val serviceIntent = Intent(this, IslandOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Failed in onListenerConnected: ${e.message}", e)
        }
    }

    private fun updateMediaController(controller: MediaController?) {
        try {
            currentController?.unregisterCallback(callback)
            currentController = controller
            IslandMediaManager.setActiveController(controller)

            if (controller != null) {
                controller.registerCallback(callback, mainHandler)
                dispatchMediaUpdate()
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error updating media controller: ${e.message}", e)
        }
    }

    private fun dispatchMediaUpdate() {
        try {
            val controller = currentController ?: return
            val metadata = controller.metadata
            val state = controller.playbackState

            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""

            // Safe artwork extraction
            val rawArtwork: Bitmap? = try {
                metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            } catch (e: Exception) {
                null
            }

            // Extract App Logo & App Name (Spotify, YouTube Music, etc.)
            val sourcePackage = controller.packageName ?: ""
            val appIcon: Drawable? = try {
                if (sourcePackage.isNotEmpty()) packageManager.getApplicationIcon(sourcePackage) else null
            } catch (e: Exception) {
                null
            }

            val appName = try {
                if (sourcePackage.isNotEmpty()) {
                    val appInfo = packageManager.getApplicationInfo(sourcePackage, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } else {
                    "NOW PLAYING"
                }
            } catch (e: Exception) {
                "NOW PLAYING"
            }

            // Forward to Island Overlay
            IslandOverlayManager.onMediaStateChanged(
                packageName = sourcePackage,
                appName = appName,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                artwork = rawArtwork,
                appIcon = appIcon
            )
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error in dispatchMediaUpdate: ${e.message}", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        try {
            if (sbn == null || sbn.packageName == packageName) return

            val extras = sbn.notification.extras ?: return
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            if (title.isEmpty() && text.isEmpty()) return

            val pm = packageManager
            val appName = try {
                val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                sbn.packageName
            }

            val appIcon: Drawable? = try {
                pm.getApplicationIcon(sbn.packageName)
            } catch (e: Exception) {
                null
            }

            // Check if this is a live Cricket / Sports score notification
            if (isCricketOrSportsNotification(sbn.packageName, title, text)) {
                val scoreText = extractCricketScore(title, text)
                IslandOverlayManager.onCricketScoreReceived(
                    appName = appName,
                    matchTitle = title,
                    scoreText = scoreText,
                    appIcon = appIcon
                )
                return
            }

            // Standard Notification (ignore ongoing notifications like foreground service sticky pins)
            if (!sbn.isOngoing && title.isNotEmpty()) {
                IslandOverlayManager.onNotificationReceived(appName, title, text, appIcon)
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error in onNotificationPosted: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        try {
            if (sbn == null || sbn.packageName == packageName) return
            // When user dismisses notification from shade, restore music state immediately
            IslandOverlayManager.onNotificationDismissed()
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error in onNotificationRemoved: ${e.message}", e)
        }
    }

    private fun isCricketOrSportsNotification(packageName: String, title: String, text: String): Boolean {
        val sportsPackages = setOf(
            "com.cricbuzz.android",
            "com.google.android.googlequicksearchbox",
            "in.startv.hotstar",
            "com.jio.media.ondemand",
            "com.jio.cinema",
            "com.fancode",
            "com.espncricinfo",
            "org.crex.app",
            "com.sportskeeda",
            "com.onefootball.score"
        )
        val combined = "$title $text".lowercase()

        if (sportsPackages.contains(packageName)) {
            // Check if it looks like a score or match
            val hasScoreIndicators = combined.contains(" vs ") || combined.contains(" v ") ||
                    combined.contains("/") || combined.contains("ov") || combined.contains("run") ||
                    combined.contains("inn") || combined.contains("target")
            if (hasScoreIndicators) return true
        }

        // Generic pattern for sports notifications with cricket score pattern like "184/3" or "45/1"
        val hasCricketKeywords = combined.contains("cricket") || combined.contains(" vs ") ||
                combined.contains(" csk ") || combined.contains(" rcb ") || combined.contains(" mi ") ||
                combined.contains(" ind ") || combined.contains(" aus ") || combined.contains(" eng ")
        val hasScorePattern = Regex("""\d+/\d+|\d+-\d+""").containsMatchIn(combined)

        return hasCricketKeywords && hasScorePattern
    }

    private fun extractCricketScore(title: String, text: String): String {
        // Return score portion if available, e.g. "IND 184/3 (18.2 ov)"
        val scoreRegex = Regex("""([A-Z]{2,4}\s+\d+/\d+.*|\d+/\d+\s*\(\d+\.?\d*\s*ov.*)""", RegexOption.IGNORE_CASE)
        val match = scoreRegex.find(text) ?: scoreRegex.find(title)
        return match?.value?.trim() ?: if (text.isNotEmpty()) text else title
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
            currentController?.unregisterCallback(callback)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
