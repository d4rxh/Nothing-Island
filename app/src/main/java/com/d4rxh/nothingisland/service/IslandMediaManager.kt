package com.d4rxh.nothingisland.service

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log

object IslandMediaManager {

    private const val TAG = "IslandMediaManager"
    private var activeController: MediaController? = null

    fun setActiveController(controller: MediaController?) {
        activeController = controller
    }

    fun hasActiveMedia(): Boolean = activeController != null

    fun isPlaying(): Boolean {
        val state = activeController?.playbackState?.state
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    fun getDurationMs(): Long {
        return try {
            val duration = activeController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            if (duration > 0) duration else 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun getPositionMs(): Long {
        val controller = activeController ?: return 0L
        val state = controller.playbackState ?: return 0L
        var position = state.position
        if (state.state == PlaybackState.STATE_PLAYING) {
            val timeDelta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (timeDelta * state.playbackSpeed).toLong()
        }
        val duration = getDurationMs()
        return if (duration > 0) position.coerceIn(0L, duration) else position.coerceAtLeast(0L)
    }

    fun seekTo(positionMs: Long) {
        try {
            activeController?.transportControls?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking track: ${e.message}")
        }
    }

    fun togglePlayPause() {
        val controller = activeController ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            controller.transportControls?.pause()
        } else {
            controller.transportControls?.play()
        }
    }

    fun skipNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }
}
