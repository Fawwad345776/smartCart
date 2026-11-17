package com.aura.smartcart.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green700,
    onPrimary = Color.White,
    primaryContainer = Green100,
    onPrimaryContainer = Green800,
    secondary = Green500,
    onSecondary = Green900,
    background = Color.White,
    onBackground = Green900,
    surface = Color.White,
    onSurface = Green900,
    surfaceVariant = Green50,
    onSurfaceVariant = LightMuted,
    surfaceContainer = Green50,
    outline = LightBorder,
    outlineVariant = LightBorder
)

private val DarkColors = darkColorScheme(
    primary = Green400,
    onPrimary = Green900,
    primaryContainer = DarkSoft,
    onPrimaryContainer = Green200,
    secondary = Green300,
    onSecondary = Green900,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkBackground,
    onSurface = DarkText,
    surfaceVariant = DarkSurface1,
    onSurfaceVariant = DarkMuted,
    surfaceContainer = DarkSurface2,
    outline = DarkBorder,
    outlineVariant = DarkBorder
)

/** Brand colours Material's scheme has no slot for (hero blocks, logo tile, floating chips). */
@Immutable
data class BrandColors(
    val isDark: Boolean,
    val logoTile: Color,
    val logoCart: Color,
    val logoCheck: Color,
    val hero: Color,
    val onHero: Color,
    val heroGlow: Color,
    val heroLine: Color,
    val heroTile: Color,
    val heroAccent: Color,
    val chip: Color,
    val onChip: Color,
    val onChipMuted: Color,
    val dotOff: Color
)

private val LightBrand = BrandColors(
    isDark = false,
    logoTile = Green700,
    logoCart = Color.White,
    logoCheck = Green300,
    hero = Green700,
    onHero = Color.White,
    heroGlow = Color.White.copy(alpha = 0.12f),
    heroLine = Color.White.copy(alpha = 0.30f),
    heroTile = Color.White,
    heroAccent = Green700,
    chip = Color.White,
    onChip = Green900,
    onChipMuted = LightMuted,
    dotOff = LightBorder
)

private val DarkBrand = BrandColors(
    isDark = true,
    logoTile = Green600,
    logoCart = Color.White,
    logoCheck = Green200,
    hero = DarkSurface1,
    onHero = DarkText,
    heroGlow = Green400.copy(alpha = 0.10f),
    heroLine = Green400.copy(alpha = 0.25f),
    heroTile = DarkSurface2,
    heroAccent = Green400,
    chip = DarkSurface2,
    onChip = DarkText,
    onChipMuted = DarkMuted,
    dotOff = DarkBorder
)

val LocalBrandColors = staticCompositionLocalOf { LightBrand }

@Composable
fun SmartCartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalBrandColors provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}

object Brand {
    val colors: BrandColors
        @Composable get() = LocalBrandColors.current
}
