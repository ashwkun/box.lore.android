package cx.aswin.boxcast.feature.explore

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.components.optimizedImageUrl
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.network.model.DailyCuriosityDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

sealed interface CardAction {
    data object Dismiss : CardAction
    data object Queue : CardAction
    data object Play : CardAction
    data object Click : CardAction
    data object PodcastClick : CardAction
}

@Composable
fun CuriosityCardStack(
    questions: List<DailyCuriosityDto>,
    isCurrentEpisode: (String) -> Boolean,
    isCurrentlyPlaying: (String) -> Boolean,
    isCurrentlyLoading: (String) -> Boolean,
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
                .fillMaxSize(),
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

    val daily = questions.first()
    val swipeThresholdPx = with(LocalDensity.current) { 88.dp.toPx() }
    val swipeState = rememberSwipeableCardState(key = daily.episode.id) { direction ->
        if (direction == SwipeDirection.Left) {
            onSwipeLeft(daily)
        } else {
            onSwipeRight(daily)
        }
    }
    val swipeProgress by remember(swipeState, swipeThresholdPx) {
        derivedStateOf {
            (abs(swipeState.offset.value.x) / swipeThresholdPx).coerceIn(0f, 1f)
        }
    }
    val visibleCards = questions.take(3)

    Box(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 520.dp)
            .padding(bottom = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        visibleCards.asReversed().forEach { card ->
            key(card.episode.id) {
                val depth = visibleCards.indexOf(card)
                val isActive = depth == 0
                val cardModifier = when (depth) {
                    2 -> Modifier
                        .matchParentSize()
                        .offset(y = (24f - (10f * swipeProgress)).dp)
                        .scale(0.93f + (0.035f * swipeProgress))
                        .graphicsLayer {
                            rotationZ = 1.8f * (1f - swipeProgress)
                        }
                    1 -> Modifier
                        .matchParentSize()
                        .offset(y = (13f - (11f * swipeProgress)).dp)
                        .scale(0.965f + (0.025f * swipeProgress))
                        .graphicsLayer {
                            rotationZ = -1.15f * (1f - swipeProgress)
                        }
                    else -> Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                swipeState.offset.value.x.roundToInt(),
                                0
                            )
                        }
                        .graphicsLayer {
                            rotationZ = (swipeState.offset.value.x / 180f)
                                .coerceIn(-2.5f, 2.5f)
                            cameraDistance = 12f * density
                        }
                        .pointerInput(card.episode.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val offsetX = swipeState.offset.value.x
                                    if (offsetX > swipeThresholdPx) {
                                        swipeState.swipe(SwipeDirection.Right)
                                    } else if (offsetX < -swipeThresholdPx) {
                                        swipeState.swipe(SwipeDirection.Left)
                                    } else {
                                        swipeState.reset()
                                    }
                                },
                                onDragCancel = swipeState::reset,
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    swipeState.drag(
                                        androidx.compose.ui.geometry.Offset(
                                            x = dragAmount,
                                            y = 0f
                                        )
                                    )
                                }
                            )
                        }
                }

                DeckCard(
                    daily = card,
                    isCurrentEpisode = isCurrentEpisode(card.episode.id.toString()),
                    isCurrentlyPlaying = isCurrentlyPlaying(card.episode.id.toString()),
                    isCurrentlyLoading = isCurrentlyLoading(card.episode.id.toString()),
                    fallbackAccentColor = accentColor,
                    interactive = isActive,
                    modifier = cardModifier,
                    onAction = { action ->
                        if (isActive) {
                            when (action) {
                                CardAction.Dismiss -> onSwipeLeft(card)
                                CardAction.Queue -> onSwipeRight(card)
                                CardAction.Play -> onPlayClick(card)
                                CardAction.Click -> onEpisodeClick(card)
                                CardAction.PodcastClick -> onPodcastClick(card)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DeckCard(
    daily: DailyCuriosityDto,
    isCurrentEpisode: Boolean,
    isCurrentlyPlaying: Boolean,
    isCurrentlyLoading: Boolean,
    fallbackAccentColor: Color,
    interactive: Boolean,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = rememberArtworkAccentColor(
        daily = daily,
        fallback = fallbackAccentColor
    )
    CuriosityCardContent(
        daily = daily,
        isCurrentEpisode = isCurrentEpisode,
        isCurrentlyPlaying = isCurrentlyPlaying,
        isCurrentlyLoading = isCurrentlyLoading,
        accentColor = accentColor,
        interactive = interactive,
        onAction = onAction,
        modifier = modifier
    )
}

@Composable
private fun rememberArtworkAccentColor(
    daily: DailyCuriosityDto,
    fallback: Color
): Color {
    val context = LocalContext.current
    val imageUrl = daily.episode.image ?: daily.episode.feedImage
    var extractedColor by remember(imageUrl) { mutableStateOf<Color?>(null) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            extractedColor = null
            return@LaunchedEffect
        }
        extractedColor = runCatching {
            val request = ImageRequest.Builder(context)
                .data(imageUrl.optimizedImageUrl(width = 160))
                .allowHardware(false)
                .size(80, 80)
                .build()
            val result = coil.Coil.imageLoader(context).execute(request)
                as? coil.request.SuccessResult
                ?: return@runCatching null
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                ?: return@runCatching null
            withContext(Dispatchers.Default) {
                extractDominantColor(bitmap)
            }
        }.getOrNull()
    }

    return animateColorAsState(
        targetValue = extractedColor ?: fallback,
        animationSpec = tween(durationMillis = 450),
        label = "StackedCardAccent"
    ).value
}

@Composable
private fun CuriosityCardContent(
    daily: DailyCuriosityDto,
    isCurrentEpisode: Boolean,
    isCurrentlyPlaying: Boolean,
    isCurrentlyLoading: Boolean,
    accentColor: Color,
    interactive: Boolean = true,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val coverArt = daily.episode.image ?: daily.episode.feedImage ?: ""
    val artworkShape = RoundedCornerShape(14.dp)
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val cardShape = MaterialTheme.shapes.extraLarge
    var hidePodcastMetadata by remember(daily.episode.id, daily.explanation) {
        mutableStateOf(false)
    }

    OutlinedCard(
        shape = cardShape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Black
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .then(
                if (interactive) {
                    Modifier.expressiveClickable(
                        shape = cardShape,
                        onClick = { onAction(CardAction.Click) }
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            OptimizedImage(
                url = coverArt,
                proxyWidth = 320,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.24f
                        scaleY = 1.24f
                    }
                    .blur(
                        radius = 64.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                    val upperWash = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.34f),
                            accentColor.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.08f, size.height * 0.02f),
                        radius = size.maxDimension * 0.8f
                    )
                    val lowerWash = Brush.radialGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(size.width, size.height),
                        radius = size.maxDimension * 0.7f
                    )
                    onDrawBehind {
                        drawRect(upperWash)
                        drawRect(lowerWash)
                    }
                }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = 0.9f),
                                tertiaryColor.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                CardQuickActions(
                    enabled = interactive,
                    onDismiss = { onAction(CardAction.Dismiss) },
                    onInfo = { onAction(CardAction.Click) },
                    onQueue = { onAction(CardAction.Queue) },
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp
                    )
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 18.dp,
                            bottom = 18.dp
                        ),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = daily.question,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!daily.explanation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = daily.explanation.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.78f),
                            textAlign = TextAlign.Start,
                            onTextLayout = { result ->
                                if (result.hasVisualOverflow) {
                                    hidePodcastMetadata = true
                                }
                            }
                        )
                    }
                }

                if (!hidePodcastMetadata) {
                    Column(
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = 18.dp
                        )
                    ) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.18f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .expressiveClickable(
                                    enabled = interactive,
                                    onClick = { onAction(CardAction.PodcastClick) }
                                )
                                .padding(vertical = 2.dp)
                        ) {
                            OptimizedImage(
                                url = coverArt,
                                proxyWidth = 128,
                                contentDescription = "Episode artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(
                                        width = 1.dp,
                                        color = accentColor.copy(alpha = 0.45f),
                                        shape = artworkShape
                                    )
                                    .clip(artworkShape)
                            )
                            Text(
                                text = daily.episode.feedTitle ?: "Podcast",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Open podcast",
                                tint = Color.White.copy(alpha = 0.62f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                EpisodePlayButton(
                    isCurrentEpisode = isCurrentEpisode,
                    isPlaying = isCurrentlyPlaying,
                    isLoading = isCurrentlyLoading,
                    accentColor = accentColor,
                    enabled = interactive,
                    onClick = { onAction(CardAction.Play) }
                )
            }
        }
    }
}

