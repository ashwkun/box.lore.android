package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BoxScope.NewEpisodeBadge(
    modifier: Modifier = Modifier
) {
    // Slow shimmer animation across the NEW badge background (4 seconds loop)
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val baseColor = MaterialTheme.colorScheme.primary
    val shimmerColor = MaterialTheme.colorScheme.primaryContainer
    
    val brush = remember(shimmerOffset, baseColor, shimmerColor) {
        Brush.linearGradient(
            colors = listOf(
                baseColor,
                shimmerColor,
                baseColor
            ),
            start = Offset(shimmerOffset, 0f),
            end = Offset(shimmerOffset + 80f, 0f)
        )
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.surface),
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(top = 4.dp, end = 4.dp)
            .background(brush, RoundedCornerShape(6.dp))
    ) {
        Text(
            text = "NEW",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                lineHeight = 8.sp
            ),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
