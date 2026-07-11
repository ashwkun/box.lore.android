package cx.aswin.boxcast.feature.player.v2.logic

import kotlin.math.abs

/** Pure swipe-to-dismiss thresholds for mini player (unit-testable). */
object MiniPlayerDismissLogic {
    const val PILL_PREVIEW_FRACTION = 0.5f
    const val PILL_HIDE_FRACTION = 0.3f
    const val REVEAL_OVERSHOOT_MULTIPLIER = 1.5f

    fun shouldDismissOnDragEnd(offsetXPx: Float, dismissThresholdPx: Float): Boolean =
        abs(offsetXPx) > dismissThresholdPx

    fun shouldShowConfirmPillWhileDragging(
        offsetXPx: Float,
        dismissThresholdPx: Float,
        currentlyShowing: Boolean,
    ): Boolean = when {
        currentlyShowing && shouldHideConfirmPill(offsetXPx, dismissThresholdPx) -> false
        !currentlyShowing && abs(offsetXPx) > dismissThresholdPx * PILL_PREVIEW_FRACTION -> true
        else -> currentlyShowing
    }

    fun shouldHideConfirmPill(offsetXPx: Float, dismissThresholdPx: Float): Boolean =
        abs(offsetXPx) < dismissThresholdPx * PILL_HIDE_FRACTION

    fun swipeDirection(offsetXPx: Float): Int = if (offsetXPx < 0) -1 else 1

    fun revealSnapTargetPx(swipeDirection: Int, dismissThresholdPx: Float): Float =
        swipeDirection * dismissThresholdPx * REVEAL_OVERSHOOT_MULTIPLIER
}
