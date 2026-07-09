package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.viewmodel.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = ImmersiveBlue,
    secondary = ImmersiveAmber,
    tertiary = ImmersiveEmerald,
    background = ImmersiveBg,
    surface = ImmersiveCard,
    surfaceVariant = ImmersiveCardHover,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    primaryContainer = Color(0x263B82F6), // 15% ImmersiveBlue
    onPrimaryContainer = ImmersiveSlateLight,
    secondaryContainer = Color(0x26FBBF24), // 15% ImmersiveAmber
    onSecondaryContainer = ImmersiveSlateLight,
    onBackground = ImmersiveSlateLight,
    onSurface = ImmersiveSlateLight,
    onSurfaceVariant = ImmersiveGreyText
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0369A1),        // Sky Blue 700
    secondary = Color(0xFF0E7490),      // Cyan 700
    tertiary = Color(0xFF7C3AED),       // Violet
    background = Color(0xFFF0F9FF),     // Sky 50
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE0F2FE), // Sky 100
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE), // Sky 100
    onPrimaryContainer = Color(0xFF0369A1), // Sky 700
    secondaryContainer = Color(0xFFCFFAFE), // Cyan 100
    onSecondaryContainer = Color(0xFF0E7490), // Cyan 700
    onBackground = Color(0xFF0C4A6E),   // Sky 900
    onSurface = Color(0xFF0C4A6E),
    onSurfaceVariant = Color(0xFF0369A1) // Sky 700
)

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appTheme) {
        AppTheme.Light -> false
        AppTheme.Dark -> true
        AppTheme.System -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
