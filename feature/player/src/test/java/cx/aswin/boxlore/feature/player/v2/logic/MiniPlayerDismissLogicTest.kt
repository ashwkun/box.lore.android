package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerDismissLogicTest {
    private val threshold = 100f

    @Test
    fun hiddenConfirmationAppearsPastHalfThreshold() {
        assertEquals(
            ConfirmationVisibility.UNCHANGED,
            confirmationVisibility(50f, threshold, currentlyVisible = false),
        )
        assertEquals(
            ConfirmationVisibility.SHOW,
            confirmationVisibility(50.1f, threshold, currentlyVisible = false),
        )
        assertEquals(
            ConfirmationVisibility.SHOW,
            confirmationVisibility(-50.1f, threshold, currentlyVisible = false),
        )
    }

    @Test
    fun visibleConfirmationHidesBelowThirtyPercent() {
        assertEquals(
            ConfirmationVisibility.UNCHANGED,
            confirmationVisibility(31f, threshold, currentlyVisible = true),
        )
        assertEquals(
            ConfirmationVisibility.HIDE,
            confirmationVisibility(29f, threshold, currentlyVisible = true),
        )
        assertEquals(
            ConfirmationVisibility.HIDE,
            confirmationVisibility(-29f, threshold, currentlyVisible = true),
        )
    }

    @Test
    fun visibilityStaysUnchangedInsideHysteresisBand() {
        assertEquals(
            ConfirmationVisibility.UNCHANGED,
            confirmationVisibility(40f, threshold, currentlyVisible = false),
        )
        assertEquals(
            ConfirmationVisibility.UNCHANGED,
            confirmationVisibility(40f, threshold, currentlyVisible = true),
        )
    }

    @Test
    fun dismissalRequiresStrictlyMoreThanThreshold() {
        assertFalse(shouldConfirmDismiss(100f, threshold))
        assertFalse(shouldConfirmDismiss(-100f, threshold))
        assertTrue(shouldConfirmDismiss(100.1f, threshold))
        assertTrue(shouldConfirmDismiss(-100.1f, threshold))
    }

    @Test
    fun directionTracksOffsetSign() {
        assertEquals(-1, dismissDirection(-1f))
        assertEquals(1, dismissDirection(0f))
        assertEquals(1, dismissDirection(1f))
    }

    @Test
    fun confirmationTargetUsesDirectionAndOvertravel() {
        assertEquals(-150f, confirmationTarget(-75f, threshold), 0.001f)
        assertEquals(150f, confirmationTarget(75f, threshold), 0.001f)
        assertEquals(150f, confirmationTarget(0f, threshold), 0.001f)
    }
}
