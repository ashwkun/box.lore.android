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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
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
            .height(530.dp),
        contentAlignment = Alignment.Center
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
                1 -> 0.95f
                2 -> 0.90f
                else -> 0.85f
            }
            val scale by animateFloatAsState(
                targetValue = scaleTarget,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "CardScale"
            )

            val offsetTarget = when (stackIndex) {
                0 -> 0.dp
                1 -> 10.dp
                2 -> 18.dp
                else -> 26.dp
            }
            val verticalOffset by animateDpAsState(
                targetValue = offsetTarget,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "CardOffset"
            )

            val rotationTarget = when (stackIndex) {
                0 -> 0f
                1 -> -7f
                2 -> 7f
                else -> -3.5f
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
                    .fillMaxSize()
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
            .fillMaxSize()
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
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 3a. Badges Row (Dismiss, Info, and Queue)
                CardBadgesRow()

                Spacer(modifier = Modifier.height(16.dp))

                // 3b. Crisp square artwork (120dp)
                OptimizedImage(
                    url = coverArt,
                    proxyWidth = 240,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3c. Podcast title row indicating it is clickable (using keyboard arrow right >)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .expressiveClickable(onClick = onPodcastClick)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = daily.episode.feedTitle ?: "Podcast",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Go to podcast",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3d. Question Text (Hook)
                Text(
                    text = daily.question,
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = 36.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3e. Explanation Text
                Text(
                    text = daily.explanation ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 20.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                // Flexible Spacer 3: Distributes space between Content and Play controls
                Spacer(modifier = Modifier.weight(1f))

                // 3f. Custom pill-style play button - Premium Circular Glassmorphic Play/Pause
                CircularPlayButton(
                    isPlaying = isCurrentlyPlaying,
                    accentColor = accentColor,
                    onClick = onPlayClick
                )
            }
        }
    }
}

@Composable
private fun CardBadgesRow(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dismiss (Left)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Dismiss",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // Info (Center)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = Color(0xFF4DABF7),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Info",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // Queue (Right)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF69DB7C),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun CircularPlayButton(
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .padding(6.dp)
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
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = if (isPlaying) Color.White else Color.Black,
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (!isPlaying) Modifier.offset(x = 2.dp) else Modifier
                )
        )
    }
}
