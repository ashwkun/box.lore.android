package cx.aswin.boxcast.feature.player.v2.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerChromeLayoutLogicTest {

    @Test
    fun `collapsedTargetYPx subtracts mini player nav bar inset and margin from container height`() {
        // 800 - (72 + 62 + 0 + 8) = 658
        assertEquals(658f, PlayerChromeLayoutLogic.collapsedTargetYPx(800f), 0.001f)
    }

    @Test
    fun `collapsedTargetYPx includes system nav bar inset`() {
        assertEquals(638f, PlayerChromeLayoutLogic.collapsedTargetYPx(800f, systemNavBarInsetPx = 20f), 0.001f)
    }

    @Test
    fun `collapsedTargetYPx never returns negative values`() {
        assertEquals(0f, PlayerChromeLayoutLogic.collapsedTargetYPx(100f), 0.001f)
        assertEquals(0f, PlayerChromeLayoutLogic.collapsedTargetYPx(-50f), 0.001f)
    }

    @Test
    fun `collapsedTargetYPx accepts custom geometry inputs`() {
        val y = PlayerChromeLayoutLogic.collapsedTargetYPx(
            containerHeightPx = 900f,
            miniPlayerHeightPx = 80f,
            navBarContentHeightPx = 70f,
            systemNavBarInsetPx = 10f,
            bottomMarginPx = 12f,
        )
        assertEquals(728f, y, 0.001f)
    }
}
