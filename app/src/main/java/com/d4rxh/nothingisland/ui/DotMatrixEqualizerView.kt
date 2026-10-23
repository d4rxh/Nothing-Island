package com.d4rxh.nothingisland.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

class DotMatrixEqualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D71920") // Nothing Crimson Red
        style = Paint.Style.FILL
    }

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    startAnimation()
                } else {
                    stopAnimation()
                }
                postInvalidateOnAnimation()
            }
        }

    private var animator: ValueAnimator? = null
    private val columnHeights = floatArrayOf(0.4f, 0.8f, 0.5f, 0.9f)
    private val targetHeights = floatArrayOf(0.4f, 0.8f, 0.5f, 0.9f)

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (isPlaying) {
                    for (i in columnHeights.indices) {
                        if (Math.abs(columnHeights[i] - targetHeights[i]) < 0.1f) {
                            targetHeights[i] = Random.nextFloat().coerceIn(0.2f, 1.0f)
                        }
                        columnHeights[i] += (targetHeights[i] - columnHeights[i]) * 0.35f
                    }
                    postInvalidateOnAnimation()
                }
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        for (i in columnHeights.indices) {
            columnHeights[i] = 0.2f
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val cols = 4
            val rows = 5
            val dotRadius = Math.min(w / (cols * 2.5f), h / (rows * 2.5f)).coerceIn(2f, 5f)
            val spacingX = w / cols
            val spacingY = h / rows

            for (c in 0 until cols) {
                val cx = spacingX * c + (spacingX / 2f)
                val litDotsCount = if (isPlaying) {
                    (columnHeights[c] * rows).toInt().coerceIn(1, rows)
                } else {
                    1
                }

                for (r in 0 until rows) {
                    val cy = h - (spacingY * r + (spacingY / 2f))
                    val isLit = r < litDotsCount
                    canvas.drawCircle(cx, cy, dotRadius, if (isLit) dotPaint else inactivePaint)
                }
            }
        } catch (e: Exception) {
            // Ignore rendering exceptions
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
