package com.d4rxh.nothingisland.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.d4rxh.nothingisland.R
import com.d4rxh.nothingisland.data.IslandPreferences
import com.d4rxh.nothingisland.helpers.NothingHapticHelper
import com.d4rxh.nothingisland.helpers.TypefaceHelper
import com.d4rxh.nothingisland.ui.DotMatrixEqualizerView
import com.d4rxh.nothingisland.ui.LockscreenOverlayActivity
import kotlin.math.abs

object IslandOverlayManager {

    private const val TAG = "IslandOverlayManager"

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isAttached = false

    // UI references
    private var islandRoot: FrameLayout? = null
    private var islandCapsule: LinearLayout? = null
    private var compactContainer: LinearLayout? = null
    private var compactLeftContainer: FrameLayout? = null
    private var compactLeftIcon: ImageView? = null
    private var compactLeftText: TextView? = null
    private var centerCutoutSpacer: View? = null
    private var compactRightContainer: LinearLayout? = null
    private var compactRightText: TextView? = null
    private var compactEqualizer: DotMatrixEqualizerView? = null

    private var expandedContainer: LinearLayout? = null
    private var expandedHeaderRow: LinearLayout? = null
    private var expandedAppIcon: ImageView? = null
    private var expandedSourceAppText: TextView? = null
    private var expandedArtwork: ImageView? = null
    private var expandedTitleText: TextView? = null
    private var expandedArtistText: TextView? = null
    private var expandedEqualizer: DotMatrixEqualizerView? = null
    private var mediaControlsRow: LinearLayout? = null
    private var btnPrev: ImageButton? = null
    private var btnPlayPause: ImageButton? = null
    private var btnNext: ImageButton? = null
    private var expandedNotificationBody: TextView? = null

    enum class State {
        IDLE,
        MUSIC_COMPACT,
        CHARGING_COMPACT,
        NOTIFICATION_COMPACT,
        CRICKET_COMPACT,
        EXPANDED
    }

    private var currentState: State = State.IDLE
    private var previousCompactState: State = State.IDLE

    // Media state persistence
    private var isMusicPlaying = false
    private var currentTrackTitle = ""
    private var currentArtistName = ""
    private var currentAppName = "SPOTIFY"
    private var currentArtwork: Bitmap? = null
    private var currentAppIcon: Drawable? = null

    // Cricket state persistence
    private var currentCricketMatch = ""
    private var currentCricketScore = ""
    private var currentCricketAppName = ""
    private var currentCricketAppIcon: Drawable? = null

    // Auto dismiss notification runnable
    private var notificationDismissRunnable: Runnable? = null

    // Touch event variables for 100% reliable tap-to-expand
    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isTouchTracking = false

    fun showOverlay(context: Context) {
        appContext = context.applicationContext

        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show overlay: Settings.canDrawOverlays is false")
            return
        }

