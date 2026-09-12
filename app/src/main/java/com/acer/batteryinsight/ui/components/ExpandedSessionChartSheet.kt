/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acer.batteryinsight.R
import com.acer.batteryinsight.model.BatteryInsightFlowSample
import com.acer.batteryinsight.model.BatteryInsightHistoryBucket
import com.acer.batteryinsight.model.BatteryInsightStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedSessionChartSheet(
    stats: BatteryInsightStats,
    activeFlow: List<BatteryInsightFlowSample>,
    activeHistory: List<BatteryInsightHistoryBucket>,
    lastChargeFlow: List<BatteryInsightFlowSample>,
    lastChargeHistory: List<BatteryInsightHistoryBucket>,
    lastDischargeFlow: List<BatteryInsightFlowSample>,
    lastDischargeHistory: List<BatteryInsightHistoryBucket>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.50f),
        dragHandle = null,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize(),
    ) {
        ExpandedSessionChartContent(
            stats = stats,
            activeFlow = activeFlow,
            activeHistory = activeHistory,
            lastChargeFlow = lastChargeFlow,
            lastChargeHistory = lastChargeHistory,
            lastDischargeFlow = lastDischargeFlow,
            lastDischargeHistory = lastDischargeHistory,
            onClose = onDismiss,
            isDark = isDark,
        )
    }
}

