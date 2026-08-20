package com.cryptomacro.app.ui.theme

/**
 * BEGINNER: 0xFF0B0E14 is ARGB hex. FF = fully opaque, then red/green/blue.
 * Bull = profit green, Bear = loss red, Gold = Shemitah / takeaway highlights.
 * AllocPalette is the donut-chart slice colors (we cycle with index % size).
 */
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF0B0E14)
val DarkSurface = Color(0xFF151A23)
val DarkSurfaceVar = Color(0xFF1E2532)
val DarkOutline = Color(0xFF2A3447)
val DarkText = Color(0xFFE8EEF8)
val DarkMuted = Color(0xFF8B9BB4)

val LightBg = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVar = Color(0xFFEDF2F7)
val LightOutline = Color(0xFFE2E8F0)

val Bull = Color(0xFF00C087)
val BullAlt = Color(0xFF10B981)
val Bear = Color(0xFFF6465D)
val BearAlt = Color(0xFFEF4444)
val BrandBlue = Color(0xFF3B82F6)
val BrandIndigo = Color(0xFF6366F1)
val Gold = Color(0xFFF59E0B)
val Teal = Color(0xFF2DD4BF)

val AllocPalette = listOf(
    Color(0xFF2DD4BF), Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFFFBBF24),
    Color(0xFFFB7185), Color(0xFF34D399), Color(0xFFF472B6), Color(0xFF94A3B8),
)
