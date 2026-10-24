package com.d4rxh.nothingisland.service

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.d4rxh.nothingisland.R

object DeviceEventsReceiver {

    private const val TAG = "DeviceEventsReceiver"
    private var isRegistered = false
    private var receiver: BroadcastReceiver? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastEventTime = 0L
    private var lastEventTitle = ""

    var onDeviceEvent: ((iconRes: Int, title: String, subtitle: String) -> Unit)? = null

    fun register(context: Context) {
        if (isRegistered) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val action = intent.action ?: return

                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val device = getDeviceFromIntent(intent)
                        val name = getDeviceName(device)
                        val battery = getDeviceBattery(device, intent)
                        val sub = if (battery in 0..100) "Connected • $battery%" else "Connected"
                        triggerEvent(R.drawable.ic_bluetooth, name.uppercase(), sub)
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = getDeviceFromIntent(intent)
                        val name = getDeviceName(device)
                        triggerEvent(R.drawable.ic_bluetooth, name.uppercase(), "Disconnected")
                    }

                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                    "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        val device = getDeviceFromIntent(intent)
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            val name = getDeviceName(device).ifEmpty { "Bluetooth Audio" }
                            val battery = getDeviceBattery(device, intent)
                            val sub = if (battery in 0..100) "Connected • $battery%" else "Connected"
                            triggerEvent(R.drawable.ic_bluetooth, name.uppercase(), sub)
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED && lastEventTitle.isNotEmpty()) {
                            val name = getDeviceName(device).ifEmpty { "Bluetooth Audio" }
                            triggerEvent(R.drawable.ic_bluetooth, name.uppercase(), "Disconnected")
                        }
                    }

                    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                        val device = getDeviceFromIntent(intent)
                        val battery = getDeviceBattery(device, intent)
                        if (battery in 0..100) {
                            val name = getDeviceName(device)
                            triggerEvent(R.drawable.ic_bluetooth, name.uppercase(), "Battery $battery%")
                        }
                    }

                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        val name = intent.getStringExtra("name") ?: "Headphones"
                        if (state == 1) {
                            triggerEvent(R.drawable.ic_music, name.uppercase(), "Headphones Connected")
                        } else if (state == 0 && lastEventTitle.isNotEmpty()) {
                            triggerEvent(R.drawable.ic_music, name.uppercase(), "Headphones Disconnected")
                        }
                    }

                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        val am = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        when (am?.ringerMode) {
                            AudioManager.RINGER_MODE_SILENT -> triggerEvent(R.drawable.ic_bell, "SOUND MODE", "Silent Mode Active")
                            AudioManager.RINGER_MODE_VIBRATE -> triggerEvent(R.drawable.ic_bell, "SOUND MODE", "Vibrate Only")
                            AudioManager.RINGER_MODE_NORMAL -> triggerEvent(R.drawable.ic_bell, "SOUND MODE", "Ring Mode Active")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }

        try {
            context.registerReceiver(receiver, filter)
            isRegistered = true
            Log.i(TAG, "DeviceEventsReceiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register DeviceEventsReceiver: ${e.message}")
        }

        // Clipboard change listener
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
                val clip = cm?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0)?.text?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        triggerEvent(R.drawable.ic_dot, "CLIPBOARD • GLYPH", "Copied: ${text.take(24)}...")
                    }
                }
            }
            cm?.addPrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register clipboard listener: ${e.message}")
        }
    }

    private fun getDeviceFromIntent(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun getDeviceName(device: BluetoothDevice?): String {
        if (device == null) return "Bluetooth Device"
        return try {
            val devName = device.name ?: device.alias
            if (!devName.isNullOrEmpty()) devName else "Bluetooth Device"
        } catch (e: SecurityException) {
            "Bluetooth Device"
        }
    }

    private fun getDeviceBattery(device: BluetoothDevice?, intent: Intent?): Int {
        if (device == null) return -1
        // 1. Direct intent extra
        val intentBattery = intent?.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1) ?: -1
        if (intentBattery in 0..100) return intentBattery

        // 2. Reflection on hidden getBatteryLevel() API (dynamicSpot mechanism)
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            if (level in 0..100) level else -1
        } catch (e: Exception) {
            -1
        }
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
        receiver = null
        clipboardListener = null
        isRegistered = false
    }

    private fun triggerEvent(iconRes: Int, title: String, subtitle: String) {
        val now = SystemClock.elapsedRealtime()
        // Deduplicate events fired within 1.5 seconds
        if (title == lastEventTitle && now - lastEventTime < 1500) return

        lastEventTitle = title
        lastEventTime = now

        mainHandler.post {
            onDeviceEvent?.invoke(iconRes, title, subtitle)
        }
    }
}
