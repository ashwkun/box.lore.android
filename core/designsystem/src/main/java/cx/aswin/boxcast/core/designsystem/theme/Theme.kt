package cx.aswin.boxcast.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Surface style modes that control background/surface lightness.
 */
object SurfaceStyles {
    const val STANDARD = "standard"
    const val AMOLED = "amoled"
    const val PURE_WHITE = "purewhite"
    const val HIGH_CONTRAST = "highcontrast"

    /** Display labels for the settings UI. */
    val entries = listOf(
        Entry(STANDARD, "Standard", "Default Material You surfaces"),
        Entry(AMOLED, "AMOLED Black", "Pure black · saves battery on OLED"),
        Entry(PURE_WHITE, "Pure White", "Maximum brightness · clean look"),
        Entry(HIGH_CONTRAST, "High Contrast", "Enhanced readability · bolder colors")
    )

    data class Entry(val key: String, val label: String, val subtitle: String)
}

@Composable
fun BoxCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeBrand: String = "violet",
    surfaceStyle: String = SurfaceStyles.STANDARD,
    content: @Composable () -> Unit
) {
    // AMOLED forces dark mode, Pure White forces light mode — ensures correct text contrast
    val effectiveDarkTheme = when (surfaceStyle) {
        SurfaceStyles.AMOLED -> true
        SurfaceStyles.PURE_WHITE -> false
        else -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseScheme = if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Apply surface style overrides to dynamic colors
            applySurfaceStyle(baseScheme, effectiveDarkTheme, surfaceStyle)
        }
        else -> {
            val seedColor = BrandSeeds[themeBrand]?.second ?: BrandSeeds["violet"]!!.second
            generateBrandColorScheme(seedColor, effectiveDarkTheme, surfaceStyle)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BoxCastTypography,
        shapes = BoxCastShapes,
        content = content
    )
}

/**
 * Applies surface style overrides to an existing ColorScheme (used for dynamic color schemes).
 */
private fun applySurfaceStyle(base: ColorScheme, isDark: Boolean, surfaceStyle: String): ColorScheme {
    return when {
        surfaceStyle == SurfaceStyles.AMOLED && isDark -> base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF242424)
        )
        surfaceStyle == SurfaceStyles.PURE_WHITE && !isDark -> base.copy(
            background = Color.White,
            surface = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainer = Color(0xFFF5F5F5),
            surfaceContainerHigh = Color(0xFFEEEEEE),
            surfaceContainerHighest = Color(0xFFE5E5E5)
        )
        surfaceStyle == SurfaceStyles.HIGH_CONTRAST && isDark -> base.copy(
            background = Color(0xFF050505),
            surface = Color(0xFF050505),
            surfaceContainerLowest = Color(0xFF020202),
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainer = Color(0xFF101010),
            surfaceContainerHigh = Color(0xFF1C1C1C),
            surfaceContainerHighest = Color(0xFF2A2A2A),
            outline = base.outline.copy(alpha = 1f),
            primary = base.primary.saturate(1.3f)
        )
        surfaceStyle == SurfaceStyles.HIGH_CONTRAST && !isDark -> base.copy(
            background = Color.White,
            surface = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF8F8F8),
            surfaceContainer = Color(0xFFF0F0F0),
            surfaceContainerHigh = Color(0xFFE6E6E6),
            surfaceContainerHighest = Color(0xFFDADADA),
            outline = base.outline.copy(alpha = 1f),
            primary = base.primary.saturate(1.2f)
        )
        else -> base
    }
}

/**
 * Increases the saturation of a color by the given factor.
 */
