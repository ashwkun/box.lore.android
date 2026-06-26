package cx.aswin.boxcast.core.designsystem.theme

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600)
        ),
        label = "ShimmerProgress"
    )

    this.drawBehind {
        val width = size.width
        val height = size.height
        val startX = -width + progress * (3 * width)

        val brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFE0E0E0), // Light Gray
                Color(0xFFF0F0F0), // Lighter Gray
                Color(0xFFE0E0E0), // Light Gray
            ),
            start = Offset(startX, 0f),
            end = Offset(startX + width, height)
        )
        drawRect(brush = brush)
    }
}

// Dark Mode Shimmer (Optional, strictly we should use Theme colors but user asked for M3 Shimmer)
// We will adapt colors based on theme in the Skeleton usage or make this composable smart.
// For now, let's use a standard implementation that accepts colors or uses safe defaults.
fun Modifier.m3Shimmer(
    baseColor: Color = Color.Gray.copy(alpha = 0.2f),
    highlightColor: Color = Color.Gray.copy(alpha = 0.4f),
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600)
        ),
        label = "ShimmerProgress"
    )

    this.clip(shape).drawBehind {
        val width = size.width
        val height = size.height
        val startX = -width + progress * (3 * width)

        val brush = Brush.linearGradient(
            colors = listOf(
                baseColor,
                highlightColor,
                baseColor,
            ),
            start = Offset(startX, 0f),
            end = Offset(startX + width, height)
        )
        drawRect(brush = brush)
    }
}
