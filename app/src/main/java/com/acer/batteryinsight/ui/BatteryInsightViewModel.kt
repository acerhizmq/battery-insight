/*
 * SPDX-FileCopyrightText: 2026 kenway214 & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acer.batteryinsight.model.BatteryInsightAppUsage
import com.acer.batteryinsight.model.BatteryInsightFlowSample
import com.acer.batteryinsight.model.BatteryInsightHistoryBucket
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.service.IBatteryInsightService
import com.acer.batteryinsight.ui.components.UpdateDownloadState
import com.acer.batteryinsight.updater.AppUpdateInfo
import com.acer.batteryinsight.updater.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatteryInsightViewModel : ViewModel() {
    companion object {
        private const val TAG = "BatteryInsightVM"
    }

    private var service: IBatteryInsightService? = null

    private var lastValidLevel: Int = 100

    private fun createInitialStats(): BatteryInsightStats {
        val stats = BatteryInsightStats()
        try {
            val ctx = com.acer.batteryinsight.BatteryInsightApp.instance
            if (ctx != null) {
                val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val sticky = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val rawLevel = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val rawScale = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (rawLevel >= 0 && rawScale > 0) ((rawLevel * 100) / rawScale) else -1
                val validLevel = if (pct in 1..100) pct else (bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1)
                val finalLevel = if (validLevel in 1..100) validLevel else 100
                stats.level = finalLevel
                lastValidLevel = finalLevel
                stats.isCharging = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                stats.voltage = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 4000) ?: 4000
                stats.temp = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
            }
        } catch (_: Exception) {}
        return stats
    }

    private val _stats = MutableStateFlow(createInitialStats())
    val stats: StateFlow<BatteryInsightStats> = _stats.asStateFlow()

    private val _flow = MutableStateFlow<List<BatteryInsightFlowSample>>(emptyList())
    val flow: StateFlow<List<BatteryInsightFlowSample>> = _flow.asStateFlow()

    private val _history = MutableStateFlow<List<BatteryInsightHistoryBucket>>(emptyList())
    val history: StateFlow<List<BatteryInsightHistoryBucket>> = _history.asStateFlow()

    private val _apps = MutableStateFlow<List<BatteryInsightAppUsage>>(emptyList())
    val apps: StateFlow<List<BatteryInsightAppUsage>> = _apps.asStateFlow()

    private val _lastChargeFlow = MutableStateFlow<List<BatteryInsightFlowSample>>(emptyList())
    val lastChargeFlow: StateFlow<List<BatteryInsightFlowSample>> = _lastChargeFlow.asStateFlow()

    private val _lastChargeHistory = MutableStateFlow<List<BatteryInsightHistoryBucket>>(emptyList())
    val lastChargeHistory: StateFlow<List<BatteryInsightHistoryBucket>> = _lastChargeHistory.asStateFlow()

    private val _lastDischargeFlow = MutableStateFlow<List<BatteryInsightFlowSample>>(emptyList())
    val lastDischargeFlow: StateFlow<List<BatteryInsightFlowSample>> = _lastDischargeFlow.asStateFlow()

    private val _lastDischargeHistory = MutableStateFlow<List<BatteryInsightHistoryBucket>>(emptyList())
    val lastDischargeHistory: StateFlow<List<BatteryInsightHistoryBucket>> = _lastDischargeHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isNotifEnabled = MutableStateFlow(true)
    val isNotifEnabled: StateFlow<Boolean> = _isNotifEnabled.asStateFlow()

    private val _monitorInterval = MutableStateFlow(10000)
    val monitorInterval: StateFlow<Int> = _monitorInterval.asStateFlow()

    private val _autoResetLevelEnabled = MutableStateFlow(false)
    val autoResetLevelEnabled: StateFlow<Boolean> = _autoResetLevelEnabled.asStateFlow()

    private val _autoResetLevel = MutableStateFlow(100)
    val autoResetLevel: StateFlow<Int> = _autoResetLevel.asStateFlow()

    private val _resetOnPlugged = MutableStateFlow(false)
    val resetOnPlugged: StateFlow<Boolean> = _resetOnPlugged.asStateFlow()

    private val _resetOnReboot = MutableStateFlow(false)
    val resetOnReboot: StateFlow<Boolean> = _resetOnReboot.asStateFlow()

    private val _batteryAlarmEnabled = MutableStateFlow(false)
    val batteryAlarmEnabled: StateFlow<Boolean> = _batteryAlarmEnabled.asStateFlow()

    private val _batteryLowThreshold = MutableStateFlow(20)
    val batteryLowThreshold: StateFlow<Int> = _batteryLowThreshold.asStateFlow()

    private val _batteryHighThreshold = MutableStateFlow(80)
    val batteryHighThreshold: StateFlow<Int> = _batteryHighThreshold.asStateFlow()

    private val _alarmFrequency = MutableStateFlow(0)
    val alarmFrequency: StateFlow<Int> = _alarmFrequency.asStateFlow()

    private val _batteryAlarmVibrate = MutableStateFlow(false)
    val batteryAlarmVibrate: StateFlow<Boolean> = _batteryAlarmVibrate.asStateFlow()

    private val _batteryAlarmSound = MutableStateFlow<String?>(null)
    val batteryAlarmSound: StateFlow<String?> = _batteryAlarmSound.asStateFlow()

    private val _fullChargeAlarmEnabled = MutableStateFlow(false)
    val fullChargeAlarmEnabled: StateFlow<Boolean> = _fullChargeAlarmEnabled.asStateFlow()

    private val _zeroCurrentAlarmEnabled = MutableStateFlow(false)
    val zeroCurrentAlarmEnabled: StateFlow<Boolean> = _zeroCurrentAlarmEnabled.asStateFlow()

    // --- GitHub Releases In-App Updater State ---
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateDownloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateDownloadState: StateFlow<UpdateDownloadState> = _updateDownloadState.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateSnackMessage = MutableStateFlow<String?>(null)
    val updateSnackMessage: StateFlow<String?> = _updateSnackMessage.asStateFlow()

    private var hasLoadedSettings = false

    private var pollingJob: kotlinx.coroutines.Job? = null

    init {
        try {
            val ctx = com.acer.batteryinsight.BatteryInsightApp.instance
            val prefs = ctx?.getSharedPreferences("battery_insight_prefs", android.content.Context.MODE_PRIVATE)
            if (prefs?.getBoolean("onboarding_completed", false) == true) {
                com.acer.batteryinsight.utils.ShellUtils.initRootShell()
                startPolling()
            }
            checkForUpdates(isManual = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ViewModel", e)
        }
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
            if (binder != null) {
                service = IBatteryInsightService.Stub.asInterface(binder)
                _isServiceConnected.value = true
                _isLoading.value = false
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            service = null
            _isServiceConnected.value = false
        }
    }

    private fun connectService(): IBatteryInsightService? {
        service?.let { return it }
        val local = com.acer.batteryinsight.service.BatteryInsightService.instance
        if (local != null) {
            service = local
            _isServiceConnected.value = true
            return local
        }
        val ctx = com.acer.batteryinsight.BatteryInsightApp.instance
        if (ctx != null) {
            try {
                val intent = android.content.Intent(ctx, com.acer.batteryinsight.service.BatteryInsightService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
                ctx.bindService(intent, serviceConnection, android.content.Context.BIND_AUTO_CREATE)
            } catch (_: Exception) {}
        }
        return null
    }

    fun pausePolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun resumePolling() {
        startPolling()
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = connectService() ?: return@launch
            try {
                _apps.value = s.getAppUsageSinceLastCharge(40)?.toList() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        val ctx = com.acer.batteryinsight.BatteryInsightApp.instance
        if (ctx != null) {
            try {
                val intent = android.content.Intent(ctx, com.acer.batteryinsight.service.BatteryInsightService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
            } catch (_: Exception) {}
        }
        pollingJob = viewModelScope.launch {
            var tick = 0
            val startTime = System.currentTimeMillis()
            while (true) {
                try {
                    refreshData(tick)
                    if (_isServiceConnected.value || System.currentTimeMillis() - startTime > 3000) {
                        _isLoading.value = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in polling loop", e)
                }
                tick++
                val intervalMs = _monitorInterval.value.coerceIn(1000, 60000).toLong()
                delay(intervalMs)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pausePolling()
        try {
            com.acer.batteryinsight.BatteryInsightApp.instance?.unbindService(serviceConnection)
        } catch (_: Exception) {}
    }

    private suspend fun refreshData(tick: Int) = withContext(Dispatchers.IO) {
        val s = connectService()
        if (s == null) {
            _isServiceConnected.value = false
            return@withContext
        }
        _isServiceConnected.value = true
        try {
            val currentStats = s.batteryState
            if (currentStats != null) {
                if (currentStats.level in 1..100) {
                    lastValidLevel = currentStats.level
                } else {
                    currentStats.level = lastValidLevel
                }
                _stats.value = currentStats
            }
            _isEnabled.value = s.isEnabled

            // Realtime Flow: update every 2-3s
            if (tick % 2 == 0) {
                _flow.value = s.getCurrentFlow(30)?.toList() ?: emptyList()
            }

            // Hourly History: update every 30s
            if (tick % 20 == 0 || _history.value.isEmpty()) {
                _history.value = s.history?.toList() ?: emptyList()
            }

            // App Usage: only update when empty or every 60 ticks (to avoid heavy dumpsys allocations while UI is active)
            if (_apps.value.isEmpty() || tick % 60 == 0) {
                _apps.value = s.getAppUsageSinceLastCharge(40)?.toList() ?: emptyList()
            }

            // Archived Session data: update every 15s or when empty
            if (tick % 10 == 0 || _lastChargeFlow.value.isEmpty()) {
                try {
                    _lastChargeFlow.value = s.getSessionFlow(1)?.toList() ?: emptyList()
                    _lastChargeHistory.value = s.getSessionHistory(1)?.toList() ?: emptyList()
                    _lastDischargeFlow.value = s.getSessionFlow(2)?.toList() ?: emptyList()
                    _lastDischargeHistory.value = s.getSessionHistory(2)?.toList() ?: emptyList()
                } catch (_: Exception) {}
            }

            // Initial settings sync
            if (!hasLoadedSettings) {
                hasLoadedSettings = true
                _isNotifEnabled.value = currentStats.isNotificationEnabled
                _monitorInterval.value = currentStats.monitorInterval
                _autoResetLevelEnabled.value = currentStats.isAutoResetLevelEnabled
                _autoResetLevel.value = currentStats.autoResetLevel
                _resetOnPlugged.value = currentStats.isResetOnPlugged
                _resetOnReboot.value = currentStats.isResetOnReboot
                _batteryAlarmEnabled.value = currentStats.isBatteryAlarmEnabled
                _batteryLowThreshold.value = currentStats.batteryLowThreshold
                _batteryHighThreshold.value = currentStats.batteryHighThreshold
                _alarmFrequency.value = currentStats.alarmFrequency
                _fullChargeAlarmEnabled.value = currentStats.isFullChargeAlarmEnabled
                _zeroCurrentAlarmEnabled.value = currentStats.isZeroCurrentAlarmEnabled
                _batteryAlarmSound.value = currentStats.batteryAlarmSound
                _batteryAlarmVibrate.value = currentStats.isBatteryAlarmVibrate
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshData failed", e)
            service = null
            _isServiceConnected.value = false
        }
    }

    fun setEnabled(v: Boolean) {
        _isEnabled.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setEnabled(v)
            } catch (_: Exception) {}
        }
    }

    fun setNotifEnabled(v: Boolean) {
        _isNotifEnabled.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setNotificationEnabled(v)
            } catch (_: Exception) {}
        }
    }

    fun setMonitorInterval(v: Int) {
        _monitorInterval.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setMonitorInterval(v)
            } catch (_: Exception) {}
        }
    }

    fun setAutoResetLevel(v: Int) {
        _autoResetLevel.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setAutoResetLevel(v)
            } catch (_: Exception) {}
        }
    }

    fun setAutoResetLevelEnabled(v: Boolean) {
        _autoResetLevelEnabled.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setAutoResetLevelEnabled(v)
            } catch (_: Exception) {}
        }
    }

    fun setResetOnPlugged(v: Boolean) {
        _resetOnPlugged.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setResetOnPlugged(v)
            } catch (_: Exception) {}
        }
    }

    fun setResetOnReboot(v: Boolean) {
        _resetOnReboot.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setResetOnReboot(v)
            } catch (_: Exception) {}
        }
    }

    fun setBatteryAlarmEnabled(v: Boolean) {
        _batteryAlarmEnabled.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setBatteryAlarmEnabled(v)
            } catch (_: Exception) {}
        }
    }

    fun setBatteryLowThreshold(v: Int) {
        _batteryLowThreshold.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setBatteryLowThreshold(v)
            } catch (_: Exception) {}
        }
    }

    fun setBatteryHighThreshold(v: Int) {
        _batteryHighThreshold.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setBatteryHighThreshold(v)
            } catch (_: Exception) {}
        }
    }

    fun setAlarmFrequency(v: Int) {
        _alarmFrequency.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setAlarmFrequency(v)
            } catch (_: Exception) {}
        }
    }

    fun setFullChargeAlarmEnabled(v: Boolean) {
        _fullChargeAlarmEnabled.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setFullChargeAlarmEnabled(v)
            } catch (_: Exception) {}
        }
    }

    fun setZeroCurrentAlarmEnabled(v: Boolean) {
        _zeroCurrentAlarmEnabled.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setZeroCurrentAlarmEnabled(v)
            } catch (_: Exception) {}
        }
    }

    fun setBatteryAlarmSound(uri: String?) {
        _batteryAlarmSound.value = uri
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setBatteryAlarmSound(uri)
            } catch (_: Exception) {}
        }
    }

    fun setBatteryAlarmVibrate(v: Boolean) {
        _batteryAlarmVibrate.value = v
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.setBatteryAlarmVibrate(v)
            } catch (_: Exception) {}
        }
    }

    fun refreshSessionArchives() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                _lastChargeFlow.value = s.getSessionFlow(1)?.toList() ?: emptyList()
                _lastChargeHistory.value = s.getSessionHistory(1)?.toList() ?: emptyList()
                _lastDischargeFlow.value = s.getSessionFlow(2)?.toList() ?: emptyList()
                _lastDischargeHistory.value = s.getSessionHistory(2)?.toList() ?: emptyList()
            } catch (_: Exception) {}
        }
    }

    fun resetStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = connectService() ?: return@launch
                s.resetStats()
                _flow.value = emptyList()
                _history.value = emptyList()
                _apps.value = emptyList()
            } catch (_: Exception) {}
        }
    }

    // --- GitHub Releases In-App Updater ---

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            try {
                val ctx = com.acer.batteryinsight.BatteryInsightApp.instance
                if (ctx != null && !UpdateManager.isNetworkAvailable(ctx)) {
                    if (isManual) {
                        _updateSnackMessage.value = "ERROR"
                    }
                    return@launch
                }
                _isCheckingUpdate.value = true
                val result = UpdateManager.checkForUpdate()
                _isCheckingUpdate.value = false
                result.onSuccess { info ->
                    if (info.isUpdateAvailable) {
                        _updateInfo.value = info
                        _updateDownloadState.value = UpdateDownloadState.Idle
                    } else if (isManual) {
                        _updateSnackMessage.value = "UP_TO_DATE"
                    }
                }.onFailure {
                    if (isManual) {
                        _updateSnackMessage.value = "ERROR"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _isCheckingUpdate.value = false
                if (isManual) {
                    _updateSnackMessage.value = "ERROR"
                }
            }
        }
    }

    fun onNotificationClickedForUpdate(context: Context) {
        try {
            val cached = UpdateManager.getCachedUpdateInfo(context)
            if (cached != null && cached.isUpdateAvailable) {
                _updateInfo.value = cached
                _updateDownloadState.value = UpdateDownloadState.Idle
            } else {
                checkForUpdates(isManual = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed handling notification update click", e)
        }
    }

    fun startUpdateDownload(context: Context) {
        val info = _updateInfo.value ?: return
        val url = info.downloadUrl ?: return
        viewModelScope.launch {
            _updateDownloadState.value = UpdateDownloadState.Downloading(0, 0, info.apkSize)
            val res = UpdateManager.downloadApk(
                context = context,
                downloadUrl = url,
                versionName = info.latestVersion,
                onProgress = { pct, down, total ->
                    _updateDownloadState.value = UpdateDownloadState.Downloading(pct, down, total)
                }
            )
            res.onSuccess { file ->
                _updateDownloadState.value = UpdateDownloadState.ReadyToInstall(file)
            }.onFailure { err ->
                _updateDownloadState.value = UpdateDownloadState.Error(err.localizedMessage ?: "Download failed")
            }
        }
    }

    fun installDownloadedApk(context: Context, file: File) {
        UpdateManager.installApk(context, file)
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
        _updateDownloadState.value = UpdateDownloadState.Idle
    }

    fun clearUpdateSnackMessage() {
        _updateSnackMessage.value = null
    }
}
