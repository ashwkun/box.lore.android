package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes

/**
 * BoxCast M3 Expressive Loaders.
 * Centralized loading states for the application.
 */
object BoxLoreLoader {

    /**
     * Expressive Morphing Loader.
     * Uses Material 3 [ContainedLoadingIndicator] (morphing shapes in a container).
     *
     * @param modifier Modifier for layout.
     * @param size Size of the loader.
     * @param polygons List of shapes to morph between. Defaults to standard M3 set.
     *                 Use `ExpressiveShapes.Polygons.Sunny` etc. for custom sequences.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun Expressive(
        modifier: Modifier = Modifier,
        size: Dp = 64.dp,
        polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
    ) {
        ContainedLoadingIndicator(
            modifier = modifier.size(size),
            polygons = polygons,
        )
    }

    /**
     * Wavy Circular Loader.
     * Uses `CircularWavyProgressIndicator`.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun CircularWavy(
        modifier: Modifier = Modifier,
        progress: Float? = null, // Null for indeterminate
        size: Dp = 48.dp,
        color: Color = MaterialTheme.colorScheme.primary,
        trackColor: Color = color.copy(alpha = 0.2f)
    ) {
        if (progress == null) {
            CircularWavyProgressIndicator(
                modifier = modifier.size(size),
                color = color,
                trackColor = trackColor
            )
        } else {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = modifier.size(size),
                color = color,
                trackColor = trackColor
            )
        }
    }

    /**
     * Wavy Linear Loader.
     * Uses `LinearWavyProgressIndicator`.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun LinearWavy(
        modifier: Modifier = Modifier,
        progress: Float? = null,
        color: Color = MaterialTheme.colorScheme.primary,
        trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        if (progress == null) {
            LinearWavyProgressIndicator(
                modifier = modifier,
                color = color,
                trackColor = trackColor
            )
        } else {
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = modifier,
                color = color,
                trackColor = trackColor,
            )
        }
    }
    
    /**
     * Custom Shape Loader (Fallback/Specific).
     * Rotates a specific Expressive Shape.
     */
    @Composable
    fun Custom(
        modifier: Modifier = Modifier,
        shape: Shape = ExpressiveShapes.Star,
        color: Color = MaterialTheme.colorScheme.primary,
        size: Dp = 48.dp
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "loader_rotate")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing)
            ),
            label = "angle"
        )
        
        Box(
            modifier = modifier
                .size(size)
                .graphicsLayer { rotationZ = angle }
                .background(color = color, shape = shape)
        )
    }
}
