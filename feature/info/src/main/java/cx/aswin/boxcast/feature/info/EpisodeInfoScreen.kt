package cx.aswin.boxcast.feature.info

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.palette.graphics.Palette
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import cx.aswin.boxcast.core.data.ShareManager
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.ShareBottomSheet
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.feature.info.components.CompactEpisodeActionRail
import cx.aswin.boxcast.feature.info.components.EpisodeActionRail
import cx.aswin.boxcast.feature.info.components.EpisodeActionRailCallbacks
import cx.aswin.boxcast.feature.info.components.EpisodeActionRailState
import cx.aswin.boxcast.feature.info.components.EpisodeArtworkBackdrop
import cx.aswin.boxcast.feature.info.components.EpisodeInfoHero
import cx.aswin.boxcast.feature.info.components.EpisodeRecommendationSection

private const val HERO_ITEM_KEY = "episode_hero"
private const val ACTION_RAIL_ITEM_KEY = "episode_action_rail"

@Composable
fun EpisodeInfoScreen(
    episodeId: String,
    episodeTitle: String,
    episodeDescription: String,
    episodeImageUrl: String,
    episodeAudioUrl: String,
    episodeDuration: Int,
    podcastId: String,
    podcastTitle: String,
    viewModel: EpisodeInfoViewModel,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    onPodcastClick: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    @Suppress("UNUSED_PARAMETER") onPlay: () -> Unit,
    entryPointContext: Bundle? = null,
    showMarkPlayedTip: Boolean = false,
    onMarkPlayedTipDismissed: () -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val likedEpisodeIds by viewModel.likedEpisodeIds.collectAsState()
    val completedEpisodeIds by viewModel.completedEpisodeIds.collectAsState()
    val queuedEpisodeIds by viewModel.queuedEpisodeIds.collectAsState()
    val isDownloaded by viewModel.isDownloaded(episodeId).collectAsState(initial = false)
    val isDownloading by viewModel.isDownloading(episodeId).collectAsState(initial = false)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.trackScreenExit()
                Lifecycle.Event.ON_START -> viewModel.onScreenResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.trackScreenExit()
        }
    }

    LaunchedEffect(episodeId) {
        viewModel.loadEpisode(
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            episodeDescription = episodeDescription,
            episodeImageUrl = episodeImageUrl,
            episodeAudioUrl = episodeAudioUrl,
            episodeDuration = episodeDuration,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            entryPointContext = entryPointContext,
        )
    }

    when (val state = uiState) {
        EpisodeInfoUiState.Loading -> EpisodeInfoLoading(modifier)
        EpisodeInfoUiState.Error -> EpisodeInfoError(modifier)
        is EpisodeInfoUiState.Success -> EpisodeInfoSuccess(
            state = state,
            liked = state.episode.id in likedEpisodeIds,
            completed = state.episode.id in completedEpisodeIds,
            queued = state.episode.id in queuedEpisodeIds,
            downloaded = isDownloaded,
            downloading = isDownloading,
            viewModel = viewModel,
            entryPointContext = entryPointContext,
            onPodcastClick = onPodcastClick,
            onEpisodeClick = onEpisodeClick,
            showMarkPlayedTip = showMarkPlayedTip,
            onMarkPlayedTipDismissed = onMarkPlayedTipDismissed,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier,
        )
    }
}

