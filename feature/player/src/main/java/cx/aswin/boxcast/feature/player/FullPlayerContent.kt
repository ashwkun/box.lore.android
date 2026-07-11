package cx.aswin.boxcast.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import cx.aswin.boxcast.core.designsystem.theme.LocalEffectiveDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerContent(
    playbackRepository: PlaybackRepository,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    colorScheme: ColorScheme,
    isFullscreenVideo: Boolean = false,
    onFullscreenVideoChange: (Boolean) -> Unit = {},
    onCollapse: () -> Unit,
    onEpisodeInfoClick: (Episode) -> Unit = {},
    onPodcastInfoClick: (Podcast) -> Unit = {},
    showSwipeMinimizeTip: Boolean = false,
    onSwipeMinimizeTipDismissed: () -> Unit = {},
    showTitleTip: Boolean = false,
    onTitleTipDismissed: () -> Unit = {},
    isExpanded: Boolean = true // Added so timers only tick when visible
) {
    val state by remember(playbackRepository) {
        playbackRepository.playerState
            .map { it.copy(position = 0, bufferedPosition = 0) }
            .distinctUntilChanged()
    }.collectAsState(initial = playbackRepository.playerState.value)
    
    val positionFlow = remember(playbackRepository) {
        playbackRepository.playerState.map { it.position }.distinctUntilChanged()
    }
    val bufferedPositionFlow = remember(playbackRepository) {
        playbackRepository.playerState.map { it.bufferedPosition }.distinctUntilChanged()
    }
    
    val episode = state.currentEpisode ?: return
    val podcast = state.currentPodcast ?: return
    
    val containerColor = colorScheme.primaryContainer.copy(alpha = 0.6f).compositeOver(colorScheme.surface)
    
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    
    val window = (LocalContext.current as? android.app.Activity)?.window
    val scope = rememberCoroutineScope()
    
    // Queue bottom sheet state
    var showQueueSheet by remember { mutableStateOf(false) }
    val queueSnackbarHostState = remember { SnackbarHostState() }
    var showShareSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Chapters bottom sheet state
    var showChaptersSheet by remember { mutableStateOf(false) }
    val chaptersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Fullscreen Transcript State
    var showFullscreenTranscript by remember(episode.id) { mutableStateOf(false) }
    var isSyncEnabled by remember(episode.id) { mutableStateOf(true) }
    
    var isLandscape by rememberSaveable {
        android.util.Log.d("BoxCastPlayer", "isLandscape initialized to true in FullPlayerContent")
        mutableStateOf(true)
    }
    var controlsVisible by rememberSaveable {
        android.util.Log.d("BoxCastPlayer", "controlsVisible initialized to true in FullPlayerContent")
        mutableStateOf(true)
    }

    if (isFullscreenVideo) {
        val activity = LocalContext.current as? android.app.Activity
        DisposableEffect(isFullscreenVideo, isLandscape) {
            android.util.Log.d("BoxCastPlayer", "DisposableEffect running in FullPlayerContent: isFullscreenVideo=$isFullscreenVideo, isLandscape=$isLandscape")
            val originalOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            
            activity?.requestedOrientation = if (isLandscape) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            
            // Hide system UI (immersive fullscreen mode)
            val window = activity?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            
            onDispose {
                android.util.Log.d("BoxCastPlayer", "DisposableEffect onDispose in FullPlayerContent: forcing portrait orientation")
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                if (window != null) {
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        
        BackHandler {
            android.util.Log.d("BoxCastPlayer", "BackHandler triggered in FullPlayerContent! Setting isFullscreenVideo = false")
            onFullscreenVideoChange(false)
        }
    }
    
    androidx.activity.compose.BackHandler(enabled = showFullscreenTranscript) {
        showFullscreenTranscript = false
    }
    
    val resolvedDark = LocalEffectiveDarkTheme.current
    SideEffect {
        window?.let { win ->
             val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
             insetsController.isAppearanceLightStatusBars = !resolvedDark
             insetsController.isAppearanceLightNavigationBars = !resolvedDark
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor)
                .padding(top = statusBarPadding, bottom = navBarPadding)
        ) {
            // Top bar
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
                    .clickable(onClick = { 
                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("collapsed")
                        onCollapse() 
                    }),
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
                    delay(3500)
                    tipVisible = false
                    onSwipeMinimizeTipDismissed()
                }
            }

            androidx.compose.animation.AnimatedContent(
                targetState = tipVisible && isExpanded,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith 
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                },
                label = "Header Text Animation"
            ) { isShowingTip ->
                if (isShowingTip) {
                    Text(
                        text = "↓ Swipe down to minimize",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary.copy(alpha = 0.8f) // Accent color for visibility
                    )
                } else {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colorScheme.onSurface.copy(alpha = 0.1f))
                    .clickable(onClick = { 
                        android.util.Log.d("ShareBottomSheet", "Share button clicked in player! Setting showShareSheet = true")
                        showShareSheet = true 
                    }),
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
        
        Spacer(modifier = Modifier.height(12.dp))

        // Main Player Content (fills available space)
        val isDownloaded by remember(episode.id) { 
            downloadRepository.isDownloaded(episode.id)
        }.collectAsState(initial = false)

        val isDownloading by remember(episode.id) {
            downloadRepository.isDownloading(episode.id)
        }.collectAsState(initial = false)

        SharedPlayerContent(
            podcast = podcast,
            episode = episode,
            isPlaying = state.isPlaying,
            isLoading = state.isLoading,
            positionFlow = positionFlow,
            durationMs = state.duration,
            bufferedPositionFlow = bufferedPositionFlow,
            playbackSpeed = state.playbackSpeed,
            sleepTimerEnd = state.sleepTimerEnd,
            isLiked = state.isLiked,
            colorScheme = colorScheme,
            controller = playbackRepository.controller,
            onPlayPause = {
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("play_pause")
                if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
            },
            onSeek = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("seek")
                playbackRepository.seekTo(it) 
            },
            onPrevious = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("previous")
                playbackRepository.skipBackward() 
            },
            onNext = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("next")
                playbackRepository.skipForward() 
            },
            onSkipPreviousEpisode = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("skip_previous_episode")
                playbackRepository.skipToPreviousEpisode() 
            },
            onSkipNextEpisode = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("skip_next_episode")
                playbackRepository.skipToNextEpisode() 
            },
            onSetSpeed = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("speed_change", value = it.toString())
                playbackRepository.setPlaybackSpeed(it) 
            },
            onSetSleepTimer = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("sleep_timer", value = it?.toString() ?: "off")
                playbackRepository.setSleepTimer(it) 
            },
            onLikeClick = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("like")
                scope.launch { playbackRepository.toggleLike() } 
            },
            onDownloadClick = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("download")
                scope.launch {
                    if (isDownloaded || isDownloading) {
                        downloadRepository.removeDownload(episode.id)
                    } else {
                        downloadRepository.addDownload(episode, podcast)
                    }
                }
            },
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            onQueueClick = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("queue")
                showQueueSheet = true 
            },
            onEpisodeInfoClick = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("episode_info")
                onCollapse()
                onEpisodeInfoClick(episode) 
            },
            onPodcastInfoClick = { 
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("podcast_info")
                onCollapse()
                onPodcastInfoClick(podcast) 
            },
            showTitleTip = showTitleTip,
            onTitleTipDismissed = onTitleTipDismissed,
            isExpanded = isExpanded,
            chapters = state.currentChapters,
            transcript = state.currentTranscript,
            isChaptersLoading = state.isChaptersLoading,
            autoTranscriptState = state.autoTranscriptState,
            autoChaptersState = state.autoChaptersState,
            autoTranscriptLimitLeft = state.autoTranscriptLimitLeft,
            onGenerateTranscript = { playbackRepository.generateAutoTranscript() },
            isSyncEnabled = isSyncEnabled,
            onSyncEnabledChange = { isSyncEnabled = it },
            onChaptersClick = {
                showChaptersSheet = true
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("chapters_sheet")
            },
            onFullscreenTranscriptClick = {
                showFullscreenTranscript = true
                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("transcript_view")
            },
            isFullscreenVideo = isFullscreenVideo,
            onFullscreenVideoChange = onFullscreenVideoChange,
            modifier = Modifier.fillMaxSize()
        )
        }
        SnackbarHost(
            hostState = queueSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
    
    // Queue Bottom Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            dragHandle = {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            QueueSheetContent(
                queue = state.queue.drop(1), // Skip currently playing
                currentPodcast = podcast,
                colorScheme = colorScheme,
                actions = QueueSheetActions(
                        onPlayEpisode = { ep ->
                            scope.launch {
                                val freshQueue = playbackRepository.playerState.value.queue
                                val epPodcastId = ep.podcastId
                                val episodePodcast = if (epPodcastId != null && epPodcastId != podcast.id) {
                                    cx.aswin.boxcast.core.model.Podcast(
                                        id = epPodcastId,
                                        title = ep.podcastTitle ?: "Unknown",
                                        artist = ep.podcastArtist ?: "",
                                        imageUrl = ep.podcastImageUrl ?: "",
                                        description = null,
                                        genre = ep.podcastGenre ?: ""
                                    )
                                } else {
                                    podcast
                                }
                                playbackRepository.playFromQueueIndex(ep.id, freshQueue, episodePodcast)
                                showQueueSheet = false
                            }
                        },
                        onRemoveEpisode = { ep ->
                            scope.launch {
                                // Defer the AUTO_FILL rejection signal until the undo window
                                // lapses, so an undone remove doesn't count as a rejection.
                                val removed = playbackRepository.removeFromQueue(ep.id, deferSkipSignal = true)
                                if (removed != null) {
                                    val result = queueSnackbarHostState.showSnackbar(
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
                            }
                        },
                        onClose = { showQueueSheet = false },
                        onMove = { fromUi, toUi ->
                            playbackRepository.moveQueueItem(
                                cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(fromUi),
                                cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(toUi)
                            )
                        },
                        onDragEnd = { episodeId, fromUi, toUi ->
                            scope.launch {
                                playbackRepository.persistQueueOrder(
                                    movedEpisodeId = episodeId,
                                    fromQueueIndex = cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(fromUi),
                                    toQueueIndex = cx.aswin.boxcast.core.data.QueueMath.uiIndexToQueueIndex(toUi)
                                )
                            }
                        }
                    )
                )
        }
    }

    // Chapters Bottom Sheet
    if (showChaptersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChaptersSheet = false },
            sheetState = chaptersSheetState,
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            ChaptersSheetContent(
                chapters = state.currentChapters,
                positionFlow = positionFlow,
                colorScheme = colorScheme,
                onSeek = { seekPos ->
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("chapters_list")
                    playbackRepository.seekTo(seekPos)
                    showChaptersSheet = false
                },
                onClose = { showChaptersSheet = false },
                chaptersUrl = state.currentEpisode?.chaptersUrl,
                isChaptersLoading = state.isChaptersLoading,
                hasTranscript = state.autoTranscriptState == cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState.NONE || state.autoTranscriptState == cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState.COMPLETED,
                onGenerateChapters = { playbackRepository.generateAutoChapters() }
            )
        }
    }
    
    // Fullscreen Transcript Overlay
    androidx.compose.animation.AnimatedVisibility(
        visible = showFullscreenTranscript,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
    ) {
        FullscreenTranscriptScreen(
            transcript = state.currentTranscript,
            positionFlow = positionFlow,
            isPlaying = state.isPlaying,
            isLoading = state.isLoading,
            durationMs = state.duration,
            colorScheme = colorScheme,
            isSyncEnabled = isSyncEnabled,
            onSyncEnabledChange = { isSyncEnabled = it },
            onSeek = { seekPos ->
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("transcript_tap")
                playbackRepository.seekTo(seekPos)
            },
            onPlayPause = {
                if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
            },
            onClose = { showFullscreenTranscript = false },
            transcriptUrl = state.currentEpisode?.transcriptUrl
        )
    }

    // Fullscreen video overlay (bypassing parent paddings/top bar entirely)
    androidx.compose.animation.AnimatedVisibility(
        visible = isFullscreenVideo,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)) + 
                androidx.compose.animation.scaleIn(initialScale = 0.95f, animationSpec = androidx.compose.animation.core.tween(400)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)) + 
               androidx.compose.animation.scaleOut(targetScale = 0.95f, animationSpec = androidx.compose.animation.core.tween(300)),
        modifier = Modifier.fillMaxSize()
    ) {
        // Controls visibility auto-fade timer
        LaunchedEffect(controlsVisible, state.isPlaying) {
            if (controlsVisible && state.isPlaying) {
                delay(3000)
                controlsVisible = false
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                            }
                        )
                    }
            ) {
                // Video View (rebound to same controller)
                val controller = playbackRepository.controller
                if (controller != null) {
                    var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }
                    DisposableEffect(controller) {
                        onDispose {
                            playerViewRef?.player = null
                        }
                    }
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                player = controller
                                useController = false // Custom overlay instead
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                playerViewRef = this
                            }
                        },
                        update = { playerView ->
                            if (playerView.player != controller) {
                                playerView.player = controller
                            }
                            playerViewRef = playerView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Controls HUD
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        // Top Bar: Back, Title, Rotate Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = { onFullscreenVideoChange(false) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FullscreenExit,
                                        contentDescription = "Exit Fullscreen",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = (episode.title).replace("+", " "),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            // Rotation toggle
                            IconButton(
                                onClick = { isLandscape = !isLandscape },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ScreenRotation,
                                    contentDescription = "Toggle Orientation",
                                    tint = Color.White
                                )
                            }
                        }

                        // Central Playback Controls
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Skip Back 10s
                            IconButton(
                                onClick = { playbackRepository.skipBackward() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Replay10,
                                    contentDescription = "Replay 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Play / Pause
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.primaryContainer)
                                    .clickable { if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(36.dp),
                                    tint = colorScheme.onPrimaryContainer
                                )
                            }

                            // Skip Forward 30s
                            IconButton(
                                onClick = { playbackRepository.skipForward() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forward30,
                                    contentDescription = "Forward 30s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Bottom section: Progress Bar + Time labels
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        ) {
                            val durationMs = state.duration
                            val positionMs = state.position
                            if (durationMs > 0) {
                                Slider(
                                    value = positionMs.toFloat(),
                                    onValueChange = { playbackRepository.seekTo(it.toLong()) },
                                    valueRange = 0f..durationMs.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = colorScheme.primary,
                                        activeTrackColor = colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(positionMs),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "-" + formatTime((durationMs - positionMs).coerceAtLeast(0)),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    android.util.Log.d("ShareBottomSheet", "Recomposing FullPlayerContent: showShareSheet = $showShareSheet")
    if (showShareSheet) {
        val context = LocalContext.current
        cx.aswin.boxcast.core.designsystem.components.ShareBottomSheet(
            id = episode.id,
            type = "episode",
            title = episode.title,
            subtitle = podcast.title,
            onDismissRequest = { showShareSheet = false },
            durationMs = episode.duration * 1000L,
            currentPositionMs = state.position,
            showTimestampOption = true,
            onShare = { _, _, t ->
                cx.aswin.boxcast.core.data.ShareManager.shareEpisode(context, episode, podcast.title, t)
            }
        )
    }
}
