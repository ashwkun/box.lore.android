package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.model.SleepTimerConstants
import cx.aswin.boxlore.feature.player.v2.logic.formatSpeedLabel
import cx.aswin.boxlore.feature.player.v2.logic.targetControlWeight
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Full-width Material 3 Expressive button group. Coordinated segment shapes and
 * deliberate weights create one visual object with a dominant play/pause action.
 */
data class PrimaryControlActions(
    val onPlayPause: () -> Unit,
    val onReplay: () -> Unit,
    val onForward: () -> Unit,
)

@Composable
fun PrimaryControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    actions: PrimaryControlActions,
    seekDurations: cx.aswin.boxlore.feature.player.SeekControlDurations =
        cx.aswin.boxlore.feature.player.SeekControlDurations(),
    modifier: Modifier = Modifier
) {
    val interactionSources = remember { List(3) { MutableInteractionSource() } }
    val replayPressed by interactionSources[0].collectIsPressedAsState()
    val playPressed by interactionSources[1].collectIsPressedAsState()
    val forwardPressed by interactionSources[2].collectIsPressedAsState()
    val pressedIndex = when {
        replayPressed -> 0
        playPressed -> 1
        forwardPressed -> 2
        else -> null
    }
    var latchedIndex by remember { mutableStateOf<Int?>(null) }
    var interactionToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(interactionToken) {
        if (interactionToken > 0) {
            delay(110)
            latchedIndex = null
        }
    }
    val activeIndex = pressedIndex ?: latchedIndex
    val baseWeights = remember { listOf(1f, 1.18f, 1f) }
    val softGroupSpring = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 65f
    )
    val replayWeight by animateFloatAsState(
        targetControlWeight(0, activeIndex, baseWeights),
        softGroupSpring,
        label = "replayWidth"
    )
    val playWeight by animateFloatAsState(
        targetControlWeight(1, activeIndex, baseWeights),
        softGroupSpring,
        label = "playWidth"
    )
    val forwardWeight by animateFloatAsState(
        targetControlWeight(2, activeIndex, baseWeights),
        softGroupSpring,
        label = "forwardWidth"
    )
    val replayShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 34.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 14.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 34.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 14.dp, smoothnessAsPercentBR = 60
    )
    val forwardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 14.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 34.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 14.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 34.dp, smoothnessAsPercentBR = 60
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportButton(
            icon = TransportIcon(
                seconds = seekDurations.backwardSeconds,
                forward = false,
                contentDescription =
                    cx.aswin.boxlore.feature.player.seekDurationContentDescription(
                        seekDurations.backwardSeconds,
                        forward = false,
                    ),
                size = 40.dp
            ),
            colorScheme = colorScheme,
            shape = replayShape,
            interactionSource = interactionSources[0],
            onClick = {
                latchedIndex = 0
                interactionToken++
                actions.onReplay()
            },
            modifier = Modifier
                .weight(replayWeight)
                .height(88.dp)
        )
        MorphingPlayButton(
            isPlaying = isPlaying,
            isLoading = isLoading,
            colorScheme = colorScheme,
            interactionSource = interactionSources[1],
            onClick = {
                latchedIndex = 1
                interactionToken++
                actions.onPlayPause()
            },
            modifier = Modifier
                .weight(playWeight)
                .height(104.dp)
        )
        TransportButton(
            icon = TransportIcon(
                seconds = seekDurations.forwardSeconds,
                forward = true,
                contentDescription =
                    cx.aswin.boxlore.feature.player.seekDurationContentDescription(
                        seekDurations.forwardSeconds,
                        forward = true,
                    ),
                size = 40.dp
            ),
            colorScheme = colorScheme,
            shape = forwardShape,
            interactionSource = interactionSources[2],
            onClick = {
                latchedIndex = 2
                interactionToken++
                actions.onForward()
            },
            modifier = Modifier
                .weight(forwardWeight)
                .height(88.dp)
        )
    }
}

private data class TransportIcon(
    val seconds: Int,
    val forward: Boolean,
    val contentDescription: String,
    val size: androidx.compose.ui.unit.Dp
)

