/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.R

@Composable
fun BatteryHealthCard(
    stats: BatteryInsightStats,
    modifier: Modifier = Modifier,
    onOpenHealth: () -> Unit,
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
                spotColor = Color(0xFFE57373).copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.08f),
            ),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = borderStroke,
    ) {
        Box(
            modifier = Modifier
                .background(glassBg)
                .clickable { onOpenHealth() }
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE57373).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.battery_insight_health),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "%.1f%%".format(stats.healthPercent),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HealthMetric(
                        label = stringResource(R.string.battery_insight_capacity),
                        value = stringResource(
                            R.string.battery_insight_capacity_ratio,
                            stats.currentCapacity,
                            stats.totalCapacity,
                        ),
                    )
                    HealthMetric(
                        label = stringResource(R.string.battery_insight_cycle_count),
                        value = stats.cycleCount.toString(),
                    )
                    HealthMetric(
                        label = stringResource(R.string.battery_insight_status),
                        value = stats.health ?: stringResource(R.string.battery_insight_unknown),
                    )
                }

                // Custom ROM Battery Health Bar
                CustomRomBatteryBar(
                    stats = stats,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
fun CustomRomBatteryBar(
    stats: BatteryInsightStats,
    modifier: Modifier = Modifier,
) {
    val totalCap = if (stats.totalCapacity > 0) stats.totalCapacity else 5000
    val currentCap = if (stats.currentCapacity in 1..totalCap) stats.currentCapacity else totalCap
    val healthRatio = (currentCap.toFloat() / totalCap.toFloat()).coerceIn(0.05f, 1f)
    val degradedMah = (totalCap - currentCap).coerceAtLeast(0)
    val degradedRatio = (degradedMah.toFloat() / totalCap.toFloat()).coerceIn(0f, 0.95f)

    val animatedHealthRatio by animateFloatAsState(
        targetValue = healthRatio,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "battery_health_bar_anim",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trackBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val deadZoneColor = Color(0xFFE57373).copy(alpha = 0.70f)
    val deadZoneBg = Color(0xFFE57373).copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(trackBg),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height
                val activeWidth = (width * animatedHealthRatio).coerceIn(0f, width)

                // 1. Usable Capacity Bar (Monet Gradient)
                if (activeWidth > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(primaryColor, tertiaryColor),
                            startX = 0f,
                            endX = width,
                        ),
                        size = Size(activeWidth, height),
                    )
                }

                // 2. Degraded / Dead Capacity Zone (Hatched stripes seamlessly meeting active bar)
                if (activeWidth < width) {
                    val deadStart = activeWidth
                    val deadWidth = width - deadStart

                    // Background tint for dead zone
                    drawRect(
                        color = deadZoneBg,
                        topLeft = Offset(deadStart, 0f),
                        size = Size(deadWidth, height),
                    )

                    // Clip to the dead zone bounds and draw 45-degree diagonal stripes
                    clipRect(left = deadStart, top = 0f, right = width, bottom = height) {
                        val stripeSpacing = 7.dp.toPx()
                        val strokeWidth = 2.dp.toPx()
                        var x = deadStart - height
                        while (x < width + height) {
                            drawLine(
                                color = deadZoneColor,
                                start = Offset(x, height),
                                end = Offset(x + height, 0f),
                                strokeWidth = strokeWidth,
                            )
                            x += stripeSpacing
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(primaryColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.battery_insight_usable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "$currentCap mAh (${"%.1f".format(healthRatio * 100)}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (degradedMah > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE57373)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.battery_insight_degraded_capacity),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "-$degradedMah mAh (${"%.1f".format(degradedRatio * 100)}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE57373),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.battery_insight_optimal_capacity),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryHealthDialog(
    stats: BatteryInsightStats,
    onDismiss: () -> Unit,
) {
    val cyclePenalty = ((stats.cycleCount / 800f) * 20f).coerceIn(0f, 20f)
    val cycleHealth = if (stats.cycleHealth > 0f) {
        stats.cycleHealth
    } else {
        (100f - cyclePenalty).coerceIn(0f, 100f)
    }
    val weighted = stats.healthPercent

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.battery_insight_close))
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFE57373))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.battery_insight_health_details))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CustomRomBatteryBar(stats = stats, modifier = Modifier.padding(bottom = 6.dp))
                Text(stringResource(R.string.battery_insight_design_capacity, stats.totalCapacity))
                Text(stringResource(R.string.battery_insight_full_charge_capacity, stats.currentCapacity))
                Text(
                    stringResource(
                        R.string.battery_insight_capacity_health,
                        "%.1f%%".format(stats.capacityHealth),
                    ),
                )
                Text(stringResource(R.string.battery_insight_cycle_count) + ": ${stats.cycleCount}")
                Text(
                    stringResource(
                        R.string.battery_insight_cycle_health,
                        "%.1f%%".format(cycleHealth),
                    ),
                )
                Text(stringResource(R.string.battery_insight_final_health_formula))
                Text(
                    stringResource(
                        R.string.battery_insight_final_health,
                        "%.1f%%".format(weighted),
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}

@Composable
private fun HealthMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DialogRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
