package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = MayaYellowPrimary,
    onPrimary = MayaTextPrimary,
    primaryContainer = MayaYellowContainer,
    onPrimaryContainer = MayaTextPrimary,
    secondary = MayaYellowDeep,
    onSecondary = MayaTextPrimary,
    secondaryContainer = MayaYellowMuted,
    onSecondaryContainer = MayaTextPrimary,
    tertiary = MayaYellowBright,
    onTertiary = MayaTextPrimary,
    background = MayaSoftBackground,
    onBackground = MayaTextPrimary,
    surface = MayaWhite,
    onSurface = MayaTextPrimary,
    surfaceVariant = MayaLightGray,
    onSurfaceVariant = MayaTextSecondary,
    outline = MayaBorder,
    outlineVariant = MayaBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = MayaYellowPrimary,
    onPrimary = MayaTextPrimary,
    primaryContainer = MayaDarkSurfaceVariant,
    onPrimaryContainer = MayaYellowBright,
    secondary = MayaYellowDeep,
    onSecondary = MayaTextPrimary,
    secondaryContainer = MayaDarkSurface,
    onSecondaryContainer = MayaYellowBright,
    tertiary = MayaYellowBright,
    onTertiary = MayaTextPrimary,
    background = MayaDarkBackground,
    onBackground = MayaDarkTextPrimary,
    surface = MayaDarkSurface,
    onSurface = MayaDarkTextPrimary,
    surfaceVariant = MayaDarkSurfaceVariant,
    onSurfaceVariant = MayaDarkTextSecondary,
    outline = MayaDarkBorder,
    outlineVariant = MayaDarkBorder
)

@Composable
fun MayaXTheme(
    darkTheme: Boolean = false, // Default is Light Yellow + White as requested
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MayaXTheme(darkTheme = darkTheme, content = content)
}
