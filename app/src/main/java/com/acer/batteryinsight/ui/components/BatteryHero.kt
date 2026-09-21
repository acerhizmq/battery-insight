/*
 * SPDX-FileCopyrightText: 2026 kenway214 & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun BatteryHero(
    stats: BatteryInsightStats,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val displayLevel = if (stats.level in 1..100) stats.level else 100

    val animatedLevel by animateFloatAsState(
        targetValue = displayLevel.toFloat(),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "hero_level_anim",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    val infiniteTransition = rememberInfiniteTransition(label = "hero_particles_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "anim_phase",
    )

    val glassCardBg = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                surfaceContainer.copy(alpha = 0.90f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.95f),
                surfaceContainer.copy(alpha = 0.95f),
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
                spotColor = primaryColor.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.08f),
            ),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = borderStroke,
    ) {
        Box(
            modifier = Modifier
                .background(glassCardBg)
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Circular Battery Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(205.dp),
                ) {
                    Canvas(modifier = Modifier.size(190.dp)) {
                        val strokeWidth = 14.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val radius = diameter / 2f
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(diameter, diameter)

                        // Track
                        drawArc(
                            color = primaryColor.copy(alpha = if (isDark) 0.15f else 0.10f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )

                        // Active Progress Arc
                        val sweep = (animatedLevel / 100f) * 270f
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(primaryColor, tertiaryColor, primaryColor),
                            ),
                            startAngle = 135f,
                            sweepAngle = sweep.coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )

                        val particleCount = 20
                        val tipAngleDeg = 135f + sweep
                        val emptySweep = (270f - sweep).coerceAtLeast(0f)

                        if (stats.isCharging) {
                            // CHARGING: Flows from the EMPTY part into the FILLED part (resmen dolduruyormuş gibi)
                            // Droplets move along the empty track from 405° towards tipAngleDeg
                            val distance = if (emptySweep > 10f) emptySweep else 270f
                            val startAngle = 405f

                            for (i in 0 until particleCount) {
                                val u = (animPhase + i.toFloat() / particleCount) % 1f
                                val angleDeg = if (emptySweep > 10f) {
                                    startAngle - u * distance
                                } else {
                                    135f + u * 270f
                                }
                                val angleRad = Math.toRadians(angleDeg.toDouble())

                                // Confined strictly INSIDE the 14dp bar
                                val px = centerOffset.x + radius * cos(angleRad).toFloat()
                                val py = centerOffset.y + radius * sin(angleRad).toFloat()

                                // Seamless sine envelope: zero at boundaries, peaks at center, zero jump on loop
                                val rawSin = sin(u * PI.toFloat())
                                val alpha = if (rawSin > 0f) rawSin.coerceIn(0f, 1f).pow(1.4f) else 0f
                                val pRadius = (3.0f + (i % 3) * 0.45f).dp.toPx()

                                val color = when (i % 3) {
                                    0 -> primaryColor
                                    1 -> tertiaryColor
                                    else -> Color.White
                                }

                                drawCircle(
                                    color = color.copy(alpha = (alpha * 0.35f).coerceIn(0f, 1f)),
                                    radius = pRadius * 1.35f,
                                    center = Offset(px, py),
                                )
                                drawCircle(
                                    color = color.copy(alpha = (alpha * 0.95f).coerceIn(0f, 1f)),
                                    radius = pRadius,
                                    center = Offset(px, py),
                                )
                            }

                            // Tip fusion halo: smooth pulsing energy at the boundary where the flow enters the full bar
                            val tipRad = Math.toRadians(tipAngleDeg.toDouble())
                            val tipX = centerOffset.x + radius * cos(tipRad).toFloat()
                            val tipY = centerOffset.y + radius * sin(tipRad).toFloat()
                            val tipPulse = (sin(animPhase * 2f * PI.toFloat()) * 0.5f + 0.5f).coerceIn(0f, 1f)
                            drawCircle(
                                color = Color.White.copy(alpha = (tipPulse * 0.5f).coerceIn(0f, 1f)),
                                radius = strokeWidth * 0.36f,
                                center = Offset(tipX, tipY),
                            )
                            drawCircle(
                                color = primaryColor.copy(alpha = (tipPulse * 0.7f).coerceIn(0f, 1f)),
                                radius = strokeWidth * 0.48f,
                                center = Offset(tipX, tipY),
                            )
                        } else {
                            // DISCHARGING: Flows from the FILLED part into the EMPTY part (doludan boşa doğru)
                            // Droplets start at tipAngleDeg and move into the empty track towards 405°
                            val distance = if (emptySweep > 10f) emptySweep else 270f

                            for (i in 0 until particleCount) {
                                val u = (animPhase + i.toFloat() / particleCount) % 1f
                                val angleDeg = if (emptySweep > 10f) {
                                    tipAngleDeg + u * distance
                                } else {
                                    135f + u * 270f
                                }
                                val angleRad = Math.toRadians(angleDeg.toDouble())

                                // Confined strictly INSIDE the 14dp bar
                                val px = centerOffset.x + radius * cos(angleRad).toFloat()
                                val py = centerOffset.y + radius * sin(angleRad).toFloat()

                                // Seamless sine envelope: zero at boundaries, zero jump on loop
                                val rawSin = sin(u * PI.toFloat())
                                val alpha = if (rawSin > 0f) (rawSin.coerceIn(0f, 1f).pow(1.4f) * (1f - u * 0.3f)).coerceIn(0f, 1f) else 0f
                                val pRadius = (3.0f + (i % 3) * 0.45f).dp.toPx() * (1f - u * 0.2f)

                                val color = when (i % 3) {
                                    0 -> primaryColor
                                    1 -> tertiaryColor
                                    else -> Color.White.copy(alpha = 0.9f)
                                }

                                drawCircle(
                                    color = color.copy(alpha = (alpha * 0.30f).coerceIn(0f, 1f)),
                                    radius = pRadius * 1.30f,
                                    center = Offset(px, py),
                                )
                                drawCircle(
                                    color = color.copy(alpha = (alpha * 0.85f).coerceIn(0f, 1f)),
                                    radius = pRadius,
                                    center = Offset(px, py),
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = "${displayLevel}%",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Remaining mAh / Health Capacity mAh (Design Capacity mAh)
                        val designCap = if (stats.totalCapacity > 0) stats.totalCapacity else 5000
                        val healthCap = if (stats.currentCapacity > 0) stats.currentCapacity else designCap
                        val currentMah = ((displayLevel.toFloat() / 100f) * healthCap).toInt()

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
                        ) {
                            Text(
                                text = "%,d / %,d mAh".format(currentMah, healthCap),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "(%,d mAh)".format(designCap),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }

                        // Compact Liquid Glass Status Badge
                        val statusText = if (stats.isCharging) {
                            if (displayLevel >= 100) {
                                stringResource(R.string.battery_insight_notif_full)
                            } else {
                                stringResource(R.string.battery_insight_notif_charging)
                            }
                        } else {
                            stringResource(R.string.battery_insight_notif_discharging)
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (stats.isCharging) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = if (stats.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // 3 Liquid Glass Metric Chips
                val sign = if (stats.currentNow > 0) "+" else if (stats.currentNow < 0) "-" else ""
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricChip(
                        icon = Icons.Rounded.ElectricMeter,
                        label = stringResource(R.string.battery_insight_current),
                        value = "${sign}${abs(stats.currentNow)} mA",
                        modifier = Modifier.weight(1f),
                    )
                    MetricChip(
                        icon = Icons.Rounded.DeviceThermostat,
                        label = stringResource(R.string.battery_insight_temp),
                        value = "%.1f°C".format(stats.temp / 10f),
                        modifier = Modifier.weight(1f),
                    )
                    MetricChip(
                        icon = Icons.Rounded.Power,
                        label = stringResource(R.string.battery_insight_power),
                        value = "%.2f W".format(stats.powerWatts),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.55f else 0.75f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (isDark) 0.12f else 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
