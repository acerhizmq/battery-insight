package com.acer.batteryinsight.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.acer.batteryinsight.BuildConfig
import com.acer.batteryinsight.MainActivity
import com.acer.batteryinsight.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseTitle: String,
    val changelog: String,
    val downloadUrl: String?,
    val apkSize: Long,
    val isUpdateAvailable: Boolean
)

object UpdateManager {

    private const val GITHUB_REPO_OWNER = "acerhizmq"
    private const val GITHUB_REPO_NAME = "battery-insight"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    const val CHANNEL_ID_UPDATES = "battery_insight_updates"
    const val NOTIFICATION_ID_UPDATE = 3001
    const val EXTRA_CHECK_UPDATE = "com.acer.batteryinsight.CHECK_UPDATE"

    const val CHECK_INTERVAL_MS = 24 * 3600 * 1000L // 24 Hours

    private const val PREFS_NAME = "battery_insight_updates_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val KEY_IGNORED_VERSION = "ignored_version"
    private const val KEY_CACHED_UPDATE_JSON = "cached_update_json"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
    private const val KEY_LAST_NOTIFIED_TIME = "last_notified_time"

    suspend fun checkForUpdate(currentVersion: String = BuildConfig.VERSION_NAME): Result<AppUpdateInfo> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(API_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "BatteryInsight-Android/${BuildConfig.VERSION_NAME}")
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(
                        Exception("GitHub API returned HTTP $responseCode")
                    )
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "").trim()
                val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
                val releaseTitle = json.optString("name", "Version $tagName").ifEmpty { "Version $tagName" }
                val body = json.optString("body", "")

                var downloadUrl: String? = null
                var apkSize: Long = 0L

                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val isNewer = isNewerVersion(currentVersion, cleanRemoteVersion)

                Result.success(
                    AppUpdateInfo(
                        latestVersion = cleanRemoteVersion,
                        releaseTitle = releaseTitle,
                        changelog = body,
                        downloadUrl = downloadUrl,
                        apkSize = apkSize,
                        isUpdateAvailable = isNewer
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun isNewerVersion(current: String, remote: String): Boolean {
        if (remote.isEmpty() || current.isEmpty()) return false
        val cParts = current.removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
        val rParts = remote.removePrefix("v").removePrefix("V").split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(cParts.size, rParts.size)
        for (i in 0 until maxLen) {
            val c = cParts.getOrElse(i) { 0 }
            val r = rParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(updateDir, "BatteryInsight-v$versionName.apk")

            val url = URL(downloadUrl)
            var connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "BatteryInsight-Android/${BuildConfig.VERSION_NAME}")

            var redirect = false
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                redirect = true
            }

            if (redirect) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(newUrl).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "BatteryInsight-Android/${BuildConfig.VERSION_NAME}")
            }

            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L

            connection.inputStream.use { input: InputStream ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var lastReportTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 100 || downloadedBytes == totalBytes) {
                            lastReportTime = now
                            val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                            withContext(Dispatchers.Main) {
                                onProgress(progress, downloadedBytes, totalBytes)
                            }
                        }
                    }
                    output.flush()
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(installIntent)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun cacheUpdateInfo(context: Context, info: AppUpdateInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("latestVersion", info.latestVersion)
            put("releaseTitle", info.releaseTitle)
            put("changelog", info.changelog)
            put("downloadUrl", info.downloadUrl ?: "")
            put("apkSize", info.apkSize)
            put("isUpdateAvailable", info.isUpdateAvailable)
        }
        prefs.edit().putString(KEY_CACHED_UPDATE_JSON, json.toString()).apply()
    }

    fun getCachedUpdateInfo(context: Context): AppUpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CACHED_UPDATE_JSON, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val downloadUrl = json.optString("downloadUrl", "").ifEmpty { null }
            AppUpdateInfo(
                latestVersion = json.getString("latestVersion"),
                releaseTitle = json.getString("releaseTitle"),
                changelog = json.getString("changelog"),
                downloadUrl = downloadUrl,
                apkSize = json.getLong("apkSize"),
                isUpdateAvailable = json.getBoolean("isUpdateAvailable")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clearCachedUpdateInfo(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CACHED_UPDATE_JSON).apply()
    }

    fun showUpdateNotification(context: Context, updateInfo: AppUpdateInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotifiedVer = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)
        val lastNotifiedTime = prefs.getLong(KEY_LAST_NOTIFIED_TIME, 0L)
        val now = System.currentTimeMillis()

        // Suppress repeated notifications for the same version if alerted within CHECK_INTERVAL_MS (24h)
        if (lastNotifiedVer == updateInfo.latestVersion && (now - lastNotifiedTime) < CHECK_INTERVAL_MS) {
            return
        }

        // Cache update info for instant opening
        cacheUpdateInfo(context, updateInfo)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_UPDATES,
                context.getString(R.string.battery_insight_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.battery_insight_channel_updates_desc)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHECK_UPDATE, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_battery_insight_notify)
            .setContentTitle(context.getString(R.string.battery_insight_update_notify_title))
            .setContentText(
                context.getString(R.string.battery_insight_update_notify_desc, updateInfo.latestVersion)
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${updateInfo.releaseTitle}\n\n${updateInfo.changelog}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)

        // Record last notification state
        prefs.edit()
            .putString(KEY_LAST_NOTIFIED_VERSION, updateInfo.latestVersion)
            .putLong(KEY_LAST_NOTIFIED_TIME, now)
            .apply()
    }

    fun shouldCheckBackground(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()
        // Check once every 24 hours in background
        return (now - lastCheck) >= CHECK_INTERVAL_MS
    }

    fun recordCheckTime(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
    }
}
