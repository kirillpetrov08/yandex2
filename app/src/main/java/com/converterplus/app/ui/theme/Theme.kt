package com.converterplus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = SurfaceLight,
    primaryContainer = AccentBlue,
    onPrimaryContainer = SurfaceLight,
    secondary = AccentTeal,
    onSecondary = SurfaceLight,
    tertiary = AccentAmber,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = DisabledFillLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight,
    outlineVariant = DividerLight,
    error = AccentCoral,
)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = SurfaceLight,
    primaryContainer = AccentBlue,
    onPrimaryContainer = SurfaceLight,
    secondary = AccentTeal,
    onSecondary = SurfaceLight,
    tertiary = AccentAmber,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = DisabledFillDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
    outlineVariant = DividerDark,
    error = AccentCoral,
)

/**
 * Тема приложения. Dynamic color выключен намеренно — у продукта своя айдентика.
 */
@Composable
fun ConverterPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
