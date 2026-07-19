package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.absoluteValue

/**
 * Full-player hero: Material 3 carousel artwork (or video viewport) that reacts to playback.
 * - Scales down slightly when paused, springs back on play.
 * - Crossfades to chapter art when the active chapter carries an image.
 * - Peeks the queued episode and commits it when that card settles.
 */
data class PlayerHeroArtwork(
    val episodeId: String,
    val artworkUrl: String?,
    val nextArtworkUrl: String?,
    val nextEpisodeTitle: String?,
    val chapterArtFlow: Flow<String?>
)

data class PlayerHeroPlayback(
    val isPlaying: Boolean,
    val isVideo: Boolean,
    val isFullscreenVideo: Boolean,
    val controller: androidx.media3.common.Player?,
    val isExpanded: Boolean
)

@Composable
fun PlayerHero(
    artwork: PlayerHeroArtwork,
    playback: PlayerHeroPlayback,
    dimensions: HeroDimensions,
    colorScheme: ColorScheme,
    onSkipNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Breathing motion: shrink slightly when paused, spring back on play
    val heroScale by animateFloatAsState(
        targetValue = if (playback.isPlaying) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heroScale"
    )

    val chapterArt by artwork.chapterArtFlow.collectAsStateWithLifecycle(initialValue = null)
    val displayedArt = chapterArt?.takeIf { it.isNotBlank() } ?: artwork.artworkUrl

    val heroShape = MaterialTheme.shapes.extraLarge
    val controller = playback.controller

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = heroScale
            scaleY = heroScale
        },
        contentAlignment = Alignment.Center
    ) {
        if (playback.isVideo && controller != null && !playback.isFullscreenVideo) {
            PlayerVideoHero(
                controller = controller,
                width = dimensions.width,
                height = dimensions.height,
                shape = heroShape
            )
        } else if (artwork.nextArtworkUrl.isNullOrBlank() || !playback.isExpanded) {
            PlayerArtworkCard(
                artworkUrl = displayedArt,
                colorScheme = colorScheme,
                modifier = Modifier
                    .width(dimensions.width)
                    .height(dimensions.height)
                    .shadow(elevation = 12.dp, shape = heroShape, clip = false)
            )
        } else {
            PlayerArtworkPager(
                episodeId = artwork.episodeId,
                artworkUrl = displayedArt,
                nextArtworkUrl = artwork.nextArtworkUrl,
                nextEpisodeTitle = artwork.nextEpisodeTitle,
                colorScheme = colorScheme,
                dimensions = dimensions,
                onSkipNextEpisode = onSkipNextEpisode
            )
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerVideoHero(
    controller: androidx.media3.common.Player,
    width: Dp,
    height: Dp,
    shape: androidx.compose.ui.graphics.Shape
) {
    var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }
    DisposableEffect(controller) {
        onDispose { playerViewRef?.player = null }
    }
    AndroidView(
        factory = { context ->
            androidx.media3.ui.PlayerView(context).apply {
                player = controller
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                playerViewRef = this
            }
        },
        update = { playerView ->
            if (playerView.player != controller) playerView.player = controller
            playerViewRef = playerView
        },
        modifier = Modifier
            .width(width)
            .height(height)
            .shadow(elevation = 12.dp, shape = shape, clip = false)
            .clip(shape)
    )
}

data class HeroDimensions(val width: Dp, val height: Dp)

@Composable
private fun PlayerArtworkPager(
    episodeId: String,
    artworkUrl: String?,
    nextArtworkUrl: String,
    nextEpisodeTitle: String?,
    colorScheme: ColorScheme,
    dimensions: HeroDimensions,
    onSkipNextEpisode: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val heroShape = MaterialTheme.shapes.extraLarge
    key(episodeId) {
        val pagerState = rememberPagerState { 2 }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .filter { settledPage -> settledPage == 1 }
                .first()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onSkipNextEpisode()
        }

        Box(modifier = Modifier.fillMaxWidth().height(dimensions.height)) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { index ->
                val pageOffset = (
                    (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                    ).absoluteValue.coerceIn(0f, 1f)
                val pageArtwork = if (index == 0) artworkUrl else nextArtworkUrl
                val contentDescription = if (index == 0) "Album Art" else "Up next: $nextEpisodeTitle"
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PlayerArtworkCard(
                        artworkUrl = pageArtwork,
                        colorScheme = colorScheme,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .width(dimensions.width)
                            .height(dimensions.height)
                            .graphicsLayer {
                                val scale = 1f - (pageOffset * 0.055f)
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - (pageOffset * 0.14f)
                            }
                            .shadow(elevation = 12.dp, shape = heroShape, clip = false)
                    )
                }
            }
            NextEpisodeHint(
                height = dimensions.height,
                colorScheme = colorScheme
            )
        }
    }
}

@Composable
private fun NextEpisodeHint(height: Dp, colorScheme: ColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 16.dp)
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            // Drop shadow layer: Offset black icon with alpha
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(32.dp)
                    .offset(x = 1.dp, y = 1.5.dp)
            )
            // Foreground M3 accent icon
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Swipe for next episode",
                tint = colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun PlayerArtworkCard(
    artworkUrl: String?,
    colorScheme: ColorScheme,
    contentDescription: String = "Album Art",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.surfaceVariant
    ) {
        Crossfade(
            targetState = artworkUrl,
            animationSpec = tween(durationMillis = 500),
            label = "heroArtCrossfade"
        ) { url ->
            OptimizedImage(
                url = url,
                proxyWidth = 800,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Builds a flow of the active chapter's artwork URL for the current position. */
fun chapterArtFlow(
    positionFlow: Flow<Long>,
    chapters: List<cx.aswin.boxlore.core.model.Chapter>
): Flow<String?> = positionFlow
    .map { pos -> chapters.lastOrNull { (it.startTime * 1000).toLong() <= pos }?.img }
    .distinctUntilChanged()

/** Video mode toggles shown under the hero for video episodes. */
@Composable
fun VideoModeButtons(
    isAudioOnly: Boolean,
    colorScheme: ColorScheme,
    onAudioOnlyChange: (Boolean) -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAudioOnly) {
                FilledTonalButton(
                    onClick = { onAudioOnlyChange(false) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch to Video", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                FilledTonalButton(
                    onClick = { onAudioOnlyChange(true) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Headset, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio Only", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = onFullscreenClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fullscreen", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
