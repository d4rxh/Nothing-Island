package com.d4rxh.nothingisland.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Custom root layout that claims status-bar edge touches with [requestDisallowInterceptTouchEvent],
 * preventing the Android notification shade pull-down gesture from stealing taps on the Dynamic Island.
 * Also handles landscape detection, horizontal swipe-to-minimize gestures, outside touches, and long presses.
 */
class IslandRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var isExpandedState: Boolean = false
    var onIslandTap: (() -> Unit)? = null
    var onIslandLongPress: (() -> Unit)? = null
    var onIslandCollapse: (() -> Unit)? = null
    var onIslandSwipeDismiss: (() -> Unit)? = null
    var onIslandSwipeNext: (() -> Unit)? = null
    var onIslandScreenshotHide: (() -> Unit)? = null
    var onOrientationChanged: (() -> Unit)? = null
    var onOutsideTouch: (() -> Unit)? = null

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var isTracking = false
    private var hasLongPressed = false
    private var lastTapTime = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (isTracking && !isExpandedState) {
            hasLongPressed = true
            onIslandLongPress?.invoke()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onOrientationChanged?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                downX = ev.rawX
                downY = ev.rawY
                isTracking = true
                hasLongPressed = false
                parent?.requestDisallowInterceptTouchEvent(true)

                mainHandler.removeCallbacks(longPressRunnable)
                if (!isExpandedState) {
                    mainHandler.postDelayed(longPressRunnable, 500)
                }

                // When compact, intercept immediately so child spacers/icons don't swallow taps
                // When expanded, allow child media buttons (prev/play/next) to handle touches first
                return !isExpandedState
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (isExpandedState) {
                onOutsideTouch?.invoke()
            }
            return true
        }

        parent?.requestDisallowInterceptTouchEvent(true)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                downX = event.rawX
                downY = event.rawY
                isTracking = true
                hasLongPressed = false
                mainHandler.removeCallbacks(longPressRunnable)
                if (!isExpandedState) {
                    mainHandler.postDelayed(longPressRunnable, 500)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.rawX - downX)
                val dy = abs(event.rawY - downY)
                val density = resources.displayMetrics.density
                if (dx > 12 * density || dy > 12 * density) {
                    mainHandler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                if (isTracking) {
                    isTracking = false
                    if (hasLongPressed) {
                        return true
                    }

                    val duration = SystemClock.uptimeMillis() - downTime
                    val dx = abs(event.rawX - downX)
                    val dy = abs(event.rawY - downY)
                    val density = resources.displayMetrics.density

                    // Vertical swipe up: quick hide for screenshot capture
                    val dySwipe = downY - event.rawY
                    if (dySwipe > 30 * density && dx < 45 * density && !isExpandedState) {
                        onIslandScreenshotHide?.invoke()
                        return true
                    }

                    // Horizontal swipe: switch between active events if multiple, or dismiss
                    val swipeThreshold = 55 * density
                    if (dx > swipeThreshold && dy < 35 * density && !isExpandedState) {
                        if (onIslandSwipeNext != null) {
                            onIslandSwipeNext?.invoke()
                        } else {
                            onIslandSwipeDismiss?.invoke()
                        }
                        return true
                    }

                    // Generous tap detection (even with slight thumb roll)
                    val maxTapDelta = 45 * density
                    if (duration < 600 && dx < maxTapDelta && dy < maxTapDelta) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTapTime < 350 && !isExpandedState) {
                            // Double tap: hide island for 6 seconds for clean screenshot
                            lastTapTime = 0L
                            onIslandScreenshotHide?.invoke()
                            return true
                        }
                        lastTapTime = now

                        if (isExpandedState) {
                            onIslandCollapse?.invoke()
                        } else {
                            onIslandTap?.invoke()
                        }
                        return true
                    }
                }
            }
        }
        return true
    }
}
