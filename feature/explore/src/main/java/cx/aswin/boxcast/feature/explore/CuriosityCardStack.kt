package cx.aswin.boxcast.feature.explore

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.network.model.DailyCuriosityDto
import kotlin.math.roundToInt

@Composable
fun CuriosityCardStack(
    questions: List<DailyCuriosityDto>,
    isCurrentlyPlaying: (String) -> Boolean,
    isCurrentlyLoading: (String) -> Boolean, // Dynamic loading state detector
    onSwipeLeft: (DailyCuriosityDto) -> Unit,
    onSwipeRight: (DailyCuriosityDto) -> Unit,
    onPlayClick: (DailyCuriosityDto) -> Unit,
    onEpisodeClick: (DailyCuriosityDto) -> Unit,
    onPodcastClick: (DailyCuriosityDto) -> Unit,
    accentColor: Color,
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
            .height(490.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Render up to 4 cards in stack representation (reversed order so top is drawn last)
        val cardsToShow = questions.take(4).reversed()

        cardsToShow.forEach { daily ->
            val stackIndex = questions.indexOf(daily)
            val isTopCard = stackIndex == 0

            val swipeState = rememberSwipeableCardState(key = daily.episode.id) { direction ->
                if (direction == SwipeDirection.Left) {
                    onSwipeLeft(daily)
                } else {
                    onSwipeRight(daily)
                }
            }

            // Dynamic spring animations for stack position changes
            val scaleTarget = when (stackIndex) {
                0 -> 1f
                1 -> 0.96f
                2 -> 0.92f
                else -> 0.88f
            }
            val scale by animateFloatAsState(
                targetValue = scaleTarget,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "CardScale"
            )

            // Offset background cards upwards to reveal top edges cleanly without clipping
            val offsetTarget = when (stackIndex) {
                0 -> 0.dp
                1 -> (-14).dp
                2 -> (-26).dp
                else -> (-36).dp
            }
            val verticalOffset by animateDpAsState(
                targetValue = offsetTarget,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "CardOffset"
            )

            val rotationTarget = when (stackIndex) {
                0 -> 0f
                1 -> -3.5f
                2 -> 3.5f
                else -> -1.5f
            }
            val rotationAngle by animateFloatAsState(
                targetValue = rotationTarget,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "CardRotation"
            )

            val alphaTarget = when (stackIndex) {
                0 -> 1f
                1 -> 0.85f
                2 -> 0.65f
                else -> 0.45f
            }
            val alphaVal by animateFloatAsState(
                targetValue = alphaTarget,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                label = "CardAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .offset {
                        if (isTopCard) {
                            IntOffset(
                                swipeState.offset.value.x.roundToInt(),
                                swipeState.offset.value.y.roundToInt()
                            )
                        } else {
                            IntOffset(0, 0)
                        }
                    }
                    .offset(y = verticalOffset)
                    .scale(scale)
                    .graphicsLayer {
                        rotationZ = if (isTopCard) {
                            rotationAngle + (swipeState.offset.value.x / 40f)
                        } else {
                            rotationAngle
                        }
                        alpha = alphaVal
                        cameraDistance = 12f * density
                    }
                    .then(
                        if (isTopCard) {
                            Modifier.pointerInput(daily.episode.id) {
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
                        } else {
                            Modifier
                        }
                    )
            ) {
                CuriosityCardContent(
                    daily = daily,
                    isCurrentlyPlaying = isCurrentlyPlaying(daily.episode.id.toString()),
                    isCurrentlyLoading = isCurrentlyLoading(daily.episode.id.toString()),
                    accentColor = accentColor,
                    onPlayClick = { if (isTopCard) onPlayClick(daily) },
                    onEpisodeClick = { if (isTopCard) onEpisodeClick(daily) },
                    onPodcastClick = { if (isTopCard) onPodcastClick(daily) }
                )
            }
        }
    }
}

@Composable
private fun CuriosityCardContent(
    daily: DailyCuriosityDto,
    isCurrentlyPlaying: Boolean,
    isCurrentlyLoading: Boolean,
    accentColor: Color,
    onPlayClick: () -> Unit,
    onEpisodeClick: () -> Unit,
    onPodcastClick: () -> Unit,
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
            .fillMaxWidth()
            .height(480.dp)
            .expressiveClickable(onClick = onEpisodeClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full-bleed blurred background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
            ) {
                OptimizedImage(
                    url = coverArt,
                    proxyWidth = 200,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. Dark scrim over blurred BG (High opacity for text readability)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
            )

            // 3. Content Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // 3a. Crisp square artwork
                OptimizedImage(
                    url = coverArt,
                    proxyWidth = 200,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3b. Podcast title row indicating it is clickable
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .expressiveClickable(onClick = onPodcastClick)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = (daily.episode.feedTitle ?: "Podcast").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Go to podcast",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // First weight spacer to push the text block slightly down from the artwork
                Spacer(modifier = Modifier.weight(1f))

                // 3c. Question Text (Hook - Sized at 24.sp for gorgeous large titles)
                Text(
                    text = daily.question,
                    fontSize = 24.sp,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = 30.sp,
                    textAlign = TextAlign.Center
                )

                // 16dp spacing between the hook question and explanation description
                Spacer(modifier = Modifier.height(16.dp))

                // 3d. Explanation Text
                Text(
                    text = daily.explanation ?: "",
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                // Second slightly larger weight spacer to push the play button to the bottom while maintaining vertical symmetry
                Spacer(modifier = Modifier.weight(1.2f))

                // 3e. Custom circular play button
                CircularPlayButton(
                    isPlaying = isCurrentlyPlaying,
                    isLoading = isCurrentlyLoading,
                    accentColor = accentColor,
                    onClick = onPlayClick
                )
            }
        }
    }
}

@Composable
private fun CircularPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .then(
                if (isPlaying) {
                    Modifier.background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.8f)
                            )
                        )
                    )
                } else {
                    Modifier.background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                Color.White.copy(alpha = 0.9f)
                            )
                        )
                    )
                }
            )
            .expressiveClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            BoxLoreLoader.CircularWavy(
                size = 28.dp,
                color = if (isPlaying) Color.White else Color.Black
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (isPlaying) Color.White else Color.Black,
                modifier = Modifier
                    .size(24.dp)
                    .then(
                        if (!isPlaying) Modifier.offset(x = 1.dp) else Modifier
                    )
            )
        }
    }
}
