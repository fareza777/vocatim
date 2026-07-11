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

    /** Proper purple — unmistakably orchid, light and dark. */
    ORCHID(
        "orchid",
        Color(0xFFE3D3F7), Color(0xFFEADEF9), Color(0xFFF2E9FC),
        Color(0xFFD0B8F0), Color(0xFFBF9FE8),
        Color(0xFF140B22), Color(0xFF1D1130), Color(0xFF281843), Color(0xFF3D2761),
    ),

    /** Real green, not a gray with regrets. */
    SAGE(
        "sage",
        Color(0xFFCFE5CD), Color(0xFFDAEBD8), Color(0xFFE6F2E4),
        Color(0xFFB6D6B3), Color(0xFF9FC79B),
        Color(0xFF0A1710), Color(0xFF0F1F16), Color(0xFF16291D), Color(0xFF24402E),
    ),

    /** Warm amber sand, café tones. */
    SAND(
        "sand",
        Color(0xFFEBDDBE), Color(0xFFF0E5CC), Color(0xFFF6EDD9),
        Color(0xFFDFC9A0), Color(0xFFD1B885),
        Color(0xFF171105), Color(0xFF20180A), Color(0xFF2B2010), Color(0xFF3E2F1A),
    ),

    /** Unambiguous blue, crisp and focused. */
    OCEAN(
        "ocean",
        Color(0xFFCCDDF6), Color(0xFFD8E5F8), Color(0xFFE3EDFA),
        Color(0xFFB4CDF0), Color(0xFF9ABBE8),
        Color(0xFF081120), Color(0xFF0D1930), Color(0xFF132342), Color(0xFF20345D),
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
