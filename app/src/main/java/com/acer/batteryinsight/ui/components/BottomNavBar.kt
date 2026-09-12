/*
 * SPDX-FileCopyrightText: 2026 kenway214 & KernelSU Authors & RisingOS Revived
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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

data class BottomNavDestination(
    val titleRes: Int,
    val icon: ImageVector,
)

val NAV_DESTINATIONS = listOf(
    BottomNavDestination(R.string.battery_insight_realtime, Icons.Rounded.Home),
    BottomNavDestination(R.string.battery_insight_history, Icons.Rounded.Timeline),
    BottomNavDestination(R.string.battery_insight_apps, Icons.Rounded.Widgets),
    BottomNavDestination(R.string.battery_insight_settings_tab, Icons.Rounded.Tune),
)

@Composable
fun AppBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isFloating: Boolean,
    isBlurEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isFloating) {
        // Standard Material 3 Navigation Bar
        NavigationBar(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            NAV_DESTINATIONS.forEachIndexed { index, item ->
                val isSelected = selectedTab == index
                val label = stringResource(item.titleRes)
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(index) },
                    icon = { Icon(item.icon, contentDescription = label) },
                    label = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    } else {
        // KernelSU-style Floating Liquid Glass Bottom Bar
        KernelSUFloatingBottomBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            isBlurEnabled = isBlurEnabled,
            modifier = modifier,
        )
    }
}

@Composable
fun KernelSUFloatingBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isBlurEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val tabCount = NAV_DESTINATIONS.size

    // Animated sliding indicator position with smooth spring physics
    val animatedIndicatorIndex by animateFloatAsState(
        targetValue = selectedTab.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 400f,
        ),
        label = "ksu_indicator_slide",
    )

    // 100% Pure Crystal Liquid Glass (Zero Matness, Fully Translucent Glass)
    val glassContainerBg = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.13f),
                Color.White.copy(alpha = 0.05f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.40f),
            )
        )
    }

    val glassBorder = BorderStroke(
        width = 0.8.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.36f else 0.75f),
                Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
            )
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.10f),
                    ambientColor = Color.Transparent,
                ),
            shape = CircleShape,
            color = Color.Transparent,
            border = glassBorder,
        ) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassContainerBg)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val tabWidth = maxWidth / tabCount

                // Dynamic Sliding Indicator Pill (Crystal Liquid Glass)
                Box(
                    modifier = Modifier
                        .offset(x = tabWidth * animatedIndicatorIndex)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (isDark) {
                                Color.White.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f)
                            }
                        )
                )

                // Tabs Row
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NAV_DESTINATIONS.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index
                        val label = stringResource(item.titleRes)
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        val tabScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.88f else if (isSelected) 1.03f else 0.95f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "tab_scale_$index",
                        )

                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                if (isDark) Color.White else MaterialTheme.colorScheme.primary
                            } else {
                                if (isDark) Color.White.copy(alpha = 0.60f) else MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            label = "tab_text_color_$index",
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .scale(tabScale)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onTabSelected(index) },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                                tint = textColor,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = textColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
