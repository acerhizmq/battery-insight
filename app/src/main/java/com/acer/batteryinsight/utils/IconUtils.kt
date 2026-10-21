package com.acer.batteryinsight.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object IconUtils {
    private const val TAG = "IconUtils"
    private const val PREFS_NAME = "battery_insight_prefs"
    const val PREF_MONET_ICON_ENABLED = "monet_icon_enabled"

    private const val DEFAULT_ACTIVITY = "com.acer.batteryinsight.MainActivity"
    private const val MONET_ACTIVITY_ALIAS = "com.acer.batteryinsight.MainActivityMonet"

    /**
     * Intent that opens the app through whichever launcher component is currently enabled.
     * An explicit MainActivity intent fails while the Monet alias is active, because that
     * disables the MainActivity component itself.
     */
    fun launchIntent(context: Context): Intent {
        val launch = try {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        } catch (_: Exception) {
            null
        }
        return launch ?: Intent().setComponent(
            ComponentName(context, if (isMonetIconEnabled(context)) MONET_ACTIVITY_ALIAS else DEFAULT_ACTIVITY)
        )
    }

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
