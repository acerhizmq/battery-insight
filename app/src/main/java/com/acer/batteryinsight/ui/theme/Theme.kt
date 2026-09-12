package com.acer.batteryinsight.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF1A3855),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF13423E),
    onSecondaryContainer = Color(0xFFA7F0E8),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF3D2554),
    onTertiaryContainer = Color(0xFFF3DAFF),
    background = Color(0xFF121216),
    onBackground = Color(0xFFF0F0F4),
    surface = Color(0xFF18181E),
    onSurface = Color(0xFFF0F0F4),
    surfaceVariant = Color(0xFF24242C),
    onSurfaceVariant = Color(0xFFCACACE),
    outline = Color(0xFF555560),
    surfaceContainer = Color(0xFF1C1C22),
    surfaceContainerHigh = Color(0xFF26262E),
    surfaceContainerHighest = Color(0xFF30303A),
    surfaceBright = Color(0xFF3A3A46),
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF1A3855),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF13423E),
    onSecondaryContainer = Color(0xFFA7F0E8),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF3D2554),
    onTertiaryContainer = Color(0xFFF3DAFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0D0D10),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF18181E),
    onSurfaceVariant = Color(0xFFD0D0D5),
    outline = Color(0xFF444450),
    surfaceContainer = Color(0xFF101014),
    surfaceContainerHigh = Color(0xFF181820),
    surfaceContainerHighest = Color(0xFF22222C),
    surfaceBright = Color(0xFF2C2C38),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA7F0E8),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF8E24AA),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1A1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1E),
    surfaceVariant = Color(0xFFE8E8EE),
    onSurfaceVariant = Color(0xFF44444A),
    surfaceContainer = Color(0xFFF0F2F6),
    surfaceContainerHigh = Color(0xFFE6E8EE),
)

@Composable
fun BatteryInsightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                if (amoledMode) dynamicDarkColorScheme(context).copy(
                    background = Color.Black,
                    surface = Color(0xFF0D0D10),
                    surfaceContainer = Color(0xFF101014),
                    surfaceContainerHigh = Color(0xFF181820),
                )
                else dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> if (amoledMode) AmoledDarkColorScheme else DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
