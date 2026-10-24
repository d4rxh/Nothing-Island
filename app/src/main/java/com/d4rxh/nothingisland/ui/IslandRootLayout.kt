package com.d4rxh.nothingisland.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Custom root layout that claims status-bar edge touches with [requestDisallowInterceptTouchEvent],
 * preventing the Android notification shade pull-down gesture from stealing taps on the Dynamic Island.
 */
class IslandRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var isExpandedState: Boolean = false
    var onIslandTap: (() -> Unit)? = null
    var onIslandCollapse: (() -> Unit)? = null

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var isTracking = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                downX = ev.rawX
                downY = ev.rawY
                isTracking = true
                parent?.requestDisallowInterceptTouchEvent(true)

                // When compact, intercept immediately so child spacers/icons don't swallow taps
                // When expanded, allow child media buttons (prev/play/next) to handle touches first
                return !isExpandedState
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                downX = event.rawX
                downY = event.rawY
                isTracking = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTracking) {
                    isTracking = false
                    val duration = SystemClock.uptimeMillis() - downTime
                    val dx = abs(event.rawX - downX)
                    val dy = abs(event.rawY - downY)
                    val density = resources.displayMetrics.density
                    val maxTapDelta = 45 * density

                    if (duration < 600 && dx < maxTapDelta && dy < maxTapDelta) {
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
