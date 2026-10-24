package com.d4rxh.nothingisland.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.EditText
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
import com.d4rxh.nothingisland.ui.IslandRootLayout

object IslandOverlayManager {

    private const val TAG = "IslandOverlayManager"

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isAttached = false

    @Volatile
    private var accessibilityService: AccessibilityService? = null

    // UI references
    private var islandRoot: IslandRootLayout? = null
    private var islandContainerRow: LinearLayout? = null
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

    // Call Controls Row
    private var callControlsRow: LinearLayout? = null
    private var btnDeclineCall: ImageButton? = null
    private var btnAnswerCall: ImageButton? = null

    // Quick Reply Row
    private var quickReplyRow: LinearLayout? = null
    private var replyEditText: EditText? = null
    private var btnSendReply: ImageButton? = null

    // Timer Controls Row
    private var timerControlsRow: LinearLayout? = null
    private var btnTimerReset: TextView? = null
    private var btnTimerPlayPause: ImageButton? = null
    private var btnTimerLap: TextView? = null

    // Multi-Event Switcher Dots
    private var eventIndicatorDots: LinearLayout? = null
    private var dotEvent1: View? = null
    private var dotEvent2: View? = null

    // Split Pill (Dual Island) views
    private var islandSubCapsule: LinearLayout? = null
    private var subCapsuleIcon: ImageView? = null
    private var subCapsuleText: TextView? = null

    enum class State {
        IDLE,
        MUSIC_COMPACT,
        MUSIC_PAUSED_COMPACT,
        CHARGING_COMPACT,
        NOTIFICATION_COMPACT,
        CRICKET_COMPACT,
        CALL_COMPACT,
        TIMER_COMPACT,
        NAVIGATION_COMPACT,
        DEVICE_EVENT_COMPACT,
        RECORDER_COMPACT,
        EXPANDED
    }

    private var currentState: State = State.IDLE

    // Voice Recorder state
    private var isRecordingActive = false
    private var recorderAppName = "RECORDER"
    private var recorderTitle = "Recording"
    private var recorderText = ""
    private var recorderAppIcon: Drawable? = null
    private var recorderIntent: PendingIntent? = null

    // External Clock / Timer / Stopwatch state
    private var isExternalClockActive = false
    private var externalClockAppName = "CLOCK"
    private var externalClockTitle = "Timer"
    private var externalClockText = ""
    private var isExternalClockTimer = true
    private var externalClockAppIcon: Drawable? = null
    private var externalClockIntent: PendingIntent? = null

    // Quick Reply state
    private var activeRemoteInput: RemoteInput? = null
    private var activeReplyIntent: PendingIntent? = null

    // Device Event banner state
    private var deviceEventTitle = ""
    private var deviceEventSubtitle = ""
    private var deviceEventDismissRunnable: Runnable? = null

    // Media state persistence
    private var isMusicPlaying = false
    private var currentTrackTitle = ""
    private var currentArtistName = ""
    private var currentPackageName = ""
    private var currentAppName = "SPOTIFY"
    private var currentArtwork: Bitmap? = null
    private var currentAppIcon: Drawable? = null

    // Cricket state persistence
    private var currentCricketMatch = ""
    private var currentCricketScore = ""
    private var currentCricketAppName = ""
    private var currentCricketAppIcon: Drawable? = null

    // Battery & Charging state persistence
    private var currentBatteryPercent = 85
    private var isDeviceCharging = false
    private var batteryReceiver: BroadcastReceiver? = null
    private var isBatteryReceiverRegistered = false
    private var displayListener: DisplayManager.DisplayListener? = null

    // Active notification persistence
    private var activeNotificationAppName = ""
    private var activeNotificationTitle = ""
    private var activeNotificationContent = ""
    private var activeNotificationPackage = ""
    private var activeNotificationIcon: Drawable? = null
    private var activeNotificationPendingIntent: PendingIntent? = null
    private var hasActiveNotification = false

    // Auto dismiss notification runnable
    private var notificationDismissRunnable: Runnable? = null
    private var pausedTimeoutRunnable: Runnable? = null
    private var cricketExpirationRunnable: Runnable? = null

    fun getCurrentState(): State = currentState
    fun getCurrentMediaPackage(): String = currentPackageName

    fun setAccessibilityService(service: AccessibilityService?) {
        accessibilityService = service
        val context = appContext ?: return
        mainHandler.post {
            if (isAttached) {
                cleanOldView()
                showOverlay(context)
            }
        }
    }