@Composable
fun ExpandedSessionChartContent(
    stats: BatteryInsightStats,
    activeFlow: List<BatteryInsightFlowSample>,
    activeHistory: List<BatteryInsightHistoryBucket>,
    lastChargeFlow: List<BatteryInsightFlowSample>,
    lastChargeHistory: List<BatteryInsightHistoryBucket>,
    lastDischargeFlow: List<BatteryInsightFlowSample>,
    lastDischargeHistory: List<BatteryInsightHistoryBucket>,
    onClose: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedSessionTab by remember { mutableIntStateOf(0) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val surfaceBg = MaterialTheme.colorScheme.background
    val glassBorder = BorderStroke(
        width = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.30f else 0.50f),
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(top = maxOf(16.dp, topInset)),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = surfaceBg,
        border = glassBorder,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.battery_insight_expanded_chart_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.battery_insight_expanded_chart_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.battery_insight_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Session Selector Tabs (Pills)
                val sessionLabels = listOf(
                    stringResource(R.string.battery_insight_session_active),
                    stringResource(R.string.battery_insight_session_last_charge),
                    stringResource(R.string.battery_insight_session_last_discharge),
                )
                Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    BatteryInsightChipSelector(
                        selectedIndex = selectedSessionTab,
                        labels = sessionLabels,
                        onSelected = { selectedSessionTab = it },
                    )
                }

                // Data resolution for selected session
                val (currentFlow, currentHistory) = when (selectedSessionTab) {
                    1 -> Pair(
                        if (lastChargeFlow.isNotEmpty()) lastChargeFlow else activeFlow.filter { it.isCharging },
                        lastChargeHistory,
                    )
                    2 -> Pair(
                        if (lastDischargeFlow.isNotEmpty()) lastDischargeFlow else activeFlow.filter { !it.isCharging },
                        lastDischargeHistory,
                    )
                    else -> Pair(activeFlow, activeHistory)
                }

                // Scrollable Body
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 10.dp,
                        bottom = bottomInset + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Interactive Scrubbing Chart Card
                    item(key = "scrub_chart_$selectedSessionTab") {
                        InteractiveScrubChartCard(
                            samples = currentFlow,
                            stats = stats,
                            isDark = isDark,
                        )
                    }

                    // 2. Hourly Breakdown Header
                    item(key = "hourly_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.battery_insight_hourly_breakdown_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // 3. Hourly Items or Empty State
                    if (currentHistory.isEmpty()) {
                        item(key = "hourly_empty") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.battery_insight_hourly_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(currentHistory.reversed(), key = { "${it.epochDay}_${it.hour}" }) { bucket ->
                            HourlyBucketCard(bucket = bucket, isDark = isDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveScrubChartCard(
    samples: List<BatteryInsightFlowSample>,
    stats: BatteryInsightStats,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Take up to 120 samples for maximum detail
    val displaySamples = samples.takeLast(120)
    val maxVal = displaySamples.maxOfOrNull { abs(it.current) }
        ?.coerceAtLeast(100)
        ?.toFloat()
        ?.times(1.12f)
        ?: 100f

    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }
    val scrubbedSample = scrubbedIndex?.let { displaySamples.getOrNull(it) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = primaryColor.copy(alpha = 0.12f),
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.65f else 0.85f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (isDark) 0.10f else 0.25f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Scrubbing HUD or Summary
            if (scrubbedSample != null) {
                // Interactive Scrub Info Floating Bar
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(scrubbedSample.timestamp))
                val sign = if (scrubbedSample.current > 0) "+" else if (scrubbedSample.current < 0) "-" else ""
                val currentText = "${sign}${abs(scrubbedSample.current)} mA"
                val currColor = if (scrubbedSample.isCharging) primaryColor else tertiaryColor

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = currentText,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = currColor,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (scrubbedSample.level > 0) {
                                Text(
                                    text = "%${scrubbedSample.level}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            if (scrubbedSample.voltage > 0) {
                                Text(
                                    text = "${scrubbedSample.voltage} mV",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                )
                            }
                            if (scrubbedSample.temp > 0) {
                                Text(
                                    text = "%.1f°C".format(scrubbedSample.temp / 10f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            } else {
                // Default Idle Summary Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        val currMa = if (samples.isNotEmpty()) samples.last().current else stats.currentNow
                        val sign = if (currMa > 0) "+" else if (currMa < 0) "-" else ""
                        Text(
                            text = "${sign}${abs(currMa)} mA",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TouchApp,
                                contentDescription = null,
                                tint = labelColor,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = stringResource(R.string.battery_insight_expand_chart_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = stringResource(R.string.battery_insight_samples, displaySamples.size),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Interactive Canvas Area
            if (displaySamples.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
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
                        .height(210.dp),
                ) {
                    // Y-Axis Labels
                    Column(
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val gridLines = 4
                        for (i in 0 until gridLines) {
                            val value = (maxVal * (gridLines - 1 - i) / (gridLines - 1)).roundToInt()
                            Text(
                                text = "$value",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = labelColor,
                            )
                        }
                    }

                    // Chart Canvas with Touch Detector
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(displaySamples.size) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        scrubbedIndex = calculateNearestSample(offset.x, size.width, displaySamples.size)
                                    },
                                    onDrag = { change, _ ->
                                        scrubbedIndex = calculateNearestSample(change.position.x, size.width, displaySamples.size)
                                    },
                                    onDragEnd = { scrubbedIndex = null },
                                    onDragCancel = { scrubbedIndex = null },
                                )
                            }
                            .pointerInput(displaySamples.size) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        scrubbedIndex = calculateNearestSample(offset.x, size.width, displaySamples.size)
                                        tryAwaitRelease()
                                        scrubbedIndex = null
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height

                            // Horizontal Grid lines
                            val gridLines = 4
                            for (i in 0 until gridLines) {
                                val y = height * i / (gridLines - 1)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1.dp.toPx(),
                                )
                            }

                            val points = displaySamples.mapIndexed { index, sample ->
                                val x = if (displaySamples.size > 1) {
                                    width * index / (displaySamples.size - 1)
                                } else {
                                    width / 2f
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
                                            primaryColor.copy(alpha = 0.38f),
                                            primaryColor.copy(alpha = 0.06f),
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

                                // Draw Touch Scrubber Indicator
                                scrubbedIndex?.let { idx ->
                                    if (idx in points.indices) {
                                        val pt = points[idx]
                                        // Vertical scrubber guideline
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.75f),
                                            start = Offset(pt.x, 0f),
                                            end = Offset(pt.x, height),
                                            strokeWidth = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                                        )
                                        // Scrub Dot
                                        drawCircle(
                                            color = Color.White,
                                            radius = 6.5.dp.toPx(),
                                            center = pt,
                                        )
                                        drawCircle(
                                            color = primaryColor,
                                            radius = 4.5.dp.toPx(),
                                            center = pt,
                                        )
                                        drawCircle(
                                            color = primaryColor.copy(alpha = 0.35f),
                                            radius = 12.dp.toPx(),
                                            center = pt,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = gridColor)
            Spacer(Modifier.height(10.dp))

            // Quick Stats Row
            val avg = if (displaySamples.isNotEmpty()) {
                displaySamples.map { abs(it.current) }.average().roundToInt()
            } else stats.avgCurrent
            val min = if (displaySamples.isNotEmpty()) {
                displaySamples.minOf { abs(it.current) }
            } else stats.minCurrent
            val max = if (displaySamples.isNotEmpty()) {
                displaySamples.maxOf { abs(it.current) }
            } else stats.maxCurrent

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ExpandedChartMiniStat(
                    label = stringResource(R.string.battery_insight_minimum),
                    value = "$min mA",
                )
                ExpandedChartMiniStat(
                    label = stringResource(R.string.battery_insight_average),
                    value = "$avg mA",
                )
                ExpandedChartMiniStat(
                    label = stringResource(R.string.battery_insight_maximum),
                    value = "$max mA",
                )
            }
        }
    }
}

@Composable
private fun ExpandedChartMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HourlyBucketCard(
    bucket: BatteryInsightHistoryBucket,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val nextHour = (bucket.hour + 1) % 24
    val timeRangeStr = "%02d:00 - %02d:00".format(bucket.hour, nextHour)

    val isChargeGain = bucket.hourlyDrainPercent < 0
    val drainPercent = abs(bucket.hourlyDrainPercent)
    val drainColor = if (isChargeGain) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.55f else 0.75f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = timeRangeStr,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = drainColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = if (isChargeGain) "+$drainPercent%" else "-$drainPercent%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = drainColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Metrics Grid (Current min/avg/max, Temp, SOT)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Min / Avg / Max Current
                Column {
                    Text(
                        text = stringResource(R.string.battery_insight_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${bucket.minCurrent} / ${bucket.avgCurrent} / ${bucket.maxCurrent} mA",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Temp
                if (bucket.temp > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.battery_insight_temp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "%.1f°C".format(bucket.temp / 10f),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Screen On Time (SOT)
                val sotMin = bucket.hourlyScreenOnMs / 60000L
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.battery_insight_sot),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${sotMin} dk",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun calculateNearestSample(x: Float, width: Int, sampleCount: Int): Int? {
    if (sampleCount <= 0 || width <= 0) return null
    val ratio = (x / width).coerceIn(0f, 1f)
    return (ratio * (sampleCount - 1)).roundToInt().coerceIn(0, sampleCount - 1)
}
