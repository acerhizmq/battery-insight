package com.acer.batteryinsight.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == "com.acer.batteryinsight.WATCHDOG_KEEPALIVE"
        ) {
            val prefs = try {
                context.getSharedPreferences("battery_insight_prefs", Context.MODE_PRIVATE)
            } catch (_: Exception) {
                null
            }
            val isEnabled = prefs?.getBoolean("battery_insight_enabled", true) ?: true
            if (isEnabled) {
                // If service instance is already alive and running, avoid redundant reboots on watchdog ticks
                if (action == "com.acer.batteryinsight.WATCHDOG_KEEPALIVE" && BatteryInsightService.instance != null) {
                    return
                }

                com.acer.batteryinsight.utils.ShellUtils.initRootShell()
                try {
                    val serviceIntent = Intent(context, BatteryInsightService::class.java).apply {
                        if (action != Intent.ACTION_MY_PACKAGE_REPLACED && action != "com.acer.batteryinsight.WATCHDOG_KEEPALIVE") {
                            putExtra("is_reboot", true)
                        }
                    }
                    context.startForegroundService(serviceIntent)
                } catch (_: Exception) {}
            }
        }
    }
}
