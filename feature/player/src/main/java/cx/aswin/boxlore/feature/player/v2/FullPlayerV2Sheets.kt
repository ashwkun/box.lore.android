package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.player.FullscreenTranscriptScreen
import cx.aswin.boxlore.feature.player.v2.logic.ResponsiveHeroLayout
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun FullPlayerFullscreenOverlays(
    dependencies: FullPlayerDependencies,
    model: FullPlayerOverlayModel,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    ui: FullPlayerUiState
) {
    FullPlayerTranscriptOverlay(dependencies.playbackRepository, model, ui)
    VideoFullscreenOverlay(
        content = VideoFullscreenContent(
            visible = display.isFullscreenVideo,
            episodeTitle = model.episode.title,
            isPlaying = model.state.isPlaying,
            durationMs = model.state.duration,
            seekBackwardSeconds = (model.state.seekBackwardMs / 1_000L).toInt(),
            seekForwardSeconds = (model.state.seekForwardMs / 1_000L).toInt(),
            positionFlow = model.positionFlow,
            controller = dependencies.playbackRepository.controller
        ),
        colorScheme = model.colorScheme,
        actions = VideoFullscreenActions(
            onExit = { actions.onFullscreenVideoChange(false) },
            onPlayPause = { togglePlayback(model.state, dependencies.playbackRepository) },
            onReplay = dependencies.playbackRepository::skipBackward,
            onForward = dependencies.playbackRepository::skipForward,
            onSeek = dependencies.playbackRepository::seekTo
        )
    )
}

@Composable
internal fun FullPlayerTranscriptOverlay(
    playbackRepository: PlaybackRepository,
    model: FullPlayerOverlayModel,
    ui: FullPlayerUiState
) {
    AnimatedVisibility(
        visible = ui.showFullscreenTranscript,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        FullscreenTranscriptScreen(
            transcript = model.state.currentTranscript,
            positionFlow = model.positionFlow,
            isPlaying = model.state.isPlaying,
            isLoading = model.state.isLoading,
            durationMs = model.state.duration,
            seekBackwardMs = model.state.seekBackwardMs,
            seekForwardMs = model.state.seekForwardMs,
            colorScheme = model.colorScheme,
            isSyncEnabled = ui.isSyncEnabled,
            onSyncEnabledChange = { ui.isSyncEnabled = it },
            onSeek = { seekPosition ->
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.setSeekSource("transcript_tap")
                playbackRepository.seekTo(seekPosition)
            },
            onPlayPause = { togglePlayback(model.state, playbackRepository) },
            onClose = { ui.showFullscreenTranscript = false },
            transcriptUrl = model.episode.transcriptUrl
        )
    }
}

internal fun togglePlayback(state: PlayerState, playbackRepository: PlaybackRepository) {
    if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
}

