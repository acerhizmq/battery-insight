/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acer.batteryinsight.model.BatteryInsightHistoryBucket
import com.acer.batteryinsight.R
import com.acer.batteryinsight.ui.utils.formatTime

@Composable
fun HistoryTimeline(
    history: List<BatteryInsightHistoryBucket>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        history.forEachIndexed { index, bucket ->
            HistoryTimelineItem(
                bucket = bucket,
                isLast = index == history.lastIndex,
            )
        }
    }
}

@Composable
fun HistoryTimelineItem(
    bucket: BatteryInsightHistoryBucket,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val drainColor = Color(0xFFE57373)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val hourlyDrain = if (bucket.hourlyDrainPercent > 0) bucket.hourlyDrainPercent else bucket.drainPercent
    val drainFraction = (hourlyDrain / 15f).coerceIn(0f, 1f)
    val sotTime = if (bucket.hourlyScreenOnMs > 0L) bucket.hourlyScreenOnMs else bucket.screenOnMs

    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val glassBg = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
            )
        )
    }

    val borderStroke = BorderStroke(
        width = 0.5.dp,
        color = Color.White.copy(alpha = if (isDark) 0.08f else 0.25f),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier
                .width(52.dp)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                stringResource(R.string.battery_insight_hour_label, bucket.hour),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(lineColor.copy(alpha = 0.5f)),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                ),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = borderStroke,
        ) {
            Box(
                modifier = Modifier
                    .background(glassBg)
                    .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.battery_insight_sot_short, formatTime(sotTime)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.battery_insight_hourly_drain, hourlyDrain),
                            style = MaterialTheme.typography.titleMedium,
                            color = drainColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { drainFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = drainColor,
                        trackColor = drainColor.copy(alpha = 0.15f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            stringResource(R.string.battery_insight_total_session_drain, bucket.drainPercent),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
