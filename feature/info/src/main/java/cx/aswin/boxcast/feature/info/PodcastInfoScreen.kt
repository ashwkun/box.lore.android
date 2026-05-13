package cx.aswin.boxcast.feature.info

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.Html
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.graphics.vector.ImageVector
import cx.aswin.boxcast.core.designsystem.theme.contrastColor
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Category
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import cx.aswin.boxcast.core.designsystem.component.ExpressiveExtendedFab
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import cx.aswin.boxcast.core.designsystem.components.AnimatedShapesFallback
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.SubcomposeAsyncImage
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Scaffold
import androidx.compose.ui.focus.focusRequester

private fun stripHtml(html: String?): String {
    if (html.isNullOrEmpty()) return ""
    return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}

private fun extractDominantColor(bitmap: Bitmap): Color {
    val palette = Palette.from(bitmap).generate()
    val colorInt = palette.vibrantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: return Color.Transparent
    return Color(colorInt)
}

// Navbar height constant
private val NAVBAR_HEIGHT = 80.dp

// M3 Expressive Easing (Standard decelerate curve)
private val ExpressiveEasing = androidx.compose.animation.core.CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PodcastInfoScreen(
    podcastId: String,
    viewModel: PodcastInfoViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (Episode, String, Int?) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val queuedEpisodeIds by viewModel.queuedEpisodeIds.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Search State
    var isSearchActive by remember { mutableStateOf(false) }

    // Use theme primary color (no dynamic extraction)
    val accentColor = MaterialTheme.colorScheme.primary
    
    // Handle Back Press for Search
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.searchEpisodes("") // Optional: Clear search on close? Or keep it? Let's clear for now.
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

    LaunchedEffect(podcastId) {
        viewModel.loadPodcast(podcastId)
    }
    
    // Scroll state for floating title animation (like Episode Info)
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else 1000f // Fully collapsed
        }
    }
    
    // Scroll fraction: 0 (expanded) -> 1 (collapsed)
    val density = LocalDensity.current
    val morphThreshold = with(density) { 150.dp.toPx() }
    val scrollFraction = (scrollOffset / morphThreshold).coerceIn(0f, 1f)
    
    // Header dimensions
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedHeaderHeight = 64.dp + statusBarHeight
    
    // Header background: transparent → surfaceContainer
    // NOTE: Don't lerp from Color.Transparent - it has RGB=0,0,0 causing black flash
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val headerColor by animateColorAsState(
        targetValue = surfaceColor.copy(alpha = scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headerColor"
    )
    
    // Title animation - floating title like Episode Info
    val titleSizeStart = MaterialTheme.typography.headlineSmall.fontSize
    val titleSizeEnd = MaterialTheme.typography.titleMedium.fontSize
    val titleFontSize = androidx.compose.ui.unit.lerp(titleSizeStart, titleSizeEnd, scrollFraction)
    
    // Y position: starts below header (above hero), ends in header
    val bodyTitleYPx = with(density) { collapsedHeaderHeight.toPx() + 16.dp.toPx() }
    val headerTitleYPx = with(density) { (statusBarHeight + 18.dp).toPx() }
    val titleTranslationY by animateFloatAsState(
        targetValue = androidx.compose.ui.util.lerp(bodyTitleYPx, headerTitleYPx, scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.85f),
        label = "titleY"
    )
    
    // MaxLines - 3 when expanded, 1 when collapsed (change at 70% for late transition)
    val titleMaxLines = 1
    // Keep alpha at 0 until header collapses, then fade in
    val titleAlpha = if (scrollFraction > 0.8f) (scrollFraction - 0.8f) / 0.2f else 0f
    
    // Horizontal padding
    val titleHorizontalPadding by animateDpAsState(
        targetValue = androidx.compose.ui.unit.lerp(20.dp, 56.dp, scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "titlePadding"
    )

    // State for options sheet

    
    // Liked episodes state
    val likedEpisodeIds by viewModel.likedEpisodesState.collectAsState()
    val completedEpisodeIds by viewModel.completedEpisodesState.collectAsState()

    // Playback state
    val episodePlaybackState by viewModel.episodePlaybackState.collectAsState()
    


    // REWRITE: Structure using Box to allow Overlay
    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is PodcastInfoUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    BoxCastLoader.Expressive(size = 80.dp)
                }
            }
            
            is PodcastInfoUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Failed to load podcast", color = MaterialTheme.colorScheme.error)
                }
            }
            
            is PodcastInfoUiState.Success -> {
                // Blurred Background Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(collapsedHeaderHeight + 240.dp)
                        .alpha(1f - scrollFraction)
                ) {
                    OptimizedImage(
                        url = state.podcast.imageUrl,
                        proxyWidth = 200,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.5f)
                            .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient overlay to blend into the background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                }

                // Content
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                
                val displayEpisodes = state.searchResults ?: state.episodes
                val feedItems = remember(displayEpisodes) { groupEpisodes(displayEpisodes) }
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                    contentPadding = PaddingValues(
                        top = collapsedHeaderHeight + 16.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomContentPadding + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // HERO SECTION: Centered Layout
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1. Centered Large Image
                            Surface(
                                modifier = Modifier.size(180.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                shadowElevation = 8.dp
                            ) {
                                OptimizedImage(
                                    url = state.podcast.imageUrl,
                                    proxyWidth = 600, // 180dp * ~3x density
                                    contentDescription = state.podcast.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // 2. Title & Artist
                            Text(
                                text = state.podcast.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = state.podcast.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 3. Scrollable Metadata Chips Row — centered
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Medium Pill — only shown for non-"podcast" mediums
                                val medium = state.podcast.medium
                                if (!medium.isNullOrEmpty() && medium != "podcast") {
                                    item {
                                        val mediumIcon = when (medium.lowercase()) {
                                            "music" -> Icons.Rounded.MusicNote
                                            "video" -> Icons.Rounded.Videocam
                                            "film" -> Icons.Rounded.Movie
                                            "audiobook" -> Icons.Rounded.AutoStories
                                            "newsletter" -> Icons.Rounded.Email
                                            "blog" -> Icons.Rounded.Article
                                            else -> Icons.Rounded.Headphones
                                        }
                                        Surface(
                                            shape = ExpressiveShapes.Pill,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = mediumIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = medium.replaceFirstChar { c -> c.uppercaseChar() },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Genre Pill — uses genre-specific icons
                                if (state.podcast.genre.isNotEmpty()) {
                                    item {
                                        val genreLc = state.podcast.genre.lowercase()
                                        val genreIcon: ImageVector = when {
                                            genreLc.contains("music") -> Icons.Rounded.MusicNote
                                            genreLc.contains("comedy") -> Icons.Rounded.SentimentVerySatisfied
                                            genreLc.contains("sport") -> Icons.Rounded.EmojiEvents
                                            genreLc.contains("science") -> Icons.Rounded.Science
                                            genreLc.contains("tech") -> Icons.Rounded.Computer
                                            genreLc.contains("news") -> Icons.Rounded.Newspaper
                                            genreLc.contains("health") -> Icons.Rounded.MonitorHeart
                                            genreLc.contains("history") -> Icons.Rounded.AccountBalance
                                            genreLc.contains("arts") -> Icons.Rounded.Palette
                                            genreLc.contains("education") -> Icons.Rounded.School
                                            genreLc.contains("tv") || genreLc.contains("film") -> Icons.Rounded.Movie
                                            genreLc.contains("fiction") -> Icons.Rounded.AutoStories
                                            genreLc.contains("religion") || genreLc.contains("spiritual") -> Icons.Rounded.SelfImprovement
                                            genreLc.contains("family") || genreLc.contains("kids") -> Icons.Rounded.ChildCare
                                            genreLc.contains("leisure") -> Icons.Rounded.Weekend
                                            genreLc.contains("business") -> Icons.Rounded.Work
                                            genreLc.contains("government") -> Icons.Rounded.Gavel
                                            genreLc.contains("society") || genreLc.contains("culture") -> Icons.Rounded.Groups
                                            genreLc.contains("crime") -> Icons.Rounded.Fingerprint
                                            else -> Icons.Rounded.Category
                                        }
                                        Surface(
                                            shape = ExpressiveShapes.Pill,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = genreIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    text = state.podcast.genre,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Podcast 2.0: Funding
                                val hasFunding = state.podcast.fundingUrl != null
                                if (hasFunding) {
                                    item {
                                        Surface(
                                            shape = ExpressiveShapes.Pill,
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            modifier = Modifier.expressiveClickable {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(state.podcast.fundingUrl))
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Favorite,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                                Text(
                                                    text = state.podcast.fundingMessage ?: "Support",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(18.dp))
                            
                            // 4. Description (Expandable — 2 lines default)
                            val strippedDesc = stripHtml(state.podcast.description)
                            var isDescExpanded by remember { mutableStateOf(false) }
                            
                            if (strippedDesc.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text(
                                        text = strippedDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 20.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .expressiveClickable { isDescExpanded = !isDescExpanded }
                                            .padding(16.dp)
                                            .animateContentSize(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                    
                    // EPISODE TOOLBAR
                    item(key = "toolbar") {
                        EpisodeToolbar(
                            searchQuery = state.searchQuery,
                            onSearchChange = { viewModel.searchEpisodes(it) },
                            isSearching = state.isSearching,
                            currentSort = state.currentSort,
                            onSortToggle = { viewModel.toggleSort() },
                            isSubscribed = state.isSubscribed,
                            onSubscribeClick = { viewModel.toggleSubscription() },
                            accentColor = accentColor,
                            genre = state.podcast.genre,
                            onSearchFocused = { isSearchActive = true }
                        )
                    }
                    
                    // Episodes
                    itemsIndexed(feedItems, key = { _, item -> item.id }) { itemIndex, feedItem ->
                        when (feedItem) {
                            is FeedItem.NormalEpisode -> {
                                val index = feedItem.globalIndex
                                val episode = feedItem.episode
                                val playState = episodePlaybackState[episode.id]
                                val isDownloaded by viewModel.isDownloaded(episode.id).collectAsState(initial = false)
                                val isDownloading by viewModel.isDownloading(episode.id).collectAsState(initial = false)
                                val isCompleted = completedEpisodeIds.contains(episode.id)
                                
                                EpisodeListItem(
                                    episode = episode,
                                    isLiked = likedEpisodeIds.contains(episode.id),
                                    accentColor = accentColor,
                                    // Playback State
                                    isPlaying = playState?.isPlaying == true,
                                    isResume = playState?.isResume == true,
                                    progress = playState?.progress ?: 0f,
                                    timeLeft = playState?.timeLeft,
                                    // Download State
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading,
                                    isQueued = queuedEpisodeIds.contains(episode.id),
                                    isCompleted = isCompleted,
                                    onClick = { 
                                        viewModel.recordEpisodeClick(episode.id)
                                        onEpisodeClick(episode, "podcast_info_episodes_list", index) 
                                    },
                                    onPlayClick = { viewModel.onPlayClick(episode) },
                                    onToggleLike = { viewModel.onToggleLike(episode) },
                                    onQueueClick = { viewModel.toggleQueue(episode) },
                                    onDownloadClick = { viewModel.toggleDownload(episode) },
                                    onMarkPlayedClick = { viewModel.onToggleCompletion(episode) },
                                    showMarkPlayedButton = false, // Hide in list view
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            is FeedItem.SingleTrailer -> {
                                SingleTrailerCard(
                                    episode = feedItem.episode,
                                    globalIndex = feedItem.globalIndex,
                                    episodePlaybackState = episodePlaybackState,
                                    onEpisodeClick = { ep, globalIndex ->
                                        viewModel.recordEpisodeClick(ep.id)
                                        onEpisodeClick(ep, "podcast_info_episodes_list", globalIndex)
                                    },
                                    onPlayClick = { ep -> viewModel.onPlayClick(ep) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            is FeedItem.TrailerGroup -> {
                                TrailerStackCard(
                                    group = feedItem,
                                    episodePlaybackState = episodePlaybackState,
                                    onEpisodeClick = { ep, globalIndex ->
                                        viewModel.recordEpisodeClick(ep.id)
                                        onEpisodeClick(ep, "podcast_info_episodes_list", globalIndex)
                                    },
                                    onPlayClick = { ep -> viewModel.onPlayClick(ep) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        
                        if (state.searchResults == null && itemIndex == feedItems.lastIndex && state.hasMoreEpisodes && !state.isLoadingMore) {
                            LaunchedEffect(displayEpisodes.size) {
                                viewModel.loadMoreEpisodes()
                            }
                        }
                    }
                    
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxCastLoader.CircularWavy(size = 32.dp)
                            }
                        }
                    }
                    
                    if (state.searchResults?.isEmpty() == true) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No episodes found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // FIXED HEADER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(collapsedHeaderHeight)
                        .background(headerColor)
                        .statusBarsPadding()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // SNACKBAR HOST (Overlay)

                
                // FLOATING TITLE
                Text(
                    text = state.podcast.title,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = titleHorizontalPadding)
                        .graphicsLayer { 
                            translationY = titleTranslationY
                            alpha = titleAlpha 
                        }
                )

                // SEARCH OVERLAY (Nested inside Success)
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
                ) {
                    PodcastInfoSearchOverlay(
                        query = state.searchQuery,
                        onQueryChange = { viewModel.searchEpisodes(it) },
                        onClose = { 
                            isSearchActive = false
                            viewModel.searchEpisodes("") // Clear on exit
                        },
                        results = state.searchResults,
                        allEpisodes = state.episodes,
                        onEpisodeClick = { episode, index -> 
                            viewModel.recordEpisodeClick(episode.id)
                            onEpisodeClick(episode, "podcast_info_search_results", index) 
                        },
                        onPlayClick = { viewModel.onPlayClick(it) },
                        onToggleLike = { viewModel.onToggleLike(it) },
                        onQueueClick = { viewModel.toggleQueue(it) },
                        onDownloadClick = { viewModel.toggleDownload(it) },
                        onToggleCompletion = { viewModel.onToggleCompletion(it) },
                        likedEpisodeIds = likedEpisodeIds,
                        completedEpisodeIds = completedEpisodeIds,
                        queuedEpisodeIds = queuedEpisodeIds,
                        episodePlaybackState = episodePlaybackState,
                        isSearching = state.isSearching,
                        accentColor = accentColor,
                        isDownloadedFlow = viewModel::isDownloaded,
                        isDownloadingFlow = viewModel::isDownloading
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeListItem(
    episode: Episode,
    isLiked: Boolean,
    accentColor: Color,
    // Playback State
    isPlaying: Boolean,
    isResume: Boolean,
    progress: Float,
    timeLeft: String?,
    // Download State
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isQueued: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onToggleLike: () -> Unit,
    onQueueClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onMarkPlayedClick: () -> Unit,
    showMarkPlayedButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp) // Generous padding inside the card
        ) {
            // 1. Content Row (Image + Text)
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Artwork with completion checkmark
                Box(modifier = Modifier.size(76.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        OptimizedImage(
                            url = episode.imageUrl,
                            proxyWidth = 200, // 76dp thumbnails
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (isCompleted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Text Content
                Column(modifier = Modifier.weight(1f)) {
                    // Metadata
                    fun formatDuration(seconds: Int): String {
                        val hours = seconds / 3600
                        val minutes = (seconds % 3600) / 60
                        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                    }
                    
                    fun formatRelativeDate(timestampSeconds: Long): String {
                        if (timestampSeconds == 0L) return ""
                        val now = System.currentTimeMillis() / 1000
                        val diff = now - timestampSeconds
                        return when {
                            diff < 3600 -> "${diff / 60}m ago"
                            diff < 86400 -> "${diff / 3600}h ago"
                            diff < 604800 -> "${diff / 86400}d ago"
                            diff < 2592000 -> "${diff / 604800}w ago"
                            diff < 31536000 -> "${diff / 2592000}mo ago"
                            else -> "${diff / 31536000}y ago"
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Podcast 2.0: Season/Episode number
                        val seLabel = buildString {
                            episode.seasonNumber?.let { append("S$it ") }
                            episode.episodeNumber?.let { append("E$it") }
                        }.trim()
                        if (seLabel.isNotEmpty()) {
                            Text(
                                text = seLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = formatRelativeDate(episode.publishedDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatDuration(episode.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Podcast 2.0: Episode type badge
                        if (episode.episodeType != null && episode.episodeType != "full") {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Surface(
                                shape = ExpressiveShapes.Pill,
                                color = if (episode.episodeType == "trailer") 
                                    MaterialTheme.colorScheme.tertiaryContainer 
                                else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = episode.episodeType!!.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (episode.episodeType == "trailer") 
                                        MaterialTheme.colorScheme.onTertiaryContainer 
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    // Title
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                    
                    // Description Preview
                    val stripped = stripHtml(episode.description)
                    if (stripped.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stripped,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // 2. Control Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween, // Push play button to edge
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                // Secondary Controls (Tonal squircle for premium feel on card)
                cx.aswin.boxcast.core.designsystem.components.AdvancedPlayerControls(
                    isLiked = isLiked,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    colorScheme = MaterialTheme.colorScheme,
                    onLikeClick = onToggleLike,
                    onDownloadClick = onDownloadClick,
                    onQueueClick = onQueueClick,
                    style = cx.aswin.boxcast.core.designsystem.components.ControlStyle.TonalSquircle,
                    overrideColor = accentColor,
                    horizontalArrangement = Arrangement.spacedBy(8.dp), 
                    showAddQueueIcon = true,
                    isQueued = isQueued,
                    showShareButton = false,
                    isPlayed = isCompleted,
                    showMarkPlayedButton = showMarkPlayedButton,
                    onMarkPlayedClick = onMarkPlayedClick,
                    controlSize = 40.dp
                )

                // Play Button
                cx.aswin.boxcast.core.designsystem.components.ExpressivePlayButton(
                    onClick = onPlayClick,
                    isPlaying = isPlaying, 
                    isResume = isResume,
                    accentColor = accentColor,
                    progress = progress,
                    timeText = timeLeft,
                    modifier = Modifier
                        .height(44.dp)
                        .padding(start = 16.dp)
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * Episode Toolbar - M3 Expressive
 * Contains: Search, Sort Toggle, Subscribe Button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isSearching: Boolean,
    currentSort: EpisodeSort,
    onSortToggle: () -> Unit,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    accentColor: Color,
    genre: String = "",
    onSearchFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // --- Genre-themed celebration icon ---
    val genreLower = genre.lowercase()
    val celebrationIcon: ImageVector = when {
        genreLower.contains("music") -> Icons.Rounded.MusicNote
        genreLower.contains("comedy") -> Icons.Rounded.SentimentVerySatisfied
        genreLower.contains("sport") -> Icons.Rounded.EmojiEvents
        genreLower.contains("science") -> Icons.Rounded.Science
        genreLower.contains("tech") -> Icons.Rounded.Computer
        genreLower.contains("news") -> Icons.Rounded.Newspaper
        genreLower.contains("health") -> Icons.Rounded.MonitorHeart
        genreLower.contains("history") -> Icons.Rounded.AccountBalance
        genreLower.contains("arts") -> Icons.Rounded.Palette
        genreLower.contains("education") -> Icons.Rounded.School
        genreLower.contains("tv") || genreLower.contains("film") -> Icons.Rounded.Movie
        genreLower.contains("fiction") -> Icons.Rounded.AutoStories
        genreLower.contains("religion") || genreLower.contains("spiritual") -> Icons.Rounded.SelfImprovement
        genreLower.contains("family") || genreLower.contains("kids") -> Icons.Rounded.ChildCare
        genreLower.contains("leisure") -> Icons.Rounded.Weekend
        genreLower.contains("business") -> Icons.Rounded.Work
        genreLower.contains("government") -> Icons.Rounded.Gavel
        genreLower.contains("society") || genreLower.contains("culture") -> Icons.Rounded.Groups
        genreLower.contains("crime") -> Icons.Rounded.Fingerprint
        else -> Icons.Rounded.Favorite // Fallback: heart
    }

    // --- 3-state machine: IDLE → CELEBRATING → DONE ---
    // 0 = normal, 1 = celebrating (genre icon), 2 = done (subscribed)
    var celebrationPhase by remember { mutableIntStateOf(if (isSubscribed) 2 else 0) }
    var prevSubscribed by remember { mutableStateOf(isSubscribed) }

    // Detect subscribe transition
    LaunchedEffect(isSubscribed) {
        if (isSubscribed && !prevSubscribed) {
            // Just subscribed → celebration
            celebrationPhase = 1
            kotlinx.coroutines.delay(900L) // Hold the genre icon
            celebrationPhase = 2
        } else if (!isSubscribed) {
            celebrationPhase = 0
        }
        prevSubscribed = isSubscribed
    }

    // Celebration icon animation
    val celebScale = remember { Animatable(0f) }
    val celebRotation = remember { Animatable(0f) }

    LaunchedEffect(celebrationPhase) {
        if (celebrationPhase == 1) {
            // Reset
            celebScale.snapTo(0f)
            celebRotation.snapTo(-30f)
            // Scale in with overshoot (parallel with rotation)
            launch {
                celebScale.animateTo(
                    1.3f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
                celebScale.animateTo(
                    1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                )
            }
            // Rotate in
            celebRotation.animateTo(
                0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Subscribe Button
        val subInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isSubPressed by subInteractionSource.collectIsPressedAsState()
        val subScale by animateFloatAsState(
            targetValue = if (isSubPressed) 0.9f else 1f,
            animationSpec = if (isSubPressed) cx.aswin.boxcast.core.designsystem.theme.ExpressiveMotion.QuickSpring else cx.aswin.boxcast.core.designsystem.theme.ExpressiveMotion.BouncySpring,
            label = "subScale"
        )

        // Animate container color smoothly
        val containerColor by animateColorAsState(
            targetValue = when (celebrationPhase) {
                1 -> accentColor // Keep accent during celebration
                2 -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> accentColor
            },
            animationSpec = tween(400),
            label = "containerColor"
        )
        // Pick text color based on container luminance for guaranteed contrast
        val onAccent = accentColor.contrastColor()
        val contentColor by animateColorAsState(
            targetValue = when (celebrationPhase) {
                1 -> onAccent
                2 -> MaterialTheme.colorScheme.onSurface
                else -> onAccent
            },
            animationSpec = tween(400),
            label = "contentColor"
        )

        FilledTonalButton(
            onClick = onSubscribeClick,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            shape = ExpressiveShapes.Pill,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            interactionSource = subInteractionSource,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .graphicsLayer {
                    scaleX = subScale
                    scaleY = subScale
                }
        ) {
            // Content: AnimatedContent for the 3 phases
            AnimatedContent(
                targetState = celebrationPhase,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f))
                        .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f))
                },
                label = "subContent"
            ) { phase ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (phase) {
                        0 -> {
                            // Normal "Subscribe" state
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subscribe",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        1 -> {
                            // Celebration: genre icon with bounce + rotate
                            Icon(
                                imageVector = celebrationIcon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .graphicsLayer {
                                        scaleX = celebScale.value
                                        scaleY = celebScale.value
                                        rotationZ = celebRotation.value
                                    }
                            )
                        }
                        else -> {
                            // Normal "Subscribed" state
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subscribed",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Sort Button
        IconButton(
            onClick = onSortToggle,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, ExpressiveShapes.Pill)
        ) {
            Icon(
                imageVector = if (currentSort == EpisodeSort.NEWEST) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Search Button
        IconButton(
            onClick = onSearchFocused,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, ExpressiveShapes.Pill)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastInfoSearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    results: List<Episode>?,
    allEpisodes: List<Episode>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    onToggleLike: (Episode) -> Unit,
    onQueueClick: (Episode) -> Unit,
    onDownloadClick: (Episode) -> Unit,
    onToggleCompletion: (Episode) -> Unit,
    likedEpisodeIds: Set<String>,
    completedEpisodeIds: Set<String>,
    queuedEpisodeIds: Set<String>,
    episodePlaybackState: Map<String, cx.aswin.boxcast.feature.info.PodcastInfoViewModel.EpisodePlaybackState>,
    isSearching: Boolean,
    accentColor: Color,
    isDownloadedFlow: (String) -> kotlinx.coroutines.flow.Flow<Boolean>,
    isDownloadingFlow: (String) -> kotlinx.coroutines.flow.Flow<Boolean>
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    
    LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                // Unified "M3 Style" Search Bar Component
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(56.dp), // Standard M3 Search Height
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = androidx.compose.foundation.shape.CircleShape // Full Pill
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Leading Icon (Back) acts as Navigation
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Input Field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                             if (query.isEmpty()) {
                                Text(
                                    "Search episodes...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            
                            androidx.compose.foundation.text.BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                        
                        // Trailing Icon (Clear)
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        val safeResults = results ?: emptyList() 
        val displayList = if (query.isEmpty()) emptyList() else safeResults

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    cx.aswin.boxcast.core.designsystem.components.BoxCastLoader.Expressive(
                        size = 64.dp
                    )
                }
            } else if (query.isNotEmpty() && displayList.isEmpty()) {
                 Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No episodes found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (displayList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(displayList, key = { _, ep -> ep.id }) { index, episode ->
                        val playState = episodePlaybackState[episode.id]
                        val isDownloaded by isDownloadedFlow(episode.id).collectAsState(initial = false)
                        val isDownloading by isDownloadingFlow(episode.id).collectAsState(initial = false)
                        val isCompleted = completedEpisodeIds.contains(episode.id)
                        
                        EpisodeListItem(
                            episode = episode,
                            isLiked = likedEpisodeIds.contains(episode.id),
                            accentColor = accentColor,
                            isPlaying = playState?.isPlaying == true,
                            isResume = playState?.isResume == true,
                            progress = playState?.progress ?: 0f,
                            timeLeft = playState?.timeLeft,

                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isQueued = queuedEpisodeIds.contains(episode.id),
                            isCompleted = isCompleted,
                            onClick = { 
                                onEpisodeClick(episode, index)
                                onClose() // Close search on nav
                            },
                            onPlayClick = { onPlayClick(episode) },
                            onToggleLike = { onToggleLike(episode) },
                            onQueueClick = { onQueueClick(episode) },
                            onDownloadClick = { onDownloadClick(episode) },
                            onMarkPlayedClick = { onToggleCompletion(episode) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

sealed class FeedItem {
    abstract val id: String
    
    data class NormalEpisode(val episode: Episode, val globalIndex: Int) : FeedItem() {
        override val id: String = episode.id
    }
    
    data class SingleTrailer(val episode: Episode, val globalIndex: Int) : FeedItem() {
        override val id: String = episode.id
    }
    
    data class TrailerGroup(val trailers: List<Pair<Episode, Int>>) : FeedItem() {
        override val id: String = "trailer_group_${trailers.firstOrNull()?.first?.id ?: hashCode()}"
    }
}

fun groupEpisodes(episodes: List<Episode>): List<FeedItem> {
    val result = mutableListOf<FeedItem>()
    val currentTrailers = mutableListOf<Pair<Episode, Int>>()
    
    episodes.forEachIndexed { index, episode ->
        if (episode.episodeType == "trailer") {
            currentTrailers.add(episode to index)
        } else {
            if (currentTrailers.isNotEmpty()) {
                if (currentTrailers.size == 1) {
                    val (tEp, tIdx) = currentTrailers.first()
                    result.add(FeedItem.SingleTrailer(tEp, tIdx))
                } else {
                    result.add(FeedItem.TrailerGroup(currentTrailers.toList()))
                }
                currentTrailers.clear()
            }
            result.add(FeedItem.NormalEpisode(episode, index))
        }
    }
    
    if (currentTrailers.isNotEmpty()) {
        if (currentTrailers.size == 1) {
            val (tEp, tIdx) = currentTrailers.first()
            result.add(FeedItem.SingleTrailer(tEp, tIdx))
        } else {
            result.add(FeedItem.TrailerGroup(currentTrailers.toList()))
        }
    }
    
    return result
}

@Composable
fun SingleTrailerCard(
    episode: Episode,
    globalIndex: Int,
    episodePlaybackState: Map<String, PodcastInfoViewModel.EpisodePlaybackState>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    val playState = episodePlaybackState[episode.id]
    val isPlaying = playState?.isPlaying == true
    val isResume = playState?.isResume == true

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEpisodeClick(episode, globalIndex) },
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Button
            Surface(
                shape = CircleShape,
                color = if (isPlaying || isResume) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onPlayClick(episode) }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isPlaying || isResume) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val durationText = if (episode.duration > 0) {
                    val h = episode.duration / 3600
                    val m = (episode.duration % 3600) / 60
                    if (h > 0) "${h}hr ${m}min" else "${m}min"
                } else "Trailer"
                
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TrailerStackCard(
    group: FeedItem.TrailerGroup,
    episodePlaybackState: Map<String, PodcastInfoViewModel.EpisodePlaybackState>,
    onEpisodeClick: (Episode, Int) -> Unit,
    onPlayClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            // Header Row (Always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Promotional Trailers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${group.trailers.size} trailers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Expanded Content (Mini-Trailers)
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                ) {
                    group.trailers.forEachIndexed { index, (episode, globalIndex) ->
                        val playState = episodePlaybackState[episode.id]
                        val isPlaying = playState?.isPlaying == true
                        val isResume = playState?.isResume == true
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEpisodeClick(episode, globalIndex) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play Button
                            Surface(
                                shape = CircleShape,
                                color = if (isPlaying || isResume) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { onPlayClick(episode) }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = if (isPlaying || isResume) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Text
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = episode.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val durationText = if (episode.duration > 0) {
                                    val h = episode.duration / 3600
                                    val m = (episode.duration % 3600) / 60
                                    if (h > 0) "${h}hr ${m}min" else "${m}min"
                                } else "Trailer"
                                
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        if (index < group.trailers.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(start = 64.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
