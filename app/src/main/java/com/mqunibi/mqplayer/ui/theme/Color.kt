package com.mqunibi.mqplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// Obsidian fallback palette (used when no album art available and API < 31)
val Obsidian      = Color(0xFF0A0A0F)
val SurfaceDark   = Color(0xFF13131A)
val SurfaceRaised = Color(0xFF1C1C28)
val Gold          = Color(0xFFD4A843)
val GoldDim       = Color(0xFF7A6228)
val SnowWhite     = Color(0xFFF4F1E8)
val Ash           = Color(0xFF9896A4)
val Border        = Color(0xFF252535)

val Parchment     = Color(0xFFF8F5EE)
val ParchmentDark = Color(0xFFEDE8DC)
val InkDark       = Color(0xFF1A1816)
val InkMid        = Color(0xFF4A4640)
val GoldLight     = Color(0xFFB8892A)

val ObsidianDark = darkColorScheme(
    primary = Gold,
    onPrimary = Obsidian,
    primaryContainer = GoldDim,
    onPrimaryContainer = SnowWhite,
    secondary = Ash,
    onSecondary = Obsidian,
    background = Obsidian,
    onBackground = SnowWhite,
    surface = SurfaceDark,
    onSurface = SnowWhite,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Ash,
    outline = Border,
    tertiary = Gold,
    onTertiary = Obsidian,
)

val ObsidianLight = lightColorScheme(
    primary = GoldLight,
    onPrimary = Parchment,
    primaryContainer = ParchmentDark,
    onPrimaryContainer = InkDark,
    secondary = InkMid,
    onSecondary = Parchment,
    background = Parchment,
    onBackground = InkDark,
    surface = ParchmentDark,
    onSurface = InkDark,
    surfaceVariant = Parchment,
    onSurfaceVariant = InkMid,
    outline = InkMid,
    tertiary = GoldLight,
    onTertiary = Parchment,
)

fun seedColorScheme(seed: Color, isDark: Boolean): ColorScheme {
    val argb = seed.toArgb()
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    val h = hsv[0]
    val s = hsv[1]

    fun c(hue: Float, sat: Float, light: Float) = Color(
        ColorUtils.HSLToColor(floatArrayOf(hue % 360f, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f)))
    )

    return if (isDark) darkColorScheme(
        primary             = c(h, s, 0.80f),
        onPrimary           = c(h, s * 0.4f, 0.12f),
        primaryContainer    = c(h, s * 0.7f, 0.25f),
        onPrimaryContainer  = c(h, s * 0.5f, 0.90f),
        secondary           = c(h + 30f, s * 0.35f, 0.70f),
        onSecondary         = c(h, s * 0.2f, 0.12f),
        secondaryContainer  = c(h + 20f, s * 0.25f, 0.20f),
        onSecondaryContainer = c(h, s * 0.15f, 0.88f),
        background          = c(h, s * 0.05f, 0.06f),
        onBackground        = c(h, s * 0.04f, 0.93f),
        surface             = c(h, s * 0.06f, 0.10f),
        onSurface           = c(h, s * 0.04f, 0.93f),
        surfaceVariant      = c(h, s * 0.10f, 0.16f),
        onSurfaceVariant    = c(h, s * 0.06f, 0.62f),
        outline             = c(h, s * 0.08f, 0.28f),
        tertiary            = c(h + 60f, s * 0.55f, 0.72f),
        onTertiary          = c(h + 60f, s * 0.3f, 0.12f),
        tertiaryContainer   = c(h + 60f, s * 0.4f, 0.22f),
        onTertiaryContainer = c(h + 60f, s * 0.3f, 0.88f),
    ) else lightColorScheme(
        primary             = c(h, s, 0.35f),
        onPrimary           = Color.White,
        primaryContainer    = c(h, s * 0.4f, 0.92f),
        onPrimaryContainer  = c(h, s * 0.6f, 0.12f),
        secondary           = c(h + 30f, s * 0.35f, 0.38f),
        onSecondary         = Color.White,
        secondaryContainer  = c(h + 20f, s * 0.25f, 0.90f),
        onSecondaryContainer = c(h, s * 0.3f, 0.15f),
        background          = c(h, s * 0.04f, 0.98f),
        onBackground        = c(h, s * 0.05f, 0.10f),
        surface             = c(h, s * 0.04f, 0.96f),
        onSurface           = c(h, s * 0.05f, 0.10f),
        surfaceVariant      = c(h, s * 0.08f, 0.90f),
        onSurfaceVariant    = c(h, s * 0.06f, 0.38f),
        outline             = c(h, s * 0.08f, 0.55f),
        tertiary            = c(h + 60f, s * 0.50f, 0.40f),
        onTertiary          = Color.White,
        tertiaryContainer   = c(h + 60f, s * 0.35f, 0.92f),
        onTertiaryContainer = c(h + 60f, s * 0.4f, 0.12f),
    )
}
