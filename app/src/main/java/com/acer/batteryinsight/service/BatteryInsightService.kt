/*
 * SPDX-FileCopyrightText: 2026 kenway214 & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.acer.batteryinsight.R
import com.acer.batteryinsight.model.BatteryInsightAppUsage
import com.acer.batteryinsight.model.BatteryInsightFlowSample
import com.acer.batteryinsight.model.BatteryInsightHistoryBucket
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.updater.UpdateManager
import com.acer.batteryinsight.utils.ExitReasonTracker
import com.acer.batteryinsight.utils.IconUtils
import com.acer.batteryinsight.utils.ShellUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

class BatteryInsightService : Service() {

    companion object {
        private const val TAG = "BatteryInsightService"
        private const val NOTIF_CHANNEL_ID = "battery_insight_monitor_v4"
        // v2: the channel is silent because the user-selected sound/vibration is played manually.
        private const val ALARM_CHANNEL_ID = "battery_insight_alarm_channel_v2"
        private const val NOTIF_ID = 1001
        private const val ALARM_NOTIF_ID = 1002

        private const val FLOW_SAMPLE_INTERVAL_MS = 2000L
        private const val MAX_FLOW_SAMPLES = 1440
        private const val MAX_HISTORY_BUCKETS = 72
        private const val DEFAULT_MONITOR_INTERVAL_MS = 10000

        private const val SESSION_SAVE_INTERVAL_MS = 30_000L
        private const val OOM_REASSERT_INTERVAL_MS = 60_000L
        private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L
        private const val IMMORTALITY_INTERVAL_MS = 30 * 60 * 1000L
        private const val APP_USAGE_REFRESH_MS = 2 * 60 * 1000L
        private const val APP_USAGE_RETRY_MS = 30 * 1000L
        // Upper bound on dumpsys lines kept in memory; the power section is a few hundred lines.
        private const val DUMPSYS_MAX_LINES = 1500
        private const val ZERO_CURRENT_MAX_MA = 5
        private const val ZERO_CURRENT_SUSTAIN_MS = 20_000L

        const val WATCHDOG_ACTION = "com.acer.batteryinsight.WATCHDOG_KEEPALIVE"
        const val PREF_NOTIF_ICON_STYLE = "battery_insight_notif_icon_style"

        @Volatile
        var instance: IBatteryInsightService? = null
            private set
    }

    // Without a handler, any exception (or Error such as OOM) escaping a launched coroutine is
    // rethrown on the worker thread and kills the whole process.
    private val coroutineErrorHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "Uncaught error in service coroutine", e)
    }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + coroutineErrorHandler)

    // All mutable session state is touched from this single thread (broadcasts + monitor loop),
    // and additionally guarded by mCurrentStats for binder callers.
    private val stateExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "BatteryInsightState") }
    private val stateDispatcher = stateExecutor.asCoroutineDispatcher()
    private var monitorJob: Job? = null
    @Volatile private var isInForeground = false

    private lateinit var prefs: SharedPreferences
    private lateinit var notifManager: NotificationManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var storage: SessionStorage
    private var vibrator: Vibrator? = null
    private var activeRingtone: Ringtone? = null

    private val mCurrentStats = BatteryInsightStats()
    private val mFlowSamples = CopyOnWriteArrayList<BatteryInsightFlowSample>()
    private val mHistoryBuckets = CopyOnWriteArrayList<BatteryInsightHistoryBucket>()
    private val mAppUsages = CopyOnWriteArrayList<BatteryInsightAppUsage>()

    private val mLastChargeFlowSamples = CopyOnWriteArrayList<BatteryInsightFlowSample>()
    private val mLastChargeHistoryBuckets = CopyOnWriteArrayList<BatteryInsightHistoryBucket>()
    private val mLastDischargeFlowSamples = CopyOnWriteArrayList<BatteryInsightFlowSample>()
    private val mLastDischargeHistoryBuckets = CopyOnWriteArrayList<BatteryInsightHistoryBucket>()

    @Volatile private var batteryBasePath: String = "/sys/class/power_supply/battery"
    @Volatile private var verifiedCurrentNowPath: String? = null
    @Volatile private var cycleCountPath: String? = null
    @Volatile private var cachedDesignCapacity: Int = 0
    @Volatile private var cachedChargeFull: Int = 0
    @Volatile private var cachedCycleCount: Int = 0
    private var lastStaticStatsCheckRealtime: Long = 0L
    private val cachedAppMap = mutableMapOf<Int, Pair<String, String>>()

    private var isScreenOn = true
    private var isCharging = false
    private var isPlugged = false
    private var batteryStatus = -1

    private var lastBatteryLevel = -1
    private var levelValidator = BatteryLevelValidator()
    private var currentNormalizer = CurrentNormalizer()
    private var lastFlowSampleTime = 0L

    // Session Metrics State
    private var sessionStartRealtime = SystemClock.elapsedRealtime()
    private var sessionStartUptime = SystemClock.uptimeMillis()
    private var serviceSessionStartTime = System.currentTimeMillis()

    private var accumScreenOnMs = 0L
    private var accumScreenOffMs = 0L
    private var accumAwakeMs = 0L
    private var accumDeepSleepMs = 0L
    private var accumDischargeOn = 0
    private var accumDischargeOff = 0

    // Charging session tracking
    private var accumChargeOn = 0
    private var accumChargeOff = 0
    private var accumChargeScreenOnMs = 0L
    private var accumChargeScreenOffMs = 0L
    private var chargeCurrentSum = 0L
    private var chargeSampleCount = 0

    /** Wall time of the last plug / unplug transition; bounds the archived "last" sessions. */
    private var segmentStartWallMs = 0L

    // Current hourly bucket baselines and per-hour current statistics
    private var bucketStartDrain = 0
    private var bucketStartScreenOnMs = 0L
    private var bucketMinCurrent = Int.MAX_VALUE
    private var bucketMaxCurrent = Int.MIN_VALUE
    private var bucketCurrentSum = 0L
    private var bucketSampleCount = 0

    private var lastScreenStateChangeRealtime = SystemClock.elapsedRealtime()
    private var lastScreenStateChangeUptime = SystemClock.uptimeMillis()

    private var lastSessionSaveTime = 0L
    private var lastOomScoreTime = 0L
    private var lastWatchdogTime = 0L
    private var lastImmortalityTime = 0L

    // Session-wide current statistics
    private var minCurrent = Int.MAX_VALUE
    private var maxCurrent = Int.MIN_VALUE
    private var totalCurrentSum = 0L
    private var sampleCount = 0

    // Alarms
    private val lowAlarm = AlarmTracker()
    private val highAlarm = AlarmTracker()
    private val fullAlarm = AlarmTracker()
    private val zeroCurrentAlarm = AlarmTracker()
    private var zeroCurrentSinceMs = 0L

    // App usage refresh
    private val appUsageFetchInFlight = AtomicBoolean(false)
    @Volatile private var lastAppUsageFetchMs = 0L

    // Notification caching – prevent redundant IPC calls when values haven't changed
    private var lastNotifTitle = ""
    private var lastNotifSummary = ""
    private var lastNotifBody = ""
    private var lastNotifIconKey = ""
    private val statusIconRenderer = StatusIconRenderer()

    private var cachedContentIntentComponent: String? = null
    private var cachedContentIntent: PendingIntent? = null

    private val binder = object : IBatteryInsightService.Stub() {
        override fun getBatteryState(): BatteryInsightStats = synchronized(mCurrentStats) {
            fillStatsLocked(recordSample = false)
            mCurrentStats.copy()
        }

        override fun getCurrentFlow(minutes: Int): Array<BatteryInsightFlowSample> {
            val count = (minutes * 30).coerceAtLeast(1)
            return mFlowSamples.takeLast(count).toTypedArray()
        }

        override fun getHistory(): Array<BatteryInsightHistoryBucket> = copyBuckets(mHistoryBuckets)

        override fun getSessionFlow(sessionType: Int): Array<BatteryInsightFlowSample> {
            return when (sessionType) {
                1 -> mLastChargeFlowSamples.toTypedArray()
                2 -> mLastDischargeFlowSamples.toTypedArray()
                else -> mFlowSamples.toTypedArray()
            }
        }

        override fun getSessionHistory(sessionType: Int): Array<BatteryInsightHistoryBucket> {
            return when (sessionType) {
                1 -> copyBuckets(mLastChargeHistoryBuckets)
                2 -> copyBuckets(mLastDischargeHistoryBuckets)
                else -> copyBuckets(mHistoryBuckets)
            }
        }

        override fun getAppUsageSinceLastCharge(maxEntries: Int): Array<BatteryInsightAppUsage> {
            maybeRefreshAppUsages()
            return mAppUsages.take(maxEntries).toTypedArray()
        }

        override fun resetStats() {
            this@BatteryInsightService.resetStatsInternal("User requested reset")
        }

        override fun isEnabled(): Boolean = prefs.getBoolean("battery_insight_enabled", true)
        override fun setEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_enabled", enabled).commit()
            if (enabled) forceNotificationUpdate() else notifManager.cancel(NOTIF_ID)
            applyRootKeepAlive()
        }

        override fun setRootKeepAliveEnabled(enabled: Boolean) {
            prefs.edit().putBoolean(RootKeepAlive.PREF_ENABLED, enabled).commit()
            applyRootKeepAlive()
        }

        override fun isNotificationEnabled(): Boolean = prefs.getBoolean("battery_insight_notif_enabled", true)
        override fun setNotificationEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_notif_enabled", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isNotificationEnabled = enabled }
            if (enabled) forceNotificationUpdate() else notifManager.cancel(NOTIF_ID)
        }

        override fun setNotificationIconStyle(style: Int) {
            prefs.edit().putInt(PREF_NOTIF_ICON_STYLE, style.coerceIn(0, StatusIconRenderer.STYLE_COUNT - 1)).apply()
            forceNotificationUpdate()
        }

        override fun setAutoResetLevel(level: Int) {
            prefs.edit().putInt("battery_insight_auto_reset_level", level).apply()
            synchronized(mCurrentStats) { mCurrentStats.autoResetLevel = level }
        }

        override fun setAutoResetLevelEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_auto_reset_level_enabled", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isAutoResetLevelEnabled = enabled }
        }

        override fun setResetOnPlugged(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_reset_on_plugged", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isResetOnPlugged = enabled }
        }

        override fun setResetOnReboot(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_reset_on_reboot", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isResetOnReboot = enabled }
        }

        override fun getMonitorInterval(): Int = prefs.getInt("battery_insight_monitor_interval", DEFAULT_MONITOR_INTERVAL_MS)
        override fun setMonitorInterval(intervalMs: Int) {
            prefs.edit().putInt("battery_insight_monitor_interval", intervalMs).apply()
            synchronized(mCurrentStats) { mCurrentStats.monitorInterval = intervalMs }
        }

        override fun setBatteryAlarmEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_battery_alarm_enabled", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isBatteryAlarmEnabled = enabled }
        }

        override fun setBatteryLowThreshold(threshold: Int) {
            prefs.edit().putInt("battery_insight_battery_low_threshold", threshold).apply()
            synchronized(mCurrentStats) { mCurrentStats.batteryLowThreshold = threshold }
        }

        override fun setBatteryHighThreshold(threshold: Int) {
            prefs.edit().putInt("battery_insight_battery_high_threshold", threshold).apply()
            synchronized(mCurrentStats) { mCurrentStats.batteryHighThreshold = threshold }
        }

        override fun setAlarmFrequency(frequency: Int) {
            prefs.edit().putInt("battery_insight_alarm_frequency", frequency).apply()
            synchronized(mCurrentStats) {
                mCurrentStats.alarmFrequency = frequency
                // Start counting from the current level under the new rule.
                lowAlarm.reset()
                highAlarm.reset()
            }
        }

        override fun setFullChargeAlarmEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_full_charge_alarm_enabled", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isFullChargeAlarmEnabled = enabled }
        }

        override fun setZeroCurrentAlarmEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_zero_current_alarm_enabled", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isZeroCurrentAlarmEnabled = enabled }
        }

        override fun setBatteryAlarmSound(uri: String?) {
            prefs.edit().putString("battery_insight_battery_alarm_sound", uri).apply()
            synchronized(mCurrentStats) { mCurrentStats.batteryAlarmSound = uri }
        }

        override fun setBatteryAlarmVibrate(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_battery_alarm_vibrate", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isBatteryAlarmVibrate = enabled }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Handled off the main thread, in order, on the state thread.
            scope.launch(stateDispatcher) {
                try {
                    handleBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in batteryReceiver", e)
                }
            }
        }
    }

    private fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> synchronized(mCurrentStats) { handleBatteryChangedLocked(intent) }
            Intent.ACTION_POWER_CONNECTED -> synchronized(mCurrentStats) {
                accumulateScreenTimeLocked()
                isPlugged = true
                archiveSessionLocked(isChargingSession = false)
                segmentStartWallMs = System.currentTimeMillis()
                if (prefs.getBoolean("battery_insight_reset_on_plugged", false)) {
                    resetStatsInternal("Reset on power connected")
                }
                saveSessionState()
            }
            Intent.ACTION_POWER_DISCONNECTED -> synchronized(mCurrentStats) {
                accumulateScreenTimeLocked()
                isPlugged = false
                zeroCurrentSinceMs = 0L
                mCurrentStats.chargeScreenOnTime = accumChargeScreenOnMs
                mCurrentStats.chargeScreenOffTime = accumChargeScreenOffMs
                val totalChargeGain = accumChargeOn + accumChargeOff
                val chargeHoursTotal = (accumChargeScreenOnMs + accumChargeScreenOffMs) / 3600000f
                if (chargeHoursTotal >= 0.016f && totalChargeGain > 0) {
                    mCurrentStats.chargeRate = totalChargeGain / chargeHoursTotal
                }
                archiveSessionLocked(isChargingSession = true)
                segmentStartWallMs = System.currentTimeMillis()
                saveSessionState()
            }
            Intent.ACTION_SCREEN_ON -> synchronized(mCurrentStats) { handleScreenStateChangeLocked(true) }
            Intent.ACTION_SCREEN_OFF -> synchronized(mCurrentStats) { handleScreenStateChangeLocked(false) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            prefs = getSharedPreferences("battery_insight_prefs", Context.MODE_PRIVATE)
            notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            storage = SessionStorage(filesDir)
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            createNotificationChannels()
            ExitReasonTracker.logRecent(this)
            synchronized(mCurrentStats) {
                loadSettingsIntoStats()
                restoreSessionState()
                restoreSessionArchives()
                restoreBucketState()

                val stickyBattery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val rawLevel = stickyBattery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val rawScale = stickyBattery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val stickyPct = if (rawLevel >= 0 && rawScale > 0) ((rawLevel * 100) / rawScale) else -1
                val plugged = stickyBattery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                isPlugged = plugged != 0
                batteryStatus = stickyBattery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                    batteryStatus == BatteryManager.BATTERY_STATUS_FULL || isPlugged

                val initialLevel = acceptLevelLocked(stickyPct)
                if (BatteryLevelValidator.isValid(initialLevel)) {
                    mCurrentStats.level = initialLevel
                    lastBatteryLevel = initialLevel
                }

                val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                isScreenOn = powerManager?.isInteractive ?: true
                lastScreenStateChangeRealtime = SystemClock.elapsedRealtime()
                lastScreenStateChangeUptime = SystemClock.uptimeMillis()
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

            instance = binder
            if (!startForegroundSafely()) {
                scheduleWatchdog()
                stopSelf()
                return
            }
            scheduleWatchdog()

            // Asynchronously detect hardware paths and initialize root in background without freezing main UI thread
            scope.launch(Dispatchers.IO) {
                detectHardwarePaths()
                synchronized(mCurrentStats) { fillStatsLocked(recordSample = false) }
                ShellUtils.initRootShell { isRoot ->
                    if (isRoot) {
                        scope.launch(Dispatchers.IO) {
                            detectHardwarePaths()
                            applyRootImmortality()
                            applyRootKeepAlive()
                            synchronized(mCurrentStats) { fillStatsLocked(recordSample = false) }
                        }
                    }
                }
                startMonitorLoop()
                checkBackgroundUpdates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during onCreate of BatteryInsightService", e)
        }
    }

    private fun copyBuckets(source: List<BatteryInsightHistoryBucket>): Array<BatteryInsightHistoryBucket> =
        synchronized(mCurrentStats) { source.map { it.copy() }.toTypedArray() }

    private fun checkBackgroundUpdates(delayMs: Long = 15000L) {
        scope.launch(Dispatchers.IO) {
            if (delayMs > 0) delay(delayMs)
            if (!UpdateManager.shouldCheckBackground(this@BatteryInsightService)) {
                return@launch
            }
            if (!UpdateManager.isNetworkAvailable(this@BatteryInsightService)) {
                return@launch
            }
            val res = UpdateManager.checkForUpdate()
            res.onSuccess { info ->
                UpdateManager.recordCheckTime(this@BatteryInsightService)
                if (info.isUpdateAvailable) {
                    UpdateManager.showUpdateNotification(this@BatteryInsightService, info)
                }
            }
        }
    }

    // region Persistence

    private fun saveSessionState() {
        try {
            prefs.edit().apply {
                putLong("session_start_realtime", sessionStartRealtime)
                putLong("session_start_uptime", sessionStartUptime)
                putLong("session_start_walltime", serviceSessionStartTime)
                putLong("accum_screen_on_ms", accumScreenOnMs)
                putLong("accum_screen_off_ms", accumScreenOffMs)
                putLong("accum_awake_ms", accumAwakeMs)
                putLong("accum_deep_sleep_ms", accumDeepSleepMs)
                putInt("accum_discharge_on", accumDischargeOn)
                putInt("accum_discharge_off", accumDischargeOff)
                putInt("accum_charge_on", accumChargeOn)
                putInt("accum_charge_off", accumChargeOff)
                putLong("accum_charge_screen_on_ms", accumChargeScreenOnMs)
                putLong("accum_charge_screen_off_ms", accumChargeScreenOffMs)
                putLong("charge_current_sum", chargeCurrentSum)
                putInt("charge_sample_count", chargeSampleCount)
                putLong("total_current_sum", totalCurrentSum)
                putInt("sample_count", sampleCount)
                putInt("min_current", minCurrent)
                putInt("max_current", maxCurrent)
                putLong("segment_start_wall", segmentStartWallMs)
                if (BatteryLevelValidator.isValid(lastBatteryLevel)) {
                    putInt("last_battery_level", lastBatteryLevel)
                    putLong("last_battery_level_time", levelValidator.lastAcceptedAtMs)
                }
                mHistoryBuckets.lastOrNull()?.let { b ->
                    putInt("bucket_start_day", b.epochDay)
                    putInt("bucket_start_hour", b.hour)
                    putInt("bucket_start_drain", bucketStartDrain)
                    putLong("bucket_start_sot", bucketStartScreenOnMs)
                }
                if (currentNormalizer.dirty) {
                    putInt("current_unit_sysfs", unitToPref(currentNormalizer.sysfsMicroAmps))
                    putInt("current_unit_bm", unitToPref(currentNormalizer.batteryManagerMicroAmps))
                    putInt("current_discharge_sign", currentNormalizer.dischargeSign)
                    putInt("current_charge_sign", currentNormalizer.chargeSign)
                    currentNormalizer.markSaved()
                }
                apply()
            }
        } catch (_: Exception) {}
    }

    private fun unitToPref(micro: Boolean?): Int = when (micro) {
        true -> 1
        false -> 2
        null -> 0
    }

    private fun prefToUnit(value: Int): Boolean? = when (value) {
        1 -> true
        2 -> false
        else -> null
    }

    private fun restoreSessionState() {
        try {
            currentNormalizer = CurrentNormalizer(
                sysfsMicroAmps = prefToUnit(prefs.getInt("current_unit_sysfs", 0)),
                batteryManagerMicroAmps = prefToUnit(prefs.getInt("current_unit_bm", 0)),
                dischargeSign = prefs.getInt("current_discharge_sign", 0),
                chargeSign = prefs.getInt("current_charge_sign", 0),
            )
            val savedLevel = prefs.getInt("last_battery_level", -1)
            if (BatteryLevelValidator.isValid(savedLevel)) {
                lastBatteryLevel = savedLevel
                levelValidator = BatteryLevelValidator(savedLevel, prefs.getLong("last_battery_level_time", 0L))
                if (mCurrentStats.level <= 0) {
                    mCurrentStats.level = savedLevel
                }
            }

            if (!prefs.contains("session_start_walltime")) return
            sessionStartRealtime = prefs.getLong("session_start_realtime", sessionStartRealtime)
            sessionStartUptime = prefs.getLong("session_start_uptime", sessionStartUptime)
            serviceSessionStartTime = prefs.getLong("session_start_walltime", serviceSessionStartTime)
            accumScreenOnMs = prefs.getLong("accum_screen_on_ms", 0L)
            accumScreenOffMs = prefs.getLong("accum_screen_off_ms", 0L)
            accumAwakeMs = prefs.getLong("accum_awake_ms", 0L)
            accumDeepSleepMs = prefs.getLong("accum_deep_sleep_ms", 0L)
            accumDischargeOn = prefs.getInt("accum_discharge_on", 0)
            accumDischargeOff = prefs.getInt("accum_discharge_off", 0)
            accumChargeOn = prefs.getInt("accum_charge_on", 0)
            accumChargeOff = prefs.getInt("accum_charge_off", 0)
            accumChargeScreenOnMs = prefs.getLong("accum_charge_screen_on_ms", 0L)
            accumChargeScreenOffMs = prefs.getLong("accum_charge_screen_off_ms", 0L)
            chargeCurrentSum = prefs.getLong("charge_current_sum", 0L)
            chargeSampleCount = prefs.getInt("charge_sample_count", 0)
            totalCurrentSum = prefs.getLong("total_current_sum", 0L)
            sampleCount = prefs.getInt("sample_count", 0)
            minCurrent = prefs.getInt("min_current", Int.MAX_VALUE)
            maxCurrent = prefs.getInt("max_current", Int.MIN_VALUE)
            segmentStartWallMs = prefs.getLong("segment_start_wall", 0L)
        } catch (_: Exception) {}
    }

    /** Restores the baselines of the in-progress hourly bucket so its hourly deltas survive restarts. */
    private fun restoreBucketState() {
        val last = mHistoryBuckets.lastOrNull() ?: return
        if (prefs.getInt("bucket_start_day", Int.MIN_VALUE) == last.epochDay &&
            prefs.getInt("bucket_start_hour", -1) == last.hour
        ) {
            bucketStartDrain = prefs.getInt("bucket_start_drain", 0)
            bucketStartScreenOnMs = prefs.getLong("bucket_start_sot", 0L)
        } else {
            // Unknown baseline (e.g. data from an older version): derive it from the stored deltas.
            bucketStartDrain = (last.drainPercent - last.hourlyDrainPercent).coerceAtLeast(0)
            bucketStartScreenOnMs = (last.screenOnMs - last.hourlyScreenOnMs).coerceAtLeast(0L)
        }
        bucketMinCurrent = if (last.minCurrent != 0) last.minCurrent else Int.MAX_VALUE
        bucketMaxCurrent = if (last.maxCurrent != 0) last.maxCurrent else Int.MIN_VALUE
        bucketCurrentSum = 0L
        bucketSampleCount = 0
    }

    private fun bucketEndWallMs(b: BatteryInsightHistoryBucket): Long = try {
        LocalDate.ofEpochDay(b.epochDay.toLong())
            .atTime(b.hour.coerceIn(0, 23), 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() + 3_600_000L
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    private fun archiveSessionLocked(isChargingSession: Boolean) {
        try {
            val since = segmentStartWallMs
            val flow = mFlowSamples.filter { it.isCharging == isChargingSession && (since <= 0L || it.timestamp >= since) }
            val buckets = mHistoryBuckets.filter { since <= 0L || bucketEndWallMs(it) > since }.map { it.copy() }
            val (targetFlow, targetBuckets) = if (isChargingSession) {
                mLastChargeFlowSamples to mLastChargeHistoryBuckets
            } else {
                mLastDischargeFlowSamples to mLastDischargeHistoryBuckets
            }
            if (flow.isNotEmpty()) {
                targetFlow.clear()
                targetFlow.addAll(flow)
            }
            if (buckets.isNotEmpty()) {
                targetBuckets.clear()
                targetBuckets.addAll(buckets)
            }
            saveArchives(includeLastSessions = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving session", e)
        }
    }

    /** Snapshots under the lock, writes on the IO dispatcher. */
    private fun saveArchives(includeLastSessions: Boolean) {
        val snapshot = synchronized(mCurrentStats) { ArchiveSnapshot.take(this, includeLastSessions) }
        scope.launch(Dispatchers.IO) { snapshot.writeTo(storage) }
    }

    private class ArchiveSnapshot(
        val activeFlow: List<BatteryInsightFlowSample>,
        val activeBuckets: List<BatteryInsightHistoryBucket>,
        val lastChargeFlow: List<BatteryInsightFlowSample>?,
        val lastChargeBuckets: List<BatteryInsightHistoryBucket>?,
        val lastDischargeFlow: List<BatteryInsightFlowSample>?,
        val lastDischargeBuckets: List<BatteryInsightHistoryBucket>?,
    ) {
        companion object {
            fun take(s: BatteryInsightService, includeLast: Boolean) = ArchiveSnapshot(
                s.mFlowSamples.toList(),
                s.mHistoryBuckets.map { it.copy() },
                if (includeLast) s.mLastChargeFlowSamples.toList() else null,
                if (includeLast) s.mLastChargeHistoryBuckets.map { it.copy() } else null,
                if (includeLast) s.mLastDischargeFlowSamples.toList() else null,
                if (includeLast) s.mLastDischargeHistoryBuckets.map { it.copy() } else null,
            )
        }

        fun writeTo(storage: SessionStorage) {
            storage.saveFlow(SessionStorage.ACTIVE_FLOW, activeFlow)
            storage.saveBuckets(SessionStorage.ACTIVE_BUCKETS, activeBuckets)
            lastChargeFlow?.let { storage.saveFlow(SessionStorage.LAST_CHARGE_FLOW, it) }
            lastChargeBuckets?.let { storage.saveBuckets(SessionStorage.LAST_CHARGE_BUCKETS, it) }
            lastDischargeFlow?.let { storage.saveFlow(SessionStorage.LAST_DISCHARGE_FLOW, it) }
            lastDischargeBuckets?.let { storage.saveBuckets(SessionStorage.LAST_DISCHARGE_BUCKETS, it) }
        }
    }

    private fun restoreSessionArchives() {
        try {
            val activeFlow = storage.loadFlow(SessionStorage.ACTIVE_FLOW)
            if (activeFlow.isNotEmpty() && mFlowSamples.isEmpty()) {
                mFlowSamples.addAll(activeFlow.takeLast(MAX_FLOW_SAMPLES))
            }
            val activeBuckets = storage.loadBuckets(SessionStorage.ACTIVE_BUCKETS)
            if (activeBuckets.isNotEmpty() && mHistoryBuckets.isEmpty()) {
                mHistoryBuckets.addAll(activeBuckets.takeLast(MAX_HISTORY_BUCKETS))
            }
            replaceIfNotEmpty(mLastChargeFlowSamples, storage.loadFlow(SessionStorage.LAST_CHARGE_FLOW))
            replaceIfNotEmpty(mLastChargeHistoryBuckets, storage.loadBuckets(SessionStorage.LAST_CHARGE_BUCKETS))
            replaceIfNotEmpty(mLastDischargeFlowSamples, storage.loadFlow(SessionStorage.LAST_DISCHARGE_FLOW))
            replaceIfNotEmpty(mLastDischargeHistoryBuckets, storage.loadBuckets(SessionStorage.LAST_DISCHARGE_BUCKETS))
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring session archives", e)
        }
    }

    private fun <T> replaceIfNotEmpty(target: MutableList<T>, items: List<T>) {
        if (items.isNotEmpty()) {
            target.clear()
            target.addAll(items)
        }
    }

    // endregion

    /**
     * Root-only keep-alive tweaks. Deliberately does not chmod sysfs or inject SELinux rules: those
     * would open the battery nodes to every untrusted app. Restricted nodes are read through the root
     * shell instead (see ShellUtils.readSysfsSync).
     */
    private fun applyRootImmortality() {
        scope.launch(Dispatchers.IO) {
            if (!ShellUtils.isRootAvailable()) return@launch
            try {
                val pid = android.os.Process.myPid()
                val pkg = packageName
                val commands = StringBuilder()
                // 1. OOM score adj: Minimum -1000 (Protected from LowMemoryKiller)
                commands.append("echo -1000 > /proc/$pid/oom_score_adj 2>/dev/null; ")
                // 2. Battery & Doze Whitelisting
                commands.append("cmd deviceidle whitelist +$pkg >/dev/null 2>&1 || dumpsys deviceidle whitelist +$pkg >/dev/null 2>&1; ")
                // 3. Prevent process throttling / standby freezing
                commands.append("am set-standby-bucket $pkg active 2>/dev/null; ")
                commands.append("cmd activity set-inactive $pkg false 2>/dev/null; ")
                // 4. Background and Autostart AppOps
                commands.append("cmd appops set $pkg RUN_IN_BACKGROUND allow 2>/dev/null; ")
                commands.append("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null; ")
                commands.append("cmd appops set $pkg START_FOREGROUND allow 2>/dev/null; ")
                commands.append("cmd appops set $pkg AUTO_START allow 2>/dev/null; ")
                commands.append("cmd appops set $pkg BOOT_COMPLETED allow 2>/dev/null; ")
                ShellUtils.execRoot(commands.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error applying root immortality", e)
            }
        }
    }

    /** Starts or stops the out-of-process root watchdog to match the current settings. */
    private fun applyRootKeepAlive() {
        scope.launch(Dispatchers.IO) {
            if (RootKeepAlive.isWanted(this@BatteryInsightService)) {
                RootKeepAlive.ensureRunning(this@BatteryInsightService)
                RootKeepAlive.applyOemExemptions(this@BatteryInsightService)
            } else {
                RootKeepAlive.stop(this@BatteryInsightService)
            }
        }
    }

    private fun watchdogPendingIntent(): PendingIntent {
        val intent = Intent(this, WatchdogReceiver::class.java).apply { action = WATCHDOG_ACTION }
        return PendingIntent.getBroadcast(
            this,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleWatchdog(delayMs: Long = WATCHDOG_INTERVAL_MS) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerAt = SystemClock.elapsedRealtime() + delayMs
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, watchdogPendingIntent())
        } catch (_: Exception) {}
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (prefs.getBoolean("battery_insight_enabled", true)) {
            try {
                val intent = Intent(applicationContext, BatteryInsightService::class.java)
                ContextCompat.startForegroundService(applicationContext, intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Android 14+ uses only the specialUse type: dataSync is limited to 6h/day on Android 15+ and
     * cannot be started from BOOT_COMPLETED, while this monitor runs continuously.
     */
    private fun startForegroundSafely(): Boolean {
        return try {
            val notif = createNotification(buildNotificationContent(snapshotStats()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
            isInForeground = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in startForegroundSafely", e)
            false
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Only reachable if a time-limited type was used; the system requires the service to stop.
        Log.w(TAG, "Foreground service timeout (type=$fgsType), stopping")
        scheduleWatchdog()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("is_reboot", false) == true) {
            if (prefs.getBoolean("battery_insight_reset_on_reboot", false)) {
                resetStatsInternal("Reset on device reboot")
            }
        }
        // A repeated start of an already-foreground service must never stop it: a failed
        // re-promotion (e.g. requested from the background) leaves the existing foreground state intact.
        if (!startForegroundSafely() && !isInForeground) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // region Hardware reading

    private fun detectHardwarePaths() {
        try {
            val possiblePaths = listOf(
                "/sys/class/power_supply/battery",
                "/sys/class/power_supply/bms",
                "/sys/class/power_supply/qcom-battery",
                "/sys/class/power_supply/sec-battery",
                "/sys/class/power_supply/google,battery",
                "/sys/class/power_supply/main",
                "/sys/class/power_supply/BAT0"
            )
            for (path in possiblePaths) {
                val f = File(path)
                if (f.isDirectory || ShellUtils.readSysfsSync("$path/current_now") != null || ShellUtils.readSysfsSync("$path/status") != null) {
                    batteryBasePath = path
                    break
                }
            }

            // Verify and cache single working current_now node
            val currentCandidates = listOf(
                "$batteryBasePath/current_now",
                "/sys/class/power_supply/bms/current_now",
                "/sys/class/power_supply/sec-battery/current_now",
                "/sys/class/power_supply/battery/current_now"
            )
            for (path in currentCandidates) {
                val v = readSysfsInt(path)
                if (v != 0) {
                    verifiedCurrentNowPath = path
                    break
                }
            }

            val possibleCycles = listOf(
                "$batteryBasePath/cycle_count",
                "$batteryBasePath/battery_cycle_count",
                "/sys/class/power_supply/bms/cycle_count",
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/sec-battery/cycle_count"
            )
            for (path in possibleCycles) {
                val v = readSysfsInt(path)
                if (v > 0) {
                    cycleCountPath = path
                    cachedCycleCount = v
                    break
                }
            }

            refreshStaticBatteryInfo()
        } catch (_: Exception) {}
    }

    private fun refreshStaticBatteryInfo() {
        try {
            if (cachedDesignCapacity <= 0) {
                val designCandidates = listOf(
                    "$batteryBasePath/charge_full_design",
                    "/sys/class/power_supply/bms/charge_full_design",
                    "$batteryBasePath/charge_design",
                    "$batteryBasePath/design_capacity",
                    "/sys/class/power_supply/battery/charge_full_design"
                )
                for (path in designCandidates) {
                    var v = readSysfsInt(path)
                    if (v > 0) {
                        if (v > 25000) v /= 1000
                        cachedDesignCapacity = v
                        break
                    }
                }
                if (cachedDesignCapacity <= 0) {
                    try {
                        val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                        val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(this)
                        val getBatteryCapacity = powerProfileClass.getMethod("getBatteryCapacity")
                        val cap = (getBatteryCapacity.invoke(powerProfile) as? Double)?.toInt() ?: 0
                        if (cap in 800..25000) {
                            cachedDesignCapacity = cap
                        }
                    } catch (_: Exception) {}
                }
            }

            // charge_counter is deliberately absent: it is the *remaining* charge, not the full
            // capacity. It is used below (scaled by level) only when no full-capacity node exists.
            val fullCandidates = listOf(
                "$batteryBasePath/charge_full",
                "/sys/class/power_supply/bms/charge_full",
                "/sys/class/power_supply/battery/charge_full"
            )
            for (path in fullCandidates) {
                var v = readSysfsInt(path)
                if (v > 0) {
                    if (v > 25000) v /= 1000
                    cachedChargeFull = v
                    break
                }
            }

            val cPath = cycleCountPath
            if (cPath != null) {
                val v = readSysfsInt(cPath)
                if (v > 0) cachedCycleCount = v
            }

            lastStaticStatsCheckRealtime = SystemClock.elapsedRealtime()
        } catch (_: Exception) {}
    }

    private fun readSysfsInt(path: String): Int =
        ShellUtils.readSysfsSync(path)?.trim()?.toIntOrNull() ?: 0

    private fun readSysfsCapacity(): Int {
        val candidates = listOf(
            "$batteryBasePath/capacity",
            "/sys/class/power_supply/battery/capacity",
            "/sys/class/power_supply/bms/capacity",
            "/sys/class/power_supply/sec-battery/capacity",
            "/sys/class/power_supply/qcom-battery/capacity",
            "/sys/class/power_supply/main/capacity"
        ).distinct()
        for (path in candidates) {
            val cap = readSysfsInt(path)
            if (BatteryLevelValidator.isValid(cap)) return cap
        }
        return -1
    }

    private fun batteryManagerCapacity(): Int =
        try { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (_: Exception) { -1 }

    /** Validates a level reading against history and independent sources (see BatteryLevelValidator). */
    private fun acceptLevelLocked(primary: Int): Int =
        levelValidator.accept(primary, System.currentTimeMillis()) {
            listOf(batteryManagerCapacity(), readSysfsCapacity())
        }

    // endregion

    private fun loadSettingsIntoStats() {
        mCurrentStats.isNotificationEnabled = prefs.getBoolean("battery_insight_notif_enabled", true)
        mCurrentStats.monitorInterval = prefs.getInt("battery_insight_monitor_interval", DEFAULT_MONITOR_INTERVAL_MS)
        mCurrentStats.isAutoResetLevelEnabled = prefs.getBoolean("battery_insight_auto_reset_level_enabled", false)
        mCurrentStats.autoResetLevel = prefs.getInt("battery_insight_auto_reset_level", 100)
        mCurrentStats.isResetOnPlugged = prefs.getBoolean("battery_insight_reset_on_plugged", false)
        mCurrentStats.isResetOnReboot = prefs.getBoolean("battery_insight_reset_on_reboot", false)
        mCurrentStats.isBatteryAlarmEnabled = prefs.getBoolean("battery_insight_battery_alarm_enabled", false)
        mCurrentStats.batteryLowThreshold = prefs.getInt("battery_insight_battery_low_threshold", 20)
        mCurrentStats.batteryHighThreshold = prefs.getInt("battery_insight_battery_high_threshold", 80)
        mCurrentStats.alarmFrequency = prefs.getInt("battery_insight_alarm_frequency", 0)
        mCurrentStats.isFullChargeAlarmEnabled = prefs.getBoolean("battery_insight_full_charge_alarm_enabled", false)
        mCurrentStats.isZeroCurrentAlarmEnabled = prefs.getBoolean("battery_insight_zero_current_alarm_enabled", false)
        mCurrentStats.batteryAlarmSound = prefs.getString("battery_insight_battery_alarm_sound", null)
        mCurrentStats.isBatteryAlarmVibrate = prefs.getBoolean("battery_insight_battery_alarm_vibrate", false)
    }

    private fun handleBatteryChangedLocked(intent: Intent) {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val level = if (rawLevel >= 0 && scale > 0) (rawLevel * 100) / scale else rawLevel
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)

        val nowPlugged = plugged != 0
        // Close the running interval under the old state; POWER_(DIS)CONNECTED may arrive later or not at all.
        if (nowPlugged != isPlugged) accumulateScreenTimeLocked()
        isPlugged = nowPlugged
        batteryStatus = status
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || isPlugged

        val validLevel = acceptLevelLocked(level)
        if (BatteryLevelValidator.isValid(validLevel)) {
            applyLevelChangeLocked(validLevel)
            mCurrentStats.level = validLevel
        }

        mCurrentStats.status = status
        mCurrentStats.plugged = plugged
        mCurrentStats.isCharging = isCharging
        if (voltage > 0) mCurrentStats.voltage = voltage
        if (temp > 0) mCurrentStats.temp = temp
        mCurrentStats.health = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        fillStatsLocked(recordSample = false)

        checkAlarmsLocked(System.currentTimeMillis())
    }

    /** Attributes a validated level change to screen-on/off drain or charge, handles auto reset. */
    private fun applyLevelChangeLocked(validLevel: Int) {
        if (BatteryLevelValidator.isValid(lastBatteryLevel)) {
            val diff = abs(validLevel - lastBatteryLevel)
            // Large jumps happen when the service was not running (e.g. device off); they are not
            // attributable to the current screen state.
            if (diff < 30 || isCharging) {
                if (validLevel < lastBatteryLevel) {
                    val drop = lastBatteryLevel - validLevel
                    if (isScreenOn) accumDischargeOn += drop else accumDischargeOff += drop
                } else if (validLevel > lastBatteryLevel) {
                    val gain = validLevel - lastBatteryLevel
                    if (isScreenOn) accumChargeOn += gain else accumChargeOff += gain
                }
            }
        }

        if (prefs.getBoolean("battery_insight_auto_reset_level_enabled", false)) {
            val target = prefs.getInt("battery_insight_auto_reset_level", 100)
            if (validLevel >= target && BatteryLevelValidator.isValid(lastBatteryLevel) && lastBatteryLevel < target) {
                resetStatsInternal("Auto reset at level $target%")
            }
        }

        lastBatteryLevel = validLevel
        saveSessionState()
    }

    /**
     * Closes the interval since the last screen/power transition. Call it *before* changing
     * isScreenOn / isPlugged, so the interval is attributed to the state it was spent in.
     */
    private fun accumulateScreenTimeLocked() {
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val deltaRealtime = max(0L, nowRealtime - lastScreenStateChangeRealtime)
        val deltaUptime = (nowUptime - lastScreenStateChangeUptime).coerceIn(0L, deltaRealtime)
        val deltaDeepSleep = max(0L, deltaRealtime - deltaUptime)

        // Time on the charger belongs to the charging counters only; the screen-on/off, awake and
        // deep-sleep counters describe discharge and must not advance while plugged in.
        if (isPlugged) {
            if (isScreenOn) accumChargeScreenOnMs += deltaRealtime else accumChargeScreenOffMs += deltaRealtime
        } else if (isScreenOn) {
            accumScreenOnMs += deltaRealtime
        } else {
            accumScreenOffMs += deltaRealtime
            accumAwakeMs += deltaUptime
            accumDeepSleepMs += deltaDeepSleep
        }
        lastScreenStateChangeRealtime = nowRealtime
        lastScreenStateChangeUptime = nowUptime
    }

    private fun handleScreenStateChangeLocked(screenOn: Boolean) {
        accumulateScreenTimeLocked()
        isScreenOn = screenOn
        saveSessionState()
    }

    /**
     * Recomputes derived statistics.
     * @param recordSample whether this call contributes to min/max/avg current statistics; only the
     *        monitor loop records samples so UI polling and broadcasts don't skew them.
     */
    private fun fillStatsLocked(recordSample: Boolean) {
        if (!BatteryLevelValidator.isValid(mCurrentStats.level)) {
            val resolved = acceptLevelLocked(-1)
            mCurrentStats.level = when {
                BatteryLevelValidator.isValid(resolved) -> resolved
                BatteryLevelValidator.isValid(lastBatteryLevel) -> lastBatteryLevel
                else -> 100
            }
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()

        val curDeltaRt = max(0L, nowRealtime - lastScreenStateChangeRealtime)
        val rawDeltaUp = max(0L, nowUptime - lastScreenStateChangeUptime)
        val curDeltaUp = rawDeltaUp.coerceIn(0L, curDeltaRt)
        val curDeltaDs = max(0L, curDeltaRt - curDeltaUp)

        // Same split as accumulateScreenTimeLocked: the open interval goes to one set of counters.
        val discharging = !isPlugged
        val curScreenOn = accumScreenOnMs + if (discharging && isScreenOn) curDeltaRt else 0L
        val curScreenOff = accumScreenOffMs + if (discharging && !isScreenOn) curDeltaRt else 0L
        val curAwake = accumAwakeMs + if (discharging && !isScreenOn) curDeltaUp else 0L
        val curDeepSleep = accumDeepSleepMs + if (discharging && !isScreenOn) curDeltaDs else 0L

        val chargingNow = isCharging || isPlugged
        val curChargeScreenOn = accumChargeScreenOnMs + if (isPlugged && isScreenOn) curDeltaRt else 0L
        val curChargeScreenOff = accumChargeScreenOffMs + if (isPlugged && !isScreenOn) curDeltaRt else 0L

        mCurrentStats.screenOnTime = curScreenOn
        mCurrentStats.screenOffTime = curScreenOff
        mCurrentStats.awakeTime = curAwake
        mCurrentStats.deepSleepTime = curDeepSleep
        mCurrentStats.batteryDrainScreenOn = accumDischargeOn
        mCurrentStats.batteryDrainScreenOff = accumDischargeOff
        mCurrentStats.chargeScreenOnTime = curChargeScreenOn
        mCurrentStats.chargeScreenOffTime = curChargeScreenOff
        mCurrentStats.batteryChargeScreenOn = accumChargeOn
        mCurrentStats.batteryChargeScreenOff = accumChargeOff

        val hoursOn = curScreenOn / 3600000f
        val hoursOff = curScreenOff / 3600000f
        mCurrentStats.activeDrainRate = if (hoursOn >= 0.016f && accumDischargeOn > 0) accumDischargeOn / hoursOn else 0f
        mCurrentStats.idleDrainRate = if (hoursOff >= 0.016f && accumDischargeOff > 0) accumDischargeOff / hoursOff else 0f

        val totalChargeGain = accumChargeOn + accumChargeOff
        val chargeHoursTotal = (curChargeScreenOn + curChargeScreenOff) / 3600000f
        if (chargeHoursTotal >= 0.016f && totalChargeGain > 0) {
            mCurrentStats.chargeRate = totalChargeGain / chargeHoursTotal
        } else if (chargingNow) {
            mCurrentStats.chargeRate = 0f
        }

        // 1. Hardware Fuelgauge Current: Prioritize verified sysfs hardware reading
        val curPath = verifiedCurrentNowPath
        val sysfsRaw = if (curPath != null) readSysfsInt(curPath) else 0
        val rawCurrentMa = if (sysfsRaw != 0) {
            currentNormalizer.toMilliAmps(sysfsRaw, CurrentNormalizer.Source.SYSFS)
        } else {
            val bmRaw = try { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Exception) { 0 }
            // Integer.MIN_VALUE means "not supported".
            if (bmRaw == Int.MIN_VALUE) 0 else currentNormalizer.toMilliAmps(bmRaw, CurrentNormalizer.Source.BATTERY_MANAGER)
        }
        val currentNowMa = currentNormalizer.signed(
            rawMa = rawCurrentMa,
            plugged = chargingNow,
            discharging = batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING,
            charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING,
            level = mCurrentStats.level,
        )
        mCurrentStats.currentNow = currentNowMa

        if (recordSample && currentNowMa != 0) {
            if (currentNowMa < minCurrent) minCurrent = currentNowMa
            if (currentNowMa > maxCurrent) maxCurrent = currentNowMa
            totalCurrentSum += currentNowMa
            sampleCount++
        }
        mCurrentStats.minCurrent = if (minCurrent == Int.MAX_VALUE) 0 else minCurrent
        mCurrentStats.maxCurrent = if (maxCurrent == Int.MIN_VALUE) 0 else maxCurrent
        mCurrentStats.avgCurrent = if (sampleCount > 0) (totalCurrentSum / sampleCount).toInt() else currentNowMa

        if (recordSample && chargingNow && currentNowMa > 0) {
            chargeCurrentSum += currentNowMa
            chargeSampleCount++
        }
        mCurrentStats.chargeCurrentAvg = if (chargeSampleCount > 0) (chargeCurrentSum / chargeSampleCount).toInt() else if (chargingNow) abs(currentNowMa) else 0

        mCurrentStats.powerWatts = (abs(currentNowMa) * mCurrentStats.voltage) / 1000000f

        // 2. Hardware Battery Capacity & Health: Use cached static properties, refresh periodically
        if (nowRealtime - lastStaticStatsCheckRealtime > 300000L || cachedDesignCapacity <= 0 || cachedChargeFull <= 0) {
            if (nowRealtime - lastStaticStatsCheckRealtime > 60000L || lastStaticStatsCheckRealtime == 0L) {
                refreshStaticBatteryInfo()
            }
        }

        val capDesign = cachedDesignCapacity
        var capFull = cachedChargeFull
        val cycles = cachedCycleCount
        mCurrentStats.cycleCount = cycles

        // Fallback: Hardware Charge Counter for current full capacity estimation
        if (capFull <= 0) {
            val ccUah = try { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) } catch (_: Exception) { 0 }
            if (ccUah > 0) {
                val ccMah = if (ccUah > 25000) ccUah / 1000 else ccUah
                if (mCurrentStats.level in 95..100) {
                    capFull = ccMah
                } else if (mCurrentStats.level in 1..94) {
                    val computed = ((ccMah.toFloat() / mCurrentStats.level) * 100f).toInt()
                    if (computed in 800..25000) capFull = computed
                }
            }
        }

        if (capDesign > 0) {
            mCurrentStats.totalCapacity = capDesign
            mCurrentStats.currentCapacity = if (capFull > 0) capFull else capDesign
            mCurrentStats.capacityHealth = (mCurrentStats.currentCapacity.toFloat() / capDesign) * 100f
            val cycleHealth = if (cycles > 0) max(0f, 100f - ((cycles / 800f) * 20f)) else 100f
            mCurrentStats.cycleHealth = cycleHealth
            mCurrentStats.healthPercent = if (cycles > 0) ((mCurrentStats.capacityHealth * 0.7f) + (cycleHealth * 0.3f)) else mCurrentStats.capacityHealth
        } else {
            val fallbackCap = if (capFull > 0) capFull else 5000
            mCurrentStats.totalCapacity = fallbackCap
            mCurrentStats.currentCapacity = fallbackCap
            mCurrentStats.capacityHealth = 100f
            mCurrentStats.cycleHealth = 100f
            mCurrentStats.healthPercent = 100f
        }
    }

    private fun snapshotStats(): BatteryInsightStats = synchronized(mCurrentStats) { mCurrentStats.copy() }

    private fun startMonitorLoop() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch(stateDispatcher) {
            while (isActive) {
                val now = System.currentTimeMillis()

                try {
                    synchronized(mCurrentStats) {
                        fillStatsLocked(recordSample = true)

                        if (now - lastFlowSampleTime >= FLOW_SAMPLE_INTERVAL_MS) {
                            val sample = BatteryInsightFlowSample(
                                now,
                                mCurrentStats.currentNow,
                                mCurrentStats.isCharging,
                                mCurrentStats.level,
                                mCurrentStats.voltage,
                                mCurrentStats.temp
                            )
                            mFlowSamples.add(sample)
                            while (mFlowSamples.size > MAX_FLOW_SAMPLES) mFlowSamples.removeAt(0)
                            lastFlowSampleTime = now

                            updateHistoryBucketLocked(now)
                        }

                        checkAlarmsLocked(now)
                    }

                    if (mCurrentStats.isNotificationEnabled) {
                        updateNotification()
                    }

                    if (now - lastSessionSaveTime >= SESSION_SAVE_INTERVAL_MS) {
                        lastSessionSaveTime = now
                        synchronized(mCurrentStats) { saveSessionState() }
                        saveArchives(includeLastSessions = false)
                    }

                    // Periodic re-assertion of root immortality and watchdog keep-alive
                    if (ShellUtils.isRootAvailable()) {
                        if (now - lastImmortalityTime >= IMMORTALITY_INTERVAL_MS) {
                            lastImmortalityTime = now
                            lastOomScoreTime = now
                            applyRootImmortality()
                        } else if (now - lastOomScoreTime >= OOM_REASSERT_INTERVAL_MS) {
                            lastOomScoreTime = now
                            ShellUtils.execRoot("echo -1000 > /proc/${android.os.Process.myPid()}/oom_score_adj 2>/dev/null")
                        }
                    }

                    if (now - lastWatchdogTime >= WATCHDOG_INTERVAL_MS) {
                        lastWatchdogTime = now
                        scheduleWatchdog()
                        checkBackgroundUpdates(0L)
                        // PowerKeeper may regenerate its exemption list; re-check periodically.
                        applyRootKeepAlive()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitor loop", e)
                }

                val interval = prefs.getInt("battery_insight_monitor_interval", DEFAULT_MONITOR_INTERVAL_MS).coerceAtLeast(1000).toLong()
                delay(interval)
            }
        }
    }

    private fun updateHistoryBucketLocked(now: Long) {
        val local = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val epochDay = local.toLocalDate().toEpochDay().toInt()
        val hour = local.hour

        val totalDrain = mCurrentStats.batteryDrainScreenOn + mCurrentStats.batteryDrainScreenOff
        val totalSot = mCurrentStats.screenOnTime

        var bucket = mHistoryBuckets.lastOrNull()
        if (bucket == null || bucket.epochDay != epochDay || bucket.hour != hour) {
            bucket = BatteryInsightHistoryBucket(epochDay, hour)
            bucketStartDrain = totalDrain
            bucketStartScreenOnMs = totalSot
            bucketMinCurrent = Int.MAX_VALUE
            bucketMaxCurrent = Int.MIN_VALUE
            bucketCurrentSum = 0L
            bucketSampleCount = 0
            mHistoryBuckets.add(bucket)
            while (mHistoryBuckets.size > MAX_HISTORY_BUCKETS) mHistoryBuckets.removeAt(0)
        }

        val current = mCurrentStats.currentNow
        if (current != 0) {
            if (current < bucketMinCurrent) bucketMinCurrent = current
            if (current > bucketMaxCurrent) bucketMaxCurrent = current
            bucketCurrentSum += current
            bucketSampleCount++
        }

        bucket.screenOnMs = totalSot
        bucket.drainPercent = totalDrain
        bucket.hourlyDrainPercent = (totalDrain - bucketStartDrain).coerceAtLeast(0)
        bucket.hourlyScreenOnMs = (totalSot - bucketStartScreenOnMs).coerceAtLeast(0L)
        if (bucketMinCurrent != Int.MAX_VALUE) bucket.minCurrent = bucketMinCurrent
        if (bucketMaxCurrent != Int.MIN_VALUE) bucket.maxCurrent = bucketMaxCurrent
        if (bucketSampleCount > 0) {
            bucket.avgCurrent = (bucketCurrentSum / bucketSampleCount).toInt()
        } else if (bucket.avgCurrent == 0) {
            bucket.avgCurrent = current
        }
        bucket.temp = mCurrentStats.temp
    }

    // region App usage

    private fun maybeRefreshAppUsages() {
        // Throttle even while empty: if parsing yields nothing on a device, retrying on every UI
        // poll would run dumpsys back to back.
        val minGap = if (mAppUsages.isEmpty()) APP_USAGE_RETRY_MS else APP_USAGE_REFRESH_MS
        val stale = System.currentTimeMillis() - lastAppUsageFetchMs >= minGap
        if (stale && appUsageFetchInFlight.compareAndSet(false, true)) {
            scope.launch {
                try {
                    fetchAppUsages()
                } finally {
                    lastAppUsageFetchMs = System.currentTimeMillis()
                    appUsageFetchInFlight.set(false)
                }
            }
        }
    }

    private fun subsystemLabel(key: String): Pair<Int, String> = when (key) {
        "screen" -> -1 to getString(R.string.battery_insight_subsystem_screen)
        "cellular" -> -2 to getString(R.string.battery_insight_subsystem_cellular)
        "wifi" -> -3 to "Wi-Fi"
        "bluetooth" -> -4 to "Bluetooth"
        "idle" -> -5 to getString(R.string.battery_insight_subsystem_idle)
        "camera" -> -6 to getString(R.string.battery_insight_subsystem_camera)
        else -> -7 to getString(R.string.battery_insight_subsystem_audio)
    }

    private suspend fun fetchAppUsages(): Unit = withContext(Dispatchers.IO) {
        try {
            val pm = packageManager
            val appMap = cachedAppMap
            if (appMap.isEmpty()) {
                try {
                    val installed = pm.getInstalledApplications(0)
                    for (info in installed) {
                        val label = try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { info.packageName }
                        appMap[info.uid] = Pair(info.packageName, label)
                    }
                } catch (_: Exception) {}

                // Predefined system services
                appMap[0] = Pair("kernel", getString(R.string.battery_insight_process_kernel))
                appMap[1000] = Pair("android", getString(R.string.battery_insight_process_android))
                appMap[1001] = Pair("com.android.phone", getString(R.string.battery_insight_process_phone))
                appMap[1073] = Pair("com.android.phone", getString(R.string.battery_insight_process_phone))
                appMap[1002] = Pair("com.android.bluetooth", "Bluetooth")
                appMap[1041] = Pair("audioserver", getString(R.string.battery_insight_process_audio))
                appMap[1040] = Pair("cameraserver", getString(R.string.battery_insight_process_camera))
                appMap[1013] = Pair("mediaserver", getString(R.string.battery_insight_process_media))
                appMap[1046] = Pair("mediaextractor", getString(R.string.battery_insight_process_mediaextractor))
                appMap[1047] = Pair("mediacodec", getString(R.string.battery_insight_process_mediacodec))
                appMap[9999] = Pair("nobody", getString(R.string.battery_insight_process_unknown))
            }

            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            val fgTimeMap = mutableMapOf<String, Long>()
            try {
                val now = System.currentTimeMillis()
                val startTime = (now - 86400000L).coerceAtLeast(0L)
                val statsList = usm?.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, now)
                if (statsList != null) {
                    for (us in statsList) {
                        if (us.totalTimeInForeground > 0) {
                            fgTimeMap[us.packageName] = (fgTimeMap[us.packageName] ?: 0L) + us.totalTimeInForeground
                        }
                    }
                }
            } catch (_: Exception) {}

            // Filter and cap in the shell: the full batterystats dump can be tens of MB, and falling
            // back to it (when the header differs) exhausted memory and crashed the process.
            val filter = "grep -i -E -A 400 'Estimated (power|battery) use' | head -n $DUMPSYS_MAX_LINES"
            var lines = ShellUtils.exec("dumpsys batterystats --charged 2>/dev/null | $filter")
            if (lines.none { it.contains("Estimated", ignoreCase = true) }) {
                lines = ShellUtils.exec("dumpsys batterystats 2>/dev/null | $filter")
            }

            val totalCapacity = synchronized(mCurrentStats) { mCurrentStats.totalCapacity }
            val screenOnTime = synchronized(mCurrentStats) { mCurrentStats.screenOnTime }
            val screenOffTime = synchronized(mCurrentStats) { mCurrentStats.screenOffTime }
            val parsed = BatteryStatsParser.parse(lines, totalCapacity.toDouble().coerceAtLeast(3000.0))
            val resultMap = linkedMapOf<String, BatteryInsightAppUsage>()

            for (sub in parsed.subsystems) {
                val existing = resultMap[sub.key]
                if (existing != null) {
                    existing.consumedPowerMah = maxOf(existing.consumedPowerMah, sub.powerMah)
                    if (sub.durationMs > 0) existing.foregroundTimeMs = maxOf(existing.foregroundTimeMs, sub.durationMs)
                } else {
                    val (uid, label) = subsystemLabel(sub.key)
                    resultMap[sub.key] = BatteryInsightAppUsage().apply {
                        this.uid = uid
                        this.packageName = sub.key
                        this.appLabel = label
                        this.consumedPowerMah = sub.powerMah
                        this.foregroundTimeMs = if (sub.key == "screen" && sub.durationMs <= 0) screenOnTime else sub.durationMs
                        this.backgroundTimeMs = if (sub.key == "idle" && sub.durationMs <= 0) screenOffTime else 0L
                    }
                }
            }

            for (entry in parsed.uids) {
                val realUid = entry.uid
                var (pkgName, label) = when (realUid) {
                    0 -> Pair("kernel", getString(R.string.battery_insight_process_kernel))
                    1000 -> Pair("android", getString(R.string.battery_insight_process_android))
                    1001, 1073 -> Pair("com.android.phone", getString(R.string.battery_insight_process_phone))
                    1041 -> Pair("audioserver", getString(R.string.battery_insight_process_audio))
                    1047 -> Pair("cameraserver", getString(R.string.battery_insight_process_camera))
                    1036 -> Pair("webview", "Android WebView")
                    else -> appMap[realUid] ?: Pair("", "")
                }

                if (pkgName.isEmpty()) {
                    val pkgs = try { pm.getPackagesForUid(realUid) } catch (_: Exception) { null }
                    pkgName = pkgs?.firstOrNull() ?: ""
                    if (pkgName.isEmpty()) {
                        pkgName = "uid.$realUid"
                        label = "UID $realUid"
                    } else {
                        label = try {
                            val ai = pm.getApplicationInfo(pkgName, 0)
                            pm.getApplicationLabel(ai).toString()
                        } catch (_: Exception) {
                            pkgName
                        }
                    }
                    appMap[realUid] = Pair(pkgName, label)
                }

                val fgMs = if (entry.foregroundMs > 0L) entry.foregroundMs else fgTimeMap[pkgName] ?: 0L
                val appKey = if (pkgName.isNotEmpty() && !pkgName.startsWith("uid.")) pkgName else "uid_$realUid"
                val existing = resultMap[appKey]
                if (existing != null) {
                    existing.consumedPowerMah = maxOf(existing.consumedPowerMah, entry.powerMah)
                    if (fgMs > 0) existing.foregroundTimeMs = maxOf(existing.foregroundTimeMs, fgMs)
                } else {
                    resultMap[appKey] = BatteryInsightAppUsage().apply {
                        this.uid = realUid
                        this.packageName = pkgName
                        this.appLabel = label
                        this.consumedPowerMah = entry.powerMah
                        this.foregroundTimeMs = fgMs
                        this.backgroundTimeMs = 0L
                    }
                }
            }

            val result = resultMap.values.filter { it.consumedPowerMah > 0.05 }.sortedByDescending { it.consumedPowerMah }.toMutableList()

            // Fallback to UsageStats if no dumpsys data is available
            if (result.isEmpty() && fgTimeMap.isNotEmpty()) {
                val totalFgTime = fgTimeMap.values.sum().coerceAtLeast(1L)
                val drainOn = synchronized(mCurrentStats) { mCurrentStats.batteryDrainScreenOn }
                val estimatedTotalDrain = (drainOn.toDouble() * (totalCapacity / 100.0)).coerceAtLeast(10.0)

                for ((pkg, fgMs) in fgTimeMap) {
                    if (fgMs < 10000L) continue
                    val (realUid, label) = try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        Pair(ai.uid, pm.getApplicationLabel(ai).toString())
                    } catch (_: Exception) {
                        Pair(10000, pkg)
                    }
                    val estMah = (fgMs.toDouble() / totalFgTime.toDouble()) * estimatedTotalDrain
                    result.add(BatteryInsightAppUsage().apply {
                        this.uid = realUid
                        this.packageName = pkg
                        this.appLabel = label
                        this.consumedPowerMah = estMah
                        this.foregroundTimeMs = fgMs
                        this.backgroundTimeMs = 0L
                    })
                }
            }

            if (result.isNotEmpty()) {
                result.sortByDescending { it.consumedPowerMah }
                synchronized(mAppUsages) {
                    mAppUsages.clear()
                    mAppUsages.addAll(result)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "App usage refresh failed", e)
        }
    }

    // endregion

    // region Alarms

    private fun checkAlarmsLocked(now: Long) {
        val s = mCurrentStats
        val level = s.level
        if (!BatteryLevelValidator.isValid(level)) return
        val plugged = isPlugged || isCharging
        val frequency = s.alarmFrequency

        // 1. Zero-current full charge: only at a real 100% with the current sustained at ~0 mA.
        // OEMs may report BATTERY_STATUS_FULL below 100% (thermal pause, charge limit); ignored.
        if (!(s.isZeroCurrentAlarmEnabled && plugged)) {
            zeroCurrentSinceMs = 0L
            zeroCurrentAlarm.reset()
        } else {
            val zeroNow = level >= 100 && abs(s.currentNow) <= ZERO_CURRENT_MAX_MA
            if (!zeroNow) {
                zeroCurrentSinceMs = 0L
            } else {
                if (zeroCurrentSinceMs == 0L) zeroCurrentSinceMs = now
                if (now - zeroCurrentSinceMs >= ZERO_CURRENT_SUSTAIN_MS &&
                    zeroCurrentAlarm.shouldFire(true, level, now, frequency, AlarmTracker.Direction.NONE)
                ) {
                    triggerAlarm(
                        getString(R.string.battery_insight_alarm_zero_current_title, level),
                        getString(R.string.battery_insight_alarm_zero_current_desc)
                    )
                }
            }
        }

        // 2. Full charge (independent of the threshold alarm switch, as shown in the settings UI).
        if (!(s.isFullChargeAlarmEnabled && plugged)) {
            fullAlarm.reset()
        } else if (level >= 100 &&
            fullAlarm.shouldFire(true, level, now, frequency, AlarmTracker.Direction.NONE)
        ) {
            triggerAlarm(
                getString(R.string.battery_insight_alarm_full_title),
                getString(R.string.battery_insight_alarm_full_desc)
            )
        }

        // 3. Threshold alarms
        if (!s.isBatteryAlarmEnabled) {
            lowAlarm.reset()
            highAlarm.reset()
            return
        }
        if (lowAlarm.shouldFire(!plugged && level <= s.batteryLowThreshold, level, now, frequency, AlarmTracker.Direction.FALLING)) {
            triggerAlarm(
                getString(R.string.battery_insight_alarm_low_title, level),
                getString(R.string.battery_insight_alarm_low_desc)
            )
        }
        if (highAlarm.shouldFire(plugged && level >= s.batteryHighThreshold, level, now, frequency, AlarmTracker.Direction.RISING)) {
            // At 100% the full-charge alarm (if enabled) is the more specific message.
            if (!(level >= 100 && s.isFullChargeAlarmEnabled)) {
                triggerAlarm(
                    getString(R.string.battery_insight_alarm_limit_title, level),
                    getString(R.string.battery_insight_alarm_limit_desc)
                )
            }
        }
    }

    private fun triggerAlarm(title: String, text: String) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        if (mCurrentStats.isBatteryAlarmVibrate && ringerMode != AudioManager.RINGER_MODE_SILENT) {
            try {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 200, 350), -1))
            } catch (_: Exception) {}
        }
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val soundUriStr = mCurrentStats.batteryAlarmSound
            val soundUri = if (!soundUriStr.isNullOrEmpty()) Uri.parse(soundUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            try {
                activeRingtone?.stop()
                activeRingtone = RingtoneManager.getRingtone(this, soundUri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            } catch (_: Exception) {}
        }

        val notif = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_insight_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notifManager.notify(ALARM_NOTIF_ID, notif)
    }

    // endregion

    fun resetStatsInternal(reason: String) {
        Log.i(TAG, "Resetting statistics: $reason")
        synchronized(mCurrentStats) {
            sessionStartRealtime = SystemClock.elapsedRealtime()
            sessionStartUptime = SystemClock.uptimeMillis()
            serviceSessionStartTime = System.currentTimeMillis()
            lastScreenStateChangeRealtime = sessionStartRealtime
            lastScreenStateChangeUptime = sessionStartUptime

            accumScreenOnMs = 0L
            accumScreenOffMs = 0L
            accumAwakeMs = 0L
            accumDeepSleepMs = 0L
            accumDischargeOn = 0
            accumDischargeOff = 0

            accumChargeOn = 0
            accumChargeOff = 0
            accumChargeScreenOnMs = 0L
            accumChargeScreenOffMs = 0L
            chargeCurrentSum = 0L
            chargeSampleCount = 0
            zeroCurrentSinceMs = 0L

            minCurrent = Int.MAX_VALUE
            maxCurrent = Int.MIN_VALUE
            totalCurrentSum = 0L
            sampleCount = 0

            bucketStartDrain = 0
            bucketStartScreenOnMs = 0L
            bucketMinCurrent = Int.MAX_VALUE
            bucketMaxCurrent = Int.MIN_VALUE
            bucketCurrentSum = 0L
            bucketSampleCount = 0

            mFlowSamples.clear()
            mHistoryBuckets.clear()
            mAppUsages.clear()
            lastAppUsageFetchMs = 0L

            saveSessionState()
        }
        // Persist the cleared buffers, otherwise a restart would restore the pre-reset data.
        saveArchives(includeLastSessions = false)
    }

    // region Notification

    /**
     * Notification-specific duration format:
     * - If 0: "0 dk" / "0m"
     * - If in 1..59999L: "< 1 dk" / "< 1m"
     * - If < 60 minutes: "X dk"
     * - If >= 60 minutes: "X sa Y dk"
     * - NO seconds are ever displayed in the notification.
     */
    private fun formatDurationNotif(ms: Long): String {
        if (ms <= 0L) {
            return getString(R.string.battery_insight_duration_m, 0)
        }
        val totalMin = ms / 1000 / 60
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> getString(R.string.battery_insight_duration_h_m, h, m)
            m > 0 -> getString(R.string.battery_insight_duration_m, m)
            else -> getString(R.string.battery_insight_duration_less_than_minute)
        }
    }

    private class NotificationContent(
        val title: String,
        val summary: String,
        val body: String,
        val iconStyle: Int,
        val iconKey: String,
        val stats: BatteryInsightStats,
    )

    private fun buildNotificationContent(s: BatteryInsightStats): NotificationContent {
        val isChargingState = s.isCharging || isPlugged
        val mA = abs(s.currentNow)
        val currentStr = when {
            mA == 0 -> "0 mA"
            s.currentNow > 0 -> "+$mA mA"
            else -> "-$mA mA"
        }
        val statusStr = if (isChargingState) {
            if (s.level >= 100) getString(R.string.battery_insight_notif_full) else getString(R.string.battery_insight_notif_charging)
        } else {
            getString(R.string.battery_insight_notif_discharging)
        }
        val tempStr = "%.1f".format(s.temp / 10f)
        val powerWattsStr = "%.2f".format(s.powerWatts)

        val title = "${s.level}% • $statusStr • $currentStr • ${tempStr}°C"
        val summary = "${s.level}% • $currentStr • $powerWattsStr W"

        val totalScreenOff = s.screenOffTime
        val hasSignificantScreenOff = totalScreenOff >= 60000L
        val awakePct = if (hasSignificantScreenOff && s.awakeTime > 0L) {
            ((s.awakeTime.toFloat() / totalScreenOff.toFloat()) * 100f).coerceIn(0f, 100f)
        } else 0f
        val deepSleepPct = if (hasSignificantScreenOff && s.deepSleepTime > 0L) {
            ((s.deepSleepTime.toFloat() / totalScreenOff.toFloat()) * 100f).coerceIn(0f, 100f)
        } else 0f

        val activeRateStr = if (s.screenOnTime >= 60000L && s.activeDrainRate > 0f) "%.1f".format(s.activeDrainRate) else "0.0"
        val idleRateStr = if (s.screenOffTime >= 60000L && s.idleDrainRate > 0f) "%.1f".format(s.idleDrainRate) else "0.0"

        val body = buildString {
            append(getString(R.string.battery_insight_notif_active, activeRateStr))
            append(" • ")
            append(getString(R.string.battery_insight_notif_idle, idleRateStr))
            append("\n")
            append(getString(R.string.battery_insight_notif_screen_on, formatDurationNotif(s.screenOnTime), s.batteryDrainScreenOn.toString()))
            append("\n")
            append(getString(R.string.battery_insight_notif_screen_off, formatDurationNotif(s.screenOffTime), s.batteryDrainScreenOff.toString()))
            append("\n")
            append(getString(R.string.battery_insight_notif_awake, formatDurationNotif(s.awakeTime), "%.1f".format(awakePct)))
            append("\n")
            append(getString(R.string.battery_insight_notif_deep_sleep, formatDurationNotif(s.deepSleepTime), "%.1f".format(deepSleepPct)))
        }

        val iconStyle = prefs.getInt(PREF_NOTIF_ICON_STYLE, StatusIconRenderer.STYLE_BATTERY)
        val iconKey = "$iconStyle|" + statusIconRenderer.labelFor(iconStyle, s).joinToString("|") +
            if (iconStyle == StatusIconRenderer.STYLE_CIRCLE_PERCENT) "|${s.level}" else ""
        return NotificationContent(title, summary, body, iconStyle, iconKey, s)
    }

    private fun contentPendingIntent(): PendingIntent {
        val launch = IconUtils.launchIntent(this)
        val component = launch.component?.flattenToString()
        val cached = cachedContentIntent
        if (cached != null && component == cachedContentIntentComponent) return cached
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).also {
            cachedContentIntent = it
            cachedContentIntentComponent = component
        }
    }

    private fun createNotification(content: NotificationContent): Notification {
        val builder = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
        val dynamicIcon: IconCompat? = statusIconRenderer.render(content.iconStyle, content.stats)
        if (dynamicIcon != null) builder.setSmallIcon(dynamicIcon) else builder.setSmallIcon(R.drawable.ic_battery_insight_notify)
        return builder
            .setContentTitle(content.title)
            .setContentText(content.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setContentIntent(contentPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setSilent(true)
            .setSound(null)
            .setVibrate(null)
            .build()
    }

    private fun forceNotificationUpdate() {
        scope.launch(stateDispatcher) {
            lastNotifTitle = ""
            updateNotification()
        }
    }

    private fun updateNotification() {
        if (!prefs.getBoolean("battery_insight_enabled", true) || !prefs.getBoolean("battery_insight_notif_enabled", true)) {
            return
        }
        try {
            val content = buildNotificationContent(snapshotStats())
            // Real-time updates: update immediately when any value changes (mA, watts, level, temp, icon)
            val changed = content.title != lastNotifTitle || content.summary != lastNotifSummary ||
                content.body != lastNotifBody || content.iconKey != lastNotifIconKey
            if (!changed) {
                return // Skip redundant IPC if absolutely nothing changed
            }

            lastNotifTitle = content.title
            lastNotifSummary = content.summary
            lastNotifBody = content.body
            lastNotifIconKey = content.iconKey
            notifManager.notify(NOTIF_ID, createNotification(content))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    private fun createNotificationChannels() {
        try {
            // Delete old channels so Android doesn't retain old status-bar icon / sound settings
            try {
                notifManager.deleteNotificationChannel("battery_insight_channel")
                notifManager.deleteNotificationChannel("battery_insight_monitor_silent_v2")
                notifManager.deleteNotificationChannel("battery_insight_monitor_silent_v3")
                notifManager.deleteNotificationChannel("battery_insight_alarm_channel")
            } catch (_: Exception) {}

            val monitorChannel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.battery_insight_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.battery_insight_notif_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                getString(R.string.battery_insight_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.battery_insight_alarm_channel_desc)
                // The alarm sound and vibration pattern chosen in the app are played by triggerAlarm().
                setSound(null, null)
                enableVibration(false)
            }

            notifManager.createNotificationChannel(monitorChannel)
            notifManager.createNotificationChannel(alarmChannel)
        } catch (_: Exception) {}
    }

    // endregion

    override fun onDestroy() {
        instance = null
        monitorJob?.cancel()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        try {
            val snapshot = synchronized(mCurrentStats) {
                saveSessionState()
                ArchiveSnapshot.take(this, includeLast = false)
            }
            snapshot.writeTo(storage)
        } catch (_: Exception) {}
        try {
            activeRingtone?.stop()
        } catch (_: Exception) {}

        scope.cancel()
        stateDispatcher.close()

        // If destroyed unexpectedly, arm revive alarm
        if (prefs.getBoolean("battery_insight_enabled", true)) {
            try {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L, watchdogPendingIntent())
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
