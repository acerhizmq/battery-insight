package com.acer.batteryinsight.service

import android.content.Context
import android.util.Log
import com.acer.batteryinsight.utils.ShellUtils
import java.io.File

/**
 * Root-only measures that keep the monitor alive when the OEM or the system kills the app.
 *
 * - A small watchdog shell loop runs as root *outside* the app's processes (detached session, moved
 *   out of the app's cgroup), so it survives the app's process group being killed and restarts
 *   the service. It exits on its own when the enable flag disappears or the app is uninstalled.
 * - On Xiaomi HyperOS/MIUI the package is added to Settings.System.MILLET_NO_RESTRICT_APP, the set
 *   that the Greezer freezer and PowerKeeper consult before freezing/killing background apps
 *   (the same list the per-app "No restrictions" option writes).
 */
object RootKeepAlive {

    private const val TAG = "RootKeepAlive"
    const val PREF_ENABLED = "battery_insight_root_keepalive"

    private const val SCRIPT = "keepalive.sh"
    private const val FLAG = "keepalive.enabled"
    private const val PID = "keepalive.pid"
    private const val CHECK_INTERVAL_SEC = 30

    fun isWanted(context: Context): Boolean {
        val prefs = context.getSharedPreferences("battery_insight_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("battery_insight_enabled", true) && prefs.getBoolean(PREF_ENABLED, true)
    }

    /** Creates or removes the flag file the watchdog checks; needs no root. */
    fun syncFlag(context: Context): Boolean {
        val wanted = isWanted(context)
        val flag = File(context.filesDir, FLAG)
        try {
            if (wanted) flag.createNewFile() else flag.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not update keep-alive flag", e)
        }
        return wanted
    }

    private fun script(context: Context): String = buildScript(
        context.filesDir.absolutePath,
        context.packageName,
        "${context.packageName}/${BatteryInsightService::class.java.name}",
    )

    internal fun buildScript(dir: String, pkg: String, service: String): String {
        return """
            |#!/system/bin/sh
            |DIR='$dir'
            |# Leave the app's cgroup so killing the app's process group does not take this loop with it.
            |for c in /sys/fs/cgroup/cgroup.procs /acct/cgroup.procs /dev/cpuctl/cgroup.procs; do
            |  echo $$ > "${'$'}c" 2>/dev/null
            |done
            |echo $$ > "${'$'}DIR/$PID"
            |while [ -f "${'$'}DIR/$FLAG" ] && [ -d "${'$'}DIR" ]; do
            |  if ! pidof '$pkg:monitor' >/dev/null 2>&1; then
            |    am start-foreground-service -n '$service' >/dev/null 2>&1 || am startservice -n '$service' >/dev/null 2>&1
            |  fi
            |  sleep $CHECK_INTERVAL_SEC
            |done
            |rm -f "${'$'}DIR/$PID"
            |""".trimMargin()
    }

    /** Starts the watchdog if wanted and not already running. Idempotent; no-op without root. */
    suspend fun ensureRunning(context: Context) {
        if (!ShellUtils.isRootAvailable() || !syncFlag(context)) return
        try {
            val scriptFile = File(context.filesDir, SCRIPT)
            val content = script(context)
            if (!scriptFile.exists() || scriptFile.readText() != content) scriptFile.writeText(content)

            ShellUtils.execRoot(buildLaunchCommand(context.filesDir.absolutePath))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start keep-alive watchdog", e)
        }
    }

    /** Stops the watchdog immediately (it would also exit on its own within one interval). */
    suspend fun stop(context: Context) {
        syncFlag(context)
        if (!ShellUtils.isRootAvailable()) return
        ShellUtils.execRoot(buildStopCommand(context.filesDir.absolutePath))
    }

    /** Adds the app to Xiaomi's Greezer/PowerKeeper "no restrictions" set if missing. */
    suspend fun applyOemExemptions(context: Context) {
        if (!ShellUtils.isRootAvailable() || !isWanted(context)) return
        ShellUtils.execRoot(buildOemCommand(context.packageName))
    }

    internal fun buildLaunchCommand(dir: String): String = """
        P=${'$'}(cat '$dir/$PID' 2>/dev/null)
        if [ -n "${'$'}P" ] && kill -0 "${'$'}P" 2>/dev/null && grep -q '$SCRIPT' /proc/"${'$'}P"/cmdline 2>/dev/null; then
          :
        elif command -v setsid >/dev/null 2>&1; then
          setsid sh '$dir/$SCRIPT' </dev/null >/dev/null 2>&1 &
        else
          nohup sh '$dir/$SCRIPT' </dev/null >/dev/null 2>&1 &
        fi
        """.trimIndent()

    internal fun buildStopCommand(dir: String): String = """
        P=${'$'}(cat '$dir/$PID' 2>/dev/null)
        if [ -n "${'$'}P" ] && grep -q '$SCRIPT' /proc/"${'$'}P"/cmdline 2>/dev/null; then kill "${'$'}P" 2>/dev/null; fi
        rm -f '$dir/$PID'
        """.trimIndent()

    internal fun buildOemCommand(pkg: String): String = """
            if [ -n "${'$'}(getprop ro.mi.os.version.name)${'$'}(getprop ro.miui.ui.version.name)" ]; then
              L=${'$'}(settings get system MILLET_NO_RESTRICT_APP 2>/dev/null)
              [ "${'$'}L" = "null" ] && L=""
              if ! echo "${'$'}L" | tr -d ' ' | tr ',' '\n' | grep -qx '$pkg'; then
                if [ -z "${'$'}L" ]; then settings put system MILLET_NO_RESTRICT_APP '$pkg'
                else settings put system MILLET_NO_RESTRICT_APP "${'$'}L,$pkg"; fi
              fi
              cmd appops set '$pkg' 10008 allow 2>/dev/null
            fi
            """.trimIndent()
}
