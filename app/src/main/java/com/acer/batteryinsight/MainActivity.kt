/*
 * SPDX-FileCopyrightText: 2026 kenway214 & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.acer.batteryinsight.ui.BatteryInsightRoot
import com.acer.batteryinsight.ui.BatteryInsightViewModel
import com.acer.batteryinsight.service.BatteryInsightService
import com.acer.batteryinsight.ui.theme.BatteryInsightTheme
import com.acer.batteryinsight.updater.UpdateManager
import com.acer.batteryinsight.utils.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: BatteryInsightViewModel by viewModels()

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

        handleUpdateIntent(intent)

        val isAmoled = prefs.getBoolean("battery_insight_amoled_mode", false)

        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLifecycleOwner provides this
            ) {
                BatteryInsightTheme(
                    amoledMode = isAmoled,
                    dynamicColor = true
                ) {
                    BatteryInsightRoot(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    private fun handleUpdateIntent(intent: Intent?) {
        try {
            if (intent?.getBooleanExtra(UpdateManager.EXTRA_CHECK_UPDATE, false) == true) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(UpdateManager.NOTIFICATION_ID_UPDATE)
                viewModel.onNotificationClickedForUpdate(this)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error handling update intent", e)
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
