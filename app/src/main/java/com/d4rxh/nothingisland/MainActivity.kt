package com.d4rxh.nothingisland

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.d4rxh.nothingisland.data.IslandPreferences
import com.d4rxh.nothingisland.helpers.NothingHapticHelper
import com.d4rxh.nothingisland.helpers.TypefaceHelper
import com.d4rxh.nothingisland.service.IslandOverlayService
import com.d4rxh.nothingisland.ui.DotMatrixEqualizerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: IslandPreferences

    // UI References
    private lateinit var appTitleText: TextView
    private lateinit var switchMasterIsland: MaterialSwitch
    private lateinit var islandStateBadge: TextView

    // Simulator
    private lateinit var simulatedIsland: LinearLayout
    private lateinit var simLeftIcon: ImageView
    private lateinit var simSpacer: View
    private lateinit var simEqualizer: DotMatrixEqualizerView
    private lateinit var simRightText: TextView
    private lateinit var btnTestMusic: Button
    private lateinit var btnTestCharging: Button
    private lateinit var btnTestNotification: Button

    // Permissions
    private lateinit var cardOverlayPermission: LinearLayout
    private lateinit var badgeOverlayPermission: TextView
    private lateinit var cardNotificationPermission: LinearLayout
    private lateinit var badgeNotificationPermission: TextView

    // Calibration
    private lateinit var btnPresetPhone2: Button
    private lateinit var btnPresetPhone1: Button
    private lateinit var sliderYOffset: Slider
    private lateinit var textYOffsetValue: TextView
    private lateinit var sliderWidth: Slider
    private lateinit var textWidthValue: TextView

    // Feature switches
    private lateinit var switchMusicIsland: MaterialSwitch
    private lateinit var switchChargingIsland: MaterialSwitch
    private lateinit var switchNotificationIsland: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = IslandPreferences(this)

        initViews()
        setupTypography()
        setupListeners()
        loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBadges()
        if (prefs.isEnabled && Settings.canDrawOverlays(this)) {
            startIslandService()
        }
    }

    private fun initViews() {
        appTitleText = findViewById(R.id.appTitleText)
        switchMasterIsland = findViewById(R.id.switchMasterIsland)
        islandStateBadge = findViewById(R.id.islandStateBadge)

        simulatedIsland = findViewById(R.id.simulatedIsland)
        simLeftIcon = findViewById(R.id.simLeftIcon)
        simSpacer = findViewById(R.id.simSpacer)
        simEqualizer = findViewById(R.id.simEqualizer)
        simRightText = findViewById(R.id.simRightText)
        btnTestMusic = findViewById(R.id.btnTestMusic)
        btnTestCharging = findViewById(R.id.btnTestCharging)
        btnTestNotification = findViewById(R.id.btnTestNotification)

        cardOverlayPermission = findViewById(R.id.cardOverlayPermission)
        badgeOverlayPermission = findViewById(R.id.badgeOverlayPermission)
        cardNotificationPermission = findViewById(R.id.cardNotificationPermission)
        badgeNotificationPermission = findViewById(R.id.badgeNotificationPermission)

        btnPresetPhone2 = findViewById(R.id.btnPresetPhone2)
        btnPresetPhone1 = findViewById(R.id.btnPresetPhone1)
        sliderYOffset = findViewById(R.id.sliderYOffset)
        textYOffsetValue = findViewById(R.id.textYOffsetValue)
        sliderWidth = findViewById(R.id.sliderWidth)
        textWidthValue = findViewById(R.id.textWidthValue)

        switchMusicIsland = findViewById(R.id.switchMusicIsland)
        switchChargingIsland = findViewById(R.id.switchChargingIsland)
        switchNotificationIsland = findViewById(R.id.switchNotificationIsland)
    }

    private fun setupTypography() {
        TypefaceHelper.applyNdot(
            appTitleText,
            islandStateBadge,
            findViewById(R.id.sectionPreviewTitle),
            textYOffsetValue,
            textWidthValue,
            findViewById(R.id.footerNotice)
        )
    }

    private fun setupListeners() {
        switchMasterIsland.setOnCheckedChangeListener { _, isChecked ->
            prefs.isEnabled = isChecked
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    startIslandService()
                    Toast.makeText(this, "Nothing Island Activated!", Toast.LENGTH_SHORT).show()
                } else {
                    requestOverlayPermission()
                }
            } else {
                stopIslandService()
                Toast.makeText(this, "Nothing Island Disabled", Toast.LENGTH_SHORT).show()
            }
        }

        cardOverlayPermission.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            requestOverlayPermission()
        }

        cardNotificationPermission.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            requestNotificationPermission()
        }

        // Test Triggers
        btnTestMusic.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(this)
            simulateMusicIsland()
        }

        btnTestCharging.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(this)
            simulateChargingIsland()
        }

        btnTestNotification.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibratePop(this)
            simulateNotificationIsland()
        }

        // Calibration presets
        btnPresetPhone2.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            sliderYOffset.value = 18f
            sliderWidth.value = 84f
            prefs.xOffset = 0
            prefs.yOffset = 18
            prefs.baseWidth = 84
            IslandOverlayService.instance?.updatePosition(0, 18, 84, prefs.baseHeight)
        }

        btnPresetPhone1.setOnClickListener {
            if (prefs.hapticsEnabled) NothingHapticHelper.vibrateTick(it)
            sliderYOffset.value = 16f
            sliderWidth.value = 76f
            prefs.xOffset = -130
            prefs.yOffset = 16
            prefs.baseWidth = 76
            IslandOverlayService.instance?.updatePosition(-130, 16, 76, prefs.baseHeight)
        }

        sliderYOffset.addOnChangeListener { _, value, fromUser ->
            textYOffsetValue.text = "${value.toInt()} dp"
            if (fromUser) {
                prefs.yOffset = value.toInt()
                IslandOverlayService.instance?.updatePosition(prefs.xOffset, value.toInt(), prefs.baseWidth, prefs.baseHeight)
            }
        }

        sliderWidth.addOnChangeListener { _, value, fromUser ->
            textWidthValue.text = "${value.toInt()} dp"
            if (fromUser) {
                prefs.baseWidth = value.toInt()
                simSpacer.layoutParams.width = (value * resources.displayMetrics.density).toInt()
                simSpacer.requestLayout()
                IslandOverlayService.instance?.updatePosition(prefs.xOffset, prefs.yOffset, value.toInt(), prefs.baseHeight)
            }
        }

        // Feature toggles
        switchMusicIsland.setOnCheckedChangeListener { _, isChecked ->
            prefs.musicIslandEnabled = isChecked
        }
        switchChargingIsland.setOnCheckedChangeListener { _, isChecked ->
            prefs.chargingIslandEnabled = isChecked
        }
        switchNotificationIsland.setOnCheckedChangeListener { _, isChecked ->
            prefs.notificationIslandEnabled = isChecked
        }
    }

    private fun loadPreferences() {
        switchMasterIsland.isChecked = prefs.isEnabled
        sliderYOffset.value = prefs.yOffset.toFloat().coerceIn(0f, 80f)
        textYOffsetValue.text = "${prefs.yOffset} dp"
        sliderWidth.value = prefs.baseWidth.toFloat().coerceIn(50f, 160f)
        textWidthValue.text = "${prefs.baseWidth} dp"

        switchMusicIsland.isChecked = prefs.musicIslandEnabled
        switchChargingIsland.isChecked = prefs.chargingIslandEnabled
        switchNotificationIsland.isChecked = prefs.notificationIslandEnabled
    }

    private fun updatePermissionBadges() {
        val hasOverlay = Settings.canDrawOverlays(this)
        badgeOverlayPermission.text = if (hasOverlay) "GRANTED" else "GRANT"
        badgeOverlayPermission.setTextColor(
            if (hasOverlay) Color.parseColor("#4CAF50") else Color.parseColor("#D71920")
        )

        val hasNotification = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        badgeNotificationPermission.text = if (hasNotification) "GRANTED" else "GRANT"
        badgeNotificationPermission.setTextColor(
            if (hasNotification) Color.parseColor("#4CAF50") else Color.parseColor("#D71920")
        )
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestNotificationPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun startIslandService() {
        val intent = Intent(this, IslandOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopIslandService() {
        val intent = Intent(this, IslandOverlayService::class.java)
        stopService(intent)
    }

    // Simulator visual tests
    private fun simulateMusicIsland() {
        islandStateBadge.text = "MUSIC // LIVE"
        simLeftIcon.setImageResource(R.drawable.ic_music)
        simLeftIcon.visibility = View.VISIBLE
        simRightText.visibility = View.GONE
        simEqualizer.visibility = View.VISIBLE
        simEqualizer.isPlaying = true

        // Also trigger real overlay test
        IslandOverlayService.instance?.onMediaStateChanged("Starboy", "The Weeknd", true, null)
    }

    private fun simulateChargingIsland() {
        islandStateBadge.text = "FAST CHARGE"
        simLeftIcon.setImageResource(R.drawable.ic_bolt)
        simLeftIcon.visibility = View.VISIBLE
        simEqualizer.visibility = View.GONE
        simRightText.visibility = View.VISIBLE
        simRightText.text = "88%"

        // Also trigger real overlay test
        IslandOverlayService.instance?.onChargingChanged(true, 88)
    }

    private fun simulateNotificationIsland() {
        islandStateBadge.text = "WHATSAPP"
        simLeftIcon.setImageResource(R.drawable.ic_bell)
        simLeftIcon.visibility = View.VISIBLE
        simEqualizer.visibility = View.GONE
        simRightText.visibility = View.VISIBLE
        simRightText.text = "Rahul: Kahan ho?"

        // Also trigger real overlay test
        IslandOverlayService.instance?.onNotificationReceived("WhatsApp", "Rahul", "Bhai call uthana urgent hai!")
    }
}
