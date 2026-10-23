package com.d4rxh.nothingisland.service

import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log

object IslandMediaManager {

    private var activeController: MediaController? = null

    fun setActiveController(controller: MediaController?) {
        activeController = controller
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
