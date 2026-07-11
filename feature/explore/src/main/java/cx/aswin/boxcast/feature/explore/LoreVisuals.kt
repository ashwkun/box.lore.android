package cx.aswin.boxcast.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

@Composable
internal fun LoreHaloBackground(
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "LoreHalo")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LoreHaloPulse"
    )
    val drift by transition.animateFloat(
        initialValue = -24f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LoreHaloDrift"
    )
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val driftPx = with(density) { drift.dp.toPx() }
    val pageBrush = remember(accentColor, backgroundColor, isDarkTheme) {
        Brush.verticalGradient(
            colors = if (isDarkTheme) {
                listOf(backgroundColor, backgroundColor)
            } else {
                listOf(
                    lerp(backgroundColor, accentColor, 0.07f),
                    backgroundColor,
                    lerp(backgroundColor, accentColor, 0.045f)
                )
            }
        )
    }
    val topBrush = remember(accentColor, isDarkTheme) {
        Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = if (isDarkTheme) 0.28f else 0.48f),
                accentColor.copy(alpha = if (isDarkTheme) 0.08f else 0.16f),
                Color.Transparent
            )
        )
    }
    val bottomBrush = remember(accentColor, isDarkTheme) {
        Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = if (isDarkTheme) 0.20f else 0.36f),
                accentColor.copy(alpha = if (isDarkTheme) 0.05f else 0.11f),
                Color.Transparent
            )
        )
    }
    val sideBrush = remember(accentColor, isDarkTheme) {
        Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = if (isDarkTheme) 0.10f else 0.22f),
                Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBrush)
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-250).dp)
                .size(540.dp)
                .graphicsLayer {
                    translationX = driftPx
                    scaleX = pulse
                    scaleY = pulse
                }
                .background(topBrush, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 150.dp, y = 190.dp)
                .size(440.dp)
                .graphicsLayer {
                    translationX = -driftPx
                    val inversePulse = 2f - pulse
                    scaleX = inversePulse
                    scaleY = inversePulse
                }
                .background(bottomBrush, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-245).dp, y = 20.dp)
                .size(500.dp)
                .graphicsLayer {
                    translationX = driftPx * 0.45f
                    scaleX = 2f - pulse
                    scaleY = 2f - pulse
                }
                .background(sideBrush, CircleShape)
        )
        content()
    }
}
