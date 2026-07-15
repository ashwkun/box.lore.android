package cx.aswin.boxcast.feature.info.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.ExpressivePlayButton
import cx.aswin.boxcast.core.designsystem.components.ExpressivePlayButtonState
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxcast.core.designsystem.theme.contrastColor
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import kotlinx.coroutines.delay

internal data class EpisodeActionRailState(
    val title: String,
    val imageUrl: String?,
    val isPlaying: Boolean,
    val isPlaybackLoading: Boolean,
    val isResume: Boolean,
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val isQueued: Boolean,
    val isCompleted: Boolean,
    val progress: Float,
    val remainingTimeText: String?,
)

internal data class EpisodeActionRailCallbacks(
    val onMainActionClick: () -> Unit,
    val onLikeClick: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onQueueClick: () -> Unit,
    val onMarkPlayedClick: () -> Unit,
)

@Composable
internal fun EpisodeActionRail(
    state: EpisodeActionRailState,
    callbacks: EpisodeActionRailCallbacks,
    accentColor: Color,
    showMarkPlayedTip: Boolean,
    onMarkPlayedTipDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExpressivePlayButton(
            onClick = callbacks.onMainActionClick,
            state = ExpressivePlayButtonState(
                isPlaying = state.isPlaying,
                isLoading = state.isPlaybackLoading,
                isResume = state.isResume,
                progress = state.progress,
                timeText = state.remainingTimeText,
            ),
            accentColor = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = 8.dp,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EpisodeToolbarActionButton(
                isActive = state.isCompleted,
                activeIcon = Icons.Rounded.CheckCircle,
                inactiveIcon = Icons.Outlined.CheckCircle,
                contentDescription = if (state.isCompleted) "Mark unplayed" else "Mark played",
                onClick = callbacks.onMarkPlayedClick,
            )
            EpisodeToolbarActionButton(
                isActive = state.isLiked,
                activeIcon = Icons.Filled.Favorite,
                inactiveIcon = Icons.Outlined.FavoriteBorder,
                contentDescription = if (state.isLiked) "Unlike" else "Like",
                onClick = callbacks.onLikeClick,
            )
            EpisodeToolbarActionButton(
                isActive = state.isDownloaded,
                isLoading = state.isDownloading,
                activeIcon = Icons.Outlined.DownloadDone,
                inactiveIcon = Icons.Outlined.Download,
                contentDescription = if (state.isDownloaded) "Remove download" else "Download",
                onClick = callbacks.onDownloadClick,
            )
            EpisodeToolbarActionButton(
                isActive = state.isQueued,
                activeIcon = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                inactiveIcon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = if (state.isQueued) "Remove from queue" else "Add to queue",
                onClick = callbacks.onQueueClick,
            )
        }
        MarkPlayedCoachmark(
            visible = showMarkPlayedTip,
            onDismissed = onMarkPlayedTipDismissed,
        )
    }
}

@Composable
private fun EpisodeToolbarActionButton(
    isActive: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "episode_toolbar_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "episode_toolbar_content",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
                if (isLoading) stateDescription = "Loading"
            }
            .expressiveClickable(
                enabled = !isLoading,
                shape = ExpressiveShapes.Pill,
                onClick = onClick,
            )
            .background(containerColor, ExpressiveShapes.Pill),
    ) {
        if (isLoading) {
            BoxLoreLoader.CircularWavy(
                size = 22.dp,
                color = contentColor,
            )
        } else {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MarkPlayedCoachmark(
    visible: Boolean,
    onDismissed: () -> Unit,
) {
    var isVisible by remember(visible) { mutableStateOf(visible) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(4_000)
            isVisible = false
            onDismissed()
        }
    }
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
    ) {
        Surface(
            shape = ExpressiveShapes.Pill,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = "Tip: tap the check to mark this episode complete",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
internal fun CompactEpisodeActionRail(
    state: EpisodeActionRailState,
    callbacks: EpisodeActionRailCallbacks,
    accentColor: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(ExpressiveMotion.SleekFadeSpec) +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 200f,
                ),
            ) { -it / 3 } +
            scaleIn(animationSpec = ExpressiveMotion.SpatialLargeSpring, initialScale = 0.96f),
        exit = fadeOut(ExpressiveMotion.SleekFadeSpec) +
            slideOutVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 200f,
                ),
            ) { -it / 3 } +
            scaleOut(animationSpec = ExpressiveMotion.SpatialLargeSpring, targetScale = 0.96f),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
            tonalElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OptimizedImage(
                    url = state.imageUrl,
                    proxyWidth = 144,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CompactPlayButton(
                    isPlaying = state.isPlaying,
                    isLoading = state.isPlaybackLoading,
                    progress = state.progress,
                    accentColor = accentColor,
                    onClick = callbacks.onMainActionClick,
                )
            }
        }
    }
}

@Composable
private fun CompactPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: Float,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val contentDescription = if (isPlaying) "Pause" else "Play"
    Surface(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(52.dp)
            .semantics {
                this.contentDescription = contentDescription
                if (isLoading) stateDescription = "Loading"
            }
            .expressiveClickable(
                enabled = !isLoading,
                shape = CircleShape,
                isolate = true,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = accentColor,
        contentColor = accentColor.contrastColor(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                BoxLoreLoader.CircularWavy(
                    size = 27.dp,
                    color = accentColor.contrastColor(),
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(27.dp),
                )
            }
            if (progress > 0f) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = accentColor.contrastColor(alpha = 0.62f),
                        trackColor = Color.Transparent,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}