@Composable
private fun CardQuickActions(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardQuickAction(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            label = "Skip",
            enabled = enabled,
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        )
        CardQuickAction(
            icon = Icons.Rounded.Info,
            label = "Info",
            enabled = enabled,
            onClick = onInfo,
            modifier = Modifier.weight(1f)
        )
        CardQuickAction(
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            label = "Queue",
            enabled = enabled,
            onClick = onQueue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CardQuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .expressiveClickable(
                enabled = enabled,
                shape = CircleShape,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.52f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.58f)
        )
    }
}

@Composable
private fun EpisodePlayButton(
    isCurrentEpisode: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eyebrow = when {
        isLoading -> "GETTING READY"
        isPlaying -> "LISTENING"
        isCurrentEpisode -> "CONTINUE"
        else -> "LISTEN NOW"
    }
    val label = when {
        isLoading -> "Loading episode"
        isPlaying -> "Pause episode"
        isCurrentEpisode -> "Resume episode"
        else -> "Play episode"
    }
    val iconVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
    val darkAccent = lerp(accentColor, Color.Black, 0.58f)
    val waveColor = lerp(accentColor, Color.White, 0.34f)
    val railShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlaybackRailPress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(railShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF111016),
                        Color(0xFF15131B),
                        darkAccent
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(29.dp)
            )
            Spacer(modifier = Modifier.width(15.dp))
            Column {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = waveColor.copy(alpha = 0.76f)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            PlaybackWave(
                active = enabled && (isPlaying || isLoading),
                color = waveColor,
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
            )
        }
    }
}

@Composable
private fun PlaybackWave(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "PlaybackWaveAmplitude"
    )
    val phaseAnimation = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (isActive) {
            phaseAnimation.snapTo(0f)
            phaseAnimation.animateTo(
                targetValue = (2f * PI).toFloat(),
                animationSpec = tween(
                    durationMillis = 1_200,
                    easing = LinearEasing
                )
            )
        }
    }

    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val startX = strokeWidth / 2f
        val endX = size.width - (strokeWidth / 2f)
        if (endX <= startX) return@Canvas

        val centerY = size.height / 2f
        val amplitude = 3.dp.toPx() * amplitudeFactor
        if (amplitude <= 0.15f) {
            drawLine(
                color = color,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val wavelength = 36.dp.toPx()
        val path = Path()
        var x = startX
        path.moveTo(
            startX,
            centerY + amplitude * sin(phaseAnimation.value)
        )
        while (x < endX) {
            x = (x + 3f).coerceAtMost(endX)
            val y = centerY + amplitude *
                sin((x / wavelength) * 2f * PI.toFloat() + phaseAnimation.value)
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
