package cx.aswin.boxcast.feature.explore

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.network.model.CuratedCuriosityPodcastDto
import cx.aswin.boxcast.core.network.model.DailyCuriosityDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    viewModel: LearnViewModel,
    playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository,
    bottomContentPadding: Dp,
    onEpisodeClick: (Episode) -> Unit,
    onQueueEpisode: (Episode) -> Unit,
    onPodcastClick: (feedId: Long?, itunesId: Long?, feedUrl: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by playbackRepository.playerState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        
        val isRefreshing = (uiState as? LearnUiState.Success)?.isRefreshing == true
        val pullToRefreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Use the passed in bottomContentPadding from MainActivity instead of the local empty Scaffold padding
            val bottomContentPaddingCalculated = bottomContentPadding

            when (val state = uiState) {
                is LearnUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        BoxLoreLoader.Expressive(size = 64.dp)
                    }
                }

                is LearnUiState.Success -> {
                    val context = LocalContext.current
                    var extractedColor by remember { mutableStateOf<Color?>(null) }
                    val accentColor = extractedColor ?: MaterialTheme.colorScheme.primary

                    // Dynamic color extraction from active card cover art in the stack
                    val dailyImage = state.data.questionsStack.firstOrNull()?.episode?.let { it.image ?: it.feedImage }
                    if (dailyImage != null) {
                        val painter = rememberAsyncImagePainter(
                            model = remember(dailyImage) {
                                ImageRequest.Builder(context)
                                    .data(dailyImage)
                                    .allowHardware(false)
                                    .build()
                            }
                        )
                        LaunchedEffect(painter.state) {
                            val painterState = painter.state
                            if (painterState is AsyncImagePainter.State.Success) {
                                val bitmap = (painterState.result.drawable as? BitmapDrawable)?.bitmap
                                if (bitmap != null) {
                                    extractedColor = extractDominantColor(bitmap)
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(bottom = bottomContentPaddingCalculated),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Centered brand logo at the top of the page (Expanded to 40.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Image(
                            painter = painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.logo_lore),
                            contentDescription = "Lore",
                            colorFilter = ColorFilter.tint(accentColor),
                            modifier = Modifier.height(40.dp)
                        )

                        // 2. Top flexible spacer
                        Spacer(modifier = Modifier.weight(1f))

                        // 3. Curiosity Card Stack
                        CuriosityCardStack(
                            questions = state.questionsStack,
                            isCurrentlyPlaying = { id -> playerState.currentEpisode?.id == id && playerState.isPlaying },
                            onSwipeLeft = { daily ->
                                viewModel.dismissCuriosity(daily.episode.id.toString())
                            },
                            onSwipeRight = { daily ->
                                val mappedEpisode = mapToEpisode(daily.episode)
                                onQueueEpisode(mappedEpisode)
                                viewModel.dismissCuriosity(daily.episode.id.toString())
                            },
                            onPlayClick = { daily ->
                                val isCurrent = playerState.currentEpisode?.id == daily.episode.id.toString()
                                if (isCurrent) {
                                    playbackRepository.togglePlayPause()
                                } else {
                                    val mappedEpisode = mapToEpisode(daily.episode)
                                    val podcast = cx.aswin.boxcast.core.model.Podcast(
                                        id = daily.episode.feedId?.toString() ?: "learn_fallback",
                                        title = daily.episode.feedTitle ?: "Podcast",
                                        artist = daily.episode.feedTitle ?: "Unknown",
                                        imageUrl = daily.episode.feedImage ?: daily.episode.image ?: ""
                                    )
                                    coroutineScope.launch {
                                        playbackRepository.playQueue(
                                            episodes = listOf(mappedEpisode),
                                            podcast = podcast,
                                            startIndex = 0,
                                            entryPoint = cx.aswin.boxcast.core.model.PlaybackEntryPoint.GENERIC
                                        )
                                    }
                                }
                            },
                            onEpisodeClick = { daily ->
                                val mappedEpisode = mapToEpisode(daily.episode)
                                onEpisodeClick(mappedEpisode)
                            },
                            onPodcastClick = { daily ->
                                onPodcastClick(
                                    daily.episode.feedId,
                                    null,
                                    "",
                                    daily.episode.feedTitle ?: "Podcast"
                                )
                            },
                            accentColor = accentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        )

                        // 4. Bottom flexible spacer (exactly matches top spacer weight to center cards vertically)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                is LearnUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                        FilledTonalButton(
                            onClick = { viewModel.loadData() }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

// Convert DTO EpisodeItem to Domain model Episode
private fun mapToEpisode(item: cx.aswin.boxcast.core.network.model.EpisodeItem): Episode {
    return Episode(
        id = item.id.toString(),
        title = item.title,
        description = item.description ?: "",
        audioUrl = item.enclosureUrl ?: "",
        imageUrl = item.image ?: item.feedImage,
        podcastImageUrl = item.feedImage,
        podcastTitle = item.feedTitle,
        podcastId = item.feedId?.toString(),
        duration = item.duration ?: 0,
        publishedDate = item.datePublished ?: 0L,
        chaptersUrl = item.chaptersUrl,
        transcriptUrl = item.transcriptUrl,
        enclosureType = item.enclosureType
    )
}

private fun extractDominantColor(bitmap: android.graphics.Bitmap): Color {
    val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
    val vibrant = palette.vibrantSwatch?.rgb
    val muted = palette.mutedSwatch?.rgb
    val dominant = palette.dominantSwatch?.rgb
    val colorInt = vibrant ?: muted ?: dominant ?: 0xFF6200EE.toInt()
    return Color(colorInt)
}

@Composable
private fun CuratedShowItem(
    show: CuratedCuriosityPodcastDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(110.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        OptimizedImage(
            url = show.artwork ?: show.image ?: "",
            proxyWidth = 220,
            contentDescription = null,
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = show.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        val author = show.author
        if (!author.isNullOrEmpty()) {
            Text(
                text = author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
