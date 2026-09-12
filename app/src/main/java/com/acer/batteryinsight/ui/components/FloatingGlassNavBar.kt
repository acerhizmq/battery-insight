/*
 * SPDX-FileCopyrightText: 2026 kenway214 & RisingOS Revived
 * SPDX-License-Identifier: Apache-2.0
 */

package com.acer.batteryinsight.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acer.batteryinsight.R

data class LiquidNavItem(
    val titleRes: Int,
    val icon: ImageVector,
)

private val LIQUID_NAV_ITEMS = listOf(
    LiquidNavItem(R.string.battery_insight_realtime, Icons.Rounded.Home),
    LiquidNavItem(R.string.battery_insight_history, Icons.Rounded.Timeline),
    LiquidNavItem(R.string.battery_insight_apps, Icons.Rounded.Widgets),
    LiquidNavItem(R.string.battery_insight_settings_tab, Icons.Rounded.Tune),
)

@Composable
fun FloatingGlassNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val glassBg = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x9928282C),
                Color(0xBB1A1A1E),
                Color(0xDD101014),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xEEFFFFFF),
                Color(0xDDF0F4F8),
                Color(0xEEF0F4F8),
            )
        )
    }

    val borderStroke = BorderStroke(
        width = 1.2.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                if (isDark) Color(0x66FFE082) else Color(0x88FFE082),
                if (isDark) Color(0x33FFFFFF) else Color(0x55FFFFFF),
                if (isDark) Color(0x10FFFFFF) else Color(0x20FFFFFF),
            )
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = CircleShape,
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.2f),
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                ),
            shape = CircleShape,
            color = Color.Transparent,
            border = borderStroke,
        ) {
            Box(
                modifier = Modifier
                    .background(glassBg)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LIQUID_NAV_ITEMS.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index
                        val label = stringResource(item.titleRes)
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        val targetScale = when {
                            isPressed -> 0.88f
                            isSelected -> 1.04f
                            else -> 0.96f
                        }

                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "nav_scale_$index",
                        )

                        val pillBgColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                if (isDark) Color(0x40FFFFFF) else Color(0x60FFFFFF)
                            } else {
                                Color.Transparent
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "nav_pill_$index",
                        )

                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                if (isDark) Color.White else MaterialTheme.colorScheme.primary
                            } else {
                                if (isDark) Color(0x99E0E0E0) else Color(0x99424242)
                            },
                            label = "nav_icon_$index",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .scale(animatedScale)
                                .clip(CircleShape)
                                .background(pillBgColor)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onTabSelected(index) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = label,
                                    tint = iconColor,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = iconColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
