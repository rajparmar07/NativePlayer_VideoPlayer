package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AppFontSize
import com.example.viewmodel.AppTheme
import com.example.viewmodel.AppThemePalette

fun scaledTypography(scale: Float): Typography {
    return Typography(
        bodyLarge = Typography.bodyLarge.copy(fontSize = (16 * scale).sp, lineHeight = (20 * scale).sp),
        bodyMedium = Typography.bodyMedium.copy(fontSize = (14 * scale).sp, lineHeight = (18 * scale).sp),
        bodySmall = Typography.bodySmall.copy(fontSize = (12 * scale).sp, lineHeight = (15 * scale).sp),
        titleLarge = Typography.titleLarge.copy(fontSize = (22 * scale).sp, lineHeight = (26 * scale).sp),
        titleMedium = Typography.titleMedium.copy(fontSize = (16 * scale).sp, lineHeight = (20 * scale).sp),
        titleSmall = Typography.titleSmall.copy(fontSize = (14 * scale).sp, lineHeight = (18 * scale).sp),
        labelLarge = Typography.labelLarge.copy(fontSize = (14 * scale).sp, lineHeight = (18 * scale).sp),
        labelMedium = Typography.labelMedium.copy(fontSize = (12 * scale).sp, lineHeight = (15 * scale).sp),
        labelSmall = Typography.labelSmall.copy(fontSize = (11 * scale).sp, lineHeight = (14 * scale).sp)
    )
}

