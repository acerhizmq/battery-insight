/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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

@Composable
fun RealtimeBubblesSection(
    stats: BatteryInsightStats,
    onOpenSessionDetails: () -> Unit,
    onOpenHealthDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHealthExpanded by remember { mutableStateOf(false) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (isHealthExpanded) 180f else 0f,
        animationSpec = spring(stiffness = 500f),
        label = "health_arrow_rotation",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Dual Action Bubbles Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Pil Sağlığı (Battery Health Bubble - Expandable)
            ActionBubbleCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.battery_insight_health),
                value = "%.1f%%".format(stats.healthPercent),
                subtext = if (isHealthExpanded) stringResource(R.string.battery_insight_health_tap_collapse) else stringResource(R.string.battery_insight_health_tap_expand),
                icon = Icons.Rounded.Favorite,
                accentColor = Color(0xFFE57373),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotation),
                    )
                },
                onClick = { isHealthExpanded = !isHealthExpanded },
            )

            // 2. Oturum Detayları (Session Details Bubble - Launches Sheet)
            ActionBubbleCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.battery_insight_session_details),
                value = formatTime(stats.screenOnTime),
                subtext = stringResource(R.string.battery_insight_consumption_analysis),
                icon = Icons.Rounded.QueryStats,
                accentColor = MaterialTheme.colorScheme.primary,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                },
                onClick = onOpenSessionDetails,
            )
        }

        // Smooth Expandable Health Breakdown Accordion
        AnimatedVisibility(
            visible = isHealthExpanded,
            enter = expandVertically(animationSpec = spring(stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(stiffness = 400f)) + fadeOut(),
        ) {
            ExpandedHealthPanel(
                stats = stats,
                onOpenFullDialog = onOpenHealthDialog,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActionBubbleCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
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
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = accentColor.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.08f),
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = borderStroke,
    ) {
        Box(
            modifier = Modifier
                .background(glassBg)
                .clickable { onClick() }
                .padding(16.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    trailingIcon?.invoke()
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExpandedHealthPanel(
    stats: BatteryInsightStats,
    onOpenFullDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val glassBg = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f),
            )
        )
    }

    val borderStroke = BorderStroke(
        width = 0.5.dp,
        color = Color.White.copy(alpha = if (isDark) 0.08f else 0.25f),
    )

    Surface(
        modifier = modifier.shadow(
            elevation = 8.dp,
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
                .padding(18.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.battery_insight_health_details_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = onOpenFullDialog) {
                        Text(
                            text = stringResource(R.string.battery_insight_health_formula_report),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))

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

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HealthMetric(
                        label = stringResource(R.string.battery_insight_capacity_health_label),
                        value = "%.1f%%".format(stats.capacityHealth),
                    )
                    HealthMetric(
                        label = stringResource(R.string.battery_insight_cycle_health_label),
                        value = "%.1f%%".format(stats.cycleHealth),
                    )
                    HealthMetric(
                        label = stringResource(R.string.battery_insight_temp),
                        value = "%.1f °C".format(stats.temp / 10.0),
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
