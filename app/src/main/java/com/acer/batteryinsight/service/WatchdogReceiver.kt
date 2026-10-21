package com.acer.batteryinsight.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the keep-alive alarm scheduled by [BatteryInsightService]. Not exported, so other apps
 * cannot use it to start the service.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BatteryInsightService.WATCHDOG_ACTION) return
        BootReceiver.startMonitorIfEnabled(context, isReboot = false, skipIfRunning = true)
    }
}
