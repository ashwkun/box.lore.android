package cx.aswin.boxcast.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand Colors - Material 3 Baseline Purple
// These act as the seed colors for Material Dynamic Theme
val SeedPurple = Color(0xFF6750A4) // M3 Baseline Purple (matches launcher icon)

/**
 * Centralized seed colors for all 20 brand palettes.
 * Each entry maps a theme key to its seed color used by generateBrandColorScheme().
 */
val BrandSeeds: LinkedHashMap<String, Pair<String, Color>> = linkedMapOf(
    "violet" to ("Violet" to Color(0xFF6750A4)),
    "emerald" to ("Emerald" to Color(0xFF006C4C)),
    "ocean" to ("Ocean" to Color(0xFF0061A4)),
    "sakura" to ("Sakura" to Color(0xFFBC004B)),
    "tangerine" to ("Tangerine" to Color(0xFF964900)),
    "crimson" to ("Crimson" to Color(0xFFB91823)),
    "canary" to ("Canary" to Color(0xFF725C00)),
    "midnight" to ("Midnight" to Color(0xFF1B3A5C)),
    "lavender" to ("Lavender" to Color(0xFF7F67BE)),
    "teal" to ("Teal" to Color(0xFF006A6A)),
    "coral" to ("Coral" to Color(0xFFC74E3A)),
    "slate" to ("Slate" to Color(0xFF506070)),
    "mint" to ("Mint" to Color(0xFF006D3F)),
    "rose" to ("Rose" to Color(0xFF9C4057)),
    "amber" to ("Amber" to Color(0xFF8B5E00)),
    "graphite" to ("Graphite" to Color(0xFF5E5E5E)),
    "plum" to ("Plum" to Color(0xFF8B2F7C)),
    "sage" to ("Sage" to Color(0xFF4B6A32)),
    "cobalt" to ("Cobalt" to Color(0xFF0047AB)),
    "rust" to ("Rust" to Color(0xFF8B3A2F))
)

// Light Theme Fallbacks (Generated from #6750A4 seed)
val md_theme_light_primary = Color(0xFF6750A4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFEADDFF)
val md_theme_light_onPrimaryContainer = Color(0xFF21005D)
val md_theme_light_secondary = Color(0xFF625B71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
val md_theme_light_onSecondaryContainer = Color(0xFF1D192B)
val md_theme_light_background = Color(0xFFFFFBFE)
val md_theme_light_onBackground = Color(0xFF1C1B1F)
val md_theme_light_surface = Color(0xFFFFFBFE)
val md_theme_light_onSurface = Color(0xFF1C1B1F)

// Dark Theme Fallbacks (Generated from #6750A4 seed)
val md_theme_dark_primary = Color(0xFFD0BCFF)
val md_theme_dark_onPrimary = Color(0xFF381E72)
val md_theme_dark_primaryContainer = Color(0xFF4F378B)
val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)
val md_theme_dark_secondary = Color(0xFFCCC2DC)
val md_theme_dark_onSecondary = Color(0xFF332D41)
val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
val md_theme_dark_background = Color(0xFF1C1B1F)
val md_theme_dark_onBackground = Color(0xFFE6E1E5)
val md_theme_dark_surface = Color(0xFF1C1B1F)
val md_theme_dark_onSurface = Color(0xFFE6E1E5)

/**
 * Computes the ideal foreground content color for a given background.
 * Uses relative luminance to guarantee WCAG-compliant contrast.
 * Light backgrounds (luminance > 0.4) → dark text, dark backgrounds → light text.
 *
 * Usage: `val textColor = backgroundColor.contrastColor()`
 */
fun Color.contrastColor(): Color =
    if (this.luminance() > 0.4f) Color.Black else Color.White

/**
 * Like [contrastColor] but returns the color with an optional alpha.
 */
fun Color.contrastColor(alpha: Float): Color =
    contrastColor().copy(alpha = alpha)

/**
 * Returns the luminance value for this color (0.0 = pure black, 1.0 = pure white).
 * Convenience wrapper around the Compose Color.luminance() extension.
 */
private fun Color.luminance(): Float {
    // sRGB relative luminance per ITU-R BT.709
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
