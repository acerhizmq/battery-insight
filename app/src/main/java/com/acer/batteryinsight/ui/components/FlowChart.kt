/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.acer.batteryinsight.model.BatteryInsightFlowSample
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.R
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FlowChart(
    flow: List<BatteryInsightFlowSample>,
    stats: BatteryInsightStats,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val displaySamples = flow.takeLast(60)
    val maxVal = displaySamples.maxOfOrNull { abs(it.current) }
        ?.coerceAtLeast(100)
        ?.toFloat()
        ?.times(1.1f)
        ?: 100f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = stringResource(
                        R.string.battery_insight_current_ma,
                        abs(stats.currentNow),
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.battery_insight_power_watts_mv,
                        stats.powerWatts,
                        stats.voltage,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(R.string.battery_insight_samples, flow.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (displaySamples.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.battery_insight_no_data),
                    color = labelColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                val gridLines = 4
                for (i in 0 until gridLines) {
                    val value = (maxVal * (gridLines - 1 - i) / (gridLines - 1)).roundToInt()
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val gridLines = 5
                    for (i in 0 until gridLines) {
                        val y = height * i / (gridLines - 1)
                        drawLine(
                            gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    if (displaySamples.isNotEmpty()) {
                        val points = displaySamples.mapIndexed { index, sample ->
                            val x = if (displaySamples.size > 1) {
                                width * index / (displaySamples.size - 1)
                            } else {
                                width / 2
                            }
                            val y = height - (abs(sample.current) / maxVal * height)
                            Offset(x, y)
                        }

                        if (points.size > 1) {
                            val path = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    val p1 = points[i - 1]
                                    val p2 = points[i]
                                    val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                    val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                    cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                }
                            }

                            val fillPath = Path().apply {
                                addPath(path)
                                lineTo(points.last().x, height)
                                lineTo(points.first().x, height)
                                close()
                            }
                            drawPath(
                                fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.35f),
                                        primaryColor.copy(alpha = 0.05f),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                            drawPath(
                                path,
                                color = primaryColor,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )

                            val lastPoint = points.last()
                            drawCircle(primaryColor, radius = 6.dp.toPx(), center = lastPoint)
                            drawCircle(
                                primaryColor.copy(alpha = 0.25f),
                                radius = 11.dp.toPx(),
                                center = lastPoint,
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.battery_insight_chart_now),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
                Text(
                    stringResource(R.string.battery_insight_chart_2m_ago),
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
            }
        }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = gridColor)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FlowMiniStat(
                label = stringResource(R.string.battery_insight_average),
                value = stringResource(R.string.battery_insight_current_ma, stats.avgCurrent),
            )
            VerticalDivider(
                modifier = Modifier.height(28.dp),
                color = gridColor,
            )
            FlowMiniStat(
                label = stringResource(R.string.battery_insight_minimum),
                value = stringResource(R.string.battery_insight_current_ma, stats.minCurrent),
            )
            VerticalDivider(
                modifier = Modifier.height(28.dp),
                color = gridColor,
            )
            FlowMiniStat(
                label = stringResource(R.string.battery_insight_maximum),
                value = stringResource(R.string.battery_insight_current_ma, stats.maxCurrent),
            )
        }
    }
}

@Composable
private fun FlowMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
