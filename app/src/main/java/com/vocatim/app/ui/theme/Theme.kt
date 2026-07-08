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

@Composable
fun VocatimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VocatimTypography,
        shapes = VocatimShapes,
        content = content,
    )
}
