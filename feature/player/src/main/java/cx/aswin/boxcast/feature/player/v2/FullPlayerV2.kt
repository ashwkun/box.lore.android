package cx.aswin.boxcast.feature.player.v2

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.PlayerState
import cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState
import cx.aswin.boxcast.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.player.ChaptersSheetContent
import cx.aswin.boxcast.feature.player.FullscreenTranscriptScreen
import cx.aswin.boxcast.feature.player.QueueSheetActions
import cx.aswin.boxcast.feature.player.QueueSheetContent
import cx.aswin.boxcast.feature.player.formatTime
import cx.aswin.boxcast.feature.player.v2.logic.ResponsiveHeroLayout
import cx.aswin.boxcast.feature.player.v2.logic.calculateResponsiveHeroLayout
import cx.aswin.boxcast.feature.player.v2.logic.resolveQueuePodcast
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * v2 full player: immersive artwork-tinted canvas, wavy seekbar, expressive control
 * deck, and below-the-fold Up Next + Show Notes cards.
 */
data class FullPlayerDependencies(
    val playbackRepository: PlaybackRepository,
    val downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository
)

data class FullPlayerDisplay(
    val colorScheme: ColorScheme,
    val isFullscreenVideo: Boolean,
    val sheetNestedScrollConnection: NestedScrollConnection,
    val isExpanded: Boolean,
    val showSwipeMinimizeTip: Boolean = false
)

data class FullPlayerActions(
    val onFullscreenVideoChange: (Boolean) -> Unit,
    val onCollapse: () -> Unit,
    val onEpisodeInfoClick: (Episode) -> Unit,
    val onPodcastInfoClick: (Podcast) -> Unit,
    val onSwipeMinimizeTipDismissed: () -> Unit = {}
)

@Stable
private class FullPlayerUiState {
    var showQueueSheet by mutableStateOf(false)
    var showChaptersSheet by mutableStateOf(false)
    var showSpeedSheet by mutableStateOf(false)
    var showSleepSheet by mutableStateOf(false)
    var showShareSheet by mutableStateOf(false)
    var showGenerateDialog by mutableStateOf(false)
    var showFullscreenTranscript by mutableStateOf(false)
    var showInlineTranscript by mutableStateOf(false)
    var isSyncEnabled by mutableStateOf(true)
    var isAudioOnly by mutableStateOf(false)
}