@Composable
private fun EpisodeInfoSuccess(
    state: EpisodeInfoUiState.Success,
    liked: Boolean,
    completed: Boolean,
    queued: Boolean,
    downloaded: Boolean,
    downloading: Boolean,
    viewModel: EpisodeInfoViewModel,
    entryPointContext: Bundle?,
    onPodcastClick: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    showMarkPlayedTip: Boolean,
    onMarkPlayedTipDismissed: () -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var showShareSheet by remember { mutableStateOf(false) }
    var extractedColor by remember(state.episode.id) { mutableStateOf(Color.Unspecified) }
    val artworkUrl = state.episode.imageUrl?.ifBlank { state.episode.podcastImageUrl }
    val podcastArtworkUrl = state.episode.podcastImageUrl?.ifBlank { state.episode.imageUrl }
    val palettePainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(podcastArtworkUrl)
            .allowHardware(false)
            .build(),
    )

    LaunchedEffect(palettePainter.state) {
        val painterState = palettePainter.state
        if (painterState is AsyncImagePainter.State.Success) {
            val bitmap = (painterState.result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) extractedColor = extractArtworkColor(bitmap)
        }
    }

    val paletteColor = if (extractedColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        extractedColor
    }
    val accentColor by animateColorAsState(
        targetValue = lerp(paletteColor, MaterialTheme.colorScheme.primary, 0.14f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "episode_accent",
    )
    val collapseThresholdPx = with(density) { 320.dp.toPx() }
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                collapseThresholdPx
            }
        }
    }
    val collapseFraction = (scrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
    val stickyRailVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 2 &&
                listState.layoutInfo.visibleItemsInfo.none { it.key == ACTION_RAIL_ITEM_KEY }
        }
    }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val appBarHeight = statusBarHeight + 64.dp
    val progress = if (state.durationMs > 0L) {
        (state.resumePositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val actionState = EpisodeActionRailState(
        title = state.episode.title,
        imageUrl = artworkUrl,
        isPlaying = state.isPlaying,
        isPlaybackLoading = state.isPlaybackLoading,
        isResume = state.resumePositionMs > 0L,
        isLiked = liked,
        isDownloaded = downloaded,
        isDownloading = downloading,
        isQueued = queued,
        isCompleted = completed,
        progress = progress,
        remainingTimeText = formatRemainingTime(state.durationMs - state.resumePositionMs),
    )
    val callbacks = remember(viewModel, state.episode, entryPointContext) {
        EpisodeActionRailCallbacks(
            onMainActionClick = { viewModel.onMainActionClick(entryPointContext) },
            onLikeClick = { viewModel.onToggleLike(state.episode) },
            onDownloadClick = { viewModel.toggleDownload(state.episode) },
            onQueueClick = viewModel::toggleQueue,
            onMarkPlayedClick = viewModel::onToggleCompletion,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        EpisodeArtworkBackdrop(
            imageUrl = podcastArtworkUrl,
            scrollOffset = scrollOffset,
            collapseFraction = collapseFraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(appBarHeight + 240.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = appBarHeight + 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    bottomContentPadding +
                    160.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = HERO_ITEM_KEY) {
                EpisodeInfoHero(
                    episode = state.episode,
                    podcastTitle = state.podcastTitle,
                    accentColor = accentColor,
                    collapseFraction = collapseFraction,
                    onPodcastClick = {
                        viewModel.onPodcastLinkClicked()
                        onPodcastClick(state.podcastId)
                    },
                )
            }
            item(key = ACTION_RAIL_ITEM_KEY) {
                EpisodeActionRail(
                    state = actionState,
                    callbacks = callbacks,
                    accentColor = accentColor,
                    showMarkPlayedTip = showMarkPlayedTip,
                    onMarkPlayedTipDismissed = onMarkPlayedTipDismissed,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            state.crossPromotion?.let { crossPromotion ->
                item(key = "cross_promotion") {
                    CrossPromotionCard(
                        crossPromotion = crossPromotion,
                        onPodcastClick = onPodcastClick,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (state.episode.description.isNotBlank()) {
                item(key = "description") {
                    EpisodeDescriptionCard(
                        description = state.episode.description,
                        accentColor = accentColor,
                        location = state.location,
                        license = state.license,
                        persons = state.episode.persons,
                        onSeekTo = viewModel::seekToPosition,
                    )
                }
            }
            if (state.similarEpisodesLoading || state.similarEpisodes.isNotEmpty()) {
                item(key = "similar_episodes") {
                    EpisodeRecommendationSection(
                        title = "More Like This",
                        icon = Icons.Rounded.AutoAwesome,
                        episodes = state.similarEpisodes,
                        loading = state.similarEpisodesLoading,
                        accentColor = accentColor,
                        fallbackImageUrl = state.episode.podcastImageUrl,
                        onEpisodeClick = onEpisodeClick,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item(key = "related_episodes") {
                EpisodeRecommendationSection(
                    title = "More from ${state.podcastTitle}",
                    icon = Icons.Rounded.Subscriptions,
                    episodes = state.relatedEpisodes,
                    loading = state.relatedEpisodesLoading,
                    accentColor = accentColor,
                    fallbackImageUrl = state.episode.podcastImageUrl,
                    emptyMessage = "No other episodes available",
                    onHeaderClick = {
                        viewModel.onPodcastLinkClicked()
                        onPodcastClick(state.podcastId)
                    },
                    onEpisodeClick = { episode ->
                        viewModel.onRelatedEpisodeClicked()
                        onEpisodeClick(episode)
                    },
                    onScrollStarted = viewModel::onRelatedEpisodesScrolled,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        ExpressiveEpisodeTopBar(
            title = state.episode.title,
            collapseFraction = collapseFraction,
            onShare = { showShareSheet = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CompactEpisodeActionRail(
            state = actionState,
            callbacks = callbacks,
            accentColor = accentColor,
            visible = stickyRailVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = appBarHeight + 8.dp, start = 12.dp, end = 12.dp),
        )
    }

    if (showShareSheet) {
        ShareBottomSheet(
            id = state.episode.id,
            type = "episode",
            title = state.episode.title,
            subtitle = state.podcastTitle,
            imageUrl = artworkUrl,
            onDismissRequest = { showShareSheet = false },
            durationMs = state.episode.duration * 1_000L,
            currentPositionMs = state.resumePositionMs,
            showTimestampOption = false,
            onShare = { _, _, timestamp, target ->
                ShareManager.shareEpisode(
                    context = context,
                    episode = state.episode,
                    podcastTitle = state.podcastTitle,
                    timestampMs = timestamp,
                    target = target,
                )
            },
        )
    }
}

@Composable
private fun ExpressiveEpisodeTopBar(
    title: String,
    collapseFraction: Float,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainer.copy(
            alpha = (collapseFraction * 0.98f).coerceIn(0f, 0.98f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "episode_top_bar_color",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 8.dp),
    ) {
        AnimatedVisibility(
            visible = collapseFraction > 0.68f,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 64.dp),
            enter = fadeIn(ExpressiveMotion.SleekFadeSpec) + slideInVertically { it / 3 },
            exit = fadeOut(ExpressiveMotion.SleekFadeSpec) + slideOutVertically { it / 3 },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TopBarAction(
            onClick = onShare,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = "Share")
        }
    }
}

@Composable
private fun TopBarAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .expressiveClickable(shape = CircleShape, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun EpisodeInfoLoading(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BoxLoreLoader.Expressive(size = 84.dp)
            Text(
                text = "Tuning this episode…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EpisodeInfoError(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(
                text = "This episode could not be loaded",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(36.dp),
            )
        }
    }
}

private fun extractArtworkColor(bitmap: Bitmap): Color {
    val palette = Palette.from(bitmap).generate()
    return Color(
        palette.vibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: 0xFF6750A4.toInt(),
    )
}

private fun formatRemainingTime(remainingMs: Long): String? {
    val totalSeconds = remainingMs.coerceAtLeast(0L) / 1_000L
    if (totalSeconds <= 0L) return null
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    return if (hours > 0L) "${hours}h ${minutes}m left" else "${minutes}m left"
}
