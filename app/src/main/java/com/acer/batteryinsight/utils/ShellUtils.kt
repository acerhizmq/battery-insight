package com.acer.batteryinsight.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.SystemClock
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ShellUtils {

    private const val TAG = "ShellUtils"

    // Paths that root `cat` could not read either; retried after this delay instead of every tick.
    private const val MISSING_PATH_RETRY_MS = 10 * 60 * 1000L
    private val missingPaths = ConcurrentHashMap<String, Long>()

    /** Configures LibSU; must run before the first shell is created (Application.onCreate). */
    fun configure() {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    /**
     * Checks whether root is currently granted and available.
     * Purely passive check; NEVER triggers a superuser request dialog.
     */
    fun isRootAvailable(): Boolean {
        val cached = Shell.getCachedShell()
        return cached != null && cached.isRoot
    }

    /**
     * Asynchronously warms up / initializes the root shell.
     * Useful on cold start, boot, or activity resume.
     */
    fun initRootShell(onResult: ((Boolean) -> Unit)? = null) {
        try {
            Shell.getShell { shell ->
                val isRoot = shell.isRoot
                Log.d(TAG, "Root shell initialized. isRoot=$isRoot")
                onResult?.invoke(isRoot)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to init root shell", e)
            onResult?.invoke(false)
        }
    }

    /**
     * Explicitly requests root access (shows Magisk / KernelSU / APatch prompt if needed).
     */
    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            shell.isRoot
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting root", e)
            false
        }
    }

    /**
     * Reads a sysfs file. First attempts direct file read, then falls back to root cat if available.
     */
    suspend fun readSysfs(path: String): String? = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.exists() && f.canRead()) {
            try {
                return@withContext f.readText().trim()
            } catch (_: Exception) {}
        }

        if (!isRootAvailable()) return@withContext null

        try {
            val result = Shell.cmd("cat '$path' 2>/dev/null").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out[0].trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Synchronous sysfs reader with direct file fast path and safe root fallback.
     */
    fun readSysfsSync(path: String): String? {
        val f = File(path)
        if (f.exists() && f.canRead()) {
            try {
                return f.readText().trim()
            } catch (_: Exception) {}
        }
        // Never execute synchronous root shell commands on Android UI / Main Thread
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return null
        }
        if (!isRootAvailable()) return null
        val missingSince = missingPaths[path]
        if (missingSince != null && SystemClock.elapsedRealtime() - missingSince < MISSING_PATH_RETRY_MS) {
            return null
        }
        return try {
            val result = Shell.cmd("cat '$path' 2>/dev/null").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                missingPaths.remove(path)
                result.out[0].trim()
            } else {
                missingPaths[path] = SystemClock.elapsedRealtime()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes a shell command line (pipes and `||` are supported) through the root shell when
     * available, otherwise through an unprivileged `sh -c` (enough for dumpsys once ADB granted DUMP).
     */
    suspend fun exec(command: String, timeoutSec: Long = 15): List<String> = withContext(Dispatchers.IO) {
        if (isRootAvailable()) {
            try {
                val result = Shell.cmd(command).exec()
                // A non-zero exit (e.g. grep found nothing) still carries useful output.
                if (result.isSuccess || result.out.isNotEmpty()) return@withContext result.out
            } catch (e: Exception) {
                Log.w(TAG, "Root exec failed: $command", e)
            }
        }
        execUnprivileged(command, timeoutSec)
    }

    /** Runs [command] only when root is granted; a no-op otherwise. */
    suspend fun execRoot(command: String): List<String> = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) return@withContext emptyList()
        try {
            Shell.cmd(command).exec().out
        } catch (e: Exception) {
            Log.w(TAG, "Root exec failed: $command", e)
            emptyList()
        }
    }

    private fun execUnprivileged(command: String, timeoutSec: Long): List<String> {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val lines = Collections.synchronizedList(mutableListOf<String>())
            val readerThread = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { lines.add(it) }
                } catch (_: Exception) {}
            }
            readerThread.start()

            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
            }
            readerThread.join(2000)
            synchronized(lines) { ArrayList(lines) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- ADB & System Permission Helpers ---

    fun hasBatteryStatsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BATTERY_STATS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasDumpPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.DUMP
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Returns true if all required ADB / non-root permissions are granted.
     */
    fun isAllAdbPermissionsGranted(context: Context): Boolean {
        return hasBatteryStatsPermission(context) &&
               hasDumpPermission(context) &&
               hasUsageStatsPermission(context) &&
               hasNotificationPermission(context)
    }

    /**
     * Returns true if either root is granted OR all required ADB permissions are granted.
     */
    fun canProceedWithMonitoring(context: Context): Boolean {
        return isRootAvailable() || isAllAdbPermissionsGranted(context)
    }
}
