package com.d4rxh.nothingisland.service

import android.content.ComponentName
import android.content.Context
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
            updateMediaController(controllers.firstOrNull())
        } catch (e: Exception) {
            Log.e("NotificationListener", "Failed to register active sessions listener", e)
        }
    }

    private fun updateMediaController(controller: MediaController?) {
        currentController?.unregisterCallback(callback)
        currentController = controller
        IslandMediaManager.setActiveController(controller)

        if (controller != null) {
            controller.registerCallback(callback)
            dispatchMediaUpdate()
        }
    }

    private fun dispatchMediaUpdate() {
        val controller = currentController ?: return
        val metadata = controller.metadata
        val state = controller.playbackState

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        IslandOverlayService.instance?.onMediaStateChanged(title, artist, isPlaying, artwork)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isNotEmpty() && text.isNotEmpty() && !sbn.isOngoing) {
            val appName = try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                sbn.packageName
            }
            IslandOverlayService.instance?.onNotificationReceived(appName, title, text)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
            currentController?.unregisterCallback(callback)
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error on destroy", e)
        }
    }
}
