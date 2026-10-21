package com.acer.batteryinsight.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.acer.batteryinsight.utils.ShellUtils

class BootReceiver : BroadcastReceiver() {

    companion object {
        fun startMonitorIfEnabled(context: Context, isReboot: Boolean, skipIfRunning: Boolean) {
            val prefs = try {
                context.getSharedPreferences("battery_insight_prefs", Context.MODE_PRIVATE)
            } catch (_: Exception) {
                null
            }
            val isEnabled = prefs?.getBoolean("battery_insight_enabled", true) ?: true
            if (!isEnabled) return
            // If service instance is already alive and running, avoid redundant restarts on watchdog ticks
            if (skipIfRunning && BatteryInsightService.instance != null) return

            ShellUtils.initRootShell()
            try {
                val serviceIntent = Intent(context, BatteryInsightService::class.java).apply {
                    if (isReboot) putExtra("is_reboot", true)
                }
                context.startForegroundService(serviceIntent)
            } catch (_: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action ?: return) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" ->
                startMonitorIfEnabled(context, isReboot = true, skipIfRunning = false)

            Intent.ACTION_MY_PACKAGE_REPLACED ->
                startMonitorIfEnabled(context, isReboot = false, skipIfRunning = false)

            // Alarms scheduled by older versions still target this receiver explicitly.
            BatteryInsightService.WATCHDOG_ACTION ->
                startMonitorIfEnabled(context, isReboot = false, skipIfRunning = true)
        }
    }
}
