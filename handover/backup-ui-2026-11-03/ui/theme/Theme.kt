package com.icoordinator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// پالت آبی آرام
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6690),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4F7),
    onPrimaryContainer = Color(0xFF0B2E4A),
    secondary = Color(0xFF4F6374),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E3F0),
    onSecondaryContainer = Color(0xFF0C1D2B),
    tertiary = Color(0xFF5B5E8E),
    background = Color(0xFFF7FAFD),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF40484F),
    outline = Color(0xFF70787F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FCBEF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF144B6E),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFFB7C8D8),
    onSecondary = Color(0xFF213240),
    secondaryContainer = Color(0xFF38495A),
    onSecondaryContainer = Color(0xFFD3E4F4),
    tertiary = Color(0xFFC3C4F5),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E3E7),
    surface = Color(0xFF181C20),
    onSurface = Color(0xFFE0E3E7),
    surfaceVariant = Color(0xFF40484F),
    onSurfaceVariant = Color(0xFFC0C8D0),
    outline = Color(0xFF8A9299),
)

@Composable
fun ICoordinatorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