private fun Color.saturate(factor: Float): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.RGBToHSL(
        (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsl
    )
    hsl[1] = (hsl[1] * factor).coerceIn(0f, 1f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/**
 * Generates a complete Material 3 color scheme algorithmically from a single semantic seed color.
 * Accepts a surfaceStyle to control background/surface lightness levels.
 */
fun generateBrandColorScheme(seedColor: Color, isDark: Boolean, surfaceStyle: String = SurfaceStyles.STANDARD): ColorScheme {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.RGBToHSL(
        (seedColor.red * 255).toInt(),
        (seedColor.green * 255).toInt(),
        (seedColor.blue * 255).toInt(),
        hsl
    )

    fun hslToColor(h: Float, s: Float, l: Float): Color {
        return Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))
    }

    val hue = hsl[0]
    val baseSat = hsl[1].coerceIn(0.3f, 0.8f)

    // Adjust saturation based on surface style
    val sat = if (surfaceStyle == SurfaceStyles.HIGH_CONTRAST) {
        (baseSat * if (isDark) 1.3f else 1.2f).coerceIn(0.3f, 0.95f)
    } else baseSat

    // Surface lightness values vary by surface style
    val surfaceLevels = getSurfaceLevels(isDark, surfaceStyle, hue, sat)

    return if (isDark) {
        darkColorScheme(
            primary = hslToColor(hue, sat, 0.7f),
            onPrimary = hslToColor(hue, sat * 0.3f, 0.15f),
            primaryContainer = hslToColor(hue, sat * 0.5f, 0.25f),
            onPrimaryContainer = hslToColor(hue, sat * 0.3f, 0.9f),
            secondary = hslToColor(hue, sat * 0.4f, 0.7f),
            onSecondary = hslToColor(hue, sat * 0.3f, 0.15f),
            secondaryContainer = hslToColor(hue, sat * 0.4f, 0.25f),
            onSecondaryContainer = hslToColor(hue, sat * 0.3f, 0.9f),
            tertiary = hslToColor(hue + 30f, sat * 0.5f, 0.7f),
            onTertiary = hslToColor(hue + 30f, sat * 0.3f, 0.15f),
            tertiaryContainer = hslToColor(hue + 30f, sat * 0.5f, 0.25f),
            onTertiaryContainer = hslToColor(hue + 30f, sat * 0.3f, 0.9f),
            background = surfaceLevels.background,
            onBackground = hslToColor(hue, sat * 0.1f, 0.90f),
            surface = surfaceLevels.surface,
            onSurface = hslToColor(hue, sat * 0.1f, 0.90f),
            surfaceVariant = hslToColor(hue, sat * 0.3f, 0.20f),
            onSurfaceVariant = hslToColor(hue, sat * 0.2f, 0.75f),
            outline = hslToColor(hue, sat * 0.25f, if (surfaceStyle == SurfaceStyles.HIGH_CONTRAST) 0.70f else 0.60f),
            outlineVariant = hslToColor(hue, sat * 0.2f, 0.30f),
            surfaceContainerLowest = surfaceLevels.containerLowest,
            surfaceContainerLow = surfaceLevels.containerLow,
            surfaceContainer = surfaceLevels.container,
            surfaceContainerHigh = surfaceLevels.containerHigh,
            surfaceContainerHighest = surfaceLevels.containerHighest
        )
    } else {
        lightColorScheme(
            primary = hslToColor(hue, sat, 0.4f),
            onPrimary = hslToColor(hue, sat * 0.2f, 0.95f),
            primaryContainer = hslToColor(hue, sat * 0.6f, 0.9f),
            onPrimaryContainer = hslToColor(hue, sat * 0.5f, 0.15f),
            secondary = hslToColor(hue, sat * 0.4f, 0.4f),
            onSecondary = hslToColor(hue, sat * 0.2f, 0.95f),
            secondaryContainer = hslToColor(hue, sat * 0.4f, 0.9f),
            onSecondaryContainer = hslToColor(hue, sat * 0.4f, 0.15f),
            tertiary = hslToColor(hue + 30f, sat * 0.5f, 0.4f),
            onTertiary = hslToColor(hue + 30f, sat * 0.2f, 0.95f),
            tertiaryContainer = hslToColor(hue + 30f, sat * 0.5f, 0.9f),
            onTertiaryContainer = hslToColor(hue + 30f, sat * 0.4f, 0.15f),
            background = surfaceLevels.background,
            onBackground = hslToColor(hue, sat * 0.1f, 0.10f),
            surface = surfaceLevels.surface,
            onSurface = hslToColor(hue, sat * 0.1f, 0.10f),
            surfaceVariant = hslToColor(hue, sat * 0.3f, 0.90f),
            onSurfaceVariant = hslToColor(hue, sat * 0.2f, 0.30f),
            outline = hslToColor(hue, sat * 0.25f, if (surfaceStyle == SurfaceStyles.HIGH_CONTRAST) 0.35f else 0.45f),
            outlineVariant = hslToColor(hue, sat * 0.25f, 0.80f),
            surfaceContainerLowest = surfaceLevels.containerLowest,
            surfaceContainerLow = surfaceLevels.containerLow,
            surfaceContainer = surfaceLevels.container,
            surfaceContainerHigh = surfaceLevels.containerHigh,
            surfaceContainerHighest = surfaceLevels.containerHighest
        )
    }
}

