package com.d4rxh.nothingisland.service

import android.app.PendingIntent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

object NavigationManager {

    var isNavigating: Boolean = false
        private set

    var turnInstruction: String = ""
        private set

    var distanceText: String = ""
        private set

    var destinationText: String = ""
        private set

    var turnIconSymbol: String = "↑"
        private set

    var appIcon: Drawable? = null
        private set

    var launchIntent: PendingIntent? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    var onNavigationUpdate: (() -> Unit)? = null

    fun onNavigationNotification(
        title: String,
        text: String,
        subText: String = "",
        icon: Drawable? = null,
        intent: PendingIntent? = null
    ) {
        isNavigating = true
        appIcon = icon
        launchIntent = intent

        // Parse turn direction & distance
        // Usually Title is Distance (e.g. "200 m" or "1.5 km") and Text is instruction ("Turn right on Park St")
        // or vice versa
        val combined = "$title $text".lowercase()
        turnIconSymbol = when {
            combined.contains("right") || combined.contains("take exit") -> "↱"
            combined.contains("left") -> "↰"
            combined.contains("u-turn") || combined.contains("uturn") -> "↺"
            combined.contains("straight") || combined.contains("continue") || combined.contains("head") -> "↑"
            else -> "➔"
        }

        val distanceRegex = Regex("""\b\d+(\.\d+)?\s*(m|km|mi|ft)\b""", RegexOption.IGNORE_CASE)
        val distMatch = distanceRegex.find(title) ?: distanceRegex.find(text)

        if (distMatch != null) {
            distanceText = distMatch.value
            turnInstruction = if (distMatch.value in title) text else title
        } else {
            distanceText = title.take(10)
            turnInstruction = text
        }

        destinationText = subText.ifEmpty { "NAVIGATING" }

        mainHandler.post {
            onNavigationUpdate?.invoke()
        }
    }

    fun clearNavigation() {
        if (!isNavigating) return
        isNavigating = false
        turnInstruction = ""
        distanceText = ""
        destinationText = ""
        turnIconSymbol = "↑"
        launchIntent = null

        mainHandler.post {
            onNavigationUpdate?.invoke()
        }
    }
}
