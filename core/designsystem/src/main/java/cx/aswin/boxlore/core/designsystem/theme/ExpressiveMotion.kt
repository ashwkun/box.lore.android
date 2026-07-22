package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive Motion Physics.
 *
 * Guidelines:
 * - Springs over Easing.
 * - Tactile Scaling (0.85 down, 1.0 up with bounce).
 */
object ExpressiveMotion {
    // Very bouncy spring for release
    val BouncySpring =
        spring<Float>(
            dampingRatio = 0.45f, // Very bouncy!
            stiffness = 300f, // Slow enough to see the bounce
        )

    // Quick spring for press
    val QuickSpring =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        )

    // Formal spring for professional reveals (M3 Expressive)
    val FormalSpring =
        spring<Float>(
            dampingRatio = 0.8f, // Grounded, minimal bounce
            stiffness = 400f, // Slightly faster for a punchy reveal
        )

    /**
     * Large spatial morphs (player sheet expand/collapse).
     *
     * Matches M3 Expressive *slow spatial* stiffness (200), but critically damped:
     * a full-screen sheet must ease into the anchor in one continuous motion —
     * underdamped bounce reads as a second animation fighting travel direction
     * (especially collapse: past mini, then jumps back up).
     */
    val SpatialLargeSpring =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 200f,
        )

    // Sleek Fade Spec (App Store style)
    val SleekFadeSpec =
        tween<Float>(
            durationMillis = 500,
            easing = LinearOutSlowInEasing,
        )
}

@Composable
private fun rememberExpressiveVisualScale(interactionSource: MutableInteractionSource): Animatable<Float, AnimationVector1D> {
    val scale = remember(interactionSource) { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        var animationJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            val target =
                when (interaction) {
                    is PressInteraction.Press -> 0.85f
                    is PressInteraction.Release,
                    is PressInteraction.Cancel,
                    -> 1f
                    else -> null
                } ?: return@collect

            animationJob?.cancel()
            animationJob =
                launch {
                    scale.animateTo(
                        targetValue = target,
                        animationSpec =
                            if (target < 1f) {
                                ExpressiveMotion.QuickSpring
                            } else {
                                ExpressiveMotion.BouncySpring
                            },
                    )
                }
        }
    }
    return scale
}

/**
 * Expressive clickable modifier that always shows visible animation:
 * 1. On tap: Quickly shrink to 0.85
 * 2. Then immediately bounce back to 1.0
 * 3. Fire onClick when animation starts
 */
fun Modifier.expressiveClickable(
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    indication: Indication? = null,
    @Suppress("UNUSED_PARAMETER") isolate: Boolean = false,
    onClick: () -> Unit,
): Modifier =
    composed {
        val interactionSource = remember { MutableInteractionSource() }
        val visualScale = rememberExpressiveVisualScale(interactionSource)
        this
            .graphicsLayer {
                scaleX = visualScale.value
                scaleY = visualScale.value
                if (shape != null) {
                    clip = true
                    this.shape = shape
                }
            }.clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                onClick = onClick,
            )
    }

// Keep backward compatibility for callers passing interactionSource
@Suppress("UNUSED_PARAMETER")
fun Modifier.expressiveClickable(
    interactionSource: MutableInteractionSource?,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    indication: Indication? = null,
    onClick: () -> Unit,
): Modifier =
    composed {
        val localInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val visualScale = rememberExpressiveVisualScale(localInteractionSource)
        this
            .graphicsLayer {
                scaleX = visualScale.value
                scaleY = visualScale.value
                if (shape != null) {
                    clip = true
                    this.shape = shape
                }
            }.clickable(
                interactionSource = localInteractionSource,
                indication = indication,
                enabled = enabled,
                onClick = onClick,
            )
    }

/**
 * Same tactile press animation as [expressiveClickable], with an additional accessible
 * long-press affordance (e.g. opening a feedback menu). Long-click also announces
 * [onLongClickLabel] for screen readers via the underlying [combinedClickable].
 */
fun Modifier.expressiveClickable(
    onLongClick: () -> Unit,
    onLongClickLabel: String? = null,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    indication: Indication? = null,
    onClick: () -> Unit,
): Modifier =
    composed {
        val interactionSource = remember { MutableInteractionSource() }
        val visualScale = rememberExpressiveVisualScale(interactionSource)
        this
            .graphicsLayer {
                scaleX = visualScale.value
                scaleY = visualScale.value
                if (shape != null) {
                    clip = true
                    this.shape = shape
                }
            }.combinedClickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                onClick = onClick,
            )
    }
