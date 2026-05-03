package cx.aswin.boxcast.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn as animFadeIn
import androidx.compose.animation.fadeOut as animFadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.Alignment
import cx.aswin.boxcast.feature.home.components.GridSkeletonItems
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size

import cx.aswin.boxcast.feature.home.components.HeroCarousel
import cx.aswin.boxcast.feature.home.components.PodcastCard
import cx.aswin.boxcast.feature.home.components.TimeBlockSection
import cx.aswin.boxcast.feature.home.CuratedTimeBlock
import cx.aswin.boxcast.feature.home.components.TopControlBar
import cx.aswin.boxcast.feature.home.components.YourShowsSection

import cx.aswin.boxcast.feature.home.components.DebugDbInspectorDialog
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.data.database.PodcastEntity

@Composable
fun HomeRoute(
    apiBaseUrl: String,
    publicKey: String,
    playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository,
    onPodcastClick: (Podcast) -> Unit,
    onHeroArrowClick: (SmartHeroItem) -> Unit,
    onEpisodeClick: ((Episode, Podcast) -> Unit)? = null, // Navigate to EpisodeInfo
    onPlayClick: ((Podcast) -> Unit)? = null, // Navigate directly to Player (Resume)
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToLatestEpisodes: (() -> Unit)? = null,
    onNavigateToExplore: ((String?) -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToPlayStoreReview: () -> Unit = {},
    onSubmitFeedback: suspend (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel: HomeViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(application, apiBaseUrl, publicKey, playbackRepository) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val debugHistory by viewModel.debugHistory.collectAsState(initial = emptyList())
    val debugPodcasts by viewModel.debugPodcasts.collectAsState(initial = emptyList())

    val showReviewPrompt by viewModel.showReviewPrompt.collectAsState()
    val showPostReview by viewModel.showPostReview.collectAsState()
    val showFeedback by viewModel.showFeedback.collectAsState()
    

    
    HomeScreen(
        uiState = uiState,
        currentPlayingPodcastId = playerState.currentPodcast?.id,
        isPlaying = playerState.isPlaying,
        debugHistory = debugHistory,
        debugPodcasts = debugPodcasts,
        onPodcastClick = onPodcastClick,
        onHeroArrowClick = onHeroArrowClick,
        onEpisodeClick = onEpisodeClick,
        onPlayClick = onPlayClick,
        onNavigateToLibrary = onNavigateToLibrary,
        onNavigateToLatestEpisodes = onNavigateToLatestEpisodes,
        onNavigateToExplore = onNavigateToExplore,
        onToggleSubscription = viewModel::toggleSubscription,
        onTogglePlayback = viewModel::togglePlayback,
        onSelectCategory = viewModel::selectCategory,
        onDeleteHistoryItem = viewModel::deleteHistoryItem,
        onNavigateToSettings = onNavigateToSettings,
        onFeedbackClick = viewModel::triggerFeedback,
        onForceReviewPrompt = viewModel::forceReviewPrompt,
        showReviewPrompt = showReviewPrompt,
        showPostReview = showPostReview,
        showFeedback = showFeedback,
        onDismissReviewPrompt = {
            viewModel.markReviewPromptShown()
            viewModel.dismissReviewPrompt()
        },
        onDismissPostReview = viewModel::dismissPostReview,
        onDismissFeedback = viewModel::dismissFeedback,
        onNavigateToPlayStoreReview = {
            viewModel.markReviewed()
            viewModel.triggerPostReview()
            onNavigateToPlayStoreReview()
        },
        onSubmitFeedback = onSubmitFeedback,
        onResetFeatureFlag = viewModel::resetFeatureFlag,

        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    currentPlayingPodcastId: String?,
    isPlaying: Boolean,
    debugHistory: List<ListeningHistoryEntity>,
    debugPodcasts: List<PodcastEntity>,
    onPodcastClick: (Podcast) -> Unit,
    onHeroArrowClick: (SmartHeroItem) -> Unit,
    onEpisodeClick: ((Episode, Podcast) -> Unit)?,
    onPlayClick: ((Podcast) -> Unit)?,
    onNavigateToLibrary: (() -> Unit)?,
    onNavigateToLatestEpisodes: (() -> Unit)?,
    onNavigateToExplore: ((String?) -> Unit)?,
    onToggleSubscription: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onSelectCategory: (String?) -> Unit,

    onDeleteHistoryItem: (String) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onFeedbackClick: () -> Unit,
    onForceReviewPrompt: () -> Unit = {},
    showReviewPrompt: Boolean = false,
    showPostReview: Boolean = false,
    showFeedback: Boolean = false,
    onDismissReviewPrompt: () -> Unit = {},
    onDismissPostReview: () -> Unit = {},
    onDismissFeedback: () -> Unit = {},
    onNavigateToPlayStoreReview: () -> Unit = {},
    onSubmitFeedback: suspend (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    onResetFeatureFlag: () -> Unit = {},

    modifier: Modifier = Modifier
) {
    // Track scroll state for collapsing top bar
    val gridState = rememberLazyStaggeredGridState()
    var showDebugDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    
    // Calculate scroll fraction: 0 = at top (expanded), 1 = scrolled (collapsed)
    val scrollFraction by remember {
        derivedStateOf {
            val firstVisibleItem = gridState.firstVisibleItemIndex
            val firstVisibleOffset = gridState.firstVisibleItemScrollOffset
            val collapseThreshold = 100f
            if (firstVisibleItem == 0) {
                (firstVisibleOffset / collapseThreshold).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }
    
    if (showDebugDialog) {
        DebugDbInspectorDialog(
            history = debugHistory,
            podcasts = debugPodcasts,
            onDeleteHistoryItem = onDeleteHistoryItem,
            onResetFeatureFlag = onResetFeatureFlag,
            onDismissRequest = { showDebugDialog = false }
        )
    }


    Box(modifier = modifier.fillMaxSize()) {
        // Main content underneath
        Column(modifier = Modifier.fillMaxSize()) {
            TopControlBar(
                scrollFraction = scrollFraction,

                onFeedbackClick = onFeedbackClick,
                onFeedbackLongClick = onForceReviewPrompt,
                onAvatarClick = { onNavigateToSettings?.invoke() },
                onAvatarLongClick = { showDebugDialog = true }
            )
            
            // Content area
            Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.isError) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Error loading content", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        PodcastFeed(
                            heroItems = uiState.heroItems,
                            latestItems = uiState.latestEpisodes,
                            unplayedEpisodeCount = uiState.unplayedEpisodeCount,
                            subscribedItems = uiState.subscribedPodcasts,
                            timeBlock = uiState.timeBlock,
                            gridItems = uiState.discoverPodcasts,
                            selectedCategory = uiState.selectedCategory,
                            currentPlayingPodcastId = currentPlayingPodcastId,
                            isPlaying = isPlaying,
                            isFilterLoading = uiState.isFilterLoading,
                            onPodcastClick = onPodcastClick,
                            onHeroArrowClick = onHeroArrowClick,
                            onEpisodeClick = onEpisodeClick,
                            onPlayClick = onPlayClick,
                            onNavigateToLibrary = onNavigateToLibrary,
                            onNavigateToLatestEpisodes = onNavigateToLatestEpisodes,
                            onNavigateToExplore = onNavigateToExplore,
                            onToggleSubscription = onToggleSubscription,
                            onTogglePlayback = onTogglePlayback,
                            onSelectCategory = onSelectCategory,
                            gridState = gridState
                        )
                    }
        }
    }
    }
    // --- Bottom Sheets outside the scrollable area ---
    if (showReviewPrompt) {
        cx.aswin.boxcast.feature.home.components.ReviewPromptSheet(
            completedCount = uiState.completedEpisodeCount,
            onDismissRequest = onDismissReviewPrompt,
            onNavigateToReview = onNavigateToPlayStoreReview,
            onNavigateToFeedback = {
                onDismissReviewPrompt()
                onFeedbackClick()
            }
        )
    }

    if (showPostReview) {
        cx.aswin.boxcast.feature.home.components.PostReviewSheet(
            onDismissRequest = onDismissPostReview,
            onNavigateToFeedback = {
                onDismissPostReview()
                onFeedbackClick()
            }
        )
    }

    if (showFeedback) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val versionStr = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        
        cx.aswin.boxcast.feature.home.components.FeedbackSheet(
            appVersion = versionStr,
            onSubmit = onSubmitFeedback,
            onRateInstead = {
                onDismissFeedback()
                val pkgName = context.packageName
                try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkgName")))
                } catch (e: Exception) {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkgName")))
                }
            },
            onDismissRequest = onDismissFeedback
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastFeed(
    heroItems: List<SmartHeroItem>,
    latestItems: List<Podcast>,
    unplayedEpisodeCount: Int,
    subscribedItems: List<Podcast>,
    timeBlock: CuratedTimeBlock?,
    gridItems: List<Podcast>,
    selectedCategory: String?,
    currentPlayingPodcastId: String?,
    isPlaying: Boolean,
    isFilterLoading: Boolean,
    onPodcastClick: (Podcast) -> Unit,
    onHeroArrowClick: (SmartHeroItem) -> Unit,
    onEpisodeClick: ((Episode, Podcast) -> Unit)?,
    onPlayClick: ((Podcast) -> Unit)?,
    onNavigateToLibrary: (() -> Unit)?,
    onNavigateToLatestEpisodes: (() -> Unit)?,
    onNavigateToExplore: ((String?) -> Unit)?,
    onToggleSubscription: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(bottom = 160.dp, start = 16.dp, end = 16.dp), // Clear navbar + mini player 
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 16.dp,
        modifier = modifier.fillMaxSize()
    ) {

        // 1. Smart Hero (Personalized Content)
        item(span = StaggeredGridItemSpan.FullLine) {
            if (heroItems.isNotEmpty()) {
                HeroCarousel(
                    heroItems = heroItems,
                    currentPlayingPodcastId = currentPlayingPodcastId,
                    isPlaying = isPlaying,
                    onPlayClick = { podcast -> onPlayClick?.invoke(podcast) },
                    onDetailsClick = { podcast ->
                        val ep = podcast.latestEpisode
                        if (ep != null) {
                            onEpisodeClick?.invoke(ep, podcast)
                        } else {
                            onPodcastClick(podcast)
                        }
                    },
                    onArrowClick = onHeroArrowClick,
                    onToggleSubscription = onToggleSubscription,
                    onTogglePlayback = onTogglePlayback,
                    modifier = Modifier.padding(horizontal = 8.dp) 
                )
            } else {
                cx.aswin.boxcast.feature.home.components.HeroSkeleton()
            }
        }

        // 2. "Your Shows" (Merged: Subscribed + New Episodes) - MOVED ABOVE "On The Rise"
        if (subscribedItems.isNotEmpty() || latestItems.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                YourShowsSection(
                    subscribedPodcasts = subscribedItems,
                    latestEpisodes = latestItems,
                    unplayedEpisodeCount = unplayedEpisodeCount,
                    onPodcastClick = onPodcastClick,
                    onEpisodeClick = { episode, podcast ->
                        onEpisodeClick?.invoke(episode, podcast)
                    },
                    onViewLibrary = { onNavigateToLibrary?.invoke() },
                    onViewAllLatest = onNavigateToLatestEpisodes
                )
            }
        }

        // 3. Time-Based Curated Block
        if (timeBlock != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                TimeBlockSection(
                    data = timeBlock,
                    onEpisodeClick = { episode, podcast -> onEpisodeClick?.invoke(episode, podcast) }
                )
            }
        }

        // 4. Discover Section (Header + Chips + Loading State)
        item(span = StaggeredGridItemSpan.FullLine) {
            cx.aswin.boxcast.feature.home.components.DiscoverSection(
                selectedCategory = selectedCategory,
                onCategorySelected = onSelectCategory,
                onHeaderClick = { onNavigateToExplore?.invoke(selectedCategory ?: "All") }
            )
        }

        // 5. Masonry Grid Content (Discover Podcasts) - LIMITED TO 6
        if (!isFilterLoading && gridItems.isNotEmpty()) {
            val limitedItems = gridItems.take(6)
            val showGenreChip = selectedCategory == null // Only show chips for "For You" tab
            items(limitedItems, key = { it.id }) { podcast ->
                val isTall = podcast.id.hashCode() % 3 == 0
                PodcastCard(
                    podcast = podcast,
                    isTall = isTall,
                    showGenreChip = showGenreChip,
                    onClick = { onPodcastClick(podcast) }
                )
            }
            
            // "View More" Button (Full Line)
            item(span = StaggeredGridItemSpan.FullLine) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { onNavigateToExplore?.invoke(selectedCategory ?: "All") }
                    ) {
                            Text("View more in ${selectedCategory ?: "Explore"}")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                    }
                }
            }
        } else { 
             // If loading OR empty, show Skeleton (Matches Hero/Rising behavior)
             GridSkeletonItems()
        }
    }
}
