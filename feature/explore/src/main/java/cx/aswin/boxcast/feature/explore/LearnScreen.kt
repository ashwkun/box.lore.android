package cx.aswin.boxcast.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import android.graphics.drawable.BitmapDrawable
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.network.model.CuratedCuriosityPodcastDto
import cx.aswin.boxcast.core.network.model.DailyCuriosityDto
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalDensity

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        
        // Setup Pull to Refresh wrapper
        val isRefreshing = (uiState as? LearnUiState.Success)?.isRefreshing == true
        
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            when (val state = uiState) {
                is LearnUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                is LearnUiState.Success -> {
                    val context = LocalContext.current
                    var extractedColor by remember { mutableStateOf<Color?>(null) }
                    val accentColor = extractedColor ?: MaterialTheme.colorScheme.primary

                    // Dynamic color extraction from daily curiosity cover art
                    val dailyImage = state.data.questionOfTheDay?.episode?.let { it.image ?: it.feedImage }
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

                    val listState = rememberLazyListState()
                    val firstVisibleItemIndex = listState.firstVisibleItemIndex
                    val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
                    
                    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val collapsedHeaderHeight = 64.dp + statusBarHeight
                    val morphThreshold = with(LocalDensity.current) { 180.dp.toPx() }

                    val scrollFraction = remember(listState, firstVisibleItemIndex, firstVisibleItemScrollOffset) {
                        if (firstVisibleItemIndex > 0) 1f
                        else (firstVisibleItemScrollOffset.toFloat() / morphThreshold).coerceIn(0f, 1f)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Premium background glow inspired by the briefing screen
                        val backgroundColor = MaterialTheme.colorScheme.background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .graphicsLayer {
                                    alpha = (1f - scrollFraction) * 0.15f
                                }
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            accentColor,
                                            backgroundColor
                                        )
                                    )
                                )
                        )

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = collapsedHeaderHeight,
                                bottom = bottomContentPadding + 24.dp
                            )
                        ) {
                            // 1. Header Section
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 20.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.logo_lore),
                                        contentDescription = "Lore",
                                        colorFilter = ColorFilter.tint(accentColor),
                                        modifier = Modifier
                                            .height(54.dp)
                                            .fillMaxWidth(),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart
                                    )
                                    Text(
                                        text = "Feed your curiosity with daily micro-stories",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }

                            // 2. Curiosity Card Stack Section
                            item {
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
                                        val mappedEpisode = mapToEpisode(daily.episode)
                                        onEpisodeClick(mappedEpisode)
                                    },
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                )
                            }

                            // 3. Curated Categories Sections
                            items(state.data.categories) { category ->
                                if (category.shows.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = category.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                                        )
                                        
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(category.shows) { show ->
                                                CuratedShowItem(
                                                    show = show,
                                                    onClick = {
                                                        onPodcastClick(
                                                            show.id,
                                                            show.itunesId,
                                                            show.feedUrl ?: "",
                                                            show.title
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating Header Overlay
                        val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
                        val headerColor by animateColorAsState(
                            targetValue = surfaceColor.copy(alpha = scrollFraction),
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "headerColor"
                        )
                        val titleAlpha = if (scrollFraction > 0.6f) (scrollFraction - 0.6f) / 0.4f else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(collapsedHeaderHeight)
                                .background(headerColor)
                                .statusBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.logo_lore),
                                contentDescription = "Lore",
                                colorFilter = ColorFilter.tint(accentColor),
                                modifier = Modifier
                                    .height(28.dp)
                                    .graphicsLayer { alpha = titleAlpha }
                            )
                        }
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
        // Thumbnail Artwork
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.size(110.dp)
        ) {
            OptimizedImage(
                url = show.image ?: show.artwork ?: "",
                proxyWidth = 220,
                contentDescription = show.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Show Title
        Text(
            text = show.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
        
        // Author
        Text(
            text = show.author ?: "Unknown",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
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