@Composable
private fun TransportButton(
    icon: TransportIcon,
    colorScheme: ColorScheme,
    quiet: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (quiet) colorScheme.surface.copy(alpha = 0.42f)
                else colorScheme.primary.copy(alpha = 0.15f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        cx.aswin.boxlore.feature.player.SeekDurationIcon(
            seconds = icon.seconds,
            forward = icon.forward,
            contentDescription = icon.contentDescription,
            modifier = Modifier.size(icon.size),
            tint = if (quiet) colorScheme.onSurfaceVariant else colorScheme.primary
        )
    }
}

@Composable
private fun MorphingPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var showLoader by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(500)
            showLoader = true
        } else {
            showLoader = false
        }
    }
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 30.dp else 46.dp,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 65f
        ),
        label = "playCorner"
    )
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = corner, smoothnessAsPercentTL = 60,
        cornerRadiusTR = corner, smoothnessAsPercentTR = 60,
        cornerRadiusBL = corner, smoothnessAsPercentBL = 60,
        cornerRadiusBR = corner, smoothnessAsPercentBR = 60
    )
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(colorScheme.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showLoader) {
            BoxLoreLoader.CircularWavy(
                size = 40.dp,
                color = colorScheme.onPrimary
            )
        } else {
            Crossfade(targetState = isPlaying, label = "playPauseCrossfade") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(44.dp),
                    tint = colorScheme.onPrimary
                )
            }
        }
    }
}

private enum class QuickControlMode { ACTIONS, SPEED, SLEEP }

internal data class QuickControlShapes(
    val topStart: androidx.compose.ui.graphics.Shape,
    val topEnd: androidx.compose.ui.graphics.Shape,
    val middle: androidx.compose.ui.graphics.Shape,
    val bottomStart: androidx.compose.ui.graphics.Shape,
    val bottomEnd: androidx.compose.ui.graphics.Shape
)

data class SecondaryPlaybackState(
    val playbackSpeed: Float,
    val sleepTimerEnd: Long?,
    val sleepAtEndOfEpisode: Boolean
)

data class SecondaryAvailabilityState(
    val hasChapters: Boolean,
    val hasTranscript: Boolean,
    val isTranscriptVisible: Boolean,
    val isChaptersLoading: Boolean,
    val autoTranscriptState: AutoTranscriptState,
    val autoChaptersState: AutoTranscriptState
)

data class SecondaryLibraryState(
    val isLiked: Boolean,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val isPlayed: Boolean
)

data class SecondarySelectionActions(
    val onSpeedSelected: (Float) -> Unit,
    val onSleepTimerSelected: (Int) -> Unit
)

data class SecondaryClickActions(
    val onQueueClick: () -> Unit,
    val onChaptersClick: () -> Unit,
    val onTranscriptClick: () -> Unit,
    val onLikeClick: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onMarkPlayedClick: () -> Unit
)

internal data class QuickControlNavigation(
    val onSpeedClick: () -> Unit,
    val onSleepClick: () -> Unit
)

