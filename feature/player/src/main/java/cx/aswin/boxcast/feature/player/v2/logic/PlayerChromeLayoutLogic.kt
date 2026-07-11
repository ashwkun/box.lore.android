package cx.aswin.boxcast.feature.player.v2.logic

import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry

object PlayerChromeLayoutLogic {
    fun collapsedTargetYPx(
        containerHeightPx: Float,
        miniPlayerHeightPx: Float = PlayerChromeGeometry.MiniPlayerHeight.value,
        navBarContentHeightPx: Float = PlayerChromeGeometry.NavBarContentHeight.value,
        systemNavBarInsetPx: Float = 0f,
        bottomMarginPx: Float = PlayerChromeGeometry.MiniPlayerBottomSpacer.value,
    ): Float {
        val safeContainer = containerHeightPx.coerceAtLeast(0f)
        val occupiedBottom = miniPlayerHeightPx + navBarContentHeightPx + systemNavBarInsetPx + bottomMarginPx
        return (safeContainer - occupiedBottom).coerceAtLeast(0f)
    }
}