private val FullPlayerUiStateSaver = listSaver<FullPlayerUiState, Boolean>(
    save = { state ->
        listOf(
            state.showQueueSheet,
            state.showChaptersSheet,
            state.showSpeedSheet,
            state.showSleepSheet,
            state.showShareSheet,
            state.showGenerateDialog,
            state.showFullscreenTranscript,
            state.showInlineTranscript,
            state.isSyncEnabled,
            state.isAudioOnly
        )
    },
    restore = { values ->
        FullPlayerUiState().apply {
            showQueueSheet = values[0]
            showChaptersSheet = values[1]
            showSpeedSheet = values[2]
            showSleepSheet = values[3]
            showShareSheet = values[4]
            showGenerateDialog = values[5]
            showFullscreenTranscript = values[6]
            showInlineTranscript = values[7]
            isSyncEnabled = values[8]
            isAudioOnly = values[9]
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerV2(
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    modifier: Modifier = Modifier
) {
    val playbackRepository = dependencies.playbackRepository
    val downloadRepository = dependencies.downloadRepository
    val colorScheme = display.colorScheme
    // Position ticks are split into separate flows so only the seekbar recomposes per tick.
    val state by remember(playbackRepository) {
        playbackRepository.playerState
            .map { it.copy(position = 0, bufferedPosition = 0) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlayerState())

    val positionFlow = remember(playbackRepository) {
        playbackRepository.playerState.map { it.position }.distinctUntilChanged()
    }
    val bufferedPositionFlow = remember(playbackRepository) {
        playbackRepository.playerState.map { it.bufferedPosition }.distinctUntilChanged()
    }

    val episode = state.currentEpisode ?: return
    val podcast = state.currentPodcast ?: return
    val nextEpisode = state.queue.drop(1).firstOrNull()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val window = (context as? android.app.Activity)?.window

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val ui = rememberSaveable(episode.id, saver = FullPlayerUiStateSaver) { FullPlayerUiState() }

    val queueSnackbarHostState = remember { SnackbarHostState() }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chaptersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isVideoPodcast = episode.enclosureType?.startsWith("video/") == true
    val isVideo = isVideoPodcast && !ui.isAudioOnly

    val isDownloaded by remember(episode.id) { downloadRepository.isDownloaded(episode.id) }
        .collectAsStateWithLifecycle(initialValue = false)
    val isDownloading by remember(episode.id) { downloadRepository.isDownloading(episode.id) }
        .collectAsStateWithLifecycle(initialValue = false)

    BackHandler(enabled = ui.showFullscreenTranscript) { ui.showFullscreenTranscript = false }
    BackHandler(enabled = ui.showInlineTranscript && !ui.showFullscreenTranscript) {
        ui.showInlineTranscript = false
    }

    LaunchedEffect(state.currentTranscript.isEmpty()) {
        if (state.currentTranscript.isEmpty()) ui.showInlineTranscript = false
    }

    val resolvedDark = LocalEffectiveDarkTheme.current
    SideEffect {
        window?.let { win ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = !resolvedDark
            insetsController.isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    val canGenerateTranscript = state.currentTranscript.isEmpty() &&
        state.autoTranscriptState != AutoTranscriptState.GENERATING &&
        state.autoTranscriptState != AutoTranscriptState.COMPLETED

    FullPlayerBody(
        model = FullPlayerBodyModel(
            state = state,
            episode = episode,
            podcast = podcast,
            nextEpisode = nextEpisode,
            isVideo = isVideo,
            isVideoPodcast = isVideoPodcast,
            download = FullPlayerDownloadState(isDownloaded, isDownloading)
        ),
        dependencies = dependencies,
        display = display,
        actions = actions,
        flows = FullPlayerFlows(positionFlow, bufferedPositionFlow),
        ui = ui,
        resources = FullPlayerBodyResources(
            scope,
            queueSnackbarHostState,
            statusBarPadding,
            navBarPadding,
            modifier
        )
    )

    // ------------------------------------------------------------------
    // Sheets
    // ------------------------------------------------------------------

    FullPlayerModalSheets(
        dependencies = dependencies,
        model = FullPlayerModalModel(
            state,
            episode,
            podcast,
            colorScheme,
            positionFlow,
            canGenerateTranscript
        ),
        ui = ui,
        resources = FullPlayerModalResources(
            scope,
            queueSnackbarHostState,
            queueSheetState,
            chaptersSheetState,
            context
        )
    )

    // ------------------------------------------------------------------
    // Fullscreen transcript overlay
    // ------------------------------------------------------------------

    FullPlayerFullscreenOverlays(
        dependencies = dependencies,
        model = FullPlayerOverlayModel(state, episode, colorScheme, positionFlow),
        display = display,
        actions = actions,
        ui = ui
    )
}

private data class FullPlayerDownloadState(
    val isDownloaded: Boolean,
    val isDownloading: Boolean
)

private data class FullPlayerModalModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val colorScheme: ColorScheme,
    val positionFlow: Flow<Long>,
    val canGenerateTranscript: Boolean
)

private data class FullPlayerOverlayModel(
    val state: PlayerState,
    val episode: Episode,
    val colorScheme: ColorScheme,
    val positionFlow: Flow<Long>
)

@Composable
private fun FullPlayerFullscreenOverlays(
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
private fun FullPlayerTranscriptOverlay(
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
            colorScheme = model.colorScheme,
            isSyncEnabled = ui.isSyncEnabled,
            onSyncEnabledChange = { ui.isSyncEnabled = it },
            onSeek = { seekPosition ->
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("transcript_tap")
                playbackRepository.seekTo(seekPosition)
            },
            onPlayPause = { togglePlayback(model.state, playbackRepository) },
            onClose = { ui.showFullscreenTranscript = false },
            transcriptUrl = model.episode.transcriptUrl
        )
    }
}

private fun togglePlayback(state: PlayerState, playbackRepository: PlaybackRepository) {
    if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
}

@OptIn(ExperimentalMaterial3Api::class)
private data class FullPlayerModalResources(
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val queueSheetState: SheetState,
    val chaptersSheetState: SheetState,
    val context: android.content.Context
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FullPlayerModalSheets(
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
private fun PlayerSpeedSheet(
    playbackRepository: PlaybackRepository,
    model: FullPlayerModalModel,
    ui: FullPlayerUiState
) {
    SpeedSheet(
        currentSpeed = model.state.playbackSpeed,
        colorScheme = model.colorScheme,
        onSpeedSelected = { speed ->
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
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
private fun PlayerSleepSheet(
    playbackRepository: PlaybackRepository,
    model: FullPlayerModalModel,
    ui: FullPlayerUiState
) {
    SleepSheet(
        sleepTimerEnd = model.state.sleepTimerEnd,
        colorScheme = model.colorScheme,
        onDurationSelected = { minutes ->
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
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
private fun PlayerGenerateTranscriptDialog(
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
private fun PlayerShareSheet(
    model: FullPlayerModalModel,
    ui: FullPlayerUiState,
    context: android.content.Context
) {
    val currentPosition by model.positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    cx.aswin.boxcast.core.designsystem.components.ShareBottomSheet(
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
            cx.aswin.boxcast.core.data.ShareManager.shareEpisode(
                context = context,
                episode = model.episode,
                podcastTitle = model.podcast.title,
                timestampMs = timestamp,
                target = target
            )
        }
    )
}

private data class FullPlayerBodyModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val nextEpisode: Episode?,
    val isVideo: Boolean,
    val isVideoPodcast: Boolean,
    val download: FullPlayerDownloadState
)

private data class FullPlayerBodyResources(
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val statusBarPadding: Dp,
    val navBarPadding: Dp,
    val modifier: Modifier
)

private data class FullPlayerScrollResources(
    val scope: CoroutineScope,
    val layout: ResponsiveHeroLayout
)

@Composable
private fun FullPlayerBody(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: FullPlayerBodyResources
) {
    Box(
        modifier = resources.modifier
            .fillMaxSize()
            .playerCanvas(display.colorScheme)
    ) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val finalMaxHeight = screenHeight - resources.statusBarPadding - resources.navBarPadding

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = resources.statusBarPadding, bottom = resources.navBarPadding)
        ) {
            PlayerTopBar(
                colorScheme = display.colorScheme,
                showSwipeMinimizeTip = display.showSwipeMinimizeTip,
                isExpanded = display.isExpanded,
                onSwipeMinimizeTipDismissed = actions.onSwipeMinimizeTipDismissed,
                onCollapse = {
                    cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("collapsed")
                    actions.onCollapse()
                },
                onShare = { ui.showShareSheet = true }
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(display.sheetNestedScrollConnection)
            ) {
                val layoutValues = calculateResponsiveHeroLayout(screenWidth, finalMaxHeight, model.isVideo)
                FullPlayerScrollableContent(
                    model = model,
                    dependencies = dependencies,
                    display = display,
                    actions = actions,
                    flows = flows,
                    ui = ui,
                    resources = FullPlayerScrollResources(
                        resources.scope,
                        layoutValues
                    )
                )
            }
        }
        SnackbarHost(
            hostState = resources.snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun FullPlayerScrollableContent(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: FullPlayerScrollResources
) {
    val scrollState = rememberScrollState()
    // Reset scroll when collapsing so reopen always starts at the top.
    LaunchedEffect(display.isExpanded) {
        if (!display.isExpanded && scrollState.value > 0) {
            scrollState.scrollTo(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 6.dp else 14.dp))
        FullPlayerHeroSection(
            model = FullPlayerHeroModel(
                state = model.state,
                episode = model.episode,
                podcast = model.podcast,
                nextEpisode = model.nextEpisode,
                isVideo = model.isVideo,
                isVideoPodcast = model.isVideoPodcast,
                dimensions = resources.layout.dimensions
            ),
            positionFlow = flows.position,
            playbackRepository = dependencies.playbackRepository,
            display = display,
            actions = actions,
            ui = ui,
            isCompact = resources.layout.isCompact
        )
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 10.dp else 16.dp))
        PlayerMetadata(model.episode, model.podcast, display.colorScheme, actions)
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 8.dp else 14.dp))
        FullPlayerControls(
            model = FullPlayerControlModel(
                state = model.state,
                episode = model.episode,
                podcast = model.podcast,
                colorScheme = display.colorScheme,
                isDownloaded = model.download.isDownloaded,
                isDownloading = model.download.isDownloading,
                isCompact = resources.layout.isCompact
            ),
            dependencies = dependencies,
            flows = flows,
            ui = ui,
            scope = resources.scope
        )
        Spacer(modifier = Modifier.height(if (resources.layout.isCompact) 14.dp else 20.dp))
        FullPlayerSupportingContent(
            state = model.state,
            episode = model.episode,
            nextEpisode = model.nextEpisode,
            colorScheme = display.colorScheme,
            playbackRepository = dependencies.playbackRepository,
            actions = actions,
            ui = ui
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private data class FullPlayerHeroModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val nextEpisode: Episode?,
    val isVideo: Boolean,
    val isVideoPodcast: Boolean,
    val dimensions: HeroDimensions
)

private data class FullPlayerFlows(
    val position: Flow<Long>,
    val bufferedPosition: Flow<Long>
)

private data class FullPlayerControlModel(
    val state: PlayerState,
    val episode: Episode,
    val podcast: Podcast,
    val colorScheme: ColorScheme,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val isCompact: Boolean
)

@Composable
private fun FullPlayerHeroSection(
    model: FullPlayerHeroModel,
    positionFlow: Flow<Long>,
    playbackRepository: PlaybackRepository,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    ui: FullPlayerUiState,
    isCompact: Boolean
) {
    AnimatedContent(
        targetState = ui.showInlineTranscript,
        transitionSpec = {
            (fadeIn(tween(240)) + scaleIn(tween(280), initialScale = 0.96f)) togetherWith
                (fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.96f))
        },
        contentAlignment = Alignment.Center,
        label = "artworkTranscriptHero",
        modifier = Modifier.fillMaxWidth()
    ) { transcriptVisible ->
        if (transcriptVisible) {
            InlineTranscriptHero(
                content = InlineTranscriptContent(
                    transcript = model.state.currentTranscript,
                    positionFlow = positionFlow,
                    transcriptUrl = model.episode.transcriptUrl,
                    artworkUrl = model.episode.imageUrl?.takeIf { it.isNotBlank() }
                        ?: model.podcast.imageUrl
                ),
                colorScheme = display.colorScheme,
                isSyncEnabled = ui.isSyncEnabled,
                actions = InlineTranscriptActions(
                    onSyncEnabledChange = { ui.isSyncEnabled = it },
                    onSeek = { seekPosition ->
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("transcript_tap")
                        playbackRepository.seekTo(seekPosition)
                    },
                    onShowArtwork = { ui.showInlineTranscript = false },
                    onFullscreen = { ui.showFullscreenTranscript = true }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(model.dimensions.height, if (isCompact) 220.dp else 250.dp))
            )
        } else {
            ArtworkPlayerHero(model, positionFlow, playbackRepository, display)
        }
    }
    if (model.isVideoPodcast && !ui.showInlineTranscript) {
        VideoModeButtons(
            isAudioOnly = ui.isAudioOnly,
            colorScheme = display.colorScheme,
            onAudioOnlyChange = { ui.isAudioOnly = it },
            onFullscreenClick = { actions.onFullscreenVideoChange(true) }
        )
    }
}

@Composable
private fun ArtworkPlayerHero(
    model: FullPlayerHeroModel,
    positionFlow: Flow<Long>,
    playbackRepository: PlaybackRepository,
    display: FullPlayerDisplay
) {
    PlayerHero(
        artwork = PlayerHeroArtwork(
            episodeId = model.episode.id,
            artworkUrl = model.episode.imageUrl?.takeIf { it.isNotBlank() } ?: model.podcast.imageUrl,
            nextArtworkUrl = model.nextEpisode?.imageUrl?.takeIf { it.isNotBlank() }
                ?: model.nextEpisode?.podcastImageUrl?.takeIf { it.isNotBlank() },
            nextEpisodeTitle = model.nextEpisode?.title,
            chapterArtFlow = remember(model.state.currentChapters, positionFlow) {
                chapterArtFlow(positionFlow, model.state.currentChapters)
            }
        ),
        playback = PlayerHeroPlayback(
            isPlaying = model.state.isPlaying,
            isVideo = model.isVideo,
            isFullscreenVideo = display.isFullscreenVideo,
            controller = playbackRepository.controller,
            isExpanded = display.isExpanded
        ),
        dimensions = model.dimensions,
        colorScheme = display.colorScheme,
        onSkipNextEpisode = {
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("skip_next_episode")
            playbackRepository.skipToNextEpisode()
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PlayerMetadata(
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
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("episode_info")
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
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("podcast_info")
            actions.onCollapse()
            actions.onPodcastInfoClick(podcast)
        }
    )
}

@Composable
private fun MarqueeMetadataText(
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
private fun FullPlayerControls(
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
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("seek")
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
private fun FullPlayerPrimaryControls(
    model: FullPlayerControlModel,
    playbackRepository: PlaybackRepository
) {
    PrimaryControls(
        isPlaying = model.state.isPlaying,
        isLoading = model.state.isLoading,
        colorScheme = model.colorScheme,
        onPlayPause = {
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("play_pause")
            if (model.state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
        },
        onReplay = {
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("previous")
            playbackRepository.skipBackward()
        },
        onForward = {
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("next")
            playbackRepository.skipForward()
        }
    )
}

@Composable
private fun FullPlayerSecondaryControls(
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

private fun fullPlayerSelectionActions(
    playbackRepository: PlaybackRepository
) = SecondarySelectionActions(
    onSpeedSelected = { speed ->
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
            "speed_change",
            value = speed.toString()
        )
        playbackRepository.setPlaybackSpeed(speed)
    },
    onSleepTimerSelected = { minutes ->
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
            "sleep_timer",
            value = minutes.toString()
        )
        playbackRepository.setSleepTimer(minutes)
    }
)

private fun fullPlayerClickActions(
    model: FullPlayerControlModel,
    dependencies: FullPlayerDependencies,
    ui: FullPlayerUiState,
    scope: CoroutineScope
) = SecondaryClickActions(
    onQueueClick = {
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("queue")
        ui.showQueueSheet = true
    },
    onChaptersClick = {
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("chapters_sheet")
        ui.showChaptersSheet = true
    },
    onTranscriptClick = {
        if (model.state.currentTranscript.isNotEmpty()) {
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction(
                if (ui.showInlineTranscript) "transcript_hide" else "transcript_inline"
            )
            ui.showInlineTranscript = !ui.showInlineTranscript
        }
    },
    onLikeClick = {
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("like")
        scope.launch { dependencies.playbackRepository.toggleLike() }
    },
    onDownloadClick = {
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("download")
        scope.launch { toggleEpisodeDownload(model, dependencies.downloadRepository) }
    },
    onMarkPlayedClick = {
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("mark_played")
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

private suspend fun toggleEpisodeDownload(
    model: FullPlayerControlModel,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository
) {
    if (model.isDownloaded || model.isDownloading) {
        downloadRepository.removeDownload(model.episode.id)
    } else {
        downloadRepository.addDownload(model.episode, model.podcast)
    }
}

@Composable
private fun FullPlayerSupportingContent(
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
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("queue")
                ui.showQueueSheet = true
            },
            onPlayNext = {
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("skip_next_episode")
                playbackRepository.skipToNextEpisode()
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    NotesPreviewCard(
        description = episode.description,
        colorScheme = colorScheme,
        onOpenEpisodeInfo = {
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("episode_info")
            actions.onCollapse()
            actions.onEpisodeInfoClick(episode)
        }
    )
}

private data class PlayerQueueSheetModel(
    val state: PlayerState,
    val podcast: Podcast,
    val colorScheme: ColorScheme
)

@OptIn(ExperimentalMaterial3Api::class)
private data class PlayerQueueSheetResources(
    val scope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
    val sheetState: SheetState
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerQueueSheet(
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

private fun queueSheetActions(
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
            cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(fromUi),
            cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(toUi)
        )
    },
    onDragEnd = { episodeId, fromUi, toUi ->
        resources.scope.launch {
            playbackRepository.persistQueueOrder(
                movedEpisodeId = episodeId,
                fromQueueIndex = cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(fromUi),
                toQueueIndex = cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(toUi)
            )
        }
    }
)

private suspend fun handleQueueRemoval(
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
private fun PlayerChaptersSheet(
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
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("chapters_list")
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
private fun SheetDragHandle(colorScheme: ColorScheme) {
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
}

@Composable
private fun PlayerTopBar(
    colorScheme: ColorScheme,
    showSwipeMinimizeTip: Boolean,
    isExpanded: Boolean,
    onSwipeMinimizeTipDismissed: () -> Unit,
    onCollapse: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorScheme.onSurface.copy(alpha = 0.1f))
                .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        var tipVisible by remember { mutableStateOf(showSwipeMinimizeTip) }
        LaunchedEffect(showSwipeMinimizeTip, isExpanded) {
            if (showSwipeMinimizeTip && isExpanded) {
                tipVisible = true
                delay(3500)
                tipVisible = false
                onSwipeMinimizeTipDismissed()
            } else {
                tipVisible = false
            }
        }
        AnimatedContent(
            targetState = tipVisible && isExpanded,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "topBarLabel"
        ) { isShowingTip ->
            Text(
                text = if (isShowingTip) "↓ Swipe down to minimize" else "Now Playing",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isShowingTip) colorScheme.primary.copy(alpha = 0.8f) else colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colorScheme.onSurface.copy(alpha = 0.1f))
                .clickable(onClick = onShare),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share",
                tint = colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Immersive fullscreen video overlay with auto-hiding HUD and rotation toggle. */
private data class VideoFullscreenContent(
    val visible: Boolean,
    val episodeTitle: String,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionFlow: kotlinx.coroutines.flow.Flow<Long>,
    val controller: androidx.media3.common.Player?
)

private data class VideoFullscreenActions(
    val onExit: () -> Unit,
    val onPlayPause: () -> Unit,
    val onReplay: () -> Unit,
    val onForward: () -> Unit,
    val onSeek: (Long) -> Unit
)

@Composable
private fun VideoFullscreenOverlay(
    content: VideoFullscreenContent,
    colorScheme: ColorScheme,
    actions: VideoFullscreenActions
) {
    var isLandscape by rememberSaveable { mutableStateOf(true) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val activity = LocalActivity.current

    FullscreenVideoSystemUi(
        visible = content.visible,
        isLandscape = isLandscape,
        activity = activity,
        onExit = actions.onExit
    )

    AnimatedVisibility(
        visible = content.visible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)),
        exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)),
        modifier = Modifier.fillMaxSize()
    ) {
        LaunchedEffect(controlsVisible, content.isPlaying) {
            if (controlsVisible && content.isPlaying) {
                delay(3000)
                controlsVisible = false
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            ) {
                VideoPlayerView(content.controller)

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        VideoHudTopBar(
                            episodeTitle = content.episodeTitle,
                            onExit = actions.onExit,
                            onRotate = { isLandscape = !isLandscape },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        VideoTransportControls(
                            isPlaying = content.isPlaying,
                            colorScheme = colorScheme,
                            actions = actions,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        VideoProgressControls(
                            content = content,
                            colorScheme = colorScheme,
                            onSeek = actions.onSeek,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenVideoSystemUi(
    visible: Boolean,
    isLandscape: Boolean,
    activity: android.app.Activity?,
    onExit: () -> Unit
) {
    if (!visible) return
    DisposableEffect(isLandscape) {
        activity?.requestedOrientation = if (isLandscape) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val window = activity?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(onBack = onExit)
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoPlayerView(controller: androidx.media3.common.Player?) {
    if (controller == null) return
    var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }
    DisposableEffect(controller) {
        onDispose { playerViewRef?.player = null }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            androidx.media3.ui.PlayerView(context).apply {
                player = controller
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerViewRef = this
            }
        },
        update = { playerView ->
            if (playerView.player != controller) playerView.player = controller
            playerViewRef = playerView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun VideoHudTopBar(
    episodeTitle: String,
    onExit: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = episodeTitle.replace("+", " "),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onRotate,
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(Icons.Rounded.ScreenRotation, contentDescription = "Toggle Orientation", tint = Color.White)
        }
    }
}

@Composable
private fun VideoTransportControls(
    isPlaying: Boolean,
    colorScheme: ColorScheme,
    actions: VideoFullscreenActions,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoSeekButton(Icons.Rounded.Replay10, "Replay 10s", actions.onReplay)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colorScheme.primaryContainer)
                .clickable(onClick = actions.onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp),
                tint = colorScheme.onPrimaryContainer
            )
        }
        VideoSeekButton(Icons.Rounded.Forward30, "Forward 30s", actions.onForward)
    }
}

@Composable
private fun VideoSeekButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(Color.White.copy(alpha = 0.15f), CircleShape)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun VideoProgressControls(
    content: VideoFullscreenContent,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val positionMs by content.positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    var draggedPosition by remember { mutableStateOf<Float?>(null) }
    val displayedPosition = draggedPosition?.toLong() ?: positionMs
    if (content.durationMs <= 0) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Slider(
            value = draggedPosition ?: positionMs.toFloat(),
            onValueChange = { draggedPosition = it },
            onValueChangeFinished = {
                draggedPosition?.let { onSeek(it.toLong()) }
                draggedPosition = null
            },
            valueRange = 0f..content.durationMs.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(displayedPosition),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Text(
                text = "-" + formatTime((content.durationMs - displayedPosition).coerceAtLeast(0)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}
