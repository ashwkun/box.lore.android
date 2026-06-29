package cx.aswin.boxcast.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope

/**
 * Material 3 Expressive Motion Physics.
 * 
 * Guidelines:
 * - Springs over Easing.
 * - Tactile Scaling (0.85 down, 1.0 up with bounce).
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue

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
        dampingRatio = 0.45f, // Very bouncy!
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
    @Suppress("UNUSED_PARAMETER") isolate: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = if (isPressed) ExpressiveMotion.QuickSpring else ExpressiveMotion.BouncySpring,
        label = "expressiveClickScale"
    )
    this
        .graphicsLayer {
            scaleX = scaleState.value
            scaleY = scaleState.value
            if (shape != null) {
                clip = true
                this.shape = shape
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

// Keep backward compatibility for callers passing interactionSource
@Suppress("UNUSED_PARAMETER")
fun Modifier.expressiveClickable(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource?,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    onClick: () -> Unit
): Modifier = composed {
    val localInteractionSource = interactionSource ?: remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by localInteractionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = if (isPressed) ExpressiveMotion.QuickSpring else ExpressiveMotion.BouncySpring,
        label = "expressiveClickScale"
    )
    this
        .graphicsLayer {
            scaleX = scaleState.value
            scaleY = scaleState.value
            if (shape != null) {
                clip = true
                this.shape = shape
            }
        }
        .clickable(
            interactionSource = localInteractionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}
