package com.d4rxh.nothingisland.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

object IslandTimerManager {

    enum class Mode {
        NONE,
        TIMER,
        STOPWATCH
    }

    var currentMode: Mode = Mode.NONE
        private set

    // --- Timer State ---
    var totalTimerSeconds: Long = 300L // default 5m
        private set
    var remainingTimerSeconds: Long = 300L
        private set
    var isTimerRunning: Boolean = false
        private set
    var isTimerPaused: Boolean = false
        private set

    private var timerEndTime: Long = 0L
    private var timerPauseRemainingMs: Long = 0L

    // --- Stopwatch State ---
    var isStopwatchRunning: Boolean = false
        private set
    var isStopwatchPaused: Boolean = false
        private set

    private var stopwatchStartTime: Long = 0L
    private var stopwatchAccumulatedMs: Long = 0L
    val laps = mutableListOf<String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tickerRunnable: Runnable? = null

    var onTimerTick: ((remainingSeconds: Long, progress: Float) -> Unit)? = null
    var onTimerCompleted: (() -> Unit)? = null
    var onStopwatchTick: ((formattedTime: String) -> Unit)? = null
    var onStateChanged: ((Mode) -> Unit)? = null

    // --- Timer Controls ---
    fun startTimer(seconds: Long) {
        totalTimerSeconds = if (seconds > 0) seconds else 300L
        remainingTimerSeconds = totalTimerSeconds
        timerEndTime = SystemClock.elapsedRealtime() + (totalTimerSeconds * 1000L)
        isTimerRunning = true
        isTimerPaused = false
        currentMode = Mode.TIMER

        startTicker()
        onStateChanged?.invoke(currentMode)
    }

    fun pauseTimer() {
        if (!isTimerRunning || isTimerPaused) return
        timerPauseRemainingMs = (timerEndTime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        isTimerPaused = true
        stopTicker()
        onStateChanged?.invoke(currentMode)
    }

    fun resumeTimer() {
        if (!isTimerRunning || !isTimerPaused) return
        timerEndTime = SystemClock.elapsedRealtime() + timerPauseRemainingMs
        isTimerPaused = false
        startTicker()
        onStateChanged?.invoke(currentMode)
    }

    fun resetTimer() {
        remainingTimerSeconds = totalTimerSeconds
        timerPauseRemainingMs = totalTimerSeconds * 1000L
        isTimerPaused = true
        onTimerTick?.invoke(remainingTimerSeconds, 1.0f)
        onStateChanged?.invoke(currentMode)
    }

    fun cancelTimer() {
        isTimerRunning = false
        isTimerPaused = false
        remainingTimerSeconds = 0L
        stopTicker()
        if (currentMode == Mode.TIMER) {
            currentMode = Mode.NONE
            onStateChanged?.invoke(currentMode)
        }
    }

    // --- Stopwatch Controls ---
    fun startStopwatch() {
        stopwatchStartTime = SystemClock.elapsedRealtime()
        stopwatchAccumulatedMs = 0L
        laps.clear()
        isStopwatchRunning = true
        isStopwatchPaused = false
        currentMode = Mode.STOPWATCH

        startTicker()
        onStateChanged?.invoke(currentMode)
    }

    fun pauseStopwatch() {
        if (!isStopwatchRunning || isStopwatchPaused) return
        stopwatchAccumulatedMs += (SystemClock.elapsedRealtime() - stopwatchStartTime)
        isStopwatchPaused = true
        stopTicker()
        onStateChanged?.invoke(currentMode)
    }

    fun resumeStopwatch() {
        if (!isStopwatchRunning || !isStopwatchPaused) return
        stopwatchStartTime = SystemClock.elapsedRealtime()
        isStopwatchPaused = false
        startTicker()
        onStateChanged?.invoke(currentMode)
    }

    fun resetStopwatch() {
        stopwatchAccumulatedMs = 0L
        stopwatchStartTime = SystemClock.elapsedRealtime()
        laps.clear()
        onStopwatchTick?.invoke("00:00.0")
        onStateChanged?.invoke(currentMode)
    }

    fun recordLap(): String {
        val formatted = formatStopwatchTime(getStopwatchElapsedMs())
        val lapStr = "Lap ${laps.size + 1}: $formatted"
        laps.add(0, lapStr)
        return lapStr
    }

    fun cancelStopwatch() {
        isStopwatchRunning = false
        isStopwatchPaused = false
        stopTicker()
        if (currentMode == Mode.STOPWATCH) {
            currentMode = Mode.NONE
            onStateChanged?.invoke(currentMode)
        }
    }

    fun getTimerDisplayString(): String {
        val mins = remainingTimerSeconds / 60
        val secs = remainingTimerSeconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    fun getStopwatchElapsedMs(): Long {
        return if (isStopwatchRunning && !isStopwatchPaused) {
            stopwatchAccumulatedMs + (SystemClock.elapsedRealtime() - stopwatchStartTime)
        } else {
            stopwatchAccumulatedMs
        }
    }

    fun getStopwatchDisplayString(): String {
        return formatStopwatchTime(getStopwatchElapsedMs())
    }

    private fun formatStopwatchTime(ms: Long): String {
        val totalSec = ms / 1000
        val tenths = (ms % 1000) / 100
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format("%02d:%02d.%d", mins, secs, tenths)
    }

    private fun startTicker() {
        stopTicker()
        tickerRunnable = object : Runnable {
            override fun run() {
                when (currentMode) {
                    Mode.TIMER -> {
                        if (isTimerRunning && !isTimerPaused) {
                            val remainingMs = timerEndTime - SystemClock.elapsedRealtime()
                            if (remainingMs <= 0) {
                                remainingTimerSeconds = 0L
                                isTimerRunning = false
                                isTimerPaused = false
                                onTimerTick?.invoke(0L, 0.0f)
                                onTimerCompleted?.invoke()
                                currentMode = Mode.NONE
                                onStateChanged?.invoke(currentMode)
                                return
                            }
                            remainingTimerSeconds = (remainingMs + 999) / 1000
                            val progress = remainingMs.toFloat() / (totalTimerSeconds * 1000L).toFloat()
                            onTimerTick?.invoke(remainingTimerSeconds, progress.coerceIn(0f, 1f))
                            mainHandler.postDelayed(this, 500)
                        }
                    }
                    Mode.STOPWATCH -> {
                        if (isStopwatchRunning && !isStopwatchPaused) {
                            onStopwatchTick?.invoke(getStopwatchDisplayString())
                            mainHandler.postDelayed(this, 100)
                        }
                    }
                    Mode.NONE -> {}
                }
            }
        }
        mainHandler.post(tickerRunnable!!)
    }

    private fun stopTicker() {
        tickerRunnable?.let { mainHandler.removeCallbacks(it) }
        tickerRunnable = null
    }
}
