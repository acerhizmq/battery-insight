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
import com.acer.batteryinsight.model.BatteryInsightAppUsage
import com.acer.batteryinsight.model.BatteryInsightFlowSample
import com.acer.batteryinsight.model.BatteryInsightHistoryBucket
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.service.IBatteryInsightService
import com.acer.batteryinsight.updater.UpdateManager
import com.acer.batteryinsight.MainActivity
import com.acer.batteryinsight.R
import com.acer.batteryinsight.utils.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max

class BatteryInsightService : Service() {

    companion object {
        private const val TAG = "BatteryInsightService"
        private const val NOTIF_CHANNEL_ID = "battery_insight_monitor_v4"
        private const val ALARM_CHANNEL_ID = "battery_insight_alarm_channel"
        private const val NOTIF_ID = 1001
        private const val ALARM_NOTIF_ID = 1002

        private const val FLOW_SAMPLE_INTERVAL_MS = 2000L
        private const val MAX_FLOW_SAMPLES = 1440
        private const val MAX_HISTORY_BUCKETS = 72

        @Volatile
        var instance: IBatteryInsightService? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var prefs: SharedPreferences
    private lateinit var notifManager: NotificationManager
    private lateinit var batteryManager: BatteryManager
    private var vibrator: Vibrator? = null

    private val mCurrentStats = BatteryInsightStats()
    private val mFlowSamples = CopyOnWriteArrayList<BatteryInsightFlowSample>()
    private val mHistoryBuckets = CopyOnWriteArrayList<BatteryInsightHistoryBucket>()
    private val mAppUsages = CopyOnWriteArrayList<BatteryInsightAppUsage>()

    private val mLastChargeFlowSamples = CopyOnWriteArrayList<BatteryInsightFlowSample>()
    private val mLastChargeHistoryBuckets = CopyOnWriteArrayList<BatteryInsightHistoryBucket>()
    private val mLastDischargeFlowSamples = CopyOnWriteArrayList<BatteryInsightFlowSample>()
    private val mLastDischargeHistoryBuckets = CopyOnWriteArrayList<BatteryInsightHistoryBucket>()

    private var batteryBasePath: String = "/sys/class/power_supply/battery"
    private var verifiedCurrentNowPath: String? = null
    private var cycleCountPath: String? = null
    private var cachedDesignCapacity: Int = 0
    private var cachedChargeFull: Int = 0
    private var cachedCycleCount: Int = 0
    private var lastStaticStatsCheckRealtime: Long = 0L
    private val cachedAppMap = mutableMapOf<Int, Pair<String, String>>()

    private var isScreenOn = true
    private var isCharging = false
    private var isPlugged = false

    private var lastBatteryLevel = -1
    private var lastFlowSampleTime = 0L
    private var lastAlarmNotifiedLevel = -1
    private var lastAlarmNotifiedTime = 0L

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

    private var zeroCurrentSustainCount = 0
    private var bucketStartDrain = 0
    private var bucketStartScreenOnMs = 0L

    private var lastScreenStateChangeRealtime = SystemClock.elapsedRealtime()
    private var lastScreenStateChangeUptime = SystemClock.uptimeMillis()

    private var lastSessionSaveTime = 0L
    private var lastOomScoreTime = 0L
    private var lastWatchdogTime = 0L

    // Current tracking
    private var minCurrent = Int.MAX_VALUE
    private var maxCurrent = Int.MIN_VALUE
    private var totalCurrentSum = 0L
    private var sampleCount = 0

    // Notification caching – prevent redundant IPC calls when values haven't changed
    private var lastNotifTitle = ""
    private var lastNotifSummary = ""
    private var lastNotifBody = ""

    private val contentPendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val binder = object : IBatteryInsightService.Stub() {
        override fun getBatteryState(): BatteryInsightStats = synchronized(mCurrentStats) {
            fillStatsLocked()
            mCurrentStats.copy()
        }

        override fun getCurrentFlow(minutes: Int): Array<BatteryInsightFlowSample> {
            val count = (minutes * 30).coerceAtLeast(1)
            return mFlowSamples.takeLast(count).toTypedArray()
        }

        override fun getHistory(): Array<BatteryInsightHistoryBucket> = mHistoryBuckets.toTypedArray()

        override fun getSessionFlow(sessionType: Int): Array<BatteryInsightFlowSample> {
            return when (sessionType) {
                1 -> mLastChargeFlowSamples.toTypedArray()
                2 -> mLastDischargeFlowSamples.toTypedArray()
                else -> mFlowSamples.toTypedArray()
            }
        }

        override fun getSessionHistory(sessionType: Int): Array<BatteryInsightHistoryBucket> {
            return when (sessionType) {
                1 -> mLastChargeHistoryBuckets.toTypedArray()
                2 -> mLastDischargeHistoryBuckets.toTypedArray()
                else -> mHistoryBuckets.toTypedArray()
            }
        }

        override fun getAppUsageSinceLastCharge(maxEntries: Int): Array<BatteryInsightAppUsage> {
            if (mAppUsages.isEmpty()) {
                scope.launch { fetchAppUsages() }
            }
            return mAppUsages.take(maxEntries).toTypedArray()
        }

        override fun resetStats() {
            this@BatteryInsightService.resetStatsInternal("User requested reset")
        }

        override fun isEnabled(): Boolean = prefs.getBoolean("battery_insight_enabled", true)
        override fun setEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_enabled", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isNotificationEnabled = enabled }
            if (enabled) updateNotification() else notifManager.cancel(NOTIF_ID)
        }

        override fun isNotificationEnabled(): Boolean = prefs.getBoolean("battery_insight_notif_enabled", true)
        override fun setNotificationEnabled(enabled: Boolean) {
            prefs.edit().putBoolean("battery_insight_notif_enabled", enabled).apply()
            synchronized(mCurrentStats) { mCurrentStats.isNotificationEnabled = enabled }
            if (enabled) updateNotification() else notifManager.cancel(NOTIF_ID)
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

        override fun getMonitorInterval(): Int = prefs.getInt("battery_insight_monitor_interval", 10000)
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
            synchronized(mCurrentStats) { mCurrentStats.alarmFrequency = frequency }
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
            try {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)
                    Intent.ACTION_POWER_CONNECTED -> {
                        val nowRealtime = SystemClock.elapsedRealtime()
                        val nowUptime = SystemClock.uptimeMillis()
                        val deltaRt = max(0L, nowRealtime - lastScreenStateChangeRealtime)
                        val deltaUp = (nowUptime - lastScreenStateChangeUptime).coerceIn(0L, deltaRt)
                        val deltaDs = max(0L, deltaRt - deltaUp)
                        if (isScreenOn) {
                            accumScreenOnMs += deltaRt
                        } else {
                            accumScreenOffMs += deltaRt
                            accumAwakeMs += deltaUp
                            accumDeepSleepMs += deltaDs
                        }
                        lastScreenStateChangeRealtime = nowRealtime
                        lastScreenStateChangeUptime = nowUptime

                        isPlugged = true
                        archiveSession(isChargingSession = false)
                        if (prefs.getBoolean("battery_insight_reset_on_plugged", false)) {
                            resetStatsInternal("Reset on power connected")
                        }
                        saveSessionState()
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        val nowRealtime = SystemClock.elapsedRealtime()
                        val nowUptime = SystemClock.uptimeMillis()
                        val deltaRt = max(0L, nowRealtime - lastScreenStateChangeRealtime)
                        val deltaUp = (nowUptime - lastScreenStateChangeUptime).coerceIn(0L, deltaRt)
                        val deltaDs = max(0L, deltaRt - deltaUp)
                        if (isScreenOn) {
                            accumScreenOnMs += deltaRt
                            accumChargeScreenOnMs += deltaRt
                        } else {
                            accumScreenOffMs += deltaRt
                            accumAwakeMs += deltaUp
                            accumDeepSleepMs += deltaDs
                            accumChargeScreenOffMs += deltaRt
                        }
                        lastScreenStateChangeRealtime = nowRealtime
                        lastScreenStateChangeUptime = nowUptime

                        isPlugged = false
                        zeroCurrentSustainCount = 0
                        lastZeroCurrentNotifiedTime = 0L
                        synchronized(mCurrentStats) {
                            mCurrentStats.chargeScreenOnTime = accumChargeScreenOnMs
                            mCurrentStats.chargeScreenOffTime = accumChargeScreenOffMs
                            val totalChargeGain = accumChargeOn + accumChargeOff
                            val chargeHoursTotal = (accumChargeScreenOnMs + accumChargeScreenOffMs) / 3600000f
                            if (chargeHoursTotal >= 0.016f && totalChargeGain > 0) {
                                mCurrentStats.chargeRate = totalChargeGain / chargeHoursTotal
                            }
                        }
                        archiveSession(isChargingSession = true)
                        saveSessionState()
                    }
                    Intent.ACTION_SCREEN_ON -> handleScreenStateChange(true)
                    Intent.ACTION_SCREEN_OFF -> handleScreenStateChange(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in batteryReceiver", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            prefs = getSharedPreferences("battery_insight_prefs", Context.MODE_PRIVATE)
            notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            createNotificationChannels()
            loadSettingsIntoStats()
            restoreSessionState()
            restoreSessionArchives()

            val stickyBattery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawLevel = stickyBattery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val rawScale = stickyBattery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val accuratePct = if (rawLevel >= 0 && rawScale > 0) ((rawLevel * 100) / rawScale) else -1

            val initialValidLevel = resolveValidBatteryLevel(accuratePct)
            mCurrentStats.level = initialValidLevel
            lastBatteryLevel = initialValidLevel

            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            isScreenOn = powerManager?.isInteractive ?: true
            lastScreenStateChangeRealtime = SystemClock.elapsedRealtime()
            lastScreenStateChangeUptime = SystemClock.uptimeMillis()

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

            instance = binder
            startForegroundSafely()
            scheduleWatchdog()

            // Asynchronously detect hardware paths and initialize root in background without freezing main UI thread
            scope.launch(Dispatchers.IO) {
                detectHardwarePaths()
                synchronized(mCurrentStats) { fillStatsLocked() }
                ShellUtils.initRootShell { isRoot ->
                    if (isRoot) {
                        scope.launch(Dispatchers.IO) {
                            detectHardwarePaths()
                            applyRootImmortality()
                            synchronized(mCurrentStats) { fillStatsLocked() }
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
                if (lastBatteryLevel in 1..100) {
                    putInt("last_battery_level", lastBatteryLevel)
                }
                apply()
            }
        } catch (_: Exception) {}
    }

    private fun restoreSessionState() {
        try {
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
            val savedLevel = prefs.getInt("last_battery_level", -1)
            if (savedLevel in 1..100) {
                lastBatteryLevel = savedLevel
                if (mCurrentStats.level <= 0) {
                    mCurrentStats.level = savedLevel
                }
            }
        } catch (_: Exception) {}
    }

    private fun archiveSession(isChargingSession: Boolean) {
        try {
            if (isChargingSession) {
                val chargeFlow = mFlowSamples.filter { it.isCharging }
                if (chargeFlow.isNotEmpty()) {
                    mLastChargeFlowSamples.clear()
                    mLastChargeFlowSamples.addAll(chargeFlow)
                }
                if (mHistoryBuckets.isNotEmpty()) {
                    mLastChargeHistoryBuckets.clear()
                    mLastChargeHistoryBuckets.addAll(mHistoryBuckets)
                }
            } else {
                val dischargeFlow = mFlowSamples.filter { !it.isCharging }
                if (dischargeFlow.isNotEmpty()) {
                    mLastDischargeFlowSamples.clear()
                    mLastDischargeFlowSamples.addAll(dischargeFlow)
                }
                if (mHistoryBuckets.isNotEmpty()) {
                    mLastDischargeHistoryBuckets.clear()
                    mLastDischargeHistoryBuckets.addAll(mHistoryBuckets)
                }
            }
            saveSessionArchives()
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving session", e)
        }
    }

    private fun saveSessionArchives() {
        scope.launch(Dispatchers.IO) {
            try {
                saveListToFile("active_flow.json", flowToJson(mFlowSamples))
                saveListToFile("active_buckets.json", bucketsToJson(mHistoryBuckets))
                saveListToFile("last_charge_flow.json", flowToJson(mLastChargeFlowSamples))
                saveListToFile("last_charge_buckets.json", bucketsToJson(mLastChargeHistoryBuckets))
                saveListToFile("last_discharge_flow.json", flowToJson(mLastDischargeFlowSamples))
                saveListToFile("last_discharge_buckets.json", bucketsToJson(mLastDischargeHistoryBuckets))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving session archives to disk", e)
            }
        }
    }

    private fun restoreSessionArchives() {
        try {
            val activeFlow = jsonToFlow(readStringFromFile("active_flow.json"))
            if (activeFlow.isNotEmpty() && mFlowSamples.isEmpty()) {
                mFlowSamples.addAll(activeFlow)
            }
            val activeBuckets = jsonToBuckets(readStringFromFile("active_buckets.json"))
            if (activeBuckets.isNotEmpty() && mHistoryBuckets.isEmpty()) {
                mHistoryBuckets.addAll(activeBuckets)
            }
            val lcFlow = jsonToFlow(readStringFromFile("last_charge_flow.json"))
            if (lcFlow.isNotEmpty()) {
                mLastChargeFlowSamples.clear()
                mLastChargeFlowSamples.addAll(lcFlow)
            }
            val lcBuckets = jsonToBuckets(readStringFromFile("last_charge_buckets.json"))
            if (lcBuckets.isNotEmpty()) {
                mLastChargeHistoryBuckets.clear()
                mLastChargeHistoryBuckets.addAll(lcBuckets)
            }
            val ldFlow = jsonToFlow(readStringFromFile("last_discharge_flow.json"))
            if (ldFlow.isNotEmpty()) {
                mLastDischargeFlowSamples.clear()
                mLastDischargeFlowSamples.addAll(ldFlow)
            }
            val ldBuckets = jsonToBuckets(readStringFromFile("last_discharge_buckets.json"))
            if (ldBuckets.isNotEmpty()) {
                mLastDischargeHistoryBuckets.clear()
                mLastDischargeHistoryBuckets.addAll(ldBuckets)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring session archives", e)
        }
    }

    private fun saveListToFile(filename: String, content: String) {
        try {
            val file = File(filesDir, filename)
            file.writeText(content)
        } catch (_: Exception) {}
    }

    private fun readStringFromFile(filename: String): String {
        return try {
            val file = File(filesDir, filename)
            if (file.exists() && file.canRead()) file.readText() else ""
        } catch (_: Exception) { "" }
    }

    private fun flowToJson(list: List<BatteryInsightFlowSample>): String {
        val arr = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject()
            obj.put("t", item.timestamp)
            obj.put("c", item.current)
            obj.put("ch", item.isCharging)
            obj.put("l", item.level)
            obj.put("v", item.voltage)
            obj.put("tp", item.temp)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun jsonToFlow(json: String): List<BatteryInsightFlowSample> {
        if (json.isEmpty()) return emptyList()
        val list = mutableListOf<BatteryInsightFlowSample>()
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(BatteryInsightFlowSample(
                    obj.optLong("t"),
                    obj.optInt("c"),
                    obj.optBoolean("ch"),
                    obj.optInt("l"),
                    obj.optInt("v"),
                    obj.optInt("tp")
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun bucketsToJson(list: List<BatteryInsightHistoryBucket>): String {
        val arr = org.json.JSONArray()
        for (b in list) {
            val obj = org.json.JSONObject()
            obj.put("d", b.epochDay)
            obj.put("h", b.hour)
            obj.put("sot", b.screenOnMs)
            obj.put("dr", b.drainPercent)
            obj.put("hdr", b.hourlyDrainPercent)
            obj.put("hsot", b.hourlyScreenOnMs)
            obj.put("min", b.minCurrent)
            obj.put("max", b.maxCurrent)
            obj.put("avg", b.avgCurrent)
            obj.put("tp", b.temp)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun jsonToBuckets(json: String): List<BatteryInsightHistoryBucket> {
        if (json.isEmpty()) return emptyList()
        val list = mutableListOf<BatteryInsightHistoryBucket>()
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val b = BatteryInsightHistoryBucket(obj.optInt("d"), obj.optInt("h")).apply {
                    screenOnMs = obj.optLong("sot")
                    drainPercent = obj.optInt("dr")
                    hourlyDrainPercent = obj.optInt("hdr")
                    hourlyScreenOnMs = obj.optLong("hsot")
                    minCurrent = obj.optInt("min")
                    maxCurrent = obj.optInt("max")
                    avgCurrent = obj.optInt("avg")
                    temp = obj.optInt("tp")
                }
                list.add(b)
            }
        } catch (_: Exception) {}
        return list
    }

    private fun applyRootImmortality() {
        scope.launch(Dispatchers.IO) {
            try {
                val pid = android.os.Process.myPid()
                val pkg = packageName
                val commands = StringBuilder()
                // 1. OOM score adj: Minimum -1000 (Protected from LowMemoryKiller)
                commands.append("echo -1000 > /proc/$pid/oom_score_adj 2>/dev/null; ")
                commands.append("echo -17 > /proc/$pid/oom_adj 2>/dev/null; ")
                // 2. Battery & Doze Whitelisting
                commands.append("dumpsys deviceidle whitelist +$pkg 2>/dev/null; ")
                commands.append("cmd deviceidle whitelist +$pkg 2>/dev/null; ")
                // 3. Prevent process throttling / standby freezing
                commands.append("am set-standby-bucket $pkg active 2>/dev/null; ")
                commands.append("cmd activity set-inactive $pkg false 2>/dev/null; ")
                // 4. Background and Autostart AppOps
                commands.append("cmd appops set $pkg RUN_IN_BACKGROUND allow 2>/dev/null; ")
                commands.append("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null; ")
                commands.append("cmd appops set $pkg START_FOREGROUND allow 2>/dev/null; ")
                commands.append("cmd appops set $pkg AUTO_START allow 2>/dev/null; ")
                commands.append("cmd appops set $pkg BOOT_COMPLETED allow 2>/dev/null; ")
                // 5. Chmod 644 on power_supply nodes to enable ultra-fast direct Java reads (0.01ms)
                commands.append("chmod -R 644 /sys/class/power_supply/* 2>/dev/null; ")
                commands.append("chmod 755 /sys/class/power_supply /sys/class/power_supply/* 2>/dev/null; ")
                // 6. Live SELinux policy injection for Magisk / KernelSU / APatch
                commands.append("magiskpolicy --live 'allow untrusted_app sysfs_batteryinfo file { read open getattr }' 2>/dev/null; ")
                commands.append("magiskpolicy --live 'allow untrusted_app sysfs file { read open getattr }' 2>/dev/null; ")
                commands.append("supolicy --live 'allow untrusted_app sysfs_batteryinfo file { read open getattr }' 2>/dev/null; ")
                commands.append("supolicy --live 'allow untrusted_app sysfs file { read open getattr }' 2>/dev/null; ")
                ShellUtils.exec(commands.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error applying root immortality", e)
            }
        }
    }

    private fun scheduleWatchdog() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, BootReceiver::class.java).apply {
                action = "com.acer.batteryinsight.WATCHDOG_KEEPALIVE"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                9999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = SystemClock.elapsedRealtime() + (5 * 60 * 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
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

    private fun startForegroundSafely() {
        try {
            val notif = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startForegroundSafely", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("is_reboot", false) == true) {
            if (prefs.getBoolean("battery_insight_reset_on_reboot", false)) {
                resetStatsInternal("Reset on device reboot")
            }
        }
        startForegroundSafely()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

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

            val fullCandidates = listOf(
                "$batteryBasePath/charge_full",
                "/sys/class/power_supply/bms/charge_full",
                "$batteryBasePath/charge_counter",
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

    private fun loadSettingsIntoStats() {
        synchronized(mCurrentStats) {
            mCurrentStats.isNotificationEnabled = prefs.getBoolean("battery_insight_notif_enabled", true)
            mCurrentStats.monitorInterval = prefs.getInt("battery_insight_monitor_interval", 10000)
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
    }

    private fun readSysfsCapacity(): Int {
        val candidates = listOf(
            "$batteryBasePath/capacity",
            "/sys/class/power_supply/battery/capacity",
            "/sys/class/power_supply/bms/capacity",
            "/sys/class/power_supply/sec-battery/capacity",
            "/sys/class/power_supply/qcom-battery/capacity",
            "/sys/class/power_supply/main/capacity"
        )
        for (path in candidates) {
            val cap = readSysfsInt(path)
            if (cap in 1..100) return cap
        }
        return -1
    }

    private fun resolveValidBatteryLevel(intentLevel: Int): Int {
        // 1. Intent EXTRA_LEVEL check (with spurious 0% drop filter)
        if (intentLevel in 1..100) {
            if (lastBatteryLevel in 15..100 && intentLevel <= 2 && !isCharging && !isPlugged) {
                val sysCap = readSysfsCapacity()
                if (sysCap in 1..100) return sysCap
                val bmCap = try { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (_: Exception) { -1 }
                if (bmCap in 1..100) return bmCap
            }
            return intentLevel
        }

        // 2. BatteryManager property check
        val bmCap = try { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (_: Exception) { -1 }
        if (bmCap in 1..100) return bmCap

        // 3. Direct Sysfs capacity node check
        val sysCap = readSysfsCapacity()
        if (sysCap in 1..100) return sysCap

        // 4. Last known valid level check
        if (lastBatteryLevel in 1..100) return lastBatteryLevel
        if (mCurrentStats.level in 1..100) return mCurrentStats.level

        return 100
    }

    private fun handleBatteryChanged(intent: Intent) {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val level = if (rawLevel >= 0 && scale > 0) (rawLevel * 100) / scale else rawLevel
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)

        isPlugged = plugged != 0
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || isPlugged

        val validLevel = resolveValidBatteryLevel(level)

        if (lastBatteryLevel in 1..100) {
            val diff = abs(validLevel - lastBatteryLevel)
            // Filter out impossible instant drops (e.g. >30% drop in one broadcast cycle)
            if (diff < 30 || isCharging) {
                if (validLevel < lastBatteryLevel) {
                    val drop = lastBatteryLevel - validLevel
                    if (isScreenOn) {
                        accumDischargeOn += drop
                    } else {
                        accumDischargeOff += drop
                    }
                } else if (validLevel > lastBatteryLevel) {
                    val gain = validLevel - lastBatteryLevel
                    if (isScreenOn) {
                        accumChargeOn += gain
                    } else {
                        accumChargeOff += gain
                    }
                }
            }
        }

        if (prefs.getBoolean("battery_insight_auto_reset_level_enabled", false)) {
            val target = prefs.getInt("battery_insight_auto_reset_level", 100)
            if (validLevel >= target && lastBatteryLevel in 1..100 && lastBatteryLevel < target) {
                resetStatsInternal("Auto reset at level $target%")
            }
        }

        lastBatteryLevel = validLevel
        saveSessionState()

        synchronized(mCurrentStats) {
            mCurrentStats.level = validLevel
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
                else -> "Good"
            }
            fillStatsLocked()
        }

        checkAlarms(validLevel, isCharging)
    }

    private fun handleScreenStateChange(screenOn: Boolean) {
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()

        val deltaRealtime = max(0L, nowRealtime - lastScreenStateChangeRealtime)
        val deltaUptime = (nowUptime - lastScreenStateChangeUptime).coerceIn(0L, deltaRealtime)
        val deltaDeepSleep = max(0L, deltaRealtime - deltaUptime)

        if (isScreenOn) {
            accumScreenOnMs += deltaRealtime
        } else {
            accumScreenOffMs += deltaRealtime
            accumAwakeMs += deltaUptime
            accumDeepSleepMs += deltaDeepSleep
        }

        if (isCharging || isPlugged) {
            if (isScreenOn) {
                accumChargeScreenOnMs += deltaRealtime
            } else {
                accumChargeScreenOffMs += deltaRealtime
            }
        }

        isScreenOn = screenOn
        lastScreenStateChangeRealtime = nowRealtime
        lastScreenStateChangeUptime = nowUptime
        saveSessionState()
    }

    private fun fillStatsLocked() {
        if (mCurrentStats.level <= 0) {
            mCurrentStats.level = resolveValidBatteryLevel(-1)
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()

        val curDeltaRt = max(0L, nowRealtime - lastScreenStateChangeRealtime)
        val rawDeltaUp = max(0L, nowUptime - lastScreenStateChangeUptime)
        val curDeltaUp = rawDeltaUp.coerceIn(0L, curDeltaRt)
        val curDeltaDs = max(0L, curDeltaRt - curDeltaUp)

        val curScreenOn = accumScreenOnMs + if (isScreenOn) curDeltaRt else 0L
        val curScreenOff = accumScreenOffMs + if (!isScreenOn) curDeltaRt else 0L
        val curAwake = accumAwakeMs + if (!isScreenOn) curDeltaUp else 0L
        val curDeepSleep = accumDeepSleepMs + if (!isScreenOn) curDeltaDs else 0L

        val curChargeScreenOn = if (isCharging || isPlugged) {
            accumChargeScreenOnMs + if (isScreenOn) curDeltaRt else 0L
        } else {
            accumChargeScreenOnMs
        }
        val curChargeScreenOff = if (isCharging || isPlugged) {
            accumChargeScreenOffMs + if (!isScreenOn) curDeltaRt else 0L
        } else {
            accumChargeScreenOffMs
        }

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
        } else if (isCharging || isPlugged) {
            mCurrentStats.chargeRate = 0f
        }

        // 1. Hardware Fuelgauge Current: Prioritize verified sysfs hardware reading
        val curPath = verifiedCurrentNowPath
        val curRaw = if (curPath != null) readSysfsInt(curPath) else 0
        val currentNowUa = if (curRaw != 0) {
            curRaw
        } else {
            try { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Exception) { 0 }
        }
        val rawCurrentMa = if (abs(currentNowUa) > 10000) currentNowUa / 1000 else currentNowUa
        val currentNowMa = if (isCharging || isPlugged) {
            abs(rawCurrentMa)
        } else {
            -abs(rawCurrentMa)
        }
        mCurrentStats.currentNow = currentNowMa

        if (currentNowMa != 0) {
            if (currentNowMa < minCurrent) minCurrent = currentNowMa
            if (currentNowMa > maxCurrent) maxCurrent = currentNowMa
            totalCurrentSum += currentNowMa
            sampleCount++
        }
        mCurrentStats.minCurrent = if (minCurrent == Int.MAX_VALUE) 0 else minCurrent
        mCurrentStats.maxCurrent = if (maxCurrent == Int.MIN_VALUE) 0 else maxCurrent
        mCurrentStats.avgCurrent = if (sampleCount > 0) (totalCurrentSum / sampleCount).toInt() else currentNowMa

        if ((isCharging || isPlugged) && currentNowMa > 0) {
            chargeCurrentSum += currentNowMa
            chargeSampleCount++
        }
        mCurrentStats.chargeCurrentAvg = if (chargeSampleCount > 0) (chargeCurrentSum / chargeSampleCount).toInt() else if (isCharging || isPlugged) abs(currentNowMa) else 0

        mCurrentStats.powerWatts = (abs(currentNowMa) * mCurrentStats.voltage) / 1000000f

        // 2. Hardware Battery Capacity & Health: Use cached static properties, refresh periodically
        if (nowRealtime - lastStaticStatsCheckRealtime > 300000L || cachedDesignCapacity <= 0 || cachedChargeFull <= 0) {
            refreshStaticBatteryInfo()
        }

        var capDesign = cachedDesignCapacity
        var capFull = cachedChargeFull
        var cycles = cachedCycleCount
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

    private fun readSysfsInt(path: String): Int {
        // Direct file read (fastest, 0.01ms)
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val v = file.readText().trim().toIntOrNull()
                if (v != null) return v
            }
        } catch (_: Exception) {}

        // Root / Shell fallback using LibSU active shell or sh
        try {
            val str = ShellUtils.readSysfsSync(path)
            if (!str.isNullOrEmpty()) {
                val v = str.trim().toIntOrNull()
                if (v != null) return v
            }
        } catch (_: Exception) {}

        return 0
    }

    private fun startMonitorLoop() {
        scope.launch {
            var loopCount = 0
            while (isActive) {
                val now = System.currentTimeMillis()

                try {
                    synchronized(mCurrentStats) {
                        fillStatsLocked()

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
                            if (mFlowSamples.size > MAX_FLOW_SAMPLES) mFlowSamples.removeAt(0)
                            lastFlowSampleTime = now

                            updateHistoryBucketLocked(now)
                        }
                    }

                    checkAlarms(mCurrentStats.level, mCurrentStats.isCharging)

                    if (mCurrentStats.isNotificationEnabled) {
                        updateNotification()
                    }

                    if (now - lastSessionSaveTime >= 30000L) {
                        lastSessionSaveTime = now
                        saveSessionState()
                        saveSessionArchives()
                    }

                    // Periodic re-assertion of root immortality and watchdog keep-alive
                    if (now - lastOomScoreTime >= 15000L) {
                        lastOomScoreTime = now
                        ShellUtils.exec("echo -1000 > /proc/${android.os.Process.myPid()}/oom_score_adj 2>/dev/null")
                    }

                    if (now - lastWatchdogTime >= 300000L) {
                        lastWatchdogTime = now
                        scheduleWatchdog()
                        applyRootImmortality()
                        checkBackgroundUpdates(0L)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitor loop", e)
                }

                loopCount++
                val interval = prefs.getInt("battery_insight_monitor_interval", 1000).coerceAtLeast(1000).toLong()
                delay(interval)
            }
        }
    }

    private fun updateHistoryBucketLocked(now: Long) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val epochDay = (now / 86400000L).toInt()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        val totalDrain = mCurrentStats.batteryDrainScreenOn + mCurrentStats.batteryDrainScreenOff
        val totalSot = mCurrentStats.screenOnTime

        var bucket = mHistoryBuckets.lastOrNull()
        if (bucket == null || bucket.epochDay != epochDay || bucket.hour != hour) {
            bucket = BatteryInsightHistoryBucket(epochDay, hour)
            bucketStartDrain = totalDrain
            bucketStartScreenOnMs = totalSot
            mHistoryBuckets.add(bucket)
            if (mHistoryBuckets.size > MAX_HISTORY_BUCKETS) mHistoryBuckets.removeAt(0)
        }
        bucket.screenOnMs = totalSot
        bucket.drainPercent = totalDrain
        bucket.hourlyDrainPercent = (totalDrain - bucketStartDrain).coerceAtLeast(0)
        bucket.hourlyScreenOnMs = (totalSot - bucketStartScreenOnMs).coerceAtLeast(0L)
        if (mCurrentStats.minCurrent != Int.MAX_VALUE && mCurrentStats.minCurrent != 0) {
            bucket.minCurrent = mCurrentStats.minCurrent
        }
        if (mCurrentStats.maxCurrent != Int.MIN_VALUE && mCurrentStats.maxCurrent != 0) {
            bucket.maxCurrent = mCurrentStats.maxCurrent
        }
        bucket.avgCurrent = mCurrentStats.avgCurrent
        bucket.temp = mCurrentStats.temp
    }

    private data class SubsystemInfo(val key: String, val uid: Int, val pkg: String, val label: String)

    private suspend fun fetchAppUsages() = withContext(Dispatchers.IO) {
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

            // Streamlined dumpsys: filter in shell to prevent massive 50MB string allocations
            var lines = ShellUtils.exec("dumpsys batterystats --charged | grep -E -A 120 'Estimated (power|battery) use' || dumpsys batterystats --charged")
            if (lines.none { it.contains("Estimated", ignoreCase = true) }) {
                lines = ShellUtils.exec("dumpsys batterystats | grep -E -A 120 'Estimated (power|battery) use' || dumpsys batterystats")
            }

            fun parseDurationMs(durationStr: String): Long {
                if (durationStr.isBlank()) return 0L
                var totalMs = 0L
                val dMatch = Regex("""(\d+(?:\.\d+)?)\s*d""").find(durationStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val hMatch = Regex("""(\d+(?:\.\d+)?)\s*h""").find(durationStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val mMatch = Regex("""(\d+(?:\.\d+)?)\s*m(?!s)""").find(durationStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val sMatch = Regex("""(\d+(?:\.\d+)?)\s*s""").find(durationStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val msMatch = Regex("""(\d+(?:\.\d+)?)\s*ms""").find(durationStr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                totalMs += (dMatch * 86400000.0).toLong()
                totalMs += (hMatch * 3600000.0).toLong()
                totalMs += (mMatch * 60000.0).toLong()
                totalMs += (sMatch * 1000.0).toLong()
                totalMs += msMatch.toLong()

                if (totalMs == 0L) {
                    val directNum = durationStr.trim().toLongOrNull()
                    if (directNum != null) return directNum
                }
                return totalMs
            }

            val resultMap = linkedMapOf<String, BatteryInsightAppUsage>()
            var inPowerSection = false
            val batteryCapacityLimit = mCurrentStats.totalCapacity.toDouble().coerceAtLeast(3000.0)

            // Universal Regex across MIUI, HyperOS, OneUI, ColorOS, OxygenOS, LineageOS & AOSP
            val uidPattern = Regex("""^\s*(?:UID\s+|Uid\s+)?(?:u(\d+)[_a](\d+)|u(\d+)[_i](\d+)|(\d+)):\s*([\d.]+)""", RegexOption.IGNORE_CASE)
            val globalItemPattern = Regex("""^\s*(screen|screen_on|mobile_radio|cellular|cell|radio|modem|wifi|wi-fi|bluetooth|bt|camera|audio|sound|video|media|idle|standby|device_idle|gnss|gps|location|sensors|sensor|wakelock|wake_lock|phone|telephony):\s*([\d.]+)(?:.*duration:\s*([^\n]+))?""", RegexOption.IGNORE_CASE)

            for (line in lines) {
                if (line.contains("Estimated power use (mAh):", ignoreCase = true) ||
                    line.contains("Estimated power use:", ignoreCase = true) ||
                    line.contains("Estimated battery use:", ignoreCase = true)) {
                    inPowerSection = true
                    continue
                }

                if (inPowerSection) {
                    if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("\t")) {
                        break
                    }

                    // 1. Check Global / Hardware Subsystems (MIUI / OneUI / AOSP / ColorOS)
                    val globalMap = globalItemPattern.find(line)
                    if (globalMap != null) {
                        val rawComponent = globalMap.groupValues[1].lowercase()
                        val power = globalMap.groupValues[2].toDoubleOrNull() ?: 0.0
                        val durationStr = globalMap.groupValues.getOrNull(3) ?: ""
                        val durationMs = if (durationStr.isNotEmpty()) parseDurationMs(durationStr) else 0L

                        if (power > 0.05 && power <= batteryCapacityLimit) {
                            val sub = when {
                                rawComponent.contains("screen") -> {
                                    SubsystemInfo("screen", -1, "screen", getString(R.string.battery_insight_subsystem_screen))
                                }
                                rawComponent.contains("mobile") || rawComponent.contains("cell") || rawComponent.contains("radio") || rawComponent.contains("modem") -> {
                                    SubsystemInfo("cellular", -2, "cellular", getString(R.string.battery_insight_subsystem_cellular))
                                }
                                rawComponent.contains("wifi") || rawComponent.contains("wi-fi") -> {
                                    SubsystemInfo("wifi", -3, "wifi", "Wi-Fi")
                                }
                                rawComponent.contains("bluetooth") || rawComponent.contains("bt") -> {
                                    SubsystemInfo("bluetooth", -4, "bluetooth", "Bluetooth")
                                }
                                rawComponent.contains("idle") || rawComponent.contains("standby") -> {
                                    SubsystemInfo("idle", -5, "idle", getString(R.string.battery_insight_subsystem_idle))
                                }
                                rawComponent.contains("camera") -> {
                                    SubsystemInfo("camera", -6, "camera", getString(R.string.battery_insight_subsystem_camera))
                                }
                                rawComponent.contains("audio") || rawComponent.contains("sound") || rawComponent.contains("video") || rawComponent.contains("media") -> {
                                    SubsystemInfo("audio", -7, "audio", getString(R.string.battery_insight_subsystem_audio))
                                }
                                else -> SubsystemInfo("", 0, "", "")
                            }

                            if (sub.key.isNotEmpty()) {
                                val existing = resultMap[sub.key]
                                if (existing != null) {
                                    existing.consumedPowerMah = maxOf(existing.consumedPowerMah, power)
                                    if (durationMs > 0) existing.foregroundTimeMs = maxOf(existing.foregroundTimeMs, durationMs)
                                } else {
                                    resultMap[sub.key] = BatteryInsightAppUsage().apply {
                                        this.uid = sub.uid
                                        this.packageName = sub.pkg
                                        this.appLabel = sub.label
                                        this.consumedPowerMah = power
                                        this.foregroundTimeMs = if (sub.key == "screen" && durationMs <= 0) mCurrentStats.screenOnTime else durationMs
                                        this.backgroundTimeMs = if (sub.key == "idle" && durationMs <= 0) mCurrentStats.screenOffTime else 0L
                                    }
                                }
                            }
                        }
                        continue
                    }

                    // 2. Check UID Items (supports u0a123, u0_a123, u999a123, direct integer UIDs 10264, etc.)
                    val match = uidPattern.find(line)
                    if (match != null) {
                        val uNormal = match.groupValues[1]
                        val aNormal = match.groupValues[2]
                        val uIsolated = match.groupValues[3]
                        val aIsolated = match.groupValues[4]
                        val directUid = match.groupValues[5]
                        val powerStr = match.groupValues[6]

                        val realUid = when {
                            uNormal.isNotEmpty() && aNormal.isNotEmpty() -> {
                                val u = uNormal.toIntOrNull() ?: 0
                                val a = aNormal.toIntOrNull() ?: continue
                                (u * 100000) + 10000 + a
                            }
                            uIsolated.isNotEmpty() && aIsolated.isNotEmpty() -> {
                                val u = uIsolated.toIntOrNull() ?: 0
                                val a = aIsolated.toIntOrNull() ?: continue
                                (u * 100000) + 99000 + a
                            }
                            directUid.isNotEmpty() -> {
                                directUid.toIntOrNull() ?: continue
                            }
                            else -> continue
                        }

                        var power = powerStr.toDoubleOrNull() ?: continue
                        if (power < 0.05) continue

                        if (power > batteryCapacityLimit) {
                            power = power.coerceAtMost(batteryCapacityLimit * 0.5)
                        }

                        // Extract screen / foreground time from parentheses across OEM styles
                        var appFgDurationMs = 0L
                        val screenTimeMatch = Regex("""screen=[\d.]+\s*\(([^)]+)\)""").find(line)
                        if (screenTimeMatch != null) {
                            appFgDurationMs = parseDurationMs(screenTimeMatch.groupValues[1])
                        } else {
                            val cpuFgMatch = Regex("""cpu:fg=[\d.]+\s*\(([^)]+)\)""").find(line)
                            if (cpuFgMatch != null) {
                                appFgDurationMs = parseDurationMs(cpuFgMatch.groupValues[1])
                            } else {
                                val fgGenericMatch = Regex("""fg=[\d.]+\s*\(([^)]+)\)""").find(line)
                                if (fgGenericMatch != null) {
                                    appFgDurationMs = parseDurationMs(fgGenericMatch.groupValues[1])
                                }
                            }
                        }

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

                        if (appFgDurationMs <= 0L) {
                            appFgDurationMs = fgTimeMap[pkgName] ?: 0L
                        }

                        val appKey = if (pkgName.isNotEmpty() && !pkgName.startsWith("uid.")) pkgName else "uid_$realUid"
                        val existing = resultMap[appKey]
                        if (existing != null) {
                            existing.consumedPowerMah = maxOf(existing.consumedPowerMah, power)
                            if (appFgDurationMs > 0) existing.foregroundTimeMs = maxOf(existing.foregroundTimeMs, appFgDurationMs)
                        } else {
                            resultMap[appKey] = BatteryInsightAppUsage().apply {
                                this.uid = realUid
                                this.packageName = pkgName
                                this.appLabel = label
                                this.consumedPowerMah = power
                                this.foregroundTimeMs = appFgDurationMs
                                this.backgroundTimeMs = 0L
                            }
                        }
                    }
                }
            }

            val result = resultMap.values.filter { it.consumedPowerMah > 0.05 }.sortedByDescending { it.consumedPowerMah }.toMutableList()

            // Fallback to UsageStats if no dumpsys data is available
            if (result.isEmpty() && fgTimeMap.isNotEmpty()) {
                val totalFgTime = fgTimeMap.values.sum().coerceAtLeast(1L)
                val estimatedTotalDrain = (mCurrentStats.batteryDrainScreenOn.toDouble() * (mCurrentStats.totalCapacity / 100.0)).coerceAtLeast(10.0)

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
        } catch (_: Exception) {}
    }

    private var lastZeroCurrentNotifiedTime = 0L

    private fun checkAlarms(level: Int, charging: Boolean) {
        val now = System.currentTimeMillis()

        // 1. Sıfır Akım Tam Şarj Bildirimi (Zero Current Full Charge Alarm)
        // Telefon KESİNLİKLE %100 olduktan sonra VE sürekli (en az 10 saniye/ölçüm) sıfır akıma (<= 5 mA) inmedikçe bildirim atma!
        // level < 100 iken cihaz/OEM BATTERY_STATUS_FULL raporlasa bile (termal duraklama, pil koruma limiti vb.) ASLA tam şarj bildirimi atılmaz!
        if (mCurrentStats.isZeroCurrentAlarmEnabled && (charging || isPlugged) && level >= 100) {
            val currentNowAbs = abs(mCurrentStats.currentNow)
            if (currentNowAbs <= 5) {
                zeroCurrentSustainCount++
                if (zeroCurrentSustainCount >= 10) {
                    if (now - lastZeroCurrentNotifiedTime >= 120000) {
                        triggerAlarm(
                            getString(R.string.battery_insight_alarm_zero_current_title, level),
                            getString(R.string.battery_insight_alarm_zero_current_desc)
                        )
                        lastZeroCurrentNotifiedTime = now
                    }
                }
            } else {
                zeroCurrentSustainCount = 0
            }
        } else {
            zeroCurrentSustainCount = 0
        }

        // 2. Eşik Seviyesi Alarmları
        if (!mCurrentStats.isBatteryAlarmEnabled) return
        if (now - lastAlarmNotifiedTime < 60000 && lastAlarmNotifiedLevel == level) return

        if (!charging && level <= mCurrentStats.batteryLowThreshold) {
            triggerAlarm(
                getString(R.string.battery_insight_alarm_low_title, level),
                getString(R.string.battery_insight_alarm_low_desc)
            )
            lastAlarmNotifiedLevel = level
            lastAlarmNotifiedTime = now
        } else if (charging && level >= mCurrentStats.batteryHighThreshold && level < 100) {
            triggerAlarm(
                getString(R.string.battery_insight_alarm_limit_title, level),
                getString(R.string.battery_insight_alarm_limit_desc)
            )
            lastAlarmNotifiedLevel = level
            lastAlarmNotifiedTime = now
        } else if (mCurrentStats.isFullChargeAlarmEnabled && level >= 100) {
            triggerAlarm(
                getString(R.string.battery_insight_alarm_full_title),
                getString(R.string.battery_insight_alarm_full_desc)
            )
            lastAlarmNotifiedLevel = level
            lastAlarmNotifiedTime = now
        }
    }

    private fun triggerAlarm(title: String, text: String) {
        if (mCurrentStats.isBatteryAlarmVibrate) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 200, 350), -1))
        }
        val soundUriStr = mCurrentStats.batteryAlarmSound
        val soundUri = if (!soundUriStr.isNullOrEmpty()) Uri.parse(soundUriStr) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            val r = RingtoneManager.getRingtone(this, soundUri)
            r.play()
        } catch (_: Exception) {}

        val notif = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_insight_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notifManager.notify(ALARM_NOTIF_ID, notif)
    }

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
            zeroCurrentSustainCount = 0
            lastZeroCurrentNotifiedTime = 0L

            minCurrent = Int.MAX_VALUE
            maxCurrent = Int.MIN_VALUE
            totalCurrentSum = 0L
            sampleCount = 0

            bucketStartDrain = 0
            bucketStartScreenOnMs = 0L

            mFlowSamples.clear()
            mHistoryBuckets.clear()
            mAppUsages.clear()

            saveSessionState()
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            getString(R.string.battery_insight_duration_h_m, h, m)
        } else if (m > 0) {
            getString(R.string.battery_insight_duration_m_s, m, s)
        } else {
            getString(R.string.battery_insight_duration_s, s)
        }
    }

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
        val totalSec = ms / 1000
        val totalMin = totalSec / 60
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> getString(R.string.battery_insight_duration_h_m, h, m)
            m > 0 -> getString(R.string.battery_insight_duration_m, m)
            else -> getString(R.string.battery_insight_duration_less_than_minute)
        }
    }

    private fun createNotification(
        titleOverride: String? = null,
        summaryOverride: String? = null,
        bodyOverride: String? = null
    ): Notification {
        val title: String
        val summary: String
        val body: String

        if (titleOverride != null && summaryOverride != null && bodyOverride != null) {
            title = titleOverride
            summary = summaryOverride
            body = bodyOverride
        } else {
            val s = synchronized(mCurrentStats) { mCurrentStats.copy() }
            val isChargingState = s.isCharging || isPlugged
            val mA = abs(s.currentNow)
            val currentStr = if (isChargingState) {
                if (mA > 0) "+$mA mA" else "0 mA"
            } else {
                if (mA > 0) "-$mA mA" else "0 mA"
            }
            val statusStr = if (isChargingState) {
                if (s.level >= 100) getString(R.string.battery_insight_notif_full) else getString(R.string.battery_insight_notif_charging)
            } else {
                getString(R.string.battery_insight_notif_discharging)
            }

            val tempStr = "%.1f".format(s.temp / 10f)
            val powerWattsStr = "%.2f".format(s.powerWatts)

            title = "${s.level}% • $statusStr • $currentStr • ${tempStr}°C"
            summary = "${s.level}% • $currentStr • $powerWattsStr W"

            val totalScreenOff = s.screenOffTime
            val hasSignificantScreenOff = totalScreenOff >= 60000L

            val awakePct = if (hasSignificantScreenOff && s.awakeTime > 0L) {
                ((s.awakeTime.toFloat() / totalScreenOff.toFloat()) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }
            val deepSleepPct = if (hasSignificantScreenOff && s.deepSleepTime > 0L) {
                ((s.deepSleepTime.toFloat() / totalScreenOff.toFloat()) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }

            val activeRateStr = if (s.screenOnTime >= 60000L && s.activeDrainRate > 0f) "%.1f".format(s.activeDrainRate) else "0.0"
            val idleRateStr = if (s.screenOffTime >= 60000L && s.idleDrainRate > 0f) "%.1f".format(s.idleDrainRate) else "0.0"
            val awakePctStr = "%.1f".format(awakePct)
            val deepSleepPctStr = "%.1f".format(deepSleepPct)

            body = buildString {
                append(getString(R.string.battery_insight_notif_active, activeRateStr))
                append(" • ")
                append(getString(R.string.battery_insight_notif_idle, idleRateStr))
                append("\n")
                append(getString(R.string.battery_insight_notif_screen_on, formatDurationNotif(s.screenOnTime), s.batteryDrainScreenOn.toString()))
                append("\n")
                append(getString(R.string.battery_insight_notif_screen_off, formatDurationNotif(s.screenOffTime), s.batteryDrainScreenOff.toString()))
                append("\n")
                append(getString(R.string.battery_insight_notif_awake, formatDurationNotif(s.awakeTime), awakePctStr))
                append("\n")
                append(getString(R.string.battery_insight_notif_deep_sleep, formatDurationNotif(s.deepSleepTime), deepSleepPctStr))
            }
        }

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_insight_notify)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
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

    private fun updateNotification() {
        if (!prefs.getBoolean("battery_insight_enabled", true) || !prefs.getBoolean("battery_insight_notif_enabled", true)) {
            return
        }
        try {
            val s = synchronized(mCurrentStats) { mCurrentStats.copy() }
            val isChargingState = s.isCharging || isPlugged
            val mA = abs(s.currentNow)
            val currentStr = if (isChargingState) {
                if (mA > 0) "+$mA mA" else "0 mA"
            } else {
                if (mA > 0) "-$mA mA" else "0 mA"
            }
            val statusStr = if (isChargingState) {
                if (s.level >= 100) getString(R.string.battery_insight_notif_full) else getString(R.string.battery_insight_notif_charging)
            } else {
                getString(R.string.battery_insight_notif_discharging)
            }
            val tempStr = "%.1f".format(s.temp / 10f)
            val powerWattsStr = "%.2f".format(s.powerWatts)
            val newTitle = "${s.level}% • $statusStr • $currentStr • ${tempStr}°C"
            val newSummary = "${s.level}% • $currentStr • $powerWattsStr W"

            val activeRateStr = if (s.screenOnTime >= 60000L && s.activeDrainRate > 0f) "%.1f".format(s.activeDrainRate) else "0.0"
            val idleRateStr = if (s.screenOffTime >= 60000L && s.idleDrainRate > 0f) "%.1f".format(s.idleDrainRate) else "0.0"
            val totalScreenOff = s.screenOffTime
            val hasSignificantScreenOff = totalScreenOff >= 60000L

            val awakePct = if (hasSignificantScreenOff && s.awakeTime > 0L) {
                ((s.awakeTime.toFloat() / totalScreenOff.toFloat()) * 100f).coerceIn(0f, 100f)
            } else 0f
            val deepSleepPct = if (hasSignificantScreenOff && s.deepSleepTime > 0L) {
                ((s.deepSleepTime.toFloat() / totalScreenOff.toFloat()) * 100f).coerceIn(0f, 100f)
            } else 0f
            val awakePctStr = "%.1f".format(awakePct)
            val deepSleepPctStr = "%.1f".format(deepSleepPct)

            val newBody = buildString {
                append(getString(R.string.battery_insight_notif_active, activeRateStr))
                append(" • ")
                append(getString(R.string.battery_insight_notif_idle, idleRateStr))
                append("\n")
                append(getString(R.string.battery_insight_notif_screen_on, formatDurationNotif(s.screenOnTime), s.batteryDrainScreenOn.toString()))
                append("\n")
                append(getString(R.string.battery_insight_notif_screen_off, formatDurationNotif(s.screenOffTime), s.batteryDrainScreenOff.toString()))
                append("\n")
                append(getString(R.string.battery_insight_notif_awake, formatDurationNotif(s.awakeTime), awakePctStr))
                append("\n")
                append(getString(R.string.battery_insight_notif_deep_sleep, formatDurationNotif(s.deepSleepTime), deepSleepPctStr))
            }

            // Real-time updates: update immediately when any value changes (mA, watts, level, temp, etc.)
            val textChanged = newTitle != lastNotifTitle || newSummary != lastNotifSummary || newBody != lastNotifBody
            if (!textChanged) {
                return // Skip redundant IPC if absolutely nothing changed
            }

            lastNotifTitle = newTitle
            lastNotifSummary = newSummary
            lastNotifBody = newBody
            notifManager.notify(NOTIF_ID, createNotification(newTitle, newSummary, newBody))
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
            }

            notifManager.createNotificationChannel(monitorChannel)
            notifManager.createNotificationChannel(alarmChannel)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        instance = null
        try {
            saveSessionState()
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}

        // If destroyed unexpectedly, arm revive alarm
        if (prefs.getBoolean("battery_insight_enabled", true)) {
            try {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val intent = Intent(this, BootReceiver::class.java).apply {
                    action = "com.acer.batteryinsight.WATCHDOG_KEEPALIVE"
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    9999,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager?.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000L, pendingIntent)
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
