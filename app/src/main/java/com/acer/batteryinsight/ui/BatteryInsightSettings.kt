/*
 * SPDX-FileCopyrightText: 2026 kenway214 & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acer.batteryinsight.ui.components.AppUsageRow
import com.acer.batteryinsight.ui.components.AppUsageSummaryCard
import com.acer.batteryinsight.ui.components.BatteryHealthCard
import com.acer.batteryinsight.ui.components.BatteryHealthDialog
import com.acer.batteryinsight.ui.components.BatteryHero
import com.acer.batteryinsight.ui.components.AppBottomNavBar
import com.acer.batteryinsight.ui.components.HistoryTimelineItem
import com.acer.batteryinsight.ui.components.MasterToggleCard
import com.acer.batteryinsight.ui.components.RealtimeBubblesSection
import com.acer.batteryinsight.ui.components.RealtimeChartSection
import com.acer.batteryinsight.ui.components.ServiceUnavailableBanner
import androidx.compose.material3.FilterChip
import com.acer.batteryinsight.ui.components.ExpandedSessionChartSheet
import com.acer.batteryinsight.ui.components.PermissionModeBottomSheet
import com.acer.batteryinsight.ui.components.SessionDetailsSheet
import com.acer.batteryinsight.ui.components.SettingsPanel
import com.acer.batteryinsight.ui.components.UpdateDialog
import com.acer.batteryinsight.ui.components.WelcomeSetupScreen
import com.acer.batteryinsight.R
import android.widget.Toast

@Composable
fun BatteryInsightRoot(viewModel: BatteryInsightViewModel = viewModel()) {
    val stats by viewModel.stats.collectAsState()
    val flow by viewModel.flow.collectAsState()
    val history by viewModel.history.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val lastChargeFlow by viewModel.lastChargeFlow.collectAsState()
    val lastChargeHistory by viewModel.lastChargeHistory.collectAsState()
    val lastDischargeFlow by viewModel.lastDischargeFlow.collectAsState()
    val lastDischargeHistory by viewModel.lastDischargeHistory.collectAsState()
    val isEnabled by viewModel.isEnabled.collectAsState()
    val isNotifEnabled by viewModel.isNotifEnabled.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isServiceConnected by viewModel.isServiceConnected.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateDownloadState by viewModel.updateDownloadState.collectAsState()
    val updateSnackMessage by viewModel.updateSnackMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showHealthDetails by remember { mutableStateOf(false) }
    var showSessionDetails by remember { mutableStateOf(false) }
    var showExpandedChart by remember { mutableStateOf(false) }
    var showPermissionSheet by remember { mutableStateOf(false) }
    var appFilterMode by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val pm = context.packageManager

    val prefs = remember { context.getSharedPreferences("battery_insight_prefs", android.content.Context.MODE_PRIVATE) }
    var showWelcomeScreen by remember { mutableStateOf(!prefs.getBoolean("onboarding_completed", false)) }

    LaunchedEffect(updateSnackMessage) {
        updateSnackMessage?.let { msg ->
            when (msg) {
                "UP_TO_DATE" -> Toast.makeText(
                    context,
                    context.getString(R.string.battery_insight_update_latest, com.acer.batteryinsight.BuildConfig.VERSION_NAME),
                    Toast.LENGTH_SHORT
                ).show()
                "ERROR" -> Toast.makeText(
                    context,
                    context.getString(R.string.battery_insight_update_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
            viewModel.clearUpdateSnackMessage()
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setBatteryAlarmSound(uri?.toString())
        }
    }

    fun launchRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                context.getString(R.string.battery_insight_select_alarm_sound),
            )
            val currentUri = viewModel.batteryAlarmSound.value
            if (!currentUri.isNullOrEmpty()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
            }
        }
        ringtonePickerLauncher.launch(intent)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            if (showWelcomeScreen) {
                WelcomeSetupScreen(
                    onComplete = {
                        prefs.edit().putBoolean("onboarding_completed", true).apply()
                        showWelcomeScreen = false
                        viewModel.startPolling()
                    },
                    modifier = Modifier.padding(top = topInset, bottom = bottomInset),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
            if (!isLoading && !isServiceConnected) {
                ServiceUnavailableBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = topInset + 8.dp, bottom = 12.dp),
                )
            }

        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(durationMillis = 200),
            label = "tab_crossfade",
            modifier = Modifier.fillMaxSize(),
        ) { targetTab ->
            when (targetTab) {
                0 -> {
                    // TAB 0: ANLIK (Realtime)
                    val realtimeScrollState = rememberLazyListState()
                    LazyColumn(
                        state = realtimeScrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = topInset + 12.dp,
                            bottom = bottomInset + 100.dp,
                        ),
                    ) {
                        item(key = "hero") {
                            BatteryHero(
                                stats = stats,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 14.dp),
                            )
                        }
                        item(key = "rt_chart") {
                            RealtimeChartSection(
                                stats = stats,
                                flow = flow,
                                onExpandChart = { showExpandedChart = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 14.dp),
                            )
                        }
                        item(key = "rt_bubbles") {
                            RealtimeBubblesSection(
                                stats = stats,
                                onOpenSessionDetails = { showSessionDetails = true },
                                onOpenHealthDialog = { showHealthDetails = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                1 -> {
                    // TAB 1: GEÇMİŞ (History)
                    val historyScrollState = rememberLazyListState()
                    LazyColumn(
                        state = historyScrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = topInset + 12.dp,
                            bottom = bottomInset + 100.dp,
                        ),
                    ) {
                        item(key = "history_header") {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text(
                                    stringResource(R.string.battery_insight_history),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(R.string.battery_insight_history_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (history.isEmpty()) {
                            item(key = "history_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.battery_insight_no_history),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = history,
                                key = { index, bucket -> "hist_${bucket.epochDay}_${bucket.hour}_$index" },
                            ) { index, bucket ->
                                HistoryTimelineItem(
                                    bucket = bucket,
                                    isLast = index == history.lastIndex,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = if (index == 0) 12.dp else 0.dp),
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: UYGULAMALAR (Apps)
                    val appsScrollState = rememberLazyListState()
                    val filteredApps = remember(apps, appFilterMode) {
                        when (appFilterMode) {
                            0 -> apps.filter { it.uid >= 10000 || it.packageName == "screen" }
                            1 -> apps.filter { it.uid < 10000 && it.packageName != "screen" }
                            else -> apps
                        }
                    }
                    val totalDischargedMah = remember(stats, apps) {
                        val drainPercent = (stats.batteryDrainScreenOn + stats.batteryDrainScreenOff)
                        val batteryCap = if (stats.totalCapacity > 0) stats.totalCapacity else 5000
                        if (drainPercent > 0) {
                            val drainFromPercent = (drainPercent * batteryCap / 100.0)
                            drainFromPercent.coerceIn(1.0, batteryCap.toDouble())
                        } else {
                            val allAppsSum = apps.sumOf { it.consumedPowerMah }
                            allAppsSum.coerceIn(1.0, batteryCap.toDouble())
                        }
                    }

                    LazyColumn(
                        state = appsScrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = topInset + 12.dp,
                            bottom = bottomInset + 100.dp,
                        ),
                    ) {
                        item(key = "apps_header") {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text(
                                    stringResource(R.string.battery_insight_apps),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 24.sp,
                                )
                                Text(
                                    stringResource(R.string.battery_insight_apps_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item(key = "apps_filter_chips") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = appFilterMode == 0,
                                    onClick = { appFilterMode = 0 },
                                    label = { Text(stringResource(R.string.battery_insight_filter_user)) },
                                )
                                FilterChip(
                                    selected = appFilterMode == 1,
                                    onClick = { appFilterMode = 1 },
                                    label = { Text(stringResource(R.string.battery_insight_filter_system)) },
                                )
                                FilterChip(
                                    selected = appFilterMode == 2,
                                    onClick = { appFilterMode = 2 },
                                    label = { Text(stringResource(R.string.battery_insight_filter_all)) },
                                )
                            }
                        }
                        if (filteredApps.isEmpty()) {
                            item(key = "apps_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(200.dp)
                                        .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.battery_insight_no_app_data),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            item(key = "apps_summary") {
                                AppUsageSummaryCard(
                                    totalMah = totalDischargedMah,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 12.dp),
                                )
                            }
                            itemsIndexed(
                                items = filteredApps,
                                key = { index, app -> "${app.uid}_${app.packageName}_$index" },
                            ) { _, app ->
                                val pct = (app.consumedPowerMah * 100.0 / totalDischargedMah).coerceIn(0.0, 100.0)
                                AppUsageRow(
                                    app = app,
                                    pct = pct,
                                    pm = pm,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // TAB 3: AYARLAR (Settings)
                    val settingsScrollState = rememberLazyListState()
                    LazyColumn(
                        state = settingsScrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = topInset + 12.dp,
                            bottom = bottomInset + 100.dp,
                        ),
                    ) {
                        item(key = "settings_header") {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text(
                                    stringResource(R.string.battery_insight_settings_tab),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(R.string.battery_insight_settings_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item(key = "settings_master_toggle") {
                            MasterToggleCard(
                                enabled = isEnabled,
                                onToggle = { viewModel.setEnabled(it) },
                                compact = false,
                                switchEnabled = isServiceConnected,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp),
                            )
                        }
                        item(key = "settings_panel") {
                            SettingsPanel(
                                viewModel = viewModel,
                                isEnabled = isEnabled,
                                isNotifEnabled = isNotifEnabled,
                                onPickSound = { launchRingtonePicker() },
                                onOpenPermissionSetup = { showPermissionSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        // App Navigation Bar (KernelSU-style Liquid Glass)
        AppBottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            isFloating = true,
            isBlurEnabled = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 12.dp),
        )

            if (showHealthDetails) {
                BatteryHealthDialog(
                    stats = stats,
                    onDismiss = { showHealthDetails = false },
                )
            }

            if (showSessionDetails) {
                SessionDetailsSheet(
                    stats = stats,
                    onDismiss = { showSessionDetails = false },
                )
            }

            if (showExpandedChart) {
                ExpandedSessionChartSheet(
                    stats = stats,
                    activeFlow = flow,
                    activeHistory = history,
                    lastChargeFlow = lastChargeFlow,
                    lastChargeHistory = lastChargeHistory,
                    lastDischargeFlow = lastDischargeFlow,
                    lastDischargeHistory = lastDischargeHistory,
                    onDismiss = { showExpandedChart = false },
                )
            }

            if (showPermissionSheet) {
                PermissionModeBottomSheet(
                    onDismiss = { showPermissionSheet = false },
                )
            }

            updateInfo?.let { info ->
                UpdateDialog(
                    updateInfo = info,
                    downloadState = updateDownloadState,
                    onDismiss = { viewModel.dismissUpdateDialog() },
                    onStartDownload = { viewModel.startUpdateDownload(context) },
                    onInstall = { file -> viewModel.installDownloadedApk(context, file) }
                )
            }
        }
    }
}
}
}
