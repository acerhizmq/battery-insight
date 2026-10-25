package com.acer.batteryinsight.utils

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.text.DateFormat
import java.util.Date

/**
 * Reads the system's record of why this app's process died (Android 11+), so unexpected
 * shutdowns can be diagnosed on the device without adb.
 */
object ExitReasonTracker {

    private const val TAG = "ExitReasonTracker"

    data class Entry(
        val timestampMs: Long,
        val reason: String,
        val description: String?,
        val importance: Int,
        val processName: String,
    )

    fun recent(context: Context, max: Int = 10): List<Entry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getHistoricalProcessExitReasons(null, 0, max).map {
                Entry(it.timestamp, reasonName(it.reason), it.description, it.importance, it.processName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read exit reasons", e)
            emptyList()
        }
    }

    fun logRecent(context: Context) {
        for (e in recent(context, 5)) {
            Log.i(TAG, "${Date(e.timestampMs)} ${e.processName} reason=${e.reason} importance=${e.importance} desc=${e.description}")
        }
    }

    fun format(e: Entry): String {
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(e.timestampMs))
        val desc = e.description?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
        return "$time • ${e.reason}$desc"
    }

    fun reasonName(code: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return code.toString()
        return when (code) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            14 -> "FREEZER"
            15 -> "PACKAGE_STATE_CHANGE"
            16 -> "PACKAGE_UPDATED"
            else -> "UNKNOWN($code)"
        }
    }
}
