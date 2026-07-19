package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.player.ChaptersSheetContent
import cx.aswin.boxlore.feature.player.QueueSheetActions
import cx.aswin.boxlore.feature.player.QueueSheetContent
import cx.aswin.boxlore.feature.player.v2.logic.resolveQueuePodcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
internal fun PlayerMetadata(
    episode: Episode,
    podcast: Podcast,
    colorScheme: ColorScheme,
    actions: FullPlayerActions
) {
    MarqueeMetadataText(
        text = episode.title.replace("+", " "),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = colorScheme.onSurface,
        velocity = 26.dp,
        onClick = {
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("episode_info")
            actions.onCollapse()
            actions.onEpisodeInfoClick(episode)
        }
    )
    Spacer(modifier = Modifier.height(3.dp))
    MarqueeMetadataText(
        text = podcast.title.replace("+", " "),
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = colorScheme.onSurfaceVariant,
        velocity = 24.dp,
        onClick = {
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("podcast_info")
            actions.onCollapse()
            actions.onPodcastInfoClick(podcast)
        }
    )
}

@Composable
internal fun MarqueeMetadataText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    velocity: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                animationMode = MarqueeAnimationMode.Immediately,
                repeatDelayMillis = 1_600,
                initialDelayMillis = 1_800,
                spacing = MarqueeSpacing.fractionOfContainer(0.18f),
                velocity = velocity
            )
    )
}

@Composable
internal fun FullPlayerControls(
    model: FullPlayerControlModel,
    dependencies: FullPlayerDependencies,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    scope: CoroutineScope
) {
    if (model.state.duration > 0) {
        PlayerSeekbar(
            progressFlows = PlayerProgressFlows(flows.position, flows.bufferedPosition),
            durationMs = model.state.duration,
            isPlaying = model.state.isPlaying,
            colorScheme = model.colorScheme,
            onSeek = {
                cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("seek")
                dependencies.playbackRepository.seekTo(it)
            },
            chapters = model.state.currentChapters
        )
    }
    Spacer(modifier = Modifier.height(if (model.isCompact) 14.dp else 18.dp))
    FullPlayerPrimaryControls(model, dependencies.playbackRepository)
    Spacer(modifier = Modifier.height(if (model.isCompact) 14.dp else 18.dp))
    FullPlayerSecondaryControls(model, dependencies, ui, scope)
}