/** Two visible rows expose every player feature without an overflow or horizontal scroll. */
@Composable
fun SecondaryRail(
    playback: SecondaryPlaybackState,
    availability: SecondaryAvailabilityState,
    library: SecondaryLibraryState,
    colorScheme: ColorScheme,
    selectionActions: SecondarySelectionActions,
    clickActions: SecondaryClickActions,
    modifier: Modifier = Modifier
) {
    var controlMode by rememberSaveable { mutableStateOf(QuickControlMode.ACTIONS) }
    val groupTrayShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 32.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 32.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 32.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 32.dp, smoothnessAsPercentBR = 60
    )
    val groupTopStartShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 24.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 9.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 9.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 9.dp, smoothnessAsPercentBR = 60
    )
    val groupMiddleShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 9.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 9.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 9.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 9.dp, smoothnessAsPercentBR = 60
    )
    val groupTopEndShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 9.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 24.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 9.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 9.dp, smoothnessAsPercentBR = 60
    )
    val groupBottomStartShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 9.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 9.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 24.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 9.dp, smoothnessAsPercentBR = 60
    )
    val groupBottomEndShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 9.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 9.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 9.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 24.dp, smoothnessAsPercentBR = 60
    )
    val selectorShapes = QuickControlShapes(
        topStart = groupTopStartShape,
        topEnd = groupTopEndShape,
        middle = groupMiddleShape,
        bottomStart = groupBottomStartShape,
        bottomEnd = groupBottomEndShape
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(groupTrayShape)
            .background(colorScheme.primary.copy(alpha = 0.12f))
            .padding(8.dp)
    ) {
        AnimatedContent(
            targetState = controlMode,
            transitionSpec = {
                val slideSpring = spring<androidx.compose.ui.unit.IntOffset>(
                    dampingRatio = 0.84f,
                    stiffness = 240f
                )
                val transition = if (targetState == QuickControlMode.ACTIONS) {
                    (
                        slideInHorizontally(slideSpring) { fullWidth -> -fullWidth / 5 } +
                            fadeIn(tween(180)) +
                            scaleIn(tween(220), initialScale = 0.98f)
                        ) togetherWith (
                        slideOutHorizontally(tween(150)) { fullWidth -> fullWidth / 6 } +
                            fadeOut(tween(130)) +
                            scaleOut(tween(150), targetScale = 0.98f)
                        )
                } else {
                    (
                        slideInHorizontally(slideSpring) { fullWidth -> fullWidth / 5 } +
                            fadeIn(tween(180)) +
                            scaleIn(tween(220), initialScale = 0.98f)
                        ) togetherWith (
                        slideOutHorizontally(tween(150)) { fullWidth -> -fullWidth / 6 } +
                            fadeOut(tween(130)) +
                            scaleOut(tween(150), targetScale = 0.98f)
                        )
                }
                transition.using(SizeTransform(clip = false))
            },
            label = "quickControlMode"
        ) { mode ->
            when (mode) {
                QuickControlMode.ACTIONS -> QuickActionsGrid(
                    playback = playback,
                    availability = availability,
                    library = library,
                    colorScheme = colorScheme,
                    shapes = selectorShapes,
                    navigation = QuickControlNavigation(
                        onSpeedClick = { controlMode = QuickControlMode.SPEED },
                        onSleepClick = { controlMode = QuickControlMode.SLEEP }
                    ),
                    clickActions = clickActions
                )

                QuickControlMode.SPEED -> InlineSpeedSelector(
                    currentSpeed = playback.playbackSpeed,
                    colorScheme = colorScheme,
                    shapes = selectorShapes,
                    onBack = { controlMode = QuickControlMode.ACTIONS },
                    onSpeedSelected = selectionActions.onSpeedSelected
                )

                QuickControlMode.SLEEP -> InlineSleepSelector(
                    sleepTimerEnd = playback.sleepTimerEnd,
                    sleepAtEndOfEpisode = playback.sleepAtEndOfEpisode,
                    colorScheme = colorScheme,
                    shapes = selectorShapes,
                    onBack = { controlMode = QuickControlMode.ACTIONS },
                    onDurationSelected = selectionActions.onSleepTimerSelected
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    currentSpeed: Float,
    colorScheme: ColorScheme,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Playback speed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            val speeds = listOf(0.5f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(speeds) { speed ->
                    val selected = speed == currentSpeed
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh)
                            .clickable { onSpeedSelected(speed) }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatSpeedLabel(speed),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(
    sleepTimerEnd: Long?,
    colorScheme: ColorScheme,
    onDurationSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timerActive = sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            val options = listOf(
                0 to "Off",
                15 to "15 min",
                30 to "30 min",
                45 to "45 min",
                60 to "1 hr",
                120 to "2 hr",
                SleepTimerConstants.END_OF_EPISODE_MINUTES to "End of episode"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { (minutes, label) ->
                    val selected = minutes == 0 && !timerActive
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh)
                            .clickable { onDurationSelected(minutes) }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
