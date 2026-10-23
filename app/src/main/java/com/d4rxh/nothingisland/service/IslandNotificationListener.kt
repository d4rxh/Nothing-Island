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
            startService(Intent(this, IslandOverlayService::class.java))
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

            // Ignore ongoing media notifications to avoid duplicate triggers with MediaSession
            if (title.isNotEmpty() && text.isNotEmpty() && !sbn.isOngoing) {
                val appName = try {
                    val pm = packageManager
                    val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    sbn.packageName
                }

                val appIcon: Drawable? = try {
                    packageManager.getApplicationIcon(sbn.packageName)
                } catch (e: Exception) {
                    null
                }

                IslandOverlayManager.onNotificationReceived(appName, title, text, appIcon)
            }
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error in onNotificationPosted: ${e.message}", e)
        }
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
