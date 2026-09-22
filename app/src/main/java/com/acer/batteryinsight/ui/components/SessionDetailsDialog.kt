/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.ui.utils.formatTime
import com.acer.batteryinsight.R
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsSheet(
    stats: BatteryInsightStats,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize(),
    ) {
        SessionDetailsContent(
            stats = stats,
            onClose = onDismiss,
        )
    }
}

@Composable
fun SessionDetailsContent(
    stats: BatteryInsightStats,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember(stats.isCharging) {
        mutableIntStateOf(if (stats.isCharging) 0 else 1)
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = maxOf(12.dp, topInset)),
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.battery_insight_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.battery_insight_session_details),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 2. Full Width TabRow (Charging vs Discharging)
        val tabs = listOf(
            stringResource(R.string.battery_insight_charging_tab),
            stringResource(R.string.battery_insight_discharging_tab),
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp,
                            ),
                            color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    },
                )
            }
        }

        // 3. Scrollable Content Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomInset + 32.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(250),
                label = "session_mode_crossfade",
            ) { mode ->
                val isChargingMode = mode == 0
                val totalCap = if (stats.totalCapacity > 0) stats.totalCapacity else 5000

                // 1. TOTAL TIME & METRICS
                val totalTimeMs = if (isChargingMode) {
                    val cTime = stats.chargeScreenOnTime + stats.chargeScreenOffTime
                    if (cTime > 0L) cTime else if (stats.isCharging) (stats.screenOnTime + stats.screenOffTime) else 0L
                } else {
                    stats.screenOnTime + stats.screenOffTime
                }

                val totalGainOrDrainPct = if (isChargingMode) {
                    stats.batteryChargeScreenOn + stats.batteryChargeScreenOff
                } else {
                    stats.batteryDrainScreenOn + stats.batteryDrainScreenOff
                }
                val totalMah = ((totalGainOrDrainPct.toFloat() / 100f) * totalCap).toInt()
                val totalHours = max(0.001f, totalTimeMs / 3600000f)
                val totalRate = if (isChargingMode) {
                    if (totalHours >= 0.016f && totalGainOrDrainPct > 0) totalGainOrDrainPct / totalHours else stats.chargeRate
                } else {
                    if (totalHours >= 0.016f && totalGainOrDrainPct > 0) totalGainOrDrainPct / totalHours else 0f
                }
                val totalRateMa = if (isChargingMode) {
                    if (stats.chargeCurrentAvg > 0) stats.chargeCurrentAvg else if (totalHours >= 0.016f) (totalMah / totalHours).toInt() else 0
                } else {
                    if (totalHours >= 0.016f) (totalMah / totalHours).toInt() else 0
                }

                // 2. SCREEN ON TIME & METRICS
                val sotMs = if (isChargingMode) stats.chargeScreenOnTime else stats.screenOnTime
                val sotGainOrDrainPct = if (isChargingMode) stats.batteryChargeScreenOn else stats.batteryDrainScreenOn
                val sotMah = ((sotGainOrDrainPct.toFloat() / 100f) * totalCap).toInt()
                val sotHours = max(0.001f, sotMs / 3600000f)
                val sotRate = if (isChargingMode) {
                    if (sotHours >= 0.016f && sotGainOrDrainPct > 0) sotGainOrDrainPct / sotHours else 0f
                } else {
                    stats.activeDrainRate
                }
                val sotMa = if (sotHours >= 0.016f && sotMah > 0) (sotMah / sotHours).toInt() else 0

                // 3. SCREEN OFF TIME & METRICS
                val soffMs = if (isChargingMode) stats.chargeScreenOffTime else stats.screenOffTime
                val soffGainOrDrainPct = if (isChargingMode) stats.batteryChargeScreenOff else stats.batteryDrainScreenOff
                val soffMah = ((soffGainOrDrainPct.toFloat() / 100f) * totalCap).toInt()
                val soffHours = max(0.001f, soffMs / 3600000f)
                val soffRate = if (isChargingMode) {
                    if (soffHours >= 0.016f && soffGainOrDrainPct > 0) soffGainOrDrainPct / soffHours else 0f
                } else {
                    stats.idleDrainRate
                }
                val soffMa = if (soffHours >= 0.016f && soffMah > 0) (soffMah / soffHours).toInt() else 0

                val totalSoffTime = max(1L, stats.screenOffTime)
                val hasSoff = stats.screenOffTime >= 60000L
                val deepSleepPct = if (hasSoff && stats.deepSleepTime > 0L) {
                    ((stats.deepSleepTime.toFloat() / totalSoffTime) * 100f).coerceIn(0f, 100f)
                } else 0f
                val awakePct = if (hasSoff && stats.awakeTime > 0L) {
                    ((stats.awakeTime.toFloat() / totalSoffTime) * 100f).coerceIn(0f, 100f)
                } else 0f
                val deepSleepMah = if (hasSoff && stats.deepSleepTime > 0L) {
                    ((stats.deepSleepTime.toFloat() / totalSoffTime) * soffMah).toInt()
                } else 0
                val awakeMah = if (hasSoff && stats.awakeTime > 0L) {
                    ((stats.awakeTime.toFloat() / totalSoffTime) * soffMah).toInt()
                } else 0

                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    // Section 1: Total time
                    SessionSectionCard(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.battery_insight_total_time),
                        duration = formatTime(totalTimeMs),
                        iconTint = MaterialTheme.colorScheme.primary,
                        subCards = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SessionMetricSubCard(
                                    label = if (isChargingMode) stringResource(R.string.battery_insight_charged) else stringResource(R.string.battery_insight_used),
                                    mainValue = "$totalGainOrDrainPct%",
                                    subValue = "$totalMah mAh",
                                    accentColor = if (isChargingMode) MaterialTheme.colorScheme.primary else Color(0xFFE57373),
                                    modifier = Modifier.weight(1f),
                                )
                                SessionMetricSubCard(
                                    label = if (isChargingMode) stringResource(R.string.battery_insight_charging_rate) else stringResource(R.string.battery_insight_discharging_rate),
                                    mainValue = "%.1f%%/h".format(totalRate),
                                    subValue = if (isChargingMode) "+$totalRateMa mA" else "-$totalRateMa mA",
                                    accentColor = if (isChargingMode) MaterialTheme.colorScheme.primary else Color(0xFFE57373),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        },
                    )

                    // Section 2: Screen on time
                    SessionSectionCard(
                        icon = Icons.Rounded.Visibility,
                        title = stringResource(R.string.battery_insight_screen_on_time),
                        duration = formatTime(sotMs),
                        iconTint = Color(0xFF81C784),
                        showInfo = true,
                        subCards = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                SessionMetricSubCard(
                                    label = if (isChargingMode) stringResource(R.string.battery_insight_charged) else stringResource(R.string.battery_insight_used),
                                    mainValue = "$sotGainOrDrainPct%",
                                    subValue = "$sotMah mAh",
                                    accentColor = if (isChargingMode) MaterialTheme.colorScheme.primary else Color(0xFFE57373),
                                    modifier = Modifier.weight(1f),
                                )
                                SessionMetricSubCard(
                                    label = if (isChargingMode) stringResource(R.string.battery_insight_charging_rate) else stringResource(R.string.battery_insight_discharging_rate),
                                    mainValue = "%.1f%%/h".format(sotRate),
                                    subValue = if (isChargingMode) "+$sotMa mA" else "-$sotMa mA",
                                    accentColor = if (isChargingMode) MaterialTheme.colorScheme.primary else Color(0xFFE57373),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        },
                    )

                    // Section 3: Screen off time
                    SessionSectionCard(
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.battery_insight_screen_off_time),
                        duration = formatTime(soffMs),
                        iconTint = Color(0xFF90CAF9),
                        showInfo = true,
                        subCards = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SessionMetricSubCard(
                                        label = if (isChargingMode) stringResource(R.string.battery_insight_charged) else stringResource(R.string.battery_insight_used),
                                        mainValue = "$soffGainOrDrainPct%",
                                        subValue = "$soffMah mAh",
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    SessionMetricSubCard(
                                        label = if (isChargingMode) stringResource(R.string.battery_insight_charging_rate) else stringResource(R.string.battery_insight_discharging_rate),
                                        mainValue = "%.1f%%/h".format(soffRate),
                                        subValue = if (isChargingMode) "+$soffMa mA" else "-$soffMa mA",
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                // Deep sleep & Held awake Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                 // Metrics Row: Deep sleep & Held awake for Discharging, or Voltage & Temp for Charging
                                 if (!isChargingMode) {
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.spacedBy(12.dp),
                                     ) {
                                         val dsText = if (deepSleepMah > 0) "$deepSleepMah mAh (%.1f%%)".format(deepSleepPct) else "%.1f%%".format(deepSleepPct)
                                         SessionSubmetricRowCard(
                                             icon = Icons.Rounded.Bedtime,
                                             label = stringResource(R.string.battery_insight_deep_sleep),
                                             duration = formatTime(stats.deepSleepTime),
                                             subtext = dsText,
                                             iconTint = Color(0xFFB39DDB),
                                             modifier = Modifier.weight(1f),
                                         )
                                         val awText = if (awakeMah > 0) "$awakeMah mAh (%.1f%%)".format(awakePct) else "%.1f%%".format(awakePct)
                                         SessionSubmetricRowCard(
                                             icon = Icons.Rounded.Warning,
                                             label = stringResource(R.string.battery_insight_held_awake),
                                             duration = formatTime(stats.awakeTime),
                                             subtext = awText,
                                             iconTint = Color(0xFFFFB74D),
                                             modifier = Modifier.weight(1f),
                                         )
                                     }
                                 } else {
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.spacedBy(12.dp),
                                     ) {
                                         val voltStr = if (stats.voltage > 0) "%.2f V".format(stats.voltage / 1000f) else "-- V"
                                         val tempStr = if (stats.temp > 0) "%.1f°C".format(stats.temp / 10f) else "--°C"
                                         SessionSubmetricRowCard(
                                             icon = Icons.Rounded.Bolt,
                                             label = stringResource(R.string.battery_insight_voltage),
                                             duration = voltStr,
                                             subtext = if (stats.isCharging) stringResource(R.string.battery_insight_notif_charging) else stringResource(R.string.battery_insight_notif_discharging),
                                             iconTint = MaterialTheme.colorScheme.primary,
                                             modifier = Modifier.weight(1f),
                                         )
                                         SessionSubmetricRowCard(
                                             icon = Icons.Rounded.DeviceThermostat,
                                             label = stringResource(R.string.battery_insight_temp),
                                             duration = tempStr,
                                             subtext = stats.health ?: "Good",
                                             iconTint = Color(0xFFFFB74D),
                                             modifier = Modifier.weight(1f),
                                         )
                                     }
                                 }}
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSectionCard(
    icon: ImageVector,
    title: String,
    duration: String,
    iconTint: Color,
    showInfo: Boolean = false,
    subCards: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (showInfo) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Subcards Content
        subCards()
    }
}

@Composable
private fun SessionMetricSubCard(
    label: String,
    mainValue: String,
    subValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val cardBg = if (isDark) {
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

    Surface(
        modifier = modifier.shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(22.dp),
            spotColor = accentColor.copy(alpha = 0.12f),
            ambientColor = Color.Black.copy(alpha = 0.06f),
        ),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (isDark) 0.08f else 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .background(cardBg)
                .padding(16.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = mainValue,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionSubmetricRowCard(
    icon: ImageVector,
    label: String,
    duration: String,
    subtext: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val cardBg = if (isDark) {
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

    Surface(
        modifier = modifier.shadow(
            elevation = 4.dp,
            shape = RoundedCornerShape(22.dp),
            spotColor = iconTint.copy(alpha = 0.12f),
            ambientColor = Color.Black.copy(alpha = 0.06f),
        ),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (isDark) 0.08f else 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .background(cardBg)
                .padding(16.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = duration,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
