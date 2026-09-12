/*
 * SPDX-FileCopyrightText: 2026 kenway214 & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.acer.batteryinsight.ui.BatteryInsightRoot
import com.acer.batteryinsight.service.BatteryInsightService
import com.acer.batteryinsight.ui.theme.BatteryInsightTheme
import com.acer.batteryinsight.utils.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val prefs = getSharedPreferences("battery_insight_prefs", Context.MODE_PRIVATE)
        val isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        // Start local foreground service safely if setup is already complete
        if (isOnboardingCompleted && prefs.getBoolean("battery_insight_enabled", true)) {
            val intent = Intent(this, BatteryInsightService::class.java)
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (_: Exception) {}
        }

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val isAmoled = prefs.getBoolean("battery_insight_amoled_mode", false)

        setContent {
            BatteryInsightTheme(
                amoledMode = isAmoled,
                dynamicColor = true
            ) {
                BatteryInsightRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("battery_insight_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_completed", false)) {
            ShellUtils.initRootShell()
        }
    }
}
