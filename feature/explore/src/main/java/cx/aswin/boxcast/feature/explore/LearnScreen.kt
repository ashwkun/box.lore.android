package cx.aswin.boxcast.feature.explore

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLearnScreenViewed()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.trackScreenExit()
                androidx.lifecycle.Lifecycle.Event.ON_START -> viewModel.onScreenResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.trackScreenExit()
        }
    }

    // Extract dominant color state at screen level
    var extractedColor by remember { mutableStateOf<Color?>(null) }
    val baseAccentColor = extractedColor ?: MaterialTheme.colorScheme.primary

    // Animate color transition smoothly for ambient background gradient and tinting
    val animatedAccentColor by animateColorAsState(
        targetValue = baseAccentColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "AccentColorTransition"
    )

    // Infinite transition for fluid background glow animations (pulsing and drifting)
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundGlowPulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulseScale"
    )
    
    val driftX by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowDriftX"
    )

    // Trigger color extraction based on active card cover artwork changes
    val activeCardImage = (uiState as? LearnUiState.Success)?.questionsStack?.firstOrNull()?.episode?.let {
        it.image ?: it.feedImage
    }

    LaunchedEffect(activeCardImage) {
        if (activeCardImage.isNullOrEmpty()) {
            extractedColor = null
            return@LaunchedEffect
        }
        try {
            val loader = coil.Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(activeCardImage)
                .allowHardware(false) // Must be a software bitmap for Palette pixel extraction
                .build()
            val result = loader.execute(request)
            if (result is coil.request.SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    // Generate palette on Default dispatcher to keep UI thread smooth
                    val color = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        extractDominantColor(bitmap)
                    }
                    extractedColor = color
                }
            } else {
                extractedColor = null
            }
        } catch (e: Exception) {
            extractedColor = null
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
        // Ambient background gradient glow wrapping the pull to refresh box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val density = this
                    val driftXPx = with(density) { driftX.dp.toPx() }

                    // 1. Top-center ambient glow orb (Breathing and drifting gently)
                    val topGlowBrush = Brush.radialGradient(
                        colors = listOf(
                            animatedAccentColor.copy(alpha = 0.25f),
                            animatedAccentColor.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = size.width / 2f + driftXPx,
                            y = -size.height * 0.1f
                        ),
                        radius = size.height * 0.85f * pulseScale
                    )
                    drawRect(brush = topGlowBrush)

                    // 2. Bottom-right ambient glow orb (Pulsing out-of-phase for a fluid lava-lamp atmosphere)
                    val bottomGlowBrush = Brush.radialGradient(
                        colors = listOf(
                            animatedAccentColor.copy(alpha = 0.18f),
                            animatedAccentColor.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = size.width * 0.8f - driftXPx,
                            y = size.height * 0.95f
                        ),
                        radius = size.height * 0.65f * (2f - pulseScale)
                    )
                    drawRect(brush = bottomGlowBrush)
                }
        ) {
            val isRefreshing = (uiState as? LearnUiState.Success)?.isRefreshing == true
            val pullToRefreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                // Apply a baseline 16.dp safety padding on top of the dynamic bottom content padding
                val bottomContentPaddingCalculated = bottomContentPadding + 16.dp

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
                        val handleDismiss: (DailyCuriosityDto) -> Unit = { daily ->
                            val mappedEpisode = mapToEpisode(daily.episode)
                            viewModel.trackCardDismissed()
                            trackLearnCardAction("dismiss", mappedEpisode)
                            viewModel.dismissCuriosity(mappedEpisode.id)
                        }

                        val handleQueue: (DailyCuriosityDto) -> Unit = { daily ->
                            val mappedEpisode = mapToEpisode(daily.episode)
                            viewModel.trackCardQueued()
                            trackLearnCardAction("queue", mappedEpisode)
                            onQueueEpisode(mappedEpisode)
                            viewModel.dismissCuriosity(mappedEpisode.id)
                        }

                        val handleInfo: (DailyCuriosityDto) -> Unit = { daily ->
                            val mappedEpisode = mapToEpisode(daily.episode)
                            viewModel.trackInfoClicked()
                            trackLearnCardAction("info", mappedEpisode)
                            onEpisodeClick(mappedEpisode)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(bottom = bottomContentPaddingCalculated),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1. Centered brand logo at the top of the page (Sized at 32.dp to save vertical space)
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                painter = painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.logo_lore),
                                contentDescription = "Lore",
                                colorFilter = ColorFilter.tint(animatedAccentColor),
                                modifier = Modifier.height(32.dp)
                            )

                            // 2. Top flexible spacer (absorbs 50% of the dynamic vertical margin)
                            Spacer(modifier = Modifier.weight(1f))

                            // 3. Curiosity Card Stack with 28.dp horizontal padding to avoid being too wide
                            CuriosityCardStack(
                                questions = state.questionsStack,
                                isCurrentlyPlaying = { id -> playerState.currentEpisode?.id == id && playerState.isPlaying },
                                isCurrentlyLoading = { id -> playerState.currentEpisode?.id == id && playerState.isLoading },
                                onSwipeLeft = handleDismiss,
                                onSwipeRight = handleQueue,
                                onPlayClick = { daily ->
                                    val mappedEpisode = mapToEpisode(daily.episode)
                                    viewModel.trackPlayClicked()
                                    trackLearnCardAction("play", mappedEpisode)
                                    val isCurrent = playerState.currentEpisode?.id == mappedEpisode.id
                                    if (isCurrent) {
                                        playbackRepository.togglePlayPause()
                                    } else {
                                        val podcast = cx.aswin.boxcast.core.model.Podcast(
                                            id = mappedEpisode.podcastId ?: "learn_fallback",
                                            title = mappedEpisode.podcastTitle ?: "Podcast",
                                            artist = mappedEpisode.podcastTitle ?: "Unknown",
                                            imageUrl = mappedEpisode.imageUrl ?: ""
                                        )
                                        coroutineScope.launch {
                                            playbackRepository.playQueue(
                                                episodes = listOf(mappedEpisode),
                                                podcast = podcast,
                                                startIndex = 0,
                                                entryPoint = cx.aswin.boxcast.core.model.PlaybackEntryPoint.LEARN
                                            )
                                        }
                                    }
                                },
                                onEpisodeClick = handleInfo,
                                onPodcastClick = { daily ->
                                    val mappedEpisode = mapToEpisode(daily.episode)
                                    viewModel.trackPodcastClicked()
                                    trackLearnCardAction("podcast", mappedEpisode)
                                    onPodcastClick(
                                        mappedEpisode.podcastId?.toLongOrNull(),
                                        null,
                                        "",
                                        mappedEpisode.podcastTitle ?: "Podcast"
                                    )
                                },
                                accentColor = animatedAccentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp)
                            )

                            // 4. Middle spacer between card stack and controls (Tightly bounded to 10.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // 5. Deck controls row (Dismiss, Info, Queue compact pill buttons on the screen bg below the card)
                            val activeCard = state.questionsStack.firstOrNull()
                            DeckControlsRow(
                                activeCard = activeCard,
                                onDismissClick = {
                                    activeCard?.let(handleDismiss)
                                },
                                onInfoClick = {
                                    activeCard?.let(handleInfo)
                                },
                                onQueueClick = {
                                    activeCard?.let(handleQueue)
                                }
                            )

                            // 6. Bottom flexible spacer (absorbs the other 50% of the dynamic vertical margin)
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    is LearnUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckControlsRow(
    activeCard: DailyCuriosityDto?,
    onDismissClick: () -> Unit,
    onInfoClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeCard == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dismiss Pill Button (Left)
        Box(
            modifier = Modifier
                .height(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .expressiveClickable(onClick = onDismissClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DISMISS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp,
                    color = Color(0xFFFF6B6B)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Info Pill Button (Center)
        Box(
            modifier = Modifier
                .height(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .expressiveClickable(onClick = onInfoClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "INFO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Queue Pill Button (Right)
        Box(
            modifier = Modifier
                .height(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .expressiveClickable(onClick = onQueueClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF69DB7C),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "QUEUE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp,
                    color = Color(0xFF69DB7C)
                )
            }
        }
    }
}

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
    
    // 1. Get the absolute dominant swatch from the image palette
    val dominantSwatch = palette.dominantSwatch
        ?: palette.vibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.lightMutedSwatch
        ?: palette.darkMutedSwatch
        
    val rgb = dominantSwatch?.rgb ?: 0xFF6200EE.toInt()
    
    // 2. Convert RGB to HSL to tweak vibrancy and lightness
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(rgb, hsl)
    
    // 3. Boost saturation to make the glow rich and colorful (minimum 40% saturation)
    hsl[1] = hsl[1].coerceIn(0.40f, 0.85f)
    
    // 4. Clamp lightness to keep the glow visually pleasant (between 25% and 55%)
    hsl[2] = hsl[2].coerceIn(0.25f, 0.55f)
    
    // 5. Convert back to RGB and then Compose Color
    val colorInt = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
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

private fun trackLearnCardAction(
    action: String,
    episode: Episode
) {
    val analytics = cx.aswin.boxcast.core.data.analytics.AnalyticsHelper
    val episodeId = episode.id
    val episodeTitle = episode.title
    val podcastId = episode.podcastId
    val podcastTitle = episode.podcastTitle

    when (action) {
        "dismiss" -> analytics.trackLearnCardDismissed(episodeId, episodeTitle, podcastId, podcastTitle)
        "queue" -> analytics.trackLearnCardQueued(episodeId, episodeTitle, podcastId, podcastTitle)
        "info" -> analytics.trackLearnCardInfoClicked(episodeId, episodeTitle, podcastId, podcastTitle)
        "play" -> analytics.trackLearnCardPlayClicked(episodeId, episodeTitle, podcastId, podcastTitle)
        "podcast" -> analytics.trackLearnCardPodcastClicked(podcastId, podcastTitle)
    }
}
