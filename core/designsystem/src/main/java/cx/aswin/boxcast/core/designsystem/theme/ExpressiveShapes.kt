package cx.aswin.boxcast.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * Cached shape implementation for custom paths to avoid recreating paths during draw/layout.
 */
class CachedPathShape(private val pathBuilder: (Size) -> Path) : androidx.compose.ui.graphics.Shape {
    private var lastSize: Size? = null
    private var lastPath: Path? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size != lastSize || lastPath == null) {
            lastSize = size
            lastPath = pathBuilder(size)
        }
        return Outline.Generic(lastPath!!)
    }
}

/**
 * Cached shape implementation for RoundedPolygons.
 */
class CachedPolygonShape(
    private val numVertices: Int,
    private val isStar: Boolean,
    private val innerRadiusRatio: Float = 0.5f,
    private val roundingRatio: Float = 0.0f
) : androidx.compose.ui.graphics.Shape {
    private var lastSize: Size? = null
    private var lastPath: Path? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        }
        if (size != lastSize || lastPath == null) {
            lastSize = size
            val radius = size.minDimension / 2
            val centerX = size.width / 2
            val centerY = size.height / 2
            val polygon = if (isStar) {
                RoundedPolygon.star(
                    numVerticesPerRadius = numVertices,
                    radius = radius,
                    innerRadius = radius * innerRadiusRatio,
                    rounding = CornerRounding(radius * roundingRatio),
                    centerX = centerX,
                    centerY = centerY
                )
            } else {
                if (roundingRatio > 0f) {
                    RoundedPolygon(
                        numVertices = numVertices,
                        radius = radius,
                        centerX = centerX,
                        centerY = centerY,
                        rounding = CornerRounding(radius * roundingRatio)
                    )
                } else {
                    RoundedPolygon(
                        numVertices = numVertices,
                        radius = radius,
                        centerX = centerX,
                        centerY = centerY
                    )
                }
            }
            lastPath = polygon.toPath().asComposePath()
        }
        return Outline.Generic(lastPath!!)
    }
}

/**
 * Expressive Shapes for BoxCast.
 * Comprehensive implementation of Material 3 Expressive Shapes.
 * Uses CachedPolygonShape and CachedPathShape to eliminate draw-time allocations.
 */
object ExpressiveShapes {

    enum class DecorativeType {
        Puffy,
        PuffyDiamond,
        SoftBurst,
        Cookie4
    }

    // --- Basic Shapes ---
    val Circle = CircleShape
    val Square = RoundedCornerShape(0)
    val Pill = CircleShape 
    val Oval = CircleShape 
    val Full = RoundedCornerShape(50) 

    val Slanted = CachedPathShape { size ->
        val path = Path()
        val slant = size.width * 0.15f
        path.moveTo(slant, 0f)
        path.lineTo(size.width, 0f)
        path.lineTo(size.width - slant, size.height)
        path.lineTo(0f, size.height)
        path.close()
        path
    }

    val Triangle = CachedPolygonShape(
        numVertices = 3,
        isStar = false,
        roundingRatio = 0.1f
    )

    val Semicircle = CachedPathShape { size ->
        val path = Path()
        path.addArc(Rect(0f, 0f, size.width, size.height * 2), 180f, 180f)
        path.close()
        path
    }

    val Arch = CachedPathShape { size ->
        val path = Path()
        path.moveTo(0f, size.height)
        path.lineTo(0f, size.height / 2)
        path.arcTo(
            rect = Rect(0f, 0f, size.width, size.height),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false
        )
        path.lineTo(size.width, size.height)
        path.close()
        path
    }
    
    val Fan = CachedPathShape { size ->
         val path = Path()
         path.moveTo(0f, size.height)
         path.lineTo(0f, 0f)
         path.arcTo(Rect(0f, 0f, size.width * 2, size.height * 2), 180f, 90f, false)
         path.lineTo(size.width, size.height)
         path.close()
         path
    }

    val Arrow = CachedPolygonShape(
        numVertices = 3,
        isStar = false,
        roundingRatio = 0.2f
    )

    // --- Polygonal / Stars ---
    val Star = CachedPolygonShape(
        numVertices = 5,
        isStar = true,
        innerRadiusRatio = 0.4f,
        roundingRatio = 0.05f
    )

    val Sunny = CachedPolygonShape(
        numVertices = 8,
        isStar = true,
        innerRadiusRatio = 0.7f,
        roundingRatio = 0.05f
    )
    
    val VerySunny = CachedPolygonShape(
        numVertices = 12,
        isStar = true,
        innerRadiusRatio = 0.75f,
        roundingRatio = 0.05f
    )

    val Diamond = CachedPolygonShape(
        numVertices = 4,
        isStar = false,
        roundingRatio = 0.1f
    )
        
    val Pentagon = CachedPolygonShape(
        numVertices = 5,
        isStar = false
    )
    
    val Hexagon = CachedPolygonShape(
        numVertices = 6,
        isStar = false
    )

    val Gem = CachedPolygonShape(
        numVertices = 6,
        isStar = false,
        roundingRatio = 0.15f
    )

    // --- Cookies (Rounded N-gons) ---
    val Cookie4 = CachedPolygonShape(4, false, roundingRatio = 0.2f)
    val Cookie6 = CachedPolygonShape(6, false, roundingRatio = 0.2f)
    val Cookie7 = CachedPolygonShape(7, false, roundingRatio = 0.2f)
    val Cookie9 = CachedPolygonShape(9, false, roundingRatio = 0.2f)
    val Cookie12 = CachedPolygonShape(12, false, roundingRatio = 0.2f)

