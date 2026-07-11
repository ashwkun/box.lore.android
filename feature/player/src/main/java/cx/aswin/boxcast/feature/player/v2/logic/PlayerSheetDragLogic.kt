package cx.aswin.boxcast.feature.player.v2.logic

import cx.aswin.boxcast.feature.player.v2.PlayerSheetState

/** Resolves collapsed vs expanded from vertical drag gesture (mirrors PlayerSheetV2). */
object PlayerSheetDragLogic {
    const val DEFAULT_VELOCITY_THRESHOLD_PX = 150f

    fun resolveTargetState(
        accumulatedDragY: Float,
        verticalVelocity: Float,
        expansionFraction: Float,
        minDragThresholdPx: Float,
        velocityThreshold: Float = DEFAULT_VELOCITY_THRESHOLD_PX,
    ): PlayerSheetState = when {
        kotlin.math.abs(accumulatedDragY) > minDragThresholdPx ->
            if (accumulatedDragY < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
        kotlin.math.abs(verticalVelocity) > velocityThreshold ->
            if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
        expansionFraction > 0.5f -> PlayerSheetState.EXPANDED
        else -> PlayerSheetState.COLLAPSED
    }

    fun clampDragTranslationY(
        proposedY: Float,
        expandedY: Float,
        collapsedY: Float,
        miniPlayerHeightPx: Float,
        overscrollFraction: Float = 0.2f,
    ): Float {
        val minY = expandedY - miniPlayerHeightPx * overscrollFraction
        val maxY = collapsedY + miniPlayerHeightPx * overscrollFraction
        return proposedY.coerceIn(minY, maxY)
    }

    fun expansionFractionFromDrag(
        initialFraction: Float,
        initialTranslationY: Float,
        newTranslationY: Float,
        collapsedY: Float,
        expandedY: Float,
    ): Float {
        val denom = (collapsedY - expandedY).coerceAtLeast(1f)
        val dragRatio = (initialTranslationY - newTranslationY) / denom
        return (initialFraction + dragRatio).coerceIn(0f, 1f)
    }
}
