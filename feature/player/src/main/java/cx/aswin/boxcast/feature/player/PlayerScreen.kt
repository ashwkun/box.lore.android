package cx.aswin.boxcast.feature.player

import cx.aswin.boxcast.core.designsystem.theme.simpleSharedElement
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Cast

import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.launch
import kotlin.random.Random
import cx.aswin.boxcast.feature.player.components.SimplePlayerControls
import cx.aswin.boxcast.core.designsystem.components.AdvancedPlayerControls
import cx.aswin.boxcast.core.designsystem.theme.generateBrandColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.ColorScheme
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size


@Composable
fun PlayerRoute(
    podcastId: String,
    apiBaseUrl: String,
    publicKey: String,
    playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val database = androidx.compose.runtime.remember { cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(application) }
    val downloadRepository = androidx.compose.runtime.remember { cx.aswin.boxcast.core.data.DownloadRepository(application, database) }

    
    val viewModel: PlayerViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(application, apiBaseUrl, publicKey, downloadRepository, playbackRepository) as T
            }
        }
    )
    
    LaunchedEffect(podcastId) {
        viewModel.loadPodcast(podcastId)
    }

    val uiState by viewModel.uiState.collectAsState()
    
    PlayerScreen(
        uiState = uiState,
        downloadRepository = downloadRepository,
        onBackClick = onBackClick,
        onPlayPause = viewModel::togglePlayPause,
        onEpisodeClick = viewModel::playEpisode,
        onSeek = viewModel::seekTo,
        onSkipForward = viewModel::skipForward,
        onSkipBackward = viewModel::skipBackward,
        onSetSpeed = viewModel::setPlaybackSpeed,
        onSetSleepTimer = viewModel::setSleepTimer,
        onToggleLike = viewModel::toggleLike,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    onBackClick: () -> Unit,
    onPlayPause: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onSeek: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Cast */ }) {
                        Icon(Icons.Rounded.Cast, contentDescription = "Cast")
                    }
                    IconButton(onClick = { /* TODO: Share */ }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        contentWindowInsets = WindowInsets(0), // Full bleed
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState) {
                is PlayerUiState.Loading -> {
                    // M3 Expressive: Morphing Loader
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BoxCastLoader.Expressive()
                    }
                }
                is PlayerUiState.Success -> {
                        PlayerContent(
                            podcast = uiState.podcast,
                            episodes = uiState.episodes,
                            currentEpisode = uiState.currentEpisode,
                            isPlaying = uiState.isPlaying,
                            isLoading = uiState.isLoading,
                            positionMs = uiState.positionMs,
                            durationMs = uiState.durationMs,
                            playbackSpeed = uiState.playbackSpeed,
                            sleepTimerEnd = uiState.sleepTimerEnd,
                            downloadRepository = downloadRepository, // Pass down

                            isLiked = uiState.isLiked,
                            onPlayPause = onPlayPause, // Pass down
                            onEpisodeClick = onEpisodeClick,
                            onSeek = onSeek,
                            onSkipForward = onSkipForward,
                            onSkipBackward = onSkipBackward,
                            onSetSpeed = onSetSpeed,
                            onSetSleepTimer = onSetSleepTimer,
                            onToggleLike = onToggleLike,
                            listState = listState,
                            coroutineScope = coroutineScope
                        )
                }
                is PlayerUiState.Error -> {
                    Text("Error loading player", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
fun PlayerContent(
    podcast: Podcast,
    episodes: List<Episode>,
    currentEpisode: Episode?,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    sleepTimerEnd: Long?,
    isLiked: Boolean,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    onPlayPause: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onSeek: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSetSpeed: (Float) -> Unit,

    onSetSleepTimer: (Int) -> Unit,
    onToggleLike: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    // 1. Color Extraction Logic
    val context = LocalContext.current
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    var extractedColorScheme by remember { mutableStateOf<ColorScheme?>(null) }
    val colorScheme = extractedColorScheme ?: MaterialTheme.colorScheme
    
    val imageUrl = currentEpisode?.imageUrl ?: podcast.imageUrl
    
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(100, 100)) // Low res for palette is fine
            .allowHardware(false)
            .build()
    )
    
    LaunchedEffect(imageUrl, painter.state) {
        val state = painter.state
        if (state is coil.compose.AsyncImagePainter.State.Success) {
             val bitmap = (state.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
             if (bitmap != null) {
                 val seed = extractSeedColor(bitmap)
                 extractedColorScheme = generateBrandColorScheme(seed, isDarkTheme)
             }
        }
    }

    // 2. State Observations
    // Use the downloadRepository to observe download state
    val isDownloaded by remember(currentEpisode?.id) {
        if (currentEpisode != null) downloadRepository.isDownloaded(currentEpisode.id) else kotlinx.coroutines.flow.flowOf(false)
    }.collectAsState(initial = false)

    val isDownloading by remember(currentEpisode?.id) {
        if (currentEpisode != null) downloadRepository.isDownloading(currentEpisode.id) else kotlinx.coroutines.flow.flowOf(false)
    }.collectAsState(initial = false)

    LaunchedEffect(currentEpisode?.id, isDownloading, isDownloaded) {
        if (currentEpisode != null) {
            android.util.Log.d("PlayerScreen", "Episode: ${currentEpisode.id}, isDownloading: $isDownloading, isDownloaded: $isDownloaded")
        }
    }
    
    SharedPlayerContent(
        podcast = podcast,
        episode = currentEpisode,
        isPlaying = isPlaying,
        isLoading = isLoading,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedPositionMs = 0L, // No buffer info in this screen yet
        playbackSpeed = playbackSpeed,
        sleepTimerEnd = sleepTimerEnd,
        colorScheme = colorScheme,
        onPlayPause = onPlayPause,
        onSeek = onSeek,
        onPrevious = onSkipBackward,
        onNext = onSkipForward,
        onSetSpeed = onSetSpeed,

        onSetSleepTimer = onSetSleepTimer,
        // Updated: Pass the passed isLiked state
        isLiked = isLiked,
        onLikeClick = onToggleLike,
        // Download Support
        isDownloaded = isDownloaded,
        isDownloading = isDownloading,
        onDownloadClick = {
            if (currentEpisode != null) {
                coroutineScope.launch {
                    if (isDownloaded || isDownloading) {
                        downloadRepository.removeDownload(currentEpisode.id)
                    } else {
                        downloadRepository.addDownload(currentEpisode, podcast)
                    }
                }
            }
        },
        onQueueClick = { 
             coroutineScope.launch {
                 listState.animateScrollToItem(0)
             }
        },

        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),

        footerContent = {
             Spacer(modifier = Modifier.height(32.dp))
             
             // Up Next
             Text(
                 text = "Up Next",
                 style = MaterialTheme.typography.titleLarge,
                 fontWeight = FontWeight.Bold,
                 modifier = Modifier.align(Alignment.Start)
             )
             
             Spacer(modifier = Modifier.height(8.dp))
             
             // M3 Expressive: Segmented Episode List
             androidx.compose.foundation.lazy.LazyColumn(
                 state = listState,
                 modifier = Modifier
                     .fillMaxWidth()
                     .weight(1f),
                 contentPadding = PaddingValues(bottom = 16.dp),
                 verticalArrangement = Arrangement.spacedBy(8.dp)
             ) {
                 items(episodes.filter { it.id != currentEpisode?.id }) { ep ->
                      androidx.compose.material3.ElevatedCard(
                          onClick = { onEpisodeClick(ep) },
                          modifier = Modifier.fillMaxWidth(),
                          shape = MaterialTheme.shapes.medium
                      ) {
                          Row(
                              modifier = Modifier.padding(12.dp),
                              verticalAlignment = Alignment.CenterVertically
                          ) {
                              AsyncImage(
                                  model = ep.imageUrl,
                                  contentDescription = null,
                                  modifier = Modifier
                                      .size(48.dp)
                                      .clip(MaterialTheme.shapes.small),
                                  contentScale = ContentScale.Crop
                              )
                              Spacer(modifier = Modifier.width(12.dp))
                              Text(
                                  text = ep.title,
                                  style = MaterialTheme.typography.bodyMedium,
                                  maxLines = 2,
                                  overflow = TextOverflow.Ellipsis
                              )
                          }
                      }
                 }
             }
        }
    )
}



