package cx.aswin.boxcast.feature.player.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

val MiniPlayerHeight = 72.dp

data class MiniPlayerContent(
    val episode: Episode,
    val podcastTitle: String,
    val podcastImageUrl: String?,
    val isPlaying: Boolean,
    val isLoading: Boolean,
    val position: Long,
    val duration: Long
)

data class MiniPlayerColors(
    val colorScheme: ColorScheme,
    val backgroundColor: Color
)

data class MiniPlayerActions(
    val onPlayPause: () -> Unit,
    val onReplay: () -> Unit,
    val onForward: () -> Unit,
    val onDismiss: () -> Unit
)

data class MiniPlayerSwipeTip(
    val visible: Boolean = false,
    val onDismissed: () -> Unit = {}
)

@Stable
private class MiniSwipeState {
    val offsetX = Animatable(0f)
    var showConfirmPill by mutableStateOf(false)
        private set
    var direction by mutableIntStateOf(0)
        private set
    private var autoHideJob: Job? = null

    fun onDragStart(haptics: HapticFeedback) {
        autoHideJob?.cancel()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onDrag(scope: CoroutineScope, dragAmount: Float, threshold: Float) {
        scope.launch {
            offsetX.snapTo(offsetX.value + dragAmount)
            if (kotlin.math.abs(offsetX.value) > threshold * 0.5f && !showConfirmPill) {
                direction = if (offsetX.value < 0) -1 else 1
                showConfirmPill = true
            }
            if (showConfirmPill && kotlin.math.abs(offsetX.value) < threshold * 0.3f) {
                showConfirmPill = false
                autoHideJob?.cancel()
            }
        }
    }

    fun onDragEnd(scope: CoroutineScope, threshold: Float, haptics: HapticFeedback) {
        scope.launch {
            if (kotlin.math.abs(offsetX.value) > threshold) {
                revealConfirmation(scope, threshold, haptics)
            } else {
                hideConfirmation()
            }
        }
    }

    fun onDragCancel(scope: CoroutineScope) {
        scope.launch { hideConfirmation() }
    }

    fun confirmDismiss(haptics: HapticFeedback, onDismiss: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        autoHideJob?.cancel()
        onDismiss()
    }

    private suspend fun revealConfirmation(
        scope: CoroutineScope,
        threshold: Float,
        haptics: HapticFeedback
    ) {
        direction = if (offsetX.value < 0) -1 else 1
        showConfirmPill = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        offsetX.animateTo(
            targetValue = direction * threshold * 1.5f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
        autoHideJob = scope.launch {
            delay(3000)
            hideConfirmation(
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    private suspend fun hideConfirmation(
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    ) {
        showConfirmPill = false
        offsetX.animateTo(0f, animationSpec)
    }
}

/**
 * v2 mini player: squircle artwork, marquee-free two-line labels, transport buttons,
 * and a hairline wavy progress indicator. Swipe horizontally while paused to reveal
 * a dismiss pill.
 */
@Composable
fun MiniPlayerV2(
    content: MiniPlayerContent,
    colors: MiniPlayerColors,
    actions: MiniPlayerActions,
    swipeTip: MiniPlayerSwipeTip = MiniPlayerSwipeTip(),
    modifier: Modifier = Modifier
) {
    val episode = content.episode
    val podcastTitle = content.podcastTitle
    val podcastImageUrl = content.podcastImageUrl
    val isPlaying = content.isPlaying
    val isLoading = content.isLoading
    val position = content.position
    val duration = content.duration
    val colorScheme = colors.colorScheme
    val backgroundColor = colors.backgroundColor
    val onPlayPause = actions.onPlayPause
    val onReplay = actions.onReplay
    val onForward = actions.onForward
    val onDismiss = actions.onDismiss
    val showSwipeTip = swipeTip.visible
    val onSwipeTipDismissed = swipeTip.onDismissed
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Swipe-to-dismiss (only while paused)
    val swipeState = remember { MiniSwipeState() }
    val dismissThreshold = with(density) { 100.dp.toPx() }

    Box(modifier = modifier) {
        // Dismiss pill revealed behind the sliding content
        AnimatedVisibility(
            visible = swipeState.showConfirmPill,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (swipeState.direction > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable {
                            swipeState.confirmDismiss(haptics, onDismiss)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Sliding content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .offset { IntOffset(swipeState.offsetX.value.toInt(), 0) }
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(
                        topStart = 26.dp,
                        topEnd = 26.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 26.dp,
                        topEnd = 26.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .pointerInput(isPlaying) {
                    if (isPlaying) return@pointerInput // No swipe-dismiss while playing
                    detectHorizontalDragGestures(
                        onDragStart = { swipeState.onDragStart(haptics) },
                        onDragEnd = { swipeState.onDragEnd(scope, dismissThreshold, haptics) },
                        onDragCancel = { swipeState.onDragCancel(scope) },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeState.onDrag(scope, dragAmount, dismissThreshold)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imageUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcastImageUrl
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(
                            AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 16.dp, smoothnessAsPercentTL = 60,
                                cornerRadiusTR = 16.dp, smoothnessAsPercentTR = 60,
                                cornerRadiusBL = 16.dp, smoothnessAsPercentBL = 60,
                                cornerRadiusBR = 16.dp, smoothnessAsPercentBR = 60
                            )
                        )
                        .background(colorScheme.surfaceVariant)
                ) {
                    OptimizedImage(
                        url = imageUrl,
                        proxyWidth = 160,
                        contentDescription = "Episode artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (isLoading) 0.62f else 1f }
                    )
                }

                Spacer(modifier = Modifier.width(11.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = episode.title.replace("+", " "),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.15).sp
                        ),
                        color = colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = podcastTitle.replace("+", " "),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            letterSpacing = 0.sp
                        ),
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (duration > 0) {
                        val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        Spacer(modifier = Modifier.height(3.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = colorScheme.primary,
                            trackColor = colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                MiniTransportButtons(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    colorScheme = colorScheme,
                    onPlayPause = onPlayPause,
                    onReplay = onReplay,
                    onForward = onForward
                )
            }
        }

        // One-time swipe tip overlay
        if (showSwipeTip && !isPlaying) {
            SwipeDismissTip(
                onDismissed = onSwipeTipDismissed,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SwipeDismissTip(
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tipVisible by remember { mutableStateOf(true) }
    var dismissalReported by remember { mutableStateOf(false) }
    val currentOnDismissed = rememberUpdatedState(onDismissed)
    fun reportDismissal() {
        if (!dismissalReported) {
            dismissalReported = true
            currentOnDismissed.value()
        }
    }

    LaunchedEffect(Unit) {
        delay(4000)
        tipVisible = false
        reportDismissal()
    }
    DisposableEffect(Unit) {
        onDispose { reportDismissal() }
    }
    AnimatedVisibility(
        visible = tipVisible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(500)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "← Swipe to dismiss →",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MiniTransportButtons(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val playShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 14.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 14.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 14.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 14.dp, smoothnessAsPercentBR = 60
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniSeekButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = "Seek back 10 seconds",
            isLoading = isLoading,
            colorScheme = colorScheme,
            shape = RoundedCornerShape(13.dp),
            onClick = onReplay
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(playShape)
                .background(colorScheme.primary)
                .expressiveClickable(
                    shape = playShape,
                    indication = ripple(bounded = false),
                    enabled = !isLoading
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = isLoading,
                animationSpec = tween(220),
                label = "miniLoading"
            ) { loading ->
                if (loading) {
                    BoxLoreLoader.CircularWavy(
                        modifier = Modifier.size(24.dp),
                        color = colorScheme.onPrimary,
                        trackColor = colorScheme.onPrimary.copy(alpha = 0.24f)
                    )
                } else {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(180),
                        label = "miniPlayPause"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        MiniSeekButton(
            icon = Icons.Rounded.Forward30,
            contentDescription = "Seek forward 30 seconds",
            isLoading = isLoading,
            colorScheme = colorScheme,
            shape = RoundedCornerShape(13.dp),
            onClick = onForward
        )
    }
}

@Composable
private fun MiniSeekButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    shape: Shape,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
            .expressiveClickable(
                shape = shape,
                indication = ripple(bounded = false),
                enabled = !isLoading
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = colorScheme.onPrimaryContainer.copy(alpha = if (isLoading) 0.45f else 0.82f),
            modifier = Modifier.size(21.dp)
        )
    }
}