@Composable
internal fun FullPlayerPrimaryControls(
    model: FullPlayerControlModel,
    playbackRepository: PlaybackRepository
) {
    PrimaryControls(
        isPlaying = model.state.isPlaying,
        isLoading = model.state.isLoading,
        colorScheme = model.colorScheme,
        actions = PrimaryControlActions(
            onPlayPause = {
                cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("play_pause")
                if (model.state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
            },
            onReplay = {
                cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("previous")
                playbackRepository.skipBackward()
            },
            onForward = {
                cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("next")
                playbackRepository.skipForward()
            },
        ),
        seekDurations = cx.aswin.boxlore.feature.player.SeekControlDurations(
            backwardSeconds = (model.state.seekBackwardMs / 1_000L).toInt(),
            forwardSeconds = (model.state.seekForwardMs / 1_000L).toInt(),
        ),
    )
}

@Composable
internal fun FullPlayerSecondaryControls(
    model: FullPlayerControlModel,
    dependencies: FullPlayerDependencies,
    ui: FullPlayerUiState,
    scope: CoroutineScope
) {
    SecondaryRail(
        playback = SecondaryPlaybackState(
            model.state.playbackSpeed,
            model.state.sleepTimerEnd,
            model.state.sleepAtEndOfEpisode
        ),
        availability = SecondaryAvailabilityState(
            hasChapters = !model.episode.chaptersUrl.isNullOrEmpty() || model.state.currentChapters.isNotEmpty(),
            hasTranscript = model.state.currentTranscript.isNotEmpty(),
            isTranscriptVisible = ui.showInlineTranscript,
            isChaptersLoading = model.state.isChaptersLoading,
            autoTranscriptState = model.state.autoTranscriptState,
            autoChaptersState = model.state.autoChaptersState
        ),
        library = SecondaryLibraryState(
            model.state.isLiked,
            model.isDownloaded,
            model.isDownloading,
            model.state.isCompleted
        ),
        colorScheme = model.colorScheme,
        selectionActions = fullPlayerSelectionActions(dependencies.playbackRepository),
        clickActions = fullPlayerClickActions(model, dependencies, ui, scope)
    )
}

internal fun fullPlayerSelectionActions(
    playbackRepository: PlaybackRepository
) = SecondarySelectionActions(
    onSpeedSelected = { speed ->
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction(
            "speed_change",
            value = speed.toString()
        )
        playbackRepository.setPlaybackSpeed(speed)
    },
    onSleepTimerSelected = { minutes ->
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction(
            "sleep_timer",
            value = minutes.toString()
        )
        playbackRepository.setSleepTimer(minutes)
    }
)

internal fun fullPlayerClickActions(
    model: FullPlayerControlModel,
    dependencies: FullPlayerDependencies,
    ui: FullPlayerUiState,
    scope: CoroutineScope
) = SecondaryClickActions(
    onQueueClick = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("queue")
        ui.showQueueSheet = true
    },
    onChaptersClick = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("chapters_sheet")
        ui.showChaptersSheet = true
    },
    onTranscriptClick = {
        if (model.state.currentTranscript.isNotEmpty()) {
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction(
                if (ui.showInlineTranscript) "transcript_hide" else "transcript_inline"
            )
            ui.showInlineTranscript = !ui.showInlineTranscript
        }
    },
    onLikeClick = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("like")
        scope.launch { dependencies.playbackRepository.toggleLike() }
    },
    onDownloadClick = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("download")
        scope.launch { toggleEpisodeDownload(model, dependencies.downloadRepository) }
    },
    onMarkPlayedClick = {
        cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("mark_played")
        scope.launch {
            dependencies.playbackRepository.toggleCompletion(
                episode = model.episode,
                podcastId = model.podcast.id,
                podcastTitle = model.podcast.title,
                podcastImageUrl = model.podcast.imageUrl
            )
        }
    }
)

internal suspend fun toggleEpisodeDownload(
    model: FullPlayerControlModel,
    downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository
) {
    if (model.isDownloaded || model.isDownloading) {
        downloadRepository.removeDownload(model.episode.id)
    } else {
        downloadRepository.addDownload(model.episode, model.podcast)
    }
}

