package com.acer.batteryinsight

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.acer.batteryinsight.service.BatteryInsightService

class BatteryInsightApp : Application() {

    companion object {
        var instance: BatteryInsightApp? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Catch uncaught exceptions gracefully and delegate to system handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("BatteryInsightApp", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Set up LibSU root shell builder (must precede any shell creation)
        com.acer.batteryinsight.utils.ShellUtils.configure()

        // The monitor process only hosts the service and its receivers; they start what they need.
        if (getProcessName().endsWith(":monitor")) return

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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Keep the UI process small once it is no longer visible.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.acer.batteryinsight.ui.components.clearAppIconCache()
        }
    }
}
