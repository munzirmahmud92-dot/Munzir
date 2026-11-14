package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

@Immutable
data class StudioPalette(
    val isDark: Boolean,
    val backgroundBrush: Brush,
    val accentGradientBrush: Brush,
    val cardGradientBrush: Brush,
    val cardBorder: Color,
    val waveformActive: Color,
    val waveformInactive: Color,
    val cyanGlow: Color,
    val emeraldStatus: Color,
    val amberWarning: Color,
    val roseError: Color
)

private val DarkStudioPalette = StudioPalette(
    isDark = true,
    backgroundBrush = Brush.verticalGradient(
        colors = listOf(StudioBlack, StudioNavyDeep, Color(0xFF070B19))
    ),
    accentGradientBrush = Brush.horizontalGradient(
        colors = listOf(DeepPurple, ElectricViolet, SapphireBlue)
    ),
    cardGradientBrush = Brush.linearGradient(
        colors = listOf(StudioNavyElevated, StudioNavySurface)
    ),
    cardBorder = StudioBorderDark,
    waveformActive = CyanGlow,
    waveformInactive = Color(0xFF2D3A6E),
    cyanGlow = CyanGlow,
    emeraldStatus = EmeraldOnline,
    amberWarning = AmberWarning,
    roseError = RoseError
)

private val LightStudioPalette = StudioPalette(
    isDark = false,
    backgroundBrush = Brush.verticalGradient(
        colors = listOf(LightCanvas, Color(0xFFEDF1FC), Color(0xFFE7ECFB))
    ),
    accentGradientBrush = Brush.horizontalGradient(
        colors = listOf(LightPrimaryPurple, LightSecondaryBlue, LightCyanAccent)
    ),
    cardGradientBrush = Brush.linearGradient(
        colors = listOf(LightSurface, Color(0xFFF8FAFF))
    ),
    cardBorder = LightBorder,
    waveformActive = LightSecondaryBlue,
    waveformInactive = Color(0xFFCBD5E1),
    cyanGlow = LightCyanAccent,
    emeraldStatus = Color(0xFF059669),
    amberWarning = Color(0xFFD97706),
    roseError = Color(0xFFE11D48)
)

val LocalStudioPalette = staticCompositionLocalOf { DarkStudioPalette }

private val DarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = DeepPurple,
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = SapphireBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1D3570),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = CyanGlow,
    onTertiary = StudioBlack,
    background = StudioBlack,
    onBackground = StudioTextPrimaryDark,
    surface = StudioNavyDeep,
    onSurface = StudioTextPrimaryDark,
    surfaceVariant = StudioNavySurface,
    onSurfaceVariant = StudioTextSecondaryDark,
    surfaceContainerLow = StudioNavySurface,
    surfaceContainer = StudioNavyElevated,
    surfaceContainerHigh = StudioNavyContainerHigh,
    outline = StudioBorderDark,
    error = RoseError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = LightSecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = LightCyanAccent,
    onTertiary = Color.White,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLow = Color(0xFFF9FAFF),
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightBorder,
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoiceCloneStudioTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val palette = if (useDark) DarkStudioPalette else LightStudioPalette

    CompositionLocalProvider(LocalStudioPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudioTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}
