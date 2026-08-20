package com.cryptomacro.app.ui.theme

/** BEGINNER: CryptoMacroTheme picks DarkScheme, LightScheme, or follows the phone (SYSTEM). All screens wrap in this. */
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import com.cryptomacro.app.data.local.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandIndigo,
    tertiary = Gold,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVar,
    onSurfaceVariant = DarkMuted,
    outline = DarkOutline,
    error = Bear,
    primaryContainer = DarkSurfaceVar,
    secondaryContainer = DarkSurfaceVar,
)

private val LightScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandIndigo,
    tertiary = Gold,
    background = LightBg,
    onBackground = Color(0xFF0B0E14),
    surface = LightSurface,
    onSurface = Color(0xFF0B0E14),
    surfaceVariant = LightSurfaceVar,
    onSurfaceVariant = Color(0xFF4A5568),
    outline = LightOutline,
    error = BearAlt,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, lineHeight = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
)

@Composable
fun CryptoMacroTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
