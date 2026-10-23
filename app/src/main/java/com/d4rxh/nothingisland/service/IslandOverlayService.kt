package com.d4rxh.nothingisland.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.d4rxh.nothingisland.MainActivity
import com.d4rxh.nothingisland.R
import com.d4rxh.nothingisland.data.IslandPreferences
import com.d4rxh.nothingisland.helpers.NothingHapticHelper
import com.d4rxh.nothingisland.helpers.TypefaceHelper
import com.d4rxh.nothingisland.ui.DotMatrixEqualizerView

class IslandOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var prefs: IslandPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private var layoutParams: WindowManager.LayoutParams? = null

    // UI references
    private lateinit var islandCapsule: LinearLayout
    private lateinit var compactContainer: LinearLayout
    private lateinit var compactLeftIcon: ImageView
    private lateinit var compactLeftText: TextView
    private lateinit var centerCutoutSpacer: View
    private lateinit var compactRightText: TextView
    private lateinit var compactEqualizer: DotMatrixEqualizerView

    private lateinit var expandedContainer: LinearLayout
    private lateinit var expandedAppIcon: ImageView
    private lateinit var expandedSourceAppText: TextView
    private lateinit var expandedArtwork: ImageView
    private lateinit var expandedTitleText: TextView
    private lateinit var expandedArtistText: TextView
    private lateinit var expandedEqualizer: DotMatrixEqualizerView
    private lateinit var mediaControlsRow: LinearLayout
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var expandedNotificationBody: TextView

    enum class State {
        IDLE,
        MUSIC_COMPACT,
        CHARGING_COMPACT,
        NOTIFICATION_COMPACT,
        EXPANDED
    }

    private var currentState: State = State.IDLE
    private var previousState: State = State.IDLE

    // Media state cache
    private var isMusicPlaying = false
    private var currentTrackTitle = ""
    private var currentArtistName = ""
    private var currentArtwork: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = IslandPreferences(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundNotification()
        createOverlayView()
    }

    private fun startForegroundNotification() {
        val channelId = "nothing_island_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Nothing Island Foreground",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Runs the Nothing Island floating overlay"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("(NOT) ISLAND ACTIVE")
            .setContentText("Displaying Nothing Dynamic Punch-Hole")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        } else {
            startForeground(1001, notification)
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createOverlayView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_island_overlay, null)

        islandCapsule = overlayView.findViewById(R.id.islandCapsule)
        compactContainer = overlayView.findViewById(R.id.compactContainer)
        compactLeftIcon = overlayView.findViewById(R.id.compactLeftIcon)
        compactLeftText = overlayView.findViewById(R.id.compactLeftText)
        centerCutoutSpacer = overlayView.findViewById(R.id.centerCutoutSpacer)
        compactRightText = overlayView.findViewById(R.id.compactRightText)
        compactEqualizer = overlayView.findViewById(R.id.compactEqualizer)

        expandedContainer = overlayView.findViewById(R.id.expandedContainer)
        expandedAppIcon = overlayView.findViewById(R.id.expandedAppIcon)
        expandedSourceAppText = overlayView.findViewById(R.id.expandedSourceAppText)
        expandedArtwork = overlayView.findViewById(R.id.expandedArtwork)
        expandedTitleText = overlayView.findViewById(R.id.expandedTitleText)
        expandedArtistText = overlayView.findViewById(R.id.expandedArtistText)
        expandedEqualizer = overlayView.findViewById(R.id.expandedEqualizer)
        mediaControlsRow = overlayView.findViewById(R.id.mediaControlsRow)
        btnPrev = overlayView.findViewById(R.id.btnPrev)
        btnPlayPause = overlayView.findViewById(R.id.btnPlayPause)
        btnNext = overlayView.findViewById(R.id.btnNext)
        expandedNotificationBody = overlayView.findViewById(R.id.expandedNotificationBody)

        TypefaceHelper.applyNdot(
            compactLeftText,
            compactRightText,
            expandedSourceAppText,
            expandedTitleText
        )

        // Set spacer size to match punch hole
        centerCutoutSpacer.layoutParams = centerCutoutSpacer.layoutParams.apply {
            width = dpToPx(prefs.baseWidth)
            height = dpToPx(prefs.baseHeight)
        }

        setupInteractions()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dpToPx(prefs.xOffset)
            y = dpToPx(prefs.yOffset)
        }

        try {
            windowManager.addView(overlayView, layoutParams)
            updateState(State.IDLE)
        } catch (e: Exception) {
            Log.e("IslandService", "Could not add overlay view", e)
        }
    }

    private fun setupInteractions() {
        islandCapsule.setOnClickListener {
            if (prefs.hapticsEnabled) {
                NothingHapticHelper.vibrateTick(it)
            }
            if (currentState == State.EXPANDED) {
                collapseIsland()
            } else if (currentState != State.IDLE) {
                expandIsland()
            }
        }

        btnPlayPause.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(this)
            IslandMediaManager.togglePlayPause()
        }

        btnPrev.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            IslandMediaManager.skipPrevious()
        }

        btnNext.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            IslandMediaManager.skipNext()
        }
    }

    fun updatePosition(xOffset: Int, yOffset: Int, baseWidth: Int, baseHeight: Int) {
        layoutParams?.let { params ->
            params.x = dpToPx(xOffset)
            params.y = dpToPx(yOffset)
            windowManager.updateViewLayout(overlayView, params)
        }
        centerCutoutSpacer.layoutParams = centerCutoutSpacer.layoutParams.apply {
            width = dpToPx(baseWidth)
            height = dpToPx(baseHeight)
        }
    }

    fun onMediaStateChanged(title: String, artist: String, isPlaying: Boolean, artwork: Bitmap?) {
        if (!prefs.musicIslandEnabled) return

        currentTrackTitle = title
        currentArtistName = artist
        isMusicPlaying = isPlaying
        currentArtwork = artwork

        mainHandler.post {
            compactEqualizer.isPlaying = isPlaying
            expandedEqualizer.isPlaying = isPlaying
            btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

            if (artwork != null) {
                compactLeftIcon.setImageBitmap(artwork)
                expandedArtwork.setImageBitmap(artwork)
            } else {
                compactLeftIcon.setImageResource(R.drawable.ic_music)
                expandedArtwork.setImageResource(R.drawable.app_logo)
            }

            expandedTitleText.text = title.ifEmpty { "Playing Music" }
            expandedArtistText.text = artist.ifEmpty { "Nothing Audio" }
            compactRightText.text = title

            if (isPlaying || currentState == State.MUSIC_COMPACT) {
                if (currentState != State.EXPANDED) {
                    updateState(State.MUSIC_COMPACT)
                }
            } else if (!isPlaying && currentState == State.MUSIC_COMPACT) {
                mainHandler.postDelayed({
                    if (!isMusicPlaying && currentState == State.MUSIC_COMPACT) {
                        updateState(State.IDLE)
                    }
                }, 3000)
            }
        }
    }

    fun onChargingChanged(isCharging: Boolean, batteryPercent: Int) {
        if (!prefs.chargingIslandEnabled) return

        mainHandler.post {
            if (isCharging) {
                compactLeftIcon.setImageResource(R.drawable.ic_bolt)
                compactLeftIcon.visibility = View.VISIBLE
                compactRightText.text = "$batteryPercent%"
                compactRightText.visibility = View.VISIBLE

                updateState(State.CHARGING_COMPACT)

                // Auto collapse to previous state after 5 seconds
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.postDelayed({
                    if (isMusicPlaying) {
                        updateState(State.MUSIC_COMPACT)
                    } else {
                        updateState(State.IDLE)
                    }
                }, 5000)
            }
        }
    }

    fun onNotificationReceived(appName: String, title: String, text: String) {
        if (!prefs.notificationIslandEnabled) return

        mainHandler.post {
            compactLeftIcon.setImageResource(R.drawable.ic_bell)
            compactLeftIcon.visibility = View.VISIBLE
            compactRightText.text = title.take(15)
            compactRightText.visibility = View.VISIBLE

            expandedSourceAppText.text = appName.uppercase()
            expandedTitleText.text = title
            expandedArtistText.text = text
            mediaControlsRow.visibility = View.GONE
            expandedNotificationBody.visibility = View.VISIBLE
            expandedNotificationBody.text = text

            updateState(State.NOTIFICATION_COMPACT)

            // Auto dismiss notification banner after 4.5 seconds
            mainHandler.postDelayed({
                if (currentState == State.NOTIFICATION_COMPACT) {
                    if (isMusicPlaying) {
                        updateState(State.MUSIC_COMPACT)
                    } else {
                        updateState(State.IDLE)
                    }
                }
            }, 4500)
        }
    }

    private fun updateState(newState: State) {
        previousState = currentState
        currentState = newState

        TransitionManager.beginDelayedTransition(islandCapsule as ViewGroup, ChangeBounds().apply {
            duration = 240
            interpolator = OvershootInterpolator(0.8f)
        })

        when (newState) {
            State.IDLE -> {
                compactContainer.visibility = View.VISIBLE
                expandedContainer.visibility = View.GONE
                compactLeftIcon.visibility = View.GONE
                compactLeftText.visibility = View.GONE
                compactRightText.visibility = View.GONE
                compactEqualizer.visibility = View.GONE
            }
            State.MUSIC_COMPACT -> {
                compactContainer.visibility = View.VISIBLE
                expandedContainer.visibility = View.GONE
                compactLeftIcon.visibility = View.VISIBLE
                compactLeftText.visibility = View.GONE
                compactRightText.visibility = View.GONE
                compactEqualizer.visibility = View.VISIBLE
            }
            State.CHARGING_COMPACT -> {
                compactContainer.visibility = View.VISIBLE
                expandedContainer.visibility = View.GONE
                compactLeftIcon.visibility = View.VISIBLE
                compactLeftText.visibility = View.VISIBLE
                compactLeftText.text = "CHARGING"
                compactRightText.visibility = View.VISIBLE
                compactEqualizer.visibility = View.GONE
            }
            State.NOTIFICATION_COMPACT -> {
                compactContainer.visibility = View.VISIBLE
                expandedContainer.visibility = View.GONE
                compactLeftIcon.visibility = View.VISIBLE
                compactLeftText.visibility = View.GONE
                compactRightText.visibility = View.VISIBLE
                compactEqualizer.visibility = View.GONE
            }
            State.EXPANDED -> {
                compactContainer.visibility = View.GONE
                expandedContainer.visibility = View.VISIBLE
                if (isMusicPlaying) {
                    mediaControlsRow.visibility = View.VISIBLE
                    expandedNotificationBody.visibility = View.GONE
                }
            }
        }
    }

    private fun expandIsland() {
        updateState(State.EXPANDED)
    }

    private fun collapseIsland() {
        if (isMusicPlaying) {
            updateState(State.MUSIC_COMPACT)
        } else {
            updateState(State.IDLE)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            if (::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
            }
        } catch (e: Exception) {
            Log.e("IslandService", "Error removing overlay on destroy", e)
        }
    }

    companion object {
        var instance: IslandOverlayService? = null
            private set
    }
}