/**
 * Surface lightness levels for each surface style mode.
 */
private data class SurfaceLevels(
    val background: Color,
    val surface: Color,
    val containerLowest: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color
)

private fun getSurfaceLevels(isDark: Boolean, surfaceStyle: String, hue: Float, sat: Float): SurfaceLevels {
    fun hsl(h: Float, s: Float, l: Float) = Color(
        androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
    )

    return when {
        // AMOLED: Pure black background, near-black containers
        surfaceStyle == SurfaceStyles.AMOLED && isDark -> SurfaceLevels(
            background = Color.Black,
            surface = Color.Black,
            containerLowest = Color.Black,
            containerLow = hsl(hue, sat * 0.05f, 0.02f),
            container = hsl(hue, sat * 0.08f, 0.04f),
            containerHigh = hsl(hue, sat * 0.10f, 0.07f),
            containerHighest = hsl(hue, sat * 0.12f, 0.10f)
        )
        // AMOLED in light mode: behave as standard
        surfaceStyle == SurfaceStyles.AMOLED && !isDark -> getSurfaceLevels(isDark, SurfaceStyles.STANDARD, hue, sat)

        // Pure White: #FFFFFF background, near-white containers
        surfaceStyle == SurfaceStyles.PURE_WHITE && !isDark -> SurfaceLevels(
            background = Color.White,
            surface = Color.White,
            containerLowest = Color.White,
            containerLow = hsl(hue, sat * 0.05f, 0.98f),
            container = hsl(hue, sat * 0.08f, 0.96f),
            containerHigh = hsl(hue, sat * 0.10f, 0.93f),
            containerHighest = hsl(hue, sat * 0.12f, 0.89f)
        )
        // Pure White in dark mode: behave as standard
        surfaceStyle == SurfaceStyles.PURE_WHITE && isDark -> getSurfaceLevels(isDark, SurfaceStyles.STANDARD, hue, sat)

        // High Contrast Dark: deeper blacks, wider spread
        surfaceStyle == SurfaceStyles.HIGH_CONTRAST && isDark -> SurfaceLevels(
            background = hsl(hue, sat * 0.05f, 0.03f),
            surface = hsl(hue, sat * 0.05f, 0.03f),
            containerLowest = hsl(hue, sat * 0.03f, 0.01f),
            containerLow = hsl(hue, sat * 0.08f, 0.05f),
            container = hsl(hue, sat * 0.10f, 0.09f),
            containerHigh = hsl(hue, sat * 0.12f, 0.14f),
            containerHighest = hsl(hue, sat * 0.15f, 0.22f)
        )
        // High Contrast Light: pure white, wider spread
        surfaceStyle == SurfaceStyles.HIGH_CONTRAST && !isDark -> SurfaceLevels(
            background = Color.White,
            surface = Color.White,
            containerLowest = Color.White,
            containerLow = hsl(hue, sat * 0.08f, 0.97f),
            container = hsl(hue, sat * 0.12f, 0.93f),
            containerHigh = hsl(hue, sat * 0.15f, 0.88f),
            containerHighest = hsl(hue, sat * 0.18f, 0.82f)
        )

        // Standard Dark
        isDark -> SurfaceLevels(
            background = hsl(hue, sat * 0.15f, 0.10f),
            surface = hsl(hue, sat * 0.15f, 0.10f),
            containerLowest = hsl(hue, sat * 0.15f, 0.05f),
            containerLow = hsl(hue, sat * 0.15f, 0.08f),
            container = hsl(hue, sat * 0.15f, 0.12f),
            containerHigh = hsl(hue, sat * 0.15f, 0.15f),
            containerHighest = hsl(hue, sat * 0.15f, 0.20f)
        )
        // Standard Light
        else -> SurfaceLevels(
            background = hsl(hue, sat * 0.1f, 0.98f),
            surface = hsl(hue, sat * 0.1f, 0.98f),
            containerLowest = hsl(hue, sat * 0.1f, 1.00f),
            containerLow = hsl(hue, sat * 0.1f, 0.96f),
            container = hsl(hue, sat * 0.15f, 0.93f),
            containerHigh = hsl(hue, sat * 0.15f, 0.90f),
            containerHighest = hsl(hue, sat * 0.15f, 0.86f)
        )
    }
}
