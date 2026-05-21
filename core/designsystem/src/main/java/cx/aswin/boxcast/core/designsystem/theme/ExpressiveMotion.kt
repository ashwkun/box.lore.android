package cx.aswin.boxcast.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    val BouncySpring = spring<Float>(
        dampingRatio = 0.5f, // Bouncy, but slightly more damped to prevent jitter
        stiffness = 300f // Slow enough to see the bounce
    )
    
    // Quick spring for press
    val QuickSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // Formal spring for professional reveals (M3 Expressive)
    val FormalSpring = spring<Float>(
        dampingRatio = 0.8f, // Grounded, minimal bounce
        stiffness = 400f // Slightly faster for a punchy reveal
    )

    // Sleek Fade Spec (App Store style)
    val SleekFadeSpec = tween<Float>(
        durationMillis = 500,
        easing = LinearOutSlowInEasing
    )
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
    isolate: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            if (shape != null) {
                clip = true
                this.shape = shape
            }
        }
        .semantics {
            role = Role.Button
            onClick(label = null) {
                currentOnClick()
                true
            }
        }
        .pointerInput(isolate) {
            awaitEachGesture {
                val downInitial = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val consumedByParent = downInitial.isConsumed

                val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Main)
                
                // If a child (like the Play button) has already consumed the down event, we ignore it.
                // We know it was consumed by a child if it is consumed in the Main pass but was NOT consumed in the Initial pass.
                val consumedByChild = down.isConsumed && !consumedByParent
                if (consumedByChild) {
                    return@awaitEachGesture
                }
                
                // If we want to isolate this gesture from parent clickables (e.g. child buttons inside a card), 
                // we consume the down event.
                if (isolate) {
                    down.consume()
                }

                // Start scale-down animation instantly on down press
                scope.launch {
                    scale.animateTo(
                        targetValue = 0.85f,
                        animationSpec = ExpressiveMotion.QuickSpring
                    )
                }

                var isCancelled = false
                val pointerId = down.id

                while (true) {
                    val event = awaitPointerEvent(pass = androidx.compose.ui.input.pointer.PointerEventPass.Final)
                    if (event.changes.isEmpty()) {
                        isCancelled = true
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null) {
                        isCancelled = true
                        break
                    }
                    if (change.isConsumed && !(!change.previousPressed && change.pressed)) {
                        isCancelled = true
                        break
                    }
                    
                    // Check if pointer is up
                    if (change.changedToUp()) {
                        change.consume()
                        break
                    }

                    // Check if the pointer moved outside the bounds of the element
                    val position = change.position
                    val isInside = position.x >= 0 && position.x < size.width &&
                                   position.y >= 0 && position.y < size.height
                    if (!isInside) {
                        isCancelled = true
                        break
                    }
                }

                if (isCancelled) {
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = ExpressiveMotion.FormalSpring
                        )
                    }
                } else {
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = ExpressiveMotion.BouncySpring
                        )
                    }
                    // Trigger the click callback immediately for instant responsiveness
                    currentOnClick()
                }
            }
        }
}

// Keep backward compatibility for callers passing interactionSource
@Suppress("UNUSED_PARAMETER")
fun Modifier.expressiveClickable(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource?,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    onClick: () -> Unit
): Modifier = expressiveClickable(enabled = enabled, shape = shape, isolate = false, onClick = onClick)
