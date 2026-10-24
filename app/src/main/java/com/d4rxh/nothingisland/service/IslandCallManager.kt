package com.d4rxh.nothingisland.service

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent

object IslandCallManager {

    private const val TAG = "IslandCallManager"

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

    var activeDirectCall: Call? = null
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
        decline: PendingIntent? = null,
        directCall: Call? = null
    ) {
        currentState = CallState.INCOMING
        callerName = name.ifEmpty { "Incoming Call" }
        callerNumber = number
        appName = sourceAppName.ifEmpty { "PHONE" }
        if (sourceAppIcon != null) appIcon = sourceAppIcon
        if (answer != null) answerIntent = answer
        if (decline != null) {
            declineIntent = decline
            endIntent = decline
        }
        if (directCall != null) activeDirectCall = directCall

        stopDurationTicker()
        onCallStateChanged?.invoke(currentState)
    }

    fun onCallActive(
        name: String,
        number: String = "",
        sourceAppName: String = "PHONE",
        sourceAppIcon: Drawable? = null,
        end: PendingIntent? = null,
        startTimeOverride: Long = 0L,
        directCall: Call? = null
    ) {
        currentState = CallState.ACTIVE
        callerName = name.ifEmpty { "Active Call" }
        callerNumber = number
        appName = sourceAppName.ifEmpty { "PHONE" }
        if (sourceAppIcon != null) appIcon = sourceAppIcon
        if (end != null) endIntent = end
        if (directCall != null) activeDirectCall = directCall
        callStartTime = if (startTimeOverride > 0L) startTimeOverride else SystemClock.elapsedRealtime()

        startDurationTicker()
        onCallStateChanged?.invoke(currentState)
    }

    fun onCallEnded() {
        if (currentState == CallState.NONE) return
        currentState = CallState.ENDED
        stopDurationTicker()
        onCallStateChanged?.invoke(currentState)

        mainHandler.postDelayed({
            if (currentState == CallState.ENDED) {
                resetCall()
                onCallStateChanged?.invoke(currentState)
            }
        }, 2000)
    }

    /**
     * Universal call answering with 4-tier redundancy:
     * 1. Direct Telecom InCallService Call.answer()
     * 2. Notification Action PendingIntent.send() with background activity start allowed
     * 3. TelecomManager.acceptRingingCall()
     * 4. Universal AudioManager HEADSETHOOK media key simulation
     */
    fun answerCall(context: Context?): Boolean {
        Log.i(TAG, "Attempting to answer call (directCall=${activeDirectCall != null}, answerIntent=${answerIntent != null})")

        // 1. InCallService Direct Call
        if (NothingInCallService.answerCurrentCall()) {
            markCallAnswered()
            return true
        }

        if (activeDirectCall != null) {
            try {
                activeDirectCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
                markCallAnswered()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Direct call answer failed: ${e.message}")
            }
        }

        // 2. Notification Action PendingIntent
        if (answerIntent != null && context != null) {
            try {
                val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityOptions.makeBasic().apply {
                        pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }.toBundle()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ActivityOptions.makeBasic().toBundle()
                } else {
                    null
                }
                answerIntent?.send(context, 0, null, null, null, null, options)
                markCallAnswered()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "answerIntent.send failed: ${e.message}")
                try {
                    answerIntent?.send()
                    markCallAnswered()
                    return true
                } catch (e2: Exception) {
                    Log.w(TAG, "Fallback answerIntent.send failed: ${e2.message}")
                }
            }
        }

        // 3. TelecomManager acceptRingingCall
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.acceptRingingCall()
                markCallAnswered()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "TelecomManager.acceptRingingCall failed: ${e.message}")
            }
        }

        // 4. Universal Headset Hook Media Key event (answers calls on all Android phones)
        if (context != null) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
                    audioManager.dispatchMediaKeyEvent(down)
                    audioManager.dispatchMediaKeyEvent(up)
                    markCallAnswered()
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Media key answer failed: ${e.message}")
            }
        }

        return false
    }

    private fun markCallAnswered() {
        currentState = CallState.ACTIVE
        callStartTime = SystemClock.elapsedRealtime()
        startDurationTicker()
        onCallStateChanged?.invoke(currentState)
    }

    /**
     * Universal call reject/hangup with 4-tier redundancy:
     * 1. Direct Telecom InCallService Call.disconnect() / reject()
     * 2. Notification Action PendingIntent.send()
     * 3. TelecomManager.endCall()
     * 4. Universal AudioManager HEADSETHOOK media key event
     */
    fun declineOrEndCall(context: Context?): Boolean {
        Log.i(TAG, "Attempting to decline/end call")

        if (NothingInCallService.declineOrEndCurrentCall()) {
            onCallEnded()
            return true
        }

        if (activeDirectCall != null) {
            try {
                if (currentState == CallState.INCOMING) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        activeDirectCall?.reject(Call.REJECT_REASON_DECLINED)
                    } else {
                        @Suppress("DEPRECATION")
                        activeDirectCall?.reject(false, null)
                    }
                } else {
                    activeDirectCall?.disconnect()
                }
                onCallEnded()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Direct call decline failed: ${e.message}")
            }
        }

        val targetIntent = if (currentState == CallState.INCOMING) declineIntent else endIntent
        if (targetIntent != null && context != null) {
            try {
                targetIntent.send()
                onCallEnded()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "declineIntent.send failed: ${e.message}")
            }
        }

        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (tm?.endCall() == true) {
                    onCallEnded()
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "TelecomManager.endCall failed: ${e.message}")
            }
        }

        // Universal headset hook fallback
        if (context != null) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
                    audioManager.dispatchMediaKeyEvent(down)
                    audioManager.dispatchMediaKeyEvent(up)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        onCallEnded()
        return false
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
        activeDirectCall = null
        callStartTime = 0L
    }
}
