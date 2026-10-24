package com.d4rxh.nothingisland

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
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
import com.d4rxh.nothingisland.service.IslandOverlayManager
import com.d4rxh.nothingisland.service.IslandOverlayService
import com.d4rxh.nothingisland.ui.DotMatrixEqualizerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: IslandPreferences

    // Header UI
    private lateinit var appTitleText: TextView
    private lateinit var headerStatusText: TextView
    private lateinit var switchMasterIsland: MaterialSwitch

    // Simulator
    private lateinit var sectionPreviewTitle: TextView
    private lateinit var islandStateBadge: TextView
    private lateinit var simulatedIsland: LinearLayout
    private lateinit var simLeftIcon: ImageView
    private lateinit var simLeftText: TextView
    private lateinit var simSpacer: View
    private lateinit var simEqualizer: DotMatrixEqualizerView
    private lateinit var simRightText: TextView
    private lateinit var btnTestMusic: Button
    private lateinit var btnTestCharging: Button
    private lateinit var btnTestNotification: Button
    private lateinit var btnTestCricket: Button
    private lateinit var btnForceShowIsland: Button

    // Permissions
    private lateinit var cardOverlayPermission: LinearLayout
    private lateinit var badgeOverlayPermission: TextView
    private lateinit var cardNotificationPermission: LinearLayout
    private lateinit var badgeNotificationPermission: TextView
    private lateinit var cardAccessibilityPermission: LinearLayout
    private lateinit var badgeAccessibilityPermission: TextView
    private lateinit var cardPhoneBluetoothPermission: LinearLayout
    private lateinit var badgePhoneBluetoothPermission: TextView

    // Presets & Calibration Sliders
    private lateinit var btnResetDefaults: Button
    private lateinit var btnPresetPhone2: Button
    private lateinit var btnPresetPhone1: Button

    private lateinit var sliderXOffset: Slider
    private lateinit var textXOffsetValue: TextView
    private lateinit var sliderYOffset: Slider
    private lateinit var textYOffsetValue: TextView
    private lateinit var sliderWidth: Slider
    private lateinit var textWidthValue: TextView
    private lateinit var sliderHeight: Slider
    private lateinit var textHeightValue: TextView
    private lateinit var sliderRadius: Slider
    private lateinit var textRadiusValue: TextView
    private lateinit var sliderCutout: Slider
    private lateinit var textCutoutValue: TextView

    // Feature switches
    private lateinit var switchAttachToTop: MaterialSwitch
    private lateinit var switchHideInLandscape: MaterialSwitch
    private lateinit var switchMusicIsland: MaterialSwitch
    private lateinit var switchChargingIsland: MaterialSwitch
    private lateinit var switchNotificationIsland: MaterialSwitch
    private lateinit var switchHaptics: MaterialSwitch
    private lateinit var switchMediaSeekbar: MaterialSwitch
    private lateinit var switchVolumeHud: MaterialSwitch
    private lateinit var switchBlacklist: MaterialSwitch
    private lateinit var switchBatteryAlerts: MaterialSwitch
    private lateinit var switchRespectDnd: MaterialSwitch
    private lateinit var switchTouchOutside: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = IslandPreferences(this)

        initViews()
        setupTypography()
        setupListeners()
        loadPreferences()
        updateSimulatedIslandAppearance()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBadges()
        if (Settings.canDrawOverlays(this)) {
            prefs.isEnabled = true
            switchMasterIsland.isChecked = true
            headerStatusText.text = "NOTHING OS 3.0 // ACTIVE"
            headerStatusText.setTextColor(Color.parseColor("#D71920"))
            IslandOverlayManager.showOverlay(this)
            startIslandServiceSafely()
        }
    }

    private fun initViews() {
        appTitleText = findViewById(R.id.appTitleText)
        headerStatusText = findViewById(R.id.headerStatusText)
        switchMasterIsland = findViewById(R.id.switchMasterIsland)

        sectionPreviewTitle = findViewById(R.id.sectionPreviewTitle)
        islandStateBadge = findViewById(R.id.islandStateBadge)
        simulatedIsland = findViewById(R.id.simulatedIsland)
        simLeftIcon = findViewById(R.id.simLeftIcon)
        simLeftText = findViewById(R.id.simLeftText)
        simSpacer = findViewById(R.id.simSpacer)
        simEqualizer = findViewById(R.id.simEqualizer)
        simRightText = findViewById(R.id.simRightText)
        btnTestMusic = findViewById(R.id.btnTestMusic)
        btnTestCharging = findViewById(R.id.btnTestCharging)
        btnTestNotification = findViewById(R.id.btnTestNotification)
        btnTestCricket = findViewById(R.id.btnTestCricket)
        btnForceShowIsland = findViewById(R.id.btnForceShowIsland)

        cardOverlayPermission = findViewById(R.id.cardOverlayPermission)
        badgeOverlayPermission = findViewById(R.id.badgeOverlayPermission)
        cardNotificationPermission = findViewById(R.id.cardNotificationPermission)
        badgeNotificationPermission = findViewById(R.id.badgeNotificationPermission)
        cardAccessibilityPermission = findViewById(R.id.cardAccessibilityPermission)
        badgeAccessibilityPermission = findViewById(R.id.badgeAccessibilityPermission)
        cardPhoneBluetoothPermission = findViewById(R.id.cardPhoneBluetoothPermission)
        badgePhoneBluetoothPermission = findViewById(R.id.badgePhoneBluetoothPermission)

        btnResetDefaults = findViewById(R.id.btnResetDefaults)
        btnPresetPhone2 = findViewById(R.id.btnPresetPhone2)
        btnPresetPhone1 = findViewById(R.id.btnPresetPhone1)

        sliderXOffset = findViewById(R.id.sliderXOffset)
        textXOffsetValue = findViewById(R.id.textXOffsetValue)
        sliderYOffset = findViewById(R.id.sliderYOffset)
        textYOffsetValue = findViewById(R.id.textYOffsetValue)
        sliderWidth = findViewById(R.id.sliderWidth)
        textWidthValue = findViewById(R.id.textWidthValue)
        sliderHeight = findViewById(R.id.sliderHeight)
        textHeightValue = findViewById(R.id.textHeightValue)
        sliderRadius = findViewById(R.id.sliderRadius)
        textRadiusValue = findViewById(R.id.textRadiusValue)
        sliderCutout = findViewById(R.id.sliderCutout)
        textCutoutValue = findViewById(R.id.textCutoutValue)

        switchAttachToTop = findViewById(R.id.switchAttachToTop)
        switchHideInLandscape = findViewById(R.id.switchHideInLandscape)
        switchMusicIsland = findViewById(R.id.switchMusicIsland)
        switchChargingIsland = findViewById(R.id.switchChargingIsland)
        switchNotificationIsland = findViewById(R.id.switchNotificationIsland)
        switchHaptics = findViewById(R.id.switchHaptics)
        switchMediaSeekbar = findViewById(R.id.switchMediaSeekbar)
        switchVolumeHud = findViewById(R.id.switchVolumeHud)
        switchBlacklist = findViewById(R.id.switchBlacklist)
        switchBatteryAlerts = findViewById(R.id.switchBatteryAlerts)
        switchRespectDnd = findViewById(R.id.switchRespectDnd)
        switchTouchOutside = findViewById(R.id.switchTouchOutside)
    }

    private fun setupTypography() {
        TypefaceHelper.applyNdotRecursively(window.decorView)
    }

    private fun setupListeners() {
        switchMasterIsland.setOnCheckedChangeListener { _, isChecked ->
            prefs.isEnabled = isChecked
            headerStatusText.text = if (isChecked) "NOTHING OS 3.0 // ACTIVE" else "SERVICE PAUSED"
            headerStatusText.setTextColor(
                if (isChecked) Color.parseColor("#D71920") else Color.parseColor("#777777")
            )
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    IslandOverlayManager.showOverlay(this)
                    startIslandServiceSafely()
                } else {
                    requestOverlayPermission()
                }
            } else {
                IslandOverlayManager.hideOverlay()
                stopIslandServiceSafely()
            }
        }

        // Permissions cards
        cardOverlayPermission.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            requestOverlayPermission()
        }

        cardNotificationPermission.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            requestNotificationPermission()
        }

        cardAccessibilityPermission.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            requestAccessibilityPermission()
        }

        cardPhoneBluetoothPermission.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            requestPhoneAndBluetoothPermissions()
        }

        // Test Buttons
        btnTestMusic.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            simulateMusicIsland()
        }

        btnTestCharging.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            simulateChargingIsland()
        }

        btnTestNotification.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            simulateNotificationIsland()
        }

        btnTestCricket.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            simulateCricketIsland()
        }

        btnForceShowIsland.setOnClickListener {
            NothingHapticHelper.vibratePop(this)
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Display Over Other Apps permission first", Toast.LENGTH_SHORT).show()
                requestOverlayPermission()
            } else {
                prefs.isEnabled = true
                switchMasterIsland.isChecked = true
                IslandOverlayManager.showOverlay(this)
                startIslandServiceSafely()
                Toast.makeText(
                    this,
                    "(NOT) ISLAND ACTIVE // Y: ${prefs.yOffset}dp (Top Center)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Presets
        btnPresetPhone2.setOnClickListener {
            NothingHapticHelper.vibratePop(this)
            prefs.notchAlignment = IslandPreferences.ALIGNMENT_CENTER
            applyPreset(x = 0, y = 14, width = 110, height = 36, radius = 20, cutout = 28)
            Toast.makeText(this, "Preset applied: Nothing Phone (2/2a) [Center]", Toast.LENGTH_SHORT).show()
        }

        btnPresetPhone1.setOnClickListener {
            NothingHapticHelper.vibratePop(this)
            prefs.notchAlignment = IslandPreferences.ALIGNMENT_LEFT
            applyPreset(x = -75, y = 14, width = 110, height = 36, radius = 20, cutout = 28)
            Toast.makeText(this, "Preset applied: Nothing Phone (1) [Left]", Toast.LENGTH_SHORT).show()
        }

        btnResetDefaults.setOnClickListener {
            NothingHapticHelper.vibrateTick(it)
            prefs.notchAlignment = IslandPreferences.ALIGNMENT_CENTER
            applyPreset(x = 0, y = 14, width = 110, height = 36, radius = 20, cutout = 28)
            Toast.makeText(this, "Reset to factory defaults", Toast.LENGTH_SHORT).show()
        }

        // Sliders Listeners
        sliderXOffset.addOnChangeListener { _, value, fromUser ->
            textXOffsetValue.text = "${value.toInt()} dp"
            if (fromUser) {
                prefs.xOffset = value.toInt()
                notifyGeometryUpdate()
            }
        }

        sliderYOffset.addOnChangeListener { _, value, fromUser ->
            textYOffsetValue.text = "${value.toInt()} dp"
            if (fromUser) {
                prefs.yOffset = value.toInt()
                notifyGeometryUpdate()
            }
        }

        sliderWidth.addOnChangeListener { _, value, fromUser ->
            textWidthValue.text = "${value.toInt()} dp"
            if (fromUser) {
                prefs.islandWidth = value.toInt()
                notifyGeometryUpdate()
            }
        }

        sliderHeight.addOnChangeListener { _, value, fromUser ->
            textHeightValue.text = "${value.toInt()} dp"
            if (fromUser) {
                prefs.islandHeight = value.toInt()
                notifyGeometryUpdate()
            }
        }

        sliderRadius.addOnChangeListener { _, value, fromUser ->
            textRadiusValue.text = "${value.toInt()} dp"
            if (fromUser) {
                prefs.cornerRadius = value.toInt()
                notifyGeometryUpdate()
            }
        }

        sliderCutout.addOnChangeListener { _, value, fromUser ->
            textCutoutValue.text = "${value.toInt()} dp"
            if (fromUser) {
                prefs.cutoutSize = value.toInt()
                notifyGeometryUpdate()
            }
        }

        // Switches
        switchAttachToTop.setOnCheckedChangeListener { _, isChecked ->
            prefs.attachToTop = isChecked
            notifyGeometryUpdate()
        }

        switchHideInLandscape.setOnCheckedChangeListener { _, isChecked ->
            prefs.hideInLandscape = isChecked
        }

        switchMusicIsland.setOnCheckedChangeListener { _, isChecked ->
            prefs.musicIslandEnabled = isChecked
            if (!isChecked) {
                IslandOverlayManager.clearMediaPlayback()
            }
        }

        switchChargingIsland.setOnCheckedChangeListener { _, isChecked ->
            prefs.chargingIslandEnabled = isChecked
        }

        switchNotificationIsland.setOnCheckedChangeListener { _, isChecked ->
            prefs.notificationIslandEnabled = isChecked
        }

        switchHaptics.setOnCheckedChangeListener { _, isChecked ->
            prefs.hapticsEnabled = isChecked
        }

        switchMediaSeekbar.setOnCheckedChangeListener { _, isChecked ->
            prefs.mediaSeekbarEnabled = isChecked
        }

        switchVolumeHud.setOnCheckedChangeListener { _, isChecked ->
            prefs.volumeHudEnabled = isChecked
        }

        switchBlacklist.setOnCheckedChangeListener { _, isChecked ->
            prefs.isBlacklistEnabled = isChecked
        }

        switchBatteryAlerts.setOnCheckedChangeListener { _, isChecked ->
            prefs.batteryAlertsEnabled = isChecked
        }

        switchRespectDnd.setOnCheckedChangeListener { _, isChecked ->
            prefs.respectDnd = isChecked
        }

        switchTouchOutside.setOnCheckedChangeListener { _, isChecked ->
            prefs.touchOutsideToCollapse = isChecked
        }
    }

    private fun applyPreset(x: Int, y: Int, width: Int, height: Int, radius: Int, cutout: Int) {
        prefs.xOffset = x
        prefs.yOffset = y
        prefs.islandWidth = width
        prefs.islandHeight = height
        prefs.cornerRadius = radius
        prefs.cutoutSize = cutout

        sliderXOffset.value = x.toFloat()
        textXOffsetValue.text = "$x dp"

        sliderYOffset.value = y.toFloat()
        textYOffsetValue.text = "$y dp"

        sliderWidth.value = width.toFloat()
        textWidthValue.text = "$width dp"

        sliderHeight.value = height.toFloat()
        textHeightValue.text = "$height dp"

        sliderRadius.value = radius.toFloat()
        textRadiusValue.text = "$radius dp"

        sliderCutout.value = cutout.toFloat()
        textCutoutValue.text = "$cutout dp"

        notifyGeometryUpdate()
    }

    private fun notifyGeometryUpdate() {
        updateSimulatedIslandAppearance()
        IslandOverlayManager.updateAppearance(
            xOffset = prefs.xOffset,
            yOffset = prefs.yOffset,
            width = prefs.islandWidth,
            height = prefs.islandHeight,
            cornerRadius = prefs.cornerRadius,
            cutoutSize = prefs.cutoutSize
        )
    }

    private fun updateSimulatedIslandAppearance() {
        val density = resources.displayMetrics.density

        // Update corner radius (attached notch vs floating pill)
        val radiusPx = prefs.cornerRadius * density
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            setStroke((1.2f * density).toInt(), Color.parseColor("#333333"))
            if (prefs.attachToTop) {
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    radiusPx, radiusPx,
                    radiusPx, radiusPx
                )
            } else {
                cornerRadius = radiusPx
            }
        }
        simulatedIsland.background = bgDrawable

        // Update dimensions & margins for Attached to Top Bezel
        val lp = simulatedIsland.layoutParams as? android.widget.FrameLayout.LayoutParams
        if (lp != null) {
            val extraHeight = if (prefs.attachToTop) (7 * density).toInt() else 0
            lp.height = (prefs.islandHeight * density).toInt() + extraHeight
            lp.topMargin = if (prefs.attachToTop) -(3 * density).toInt() else (14 * density).toInt()
            simulatedIsland.layoutParams = lp
        }
        simulatedIsland.minimumWidth = (prefs.islandWidth * density).toInt()

        // Spacer size
        simSpacer.layoutParams = simSpacer.layoutParams.apply {
            width = (prefs.cutoutSize * density).toInt()
            height = (prefs.cutoutSize * density).toInt()
        }

        if (islandStateBadge.text == "LIVE PREVIEW") {
            simLeftIcon.setImageResource(R.drawable.ic_dot)
            simLeftIcon.visibility = View.VISIBLE
            simRightText.text = "NOTHING"
            simRightText.visibility = View.VISIBLE
        }
    }

    private fun loadPreferences() {
        switchMasterIsland.isChecked = prefs.isEnabled

        sliderXOffset.value = prefs.xOffset.toFloat().coerceIn(-120f, 120f)
        textXOffsetValue.text = "${prefs.xOffset} dp"

        sliderYOffset.value = prefs.yOffset.toFloat().coerceIn(0f, 60f)
        textYOffsetValue.text = "${prefs.yOffset} dp"

        sliderWidth.value = prefs.islandWidth.toFloat().coerceIn(70f, 220f)
        textWidthValue.text = "${prefs.islandWidth} dp"

        sliderHeight.value = prefs.islandHeight.toFloat().coerceIn(28f, 50f)
        textHeightValue.text = "${prefs.islandHeight} dp"

        sliderRadius.value = prefs.cornerRadius.toFloat().coerceIn(8f, 26f)
        textRadiusValue.text = "${prefs.cornerRadius} dp"

        sliderCutout.value = prefs.cutoutSize.toFloat().coerceIn(16f, 44f)
        textCutoutValue.text = "${prefs.cutoutSize} dp"

        switchAttachToTop.isChecked = prefs.attachToTop
        switchHideInLandscape.isChecked = prefs.hideInLandscape
        switchMusicIsland.isChecked = prefs.musicIslandEnabled
        switchChargingIsland.isChecked = prefs.chargingIslandEnabled
        switchNotificationIsland.isChecked = prefs.notificationIslandEnabled
        switchHaptics.isChecked = prefs.hapticsEnabled
        switchMediaSeekbar.isChecked = prefs.mediaSeekbarEnabled
        switchVolumeHud.isChecked = prefs.volumeHudEnabled
        switchBlacklist.isChecked = prefs.isBlacklistEnabled
        switchBatteryAlerts.isChecked = prefs.batteryAlertsEnabled
        switchRespectDnd.isChecked = prefs.respectDnd
        switchTouchOutside.isChecked = prefs.touchOutsideToCollapse
    }

    private fun updatePermissionBadges() {
        try {
            val hasOverlay = Settings.canDrawOverlays(this)
            badgeOverlayPermission.text = if (hasOverlay) "GRANTED" else "GRANT"
            badgeOverlayPermission.setTextColor(
                if (hasOverlay) Color.parseColor("#4CAF50") else Color.parseColor("#D71920")
            )

            val hasNotification =
                NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
            badgeNotificationPermission.text = if (hasNotification) "GRANTED" else "GRANT"
            badgeNotificationPermission.setTextColor(
                if (hasNotification) Color.parseColor("#4CAF50") else Color.parseColor("#D71920")
            )

            val hasA11y = isAccessibilityServiceEnabled()
            badgeAccessibilityPermission.text = if (hasA11y) "ACTIVE" else "ENABLE"
            badgeAccessibilityPermission.setTextColor(
                if (hasA11y) Color.parseColor("#4CAF50") else Color.parseColor("#D71920")
            )

            val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            val hasPhone = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasPhoneBt = hasBluetooth && hasPhone
            badgePhoneBluetoothPermission.text = if (hasPhoneBt) "ACTIVE" else "GRANT"
            badgePhoneBluetoothPermission.setTextColor(
                if (hasPhoneBt) Color.parseColor("#4CAF50") else Color.parseColor("#D71920")
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking permissions: ${e.message}")
        }
    }

    private fun requestPhoneAndBluetoothPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        perms.add(android.Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            perms.add(android.Manifest.permission.ANSWER_PHONE_CALLS)
        }
        perms.add(android.Manifest.permission.READ_CONTACTS)

        val ungranted = perms.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            requestPermissions(ungranted.toTypedArray(), 102)
        } else {
            Toast.makeText(this, "Call & Bluetooth permissions already active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            enabledServices?.any { it.resolveInfo.serviceInfo.packageName == packageName } == true
        } catch (e: Exception) {
            false
        }
    }

    private fun requestAccessibilityPermission() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find '(NOT) ISLAND' in Downloaded Apps and turn it ON for lock screen display", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Open Settings -> Accessibility -> (NOT) Island", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestOverlayPermission() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching overlay settings: ${e.message}")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e2: Exception) {
                Toast.makeText(this, "Open System Settings -> Apps -> (NOT) Island -> Display over other apps", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching notification settings: ${e.message}")
            Toast.makeText(this, "Open Settings -> Apps -> Special Access -> Device & app notifications", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 || requestCode == 102) {
            updatePermissionBadges()
            if (Settings.canDrawOverlays(this)) {
                startIslandServiceSafely()
            }
        }
    }

    private fun startIslandServiceSafely() {
        try {
            val intent = Intent(this, IslandOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "startService caught: ${e.message}", e)
        }
    }

    private fun stopIslandServiceSafely() {
        try {
            stopService(Intent(this, IslandOverlayService::class.java))
        } catch (e: Exception) {
            Log.e("MainActivity", "stopService caught: ${e.message}", e)
        }
    }

    // Interactive simulator demonstrations
    private fun simulateMusicIsland() {
        islandStateBadge.text = "MUSIC // LIVE"
        simLeftIcon.setImageResource(R.drawable.ic_music)
        simLeftIcon.visibility = View.VISIBLE
        simLeftText.visibility = View.GONE
        simRightText.visibility = View.GONE
        simEqualizer.visibility = View.VISIBLE
        simEqualizer.isPlaying = true

        // Real screen overlay test with Spotify branding
        IslandOverlayManager.onMediaStateChanged(
            packageName = "com.spotify.music",
            appName = "Spotify",
            title = "Starboy",
            artist = "The Weeknd",
            isPlaying = true,
            artwork = null,
            appIcon = getDrawable(R.drawable.ic_music)
        )
    }

    private fun simulateChargingIsland() {
        islandStateBadge.text = "FAST CHARGE"
        simLeftIcon.setImageResource(R.drawable.ic_bolt)
        simLeftIcon.visibility = View.VISIBLE
        simEqualizer.visibility = View.GONE
        simLeftText.visibility = View.VISIBLE
        simLeftText.text = "CHARGE"
        simRightText.visibility = View.VISIBLE
        simRightText.text = "92%"

        // Real screen overlay test
        IslandOverlayManager.onChargingChanged(true, 92)
    }

    private fun simulateNotificationIsland() {
        islandStateBadge.text = "WHATSAPP"
        simLeftIcon.setImageResource(R.drawable.ic_bell)
        simLeftIcon.visibility = View.VISIBLE
        simEqualizer.visibility = View.GONE
        simLeftText.visibility = View.GONE
        simRightText.visibility = View.VISIBLE
        simRightText.text = "Rahul: Kahan ho?"

        // Real screen overlay test with WhatsApp branding
        IslandOverlayManager.onNotificationReceived(
            appName = "WhatsApp",
            title = "Rahul",
            content = "Kahan ho bhai? Match start ho gaya!",
            appIcon = getDrawable(R.drawable.ic_bell)
        )
    }

    private fun simulateCricketIsland() {
        islandStateBadge.text = "CRICKET // LIVE"
        simLeftIcon.setImageResource(R.drawable.ic_cricket)
        simLeftIcon.visibility = View.VISIBLE
        simEqualizer.visibility = View.GONE
        simLeftText.visibility = View.GONE
        simRightText.text = "IND 184/3 (18.2)"
        simRightText.visibility = View.VISIBLE

        // Real screen overlay test with Cricbuzz live cricket score
        IslandOverlayManager.onCricketScoreReceived(
            appName = "Cricbuzz",
            matchTitle = "IND vs AUS",
            scoreText = "IND 184/3 (18.2 ov)",
            appIcon = getDrawable(R.drawable.ic_cricket)
        )
    }
}