fun buildColorScheme(
    appTheme: AppTheme,
    palette: AppThemePalette,
    isDark: Boolean,
    isHighContrastDark: Boolean = false
): ColorScheme {
    // ── Main App Theme Brand Palette (#46F0D2, #131321, #FBE2B4, #FFFFFF) ───────
    // Applied for Light Mode, Dark Mode, and System Default!
    val baseScheme = if (appTheme != AppTheme.Custom) {
        if (isDark) {
            darkColorScheme(
                primary = BrandPrimaryMint,
                onPrimary = BrandDarkNavy,
                primaryContainer = BrandPrimaryMint.copy(alpha = 0.20f),
                onPrimaryContainer = BrandPrimaryMint,
                secondary = BrandWarmSand,
                onSecondary = BrandDarkNavy,
                secondaryContainer = BrandWarmSand.copy(alpha = 0.20f),
                onSecondaryContainer = BrandWarmSand,
                tertiary = BrandPrimaryMint,
                background = BrandDarkNavy,
                surface = Color(0xFF1B1B2C),
                surfaceVariant = Color(0xFF24243B),
                onBackground = BrandWhite,
                onSurface = BrandWhite,
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = BrandPrimaryLight,
                onPrimary = BrandWhite,
                primaryContainer = BrandWarmSand,
                onPrimaryContainer = BrandDarkNavy,
                secondary = Color(0xFF0D9488),
                onSecondary = BrandWhite,
                secondaryContainer = BrandWarmSand.copy(alpha = 0.30f),
                onSecondaryContainer = BrandDarkNavy,
                tertiary = BrandPrimaryMint,
                background = Color(0xFFFFFFFF), // Pure White
                surface = Color(0xFFFFFFFF),    // Pure White
                surfaceVariant = Color(0xFFF1F5F9),
                onBackground = BrandDarkNavy,
                onSurface = BrandDarkNavy,
                onSurfaceVariant = Color(0xFF475569)
            )
        }
    } else {
        if (isDark) {
            when (palette) {
                AppThemePalette.SageMint -> darkColorScheme(
                    primary = Color(0xFF4ADE80),
                    onPrimary = Color.Black,
                    secondary = Color(0xFF2DD4BF),
                    onSecondary = Color.Black,
                    tertiary = ImmersiveEmerald,
                    background = BrandDarkNavy,
                    surface = Color(0xFF1B1B2C),
                    surfaceVariant = Color(0xFF24243B),
                    primaryContainer = Color(0xFF4ADE80).copy(alpha = 0.22f),
                    onPrimaryContainer = Color(0xFF4ADE80),
                    secondaryContainer = Color(0xFF2DD4BF).copy(alpha = 0.22f),
                    onSecondaryContainer = Color(0xFF2DD4BF),
                    onBackground = BrandWhite,
                    onSurface = BrandWhite,
                    onSurfaceVariant = ImmersiveGreyText
                )
                AppThemePalette.SoftLavender -> darkColorScheme(
                    primary = SoftLavenderPrimaryLight,
                    onPrimary = Color.White,
                    secondary = SoftLavenderSecondaryLight,
                    onSecondary = Color.White,
                    tertiary = ImmersiveAmber,
                    background = BrandDarkNavy,
                    surface = Color(0xFF1B1B2C),
                    surfaceVariant = Color(0xFF24243B),
                    primaryContainer = Color(0xFFA78BFA).copy(alpha = 0.22f),
                    onPrimaryContainer = Color(0xFFA78BFA),
                    secondaryContainer = Color(0xFFFBBF24).copy(alpha = 0.22f),
                    onSecondaryContainer = Color(0xFFFBBF24),
                    onBackground = BrandWhite,
                    onSurface = BrandWhite,
                    onSurfaceVariant = ImmersiveGreyText
                )
                AppThemePalette.WarmSand -> darkColorScheme(
                    primary = Color(0xFFFB923C),
                    onPrimary = Color.Black,
                    secondary = Color(0xFFFB7185),
                    onSecondary = Color.Black,
                    tertiary = ImmersiveAmber,
                    background = BrandDarkNavy,
                    surface = Color(0xFF1B1B2C),
                    surfaceVariant = Color(0xFF24243B),
                    primaryContainer = Color(0xFFFB923C).copy(alpha = 0.22f),
                    onPrimaryContainer = Color(0xFFFB923C),
                    secondaryContainer = Color(0xFFFB7185).copy(alpha = 0.22f),
                    onSecondaryContainer = Color(0xFFFB7185),
                    onBackground = BrandWhite,
                    onSurface = BrandWhite,
                    onSurfaceVariant = ImmersiveGreyText
                )
                AppThemePalette.OceanSlate -> darkColorScheme(
                    primary = OceanSlatePrimaryDark,
                    onPrimary = Color.Black,
                    secondary = OceanSlateSecondaryDark,
                    onSecondary = Color.Black,
                    tertiary = ImmersiveEmerald,
                    background = BrandDarkNavy,
                    surface = Color(0xFF1B1B2C),
                    surfaceVariant = Color(0xFF24243B),
                    primaryContainer = OceanSlatePrimaryDark.copy(alpha = 0.22f),
                    onPrimaryContainer = OceanSlatePrimaryDark,
                    secondaryContainer = OceanSlateSecondaryDark.copy(alpha = 0.22f),
                    onSecondaryContainer = OceanSlateSecondaryDark,
                    onBackground = BrandWhite,
                    onSurface = BrandWhite,
                    onSurfaceVariant = ImmersiveGreyText
                )
                AppThemePalette.NordicIndigo -> darkColorScheme(
                    primary = NordicIndigoPrimaryDark,
                    onPrimary = Color.Black,
                    secondary = NordicIndigoSecondaryDark,
                    onSecondary = Color.Black,
                    tertiary = ImmersiveEmerald,
                    background = BrandDarkNavy,
                    surface = Color(0xFF1B1B2C),
                    surfaceVariant = Color(0xFF24243B),
                    primaryContainer = NordicIndigoPrimaryDark.copy(alpha = 0.22f),
                    onPrimaryContainer = NordicIndigoPrimaryDark,
                    secondaryContainer = NordicIndigoSecondaryDark.copy(alpha = 0.22f),
                    onSecondaryContainer = NordicIndigoSecondaryDark,
                    onBackground = BrandWhite,
                    onSurface = BrandWhite,
                    onSurfaceVariant = ImmersiveGreyText
                )
                AppThemePalette.RoseQuartz -> darkColorScheme(
                    primary = RoseQuartzPrimaryDark,
                    onPrimary = Color.Black,
                    secondary = RoseQuartzSecondaryDark,
                    onSecondary = Color.Black,
                    tertiary = ImmersiveAmber,
                    background = BrandDarkNavy,
                    surface = Color(0xFF1B1B2C),
                    surfaceVariant = Color(0xFF24243B),
                    primaryContainer = RoseQuartzPrimaryDark.copy(alpha = 0.22f),
                    onPrimaryContainer = RoseQuartzPrimaryDark,
                    secondaryContainer = RoseQuartzSecondaryDark.copy(alpha = 0.22f),
                    onSecondaryContainer = RoseQuartzSecondaryDark,
                    onBackground = BrandWhite,
                    onSurface = BrandWhite,
                    onSurfaceVariant = ImmersiveGreyText
                )
            }
        } else {
            when (palette) {
                AppThemePalette.SageMint -> lightColorScheme(
                    primary = SageMintPrimaryLight,
                    onPrimary = Color.White,
                    secondary = SageMintSecondaryLight,
                    onSecondary = Color.White,
                    tertiary = ImmersiveEmerald,
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF1F5F9),
                    primaryContainer = SageMintPrimaryLight.copy(alpha = 0.15f),
                    onPrimaryContainer = SageMintPrimaryLight,
                    secondaryContainer = SageMintSecondaryLight.copy(alpha = 0.15f),
                    onSecondaryContainer = SageMintSecondaryLight,
                    onBackground = BrandDarkNavy,
                    onSurface = BrandDarkNavy,
                    onSurfaceVariant = Color(0xFF475569)
                )
                AppThemePalette.SoftLavender -> lightColorScheme(
                    primary = SoftLavenderPrimaryLight,
                    onPrimary = Color.White,
                    secondary = SoftLavenderSecondaryLight,
                    onSecondary = Color.White,
                    tertiary = ImmersiveAmber,
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF1F5F9),
                    primaryContainer = SoftLavenderPrimaryLight.copy(alpha = 0.15f),
                    onPrimaryContainer = SoftLavenderPrimaryLight,
                    secondaryContainer = SoftLavenderSecondaryLight.copy(alpha = 0.15f),
                    onSecondaryContainer = SoftLavenderSecondaryLight,
                    onBackground = BrandDarkNavy,
                    onSurface = BrandDarkNavy,
                    onSurfaceVariant = Color(0xFF475569)
                )
                AppThemePalette.WarmSand -> lightColorScheme(
                    primary = WarmSandPrimaryLight,
                    onPrimary = Color.White,
                    secondary = WarmSandSecondaryLight,
                    onSecondary = Color.White,
                    tertiary = ImmersiveAmber,
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF1F5F9),
                    primaryContainer = WarmSandPrimaryLight.copy(alpha = 0.15f),
                    onPrimaryContainer = WarmSandPrimaryLight,
                    secondaryContainer = WarmSandSecondaryLight.copy(alpha = 0.15f),
                    onSecondaryContainer = WarmSandSecondaryLight,
                    onBackground = BrandDarkNavy,
                    onSurface = BrandDarkNavy,
                    onSurfaceVariant = Color(0xFF475569)
                )
                AppThemePalette.OceanSlate -> lightColorScheme(
                    primary = TealPrimaryLight,
                    onPrimary = TealOnPrimaryLight,
                    secondary = Color(0xFF4B626A),
                    onSecondary = Color.White,
                    tertiary = ImmersiveEmerald,
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF1F5F9),
                    primaryContainer = TealPrimaryLight.copy(alpha = 0.15f),
                    onPrimaryContainer = TealPrimaryLight,
                    secondaryContainer = Color(0xFF4B626A).copy(alpha = 0.15f),
                    onSecondaryContainer = Color(0xFF4B626A),
                    onBackground = BrandDarkNavy,
                    onSurface = BrandDarkNavy,
                    onSurfaceVariant = Color(0xFF475569)
                )
                AppThemePalette.NordicIndigo -> lightColorScheme(
                    primary = Color(0xFF3730A3),
                    onPrimary = Color.White,
                    secondary = Color(0xFF0284C7),
                    onSecondary = Color.White,
                    tertiary = ImmersiveEmerald,
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF1F5F9),
                    primaryContainer = Color(0xFF3730A3).copy(alpha = 0.15f),
                    onPrimaryContainer = Color(0xFF3730A3),
                    secondaryContainer = Color(0xFF0284C7).copy(alpha = 0.15f),
                    onSecondaryContainer = Color(0xFF0284C7),
                    onBackground = BrandDarkNavy,
                    onSurface = BrandDarkNavy,
                    onSurfaceVariant = Color(0xFF475569)
                )
                AppThemePalette.RoseQuartz -> lightColorScheme(
                    primary = Color(0xFF9D174D),
                    onPrimary = Color.White,
                    secondary = Color(0xFFCA8A04),
                    onSecondary = Color.White,
                    tertiary = ImmersiveAmber,
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFF1F5F9),
                    primaryContainer = Color(0xFF9D174D).copy(alpha = 0.15f),
                    onPrimaryContainer = Color(0xFF9D174D),
                    secondaryContainer = Color(0xFFCA8A04).copy(alpha = 0.15f),
                    onSecondaryContainer = Color(0xFFCA8A04),
                    onBackground = BrandDarkNavy,
                    onSurface = BrandDarkNavy,
                    onSurfaceVariant = Color(0xFF475569)
                )
            }
        }
    }

    return if (isDark && isHighContrastDark) {
        baseScheme.copy(
            background = Color(0xFF000000),      // Pure OLED Black
            surface = Color(0xFF07070B),         // High Contrast Black Surface
            surfaceVariant = Color(0xFF12121E),  // High Contrast Surface Variant
            onBackground = Color(0xFFFFFFFF),    // Crisp Pure White
            onSurface = Color(0xFFFFFFFF),       // Crisp Pure White
            onSurfaceVariant = Color(0xFFB4B4C6) // High Contrast Subtitle Text
        )
    } else {
        baseScheme
    }
}

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.System,
    appPalette: AppThemePalette = AppThemePalette.OceanSlate,
    appFontSize: AppFontSize = AppFontSize.Regular,
    isHighContrastDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appTheme) {
        AppTheme.Light -> false
        AppTheme.Dark -> true
        AppTheme.Custom -> !appPalette.isLightPalette
        AppTheme.System -> isSystemInDarkTheme()
    }

    val colorScheme = buildColorScheme(appTheme, appPalette, darkTheme, isHighContrastDark)
    val typography = scaledTypography(appFontSize.scaleFactor)

    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale * appFontSize.scaleFactor
    )

    CompositionLocalProvider(LocalDensity provides customDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