    // --- Expressive ---
    val GhostIsh = CachedPathShape { size ->
        val path = Path()
        val w = size.width
        val h = size.height
        path.moveTo(0f, h * 0.5f)
        path.arcTo(Rect(0f, 0f, w, h), 180f, 180f, false)
        path.lineTo(w, h * 0.8f)
        path.cubicTo(w * 0.75f, h, w * 0.25f, h * 0.6f, 0f, h * 0.8f)
        path.close()
        path
    }

    val Clover4 = CachedPolygonShape(
        numVertices = 4,
        isStar = true,
        innerRadiusRatio = 0.1f,
        roundingRatio = 0.25f
    )
        
    val Clover8 = CachedPolygonShape(
        numVertices = 8,
        isStar = true,
        innerRadiusRatio = 0.3f,
        roundingRatio = 0.15f
    )

    val Burst = CachedPolygonShape(
        numVertices = 12,
        isStar = true,
        innerRadiusRatio = 0.6f
    )
        
    val SoftBurst = CachedPolygonShape(
        numVertices = 10,
        isStar = true,
        innerRadiusRatio = 0.7f,
        roundingRatio = 0.05f
    )
        
    val Boom = CachedPolygonShape(
        numVertices = 16,
        isStar = true,
        innerRadiusRatio = 0.4f
    )
    
    val SoftBoom = CachedPolygonShape(
        numVertices = 16,
        isStar = true,
        innerRadiusRatio = 0.4f,
        roundingRatio = 0.03f
    )

    val Flower = CachedPolygonShape(
        numVertices = 8,
        isStar = true,
        innerRadiusRatio = 0.6f,
        roundingRatio = 0.05f
    )

    val Puffy = CachedPolygonShape(
        numVertices = 8,
        isStar = true,
        innerRadiusRatio = 0.7f,
        roundingRatio = 0.2f
    )
    
    val PuffyDiamond = CachedPolygonShape(
        numVertices = 4,
        isStar = true,
        innerRadiusRatio = 0.5f,
        roundingRatio = 0.2f
    )

    val Bun = CachedPathShape { size ->
        val path = Path()
        path.addOval(Rect(0f, 0f, size.width, size.height * 0.65f))
        path.addOval(Rect(0f, size.height * 0.35f, size.width, size.height))
        path
    }

    val Heart = CachedPathShape { size ->
        val width = size.width
        val height = size.height
        val path = Path()
        path.moveTo(width / 2, height * 0.25f)
        path.cubicTo(width, 0f, width, height * 0.5f, width / 2, height)
        path.cubicTo(0f, height * 0.5f, 0f, 0f, width / 2, height * 0.25f)
        path
    }
        
    val Clamshell = CachedPathShape { size ->
         val path = Path()
         path.addArc(Rect(0f, 0f, size.width, size.height), 0f, 180f)
         path
    }

    val Decorative = listOf(
        Sunny, VerySunny, 
        Cookie4, Cookie6, Cookie9, Cookie12,
        Burst, SoftBurst, Boom, SoftBoom,
        Flower, Puffy, PuffyDiamond,
        Heart, Bun, GhostIsh,
        Diamond, Gem, Pentagon
    )

    fun androidPath(
        type: DecorativeType,
        width: Float,
        height: Float
    ): android.graphics.Path {
        val shape = when (type) {
            DecorativeType.Puffy -> Puffy
            DecorativeType.PuffyDiamond -> PuffyDiamond
            DecorativeType.SoftBurst -> SoftBurst
            DecorativeType.Cookie4 -> Cookie4
        }
        val outline = shape.createOutline(
            size = Size(width, height),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f)
        )
        return when (outline) {
            is Outline.Generic -> outline.path.asAndroidPath()
            is Outline.Rectangle -> android.graphics.Path().apply {
                addRect(0f, 0f, width, height, android.graphics.Path.Direction.CW)
            }
            is Outline.Rounded -> android.graphics.Path().apply {
                addRoundRect(
                    0f,
                    0f,
                    width,
                    height,
                    outline.roundRect.topLeftCornerRadius.x,
                    outline.roundRect.topLeftCornerRadius.y,
                    android.graphics.Path.Direction.CW
                )
            }
        }
    }

    // --- Raw Polygons (for LoadingIndicator) ---
    object Polygons {
        private const val RADIUS = 1f
        
        val Star: RoundedPolygon = RoundedPolygon.star(
            numVerticesPerRadius = 5,
            radius = RADIUS,
            innerRadius = RADIUS * 0.4f,
            rounding = CornerRounding(radius = RADIUS * 0.05f)
        )

        val Sunny: RoundedPolygon = RoundedPolygon.star(
            numVerticesPerRadius = 8,
            radius = RADIUS,
            innerRadius = RADIUS * 0.7f,
            rounding = CornerRounding(radius = RADIUS * 0.05f)
        )

        val Burst: RoundedPolygon = RoundedPolygon.star(
            numVerticesPerRadius = 12,
            radius = RADIUS,
            innerRadius = RADIUS * 0.6f
        )
        
        val Cookie4: RoundedPolygon = RoundedPolygon(
            numVertices = 4,
            radius = RADIUS,
            rounding = CornerRounding(RADIUS * 0.2f)
        )
        
        val Cookie12: RoundedPolygon = RoundedPolygon(
            numVertices = 12,
            radius = RADIUS,
            rounding = CornerRounding(RADIUS * 0.2f)
        )
    }
}
