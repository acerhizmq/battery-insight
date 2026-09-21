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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        val density = LocalDensity.current
        val fontScale = density.fontScale
        val adaptiveM3FontSize = if (fontScale > 1.15f) (11f / (fontScale / 1.15f)).sp else 11.sp

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
                            fontSize = adaptiveM3FontSize,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = configuration.screenWidthDp.dp
    val fontScale = density.fontScale

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

    // Responsive horizontal margin: smaller on compact screens to give tabs maximum space
    val horizontalMargin = when {
        screenWidth < 360.dp -> 6.dp
        screenWidth < 400.dp -> 10.dp
        else -> 16.dp
    }

    // Responsive height that gently accommodates larger font scales without clipping
    val navBarHeight = (64f * fontScale.coerceIn(1f, 1.22f)).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarHeight)
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassContainerBg)
                    .padding(horizontal = 3.dp, vertical = 3.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val tabWidth = maxWidth / tabCount
                val isCompact = tabWidth < 76.dp || fontScale > 1.15f
                val iconSize = if (tabWidth < 68.dp) 18.dp else if (isCompact) 20.dp else 22.dp

                // Adaptive font sizing based on available tab width and font scale
                val baseFontSize = when {
                    tabWidth < 68.dp -> 8.5.sp
                    tabWidth < 76.dp -> 9.5.sp
                    tabWidth < 88.dp -> 10.2.sp
                    else -> 11.sp
                }
                // When system font scale is high (> 1.1f), scale the base SP so that the resulting pixel size fits cleanly
                val effectiveFontSize = if (fontScale > 1.1f) {
                    (baseFontSize.value / (fontScale / 1.1f)).sp
                } else {
                    baseFontSize
                }

                val indicatorOffsetX = (tabWidth.value * animatedIndicatorIndex).dp

                // Dynamic Sliding Indicator Pill (Crystal Liquid Glass)
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffsetX)
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
                                modifier = Modifier.size(iconSize),
                            )
                            Spacer(modifier = Modifier.height(if (isCompact) 1.dp else 2.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = effectiveFontSize,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = if (tabWidth < 75.dp) (-0.3).sp else (-0.1).sp,
                                ),
                                color = textColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
            }
        }
    }
}
}



