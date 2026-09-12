/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.acer.batteryinsight.model.BatteryInsightFlowSample
import com.acer.batteryinsight.model.BatteryInsightStats
import com.acer.batteryinsight.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip

@Composable
fun RealtimeChartSection(
    stats: BatteryInsightStats,
    flow: List<BatteryInsightFlowSample>,
    modifier: Modifier = Modifier,
    onExpandChart: (() -> Unit)? = null,
) {
    var selectedChartTab by remember(stats.isCharging) {
        mutableIntStateOf(if (stats.isCharging) 0 else 1)
    }

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
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.08f),
            ),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = borderStroke,
    ) {
        Box(
            modifier = Modifier
                .background(glassBg)
                .padding(18.dp)
        ) {
            Column {
                val chartLabels = listOf(
                    stringResource(R.string.battery_insight_charging_tab),
                    stringResource(R.string.battery_insight_discharging_tab),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        BatteryInsightChipSelector(
                            selectedIndex = selectedChartTab,
                            labels = chartLabels,
                            onSelected = { selectedChartTab = it },
                        )
                    }
                    if (onExpandChart != null) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = onExpandChart,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInFull,
                                contentDescription = stringResource(R.string.battery_insight_expanded_chart_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                val filteredFlow = flow.filter { it.isCharging == (selectedChartTab == 0) }
                Box(modifier = if (onExpandChart != null) Modifier.clickable { onExpandChart() } else Modifier) {
                    FlowChart(filteredFlow, stats)
                }
            }
        }
    }
}
