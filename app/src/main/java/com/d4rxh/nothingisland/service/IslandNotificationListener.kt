package com.d4rxh.nothingisland.service

import android.app.PendingIntent
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
            val active = controllers?.firstOrNull { c ->
                val state = c.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            } ?: controllers?.firstOrNull()
            updateMediaController(active)
        }

    private var currentController: MediaController? = null
    private val cachedThumbnails = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private var activeCallPackage = ""
    private var activeNavigationPackage = ""

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            dispatchMediaUpdate()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            dispatchMediaUpdate()
        }

        override fun onSessionDestroyed() {
            IslandOverlayManager.clearMediaPlayback()
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
            } else {
                IslandOverlayManager.clearMediaPlayback()
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error updating media controller: ${e.message}", e)
        }
    }

    private fun dispatchMediaUpdate() {
        try {
            val controller = currentController
            if (controller == null) {
                IslandOverlayManager.clearMediaPlayback()
                return
            }

            val metadata = controller.metadata
            val state = controller.playbackState

            if (state == null || state.state == PlaybackState.STATE_NONE || state.state == PlaybackState.STATE_STOPPED) {
                IslandOverlayManager.clearMediaPlayback()
                return
            }

            val isPlaying = state.state == PlaybackState.STATE_PLAYING
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""

            if (title.isEmpty() && !isPlaying) {
                IslandOverlayManager.clearMediaPlayback()
                return
            }

            // Safe artwork extraction (with notification thumbnail fallback for YouTube & players)
            val rawArtwork: Bitmap? = try {
                metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    ?: cachedThumbnails[controller.packageName ?: ""]
            } catch (e: Exception) {
                cachedThumbnails[controller.packageName ?: ""]
            }

            // Extract App Logo & App Name (Spotify, YouTube Music, etc.)
            val sourcePackage = controller.packageName ?: ""
            val appIcon: Drawable? = try {
                if (sourcePackage.isNotEmpty()) packageManager.getApplicationIcon(sourcePackage) else null
            } catch (e: Exception) {
                null
            }

            val appName = try {
                if (sourcePackage.contains("youtube")) {
                    "YOUTUBE"
                } else if (sourcePackage.isNotEmpty()) {
                    val appInfo = packageManager.getApplicationInfo(sourcePackage, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } else {
                    "NOW PLAYING"
                }
            } catch (e: Exception) {
                if (sourcePackage.contains("youtube")) "YOUTUBE" else "NOW PLAYING"
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

            // Ignore system notifications spam & noisy background updates
            val ignoredPackages = setOf(
                "android",
                "com.android.systemui",
                "com.google.android.gms",
                "com.google.android.googlequicksearchbox",
                "com.android.vending",
                "com.google.android.apps.restore",
                "com.android.providers.downloads",
                "com.nothing.heatservice",
                "com.nothing.systemupdate"
            )
            if (ignoredPackages.contains(sbn.packageName)) return

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            // Cache media thumbnail for YouTube and video/audio players
            if (sbn.packageName.contains("youtube") || sbn.packageName.contains("music") || sbn.packageName.contains("spotify") || sbn.packageName.contains("video")) {
                val largeIcon = notification.getLargeIcon()
                if (largeIcon != null) {
                    try {
                        val drawable = largeIcon.loadDrawable(this)
                        if (drawable is android.graphics.drawable.BitmapDrawable) {
                            cachedThumbnails[sbn.packageName] = drawable.bitmap
                        } else if (drawable != null) {
                            val bmp = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            cachedThumbnails[sbn.packageName] = bmp
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val actions = notification.actions

            // 1. Check for Calls (Incoming or Ongoing Call)
            val isCall = notification.category == android.app.Notification.CATEGORY_CALL ||
                    sbn.packageName.contains("dialer") || sbn.packageName.contains("telecom") ||
                    sbn.packageName.contains("phone") ||
                    actions?.any {
                        val t = it.title?.toString()?.lowercase() ?: ""
                        t.contains("answer") || t.contains("decline") || t.contains("reject") || t.contains("hang")
                    } == true

            if (isCall && (title.isNotEmpty() || text.isNotEmpty())) {
                val pm = packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    "CALL"
                }
                val appIcon: Drawable? = try {
                    pm.getApplicationIcon(sbn.packageName)
                } catch (e: Exception) {
                    null
                }

                val answerAction = actions?.firstOrNull { it.title?.toString()?.contains("answer", ignoreCase = true) == true }
                val declineAction = actions?.firstOrNull {
                    val t = it.title?.toString()?.lowercase() ?: ""
                    t.contains("decline") || t.contains("reject") || t.contains("end") || t.contains("hang")
                }

                if (answerAction != null) {
                    // Incoming call
                    IslandCallManager.onIncomingCall(
                        name = title.ifEmpty { "Incoming Call" },
                        number = text,
                        sourceAppName = appName,
                        sourceAppIcon = appIcon,
                        answer = answerAction.actionIntent,
                        decline = declineAction?.actionIntent ?: notification.contentIntent
                    )
                } else {
                    // Ongoing / Active call
                    IslandCallManager.onCallActive(
                        name = title.ifEmpty { "Active Call" },
                        number = text,
                        sourceAppName = appName,
                        sourceAppIcon = appIcon,
                        end = declineAction?.actionIntent ?: notification.contentIntent,
                        startTimeOverride = if (notification.`when` > 0L) notification.`when` else 0L
                    )
                }
                activeCallPackage = sbn.packageName
                return
            }

            // 2. Check for Navigation (Google Maps)
            val isNavigation = sbn.packageName == "com.google.android.apps.maps" ||
                    notification.category == android.app.Notification.CATEGORY_NAVIGATION

            if (isNavigation && (title.isNotEmpty() || text.isNotEmpty())) {
                val pm = packageManager
                val appIcon: Drawable? = try {
                    pm.getApplicationIcon(sbn.packageName)
                } catch (e: Exception) {
                    null
                }

                NavigationManager.onNavigationNotification(
                    title = title,
                    text = text,
                    subText = extras.getCharSequence("android.subText")?.toString() ?: "",
                    icon = appIcon,
                    intent = notification.contentIntent
                )
                activeNavigationPackage = sbn.packageName
                return
            }

            // Ignore ongoing / foreground service sticky pins (unless call or navigation above)
            if (sbn.isOngoing) return

            // Ignore progress bars (e.g. downloads, file transfers)
            val maxProgress = extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0)
            if (maxProgress > 0) return

            // Ignore group summaries with no direct text
            if ((notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0 &&
                !extras.containsKey("android.text") && !extras.containsKey("android.title")) {
                return
            }

            // If notification is from the currently active media player, ignore so music isn't interrupted
            val currentMediaPkg = IslandOverlayManager.getCurrentMediaPackage()
            if (currentMediaPkg.isNotEmpty() && sbn.packageName == currentMediaPkg) return

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

            // Extract native PendingIntent for direct, 100% reliable 1-tap app launch!
            val contentIntent: PendingIntent? = notification.contentIntent

            // Extract Quick Reply RemoteInput & action intent if messaging app (WhatsApp, Telegram, SMS)
            var quickReplyRemoteInput: android.app.RemoteInput? = null
            var quickReplyPendingIntent: PendingIntent? = null

            actions?.forEach { act ->
                val rInputs = act.remoteInputs
                if (rInputs != null && rInputs.isNotEmpty()) {
                    quickReplyRemoteInput = rInputs[0]
                    quickReplyPendingIntent = act.actionIntent
                }
            }

            // Standard Notification
            if (title.isNotEmpty() || text.isNotEmpty()) {
                IslandOverlayManager.onNotificationReceived(
                    appName = appName,
                    title = title,
                    content = text,
                    appIcon = appIcon,
                    packageName = sbn.packageName,
                    contentIntent = contentIntent,
                    remoteInput = quickReplyRemoteInput,
                    replyIntent = quickReplyPendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error in onNotificationPosted: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        try {
            if (sbn == null || sbn.packageName == packageName) return

            if (activeCallPackage.isNotEmpty() && sbn.packageName == activeCallPackage) {
                IslandCallManager.onCallEnded()
                activeCallPackage = ""
                return
            }

            if (activeNavigationPackage.isNotEmpty() && sbn.packageName == activeNavigationPackage) {
                NavigationManager.clearNavigation()
                activeNavigationPackage = ""
                return
            }

            val currentMediaPkg = IslandOverlayManager.getCurrentMediaPackage()
            if (currentMediaPkg.isNotEmpty() && sbn.packageName == currentMediaPkg) {
                // Active media playback notification was dismissed from notification panel!
                IslandOverlayManager.clearMediaPlayback()
                return
            }

            // When user dismisses notification from shade, restore state immediately
            IslandOverlayManager.onNotificationDismissed()
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error in onNotificationRemoved: ${e.message}", e)
        }
    }

    private fun isCricketOrSportsNotification(packageName: String, title: String, text: String): Boolean {
        val sportsPackages = setOf(
            "com.cricbuzz.android",
            "com.espncricinfo",
            "org.crex.app",
            "com.sportskeeda"
        )
        val combined = "$title $text".lowercase()
        val cricketScoreRegex = Regex("""\b\d{1,3}/\d{1,2}\b""")

        if (sportsPackages.contains(packageName)) {
            // Check if it looks like a score or match
            val hasScoreIndicators = combined.contains(" vs ") || combined.contains(" v ") ||
                    cricketScoreRegex.containsMatchIn(combined) || combined.contains("ov") || combined.contains("target")
            if (hasScoreIndicators) return true
        }

        // Generic pattern for sports notifications with cricket score pattern like "184/3" or "45/1"
        val hasCricketKeywords = combined.contains("cricket") ||
                combined.contains(" csk ") || combined.contains(" rcb ") || combined.contains(" mi ") ||
                combined.contains(" ind ") || combined.contains(" aus ") || combined.contains(" eng ")
        val hasScorePattern = cricketScoreRegex.containsMatchIn(combined)

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
