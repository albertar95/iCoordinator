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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.icoordinator.R

/** فونت وزیرمتن (نسخه‌ی ارقام فارسی) با چهار وزن 400/500/600/700 */
val VazirmatnFamily: FontFamily = FontFamily(
    Font(R.font.vazirmatn_fd_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_fd_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_fd_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_fd_bold, FontWeight.Bold),
)

// ---- Light palette (spec-v3 "Colors") ----
private val PrimaryLight = Color(0xFF00696B)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFDCEEEE)
private val SecondaryLight = Color(0xFF52636A)
private val BackgroundLight = Color(0xFFF7F7F8)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceContainerLight = Color(0xFFF0F0F3)
private val SurfaceContainerHighLight = Color(0xFFFFFFFF)
private val SurfaceVariantLight = Color(0xFFE6E6EA)
private val OnSurfaceLight = Color(0xFF1B1B1D)
private val OnSurfaceVariantLight = Color(0xFF76767C)
private val OutlineVariantLight = Color(0xFFE2E2E6)
private val OutlineLight = Color(0xFF1B1B1D)
private val ErrorLight = Color(0xFFBA1A1A)
private val OnErrorLight = Color(0xFFFFFFFF)

// ---- Dark palette ----
private val PrimaryDark = Color(0xFF4CDADB)
private val OnPrimaryDark = Color(0xFF003738)
private val PrimaryContainerDark = Color(0xFF1F3F40)
private val SecondaryDark = Color(0xFFB9C9D0)
private val BackgroundDark = Color(0xFF1C1C1E)
private val SurfaceDark = Color(0xFF2A2A2D)
private val SurfaceContainerDark = Color(0xFF38383C)
private val SurfaceContainerHighDark = Color(0xFF232326)
private val SurfaceVariantDark = Color(0xFF3A3A3E)
private val OnSurfaceDark = Color(0xFFF2F2F4)
private val OnSurfaceVariantDark = Color(0xFFA3A3AA)
private val OutlineVariantDark = Color(0xFF3C3C40)
private val OutlineDark = Color(0xFFF2F2F4)
private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF410002)

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    secondary = SecondaryLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    secondary = SecondaryDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
)

// ---- Score colours (bands: above 80 green, 50 to 80 amber, below 50 red) ----
private val ScoreGreenLight = Color(0xFF2E7D32)
private val ScoreAmberLight = Color(0xFFF9A825)
private val ScoreRedLight = Color(0xFFC62828)
private val ScoreGreyLight = Color(0xFF757575)
private val ScoreGreenDark = Color(0xFF7BD88F)
private val ScoreAmberDark = Color(0xFFFFCA28)
private val ScoreRedDark = Color(0xFFFF8A80)

/** رنگ نمره: بالای ۸۰ سبز، ۵۰ تا ۸۰ کهربایی، زیر ۵۰ قرمز، بدون داده خاکستری */
@Composable
fun scoreColor(percent: Int?): Color {
    val dark = isSystemInDarkTheme()
    return when {
        percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        percent > 80 -> if (dark) ScoreGreenDark else ScoreGreenLight
        percent >= 50 -> if (dark) ScoreAmberDark else ScoreAmberLight
        else -> if (dark) ScoreRedDark else ScoreRedLight
    }
}

private fun style(size: Int, line: Int, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = VazirmatnFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = 0.sp,
)

// Styles from spec-v3 "Typography"; the others keep the M3 default size/weight/line height.
private val AppTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Normal),
    displayMedium = style(45, 52, FontWeight.Normal),
    displaySmall = style(36, 44, FontWeight.Normal),
    headlineLarge = style(32, 40, FontWeight.Normal),
    headlineMedium = style(26, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.Normal),
    titleLarge = style(22, 30, FontWeight.Bold),
    titleMedium = style(17, 26, FontWeight.SemiBold),
    titleSmall = style(16, 24, FontWeight.Bold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 22, FontWeight.Normal),
    bodySmall = style(13, 20, FontWeight.Normal),
    labelLarge = style(17, 24, FontWeight.Bold),
    labelMedium = style(13, 18, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium),
)

// small = calendar cell 14, medium = text fields / buttons 28, large = pill rows and big cards 30, extraLarge = dialog 32
private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(32.dp),
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
