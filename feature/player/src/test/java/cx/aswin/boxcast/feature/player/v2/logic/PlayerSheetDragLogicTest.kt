package cx.aswin.boxcast.feature.player.v2.logic

import cx.aswin.boxcast.feature.player.v2.PlayerSheetState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSheetDragLogicTest {

    @Test
    fun `drag distance above threshold expands when dragging up`() {
        val state = PlayerSheetDragLogic.resolveTargetState(
            accumulatedDragY = -20f,
            verticalVelocity = 0f,
            expansionFraction = 0.1f,
            minDragThresholdPx = 10f,
        )
        assertEquals(PlayerSheetState.EXPANDED, state)
    }

    @Test
    fun `drag distance above threshold collapses when dragging down`() {
        val state = PlayerSheetDragLogic.resolveTargetState(
            accumulatedDragY = 20f,
            verticalVelocity = 0f,
            expansionFraction = 0.9f,
            minDragThresholdPx = 10f,
        )
        assertEquals(PlayerSheetState.COLLAPSED, state)
    }

    @Test
    fun `fast upward flick expands when drag distance is small`() {
        val state = PlayerSheetDragLogic.resolveTargetState(
            accumulatedDragY = 2f,
            verticalVelocity = -200f,
            expansionFraction = 0.2f,
            minDragThresholdPx = 10f,
        )
        assertEquals(PlayerSheetState.EXPANDED, state)
    }

    @Test
    fun `fast downward flick collapses when drag distance is small`() {
        val state = PlayerSheetDragLogic.resolveTargetState(
            accumulatedDragY = -2f,
            verticalVelocity = 200f,
            expansionFraction = 0.8f,
            minDragThresholdPx = 10f,
        )
        assertEquals(PlayerSheetState.COLLAPSED, state)
    }

    @Test
    fun `fraction above half expands when drag and velocity are below thresholds`() {
        val state = PlayerSheetDragLogic.resolveTargetState(
            accumulatedDragY = 0f,
            verticalVelocity = 0f,
            expansionFraction = 0.51f,
            minDragThresholdPx = 10f,
        )
        assertEquals(PlayerSheetState.EXPANDED, state)
    }

    @Test
    fun `fraction at half collapses when drag and velocity are below thresholds`() {
        val state = PlayerSheetDragLogic.resolveTargetState(
            accumulatedDragY = 0f,
            verticalVelocity = 0f,
            expansionFraction = 0.5f,
            minDragThresholdPx = 10f,
        )
        assertEquals(PlayerSheetState.COLLAPSED, state)
    }

    @Test
    fun `drag threshold takes priority over velocity and fraction`() {
        val state = PlayerSheetDragLogic.resolveTargetState(
            accumulatedDragY = -30f,
            verticalVelocity = 500f,
            expansionFraction = 0.1f,
            minDragThresholdPx = 10f,
        )
        assertEquals(PlayerSheetState.EXPANDED, state)
    }

    @Test
    fun `clampDragTranslationY allows overscroll within mini player fraction`() {
        val collapsedY = 600f
        val expandedY = 0f
        val miniHeight = 72f

        assertEquals(-14.4f, PlayerSheetDragLogic.clampDragTranslationY(-20f, expandedY, collapsedY, miniHeight), 0.001f)
        assertEquals(614.4f, PlayerSheetDragLogic.clampDragTranslationY(700f, expandedY, collapsedY, miniHeight), 0.001f)
        assertEquals(300f, PlayerSheetDragLogic.clampDragTranslationY(300f, expandedY, collapsedY, miniHeight), 0.001f)
    }

    @Test
    fun `expansionFractionFromDrag maps translation to fraction`() {
        val collapsedY = 500f
        val expandedY = 0f

        assertEquals(0f, PlayerSheetDragLogic.expansionFractionFromDrag(0f, 500f, 500f, collapsedY, expandedY), 0.001f)
        assertEquals(1f, PlayerSheetDragLogic.expansionFractionFromDrag(0f, 500f, 0f, collapsedY, expandedY), 0.001f)
        assertEquals(0.5f, PlayerSheetDragLogic.expansionFractionFromDrag(0f, 500f, 250f, collapsedY, expandedY), 0.001f)
    }

    @Test
    fun `expansionFractionFromDrag clamps to zero and one`() {
        assertEquals(0f, PlayerSheetDragLogic.expansionFractionFromDrag(0f, 500f, 700f, 500f, 0f), 0.001f)
        assertEquals(1f, PlayerSheetDragLogic.expansionFractionFromDrag(0.8f, 500f, -100f, 500f, 0f), 0.001f)
    }

    @Test
    fun `expansionFractionFromDrag clamps when collapsed and expanded Y are equal`() {
        assertEquals(1f, PlayerSheetDragLogic.expansionFractionFromDrag(0.4f, 100f, 50f, 100f, 100f), 0.001f)
    }
}
