package com.d4rxh.nothingisland.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class NothingIslandAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Nothing Island Accessibility Service connected! Enabling TYPE_ACCESSIBILITY_OVERLAY for seamless lockscreen visibility")
        IslandOverlayManager.setAccessibilityService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event interception needed - we only use the overlay window capability
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        IslandOverlayManager.setAccessibilityService(null)
    }

    companion object {
        private const val TAG = "NothingIslandA11y"

        var instance: NothingIslandAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
