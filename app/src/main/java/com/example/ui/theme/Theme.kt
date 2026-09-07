package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CineStreamColorScheme = darkColorScheme(
    primary = NetflixRed,
    onPrimary = Color.White,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = Color.White,
    secondary = TextPrimary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = BadgeTeal,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = AccentBorder
)

@Composable
fun CineStreamTheme(
    darkTheme: Boolean = true, // Siempre modo oscuro por estética cinematográfica
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CineStreamColorScheme,
        typography = Typography,
        content = content
    )
}
