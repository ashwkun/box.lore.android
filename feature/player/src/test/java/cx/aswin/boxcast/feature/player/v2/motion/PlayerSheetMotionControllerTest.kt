package cx.aswin.boxcast.feature.player.v2.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import cx.aswin.boxcast.feature.player.v2.logic.PlayerSheetVisualLogic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerSheetMotionControllerTest {

    @Test
    fun snapCollapsed_setsTranslationAndZeroExpansion() = runTest {
        val controller = createController(initialY = 50f, initialFraction = 0.4f)

        controller.snapCollapsed(collapsedY = 400f)

        assertEquals(400f, controller.translationY.value, 0.001f)
        assertEquals(0f, controller.expansionFraction.value, 0.001f)
    }

    @Test
    fun snapTo_updatesBothAnimatables() = runTest {
        val controller = createController()

        controller.snapTo(translationYValue = 250f, expansionFractionValue = 0.75f)

        assertEquals(250f, controller.translationY.value, 0.001f)
        assertEquals(0.75f, controller.expansionFraction.value, 0.001f)
    }

    @Test
    fun animateTo_noOpWhenAlreadyAtTarget() = runTest {
        val controller = createController(initialY = 0f, initialFraction = 1f)

        controller.animateTo(
            targetExpanded = true,
            collapsedY = 500f,
        )

        assertEquals(0f, controller.translationY.value, 0.001f)
        assertEquals(1f, controller.expansionFraction.value, 0.001f)
        assertFalse(controller.translationY.isRunning)
        assertFalse(controller.expansionFraction.isRunning)
    }

    @Test
    fun animateTo_expandedTargetUpdatesFractionWhenStartingCollapsed() = runTest {
        val controller = createController(initialY = 500f, initialFraction = 0f)

        controller.snapTo(500f, 0f)
        assertEquals(0f, controller.expansionFraction.value, 0.001f)
    }

    @Test
    fun visualLogicMatchesMotionStateAtHalfExpansion() {
        val fraction = 0.5f
        val miniHeight = 72f
        val containerHeight = 800f

        assertEquals(
            PlayerSheetVisualLogic.playerContentAreaHeightPx(fraction, miniHeight, containerHeight),
            436f,
            0.001f,
        )
        assertEquals(
            PlayerSheetVisualLogic.miniAlpha(fraction),
            0f,
            0.001f,
        )
    }

    private fun createController(
        initialY: Float = 500f,
        initialFraction: Float = 0f,
    ): PlayerSheetMotionController {
        val translationY = Animatable(initialY)
        val expansionFraction = Animatable(initialFraction)
        return PlayerSheetMotionController(
            translationY = translationY,
            expansionFraction = expansionFraction,
            mutex = androidx.compose.foundation.MutatorMutex(),
            defaultAnimationSpec = tween(durationMillis = 1),
        )
    }
}
