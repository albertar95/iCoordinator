package com.icoordinator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// پالت فیروزه‌ای آرام
private val TealPrimaryLight = Color(0xFF00696B)
private val TealOnPrimaryLight = Color(0xFFFFFFFF)
private val TealContainerLight = Color(0xFFB8ECEC)
private val SlateSecondaryLight = Color(0xFF52636A)
private val BackgroundLight = Color(0xFFF6FAFA)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceVariantLight = Color(0xFFE3EDED)
private val OnSurfaceLight = Color(0xFF171D1E)
private val OnSurfaceVariantLight = Color(0xFF3F4949)
private val ErrorLight = Color(0xFFBA1A1A)

private val TealPrimaryDark = Color(0xFF4CDADB)
private val TealOnPrimaryDark = Color(0xFF003738)
private val TealContainerDark = Color(0xFF004F51)
private val SlateSecondaryDark = Color(0xFFB9C9D0)
private val BackgroundDark = Color(0xFF0E1415)
private val SurfaceDark = Color(0xFF141B1C)
private val SurfaceVariantDark = Color(0xFF232C2D)
private val OnSurfaceDark = Color(0xFFDDE4E4)
private val OnSurfaceVariantDark = Color(0xFFBEC8C8)
private val ErrorDark = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = TealOnPrimaryLight,
    primaryContainer = TealContainerLight,
    secondary = SlateSecondaryLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error = ErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    secondary = SlateSecondaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorDark,
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    medium = RoundedCornerShape(12.dp),
)

@Composable
fun ICoordinatorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
