package cx.aswin.boxcast.feature.player.v2.logic

import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry

/** Pure layout math for player sheet visual interpolation (unit-testable). */
object PlayerSheetVisualLogic {
    fun playerContentAreaHeightPx(
        expansionFraction: Float,
        miniHeightPx: Float,
        containerHeightPx: Float,
    ): Float = lerp(miniHeightPx, containerHeightPx, expansionFraction)

    fun topCornerRadiusPx(
        expansionFraction: Float,
        collapsedCornerPx: Float = PlayerChromeGeometry.MiniPlayerCollapsedCorner.value,
        expandedCornerPx: Float = PlayerChromeGeometry.SheetTopCornerExpanded.value,
    ): Float = lerp(collapsedCornerPx, expandedCornerPx, expansionFraction)

    fun bottomCornerRadiusPx(
        expansionFraction: Float,
        dockedCornerPx: Float = PlayerChromeGeometry.MiniPlayerDockedBottomCorner.value,
        expandedCornerPx: Float = PlayerChromeGeometry.SheetTopCornerExpanded.value,
    ): Float = lerp(dockedCornerPx, expandedCornerPx, expansionFraction)

    fun horizontalPaddingPx(
        expansionFraction: Float,
        collapsedPaddingPx: Float = PlayerChromeGeometry.MiniPlayerHorizontalInset.value,
    ): Float = lerp(collapsedPaddingPx, 0f, expansionFraction)

    fun elevationPx(expansionFraction: Float): Float = lerp(3f, 16f, expansionFraction)

    fun miniAlpha(expansionFraction: Float): Float =
        (1f - expansionFraction * 2f).coerceIn(0f, 1f)

    fun fullPlayerAlpha(expansionFraction: Float): Float =
        ((expansionFraction - 0.25f).coerceIn(0f, 0.75f) / 0.75f)

    fun fullPlayerTranslationYPx(expansionFraction: Float): Float =
        lerp(24f, 0f, fullPlayerAlpha(expansionFraction))

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction
}