@OptIn(ExperimentalMaterial3Api::class)
internal data class FullPlayerModalResources(
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val queueSheetState: SheetState,
    val chaptersSheetState: SheetState,
    val context: android.content.Context
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FullPlayerModalSheets(
    dependencies: FullPlayerDependencies,
    model: FullPlayerModalModel,
    ui: FullPlayerUiState,
    resources: FullPlayerModalResources
) {
    if (ui.showQueueSheet) {
        PlayerQueueSheet(
            playbackRepository = dependencies.playbackRepository,
            model = PlayerQueueSheetModel(model.state, model.podcast, model.colorScheme),
            resources = PlayerQueueSheetResources(
                resources.scope,
                resources.snackbarHostState,
                resources.queueSheetState
            ),
            ui = ui
        )
    }
    if (ui.showChaptersSheet) {
        PlayerChaptersSheet(
            playbackRepository = dependencies.playbackRepository,
            state = model.state,
            episode = model.episode,
            positionFlow = model.positionFlow,
            colorScheme = model.colorScheme,
            sheetState = resources.chaptersSheetState,
            ui = ui
        )
    }
    if (ui.showSpeedSheet) {
        PlayerSpeedSheet(dependencies.playbackRepository, model, ui)
    }
    if (ui.showSleepSheet) {
        PlayerSleepSheet(dependencies.playbackRepository, model, ui)
    }
    if (ui.showGenerateDialog && model.canGenerateTranscript) {
        PlayerGenerateTranscriptDialog(dependencies.playbackRepository, model, ui)
    }
    if (ui.showShareSheet) {
        PlayerShareSheet(model, ui, resources.context)
    }
}

@Composable
internal fun PlayerSpeedSheet(
    playbackRepository: PlaybackRepository,
    model: FullPlayerModalModel,
    ui: FullPlayerUiState
) {
    SpeedSheet(
        currentSpeed = model.state.playbackSpeed,
        colorScheme = model.colorScheme,
        onSpeedSelected = { speed ->
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction(
                "speed_change",
                value = speed.toString()
            )
            playbackRepository.setPlaybackSpeed(speed)
            ui.showSpeedSheet = false
        },
        onDismiss = { ui.showSpeedSheet = false }
    )
}

@Composable
internal fun PlayerSleepSheet(
    playbackRepository: PlaybackRepository,
    model: FullPlayerModalModel,
    ui: FullPlayerUiState
) {
    SleepSheet(
        sleepTimerEnd = model.state.sleepTimerEnd,
        colorScheme = model.colorScheme,
        onDurationSelected = { minutes ->
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction(
                "sleep_timer",
                value = minutes.toString()
            )
            playbackRepository.setSleepTimer(minutes)
            ui.showSleepSheet = false
        },
        onDismiss = { ui.showSleepSheet = false }
    )
}

@Composable
internal fun PlayerGenerateTranscriptDialog(
    playbackRepository: PlaybackRepository,
    model: FullPlayerModalModel,
    ui: FullPlayerUiState
) {
    val durationSeconds = if (model.state.duration > 0) {
        model.state.duration / 1000
    } else {
        model.episode.duration.toLong()
    }
    GenerateTranscriptDialog(
        episodeDurationSec = durationSeconds,
        autoTranscriptLimitLeft = model.state.autoTranscriptLimitLeft,
        colorScheme = model.colorScheme,
        onConfirm = {
            ui.showGenerateDialog = false
            playbackRepository.generateAutoTranscript()
        },
        onDismiss = { ui.showGenerateDialog = false }
    )
}

@Composable
internal fun PlayerShareSheet(
    model: FullPlayerModalModel,
    ui: FullPlayerUiState,
    context: android.content.Context
) {
    val currentPosition by model.positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    cx.aswin.boxlore.core.designsystem.components.ShareBottomSheet(
        id = model.episode.id,
        type = "episode",
        title = model.episode.title,
        subtitle = model.podcast.title,
        imageUrl = model.episode.imageUrl ?: model.podcast.imageUrl,
        onDismissRequest = { ui.showShareSheet = false },
        durationMs = model.episode.duration * 1000L,
        currentPositionMs = currentPosition,
        showTimestampOption = true,
        onShare = { _, _, timestamp, target ->
            cx.aswin.boxlore.core.designsystem.share.ShareManager.shareEpisode(
                context = context,
                episode = model.episode,
                podcastTitle = model.podcast.title,
                timestampMs = timestamp,
                target = target
            )
        }
    )
}

internal data class FullPlayerBodyModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val nextEpisode: Episode?,
    val isVideo: Boolean,
    val isVideoPodcast: Boolean,
    val download: FullPlayerDownloadState
)

internal data class FullPlayerBodyResources(
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val statusBarPadding: Dp,
    val navBarPadding: Dp,
    val modifier: Modifier
)

internal data class FullPlayerScrollResources(
    val scope: CoroutineScope,
    val layout: ResponsiveHeroLayout
)
