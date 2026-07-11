package cx.aswin.boxcast.feature.player.v2.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSheetVisualLogicTest {

    @Test
    fun `player content area height lerps between mini and full container`() {
        assertEquals(72f, PlayerSheetVisualLogic.playerContentAreaHeightPx(0f, 72f, 800f), 0.001f)
        assertEquals(800f, PlayerSheetVisualLogic.playerContentAreaHeightPx(1f, 72f, 800f), 0.001f)
        assertEquals(436f, PlayerSheetVisualLogic.playerContentAreaHeightPx(0.5f, 72f, 800f), 0.001f)
    }

    @Test
    fun `corner radii interpolate from collapsed docked values to expanded flat top`() {
        assertEquals(32f, PlayerSheetVisualLogic.topCornerRadiusPx(0f), 0.001f)
        assertEquals(0f, PlayerSheetVisualLogic.topCornerRadiusPx(1f), 0.001f)
        assertEquals(12f, PlayerSheetVisualLogic.bottomCornerRadiusPx(0f), 0.001f)
        assertEquals(0f, PlayerSheetVisualLogic.bottomCornerRadiusPx(1f), 0.001f)
    }

    @Test
    fun `horizontal padding fades out as sheet expands`() {
        assertEquals(12f, PlayerSheetVisualLogic.horizontalPaddingPx(0f), 0.001f)
        assertEquals(0f, PlayerSheetVisualLogic.horizontalPaddingPx(1f), 0.001f)
        assertEquals(6f, PlayerSheetVisualLogic.horizontalPaddingPx(0.5f), 0.001f)
    }

    @Test
    fun `elevation ramps from mini shadow to full player shadow`() {
        assertEquals(3f, PlayerSheetVisualLogic.elevationPx(0f), 0.001f)
        assertEquals(16f, PlayerSheetVisualLogic.elevationPx(1f), 0.001f)
        assertEquals(9.5f, PlayerSheetVisualLogic.elevationPx(0.5f), 0.001f)
    }

    @Test
    fun `mini alpha fades out by halfway through expansion`() {
        assertEquals(1f, PlayerSheetVisualLogic.miniAlpha(0f), 0.001f)
        assertEquals(0f, PlayerSheetVisualLogic.miniAlpha(0.5f), 0.001f)
        assertEquals(0f, PlayerSheetVisualLogic.miniAlpha(1f), 0.001f)
        assertEquals(0.5f, PlayerSheetVisualLogic.miniAlpha(0.25f), 0.001f)
    }

    @Test
    fun `full player alpha stays hidden until quarter expansion then ramps to one`() {
        assertEquals(0f, PlayerSheetVisualLogic.fullPlayerAlpha(0f), 0.001f)
        assertEquals(0f, PlayerSheetVisualLogic.fullPlayerAlpha(0.24f), 0.001f)
        assertEquals(0f, PlayerSheetVisualLogic.fullPlayerAlpha(0.25f), 0.001f)
        assertEquals(1f, PlayerSheetVisualLogic.fullPlayerAlpha(1f), 0.001f)
        assertEquals(0.5f, PlayerSheetVisualLogic.fullPlayerAlpha(0.625f), 0.001f)
    }

    @Test
    fun `full player translation Y eases from offset to zero with alpha`() {
        assertEquals(24f, PlayerSheetVisualLogic.fullPlayerTranslationYPx(0f), 0.001f)
        assertEquals(0f, PlayerSheetVisualLogic.fullPlayerTranslationYPx(1f), 0.001f)
        assertEquals(12f, PlayerSheetVisualLogic.fullPlayerTranslationYPx(0.625f), 0.001f)
    }

    @Test
    fun `custom corner radii are respected`() {
        assertEquals(20f, PlayerSheetVisualLogic.topCornerRadiusPx(0f, collapsedCornerPx = 20f, expandedCornerPx = 4f), 0.001f)
        assertEquals(4f, PlayerSheetVisualLogic.topCornerRadiusPx(1f, collapsedCornerPx = 20f, expandedCornerPx = 4f), 0.001f)
    }
}