        mainHandler.post {
            try {
                if (windowManager == null) {
                    windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                }

                if (!isAttached || overlayView == null) {
                    createAndAttachOverlay(context)
                } else {
                    applyStateVisibilities(currentState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in showOverlay: ${e.message}", e)
            }
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createAndAttachOverlay(context: Context) {
        val wm = windowManager ?: return
        val prefs = IslandPreferences(context)

        cleanOldView()

        val themedContext = ContextThemeWrapper(context, R.style.Theme_NOTIsland)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.layout_island_overlay, null)
        overlayView = view

        islandRoot = view.findViewById(R.id.islandRoot)
        islandCapsule = view.findViewById(R.id.islandCapsule)
        compactContainer = view.findViewById(R.id.compactContainer)
        compactLeftContainer = view.findViewById(R.id.compactLeftContainer)
        compactLeftIcon = view.findViewById(R.id.compactLeftIcon)
        compactLeftText = view.findViewById(R.id.compactLeftText)
        centerCutoutSpacer = view.findViewById(R.id.centerCutoutSpacer)
        compactRightContainer = view.findViewById(R.id.compactRightContainer)
        compactRightText = view.findViewById(R.id.compactRightText)
        compactEqualizer = view.findViewById(R.id.compactEqualizer)

        expandedContainer = view.findViewById(R.id.expandedContainer)
        expandedHeaderRow = view.findViewById(R.id.expandedHeaderRow)
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

        compactLeftText?.let { TypefaceHelper.applyNdot(it) }
        compactRightText?.let { TypefaceHelper.applyNdot(it) }
        expandedSourceAppText?.let { TypefaceHelper.applyNdot(it) }
        expandedTitleText?.let { TypefaceHelper.applyNdot(it) }

        // Enable marquee scrolling on texts
        compactRightText?.isSelected = true
        expandedTitleText?.isSelected = true

        applyCustomStyling(context)
        setupInteractions(context)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // FLAG_SHOW_WHEN_LOCKED + FLAG_LAYOUT_IN_SCREEN + FLAG_LAYOUT_NO_LIMITS
        val windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val targetY = if (prefs.attachToTop) 0 else dpToPx(context, prefs.yOffset)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dpToPx(context, prefs.xOffset)
            y = targetY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        layoutParams = params

        try {
            wm.addView(view, params)
            isAttached = true
            applyStateVisibilities(State.IDLE)
            Log.i(TAG, "Dynamic Island overlay attached successfully at (x=${params.x}, y=${params.y})")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error adding overlay view: ${e.message}", e)
            isAttached = false
        }
    }

    private fun cleanOldView() {
        val wm = windowManager ?: return
        overlayView?.let { oldView ->
            try {
                if (oldView.isAttachedToWindow || isAttached) {
                    wm.removeViewImmediate(oldView)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning old view: ${e.message}")
            }
        }
        overlayView = null
        isAttached = false
    }

    private fun applyCustomStyling(context: Context) {
        try {
            val prefs = IslandPreferences(context)
            val capsule = islandCapsule ?: return

            val radiusPx = dpToPx(context, prefs.cornerRadius).toFloat()
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                setStroke(dpToPx(context, 1.2f), Color.parseColor("#444444"))
                if (prefs.attachToTop) {
                    // Apple-style attached to top bezel:
                    // Flat top edge flush with bezel, rounded bottom corners
                    cornerRadii = floatArrayOf(
                        0f, 0f,             // Top-left
                        0f, 0f,             // Top-right
                        radiusPx, radiusPx, // Bottom-right
                        radiusPx, radiusPx  // Bottom-left
                    )
                } else {
                    cornerRadius = radiusPx
                }
            }
            capsule.background = bgDrawable

            // If attached to top, add top padding and extend height to cover bezel seamlessly
            val extraHeight = if (prefs.attachToTop) dpToPx(context, 6) else 0
            val topPadding = if (prefs.attachToTop) dpToPx(context, 4) else 0

            compactContainer?.setPadding(
                dpToPx(context, 10),
                topPadding,
                dpToPx(context, 10),
                0
            )

            compactContainer?.layoutParams = compactContainer?.layoutParams?.apply {
                height = dpToPx(context, prefs.islandHeight) + extraHeight
            }

            // Normal idle width
            if (currentState != State.EXPANDED && currentState != State.MUSIC_COMPACT && currentState != State.CRICKET_COMPACT) {
                capsule.minimumWidth = dpToPx(context, prefs.islandWidth)
            }

            centerCutoutSpacer?.layoutParams = centerCutoutSpacer?.layoutParams?.apply {
                width = dpToPx(context, prefs.cutoutSize)
                height = dpToPx(context, prefs.cutoutSize)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyCustomStyling: ${e.message}")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupInteractions(context: Context) {
        val prefs = IslandPreferences(context)

        // Resilient OnTouchListener:
        // Handles both ACTION_UP and ACTION_CANCEL (cancels occur when status-bar shade begins gesture)
        val islandTouchListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = SystemClock.uptimeMillis()
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    isTouchTracking = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isTouchTracking) {
                        isTouchTracking = false
                        val duration = SystemClock.uptimeMillis() - touchDownTime
                        val dx = abs(event.rawX - touchDownX)
                        val dy = abs(event.rawY - touchDownY)
                        val density = view.resources.displayMetrics.density
                        val maxTapDelta = 35 * density

                        if (duration < 500 && dx < maxTapDelta && dy < maxTapDelta) {
                            if (prefs.hapticsEnabled) {
                                NothingHapticHelper.vibratePop(view.context)
                            }
                            handleIslandTap()
                            return@OnTouchListener true
                        }
                    }
                    false
                }
                else -> false
            }
        }

        islandCapsule?.setOnTouchListener(islandTouchListener)
        compactContainer?.setOnTouchListener(islandTouchListener)
        overlayView?.setOnTouchListener(islandTouchListener)

        // Tapping expanded header or collapse pill collapses the island
        val collapseClickListener = View.OnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            collapseIsland()
        }
        expandedHeaderRow?.setOnClickListener(collapseClickListener)
        expandedArtwork?.setOnClickListener(collapseClickListener)

