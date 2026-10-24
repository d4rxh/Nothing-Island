package com.d4rxh.nothingisland.service

import android.bluetooth.BluetoothDevice
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
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val name = try { device?.name ?: "Bluetooth Device" } catch (e: SecurityException) { "Bluetooth Device" }
                        val battery = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                        val sub = if (battery in 0..100) "Connected • $battery%" else "Connected"
                        triggerEvent(name.uppercase(), sub)
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val name = try { device?.name ?: "Bluetooth Device" } catch (e: SecurityException) { "Bluetooth Device" }
                        triggerEvent(name.uppercase(), "Disconnected")
                    }

                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        val name = intent.getStringExtra("name") ?: "Headphones"
                        if (state == 1) {
                            triggerEvent(name.uppercase(), "Headphones Connected")
                        } else if (state == 0 && lastEventTitle.isNotEmpty()) {
                            triggerEvent(name.uppercase(), "Headphones Disconnected")
                        }
                    }

                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        val am = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        when (am?.ringerMode) {
                            AudioManager.RINGER_MODE_SILENT -> triggerEvent("SOUND MODE", "Silent Mode Active")
                            AudioManager.RINGER_MODE_VIBRATE -> triggerEvent("SOUND MODE", "Vibrate Only")
                            AudioManager.RINGER_MODE_NORMAL -> triggerEvent("SOUND MODE", "Ring Mode Active")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }

        try {
            context.registerReceiver(receiver, filter)
            isRegistered = true
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
                        triggerEvent("CLIPBOARD // GLYPH", "Copied: ${text.take(24)}...")
                    }
                }
            }
            cm?.addPrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register clipboard listener: ${e.message}")
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

    private fun triggerEvent(title: String, subtitle: String) {
        val now = SystemClock.elapsedRealtime()
        // Deduplicate events fired within 1.5 seconds
        if (title == lastEventTitle && now - lastEventTime < 1500) return

        lastEventTitle = title
        lastEventTime = now

        mainHandler.post {
            onDeviceEvent?.invoke(0, title, subtitle)
        }
    }
}
