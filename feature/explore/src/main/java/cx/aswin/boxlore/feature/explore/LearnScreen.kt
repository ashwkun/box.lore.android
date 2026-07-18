package cx.aswin.boxlore.feature.explore

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.optimizedImageUrl
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    viewModel: LearnViewModel,
    playbackRepository: cx.aswin.boxlore.core.data.PlaybackRepository,
    bottomContentPadding: Dp,
    onEpisodeClick: (Episode) -> Unit,
    onQueueEpisode: (Episode) -> Unit,
    onPodcastClick: (feedId: Long?, itunesId: Long?, feedUrl: String, title: String) -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val stablePlayerState = remember(playbackRepository) {
        playbackRepository.playerState
            .map { it.copy(position = 0L, bufferedPosition = 0L) }
            .distinctUntilChanged()
    }
    val playerState by stablePlayerState.collectAsState(
        initial = cx.aswin.boxlore.core.data.PlayerState()
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackLearnScreenViewed()
    }

    TrackScreenSession(
        onSessionResume = viewModel::onScreenResume,
        onSessionExit = viewModel::trackScreenExit
    )

    // Extract dominant color state at screen level
    var extractedColor by remember { mutableStateOf<Color?>(null) }
    val baseAccentColor = extractedColor ?: MaterialTheme.colorScheme.primary

    // Animate color transition smoothly for ambient background gradient and tinting
    val animatedAccentColor by animateColorAsState(
        targetValue = baseAccentColor,
        animationSpec = tween(durationMillis = 700),
        label = "AccentColorTransition"
    )

    // Trigger color extraction based on active card cover artwork changes
    val activeCardImage = (uiState as? LearnUiState.Success)
        ?.questionsStack
        ?.firstOrNull()
        ?.let { it.imageUrl ?: it.feedImage }

    LaunchedEffect(activeCardImage) {
        if (activeCardImage.isNullOrEmpty()) {
            extractedColor = null
            return@LaunchedEffect
        }
        try {
            val loader = coil.Coil.imageLoader(context)
            val optimizedUrl = activeCardImage.optimizedImageUrl(width = 200)
            val request = ImageRequest.Builder(context)
                .data(optimizedUrl)
                .allowHardware(false) // Must be a software bitmap for Palette pixel extraction
                .size(100, 100) // Constrain decode size in memory for palette extraction
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
                } else {
                    extractedColor = null
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
        LoreHaloBackground(
            accentColor = animatedAccentColor,
            modifier = Modifier.fillMaxSize()
        ) {
            val isRefreshing = when (val state = uiState) {
                is LearnUiState.Success -> state.isRefreshing
                is LearnUiState.CaughtUp -> state.isRefreshing
                else -> false
            }
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomContentPaddingCalculated),
                            contentAlignment = Alignment.Center
                        ) {
                            BoxLoreLoader.Expressive(size = 64.dp)
                        }
                    }
                    is LearnUiState.CaughtUp -> {
                        LoreStateCard(
                            accentColor = animatedAccentColor,
                            title = "You’re all caught up",
                            description = "New curiosities arrive daily. Restore a favorite from your Lore history whenever inspiration strikes.",
                            bottomContentPadding = bottomContentPaddingCalculated
                        ) {
                            FilledTonalButton(
                                onClick = viewModel::refresh,
                                shape = CircleShape
                            ) {
                                Text("Check for new cards")
                            }
                            TextButton(onClick = onNavigateToHistory) {
                                Text("Open Lore history")
                            }
                        }
                    }
                    is LearnUiState.Success -> {
                        val visibleCard = state.questionsStack.firstOrNull()
                        LaunchedEffect(visibleCard?.episodeId) {
                            visibleCard?.let(viewModel::trackCardVisible)
                        }
                        val handleLearnCardAction: (String, LearnCuriosityCard) -> Unit = { action, card ->
                            val mappedEpisode = card.toEpisode()
                            when (action) {
                                "dismiss" -> {
                                    viewModel.trackCardDismissed(card)
                                    trackLearnCardAction("dismiss", mappedEpisode)
                                    viewModel.dismissCuriosity(card, LearnHistoryAction.DISMISS)
                                }
                                "queue" -> {
                                    viewModel.trackCardQueued(card)
                                    trackLearnCardAction("queue", mappedEpisode)
                                    onQueueEpisode(mappedEpisode)
                                    viewModel.dismissCuriosity(card, LearnHistoryAction.QUEUE)
                                }
                                "info" -> {
                                    viewModel.trackInfoClicked(card)
                                    trackLearnCardAction("info", mappedEpisode)
                                    onEpisodeClick(mappedEpisode)
                                }
                                "play" -> {
                                    viewModel.trackPlayClicked(card)
                                    trackLearnCardAction("play", mappedEpisode)
                                    val isCurrent = playerState.currentEpisode?.id == mappedEpisode.id
                                    if (isCurrent) {
                                        playbackRepository.togglePlayPause()
                                    } else {
                                        val podcast = cx.aswin.boxlore.core.model.Podcast(
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
                                                entryPoint = cx.aswin.boxlore.core.model.PlaybackEntryPoint.LEARN
                                            )
                                        }
                                    }
                                }
                                "podcast" -> {
                                    viewModel.trackPodcastClicked(card)
                                    trackLearnCardAction("podcast", mappedEpisode)
                                    onPodcastClick(
                                        mappedEpisode.podcastId?.toLongOrNull(),
                                        null,
                                        "",
                                        mappedEpisode.podcastTitle ?: "Podcast"
                                    )
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
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = cx.aswin.boxlore.core.designsystem.R.drawable.logo_lore),
                                    contentDescription = "Lore",
                                    colorFilter = ColorFilter.tint(animatedAccentColor),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .align(Alignment.Center)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    IconButton(onClick = onNavigateToHistory) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = "Lore history",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(30.dp))
                            CuriosityCardStack(
                                questions = state.questionsStack,
                                isCurrentEpisode = { id -> playerState.currentEpisode?.id == id },
                                isCurrentlyPlaying = { id -> playerState.currentEpisode?.id == id && playerState.isPlaying },
                                isCurrentlyLoading = { id -> playerState.currentEpisode?.id == id && playerState.isLoading },
                                onSwipeLeft = { handleLearnCardAction("dismiss", it) },
                                onSwipeRight = { handleLearnCardAction("queue", it) },
                                onPlayClick = { handleLearnCardAction("play", it) },
                                onEpisodeClick = { handleLearnCardAction("info", it) },
                                onPodcastClick = { handleLearnCardAction("podcast", it) },
                                accentColor = animatedAccentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 20.dp)
                            )
                        }
                    }
                    is LearnUiState.Error -> {
                        LoreStateCard(
                            accentColor = animatedAccentColor,
                            title = "Lore lost the thread",
                            description = state.message,
                            bottomContentPadding = bottomContentPaddingCalculated
                        ) {
                            FilledTonalButton(
                                onClick = viewModel::refresh,
                                shape = CircleShape
                            ) {
                                Text("Try again")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoreStateCard(
    accentColor: Color,
    title: String,
    description: String,
    bottomContentPadding: Dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = bottomContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = cx.aswin.boxlore.core.designsystem.R.drawable.logo_lore),
            contentDescription = "Lore",
            colorFilter = ColorFilter.tint(accentColor),
            modifier = Modifier.height(38.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }
    }
}

internal fun extractDominantColor(bitmap: android.graphics.Bitmap): Color {
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

private fun trackLearnCardAction(
    action: String,
    episode: Episode
) {
    val analytics = cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
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
