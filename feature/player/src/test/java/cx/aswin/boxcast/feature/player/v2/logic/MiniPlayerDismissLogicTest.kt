package cx.aswin.boxcast.feature.player.v2.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerDismissLogicTest {

    private val threshold = 100f

    @Test
    fun `shouldDismissOnDragEnd is true only beyond threshold`() {
        assertFalse(MiniPlayerDismissLogic.shouldDismissOnDragEnd(50f, threshold))
        assertFalse(MiniPlayerDismissLogic.shouldDismissOnDragEnd(-50f, threshold))
        assertFalse(MiniPlayerDismissLogic.shouldDismissOnDragEnd(100f, threshold))
        assertTrue(MiniPlayerDismissLogic.shouldDismissOnDragEnd(101f, threshold))
        assertTrue(MiniPlayerDismissLogic.shouldDismissOnDragEnd(-101f, threshold))
    }

    @Test
    fun `shouldShowConfirmPillWhileDragging previews at half threshold`() {
        assertFalse(
            MiniPlayerDismissLogic.shouldShowConfirmPillWhileDragging(40f, threshold, currentlyShowing = false),
        )
        assertTrue(
            MiniPlayerDismissLogic.shouldShowConfirmPillWhileDragging(60f, threshold, currentlyShowing = false),
        )
        assertTrue(
            MiniPlayerDismissLogic.shouldShowConfirmPillWhileDragging(-60f, threshold, currentlyShowing = false),
        )
    }

    @Test
    fun `shouldShowConfirmPillWhileDragging hides when dragged back below hide fraction`() {
        assertFalse(
            MiniPlayerDismissLogic.shouldShowConfirmPillWhileDragging(20f, threshold, currentlyShowing = true),
        )
        assertTrue(
            MiniPlayerDismissLogic.shouldShowConfirmPillWhileDragging(40f, threshold, currentlyShowing = true),
        )
    }

    @Test
    fun `shouldHideConfirmPill uses thirty percent of threshold`() {
        assertTrue(MiniPlayerDismissLogic.shouldHideConfirmPill(20f, threshold))
        assertFalse(MiniPlayerDismissLogic.shouldHideConfirmPill(40f, threshold))
    }

    @Test
    fun `swipeDirection follows sign of offset`() {
        assertEquals(-1, MiniPlayerDismissLogic.swipeDirection(-10f))
        assertEquals(1, MiniPlayerDismissLogic.swipeDirection(10f))
        assertEquals(1, MiniPlayerDismissLogic.swipeDirection(0f))
    }

    @Test
    fun `revealSnapTargetPx overshoots threshold by one and a half times direction`() {
        assertEquals(-150f, MiniPlayerDismissLogic.revealSnapTargetPx(-1, threshold), 0.001f)
        assertEquals(150f, MiniPlayerDismissLogic.revealSnapTargetPx(1, threshold), 0.001f)
    }
}
