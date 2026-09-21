/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BatteryInsightChipSelector(
    selectedIndex: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val slotWidth = (maxWidth - 8.dp) / labels.size
        val textStyle = rememberFittingTextStyle(
            labels = labels,
            baseStyle = MaterialTheme.typography.labelMedium,
            slotWidth = slotWidth,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedIndex == index,
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    label = {
                        Text(
                            text = label,
                            style = textStyle,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberFittingTextStyle(
    labels: List<String>,
    baseStyle: TextStyle,
    slotWidth: Dp,
    minFontSize: TextUnit = 9.sp,
): TextStyle {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slotWidthPx = with(density) { slotWidth.toPx() }
    return remember(labels, slotWidthPx, baseStyle, minFontSize) {
        var fittedSize = baseStyle.fontSize
        for (label in labels) {
            var size = baseStyle.fontSize
            while (size > minFontSize) {
                val result = textMeasurer.measure(
                    text = label,
                    style = baseStyle.copy(fontSize = size),
                    maxLines = 1,
                    softWrap = false,
                )
                if (result.size.width <= slotWidthPx) {
                    break
                }
                size = (size.value - 0.5f).sp
            }
            if (size < fittedSize) {
                fittedSize = size
            }
        }
        baseStyle.copy(fontSize = fittedSize)
    }
}
