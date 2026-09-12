package com.acer.batteryinsight

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.acer.batteryinsight.service.BatteryInsightService
import com.topjohnwu.superuser.Shell

class BatteryInsightApp : Application() {

    companion object {
        var instance: BatteryInsightApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Catch uncaught exceptions gracefully
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("BatteryInsightApp", "Uncaught exception in thread ${thread.name}", throwable)
        }

        // Set up LibSU root shell builder
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )

        val prefs = getSharedPreferences("battery_insight_prefs", Context.MODE_PRIVATE)
        val isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        if (isOnboardingCompleted) {
            // Warm up root shell immediately in background only after user completed onboarding
            com.acer.batteryinsight.utils.ShellUtils.initRootShell()

            // Start Background Monitor Service if enabled
            if (prefs.getBoolean("battery_insight_enabled", true)) {
                val intent = Intent(this, BatteryInsightService::class.java)
                try {
                    ContextCompat.startForegroundService(this, intent)
                } catch (_: Exception) {}
            }
        }
    }
}