        // Media controls in expanded state
        btnPlayPause?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(it.context)
            IslandMediaManager.togglePlayPause()
        }

        btnPrev?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            IslandMediaManager.skipPrevious()
        }

        btnNext?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            IslandMediaManager.skipNext()
        }
    }

    private fun handleIslandTap() {
        if (currentState == State.EXPANDED) {
            collapseIsland()
        } else {
            expandIsland()
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
        val context = appContext ?: return
        val view = overlayView ?: return
        if (!isAttached) return
        val prefs = IslandPreferences(context)

        layoutParams?.let { params ->
            params.x = dpToPx(context, xOffset)
            params.y = if (prefs.attachToTop) 0 else dpToPx(context, yOffset)
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating layout params: ${e.message}")
            }
        }

        applyCustomStyling(context)
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
        val context = appContext ?: return
        val prefs = IslandPreferences(context)
        if (!prefs.musicIslandEnabled) return

        currentTrackTitle = title
        currentArtistName = artist
        currentAppName = appName
        isMusicPlaying = isPlaying
        currentArtwork = artwork
        currentAppIcon = appIcon

        mainHandler.post {
            try {
                if (title.isNotEmpty() && isPlaying) {
                    previousCompactState = State.MUSIC_COMPACT

                    // 1. Setup Compact Island (Locked 24x24dp Container)
                    if (artwork != null) {
                        compactLeftIcon?.setImageBitmap(artwork)
                    } else if (appIcon != null) {
                        compactLeftIcon?.setImageDrawable(appIcon)
                    } else {
                        compactLeftIcon?.setImageResource(R.drawable.ic_music)
                    }
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE

                    // Stable song title with marquee scrolling
                    compactRightText?.text = title
                    compactRightText?.setTextColor(Color.WHITE)
                    compactRightText?.isSelected = true
                    compactRightText?.visibility = View.VISIBLE

                    compactEqualizer?.visibility = View.VISIBLE
                    compactEqualizer?.isPlaying = true

                    // Rock-Solid Fixed Music Width (prevents shrinking or jumping)
                    val stableMusicWidth = dpToPx(context, maxOf(prefs.islandWidth, 215))
                    islandCapsule?.minimumWidth = stableMusicWidth

                    // 2. Setup Expanded Media Card
                    if (appIcon != null) {
                        expandedAppIcon?.setImageDrawable(appIcon)
                    } else {
                        expandedAppIcon?.setImageResource(R.drawable.ic_music)
                    }
                    expandedSourceAppText?.text = appName.uppercase()

                    if (artwork != null) {
                        expandedArtwork?.setImageBitmap(artwork)
                        expandedArtwork?.visibility = View.VISIBLE
                    } else if (appIcon != null) {
                        expandedArtwork?.setImageDrawable(appIcon)
                        expandedArtwork?.visibility = View.VISIBLE
                    } else {
                        expandedArtwork?.setImageResource(R.drawable.ic_music)
                        expandedArtwork?.visibility = View.VISIBLE
                    }

                    expandedTitleText?.text = title
                    expandedArtistText?.text = artist
                    mediaControlsRow?.visibility = View.VISIBLE
                    expandedNotificationBody?.visibility = View.GONE
                    btnPlayPause?.setImageResource(R.drawable.ic_pause)
                    expandedEqualizer?.isPlaying = true

                    // Don't interrupt if notification or expanded is showing
                    if (currentState != State.EXPANDED && currentState != State.NOTIFICATION_COMPACT) {
                        updateState(State.MUSIC_COMPACT)
                    }
                } else if (!isPlaying && currentState == State.MUSIC_COMPACT) {
                    compactEqualizer?.isPlaying = false
                    expandedEqualizer?.isPlaying = false
                    btnPlayPause?.setImageResource(R.drawable.ic_play)
                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                    updateState(State.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onMediaStateChanged UI update: ${e.message}", e)
            }
        }
    }

    fun onChargingChanged(isCharging: Boolean, batteryPercent: Int) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)
        if (!prefs.chargingIslandEnabled) return

        mainHandler.post {
            try {
                if (isCharging) {
                    compactLeftIcon?.setImageResource(R.drawable.ic_bolt)
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.VISIBLE
                    compactLeftText?.text = "CHARGE"

                    compactRightText?.text = "$batteryPercent%"
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE

                    updateState(State.CHARGING_COMPACT)

                    // Auto revert back to MUSIC or IDLE after 5 seconds
                    mainHandler.postDelayed({
                        if (currentState == State.CHARGING_COMPACT) {
                            if (isMusicPlaying && currentTrackTitle.isNotEmpty()) {
                                restoreMusicCompactState()
                            } else {
                                updateState(State.IDLE)
                            }
                        }
                    }, 5000)
                } else {
                    if (currentState == State.CHARGING_COMPACT) {
                        if (isMusicPlaying && currentTrackTitle.isNotEmpty()) {
                            restoreMusicCompactState()
                        } else {
                            updateState(State.IDLE)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onChargingChanged: ${e.message}", e)
            }
        }
    }

    fun onNotificationReceived(
        appName: String,
        title: String,
        content: String,
        appIcon: Drawable?
    ) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)
        if (!prefs.notificationIslandEnabled) return

        mainHandler.post {
            try {
                // Remove any pending auto-dismiss
                notificationDismissRunnable?.let { mainHandler.removeCallbacks(it) }

                // 1. Compact View: App Icon + Notification Title
                if (appIcon != null) {
                    compactLeftIcon?.setImageDrawable(appIcon)
                } else {
                    compactLeftIcon?.setImageResource(R.drawable.ic_bell)
                }
                compactLeftIcon?.visibility = View.VISIBLE
                compactLeftText?.visibility = View.GONE

                compactRightText?.text = title
                compactRightText?.isSelected = true
                compactRightText?.visibility = View.VISIBLE
                compactEqualizer?.visibility = View.GONE

                // Fixed elegant notification width
                islandCapsule?.minimumWidth = dpToPx(context, maxOf(prefs.islandWidth, 200))

                // 2. Expanded View
                if (appIcon != null) {
                    expandedAppIcon?.setImageDrawable(appIcon)
                    expandedArtwork?.setImageDrawable(appIcon)
                    expandedArtwork?.visibility = View.VISIBLE
                } else {
                    expandedAppIcon?.setImageResource(R.drawable.ic_bell)
                    expandedArtwork?.visibility = View.GONE
                }

                expandedSourceAppText?.text = appName.uppercase()
                expandedTitleText?.text = title
                expandedArtistText?.text = content
                mediaControlsRow?.visibility = View.GONE
                expandedNotificationBody?.visibility = View.VISIBLE
                expandedNotificationBody?.text = content

                updateState(State.NOTIFICATION_COMPACT)

                // Auto dismiss notification banner after exactly 4.0 seconds and revert to music
                notificationDismissRunnable = Runnable {
                    dismissNotificationAndRestore()
                }
                mainHandler.postDelayed(notificationDismissRunnable!!, 4000)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onNotificationReceived: ${e.message}", e)
            }
        }
    }

    fun onNotificationDismissed() {
        mainHandler.post {
            dismissNotificationAndRestore()
        }
    }

    private fun dismissNotificationAndRestore() {
        notificationDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        notificationDismissRunnable = null

        if (currentState == State.NOTIFICATION_COMPACT) {
            if (isMusicPlaying && currentTrackTitle.isNotEmpty()) {
                restoreMusicCompactState()
            } else if (currentCricketScore.isNotEmpty()) {
                restoreCricketCompactState()
            } else {
                updateState(State.IDLE)
            }
        }
    }

    fun onCricketScoreReceived(
        appName: String,
        matchTitle: String,
        scoreText: String,
        appIcon: Drawable?
    ) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)

        currentCricketMatch = matchTitle
        currentCricketScore = scoreText
        currentCricketAppName = appName
        currentCricketAppIcon = appIcon

        mainHandler.post {
            try {
                if (currentState != State.NOTIFICATION_COMPACT && currentState != State.EXPANDED) {
                    // If music is playing, flash score for 5s then revert to music
                    displayCricketCompact()

                    if (isMusicPlaying && currentTrackTitle.isNotEmpty()) {
                        mainHandler.postDelayed({
                            if (currentState == State.CRICKET_COMPACT && isMusicPlaying) {
                                restoreMusicCompactState()
                            }
                        }, 5000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCricketScoreReceived: ${e.message}", e)
            }
        }
    }

    private fun displayCricketCompact() {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)

        if (currentCricketAppIcon != null) {
            compactLeftIcon?.setImageDrawable(currentCricketAppIcon)
        } else {
            compactLeftIcon?.setImageResource(R.drawable.ic_cricket)
        }
        compactLeftIcon?.visibility = View.VISIBLE
        compactLeftText?.visibility = View.GONE

        compactRightText?.text = currentCricketScore
        compactRightText?.setTextColor(Color.WHITE)
        compactRightText?.isSelected = true
        compactRightText?.visibility = View.VISIBLE
        compactEqualizer?.visibility = View.GONE

        islandCapsule?.minimumWidth = dpToPx(context, maxOf(prefs.islandWidth, 215))

        // Expanded view for cricket
        if (currentCricketAppIcon != null) {
            expandedAppIcon?.setImageDrawable(currentCricketAppIcon)
            expandedArtwork?.setImageDrawable(currentCricketAppIcon)
            expandedArtwork?.visibility = View.VISIBLE
        } else {
            expandedAppIcon?.setImageResource(R.drawable.ic_cricket)
            expandedArtwork?.setImageResource(R.drawable.ic_cricket)
            expandedArtwork?.visibility = View.VISIBLE
        }

        expandedSourceAppText?.text = "LIVE CRICKET - ${currentCricketAppName.uppercase()}"
        expandedTitleText?.text = currentCricketMatch
        expandedArtistText?.text = currentCricketScore
        mediaControlsRow?.visibility = View.GONE
        expandedNotificationBody?.visibility = View.VISIBLE
        expandedNotificationBody?.text = "$currentCricketMatch\n$currentCricketScore"

        updateState(State.CRICKET_COMPACT)
    }

    private fun restoreCricketCompactState() {
        displayCricketCompact()
    }

    private fun restoreMusicCompactState() {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)

        if (currentArtwork != null) {
            compactLeftIcon?.setImageBitmap(currentArtwork)
        } else if (currentAppIcon != null) {
            compactLeftIcon?.setImageDrawable(currentAppIcon)
        } else {
            compactLeftIcon?.setImageResource(R.drawable.ic_music)
        }
        compactLeftIcon?.visibility = View.VISIBLE
        compactLeftText?.visibility = View.GONE

        compactRightText?.text = currentTrackTitle
        compactRightText?.setTextColor(Color.WHITE)
        compactRightText?.isSelected = true
        compactRightText?.visibility = View.VISIBLE

        compactEqualizer?.visibility = View.VISIBLE
        compactEqualizer?.isPlaying = isMusicPlaying

        val stableMusicWidth = dpToPx(context, maxOf(prefs.islandWidth, 215))
        islandCapsule?.minimumWidth = stableMusicWidth

        // Restore expanded media view
        if (currentAppIcon != null) {
            expandedAppIcon?.setImageDrawable(currentAppIcon)
        } else {
            expandedAppIcon?.setImageResource(R.drawable.ic_music)
        }
        expandedSourceAppText?.text = currentAppName.uppercase()

        if (currentArtwork != null) {
            expandedArtwork?.setImageBitmap(currentArtwork)
            expandedArtwork?.visibility = View.VISIBLE
        } else if (currentAppIcon != null) {
            expandedArtwork?.setImageDrawable(currentAppIcon)
            expandedArtwork?.visibility = View.VISIBLE
        }

        expandedTitleText?.text = currentTrackTitle
        expandedArtistText?.text = currentArtistName
        mediaControlsRow?.visibility = View.VISIBLE
        expandedNotificationBody?.visibility = View.GONE
        btnPlayPause?.setImageResource(if (isMusicPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        expandedEqualizer?.isPlaying = isMusicPlaying

        updateState(State.MUSIC_COMPACT)
    }

    private fun updateState(newState: State) {
        currentState = newState

        mainHandler.post {
            try {
                val view = overlayView ?: return@post
                val capsule = islandCapsule ?: return@post
                if (!isAttached) return@post

                try {
                    TransitionManager.beginDelayedTransition(
                        capsule as ViewGroup,
                        ChangeBounds().apply {
                            duration = 200
                            interpolator = OvershootInterpolator(0.7f)
                        }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Transition failed: ${e.message}")
                }

                applyStateVisibilities(newState)

                // Force WindowManager to relayout
                layoutParams?.let { params ->
                    windowManager?.updateViewLayout(view, params)
                }
                view.requestLayout()

                // Sync lock screen if active
                LockscreenOverlayActivity.instance?.syncState(
                    state = newState,
                    trackTitle = currentTrackTitle,
                    artistName = currentArtistName,
                    appName = currentAppName,
                    isPlaying = isMusicPlaying,
                    artwork = currentArtwork,
                    appIcon = currentAppIcon,
                    cricketMatch = currentCricketMatch,
                    cricketScore = currentCricketScore
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateState: ${e.message}", e)
            }
        }
    }

    private fun applyStateVisibilities(state: State) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)

        try {
            when (state) {
                State.IDLE -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE

                    compactLeftIcon?.setImageResource(R.drawable.ic_dot)
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE

                    centerCutoutSpacer?.visibility = View.VISIBLE

                    compactRightText?.text = "NOTHING"
                    compactRightText?.setTextColor(Color.WHITE)
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE

                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.MUSIC_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.VISIBLE
                    islandCapsule?.minimumWidth = dpToPx(context, maxOf(prefs.islandWidth, 215))
                }
                State.CHARGING_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.setImageResource(R.drawable.ic_bolt)
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.VISIBLE
                    compactLeftText?.text = "CHARGE"
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE
                }
                State.NOTIFICATION_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE
                    islandCapsule?.minimumWidth = dpToPx(context, maxOf(prefs.islandWidth, 200))
                }
                State.CRICKET_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE
                    islandCapsule?.minimumWidth = dpToPx(context, maxOf(prefs.islandWidth, 215))
                }
                State.EXPANDED -> {
                    compactContainer?.visibility = View.GONE
                    expandedContainer?.visibility = View.VISIBLE
                    if (isMusicPlaying) {
                        mediaControlsRow?.visibility = View.VISIBLE
                        expandedNotificationBody?.visibility = View.GONE
                    } else {
                        mediaControlsRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyStateVisibilities: ${e.message}")
        }
    }

    fun expandIsland() {
        updateState(State.EXPANDED)
    }

    fun collapseIsland() {
        if (isMusicPlaying && currentTrackTitle.isNotEmpty()) {
            restoreMusicCompactState()
        } else if (currentCricketScore.isNotEmpty()) {
            restoreCricketCompactState()
        } else {
            updateState(State.IDLE)
        }
    }

    fun hideOverlay() {
        val wm = windowManager ?: return
        overlayView?.let { view ->
            try {
                if (isAttached) {
                    wm.removeViewImmediate(view)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in hideOverlay: ${e.message}")
            } finally {
                overlayView = null
                isAttached = false
            }
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
