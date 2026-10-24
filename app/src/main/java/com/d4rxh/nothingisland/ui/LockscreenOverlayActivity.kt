package com.d4rxh.nothingisland.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.d4rxh.nothingisland.R
import com.d4rxh.nothingisland.data.IslandPreferences
import com.d4rxh.nothingisland.helpers.NothingHapticHelper
import com.d4rxh.nothingisland.helpers.TypefaceHelper
import com.d4rxh.nothingisland.service.IslandMediaManager
import com.d4rxh.nothingisland.service.IslandOverlayManager
import kotlin.math.abs

class LockscreenOverlayActivity : Activity() {

    private var islandCapsule: LinearLayout? = null
    private var compactContainer: LinearLayout? = null
    private var compactLeftIcon: ImageView? = null
    private var compactLeftText: TextView? = null
    private var centerCutoutSpacer: View? = null
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

    private var isExpanded = false
    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isTrackingTouch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        // Android 8.1+ / 14 / 15 Lock Screen visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Notch cutouts layout & transparent bounds
        val prefs = IslandPreferences(this)
        val lp = window.attributes
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = dpToPx(prefs.xOffset)
        lp.y = if (prefs.attachToTop) 0 else dpToPx(prefs.yOffset)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.attributes = lp

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(R.layout.layout_island_overlay)
        initViews(prefs)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViews(prefs: IslandPreferences) {
        islandCapsule = findViewById(R.id.islandCapsule)
        compactContainer = findViewById(R.id.compactContainer)
        compactLeftIcon = findViewById(R.id.compactLeftIcon)
        compactLeftText = findViewById(R.id.compactLeftText)
        centerCutoutSpacer = findViewById(R.id.centerCutoutSpacer)
        compactRightText = findViewById(R.id.compactRightText)
        compactEqualizer = findViewById(R.id.compactEqualizer)

        expandedContainer = findViewById(R.id.expandedContainer)
        expandedHeaderRow = findViewById(R.id.expandedHeaderRow)
        expandedAppIcon = findViewById(R.id.expandedAppIcon)
        expandedSourceAppText = findViewById(R.id.expandedSourceAppText)
        expandedArtwork = findViewById(R.id.expandedArtwork)
        expandedTitleText = findViewById(R.id.expandedTitleText)
        expandedArtistText = findViewById(R.id.expandedArtistText)
        expandedEqualizer = findViewById(R.id.expandedEqualizer)
        mediaControlsRow = findViewById(R.id.mediaControlsRow)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        expandedNotificationBody = findViewById(R.id.expandedNotificationBody)

        compactLeftText?.let { TypefaceHelper.applyNdot(it) }
        compactRightText?.let { TypefaceHelper.applyNdot(it) }
        expandedSourceAppText?.let { TypefaceHelper.applyNdot(it) }
        expandedTitleText?.let { TypefaceHelper.applyNdot(it) }

        compactRightText?.isSelected = true
        expandedTitleText?.isSelected = true

        applyStyling(prefs)

        // Resilient tap detection for lock screen
        val tapListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = SystemClock.uptimeMillis()
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    isTrackingTouch = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isTrackingTouch) {
                        isTrackingTouch = false
                        val duration = SystemClock.uptimeMillis() - touchDownTime
                        val dx = abs(event.rawX - touchDownX)
                        val dy = abs(event.rawY - touchDownY)
                        val density = resources.displayMetrics.density

                        if (duration < 500 && dx < 35 * density && dy < 35 * density) {
                            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(this)
                            toggleExpand()
                            return@OnTouchListener true
                        }
                    }
                    false
                }
                else -> false
            }
        }

        islandCapsule?.setOnTouchListener(tapListener)
        compactContainer?.setOnTouchListener(tapListener)

        expandedHeaderRow?.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            toggleExpand()
        }

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

    private fun applyStyling(prefs: IslandPreferences) {
        val capsule = islandCapsule ?: return
        val radiusPx = dpToPx(prefs.cornerRadius).toFloat()

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            setStroke(dpToPx(1.2f), Color.parseColor("#444444"))
            if (prefs.attachToTop) {
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    radiusPx, radiusPx,
                    radiusPx, radiusPx
                )
            } else {
                cornerRadius = radiusPx
            }
        }
        capsule.background = bgDrawable

        val extraHeight = if (prefs.attachToTop) dpToPx(6) else 0
        compactContainer?.layoutParams = compactContainer?.layoutParams?.apply {
            height = dpToPx(prefs.islandHeight) + extraHeight
        }

        centerCutoutSpacer?.layoutParams = centerCutoutSpacer?.layoutParams?.apply {
            width = dpToPx(prefs.cutoutSize)
            height = dpToPx(prefs.cutoutSize)
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        compactContainer?.visibility = if (isExpanded) View.GONE else View.VISIBLE
        expandedContainer?.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }

    fun syncState(
        state: IslandOverlayManager.State,
        trackTitle: String,
        artistName: String,
        appName: String,
        isPlaying: Boolean,
        artwork: Bitmap?,
        appIcon: Drawable?,
        cricketMatch: String,
        cricketScore: String
    ) {
        runOnUiThread {
            try {
                when (state) {
                    IslandOverlayManager.State.MUSIC_COMPACT -> {
                        compactContainer?.visibility = View.VISIBLE
                        expandedContainer?.visibility = View.GONE
                        isExpanded = false

                        if (artwork != null) {
                            compactLeftIcon?.setImageBitmap(artwork)
                            expandedArtwork?.setImageBitmap(artwork)
                        } else if (appIcon != null) {
                            compactLeftIcon?.setImageDrawable(appIcon)
                            expandedArtwork?.setImageDrawable(appIcon)
                        } else {
                            compactLeftIcon?.setImageResource(R.drawable.ic_music)
                        }
                        compactLeftIcon?.visibility = View.VISIBLE
                        compactLeftText?.visibility = View.GONE

                        compactRightText?.text = trackTitle
                        compactRightText?.visibility = View.VISIBLE
                        compactEqualizer?.visibility = View.VISIBLE
                        compactEqualizer?.isPlaying = isPlaying

                        expandedTitleText?.text = trackTitle
                        expandedArtistText?.text = artistName
                        expandedSourceAppText?.text = appName.uppercase()
                        mediaControlsRow?.visibility = View.VISIBLE
                        expandedNotificationBody?.visibility = View.GONE
                        btnPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                        expandedEqualizer?.isPlaying = isPlaying
                    }
                    IslandOverlayManager.State.CRICKET_COMPACT -> {
                        compactContainer?.visibility = View.VISIBLE
                        expandedContainer?.visibility = View.GONE
                        isExpanded = false

                        if (appIcon != null) {
                            compactLeftIcon?.setImageDrawable(appIcon)
                        } else {
                            compactLeftIcon?.setImageResource(R.drawable.ic_cricket)
                        }
                        compactLeftIcon?.visibility = View.VISIBLE
                        compactLeftText?.visibility = View.GONE

                        compactRightText?.text = cricketScore
                        compactRightText?.visibility = View.VISIBLE
                        compactEqualizer?.visibility = View.GONE

                        expandedTitleText?.text = cricketMatch
                        expandedArtistText?.text = cricketScore
                        mediaControlsRow?.visibility = View.GONE
                        expandedNotificationBody?.visibility = View.VISIBLE
                        expandedNotificationBody?.text = "$cricketMatch\n$cricketScore"
                    }
                    else -> {
                        compactContainer?.visibility = View.VISIBLE
                        expandedContainer?.visibility = View.GONE
                        isExpanded = false

                        compactLeftIcon?.setImageResource(R.drawable.ic_dot)
                        compactLeftIcon?.visibility = View.VISIBLE
                        compactLeftText?.visibility = View.GONE

                        compactRightText?.text = "NOTHING"
                        compactRightText?.visibility = View.VISIBLE
                        compactEqualizer?.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("LockscreenOverlay", "syncState error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun dpToPx(dp: Float): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    companion object {
        var instance: LockscreenOverlayActivity? = null
            private set

        fun dismiss() {
            instance?.let { activity ->
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.finish()
                        activity.overridePendingTransition(0, 0)
                    }
                }
            }
        }
    }
}
