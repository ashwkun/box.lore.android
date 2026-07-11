package cx.aswin.boxcast.feature.player.v2.full

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.player.v2.chrome.artworkSquircleShape
import cx.aswin.boxcast.feature.player.v2.logic.CarouselSkipAction
import cx.aswin.boxcast.feature.player.v2.logic.EpisodeCarouselLogic
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EpisodeArtCarousel(
    currentEpisode: Episode,
    queue: List<Episode>,
    podcasts: Map<String, Podcast> = emptyMap(),
    colorScheme: ColorScheme,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    skipDirection: Int? = null,
    modifier: Modifier = Modifier,
    artworkSizeFraction: Float = 0.85f,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }

    val neighbors = EpisodeCarouselLogic.queueNeighbors(queue, currentEpisode.id)
    val previousEpisode = neighbors.previous
    val nextEpisode = neighbors.next

    LaunchedEffect(skipDirection) {
        when (skipDirection) {
            -1 -> {
                dragOffset.animateTo(
                    targetValue = with(density) { 120.dp.toPx() },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
                onSkipPrevious()
                dragOffset.snapTo(0f)
            }
            1 -> {
                dragOffset.animateTo(
                    targetValue = with(density) { -120.dp.toPx() },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
                onSkipNext()
                dragOffset.snapTo(0f)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val artworkSize = maxWidth * artworkSizeFraction
        val artworkSizePx = with(density) { artworkSize.toPx() }
        val commitThreshold = EpisodeCarouselLogic.commitThresholdPx(artworkSizePx)
        val artworkShape = artworkSquircleShape()

        fun episodeImageUrl(episode: Episode): String? =
            EpisodeCarouselLogic.resolveEpisodeImageUrl(episode, podcasts)

        Box(
            modifier = Modifier
                .size(artworkSize)
                .pointerInput(currentEpisode.id) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                when (EpisodeCarouselLogic.resolveSkipFromDrag(totalDrag, commitThreshold)) {
                                    CarouselSkipAction.PreviousEpisode -> {
                                        dragOffset.animateTo(
                                            targetValue = commitThreshold * 1.2f,
                                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                        )
                                        onSkipPrevious()
                                    }
                                    CarouselSkipAction.NextEpisode -> {
                                        dragOffset.animateTo(
                                            targetValue = -commitThreshold * 1.2f,
                                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                        )
                                        onSkipNext()
                                    }
                                    CarouselSkipAction.None -> Unit
                                }
                                dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                totalDrag = 0f
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(0f, spring())
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                            coroutineScope.launch {
                                dragOffset.snapTo(dragOffset.value + dragAmount)
                            }
                        },
                    )
                },
        ) {
            previousEpisode?.let { episode ->
                CarouselArtwork(
                    imageUrl = episodeImageUrl(episode),
                    colorScheme = colorScheme,
                    artworkShape = artworkShape,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            val parallax = (dragOffset.value * 0.35f).coerceAtLeast(0f)
                            translationX = -artworkSizePx * 0.55f + parallax
                            alpha = 0.45f + (abs(dragOffset.value) / commitThreshold).coerceIn(0f, 0.4f)
                            scaleX = 0.82f
                            scaleY = 0.82f
                        },
                )
            }

            nextEpisode?.let { episode ->
                CarouselArtwork(
                    imageUrl = episodeImageUrl(episode),
                    colorScheme = colorScheme,
                    artworkShape = artworkShape,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer {
                            val parallax = (dragOffset.value * 0.35f).coerceAtMost(0f)
                            translationX = artworkSizePx * 0.55f + parallax
                            alpha = 0.45f + (abs(dragOffset.value) / commitThreshold).coerceIn(0f, 0.4f)
                            scaleX = 0.82f
                            scaleY = 0.82f
                        },
                )
            }

            CarouselArtwork(
                imageUrl = episodeImageUrl(currentEpisode),
                colorScheme = colorScheme,
                artworkShape = artworkShape,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                    .graphicsLayer {
                        val dragFraction = (abs(dragOffset.value) / commitThreshold).coerceIn(0f, 1f)
                        scaleX = 1f - dragFraction * 0.04f
                        scaleY = 1f - dragFraction * 0.04f
                    },
            )
        }
    }
}

@Composable
private fun CarouselArtwork(
    imageUrl: String?,
    colorScheme: ColorScheme,
    artworkShape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(4.dp)
            .clip(artworkShape),
        shape = artworkShape,
        color = colorScheme.surfaceVariant,
        shadowElevation = 4.dp,
    ) {
        OptimizedImage(
            url = imageUrl,
            proxyWidth = 800,
            contentDescription = "Episode artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (imageUrl.isNullOrBlank()) 0.5f else 1f),
        )
    }
}
