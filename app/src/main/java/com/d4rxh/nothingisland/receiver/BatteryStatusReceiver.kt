package com.d4rxh.nothingisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.d4rxh.nothingisland.service.IslandOverlayManager
import com.d4rxh.nothingisland.service.IslandOverlayService

class BatteryStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                val level = getBatteryPercentage(context)
                lastCharging = true
                IslandOverlayManager.onChargingChanged(true, level)
                IslandOverlayService.instance?.onChargingChanged(true, level)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val level = getBatteryPercentage(context)
                lastCharging = false
                IslandOverlayManager.onChargingChanged(false, level)
                IslandOverlayService.instance?.onChargingChanged(false, level)
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (level >= 0 && scale > 0) (level * 100) / scale else getBatteryPercentage(context)

                if (isCharging && lastCharging != true) {
                    lastCharging = true
                    IslandOverlayManager.onChargingChanged(true, percent)
                    IslandOverlayService.instance?.onChargingChanged(true, percent)
                } else if (!isCharging && lastCharging == true) {
                    lastCharging = false
                    IslandOverlayManager.onChargingChanged(false, percent)
                    IslandOverlayService.instance?.onChargingChanged(false, percent)
                }
            }
        }
    }

    private fun getBatteryPercentage(context: Context?): Int {
        if (context == null) return 85
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
    }

    companion object {
        private var lastCharging: Boolean? = null
    }
}
