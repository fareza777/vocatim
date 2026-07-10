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

/**
 * User-selectable SURFACE palettes: what backgrounds, cards, and chips are
 * tinted with — so the whole app can shift mood, not just the accent color.
 * Each style carries a light set and a subtly-tinted dark set.
 */
enum class SurfaceStyle(
    val key: String,
    val lightBg: Color,
    val lightSurface: Color,
    val lightCard: Color,
    val lightVariant: Color,
    val lightBorder: Color,
    val darkBg: Color,
    val darkSurface: Color,
    val darkCard: Color,
    val darkBorder: Color,
) {
    /** The signature warm greige-lavender (default). */
    LINEN(
        "linen",
        Snow, SnowSurface, SnowCard, SnowVariant, SnowBorder,
        Ink, InkSurface, InkCard, InkBorder,
    ),

    /** Brighter, near-neutral — the closest to classic white. */
    PEARL(
        "pearl",
        Color(0xFFF0EFF4), Color(0xFFF5F4F8), Color(0xFFFBFAFD),
        Color(0xFFE3E1EA), Color(0xFFD6D4DF),
        Color(0xFF0E0F13), Color(0xFF16171D), Color(0xFF1E2027), Color(0xFF2B2D37),
    ),

    /** Warm cream and coffee tones. */
    SAND(
        "sand",
        Color(0xFFECE7DF), Color(0xFFF2EEE7), Color(0xFFF8F4EE),
        Color(0xFFDFD8CC), Color(0xFFD2CABB),
        Color(0xFF12100B), Color(0xFF1A1712), Color(0xFF231F18), Color(0xFF322C22),
    ),

    /** Calm green-gray, easy on long reading sessions. */
    SAGE(
        "sage",
        Color(0xFFE4EAE4), Color(0xFFECF0EB), Color(0xFFF2F5F1),
        Color(0xFFD5DDD4), Color(0xFFC7D1C6),
        Color(0xFF0B120E), Color(0xFF121915), Color(0xFF19231D), Color(0xFF27342B),
    ),

    /** Cool blue-gray, crisp and focused. */
    OCEAN(
        "ocean",
        Color(0xFFE3E8F0), Color(0xFFEAEEF5), Color(0xFFF1F4F9),
        Color(0xFFD3DAE6), Color(0xFFC5CEDD),
        Color(0xFF0A0F1A), Color(0xFF101624), Color(0xFF161E30), Color(0xFF232D45),
    );

    companion object {
        fun fromKey(key: String): SurfaceStyle = entries.firstOrNull { it.key == key } ?: LINEN
    }
}

@Composable
fun VocatimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentKey: String = "violet",
    surfaceKey: String = "linen",
    content: @Composable () -> Unit,
) {
    val accent = Accent.fromKey(accentKey)
    val style = SurfaceStyle.fromKey(surfaceKey)
    val base = if (darkTheme) DarkColors else LightColors
    val primary = if (darkTheme) accent.dark else accent.light
    val scheme = if (darkTheme) {
        base.copy(
            primary = primary,
            primaryContainer = primary,
            secondary = if (accent == Accent.TEAL) accent.dark else base.secondary,
            background = style.darkBg,
            surface = style.darkSurface,
            surfaceVariant = style.darkCard,
            surfaceContainer = style.darkSurface,
            surfaceContainerHigh = style.darkCard,
            surfaceContainerHighest = style.darkCard,
            outlineVariant = style.darkBorder,
        )
    } else {
        base.copy(
            primary = primary,
            primaryContainer = primary,
            secondary = if (accent == Accent.TEAL) accent.dark else base.secondary,
            background = style.lightBg,
            surface = style.lightBg,
            surfaceVariant = style.lightVariant,
            surfaceContainer = style.lightSurface,
            surfaceContainerHigh = style.lightCard,
            surfaceContainerHighest = style.lightCard,
            outlineVariant = style.lightBorder,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = VocatimTypography,
        shapes = VocatimShapes,
        content = content,
    )
}
