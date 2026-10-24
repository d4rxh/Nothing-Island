package com.d4rxh.nothingisland.service

import android.app.NotificationManager
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
import com.d4rxh.nothingisland.data.IslandPreferences

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
    private var activeRecorderPackage = ""
    private var activeClockPackage = ""

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

            val prefs = IslandPreferences(this)
            if (prefs.respectDnd && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
                if (filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                    // Respect Do Not Disturb (DND): skip popups
                    return
                }
            }

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

            val template = extras.getString("android.template", "")
            val isCallStyle = template.contains("CallStyle")
            val callType = extras.getInt("android.callType", -1)

            // 1. Check for Calls (Incoming or Ongoing Call from Dialer, Telecom, WhatsApp, Truecaller, Telegram, etc.)
            val isCall = isCallStyle ||
                    notification.category == android.app.Notification.CATEGORY_CALL ||
                    sbn.packageName.contains("dialer") || sbn.packageName.contains("telecom") ||
                    sbn.packageName.contains("phone") || sbn.packageName.contains("truecaller") ||
                    sbn.packageName.contains("incall") ||
                    actions?.any {
                        val t = it.title?.toString()?.lowercase() ?: ""
                        it.semanticAction == android.app.Notification.Action.SEMANTIC_ACTION_CALL ||
                        t.contains("answer") || t.contains("decline") || t.contains("reject") || t.contains("hang") ||
                        t.contains("accept") || t.contains("receive") || t.contains("pick") || t.contains("उत्तर")
                    } == true

            val callerCandidate = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence("android.title.big")?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    (extras.getParcelable(android.app.Notification.EXTRA_CALL_PERSON) as? android.app.Person)?.name?.toString()
                } else null)
                ?: title

            if (isCall && (callerCandidate.isNotEmpty() || text.isNotEmpty() || actions != null)) {
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

                // 1. Check native CallStyle extra
                var answerPendingIntent: PendingIntent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable("android.answerIntent", PendingIntent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable("android.answerIntent")
                }

                // 2. Check semanticAction or title in notification actions
                if (answerPendingIntent == null) {
                    val answerAction = actions?.firstOrNull {
                        it.title?.toString()?.lowercase()?.let { t ->
                            t.contains("answer") || t.contains("accept") || t.contains("receive") ||
                            t.contains("join") || t.contains("pick") || t.contains("उत्तर") || t.contains("हाँ")
                        } == true
                    }
                    answerPendingIntent = answerAction?.actionIntent
                }

                // 3. Decline/End intent
                var declinePendingIntent: PendingIntent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable("android.declineIntent", PendingIntent::class.java)
                        ?: extras.getParcelable("android.hangUpIntent", PendingIntent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable("android.declineIntent")
                        ?: extras.getParcelable("android.hangUpIntent")
                }

                if (declinePendingIntent == null) {
                    val declineAction = actions?.firstOrNull {
                        it.title?.toString()?.lowercase()?.let { t ->
                            t.contains("decline") || t.contains("reject") || t.contains("end") ||
                            t.contains("hang") || t.contains("dismiss") || t.contains("अस्वीकार")
                        } == true
                    }
                    declinePendingIntent = declineAction?.actionIntent ?: notification.contentIntent
                }

                val finalCaller = callerCandidate.ifEmpty { "Incoming Call" }

                val isIncoming = callType == 1 || answerPendingIntent != null || actions?.any {
                    it.title?.toString()?.lowercase()?.let { t ->
                        t.contains("answer") || t.contains("accept") || t.contains("receive") || t.contains("pick")
                    } == true
                } == true

                if (isIncoming) {
                    // Incoming call
                    IslandCallManager.onIncomingCall(
                        name = finalCaller,
                        number = text,
                        sourceAppName = appName,
                        sourceAppIcon = appIcon,
                        answer = answerPendingIntent,
                        decline = declinePendingIntent
                    )
                } else {
                    // Ongoing / Active call
                    IslandCallManager.onCallActive(
                        name = finalCaller,
                        number = text,
                        sourceAppName = appName,
                        sourceAppIcon = appIcon,
                        end = declinePendingIntent,
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

            // 3. Check for Voice Recorder (Nothing Sound Recorder, Google Recorder, Samsung Voice Note, etc.)
            val isRecorder = sbn.packageName.contains("recorder") ||
                    sbn.packageName.contains("voicenote") ||
                    sbn.packageName.contains("soundrecorder") ||
                    sbn.packageName.contains("audiorecorder") ||
                    title.contains("recording", ignoreCase = true) ||
                    text.contains("recording", ignoreCase = true) ||
                    (title.contains("record", ignoreCase = true) && !sbn.packageName.contains("screen"))

            if (isRecorder && (title.isNotEmpty() || text.isNotEmpty())) {
                val pm = packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    "RECORDER"
                }
                val appIcon: Drawable? = try {
                    pm.getApplicationIcon(sbn.packageName)
                } catch (e: Exception) {
                    null
                }

                activeRecorderPackage = sbn.packageName
                IslandOverlayManager.onRecorderNotification(
                    appName = appName,
                    title = title,
                    text = text,
                    appIcon = appIcon,
                    intent = notification.contentIntent
                )
                return
            }

            // 4. Check for External Stopwatch / Timer (Google Clock, Nothing Clock, Samsung Clock, etc.)
            val isClock = sbn.packageName.contains("clock") ||
                    sbn.packageName.contains("deskclock") ||
                    notification.category == "stopwatch" ||
                    notification.category == android.app.Notification.CATEGORY_ALARM ||
                    title.contains("stopwatch", ignoreCase = true) ||
                    title.contains("timer", ignoreCase = true) ||
                    text.contains("stopwatch", ignoreCase = true) ||
                    text.contains("timer", ignoreCase = true)

            if (isClock && (title.isNotEmpty() || text.isNotEmpty())) {
                val pm = packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    "CLOCK"
                }
                val appIcon: Drawable? = try {
                    pm.getApplicationIcon(sbn.packageName)
                } catch (e: Exception) {
                    null
                }

                activeClockPackage = sbn.packageName
                val isTimer = title.contains("timer", ignoreCase = true) || text.contains("timer", ignoreCase = true)
                IslandOverlayManager.onExternalClockNotification(
                    appName = appName,
                    title = title,
                    text = text,
                    isTimer = isTimer,
                    appIcon = appIcon,
                    intent = notification.contentIntent
                )
                return
            }

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

            // 5. Check if this is a live Cricket / Sports score notification BEFORE isOngoing check!
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

            // Ignore ongoing / foreground service sticky pins (unless call, navigation, recorder, timer, cricket handled above)
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
                    replyIntent = quickReplyPendingIntent,
                    actions = actions
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

            if (activeRecorderPackage.isNotEmpty() && sbn.packageName == activeRecorderPackage) {
                IslandOverlayManager.clearRecorderNotification()
                activeRecorderPackage = ""
                return
            }

            if (activeClockPackage.isNotEmpty() && sbn.packageName == activeClockPackage) {
                IslandOverlayManager.clearExternalClockNotification()
                activeClockPackage = ""
                return
            }

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

            // When user dismisses notification from shade, restore state only if it matches!
            IslandOverlayManager.onNotificationDismissed(sbn.packageName)
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error in onNotificationRemoved: ${e.message}", e)
        }
    }

    private fun isCricketOrSportsNotification(packageName: String, title: String, text: String): Boolean {
        val sportsPackages = setOf(
            "com.cricbuzz.android",
            "com.espncricinfo",
            "org.crex.app",
            "com.sportskeeda",
            "com.google.android.googlequicksearchbox"
        )
        val combined = "$title $text".lowercase()
        val cricketScoreRegex = Regex("""\b\d{1,3}/\d{0,2}\b""")

        if (sportsPackages.contains(packageName)) {
            // Check if it looks like a score or match
            val hasScoreIndicators = combined.contains(" vs ") || combined.contains(" v ") ||
                    cricketScoreRegex.containsMatchIn(combined) || combined.contains("ov") ||
                    combined.contains("target") || combined.contains("cricket") ||
                    combined.contains("score") || combined.contains("innings") || combined.contains("opt to")
            if (hasScoreIndicators) return true
        }

        // Generic pattern for sports notifications with cricket score pattern like "184/3" or "45/1" or "201-4"
        val hasCricketKeywords = combined.contains("cricket") ||
                combined.contains(" csk ") || combined.contains(" rcb ") || combined.contains(" mi ") ||
                combined.contains(" ind ") || combined.contains(" aus ") || combined.contains(" eng ") ||
                combined.contains(" pak ") || combined.contains(" sa ") || combined.contains(" nz ") ||
                combined.contains("ipl") || combined.contains("t20") || combined.contains("world cup") ||
                combined.contains("india") || combined.contains("australia")
        val hasScorePattern = cricketScoreRegex.containsMatchIn(combined) || combined.contains(" ov")

        return hasCricketKeywords && (hasScorePattern || combined.contains(" vs ") || combined.contains(" v "))
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
