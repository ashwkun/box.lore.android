package cx.aswin.boxcast.feature.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class SwipeDirection {
    Left, Right
}

class SwipeableCardState(
    val coroutineScope: CoroutineScope,
    val onSwiped: (SwipeDirection) -> Unit
) {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)

    fun swipe(direction: SwipeDirection) {
        coroutineScope.launch {
            val targetX = if (direction == SwipeDirection.Left) -1500f else 1500f
            offset.animateTo(
                targetValue = Offset(targetX, offset.value.y),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            onSwiped(direction)
            offset.snapTo(Offset.Zero)
        }
    }

    fun drag(dragAmount: Offset) {
        coroutineScope.launch {
            offset.snapTo(
                Offset(
                    x = offset.value.x + dragAmount.x,
                    y = 0f
                )
            )
        }
    }

    fun reset() {
        coroutineScope.launch {
            offset.animateTo(
                targetValue = Offset.Zero,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
}

@Composable
fun rememberSwipeableCardState(
    key: Any?,
    onSwiped: (SwipeDirection) -> Unit
): SwipeableCardState {
    val scope = rememberCoroutineScope()
    return remember(key) {
        SwipeableCardState(scope, onSwiped)
    }
}
