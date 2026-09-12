package com.acer.batteryinsight.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object IconUtils {
    private const val TAG = "IconUtils"
    private const val PREFS_NAME = "battery_insight_prefs"
    const val PREF_MONET_ICON_ENABLED = "monet_icon_enabled"

    private const val DEFAULT_ACTIVITY = "com.acer.batteryinsight.MainActivity"
    private const val MONET_ACTIVITY_ALIAS = "com.acer.batteryinsight.MainActivityMonet"

    fun isMonetIconEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_MONET_ICON_ENABLED, false)
    }

    fun setMonetIconEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_MONET_ICON_ENABLED, enabled).apply()

        try {
            val pm = context.packageManager
            val defaultComp = ComponentName(context, DEFAULT_ACTIVITY)
            val monetComp = ComponentName(context, MONET_ACTIVITY_ALIAS)

            if (enabled) {
                pm.setComponentEnabledSetting(
                    monetComp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    defaultComp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                pm.setComponentEnabledSetting(
                    defaultComp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    monetComp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            Log.d(TAG, "Launcher icon switched. Monet enabled: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch launcher icon alias", e)
        }
    }
}
