/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.utils

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.R

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val s = totalSec % 60
    val totalMin = totalSec / 60
    val m = totalMin % 60
    val h = totalMin / 60

    val isTr = java.util.Locale.getDefault().language == "tr"
    val hUnit = if (isTr) "sa" else "h"
    val mUnit = if (isTr) "dk" else "m"
    val sUnit = if (isTr) "sn" else "s"

    return if (h > 0) {
        if (s > 0) "$h $hUnit $m $mUnit $s $sUnit" else "$h $hUnit $m $mUnit"
    } else if (m > 0) {
        "$m $mUnit $s $sUnit"
    } else {
        "$s $sUnit"
    }
}

fun getRingtoneName(context: Context, uriString: String?): String {
    if (uriString.isNullOrEmpty()) {
        return context.getString(R.string.battery_insight_ringtone_none)
    }
    return try {
        val uri = Uri.parse(uriString)
        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            ?: context.getString(R.string.battery_insight_ringtone_none)
    } catch (_: Exception) {
        context.getString(R.string.battery_insight_ringtone_none)
    }
}

fun alarmFrequencyLabels(context: Context): List<String> = listOf(
    context.getString(R.string.battery_insight_alarm_freq_once),
    context.getString(R.string.battery_insight_alarm_freq_1pct),
    context.getString(R.string.battery_insight_alarm_freq_5pct),
    context.getString(R.string.battery_insight_alarm_freq_10pct),
    context.getString(R.string.battery_insight_alarm_freq_5min),
)

fun BatteryInsightStats.isFullyCharged(): Boolean {
    if (level < 100 || plugged == 0) return false
    return status == android.os.BatteryManager.BATTERY_STATUS_FULL
        || status == android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING
}
