package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ImmersiveBlue,
    secondary = ImmersiveAmber,
    tertiary = ImmersiveEmerald,
    background = ImmersiveBg,
    surface = ImmersiveCard,
    surfaceVariant = ImmersiveCardHover,
    onBackground = ImmersiveSlateLight,
    onSurface = ImmersiveSlateLight,
    onSurfaceVariant = ImmersiveGreyText
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
