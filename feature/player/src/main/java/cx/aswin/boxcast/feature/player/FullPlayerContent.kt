package cx.aswin.boxcast.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerContent(
    playbackRepository: PlaybackRepository,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    isDarkTheme: Boolean,
    colorScheme: ColorScheme,
    onCollapse: () -> Unit,
    onEpisodeInfoClick: (Episode) -> Unit = {},
    onPodcastInfoClick: (Podcast) -> Unit = {},
    showSwipeMinimizeTip: Boolean = false,
    onSwipeMinimizeTipDismissed: () -> Unit = {},
    showTitleTip: Boolean = false,
    onTitleTipDismissed: () -> Unit = {},
    isExpanded: Boolean = true // Added so timers only tick when visible
) {
    val state by playbackRepository.playerState.collectAsState()
    val episode = state.currentEpisode ?: return
    val podcast = state.currentPodcast ?: return
    
    val containerColor = colorScheme.primaryContainer.copy(alpha = 0.6f).compositeOver(colorScheme.surface)
    
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    
    val window = (LocalContext.current as? android.app.Activity)?.window
    val scope = rememberCoroutineScope()
    
    // Queue bottom sheet state
    var showQueueSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    SideEffect {
        window?.let { win ->
             val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
             insetsController.isAppearanceLightStatusBars = !isDarkTheme
             insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
    
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
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("collapsed", podcast.id, episode.id)
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
        
            Box(modifier = Modifier.size(42.dp))
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
            positionMs = state.position,
            durationMs = state.duration,
            bufferedPositionMs = state.bufferedPosition,
            playbackSpeed = state.playbackSpeed,
            sleepTimerEnd = state.sleepTimerEnd,
            isLiked = state.isLiked,
            colorScheme = colorScheme,
            onPlayPause = {
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("play_pause", podcast.id, episode.id)
                if (state.isPlaying) playbackRepository.pause() else playbackRepository.resume()
            },
            onSeek = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("seek", podcast.id, episode.id)
                playbackRepository.seekTo(it) 
            },
            onPrevious = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("previous", podcast.id, episode.id)
                playbackRepository.skipBackward() 
            },
            onNext = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("next", podcast.id, episode.id)
                playbackRepository.skipForward() 
            },
            onSkipPreviousEpisode = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("skip_previous_episode", podcast.id, episode.id)
                playbackRepository.skipToPreviousEpisode() 
            },
            onSkipNextEpisode = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("skip_next_episode", podcast.id, episode.id)
                playbackRepository.skipToNextEpisode() 
            },
            onSetSpeed = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("speed_change", podcast.id, episode.id, value = it.toString())
                playbackRepository.setPlaybackSpeed(it) 
            },
            onSetSleepTimer = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("sleep_timer", podcast.id, episode.id, value = it?.toString() ?: "off")
                playbackRepository.setSleepTimer(it) 
            },
            onLikeClick = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("like", podcast.id, episode.id)
                scope.launch { playbackRepository.toggleLike() } 
            },
            onDownloadClick = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("download", podcast.id, episode.id)
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
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("queue", podcast.id, episode.id)
                showQueueSheet = true 
            },
            onEpisodeInfoClick = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("episode_info", podcast.id, episode.id)
                onCollapse()
                onEpisodeInfoClick(episode) 
            },
            onPodcastInfoClick = { 
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFullPlayerInteraction("podcast_info", podcast.id, episode.id)
                onCollapse()
                onPodcastInfoClick(podcast) 
            },
            showTitleTip = showTitleTip,
            onTitleTipDismissed = onTitleTipDismissed,
            isExpanded = isExpanded,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
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
                        playbackRepository.removeFromQueue(ep.id)
                    }
                },
                onClose = { showQueueSheet = false }
            )
        }
    }
}
