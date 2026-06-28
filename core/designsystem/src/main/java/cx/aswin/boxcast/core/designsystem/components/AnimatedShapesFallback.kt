package cx.aswin.boxcast.core.designsystem.components

import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.random.Random

data class PlacedShape(val x: Float, val y: Float, val size: Int, val shape: androidx.compose.ui.graphics.Shape)

@Composable
fun AnimatedShapesFallback() {
    LogRecomposition(name = "AnimatedShapesFallback")
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val podcastIcon = Icons.Rounded.Podcasts
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)

    // Random positions and sizes calculated once per composition
    val shapes = remember { calculateFallbackPlacedShapes() }

    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .graphicsLayer { clip = true } // Allow clipping at container edge
            .logDrawTime("AnimatedShapesFallbackDraw")
            .logLayoutTime("AnimatedShapesFallbackLayout")
            .drawWithCache {
                val cachedOutlines = shapes.map { placed ->
                    val sizePx = placed.size.dp.toPx()
                    val outline = placed.shape.createOutline(
                        size = Size(sizePx, sizePx),
                        layoutDirection = layoutDirection,
                        density = this
                    )
                    Triple(
                        placed.x.dp.toPx() - (sizePx / 2f),
                        placed.y.dp.toPx() - (sizePx / 2f),
                        outline
                    )
                }
                
                onDrawBehind {
                    cachedOutlines.forEach { (xPx, yPx, outline) ->
                        translate(left = xPx, top = yPx) {
                            drawOutline(
                                outline = outline,
                                color = primaryColor,
                                alpha = 0.05f
                            )
                        }
                    }
                }
            }
    ) {
        // Centered Podcast Icon (Only layout child)
        Icon(
            imageVector = podcastIcon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.Center)
        )
    }
}

private fun calculateFallbackPlacedShapes(): List<PlacedShape> {
    val allShapes = listOf(
        ExpressiveShapes.Sunny, ExpressiveShapes.VerySunny, 
        ExpressiveShapes.Cookie4, ExpressiveShapes.Cookie6, ExpressiveShapes.Cookie9, ExpressiveShapes.Cookie12,
        ExpressiveShapes.Burst, ExpressiveShapes.SoftBurst, ExpressiveShapes.Boom, ExpressiveShapes.SoftBoom,
        ExpressiveShapes.Flower, ExpressiveShapes.Puffy, ExpressiveShapes.PuffyDiamond,
        ExpressiveShapes.Heart, ExpressiveShapes.Bun, ExpressiveShapes.GhostIsh,
        ExpressiveShapes.Diamond, ExpressiveShapes.Gem, ExpressiveShapes.Pentagon
    ).shuffled()
    
    val placedShapes = mutableListOf<PlacedShape>()
    val availableShapes = allShapes.toMutableList()
    
    for (i in 0 until 100) {
        if (placedShapes.size >= 6 || availableShapes.isEmpty()) break
        
        val x = Random.nextFloat() * 350f
        val y = Random.nextFloat() * 600f
        
        if (!isOverlappingWithPlaced(x, y, placedShapes)) {
            val size = 180 + Random.nextInt(170)
            placedShapes.add(PlacedShape(x, y, size, availableShapes.removeAt(0)))
        }
    }
    return placedShapes
}

private fun isOverlappingWithPlaced(x: Float, y: Float, placedShapes: List<PlacedShape>): Boolean {
    for (placed in placedShapes) {
        val dist = kotlin.math.sqrt((x - placed.x) * (x - placed.x) + (y - placed.y) * (y - placed.y))
        if (dist < 200f) return true
    }
    return false
}


/**
 * Custom drawOutline extension function to draw Outline primitives directly,
 * bypassing version/import discrepancies in compose graphics libraries.
 */
fun DrawScope.drawOutline(
    outline: Outline,
    color: Color,
    alpha: Float = 1.0f
) {
    when (outline) {
        is Outline.Rectangle -> {
            drawRect(
                color = color,
                topLeft = outline.rect.topLeft,
                size = outline.rect.size,
                alpha = alpha
            )
        }
        is Outline.Rounded -> {
            val rr = outline.roundRect
            drawRoundRect(
                color = color,
                topLeft = Offset(rr.left, rr.top),
                size = Size(rr.width, rr.height),
                cornerRadius = rr.topLeftCornerRadius,
                alpha = alpha
            )
        }
        is Outline.Generic -> {
            drawPath(
                path = outline.path,
                color = color,
                alpha = alpha
            )
        }
    }
}
