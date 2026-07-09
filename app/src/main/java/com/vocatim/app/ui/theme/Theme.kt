package com.vocatim.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Brand identity is fixed: no dynamic (Material You) colors.
private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = VioletDeep,
    onPrimaryContainer = Color.White,
    secondary = Teal,
    onSecondary = Ink,
    tertiary = Gold,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimaryDark,
    surface = InkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = InkCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkCard,
    surfaceContainerHighest = InkCard,
    outline = TextSecondaryDark,
    outlineVariant = InkBorder,
    error = ErrorRed,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Violet,
    onPrimaryContainer = Color.White,
    secondary = Teal,
    onSecondary = TextPrimaryLight,
    tertiary = Gold,
    onTertiary = TextPrimaryLight,
    background = Snow,
    onBackground = TextPrimaryLight,
    surface = Snow,
    onSurface = TextPrimaryLight,
    surfaceVariant = SnowVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = SnowSurface,
    surfaceContainerHigh = SnowCard,
    surfaceContainerHighest = SnowCard,
    outline = TextSecondaryLight,
    outlineVariant = SnowBorder,
    error = ErrorRedDim,
    onError = Color.White,
)

val VocatimShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Signature gradient used for hero actions (record button, highlights). */
val BrandGradient = Brush.linearGradient(listOf(Violet, Teal))

/** User-selectable accent colors layered over the fixed ink/linen palette. */
enum class Accent(val key: String, val light: Color, val dark: Color) {
    VIOLET("violet", VioletDeep, Violet),
    TEAL("teal", Color(0xFF1FA398), Teal),
    GOLD("gold", Color(0xFFC98A1E), Gold),
    BLUE("blue", Color(0xFF3A6FF0), Color(0xFF6E93FF)),
    ROSE("rose", Color(0xFFD1477E), Color(0xFFFF7FB0));

    companion object {
        fun fromKey(key: String): Accent = entries.firstOrNull { it.key == key } ?: VIOLET
    }
}

@Composable
fun VocatimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentKey: String = "violet",
    content: @Composable () -> Unit,
) {
    val accent = Accent.fromKey(accentKey)
    val base = if (darkTheme) DarkColors else LightColors
    val primary = if (darkTheme) accent.dark else accent.light
    val scheme = base.copy(
        primary = primary,
        primaryContainer = primary,
        secondary = if (accent == Accent.TEAL) accent.dark else base.secondary,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = VocatimTypography,
        shapes = VocatimShapes,
        content = content,
    )
}
