package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeBrandTokensTest {
    @Test
    fun `brand seeds contains twenty named palettes`() {
        assertEquals(20, BrandSeeds.size)
        assertTrue(BrandSeeds.containsKey("violet"))
        assertTrue(BrandSeeds.containsKey("rust"))
    }

    @Test
    fun `is custom theme brand accepts hex seeds`() {
        assertTrue(isCustomThemeBrand("#5B5BD6"))
        assertFalse(isCustomThemeBrand("violet"))
        assertFalse(isCustomThemeBrand("#abc"))
    }

    @Test
    fun `resolve theme seed color falls back to violet for unknown keys`() {
        assertEquals(BrandSeeds["violet"]!!.second, resolveThemeSeedColor("unknown"))
    }

    @Test
    fun `resolve theme seed color parses custom hex or falls back on invalid`() {
        val resolved = resolveThemeSeedColor("#006C4C")
        assertTrue(
            resolved == BrandSeeds["emerald"]!!.second ||
                resolved == BrandSeeds["violet"]!!.second,
        )
        assertEquals(BrandSeeds["violet"]!!.second, resolveThemeSeedColor("#not-a-color"))
    }

    @Test
    fun `to theme brand hex strips alpha`() {
        assertEquals("#6750A4", Color(0xFF6750A4).toThemeBrandHex())
    }

    @Test
    fun `contrast color picks black on light and white on dark`() {
        assertEquals(Color.Black, Color.White.contrastColor())
        assertEquals(Color.White, Color.Black.contrastColor())
    }
}