@Composable
internal fun FullPlayerSupportingContent(
    state: PlayerState,
    episode: Episode,
    nextEpisode: Episode?,
    colorScheme: ColorScheme,
    playbackRepository: PlaybackRepository,
    actions: FullPlayerActions,
    ui: FullPlayerUiState
) {
    if (nextEpisode != null) {
        UpNextCard(
            queuedEpisodes = state.queue.drop(1),
            colorScheme = colorScheme,
            onOpenQueue = {
                cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("queue")
                ui.showQueueSheet = true
            },
            onPlayNext = {
                cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("skip_next_episode")
                playbackRepository.skipToNextEpisode()
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    NotesPreviewCard(
        description = episode.description,
        colorScheme = colorScheme,
        onOpenEpisodeInfo = {
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("episode_info")
            actions.onCollapse()
            actions.onEpisodeInfoClick(episode)
        }
    )
}

internal data class PlayerQueueSheetModel(
    val state: PlayerState,
    val podcast: Podcast,
    val colorScheme: ColorScheme
)

@OptIn(ExperimentalMaterial3Api::class)
internal data class PlayerQueueSheetResources(
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val sheetState: SheetState
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerQueueSheet(
    playbackRepository: PlaybackRepository,
    model: PlayerQueueSheetModel,
    resources: PlayerQueueSheetResources,
    ui: FullPlayerUiState
) {
    ModalBottomSheet(
        onDismissRequest = { ui.showQueueSheet = false },
        sheetState = resources.sheetState,
        containerColor = model.colorScheme.surface,
        contentColor = model.colorScheme.onSurface,
        dragHandle = { SheetDragHandle(model.colorScheme) }
    ) {
        QueueSheetContent(
            queue = model.state.queue.drop(1),
            currentPodcast = model.podcast,
            colorScheme = model.colorScheme,
            actions = queueSheetActions(playbackRepository, model.podcast, resources, ui)
        )
    }
}
internal fun queueSheetActions(
    playbackRepository: PlaybackRepository,
    podcast: Podcast,
    resources: PlayerQueueSheetResources,
    ui: FullPlayerUiState
) = QueueSheetActions(
    onPlayEpisode = { episode ->
        resources.scope.launch {
            val freshQueue = playbackRepository.playerState.value.queue
            val episodePodcast = resolveQueuePodcast(episode, podcast)
            playbackRepository.playFromQueueIndex(episode.id, freshQueue, episodePodcast)
            ui.showQueueSheet = false
        }
    },
    onRemoveEpisode = { episode ->
        resources.scope.launch {
            handleQueueRemoval(playbackRepository, episode.id, resources.snackbarHostState)
        }
    },
    onClose = { ui.showQueueSheet = false },
    onMove = { fromUi, toUi ->
        playbackRepository.moveQueueItem(
            cx.aswin.boxlore.core.playback.QueueMath.uiIndexToQueueIndex(fromUi),
            cx.aswin.boxlore.core.playback.QueueMath.uiIndexToQueueIndex(toUi)
        )
    },
    onDragEnd = { episodeId, fromUi, toUi ->
        resources.scope.launch {
            playbackRepository.persistQueueOrder(
                movedEpisodeId = episodeId,
                fromQueueIndex = cx.aswin.boxlore.core.playback.QueueMath.uiIndexToQueueIndex(fromUi),
                toQueueIndex = cx.aswin.boxlore.core.playback.QueueMath.uiIndexToQueueIndex(toUi)
            )
        }
    }
)

internal suspend fun handleQueueRemoval(
    playbackRepository: PlaybackRepository,
    episodeId: String,
    snackbarHostState: SnackbarHostState
) {
    val removed = playbackRepository.removeFromQueue(episodeId, deferSkipSignal = true) ?: return
    val result = snackbarHostState.showSnackbar(
        message = "Removed from queue",
        actionLabel = "Undo",
        duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) {
        playbackRepository.undoQueueRemoval(removed)
    } else {
        playbackRepository.confirmQueueRemoval(removed)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerChaptersSheet(
    playbackRepository: PlaybackRepository,
    state: PlayerState,
    episode: Episode,
    positionFlow: Flow<Long>,
    colorScheme: ColorScheme,
    sheetState: SheetState,
    ui: FullPlayerUiState
) {
    ModalBottomSheet(
        onDismissRequest = { ui.showChaptersSheet = false },
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        dragHandle = { SheetDragHandle(colorScheme) }
    ) {
        ChaptersSheetContent(
            chapters = state.currentChapters,
            positionFlow = positionFlow,
            colorScheme = colorScheme,
            onSeek = { seekPosition ->
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.setSeekSource("chapters_list")
                playbackRepository.seekTo(seekPosition)
                ui.showChaptersSheet = false
            },
            onClose = { ui.showChaptersSheet = false },
            chaptersUrl = episode.chaptersUrl,
            isChaptersLoading = state.isChaptersLoading,
            hasTranscript = state.autoTranscriptState == AutoTranscriptState.NONE ||
                state.autoTranscriptState == AutoTranscriptState.COMPLETED,
            onGenerateChapters = playbackRepository::generateAutoChapters
        )
    }
}

@Composable
internal fun SheetDragHandle(colorScheme: ColorScheme) {
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
}
