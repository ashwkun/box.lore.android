package cx.aswin.boxcast.feature.explore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.network.model.DailyCuriosityDto
import kotlin.math.roundToInt

@Composable
fun CuriosityCardStack(
    questions: List<DailyCuriosityDto>,
    isCurrentlyPlaying: (String) -> Boolean,
    onSwipeLeft: (DailyCuriosityDto) -> Unit,
    onSwipeRight: (DailyCuriosityDto) -> Unit,
    onPlayClick: (DailyCuriosityDto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (questions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(340.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No more curiosities for today!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center
    ) {
        // Render up to 3 cards in stack representation (reversed order so top is drawn last)
        val cardsToShow = questions.take(3).reversed()

        cardsToShow.forEach { daily ->
            val isTopCard = daily.episode.id.toString() == questions.first().episode.id.toString()

            if (isTopCard) {
                val swipeState = rememberSwipeableCardState(key = daily.episode.id) { direction ->
                    if (direction == SwipeDirection.Left) {
                        onSwipeLeft(daily)
                    } else {
                        onSwipeRight(daily)
                    }
                }

                var isFlipped by remember(daily.episode.id) { mutableStateOf(false) }

                val cardRotationY by animateFloatAsState(
                    targetValue = if (isFlipped) 180f else 0f,
                    animationSpec = tween(durationMillis = 400),
                    label = "CardFlip"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                swipeState.offset.value.x.roundToInt(),
                                swipeState.offset.value.y.roundToInt()
                            )
                        }
                        .graphicsLayer {
                            rotationZ = (swipeState.offset.value.x / 40f)
                            this.rotationY = cardRotationY
                            cameraDistance = 12f * density
                        }
                        .pointerInput(daily.episode.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    val offsetX = swipeState.offset.value.x
                                    if (offsetX > 400f) {
                                        swipeState.swipe(SwipeDirection.Right)
                                    } else if (offsetX < -400f) {
                                        swipeState.swipe(SwipeDirection.Left)
                                    } else {
                                        swipeState.reset()
                                    }
                                },
                                onDragCancel = {
                                    swipeState.reset()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    swipeState.drag(dragAmount)
                                }
                            )
                        }
                ) {
                    CuriosityCardContent(
                        daily = daily,
                        isCurrentlyPlaying = isCurrentlyPlaying(daily.episode.id.toString()),
                        isFlipped = isFlipped,
                        rotationY = cardRotationY,
                        onFlipToggle = { isFlipped = !isFlipped },
                        onPlayClick = { onPlayClick(daily) }
                    )
                }
            } else {
                // Lower cards in stack
                val stackLevel = questions.indexOf(daily) // 1 or 2
                val scale = if (stackLevel == 1) 0.95f else 0.90f
                val verticalOffset = if (stackLevel == 1) 12.dp else 24.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = verticalOffset)
                        .scale(scale)
                        .graphicsLayer {
                            alpha = if (stackLevel == 1) 0.85f else 0.6f
                        }
                ) {
                    CuriosityCardContent(
                        daily = daily,
                        isCurrentlyPlaying = false,
                        isFlipped = false,
                        rotationY = 0f,
                        onFlipToggle = {},
                        onPlayClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun CuriosityCardContent(
    daily: DailyCuriosityDto,
    isCurrentlyPlaying: Boolean,
    isFlipped: Boolean,
    rotationY: Float,
    onFlipToggle: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverArt = daily.episode.image ?: daily.episode.feedImage ?: ""

    OutlinedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxSize()
            .expressiveClickable(onClick = onFlipToggle)
    ) {
        if (rotationY > 90f) {
            // Back Side (Flipped View)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.rotationY = 180f
                    }
            ) {
                // Background cover art with solid container tint for legibility
                OptimizedImage(
                    url = coverArt,
                    proxyWidth = 300,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Header Row with Episode details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OptimizedImage(
                                url = coverArt,
                                proxyWidth = 120,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = daily.episode.feedTitle ?: "Podcast",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val durationSec = daily.episode.duration
                                val durationMin = if (durationSec != null && durationSec > 0) {
                                    "${durationSec / 60} min"
                                } else {
                                    "Daily micro-story"
                                }
                                Text(
                                    text = durationMin,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Explanation text
                        Text(
                            text = daily.explanation ?: "No explanation available.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 24.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Premium listen button
                    FilledTonalButton(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = if (isCurrentlyPlaying) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (isCurrentlyPlaying) Icons.Filled.VolumeUp else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCurrentlyPlaying) "Playing micro-story" else "Listen to micro-story",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Front side card structure: Unified Full Image Background with Blurred Bottom Panel
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Full Image Background
                OptimizedImage(
                    url = coverArt,
                    proxyWidth = 600,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Blurred Bottom Panel (Fading out smoothly towards the top)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            // Alpha mask the content of this Box to fade smoothly
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black
                                    )
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    // Blurred Artwork Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(80.dp)
                    ) {
                        OptimizedImage(
                            url = coverArt,
                            proxyWidth = 300,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Solid surface container tint overlay for M3 color tone consistency
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            )
                    )
                }

                // 4. Question & Interaction text overlayed on the scrim
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = daily.question,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 36.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap to Reveal Details",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Swipe to Skip / Queue",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