    private fun registerDisplayListener(context: Context) {
        if (displayListener != null) return
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                onOrientationChanged()
            }
        }
        try {
            dm.registerDisplayListener(displayListener, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Error registering display listener: ${e.message}")
        }
    }

    fun registerBatteryReceiver(context: Context) {
        if (isBatteryReceiverRegistered) return
        batteryReceiver = object : BroadcastReceiver() {
            private var lastIsCharging: Boolean? = null

            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val action = intent.action ?: return

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = action == Intent.ACTION_POWER_CONNECTED ||
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (level >= 0 && scale > 0) {
                    (level * 100) / scale
                } else {
                    val bm = ctx?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: currentBatteryPercent
                }

                if (action == Intent.ACTION_POWER_CONNECTED || (isCharging && lastIsCharging != true)) {
                    lastIsCharging = true
                    onChargingChanged(true, percent, isFirstConnect = true)
                } else if (action == Intent.ACTION_POWER_DISCONNECTED || (!isCharging && lastIsCharging == true)) {
                    lastIsCharging = false
                    onChargingChanged(false, percent, isFirstConnect = false)
                } else if (isCharging) {
                    // LIVE UPDATE: Update percentage in real time as battery charges!
                    onChargingChanged(true, percent, isFirstConnect = false)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(batteryReceiver, filter)
            }
            isBatteryReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register battery receiver: ${e.message}")
        }
    }

    private fun updateLayoutParamsForOrientation(context: Context, params: WindowManager.LayoutParams) {
        val prefs = IslandPreferences(context)
        val wm = windowManager ?: return

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: Surface.ROTATION_0
            } catch (e: Exception) {
                wm.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } else {
            wm.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }

        val targetY = if (prefs.attachToTop) -dpToPx(context, 3) else dpToPx(context, prefs.yOffset)
        val targetX = dpToPx(context, prefs.xOffset)

        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                wm.currentWindowMetrics.windowInsets
            } catch (e: Exception) {
                overlayView?.rootWindowInsets
            }
        } else {
            overlayView?.rootWindowInsets
        }

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            insets?.displayCutout
        } else null

        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cutout?.boundingRects
        } else null

        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

        if (isLandscape) {
            val edgeY = if (prefs.attachToTop) -dpToPx(context, 3) else targetY
            if (rotation == Surface.ROTATION_90) {
                // Landscape gaming mode: camera notch on the LEFT edge
                params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                params.x = edgeY
                params.y = targetX
            } else {
                // Reverse landscape: camera notch on the RIGHT edge
                params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                params.x = edgeY
                params.y = -targetX
            }

            if (currentState == State.EXPANDED) {
                // Full expansion in landscape mode without clipping
                islandContainerRow?.orientation = LinearLayout.HORIZONTAL
                islandCapsule?.layoutParams?.width = ViewGroup.LayoutParams.WRAP_CONTENT
                islandCapsule?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                // Vertically hug the camera punch-hole along the short edge (portrait-style along the notch)
                val landscapeThickness = dpToPx(context, prefs.islandHeight)
                islandContainerRow?.orientation = LinearLayout.VERTICAL
                islandCapsule?.layoutParams?.width = landscapeThickness
                islandCapsule?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                compactContainer?.orientation = LinearLayout.VERTICAL
                compactContainer?.layoutParams?.width = landscapeThickness
                compactContainer?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                compactContainer?.setPadding(dpToPx(context, 4), dpToPx(context, 8), dpToPx(context, 4), dpToPx(context, 8))
                islandSubCapsule?.layoutParams?.let { lp ->
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.topMargin = dpToPx(context, 6)
                        lp.marginStart = 0
                    }
                }
            }
        } else {
            // Standard portrait: camera notch on TOP CENTER
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Pure center alignment (offset only by user setting prefs.xOffset)
            params.x = targetX
            params.y = if (prefs.attachToTop) -dpToPx(context, 3) else if (bounds != null && bounds.isNotEmpty()) bounds[0].top else targetY

            val extraHeight = if (prefs.attachToTop) dpToPx(context, 7) else 0
            val topPadding = if (prefs.attachToTop) dpToPx(context, 4) else 0

            // Horizontally hug the camera punch-hole along the top edge
            islandContainerRow?.orientation = LinearLayout.HORIZONTAL
            islandCapsule?.layoutParams?.width = ViewGroup.LayoutParams.WRAP_CONTENT
            islandCapsule?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            compactContainer?.orientation = LinearLayout.HORIZONTAL
            compactContainer?.layoutParams?.width = ViewGroup.LayoutParams.WRAP_CONTENT
            compactContainer?.layoutParams?.height = dpToPx(context, prefs.islandHeight) + extraHeight
            compactContainer?.setPadding(dpToPx(context, 10), topPadding, dpToPx(context, 10), 0)
            islandSubCapsule?.layoutParams?.let { lp ->
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.topMargin = 0
                    lp.marginStart = dpToPx(context, 8)
                }
            }
        }

        // Reset rotation so the capsule is never clipped or collapsed by WindowManager bounds
        islandCapsule?.rotation = 0f
    }

    fun onOrientationChanged() {
        val context = appContext ?: return
        val view = overlayView ?: return
        val wm = windowManager ?: return
        val params = layoutParams ?: return

        mainHandler.post {
            try {
                updateLayoutParamsForOrientation(context, params)
                view.visibility = View.VISIBLE
                wm.updateViewLayout(view, params)
                view.requestLayout()
                applyStateVisibilities(currentState)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating orientation layout: ${e.message}", e)
            }
        }
    }

    fun showOverlay(context: Context) {
        appContext = context.applicationContext

        if (!Settings.canDrawOverlays(context) && accessibilityService == null) {
            Log.w(TAG, "Cannot show overlay: overlay permission not granted")
            return
        }

        registerDisplayListener(context)
        registerBatteryReceiver(context)
        DeviceEventsReceiver.register(context)

        // Initialize Call Manager Callbacks
        IslandCallManager.onCallStateChanged = { state ->
            mainHandler.post {
                if (state == IslandCallManager.CallState.INCOMING || state == IslandCallManager.CallState.ACTIVE) {
                    updateState(State.CALL_COMPACT)
                    if (state == IslandCallManager.CallState.INCOMING) {
                        expandIsland()
                    }
                } else {
                    restorePreviousMediaOrIdle()
                }
            }
        }
        IslandCallManager.onDurationTick = { durationStr ->
            mainHandler.post {
                if (currentState == State.CALL_COMPACT) {
                    compactRightText?.text = durationStr
                } else if (currentState == State.EXPANDED && IslandCallManager.currentState == IslandCallManager.CallState.ACTIVE) {
                    expandedArtistText?.text = "Active Call • $durationStr"
                }
            }
        }

        // Initialize Timer Manager Callbacks
        IslandTimerManager.onTimerTick = { _, _ ->
            mainHandler.post {
                if (currentState == State.TIMER_COMPACT) {
                    compactRightText?.text = IslandTimerManager.getTimerDisplayString()
                } else if (currentState == State.EXPANDED && IslandTimerManager.currentMode == IslandTimerManager.Mode.TIMER) {
                    expandedTitleText?.text = IslandTimerManager.getTimerDisplayString()
                }
                updateSplitPillState()
            }
        }
        IslandTimerManager.onStopwatchTick = { timeStr ->
            mainHandler.post {
                if (currentState == State.TIMER_COMPACT) {
                    compactRightText?.text = timeStr
                } else if (currentState == State.EXPANDED && IslandTimerManager.currentMode == IslandTimerManager.Mode.STOPWATCH) {
                    expandedTitleText?.text = timeStr
                }
            }
        }
        IslandTimerManager.onStateChanged = { mode ->
            mainHandler.post {
                if (mode != IslandTimerManager.Mode.NONE) {
                    if (currentState != State.EXPANDED) updateState(State.TIMER_COMPACT)
                } else {
                    restorePreviousMediaOrIdle()
                }
                updateSplitPillState()
            }
        }

        // Initialize Navigation Callbacks
        NavigationManager.onNavigationUpdate = {
            mainHandler.post {
                if (NavigationManager.isNavigating) {
                    if (currentState != State.EXPANDED) updateState(State.NAVIGATION_COMPACT)
                } else {
                    restorePreviousMediaOrIdle()
                }
            }
        }

        // Initialize Device Events (Bluetooth, Headset, Ringer)
        DeviceEventsReceiver.onDeviceEvent = { _, title, subtitle ->
            mainHandler.post {
                displayDeviceEvent(title, subtitle)
            }
        }

        mainHandler.post {
            try {
                val wm = if (accessibilityService != null) {
                    accessibilityService!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                } else {
                    context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                }
                windowManager = wm

                if (!isAttached || overlayView == null) {
                    createAndAttachOverlay(context, wm)
                } else {
                    applyStateVisibilities(currentState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in showOverlay: ${e.message}", e)
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun createAndAttachOverlay(context: Context, wm: WindowManager) {
        val prefs = IslandPreferences(context)
        cleanOldView()

        val themedContext = ContextThemeWrapper(context, R.style.Theme_NOTIsland)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.layout_island_overlay, null)
        overlayView = view

        islandRoot = view.findViewById(R.id.islandRoot)
        islandContainerRow = view.findViewById(R.id.islandContainerRow)
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

        callControlsRow = view.findViewById(R.id.callControlsRow)
        btnDeclineCall = view.findViewById(R.id.btnDeclineCall)
        btnAnswerCall = view.findViewById(R.id.btnAnswerCall)

        quickReplyRow = view.findViewById(R.id.quickReplyRow)
        replyEditText = view.findViewById(R.id.replyEditText)
        btnSendReply = view.findViewById(R.id.btnSendReply)

        timerControlsRow = view.findViewById(R.id.timerControlsRow)
        btnTimerReset = view.findViewById(R.id.btnTimerReset)
        btnTimerPlayPause = view.findViewById(R.id.btnTimerPlayPause)
        btnTimerLap = view.findViewById(R.id.btnTimerLap)

        eventIndicatorDots = view.findViewById(R.id.eventIndicatorDots)
        dotEvent1 = view.findViewById(R.id.dotEvent1)
        dotEvent2 = view.findViewById(R.id.dotEvent2)

        islandSubCapsule = view.findViewById(R.id.islandSubCapsule)
        subCapsuleIcon = view.findViewById(R.id.subCapsuleIcon)
        subCapsuleText = view.findViewById(R.id.subCapsuleText)

        // Apply authentic NDot font recursively to every text element in the island
        TypefaceHelper.applyNdotRecursively(view)

        compactRightText?.isSelected = true
        expandedTitleText?.isSelected = true

        applyCustomStyling(context)
        setupInteractions(context)

        // Window Type: TYPE_ACCESSIBILITY_OVERLAY if accessibility enabled (renders over lockscreen cleanly without blocking PIN)
        val layoutFlag = if (accessibilityService != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        val targetY = if (prefs.attachToTop) -dpToPx(context, 3) else dpToPx(context, prefs.yOffset)

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
        updateLayoutParamsForOrientation(context, params)
        layoutParams = params

        try {
            wm.addView(view, params)
            isAttached = true
            applyStateVisibilities(State.IDLE)
            Log.i(TAG, "Dynamic Island overlay attached successfully (type=$layoutFlag, x=${params.x}, y=${params.y})")
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
        islandRoot = null
        islandContainerRow = null
        islandCapsule = null
        compactContainer = null
        callControlsRow = null
        btnDeclineCall = null
        btnAnswerCall = null
        quickReplyRow = null
        replyEditText = null
        btnSendReply = null
        timerControlsRow = null
        btnTimerReset = null
        btnTimerPlayPause = null
        btnTimerLap = null
        eventIndicatorDots = null
        dotEvent1 = null
        dotEvent2 = null
        islandSubCapsule = null
        subCapsuleIcon = null
        subCapsuleText = null
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
                setStroke(dpToPx(context, 1.2f), Color.parseColor("#333333"))
                if (prefs.attachToTop) {
                    cornerRadii = floatArrayOf(
                        0f, 0f,
                        0f, 0f,
                        radiusPx, radiusPx,
                        radiusPx, radiusPx
                    )
                } else {
                    cornerRadius = radiusPx
                }
            }
            capsule.background = bgDrawable

            // Eliminate upward elevation shadow when flush-attached against the top bezel
            if (prefs.attachToTop) {
                capsule.elevation = 0f
            } else {
                capsule.elevation = dpToPx(context, 8).toFloat()
            }

            val extraHeight = if (prefs.attachToTop) dpToPx(context, 7) else 0
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

            // Normal idle width - sleek, strictly matches user preference
            if (currentState != State.EXPANDED) {
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

    private fun setupInteractions(context: Context) {
        val prefs = IslandPreferences(context)

        // IslandRootLayout claims top status-bar touches via requestDisallowInterceptTouchEvent
        // so the system Notification Shade pull-down gesture CANNOT steal taps on the island!
        islandRoot?.onIslandTap = {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(context)
            handleIslandTap()
        }

        islandRoot?.onIslandCollapse = {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(context)
            collapseIsland()
        }

        // Horizontal swipe to minimize / dismiss
        islandRoot?.onIslandSwipeDismiss = {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(context)
            collapseIsland()
        }

        // Swipe UP or double-tap: Hide island for 6 seconds for clean screenshot
        islandRoot?.onIslandScreenshotHide = {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(context)
            temporarilyHideIsland(6000)
        }

        islandRoot?.onOrientationChanged = {
            onOrientationChanged()
        }

        // Tapping expanded header row collapses the card
        expandedHeaderRow?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            collapseIsland()
        }

        // Tapping expanded artwork or notification body launches the app (WhatsApp / Spotify / etc.)
        expandedArtwork?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            handleExpandedCardClick(context)
        }

        expandedNotificationBody?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            handleExpandedCardClick(context)
        }

        expandedTitleText?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            handleExpandedCardClick(context)
        }

        expandedArtistText?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            handleExpandedCardClick(context)
        }

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

        // Horizontal swipe to switch active events (or collapse)
        islandRoot?.onIslandSwipeNext = {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(context)
            cycleToNextActiveEvent()
        }

        // Call Controls
        btnAnswerCall?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(it.context)
            IslandCallManager.answerCall(context)
        }

        btnDeclineCall?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(it.context)
            IslandCallManager.declineOrEndCall(context)
            collapseIsland()
        }

        // Quick Reply
        btnSendReply?.setOnClickListener {
            sendQuickReply(context)
        }
        replyEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendQuickReply(context)
                true
            } else false
        }

        // Timer Controls
        btnTimerPlayPause?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(it.context)
            when (IslandTimerManager.currentMode) {
                IslandTimerManager.Mode.TIMER -> {
                    if (IslandTimerManager.isTimerRunning && !IslandTimerManager.isTimerPaused) {
                        IslandTimerManager.pauseTimer()
                        btnTimerPlayPause?.setImageResource(R.drawable.ic_play)
                    } else {
                        IslandTimerManager.resumeTimer()
                        btnTimerPlayPause?.setImageResource(R.drawable.ic_pause)
                    }
                }
                IslandTimerManager.Mode.STOPWATCH -> {
                    if (IslandTimerManager.isStopwatchRunning && !IslandTimerManager.isStopwatchPaused) {
                        IslandTimerManager.pauseStopwatch()
                        btnTimerPlayPause?.setImageResource(R.drawable.ic_play)
                    } else {
                        IslandTimerManager.resumeStopwatch()
                        btnTimerPlayPause?.setImageResource(R.drawable.ic_pause)
                    }
                }
                IslandTimerManager.Mode.NONE -> {
                    IslandTimerManager.startTimer(300L) // Default 5 min timer
                    btnTimerPlayPause?.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        btnTimerReset?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            if (IslandTimerManager.currentMode == IslandTimerManager.Mode.TIMER) {
                IslandTimerManager.resetTimer()
            } else {
                IslandTimerManager.resetStopwatch()
            }
        }

        btnTimerLap?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            if (IslandTimerManager.currentMode == IslandTimerManager.Mode.STOPWATCH) {
                val lap = IslandTimerManager.recordLap()
                expandedArtistText?.text = lap
            } else if (IslandTimerManager.currentMode == IslandTimerManager.Mode.TIMER) {
                IslandTimerManager.startTimer(IslandTimerManager.remainingTimerSeconds + 60L)
            }
        }

        // Split Secondary Capsule click expands battery / timer status
        islandSubCapsule?.setOnClickListener {
            if (prefs.hapticsEnabled) overlayView?.let { v -> NothingHapticHelper.vibrateTick(v) }
            expandIsland()
        }
    }

    private fun sendQuickReply(context: Context) {
        val rInput = activeRemoteInput ?: return
        val rIntent = activeReplyIntent ?: return
        val text = replyEditText?.text?.toString()?.trim() ?: ""
        if (text.isEmpty()) return

        try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(rInput.resultKey, text)
            RemoteInput.addResultsToIntent(arrayOf(rInput), intent, bundle)
            rIntent.send(context, 0, intent)

            replyEditText?.setText("")
            compactRightText?.text = "REPLY SENT"
            NothingHapticHelper.vibratePop(context)
            collapseIsland()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send quick reply: ${e.message}")
        }
    }

    private fun getActiveEvents(): List<State> {
        val list = mutableListOf<State>()
        if (IslandCallManager.currentState != IslandCallManager.CallState.NONE) list.add(State.CALL_COMPACT)
        if (isRecordingActive) list.add(State.RECORDER_COMPACT)
        if (NavigationManager.isNavigating) list.add(State.NAVIGATION_COMPACT)
        if (isExternalClockActive || IslandTimerManager.currentMode != IslandTimerManager.Mode.NONE) list.add(State.TIMER_COMPACT)
        if (isMusicPlaying) list.add(State.MUSIC_COMPACT)
        else if (currentTrackTitle.isNotEmpty() && pausedTimeoutRunnable != null) list.add(State.MUSIC_PAUSED_COMPACT)
        if (currentCricketScore.isNotEmpty()) list.add(State.CRICKET_COMPACT)
        return list
    }

    fun cycleToNextActiveEvent() {
        val active = getActiveEvents()
        if (active.size <= 1) {
            collapseIsland()
            return
        }
        val currentIndex = active.indexOf(currentState)
        val nextIndex = (currentIndex + 1) % active.size
        updateState(active[nextIndex])
    }

    private fun handleExpandedCardClick(context: Context) {
        if (isRecordingActive && recorderIntent != null) {
            try {
                recorderIntent?.send()
                collapseIsland()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Recorder launch error: ${e.message}")
            }
        }

        if (isExternalClockActive && externalClockIntent != null) {
            try {
                externalClockIntent?.send()
                collapseIsland()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Clock launch error: ${e.message}")
            }
        }

        if (NavigationManager.isNavigating) {
            try {
                NavigationManager.launchIntent?.send()
                collapseIsland()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Navigation launch error: ${e.message}")
            }
        }

        if (hasActiveNotification) {
            handleNotificationLaunch(context)
            return
        }

        if (currentPackageName.isNotEmpty()) {
            openSourcePlayerApp(context)
        } else {
            collapseIsland()
        }
    }

    private fun clearActiveNotification() {
        hasActiveNotification = false
        activeNotificationAppName = ""
        activeNotificationTitle = ""
        activeNotificationContent = ""
        activeNotificationPackage = ""
        activeNotificationIcon = null
        activeNotificationPendingIntent = null
        activeRemoteInput = null
        activeReplyIntent = null
    }

    private fun handleNotificationLaunch(context: Context) {
        notificationDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        notificationDismissRunnable = null

        val pendingIntent = activeNotificationPendingIntent
        if (pendingIntent != null) {
            try {
                pendingIntent.send()
                clearActiveNotification()
                collapseIsland()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send PendingIntent: ${e.message}")
            }
        }

        if (activeNotificationPackage.isNotEmpty()) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(activeNotificationPackage)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    clearActiveNotification()
                    collapseIsland()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error opening notification app: ${e.message}")
            }
        }

        clearActiveNotification()
        collapseIsland()
    }

    private fun openSourcePlayerApp(context: Context) {
        if (currentPackageName.isNotEmpty()) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(currentPackageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    collapseIsland()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error opening player app: ${e.message}")
            }
        }
    }

    private fun handleIslandTap() {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)
        if (prefs.hapticsEnabled) overlayView?.let { NothingHapticHelper.vibrateTick(it) }

        // If voice recorder is running, single tap immediately opens recorder
        if (currentState == State.RECORDER_COMPACT && isRecordingActive) {
            if (recorderIntent != null) {
                try {
                    recorderIntent?.send()
                    return
                } catch (e: Exception) {
                    // fallthrough to expand
                }
            }
        }

        // If external clock / timer is running, single tap opens clock
        if (currentState == State.TIMER_COMPACT && isExternalClockActive) {
            if (externalClockIntent != null) {
                try {
                    externalClockIntent?.send()
                    return
                } catch (e: Exception) {
                    // fallthrough to expand
                }
            }
        }

        // If a notification is active, clicking the island capsule OPENS the notifying app immediately!
        if (hasActiveNotification) {
            handleNotificationLaunch(context)
            return
        }

        if (currentState == State.EXPANDED) {
            collapseIsland()
        } else {
            expandIsland()
        }
    }

    fun temporarilyHideIsland(durationMs: Long = 6000) {
        val view = overlayView ?: return
        mainHandler.post {
            view.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(160)
                .withEndAction {
                    view.visibility = View.INVISIBLE
                    mainHandler.postDelayed({
                        if (isAttached && overlayView != null) {
                            view.visibility = View.VISIBLE
                            view.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .start()
                        }
                    }, durationMs)
                }
                .start()
        }
    }

    fun onRecorderNotification(
        appName: String,
        title: String,
        text: String,
        appIcon: Drawable?,
        intent: PendingIntent?
    ) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)
        isRecordingActive = true
        recorderAppName = appName
        recorderTitle = title
        recorderText = text
        recorderAppIcon = appIcon
        recorderIntent = intent

        mainHandler.post {
            compactLeftIcon?.setImageResource(R.drawable.ic_dot)
            compactLeftIcon?.setColorFilter(Color.parseColor("#D71920"))
            compactLeftIcon?.visibility = View.VISIBLE
            compactLeftText?.visibility = View.GONE
            centerCutoutSpacer?.visibility = View.VISIBLE

            val timeRegex = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
            val extracted = timeRegex.find(text)?.value
                ?: timeRegex.find(title)?.value
                ?: "REC"
            compactRightText?.text = extracted
            compactRightText?.setTextColor(Color.parseColor("#D71920"))
            compactRightText?.visibility = View.VISIBLE
            compactEqualizer?.visibility = View.GONE
            islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)

            updateState(State.RECORDER_COMPACT)
        }
    }

    fun clearRecorderNotification() {
        isRecordingActive = false
        mainHandler.post {
            if (currentState == State.RECORDER_COMPACT) {
                restorePreviousMediaOrIdle()
            }
        }
    }

    fun onExternalClockNotification(
        appName: String,
        title: String,
        text: String,
        isTimer: Boolean,
        appIcon: Drawable?,
        intent: PendingIntent?
    ) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)
        isExternalClockActive = true
        externalClockAppName = appName
        externalClockTitle = title
        externalClockText = text
        isExternalClockTimer = isTimer
        externalClockAppIcon = appIcon
        externalClockIntent = intent

        mainHandler.post {
            compactLeftIcon?.setImageResource(R.drawable.ic_timer)
            compactLeftIcon?.setColorFilter(if (isTimer) Color.parseColor("#D71920") else Color.WHITE)
            compactLeftIcon?.visibility = View.VISIBLE
            compactLeftText?.visibility = View.GONE
            centerCutoutSpacer?.visibility = View.VISIBLE

            val timeRegex = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
            val extracted = timeRegex.find(text)?.value
                ?: timeRegex.find(title)?.value
                ?: (if (isTimer) "TIMER" else "STOPWATCH")
            compactRightText?.text = extracted
            compactRightText?.setTextColor(Color.WHITE)
            compactRightText?.visibility = View.VISIBLE
            compactEqualizer?.visibility = View.GONE
            islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)

            updateState(State.TIMER_COMPACT)
        }
    }

    fun clearExternalClockNotification() {
        isExternalClockActive = false
        mainHandler.post {
            if (currentState == State.TIMER_COMPACT && IslandTimerManager.currentMode == IslandTimerManager.Mode.NONE) {
                restorePreviousMediaOrIdle()
            }
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
            params.y = if (prefs.attachToTop) -dpToPx(context, 3) else dpToPx(context, yOffset)
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating layout params: ${e.message}")
            }
        }

        applyCustomStyling(context)
    }

    fun clearMediaPlayback() {
        currentTrackTitle = ""
        currentArtistName = ""
        currentPackageName = ""
        isMusicPlaying = false
        currentArtwork = null
        currentAppIcon = null
        pausedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        pausedTimeoutRunnable = null

        mainHandler.post {
            compactEqualizer?.isPlaying = false
            compactEqualizer?.visibility = View.GONE
            expandedEqualizer?.isPlaying = false
            btnPlayPause?.setImageResource(R.drawable.ic_play)
            updateSplitPillState()

            if (currentState == State.MUSIC_COMPACT || currentState == State.MUSIC_PAUSED_COMPACT ||
                (currentState == State.EXPANDED && currentCricketScore.isEmpty())
            ) {
                restorePreviousMediaOrIdle()
            }
        }
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
        currentPackageName = packageName
        isMusicPlaying = isPlaying
        currentArtwork = artwork
        currentAppIcon = appIcon

        mainHandler.post {
            try {
                if (title.isNotEmpty()) {
                    // Cancel any previous paused timeout
                    pausedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

                    // Setup artwork
                    if (artwork != null) {
                        compactLeftIcon?.setImageBitmap(artwork)
                        expandedArtwork?.setImageBitmap(artwork)
                    } else if (appIcon != null) {
                        compactLeftIcon?.setImageDrawable(appIcon)
                        expandedArtwork?.setImageDrawable(appIcon)
                    } else {
                        compactLeftIcon?.setImageResource(R.drawable.ic_music)
                        expandedArtwork?.setImageResource(R.drawable.ic_music)
                    }
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    compactRightText?.visibility = View.GONE

                    // Setup expanded media card
                    if (appIcon != null) {
                        expandedAppIcon?.setImageDrawable(appIcon)
                    } else {
                        expandedAppIcon?.setImageResource(R.drawable.ic_music)
                    }
                    expandedSourceAppText?.text = appName.uppercase()
                    expandedTitleText?.text = title
                    expandedArtistText?.text = artist
                    mediaControlsRow?.visibility = View.VISIBLE
                    expandedNotificationBody?.visibility = View.GONE
                    expandedArtwork?.visibility = View.VISIBLE

                    if (isPlaying) {
                        // PLAYING STATE:
                        compactEqualizer?.visibility = View.VISIBLE
                        compactEqualizer?.isPlaying = true
                        expandedEqualizer?.isPlaying = true
                        btnPlayPause?.setImageResource(R.drawable.ic_pause)

                        islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)

                        if (currentState != State.EXPANDED && currentState != State.NOTIFICATION_COMPACT) {
                            updateState(State.MUSIC_COMPACT)
                        }
                        updateSplitPillState()
                    } else {
                        // PAUSED STATE: Show Play button and persist for 25s
                        compactEqualizer?.visibility = View.VISIBLE
                        compactEqualizer?.isPlaying = false
                        expandedEqualizer?.isPlaying = false
                        btnPlayPause?.setImageResource(R.drawable.ic_play)

                        islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)

                        if (currentState != State.EXPANDED && currentState != State.NOTIFICATION_COMPACT) {
                            updateState(State.MUSIC_PAUSED_COMPACT)
                        }
                        updateSplitPillState()

                        // Graceful timeout: auto-collapse if paused without activity for 25 seconds
                        pausedTimeoutRunnable = Runnable {
                            if (!isMusicPlaying) {
                                clearMediaPlayback()
                            }
                        }
                        mainHandler.postDelayed(pausedTimeoutRunnable!!, 25000)
                    }
                } else if (!isPlaying && (currentState == State.MUSIC_COMPACT || currentState == State.MUSIC_PAUSED_COMPACT)) {
                    clearMediaPlayback()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onMediaStateChanged UI update: ${e.message}", e)
            }
        }
    }

    fun onChargingChanged(isCharging: Boolean, batteryPercent: Int, isFirstConnect: Boolean = false) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)
        if (!prefs.chargingIslandEnabled) return

        currentBatteryPercent = batteryPercent
        isDeviceCharging = isCharging

        mainHandler.post {
            try {
                if (isCharging) {
                    expandedAppIcon?.setImageResource(R.drawable.ic_bolt)
                    expandedArtwork?.setImageResource(R.drawable.ic_bolt)
                    expandedArtwork?.visibility = View.VISIBLE
                    expandedSourceAppText?.text = "FAST CHARGING // GLYPH"
                    expandedTitleText?.text = "BATTERY CHARGING"
                    expandedArtistText?.text = "$batteryPercent% • Connected to Power"
                    expandedNotificationBody?.text = "$batteryPercent% • Connected to Power"

                    if (currentState == State.CHARGING_COMPACT) {
                        compactRightText?.text = "$batteryPercent%"
                    }

                    // Dual Island Split Pill update!
                    updateSplitPillState()

                    // ONLY flash charging banner when cable is FIRST plugged in!
                    // Never interrupt active media / YouTube when already plugged in!
                    if (isFirstConnect) {
                        if (prefs.hapticsEnabled) {
                            NothingHapticHelper.vibratePop(context)
                        }
                        displayChargingCompact()
                        playChargingAnimation(batteryPercent)

                        mainHandler.postDelayed({
                            if (currentState == State.CHARGING_COMPACT) {
                                restorePreviousMediaOrIdle()
                            }
                        }, 3500)
                    }
                } else {
                    // Charger disconnected
                    updateSplitPillState()
                    if (currentState == State.CHARGING_COMPACT) {
                        restorePreviousMediaOrIdle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onChargingChanged: ${e.message}", e)
            }
        }
    }

    private fun updateSplitPillState() {
        if (currentState == State.EXPANDED) {
            islandSubCapsule?.visibility = View.GONE
            return
        }

        if (isMusicPlaying && isDeviceCharging) {
            islandSubCapsule?.visibility = View.VISIBLE
            subCapsuleIcon?.setImageResource(R.drawable.ic_bolt)
            subCapsuleIcon?.setColorFilter(Color.parseColor("#00E676"))
            subCapsuleText?.text = "$currentBatteryPercent%"
            subCapsuleText?.visibility = View.VISIBLE
        } else if (isMusicPlaying && IslandTimerManager.currentMode != IslandTimerManager.Mode.NONE) {
            islandSubCapsule?.visibility = View.VISIBLE
            subCapsuleIcon?.setImageResource(R.drawable.ic_timer)
            subCapsuleIcon?.setColorFilter(Color.parseColor("#D71920"))
            subCapsuleText?.text = if (IslandTimerManager.currentMode == IslandTimerManager.Mode.TIMER) {
                IslandTimerManager.getTimerDisplayString()
            } else {
                IslandTimerManager.getStopwatchDisplayString().take(5)
            }
            subCapsuleText?.visibility = View.VISIBLE
        } else if (isMusicPlaying && IslandCallManager.currentState != IslandCallManager.CallState.NONE) {
            islandSubCapsule?.visibility = View.VISIBLE
            subCapsuleIcon?.setImageResource(R.drawable.ic_call)
            subCapsuleIcon?.setColorFilter(Color.parseColor("#00E676"))
            subCapsuleText?.text = IslandCallManager.getDurationString()
            subCapsuleText?.visibility = View.VISIBLE
        } else {
            islandSubCapsule?.visibility = View.GONE
        }
    }

    private fun displayDeviceEvent(title: String, subtitle: String) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)

        deviceEventTitle = title
        deviceEventSubtitle = subtitle

        deviceEventDismissRunnable?.let { mainHandler.removeCallbacks(it) }

        compactLeftIcon?.setImageResource(R.drawable.ic_dot)
        compactLeftIcon?.setColorFilter(Color.parseColor("#00E676"))
        compactLeftIcon?.visibility = View.VISIBLE
        compactLeftText?.visibility = View.GONE
        centerCutoutSpacer?.visibility = View.VISIBLE
        compactRightText?.text = title
        compactRightText?.visibility = View.VISIBLE
        compactEqualizer?.visibility = View.GONE

        updateState(State.DEVICE_EVENT_COMPACT)
        playChargingAnimation(0)

        deviceEventDismissRunnable = Runnable {
            if (currentState == State.DEVICE_EVENT_COMPACT) {
                restorePreviousMediaOrIdle()
            }
        }
        mainHandler.postDelayed(deviceEventDismissRunnable!!, 3500)
    }

    private fun playChargingAnimation(batteryPercent: Int) {
        val capsule = islandCapsule ?: return

        // 1. Morphing scale bounce animation
        capsule.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(2.0f))
            .withEndAction {
                capsule.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(180)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            .start()

        // 2. Pulse bolt icon
        compactLeftIcon?.let { icon ->
            icon.setColorFilter(Color.parseColor("#00E676"))
            icon.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(200)
                .withEndAction {
                    icon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
                .start()
        }

        // 3. Sub-capsule pop animation if split
        islandSubCapsule?.let { sub ->
            if (sub.visibility == View.VISIBLE) {
                sub.scaleX = 0.5f
                sub.scaleY = 0.5f
                sub.alpha = 0f
                sub.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(250)
                    .setInterpolator(OvershootInterpolator(1.5f))
                    .start()
            }
        }
    }

    private fun displayChargingCompact() {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)

        compactLeftIcon?.setImageResource(R.drawable.ic_bolt)
        compactLeftIcon?.visibility = View.VISIBLE
        compactLeftText?.visibility = View.GONE

        centerCutoutSpacer?.visibility = View.VISIBLE

        compactRightText?.text = "$currentBatteryPercent%"
        compactRightText?.visibility = View.VISIBLE

        // STOP and HIDE equalizer during charging
        compactEqualizer?.isPlaying = false
        compactEqualizer?.visibility = View.GONE

        islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
        updateState(State.CHARGING_COMPACT)
    }

    fun onNotificationReceived(
        appName: String,
        title: String,
        content: String,
        appIcon: Drawable?,
        packageName: String = "",
        contentIntent: PendingIntent? = null,
        remoteInput: RemoteInput? = null,
        replyIntent: PendingIntent? = null
    ) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)
        if (!prefs.notificationIslandEnabled) return

        activeNotificationAppName = appName
        activeNotificationTitle = title
        activeNotificationContent = content
        activeNotificationPackage = packageName
        activeNotificationIcon = appIcon
        activeNotificationPendingIntent = contentIntent
        activeRemoteInput = remoteInput
        activeReplyIntent = replyIntent
        hasActiveNotification = true

        mainHandler.post {
            try {
                notificationDismissRunnable?.let { mainHandler.removeCallbacks(it) }

                // 1. Compact View: App Icon + preview snippet
                if (appIcon != null) {
                    compactLeftIcon?.setImageDrawable(appIcon)
                } else {
                    compactLeftIcon?.setImageResource(R.drawable.ic_bell)
                }
                compactLeftIcon?.visibility = View.VISIBLE
                compactLeftText?.visibility = View.GONE

                val snippet = when {
                    title.isNotEmpty() && content.isNotEmpty() -> "$title: $content"
                    title.isNotEmpty() -> title
                    else -> content
                }
                compactRightText?.text = snippet
                compactRightText?.visibility = View.VISIBLE

                // CRITICAL BUG FIX: Force stop & hide equalizer animation during notifications!
                compactEqualizer?.isPlaying = false
                compactEqualizer?.visibility = View.GONE
                expandedEqualizer?.isPlaying = false

                // Sleek width
                islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)

                // 2. Expanded View: Full Message Body
                if (appIcon != null) {
                    expandedAppIcon?.setImageDrawable(appIcon)
                    expandedArtwork?.setImageDrawable(appIcon)
                    expandedArtwork?.visibility = View.VISIBLE
                } else {
                    expandedAppIcon?.setImageResource(R.drawable.ic_bell)
                    expandedArtwork?.visibility = View.GONE
                }

                expandedSourceAppText?.text = "${appName.uppercase()} // NOTIFICATION"
                expandedTitleText?.text = title.ifEmpty { appName }
                expandedArtistText?.text = "Tap to open ${appName.ifEmpty { "App" }}"
                mediaControlsRow?.visibility = View.GONE
                expandedNotificationBody?.visibility = View.VISIBLE
                expandedNotificationBody?.text = if (content.isNotEmpty()) content else title

                updateState(State.NOTIFICATION_COMPACT)

                // Auto dismiss notification banner after 6.0 seconds and revert to music or charging
                notificationDismissRunnable = Runnable {
                    clearActiveNotification()
                    dismissNotificationAndRestore()
                }
                mainHandler.postDelayed(notificationDismissRunnable!!, 6000)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onNotificationReceived: ${e.message}", e)
            }
        }
    }

    fun onNotificationDismissed(packageName: String = "") {
        if (packageName.isNotEmpty() && packageName != activeNotificationPackage) {
            return
        }
        clearActiveNotification()
        mainHandler.post {
            dismissNotificationAndRestore()
        }
    }

    private fun dismissNotificationAndRestore() {
        notificationDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        notificationDismissRunnable = null

        if (currentState == State.NOTIFICATION_COMPACT) {
            restorePreviousMediaOrIdle()
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

        cricketExpirationRunnable?.let { mainHandler.removeCallbacks(it) }
        // Auto-expire cricket score after 60 seconds so it doesn't stay permanently stuck
        cricketExpirationRunnable = Runnable {
            currentCricketScore = ""
            currentCricketMatch = ""
            currentCricketAppName = ""
            currentCricketAppIcon = null
            if (currentState == State.CRICKET_COMPACT) {
                restorePreviousMediaOrIdle()
            }
        }
        mainHandler.postDelayed(cricketExpirationRunnable!!, 60000)

        mainHandler.post {
            try {
                if (currentState != State.NOTIFICATION_COMPACT && currentState != State.EXPANDED) {
                    displayCricketCompact()

                    if (isMusicPlaying && currentTrackTitle.isNotEmpty()) {
                        mainHandler.postDelayed({
                            if (currentState == State.CRICKET_COMPACT) {
                                restorePreviousMediaOrIdle()
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

        val compactScore = Regex("""\d+/\d+""").find(currentCricketScore)?.value ?: currentCricketScore.take(8)
        compactRightText?.text = compactScore
        compactRightText?.setTextColor(Color.WHITE)
        compactRightText?.visibility = View.VISIBLE

        // STOP and HIDE equalizer during cricket score flash
        compactEqualizer?.isPlaying = false
        compactEqualizer?.visibility = View.GONE

        islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)

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

        expandedSourceAppText?.text = "LIVE CRICKET // ${currentCricketAppName.uppercase()}"
        expandedTitleText?.text = currentCricketMatch
        expandedArtistText?.text = currentCricketScore
        mediaControlsRow?.visibility = View.GONE
        expandedNotificationBody?.visibility = View.VISIBLE
        expandedNotificationBody?.text = "$currentCricketMatch\n$currentCricketScore"

        updateState(State.CRICKET_COMPACT)
    }

    private fun restorePreviousMediaOrIdle() {
        clearActiveNotification()
        if (IslandCallManager.currentState != IslandCallManager.CallState.NONE) {
            updateState(State.CALL_COMPACT)
        } else if (isRecordingActive) {
            updateState(State.RECORDER_COMPACT)
        } else if (NavigationManager.isNavigating) {
            updateState(State.NAVIGATION_COMPACT)
        } else if (isExternalClockActive || IslandTimerManager.currentMode != IslandTimerManager.Mode.NONE) {
            updateState(State.TIMER_COMPACT)
        } else if (currentTrackTitle.isNotEmpty() && (isMusicPlaying || pausedTimeoutRunnable != null)) {
            restoreMusicCompactState(isPlaying = isMusicPlaying)
        } else if (currentCricketScore.isNotEmpty()) {
            displayCricketCompact()
        } else {
            updateState(State.IDLE)
        }
    }

    private fun restoreMusicCompactState(isPlaying: Boolean) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)

        compactLeftIcon?.clearColorFilter()
        if (currentArtwork != null) {
            compactLeftIcon?.setImageBitmap(currentArtwork)
        } else if (currentAppIcon != null) {
            compactLeftIcon?.setImageDrawable(currentAppIcon)
        } else {
            compactLeftIcon?.setImageResource(R.drawable.ic_music)
        }
        compactLeftIcon?.visibility = View.VISIBLE
        compactLeftText?.visibility = View.GONE

        compactRightText?.visibility = View.GONE
        compactEqualizer?.visibility = View.VISIBLE
        compactEqualizer?.isPlaying = isPlaying

        islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)

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
        btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        expandedEqualizer?.isPlaying = isPlaying

        updateState(if (isPlaying) State.MUSIC_COMPACT else State.MUSIC_PAUSED_COMPACT)
    }

    private fun updateState(newState: State) {
        currentState = newState

        mainHandler.post {
            try {
                val view = overlayView ?: return@post
                val capsule = islandCapsule ?: return@post
                if (!isAttached) return@post

                islandRoot?.isExpandedState = (newState == State.EXPANDED)

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

                layoutParams?.let { params ->
                    windowManager?.updateViewLayout(view, params)
                }
                view.requestLayout()
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateState: ${e.message}", e)
            }
        }
    }

    private fun applyStateVisibilities(state: State) {
        val context = appContext ?: return
        val prefs = IslandPreferences(context)

        try {
            updateSplitPillState()
            if (state != State.EXPANDED) {
                applyCustomStyling(context)
            }
            when (state) {
                State.IDLE -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE

                    compactLeftIcon?.clearColorFilter()
                    compactLeftIcon?.setImageResource(R.drawable.ic_dot)
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE

                    centerCutoutSpacer?.visibility = View.VISIBLE

                    compactRightText?.text = "NOTHING"
                    compactRightText?.setTextColor(Color.WHITE)
                    compactRightText?.visibility = View.VISIBLE

                    compactEqualizer?.isPlaying = false
                    compactEqualizer?.visibility = View.GONE

                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.MUSIC_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.clearColorFilter()
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.visibility = View.GONE

                    compactEqualizer?.visibility = View.VISIBLE
                    compactEqualizer?.isPlaying = true

                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.MUSIC_PAUSED_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.clearColorFilter()
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.visibility = View.GONE

                    compactEqualizer?.visibility = View.VISIBLE
                    compactEqualizer?.isPlaying = false

                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.CHARGING_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.setImageResource(R.drawable.ic_bolt)
                    compactLeftIcon?.setColorFilter(Color.parseColor("#00E676"))
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.text = "$currentBatteryPercent%"
                    compactRightText?.visibility = View.VISIBLE

                    compactEqualizer?.isPlaying = false
                    compactEqualizer?.visibility = View.GONE

                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.NOTIFICATION_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.clearColorFilter()
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.visibility = View.VISIBLE

                    // CRITICAL: Strictly stopped and hidden
                    compactEqualizer?.isPlaying = false
                    compactEqualizer?.visibility = View.GONE

                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.CRICKET_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.clearColorFilter()
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE
                    compactRightText?.visibility = View.VISIBLE

                    compactEqualizer?.isPlaying = false
                    compactEqualizer?.visibility = View.GONE

                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.CALL_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    if (IslandCallManager.appIcon != null) {
                        compactLeftIcon?.setImageDrawable(IslandCallManager.appIcon)
                    } else {
                        compactLeftIcon?.setImageResource(R.drawable.ic_call)
                    }
                    compactLeftIcon?.setColorFilter(Color.parseColor("#00E676"))
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE

                    compactRightText?.text = if (IslandCallManager.currentState == IslandCallManager.CallState.INCOMING) "CALL" else IslandCallManager.getDurationString()
                    compactRightText?.setTextColor(Color.parseColor("#00E676"))
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE
                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.TIMER_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.setImageResource(R.drawable.ic_timer)
                    compactLeftIcon?.setColorFilter(if (isExternalClockActive && !isExternalClockTimer) Color.WHITE else Color.parseColor("#D71920"))
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE

                    compactRightText?.text = if (isExternalClockActive) {
                        val timeRegex = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
                        timeRegex.find(externalClockText)?.value
                            ?: timeRegex.find(externalClockTitle)?.value
                            ?: (if (isExternalClockTimer) "TIMER" else "STOPWATCH")
                    } else if (IslandTimerManager.currentMode == IslandTimerManager.Mode.TIMER) {
                        IslandTimerManager.getTimerDisplayString()
                    } else {
                        IslandTimerManager.getStopwatchDisplayString()
                    }
                    compactRightText?.setTextColor(Color.WHITE)
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE
                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.RECORDER_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.setImageResource(R.drawable.ic_dot)
                    compactLeftIcon?.setColorFilter(Color.parseColor("#D71920"))
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE

                    val timeRegex = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
                    val extracted = timeRegex.find(recorderText)?.value
                        ?: timeRegex.find(recorderTitle)?.value
                        ?: "REC"
                    compactRightText?.text = extracted
                    compactRightText?.setTextColor(Color.parseColor("#D71920"))
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE
                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.NAVIGATION_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    if (NavigationManager.appIcon != null) {
                        compactLeftIcon?.setImageDrawable(NavigationManager.appIcon)
                        compactLeftIcon?.clearColorFilter()
                        compactLeftIcon?.visibility = View.VISIBLE
                    } else {
                        compactLeftIcon?.setImageResource(R.drawable.ic_navigation)
                        compactLeftIcon?.setColorFilter(Color.parseColor("#00E676"))
                        compactLeftIcon?.visibility = View.VISIBLE
                    }
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE

                    compactRightText?.text = NavigationManager.distanceText
                    compactRightText?.setTextColor(Color.WHITE)
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE
                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.DEVICE_EVENT_COMPACT -> {
                    compactContainer?.visibility = View.VISIBLE
                    expandedContainer?.visibility = View.GONE
                    compactLeftIcon?.setImageResource(R.drawable.ic_dot)
                    compactLeftIcon?.setColorFilter(Color.parseColor("#00E676"))
                    compactLeftIcon?.visibility = View.VISIBLE
                    compactLeftText?.visibility = View.GONE
                    centerCutoutSpacer?.visibility = View.VISIBLE

                    compactRightText?.text = deviceEventTitle
                    compactRightText?.setTextColor(Color.WHITE)
                    compactRightText?.visibility = View.VISIBLE
                    compactEqualizer?.visibility = View.GONE
                    islandCapsule?.minimumWidth = dpToPx(context, prefs.islandWidth)
                }
                State.EXPANDED -> {
                    compactContainer?.visibility = View.GONE
                    expandedContainer?.visibility = View.VISIBLE

                    // Multi-event switcher dots visibility
                    val activeCount = getActiveEvents().size
                    if (activeCount >= 2) {
                        eventIndicatorDots?.visibility = View.VISIBLE
                    } else {
                        eventIndicatorDots?.visibility = View.GONE
                    }

                    if (IslandCallManager.currentState != IslandCallManager.CallState.NONE) {
                        // 1. Call Expanded Card
                        mediaControlsRow?.visibility = View.GONE
                        quickReplyRow?.visibility = View.GONE
                        timerControlsRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.GONE

                        if (IslandCallManager.appIcon != null) {
                            expandedAppIcon?.setImageDrawable(IslandCallManager.appIcon)
                            expandedArtwork?.setImageDrawable(IslandCallManager.appIcon)
                        } else {
                            expandedAppIcon?.setImageResource(R.drawable.ic_call)
                            expandedArtwork?.setImageResource(R.drawable.ic_call)
                        }
                        expandedArtwork?.visibility = View.VISIBLE

                        expandedSourceAppText?.text = "${IslandCallManager.appName.uppercase()} // CALL"
                        expandedTitleText?.text = IslandCallManager.callerName
                        expandedArtistText?.text = if (IslandCallManager.currentState == IslandCallManager.CallState.INCOMING) {
                            "Incoming Call • Tap Answer"
                        } else {
                            "Active Call • ${IslandCallManager.getDurationString()}"
                        }

                        callControlsRow?.visibility = View.VISIBLE
                        btnAnswerCall?.visibility = if (IslandCallManager.currentState == IslandCallManager.CallState.INCOMING) View.VISIBLE else View.GONE
                        btnDeclineCall?.visibility = View.VISIBLE
                    } else if (isRecordingActive) {
                        // Recorder Expanded Card
                        mediaControlsRow?.visibility = View.GONE
                        quickReplyRow?.visibility = View.GONE
                        timerControlsRow?.visibility = View.GONE
                        callControlsRow?.visibility = View.GONE
                        expandedEqualizer?.visibility = View.GONE

                        if (recorderAppIcon != null) {
                            expandedAppIcon?.setImageDrawable(recorderAppIcon)
                            expandedArtwork?.setImageDrawable(recorderAppIcon)
                        } else {
                            expandedAppIcon?.setImageResource(R.drawable.ic_dot)
                            expandedAppIcon?.setColorFilter(Color.parseColor("#D71920"))
                            expandedArtwork?.setImageResource(R.drawable.ic_dot)
                            expandedArtwork?.setColorFilter(Color.parseColor("#D71920"))
                        }
                        expandedArtwork?.visibility = View.VISIBLE
                        expandedSourceAppText?.text = "VOICE RECORDER // ${recorderAppName.uppercase()}"
                        expandedTitleText?.text = recorderTitle.ifEmpty { "RECORDING" }
                        expandedArtistText?.text = recorderText.ifEmpty { "Audio recording active" }
                        expandedNotificationBody?.visibility = View.VISIBLE
                        expandedNotificationBody?.text = "Recording in progress • Tap to open"
                    } else if (isExternalClockActive) {
                        // External Clock / Timer Expanded Card
                        mediaControlsRow?.visibility = View.GONE
                        quickReplyRow?.visibility = View.GONE
                        timerControlsRow?.visibility = View.GONE
                        callControlsRow?.visibility = View.GONE
                        expandedEqualizer?.visibility = View.GONE

                        if (externalClockAppIcon != null) {
                            expandedAppIcon?.setImageDrawable(externalClockAppIcon)
                            expandedArtwork?.setImageDrawable(externalClockAppIcon)
                        } else {
                            expandedAppIcon?.setImageResource(R.drawable.ic_timer)
                            expandedArtwork?.setImageResource(R.drawable.ic_timer)
                        }
                        expandedArtwork?.visibility = View.VISIBLE
                        expandedSourceAppText?.text = externalClockAppName.uppercase()
                        expandedTitleText?.text = externalClockTitle
                        expandedArtistText?.text = externalClockText
                        expandedNotificationBody?.visibility = View.VISIBLE
                        expandedNotificationBody?.text = externalClockText.ifEmpty { "Timer active • Tap to open" }
                    } else if (NavigationManager.isNavigating) {
                        // 2. Navigation Expanded Card
                        mediaControlsRow?.visibility = View.GONE
                        callControlsRow?.visibility = View.GONE
                        quickReplyRow?.visibility = View.GONE
                        timerControlsRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.GONE

                        if (NavigationManager.appIcon != null) {
                            expandedAppIcon?.setImageDrawable(NavigationManager.appIcon)
                            expandedArtwork?.setImageDrawable(NavigationManager.appIcon)
                        } else {
                            expandedAppIcon?.setImageResource(R.drawable.ic_navigation)
                            expandedArtwork?.setImageResource(R.drawable.ic_navigation)
                        }
                        expandedArtwork?.visibility = View.VISIBLE

                        expandedSourceAppText?.text = "GOOGLE MAPS // NAVIGATION"
                        expandedTitleText?.text = NavigationManager.turnInstruction
                        expandedArtistText?.text = "${NavigationManager.distanceText} • ${NavigationManager.destinationText}"
                    } else if (IslandTimerManager.currentMode != IslandTimerManager.Mode.NONE) {
                        // 3. Timer & Stopwatch Expanded Card
                        mediaControlsRow?.visibility = View.GONE
                        callControlsRow?.visibility = View.GONE
                        quickReplyRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.GONE

                        expandedAppIcon?.setImageResource(R.drawable.ic_timer)
                        expandedArtwork?.setImageResource(R.drawable.ic_timer)
                        expandedArtwork?.visibility = View.VISIBLE

                        expandedSourceAppText?.text = if (IslandTimerManager.currentMode == IslandTimerManager.Mode.TIMER) "NOTHING TIMER" else "NOTHING STOPWATCH"
                        expandedTitleText?.text = if (IslandTimerManager.currentMode == IslandTimerManager.Mode.TIMER) {
                            IslandTimerManager.getTimerDisplayString()
                        } else {
                            IslandTimerManager.getStopwatchDisplayString()
                        }
                        expandedArtistText?.text = if (IslandTimerManager.currentMode == IslandTimerManager.Mode.TIMER) {
                            "Countdown Active"
                        } else {
                            IslandTimerManager.laps.firstOrNull() ?: "Stopwatch Running"
                        }

                        timerControlsRow?.visibility = View.VISIBLE
                        btnTimerPlayPause?.setImageResource(
                            if (IslandTimerManager.isTimerRunning && !IslandTimerManager.isTimerPaused ||
                                IslandTimerManager.isStopwatchRunning && !IslandTimerManager.isStopwatchPaused) {
                                R.drawable.ic_pause
                            } else {
                                R.drawable.ic_play
                            }
                        )
                    } else if (hasActiveNotification && (activeNotificationTitle.isNotEmpty() || activeNotificationContent.isNotEmpty())) {
                        // 4. Notification Card (with Quick Reply if messaging app)
                        mediaControlsRow?.visibility = View.GONE
                        callControlsRow?.visibility = View.GONE
                        timerControlsRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.VISIBLE

                        if (activeNotificationIcon != null) {
                            expandedAppIcon?.setImageDrawable(activeNotificationIcon)
                            expandedArtwork?.setImageDrawable(activeNotificationIcon)
                            expandedArtwork?.visibility = View.VISIBLE
                        } else {
                            expandedAppIcon?.setImageResource(R.drawable.ic_bell)
                            expandedArtwork?.visibility = View.GONE
                        }
                        expandedSourceAppText?.text = "${activeNotificationAppName.uppercase()} // NOTIFICATION"
                        expandedTitleText?.text = activeNotificationTitle.ifEmpty { activeNotificationAppName }
                        expandedArtistText?.text = "Tap to open ${activeNotificationAppName.ifEmpty { "App" }}"
                        expandedNotificationBody?.text = if (activeNotificationContent.isNotEmpty()) activeNotificationContent else activeNotificationTitle

                        if (activeRemoteInput != null) {
                            quickReplyRow?.visibility = View.VISIBLE
                            replyEditText?.requestFocus()
                        } else {
                            quickReplyRow?.visibility = View.GONE
                        }
                    } else if (isMusicPlaying) {
                        mediaControlsRow?.visibility = View.VISIBLE
                        expandedNotificationBody?.visibility = View.GONE
                        btnPlayPause?.setImageResource(R.drawable.ic_pause)
                        expandedEqualizer?.isPlaying = true
                        if (currentArtwork != null) {
                            expandedArtwork?.setImageBitmap(currentArtwork)
                        } else if (currentAppIcon != null) {
                            expandedArtwork?.setImageDrawable(currentAppIcon)
                        } else {
                            expandedArtwork?.setImageResource(R.drawable.ic_music)
                        }
                        expandedArtwork?.visibility = View.VISIBLE
                    } else if (currentTrackTitle.isNotEmpty()) {
                        mediaControlsRow?.visibility = View.VISIBLE
                        expandedNotificationBody?.visibility = View.GONE
                        btnPlayPause?.setImageResource(R.drawable.ic_play)
                        expandedEqualizer?.isPlaying = false
                        if (currentArtwork != null) {
                            expandedArtwork?.setImageBitmap(currentArtwork)
                        } else if (currentAppIcon != null) {
                            expandedArtwork?.setImageDrawable(currentAppIcon)
                        } else {
                            expandedArtwork?.setImageResource(R.drawable.ic_music)
                        }
                        expandedArtwork?.visibility = View.VISIBLE
                    } else if (isDeviceCharging) {
                        mediaControlsRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.VISIBLE
                        expandedAppIcon?.setImageResource(R.drawable.ic_bolt)
                        expandedArtwork?.setImageResource(R.drawable.ic_bolt)
                        expandedArtwork?.visibility = View.VISIBLE
                        expandedSourceAppText?.text = "FAST CHARGING // GLYPH"
                        expandedTitleText?.text = "BATTERY CHARGING"
                        expandedArtistText?.text = "$currentBatteryPercent% • Connected to Power"
                        expandedNotificationBody?.text = "$currentBatteryPercent% • Connected to Power"
                    } else if (currentCricketScore.isNotEmpty()) {
                        mediaControlsRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.VISIBLE
                        if (currentCricketAppIcon != null) {
                            expandedArtwork?.setImageDrawable(currentCricketAppIcon)
                        } else {
                            expandedArtwork?.setImageResource(R.drawable.ic_cricket)
                        }
                        expandedArtwork?.visibility = View.VISIBLE
                        expandedSourceAppText?.text = "LIVE CRICKET // ${currentCricketAppName.uppercase()}"
                        expandedTitleText?.text = currentCricketMatch
                        expandedArtistText?.text = currentCricketScore
                        expandedNotificationBody?.text = "$currentCricketMatch\n$currentCricketScore"
                    } else {
                        mediaControlsRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.VISIBLE
                        expandedAppIcon?.setImageResource(R.drawable.app_logo)
                        expandedArtwork?.setImageResource(R.drawable.app_logo)
                        expandedArtwork?.visibility = View.VISIBLE
                        expandedSourceAppText?.text = "NOTHING OS 3.0"
                        expandedTitleText?.text = "(NOT) ISLAND"
                        expandedArtistText?.text = "System active • Tap to collapse"
                        expandedNotificationBody?.text = "System active • Tap to collapse"
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
        hasActiveNotification = false
        restorePreviousMediaOrIdle()
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
