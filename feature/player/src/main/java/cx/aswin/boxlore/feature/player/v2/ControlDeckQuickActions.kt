package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.model.SleepTimerConstants
import cx.aswin.boxlore.feature.player.formatTime
import cx.aswin.boxlore.feature.player.v2.logic.downloadLabel
import cx.aswin.boxlore.feature.player.v2.logic.formatSpeedLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun QuickActionsGrid(
    playback: SecondaryPlaybackState,
    availability: SecondaryAvailabilityState,
    library: SecondaryLibraryState,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    navigation: QuickControlNavigation,
    clickActions: SecondaryClickActions
) {
    val chaptersBusy = availability.isChaptersLoading ||
        availability.autoChaptersState == AutoTranscriptState.GENERATING
    val transcriptBusy = availability.autoTranscriptState == AutoTranscriptState.GENERATING
    Column {
        QuickActionsTopRow(playback, library, colorScheme, shapes, navigation, clickActions)
        Spacer(Modifier.height(3.dp))
        QuickActionsBottomRow(
            availability,
            library,
            colorScheme,
            shapes,
            clickActions,
            chaptersBusy,
            transcriptBusy
        )
    }
}

@Composable
internal fun QuickActionsTopRow(
    playback: SecondaryPlaybackState,
    library: SecondaryLibraryState,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    navigation: QuickControlNavigation,
    clickActions: SecondaryClickActions
) {
    val speedIsCustom = kotlin.math.abs(playback.playbackSpeed - 1f) > 0.001f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        UtilityAction(
            icon = Icons.Rounded.Speed,
            label = formatSpeedLabel(playback.playbackSpeed),
            state = UtilityActionState(
                active = speedIsCustom,
                status = formatSpeedLabel(playback.playbackSpeed).takeIf { speedIsCustom }
            ),
            colorScheme = colorScheme,
            shape = shapes.topStart,
            onClick = navigation.onSpeedClick,
            modifier = Modifier.weight(1f)
        )
        SleepAction(
            sleepTimerEnd = playback.sleepTimerEnd,
            sleepAtEndOfEpisode = playback.sleepAtEndOfEpisode,
            colorScheme = colorScheme,
            shape = shapes.middle,
            onClick = navigation.onSleepClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = if (library.isDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
            label = downloadLabel(library.isDownloaded, library.isDownloading),
            state = UtilityActionState(
                active = library.isDownloaded,
                loading = library.isDownloading
            ),
            colorScheme = colorScheme,
            shape = shapes.middle,
            onClick = clickActions.onDownloadClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            label = "Queue",
            colorScheme = colorScheme,
            shape = shapes.topEnd,
            onClick = clickActions.onQueueClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun QuickActionsBottomRow(
    availability: SecondaryAvailabilityState,
    library: SecondaryLibraryState,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    clickActions: SecondaryClickActions,
    chaptersBusy: Boolean,
    transcriptBusy: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        UtilityAction(
            icon = if (library.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = if (library.isLiked) "Liked" else "Like",
            state = UtilityActionState(active = library.isLiked),
            colorScheme = colorScheme,
            shape = shapes.bottomStart,
            onClick = clickActions.onLikeClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = Icons.AutoMirrored.Rounded.Toc,
            label = "Chapters",
            state = UtilityActionState(
                enabled = !chaptersBusy,
                loading = chaptersBusy,
                subdued = !availability.hasChapters && !chaptersBusy
            ),
            colorScheme = colorScheme,
            shape = shapes.middle,
            onClick = clickActions.onChaptersClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = Icons.Rounded.Description,
            label = "Transcript",
            state = UtilityActionState(
                active = availability.isTranscriptVisible,
                enabled = availability.hasTranscript && !transcriptBusy,
                loading = transcriptBusy,
                subdued = !availability.hasTranscript && !transcriptBusy
            ),
            colorScheme = colorScheme,
            shape = shapes.middle,
            onClick = clickActions.onTranscriptClick,
            modifier = Modifier.weight(1f)
        )
        UtilityAction(
            icon = if (library.isPlayed) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
            label = if (library.isPlayed) "Played" else "Complete",
            state = UtilityActionState(active = library.isPlayed),
            colorScheme = colorScheme,
            shape = shapes.bottomEnd,
            onClick = clickActions.onMarkPlayedClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun InlineSpeedSelector(
    currentSpeed: Float,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    onBack: () -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = remember {
        listOf(0.5f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    }
    val currentIndex = speeds.indices.minByOrNull { index ->
        kotlin.math.abs(speeds[index] - currentSpeed)
    } ?: speeds.indexOf(1f)
    val presets = listOf(0.8f, 1f, 1.5f, 2f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            UtilityAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = "Back to quick actions",
                colorScheme = colorScheme,
                shape = shapes.topStart,
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            UtilityAction(
                icon = Icons.Rounded.Remove,
                label = "Slower",
                state = UtilityActionState(
                    enabled = currentIndex > 0,
                    subdued = currentIndex <= 0
                ),
                colorScheme = colorScheme,
                shape = shapes.middle,
                onClick = { onSpeedSelected(speeds[(currentIndex - 1).coerceAtLeast(0)]) },
                modifier = Modifier.weight(1f)
            )
            LabelAction(
                label = formatSpeedLabel(currentSpeed),
                active = true,
                enabled = false,
                colorScheme = colorScheme,
                shape = shapes.middle,
                onClick = {},
                modifier = Modifier.weight(1f)
            )
            UtilityAction(
                icon = Icons.Rounded.Add,
                label = "Faster",
                state = UtilityActionState(
                    enabled = currentIndex < speeds.lastIndex,
                    subdued = currentIndex >= speeds.lastIndex
                ),
                colorScheme = colorScheme,
                shape = shapes.topEnd,
                onClick = { onSpeedSelected(speeds[(currentIndex + 1).coerceAtMost(speeds.lastIndex)]) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            presets.forEachIndexed { index, speed ->
                LabelAction(
                    label = formatSpeedLabel(speed),
                    active = kotlin.math.abs(currentSpeed - speed) < 0.001f,
                    colorScheme = colorScheme,
                    shape = when (index) {
                        0 -> shapes.bottomStart
                        presets.lastIndex -> shapes.bottomEnd
                        else -> shapes.middle
                    },
                    onClick = {
                        onSpeedSelected(speed)
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun InlineSleepSelector(
    sleepTimerEnd: Long?,
    sleepAtEndOfEpisode: Boolean,
    colorScheme: ColorScheme,
    shapes: QuickControlShapes,
    onBack: () -> Unit,
    onDurationSelected: (Int) -> Unit
) {
    val timerActive = sleepAtEndOfEpisode ||
        (sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis())
    val remainingMinutes = sleepTimerEnd
        ?.let { kotlin.math.round((it - System.currentTimeMillis()) / 60_000.0).toInt() }
        ?.coerceAtLeast(0)
    val selectDurationAndBack: (Int) -> Unit = { minutes ->
        onDurationSelected(minutes)
        onBack()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            UtilityAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = "Back to quick actions",
                colorScheme = colorScheme,
                shape = shapes.topStart,
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            LabelAction(
                label = "Off",
                active = !timerActive,
                colorScheme = colorScheme,
                shape = shapes.middle,
                onClick = { selectDurationAndBack(0) },
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 15,
                label = "15m",
                active = !sleepAtEndOfEpisode && remainingMinutes == 15,
                colorScheme = colorScheme,
                shape = shapes.middle,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 30,
                label = "30m",
                active = !sleepAtEndOfEpisode && remainingMinutes == 30,
                colorScheme = colorScheme,
                shape = shapes.topEnd,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SleepDurationAction(
                minutes = 45,
                label = "45m",
                active = !sleepAtEndOfEpisode && remainingMinutes == 45,
                colorScheme = colorScheme,
                shape = shapes.bottomStart,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 60,
                label = "1h",
                active = !sleepAtEndOfEpisode && remainingMinutes == 60,
                colorScheme = colorScheme,
                shape = shapes.middle,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = 120,
                label = "2h",
                active = !sleepAtEndOfEpisode && remainingMinutes == 120,
                colorScheme = colorScheme,
                shape = shapes.middle,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
            SleepDurationAction(
                minutes = SleepTimerConstants.END_OF_EPISODE_MINUTES,
                label = "End",
                active = sleepAtEndOfEpisode,
                colorScheme = colorScheme,
                shape = shapes.bottomEnd,
                onDurationSelected = selectDurationAndBack,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun SleepDurationAction(
    minutes: Int,
    label: String,
    active: Boolean,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LabelAction(
        label = label,
        active = active,
        colorScheme = colorScheme,
        shape = shape,
        onClick = { onDurationSelected(minutes) },
        modifier = modifier
    )
}

@Composable
internal fun LabelAction(
    label: String,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (active) colorScheme.primary else colorScheme.surfaceContainerHigh,
        animationSpec = tween(180),
        label = "labelActionContainer"
    )
    val content by animateColorAsState(
        targetValue = if (active) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "labelActionContent"
    )
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(container)
            .quickActionClickable(enabled = enabled, shape = shape, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1
        )
    }
}

internal data class UtilityActionState(
    val active: Boolean = false,
    val enabled: Boolean = true,
    val loading: Boolean = false,
    val subdued: Boolean = false,
    val status: String? = null
)

@Composable
internal fun UtilityAction(
    icon: ImageVector,
    label: String,
    colorScheme: ColorScheme,
    state: UtilityActionState = UtilityActionState(),
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetContainer = if (state.active) colorScheme.primary else colorScheme.surfaceContainerHigh
    val targetContent = when {
        state.active -> colorScheme.onPrimary
        state.subdued -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> colorScheme.onSurfaceVariant
    }
    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(180),
        label = "utilityActionContainer"
    )
    val content by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(180),
        label = "utilityActionContent"
    )
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(container)
            .quickActionClickable(enabled = state.enabled, shape = shape, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.loading) {
            BoxLoreLoader.CircularWavy(
                modifier = Modifier.size(24.dp),
                size = 24.dp,
                color = colorScheme.primary
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(if (state.status == null) 27.dp else 21.dp)
            )
            if (state.status != null) {
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = content,
                    maxLines = 1
                )
            }
        }
    }
}

internal fun Modifier.quickActionClickable(
    enabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.snapTo(0.92f)
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    scale.animateTo(
                        targetValue = 1.035f,
                        animationSpec = spring(
                            dampingRatio = 0.62f,
                            stiffness = 420f
                        )
                    )
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 280f
                        )
                    )
                }
            }
        }
    }
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            clip = true
            this.shape = shape
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
internal fun SleepAction(
    sleepTimerEnd: Long?,
    sleepAtEndOfEpisode: Boolean,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remainingTime by remember { mutableStateOf("") }
    LaunchedEffect(sleepTimerEnd) {
        if (sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()) {
            while (isActive) {
                val left = sleepTimerEnd - System.currentTimeMillis()
                if (left <= 0) {
                    remainingTime = ""
                    break
                }
                remainingTime = formatTime(left)
                delay(1000)
            }
        } else {
            remainingTime = ""
        }
    }
    val hasCountdown = sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()
    val active = hasCountdown || sleepAtEndOfEpisode
    val status = when {
        sleepAtEndOfEpisode -> "End"
        hasCountdown && remainingTime.isNotEmpty() -> remainingTime
        else -> null
    }
    UtilityAction(
        icon = Icons.Rounded.NightsStay,
        label = "Sleep",
        state = UtilityActionState(active = active, status = status),
        colorScheme = colorScheme,
        shape = shape,
        onClick = onClick,
        modifier = modifier
    )
}
