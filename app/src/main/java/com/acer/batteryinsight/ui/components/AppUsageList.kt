/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.acer.batteryinsight.ui.utils.formatTime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.acer.batteryinsight.model.BatteryInsightAppUsage
import com.acer.batteryinsight.R

private val appIconCache = LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(120)

@Composable
fun AppUsageSummaryCard(
    totalMah: Double,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val glassBg = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.80f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
            )
        )
    }

    val borderStroke = BorderStroke(
        width = 0.5.dp,
        color = Color.White.copy(alpha = if (isDark) 0.08f else 0.25f),
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.08f),
            ),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = borderStroke,
    ) {
        Box(
            modifier = Modifier
                .background(glassBg)
                .padding(20.dp)
        ) {
            Column {
                Text(
                    stringResource(R.string.battery_insight_app_usage_since_charge),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.battery_insight_total_mah, totalMah),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun AppUsageRow(
    app: BatteryInsightAppUsage,
    pct: Double,
    pm: android.content.pm.PackageManager,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val glassBg = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f),
                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.75f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f),
            )
        )
    }

    val borderStroke = BorderStroke(
        width = 0.5.dp,
        color = Color.White.copy(alpha = if (isDark) 0.08f else 0.25f),
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                ambientColor = Color.Black.copy(alpha = 0.04f),
            ),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = borderStroke,
    ) {
        Box(
            modifier = Modifier
                .background(glassBg)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Determine system icon or app icon
                val systemIcon = when (app.packageName) {
                    "screen" -> Icons.Rounded.Smartphone
                    "cellular" -> Icons.Rounded.SignalCellularAlt
                    "wifi" -> Icons.Rounded.Wifi
                    "bluetooth" -> Icons.Rounded.Bluetooth
                    "idle" -> Icons.Rounded.Bedtime
                    "android" -> Icons.Rounded.Android
                    "kernel" -> Icons.Rounded.Memory
                    else -> null
                }

                val resolvedPkg = remember(app.packageName, app.uid) {
                    if (!app.packageName.isNullOrEmpty() && !app.packageName.startsWith("uid.")) {
                        app.packageName
                    } else if (app.uid > 0) {
                        try {
                            pm.getPackagesForUid(app.uid)?.firstOrNull() ?: app.packageName
                        } catch (_: Exception) {
                            app.packageName
                        }
                    } else {
                        app.packageName
                    }
                }

                val resolvedLabel = remember(app.appLabel, resolvedPkg, app.uid) {
                    if (!app.appLabel.isNullOrEmpty() && !app.appLabel.startsWith("UID ")) {
                        app.appLabel
                    } else if (!resolvedPkg.isNullOrEmpty() && !resolvedPkg.startsWith("uid.")) {
                        try {
                            val ai = pm.getApplicationInfo(resolvedPkg, 0)
                            pm.getApplicationLabel(ai).toString()
                        } catch (_: Exception) {
                            app.appLabel ?: "UID ${app.uid}"
                        }
                    } else {
                        app.appLabel ?: "UID ${app.uid}"
                    }
                }

                val iconBitmap = remember(resolvedPkg, systemIcon) {
                    if (systemIcon != null) null
                    else {
                        val key = resolvedPkg ?: "default"
                        appIconCache.get(key) ?: run {
                            val bmp = try {
                                if (resolvedPkg.isNullOrEmpty() || resolvedPkg.startsWith("uid.")) {
                                    pm.defaultActivityIcon.toBitmap(72, 72).asImageBitmap()
                                } else {
                                    pm.getApplicationIcon(resolvedPkg).toBitmap(72, 72).asImageBitmap()
                                }
                            } catch (_: Exception) {
                                try {
                                    pm.defaultActivityIcon.toBitmap(72, 72).asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (bmp != null) appIconCache.put(key, bmp)
                            bmp
                        }
                    }
                }

                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                ) {
                    if (systemIcon != null) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = systemIcon,
                                contentDescription = resolvedLabel,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = resolvedLabel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (resolvedLabel.firstOrNull() ?: 'A').uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = resolvedLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "%.1f%%".format(pct),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    )
                    val subtitle = if (app.foregroundTimeMs > 0) {
                        "%.2f mAh • %s".format(app.consumedPowerMah, formatTime(app.foregroundTimeMs))
                    } else {
                        "%.2f mAh".format(app.consumedPowerMah)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
