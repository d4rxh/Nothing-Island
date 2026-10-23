package com.d4rxh.nothingisland.service

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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
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
    private var overlayView: View? = null
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

        startForegroundNotificationSafely()
        initOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null || overlayView?.isAttachedToWindow != true) {
            initOverlay()
        }
        return START_STICKY
    }

    private fun startForegroundNotificationSafely() {
        try {
            val channelId = "nothing_island_service_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "(NOT) Island Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Dynamic punch-hole overlay service"
                    setShowBadge(false)
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification_island)
                .setContentTitle("(NOT) ISLAND ACTIVE")
                .setContentText("Dynamic punch-hole status overlay")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1001,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            Log.e("IslandService", "Could not start foreground notification: ${e.message}", e)
        }
    }

    private fun initOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("IslandService", "Overlay permission not granted; cannot create overlay view")
            return
        }

        try {
            createOverlayView()
        } catch (e: Exception) {
            Log.e("IslandService", "Fatal error creating overlay view", e)
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createOverlayView() {
        try {
            // Clean up any existing view first
            if (overlayView != null) {
                try {
                    if (overlayView?.isAttachedToWindow == true) {
                        windowManager.removeViewImmediate(overlayView)
                    }
                } catch (e: Exception) {
                    Log.w("IslandService", "Old overlay cleanup error: ${e.message}")
                }
                overlayView = null
            }

            val view = LayoutInflater.from(this).inflate(R.layout.layout_island_overlay, null)
            overlayView = view

            islandCapsule = view.findViewById(R.id.islandCapsule)
            compactContainer = view.findViewById(R.id.compactContainer)
            compactLeftIcon = view.findViewById(R.id.compactLeftIcon)
            compactLeftText = view.findViewById(R.id.compactLeftText)
            centerCutoutSpacer = view.findViewById(R.id.centerCutoutSpacer)
            compactRightText = view.findViewById(R.id.compactRightText)
            compactEqualizer = view.findViewById(R.id.compactEqualizer)

            expandedContainer = view.findViewById(R.id.expandedContainer)
            expandedAppIcon = view.findViewById(R.id.expandedAppIcon)
            expandedSourceAppText = view.findViewById(R.id.expandedSourceAppText)
            expandedArtwork = view.findViewById(R.id.expandedArtwork)
            expandedTitleText = view.findViewById(R.id.expandedTitleText)
            expandedArtistText = view.findViewById(R.id.expandedArtistText)
            expandedEqualizer = view.findViewById(R.id.expandedEqualizer)
            mediaControlsRow = view.findViewById(R.id.mediaControlsRow)
            btnPrev = view.findViewById(R.id.btnPrev)
            btnPlayPause = view.findViewById(R.id.btnPlayPause)
            btnNext = view.findViewById(R.id.btnNext)
            expandedNotificationBody = view.findViewById(R.id.expandedNotificationBody)

            TypefaceHelper.applyNdot(
                compactLeftText,
                compactRightText,
                expandedSourceAppText,
                expandedTitleText
            )

            applyCustomStyling()
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
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = dpToPx(prefs.xOffset)
                y = dpToPx(prefs.yOffset)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            windowManager.addView(view, layoutParams)
            applyStateVisibilities(State.IDLE)
        } catch (e: Exception) {
            Log.e("IslandService", "Could not add overlay view to WindowManager", e)
        }
    }

    private fun applyCustomStyling() {
        try {
            val radiusPx = dpToPx(prefs.cornerRadius).toFloat()
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                setStroke(dpToPx(1.2f), Color.parseColor("#333333"))
                cornerRadius = radiusPx
            }
            islandCapsule.background = bgDrawable

            compactContainer.layoutParams = compactContainer.layoutParams.apply {
                height = dpToPx(prefs.islandHeight)
            }
            islandCapsule.minimumWidth = dpToPx(prefs.islandWidth)

            centerCutoutSpacer.layoutParams = centerCutoutSpacer.layoutParams.apply {
                width = dpToPx(prefs.cutoutSize)
                height = dpToPx(prefs.cutoutSize)
            }
        } catch (e: Exception) {
            Log.e("IslandService", "Error in applyCustomStyling: ${e.message}")
        }
    }

    private fun setupInteractions() {
        islandCapsule.setOnClickListener {
            if (prefs.hapticsEnabled) {
                NothingHapticHelper.vibrateTick(it)
            }
            if (currentState == State.EXPANDED) {
                collapseIsland()
            } else {
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

    fun updateAppearance(
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        cornerRadius: Int,
        cutoutSize: Int
    ) {
        val view = overlayView ?: return
        if (!view.isAttachedToWindow) return

        layoutParams?.let { params ->
            params.x = dpToPx(xOffset)
            params.y = dpToPx(yOffset)
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w("IslandService", "Error updating layout params", e)
            }
        }

        applyCustomStyling()
    }

    fun onMediaStateChanged(title: String, artist: String, isPlaying: Boolean, artwork: Bitmap?) {
        if (!prefs.musicIslandEnabled) return

        currentTrackTitle = title
        currentArtistName = artist
        isMusicPlaying = isPlaying
        currentArtwork = artwork

        mainHandler.post {
            if (title.isNotEmpty() && isPlaying) {
                compactLeftIcon.setImageResource(R.drawable.ic_music)
                compactEqualizer.isPlaying = true

                expandedTitleText.text = title
                expandedArtistText.text = artist
                expandedSourceAppText.text = "NOW PLAYING"
                expandedAppIcon.setImageResource(R.drawable.ic_music)
                btnPlayPause.setImageResource(R.drawable.ic_pause)
                expandedEqualizer.isPlaying = true

                if (artwork != null) {
                    compactLeftIcon.setImageBitmap(artwork)
                    expandedArtwork.setImageBitmap(artwork)
                    expandedArtwork.visibility = View.VISIBLE
                } else {
                    compactLeftIcon.setImageResource(R.drawable.ic_music)
                    expandedArtwork.visibility = View.GONE
                }

                if (currentState != State.EXPANDED) {
                    updateState(State.MUSIC_COMPACT)
                }
            } else if (!isPlaying && currentState == State.MUSIC_COMPACT) {
                compactEqualizer.isPlaying = false
                expandedEqualizer.isPlaying = false
                btnPlayPause.setImageResource(R.drawable.ic_play)
                updateState(State.IDLE)
            }
        }
    }

    fun onChargingChanged(isCharging: Boolean, batteryPercent: Int) {
        if (!prefs.chargingIslandEnabled) return

        mainHandler.post {
            if (isCharging) {
                compactLeftIcon.setImageResource(R.drawable.ic_bolt)
                compactRightText.text = "$batteryPercent%"
                compactLeftText.text = "CHARGING"

                expandedTitleText.text = "NOTHING FAST CHARGE"
                expandedArtistText.text = "Battery level at $batteryPercent%"
                expandedSourceAppText.text = "GLYPH CHARGING"
                expandedAppIcon.setImageResource(R.drawable.ic_bolt)
                mediaControlsRow.visibility = View.GONE

                updateState(State.CHARGING_COMPACT)

                mainHandler.postDelayed({
                    if (currentState == State.CHARGING_COMPACT) {
                        if (isMusicPlaying) {
                            updateState(State.MUSIC_COMPACT)
                        } else {
                            updateState(State.IDLE)
                        }
                    }
                }, 5000)
            } else {
                if (currentState == State.CHARGING_COMPACT) {
                    if (isMusicPlaying) {
                        updateState(State.MUSIC_COMPACT)
                    } else {
                        updateState(State.IDLE)
                    }
                }
            }
        }
    }

    fun onNotificationReceived(appName: String, title: String, content: String) {
        if (!prefs.notificationIslandEnabled) return

        mainHandler.post {
            compactLeftIcon.setImageResource(R.drawable.ic_bell)
            compactRightText.text = title

            expandedSourceAppText.text = appName.uppercase()
            expandedTitleText.text = title
            expandedArtistText.text = content
            expandedNotificationBody.text = content
            expandedNotificationBody.visibility = View.VISIBLE
            mediaControlsRow.visibility = View.GONE
            expandedAppIcon.setImageResource(R.drawable.ic_bell)

            updateState(State.NOTIFICATION_COMPACT)

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
        currentState = newState

        val view = overlayView
        if (view != null && view.isAttachedToWindow) {
            try {
                TransitionManager.beginDelayedTransition(
                    islandCapsule as ViewGroup,
                    ChangeBounds().apply {
                        duration = 240
                        interpolator = OvershootInterpolator(0.8f)
                    }
                )
            } catch (e: Exception) {
                Log.w("IslandService", "Transition failed, applying directly: ${e.message}")
            }
        }

        applyStateVisibilities(newState)
    }

    private fun applyStateVisibilities(state: State) {
        when (state) {
            State.IDLE -> {
                compactContainer.visibility = View.VISIBLE
                expandedContainer.visibility = View.GONE

                // Make Standby visibly recognizable around punch-hole:
                // Left: iconic Nothing Red dot
                compactLeftIcon.setImageResource(R.drawable.ic_dot)
                compactLeftIcon.visibility = View.VISIBLE

                compactLeftText.visibility = View.GONE

                // Right: Nothing label in NDot57 typography
                compactRightText.text = "NOTHING"
                compactRightText.visibility = View.VISIBLE
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
                compactLeftText.text = "CHARGE"
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

    private fun dpToPx(dp: Float): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            overlayView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                }
            }
        } catch (e: Exception) {
            Log.e("IslandService", "Error removing overlay on destroy", e)
        } finally {
            overlayView = null
        }
    }

    companion object {
        var instance: IslandOverlayService? = null
            private set
    }
}
