package com.d4rxh.nothingisland.service

import android.app.PendingIntent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

object IslandCallManager {

    enum class CallState {
        NONE,
        INCOMING,
        ACTIVE,
        ENDED
    }

    var currentState: CallState = CallState.NONE
        private set

    var callerName: String = ""
        private set

    var callerNumber: String = ""
        private set

    var appName: String = "PHONE"
        private set

    var appIcon: Drawable? = null
        private set

    var answerIntent: PendingIntent? = null
        private set

    var declineIntent: PendingIntent? = null
        private set

    var endIntent: PendingIntent? = null
        private set

    private var callStartTime: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var durationTickerRunnable: Runnable? = null

    var onCallStateChanged: ((CallState) -> Unit)? = null
    var onDurationTick: ((String) -> Unit)? = null

    fun onIncomingCall(
        name: String,
        number: String = "",
        sourceAppName: String = "PHONE",
        sourceAppIcon: Drawable? = null,
        answer: PendingIntent? = null,
        decline: PendingIntent? = null
    ) {
        currentState = CallState.INCOMING
        callerName = name.ifEmpty { "Incoming Call" }
        callerNumber = number
        appName = sourceAppName
        appIcon = sourceAppIcon
        answerIntent = answer
        declineIntent = decline
        endIntent = decline

        stopDurationTicker()
        onCallStateChanged?.invoke(currentState)
    }

    fun onCallActive(
        name: String,
        number: String = "",
        sourceAppName: String = "PHONE",
        sourceAppIcon: Drawable? = null,
        end: PendingIntent? = null,
        startTimeOverride: Long = 0L
    ) {
        currentState = CallState.ACTIVE
        callerName = name.ifEmpty { "Active Call" }
        callerNumber = number
        appName = sourceAppName
        appIcon = sourceAppIcon
        endIntent = end
        callStartTime = if (startTimeOverride > 0L) startTimeOverride else SystemClock.elapsedRealtime()

        startDurationTicker()
        onCallStateChanged?.invoke(currentState)
    }

    fun onCallEnded() {
        if (currentState == CallState.NONE) return
        currentState = CallState.ENDED
        stopDurationTicker()
        onCallStateChanged?.invoke(currentState)

        // Clear after 3 seconds
        mainHandler.postDelayed({
            if (currentState == CallState.ENDED) {
                resetCall()
                onCallStateChanged?.invoke(currentState)
            }
        }, 3000)
    }

    fun answerCall(): Boolean {
        return try {
            answerIntent?.send()
            currentState = CallState.ACTIVE
            callStartTime = SystemClock.elapsedRealtime()
            startDurationTicker()
            onCallStateChanged?.invoke(currentState)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun declineOrEndCall(): Boolean {
        val intent = if (currentState == CallState.INCOMING) declineIntent else endIntent
        return try {
            intent?.send()
            onCallEnded()
            true
        } catch (e: Exception) {
            onCallEnded()
            false
        }
    }

    fun getDurationString(): String {
        if (callStartTime <= 0L || currentState != CallState.ACTIVE) return "00:00"
        val elapsedSec = (SystemClock.elapsedRealtime() - callStartTime) / 1000
        val mins = elapsedSec / 60
        val secs = elapsedSec % 60
        return if (mins >= 60) {
            val hrs = mins / 60
            val remMins = mins % 60
            String.format("%02d:%02d:%02d", hrs, remMins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    private fun startDurationTicker() {
        stopDurationTicker()
        durationTickerRunnable = object : Runnable {
            override fun run() {
                if (currentState == CallState.ACTIVE) {
                    onDurationTick?.invoke(getDurationString())
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }
        mainHandler.post(durationTickerRunnable!!)
    }

    private fun stopDurationTicker() {
        durationTickerRunnable?.let { mainHandler.removeCallbacks(it) }
        durationTickerRunnable = null
    }

    private fun resetCall() {
        currentState = CallState.NONE
        callerName = ""
        callerNumber = ""
        appName = "PHONE"
        appIcon = null
        answerIntent = null
        declineIntent = null
        endIntent = null
        callStartTime = 0L
    }
}
