package cx.aswin.boxcast.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
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
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

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
import cx.aswin.boxcast.feature.home.components.YourShowsSkeleton
import cx.aswin.boxcast.feature.home.components.TimeBlockSkeleton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.EpisodeStatus
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.designsystem.components.LogRecomposition

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
import cx.aswin.boxcast.feature.home.components.ForYouSection

import cx.aswin.boxcast.feature.home.components.DebugDbInspectorDialog
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.data.database.PodcastEntity

@Composable
fun HomeRoute(
    apiBaseUrl: String,
    publicKey: String,
    playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onHeroArrowClick: (SmartHeroItem, Int) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)? = null, // Navigate to EpisodeInfo
    onCuratedEpisodeClick: ((Episode, Podcast, String, Int) -> Unit)? = null,
    onPlayClick: ((Podcast, android.os.Bundle?) -> Unit)? = null, // Navigate directly to Player (Resume)
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToLatestEpisodes: (() -> Unit)? = null,
    onNavigateToExplore: ((String?, String, String?) -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToPlayStoreReview: () -> Unit = {},
    onSubmitFeedback: suspend (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    onResetSleepNudge: () -> Unit = {},
    onClearSleepTimer: () -> Unit = {},
    navController: NavController? = null,
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

    if (navController != null) {
        DisposableEffect(navController) {
            val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                val route = destination.route
                if (route != null && route != "home" && !route.startsWith("episode") && !route.startsWith("podcast")) {
                    viewModel.selectPodcast(null)
                }
            }
            navController.addOnDestinationChangedListener(listener)
            onDispose {
                navController.removeOnDestinationChangedListener(listener)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val isPlaying by remember(viewModel) {
        viewModel.playerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val currentPlayingPodcastId by remember(viewModel) {
        viewModel.playerState.map { it.currentPodcast?.id }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val currentPlayingEpisodeId by remember(viewModel) {
        viewModel.playerState.map { it.currentEpisode?.id }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val debugHistory by viewModel.debugHistory.collectAsState(initial = emptyList())
    val debugPodcasts by viewModel.debugPodcasts.collectAsState(initial = emptyList())

    val showReviewPrompt by viewModel.showReviewPrompt.collectAsState()
    val showPostReview by viewModel.showPostReview.collectAsState()
    val showFeedback by viewModel.showFeedback.collectAsState()
    

    
    HomeScreen(
        uiState = uiState,
        currentPlayingPodcastId = currentPlayingPodcastId,
        currentPlayingEpisodeId = currentPlayingEpisodeId,
        isPlaying = isPlaying,
        debugHistory = debugHistory,
        debugPodcasts = debugPodcasts,
        onPodcastClick = onPodcastClick,
        onHeroArrowClick = onHeroArrowClick,
        onEpisodeClick = onEpisodeClick,
        onCuratedEpisodeClick = onCuratedEpisodeClick,
        onCuratedImpression = viewModel::trackCuratedImpressionOnce,
        onPlayClick = onPlayClick,
        onNavigateToLibrary = onNavigateToLibrary,
        onNavigateToLatestEpisodes = onNavigateToLatestEpisodes,
        onNavigateToExplore = onNavigateToExplore,
        onToggleSubscription = viewModel::toggleSubscription,
        onTogglePlayback = viewModel::togglePlayback,
        onSelectCategory = viewModel::selectCategory,
        onPodcastSelected = viewModel::selectPodcast,
        onPlayMix = viewModel::playUnplayedMix,
        onPlayEpisode = viewModel::playEpisode,
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
        onResetSleepNudge = onResetSleepNudge,
        onClearSleepTimer = onClearSleepTimer,
        onSwitchRegion = viewModel::setRegion,
        onDismissNudge = viewModel::dismissRegionNudge,

        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    debugHistory: List<ListeningHistoryEntity>,
    debugPodcasts: List<PodcastEntity>,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onHeroArrowClick: (SmartHeroItem, Int) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    onCuratedEpisodeClick: ((Episode, Podcast, String, Int) -> Unit)?,
    onCuratedImpression: (String, List<String>) -> Unit = { _, _ -> },
    onPlayClick: ((Podcast, android.os.Bundle?) -> Unit)?,
    onNavigateToLibrary: (() -> Unit)?,
    onNavigateToLatestEpisodes: (() -> Unit)?,
    onNavigateToExplore: ((String?, String, String?) -> Unit)?,
    onToggleSubscription: (String) -> Unit,
    onTogglePlayback: (android.os.Bundle?) -> Unit,
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
    onResetSleepNudge: () -> Unit = {},
    onClearSleepTimer: () -> Unit = {},
    onSwitchRegion: (String) -> Unit = {},
    onDismissNudge: () -> Unit = {},
    onPodcastSelected: (String?) -> Unit = {},
    onPlayMix: () -> Unit = {},
    onPlayEpisode: (Episode, Podcast) -> Unit = { _, _ -> },

    modifier: Modifier = Modifier
) {
    LogRecomposition(name = "HomeScreen")
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
            onResetSleepNudge = onResetSleepNudge,
            onClearSleepTimer = onClearSleepTimer,
            onDismissRequest = { showDebugDialog = false }
        )
    }


    Box(modifier = modifier.fillMaxSize()) {
        // Main content underneath
        Column(modifier = Modifier.fillMaxSize()) {
            TopControlBar(
                scrollFraction = scrollFraction,

                onFeedbackClick = {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTopControlbarInteraction("feedback_clicked", "home")
                    onFeedbackClick()
                },
                onFeedbackLongClick = {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTopControlbarInteraction("feedback_long_clicked", "home")
                    onForceReviewPrompt()
                },
                onAvatarClick = {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTopControlbarInteraction("settings_clicked", "home")
                    onNavigateToSettings?.invoke()
                },
                onAvatarLongClick = {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTopControlbarInteraction("avatar_long_clicked", "home")
                    showDebugDialog = true
                }
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
                            currentPlayingEpisodeId = currentPlayingEpisodeId,
                            recommendations = uiState.recommendations,
                            isPlaying = isPlaying,
                            isFilterLoading = uiState.isFilterLoading,
                            selectedPodcastId = uiState.selectedPodcastId,
                            selectedPodcastEpisodes = uiState.selectedPodcastEpisodes,
                            isSelectedPodcastLoading = uiState.isSelectedPodcastLoading,
                            episodePlaybackState = uiState.episodePlaybackState,
                            isLoading = uiState.isLoading,
                            onPodcastSelected = onPodcastSelected,
                            onPlayMix = onPlayMix,
                            onPlayEpisode = onPlayEpisode,
                            onPodcastClick = onPodcastClick,
                            onHeroArrowClick = onHeroArrowClick,
                            onEpisodeClick = onEpisodeClick,
                            onCuratedEpisodeClick = onCuratedEpisodeClick,
                            onCuratedImpression = onCuratedImpression,
                            onPlayClick = { podcast, bundle -> onPlayClick?.invoke(podcast, bundle) },
                            onNavigateToLibrary = onNavigateToLibrary,
                            onNavigateToLatestEpisodes = onNavigateToLatestEpisodes,
                            onNavigateToExplore = onNavigateToExplore,
                            onToggleSubscription = onToggleSubscription,
                            onTogglePlayback = onTogglePlayback,
                            onSelectCategory = onSelectCategory,
                            showRegionNudge = uiState.showRegionNudge,
                            systemRegionCode = uiState.systemRegionCode,
                            activeRegionCode = uiState.activeRegionCode,
                            onSwitchRegion = onSwitchRegion,
                            onDismissNudge = onDismissNudge,
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
    currentPlayingEpisodeId: String?,
    recommendations: List<Episode> = emptyList(),
    isPlaying: Boolean,
    isFilterLoading: Boolean,
    selectedPodcastId: String? = null,
    selectedPodcastEpisodes: List<Episode> = emptyList(),
    isSelectedPodcastLoading: Boolean = false,
    episodePlaybackState: Map<String, Pair<EpisodeStatus, Float>> = emptyMap(),
    isLoading: Boolean,
    onPodcastSelected: (String?) -> Unit = {},
    onPlayMix: () -> Unit = {},
    onPlayEpisode: (Episode, Podcast) -> Unit = { _, _ -> },
    showRegionNudge: Boolean = false,
    systemRegionCode: String = "",
    activeRegionCode: String = "",
    onSwitchRegion: (String) -> Unit = {},
    onDismissNudge: () -> Unit = {},
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onHeroArrowClick: (SmartHeroItem, Int) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    onCuratedEpisodeClick: ((Episode, Podcast, String, Int) -> Unit)?,
    onCuratedImpression: (String, List<String>) -> Unit = { _, _ -> },
    onPlayClick: ((Podcast, android.os.Bundle?) -> Unit)?,
    onNavigateToLibrary: (() -> Unit)?,
    onNavigateToLatestEpisodes: (() -> Unit)?,
    onNavigateToExplore: ((String?, String, String?) -> Unit)?,
    onToggleSubscription: (String) -> Unit,
    onTogglePlayback: (android.os.Bundle?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    modifier: Modifier = Modifier
) {
    LogRecomposition(name = "PodcastFeed")
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(bottom = 160.dp, start = 16.dp, end = 16.dp), // Clear navbar + mini player 
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 16.dp,
        modifier = modifier.fillMaxSize()
    ) {

        // 1. Smart Hero (Personalized Content) and Region Nudge
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                if (isLoading || heroItems.isEmpty()) {
                    cx.aswin.boxcast.feature.home.components.HeroSkeleton()
                } else {
                    HeroCarousel(
                        heroItems = heroItems,
                        currentPlayingPodcastId = currentPlayingPodcastId,
                        isPlaying = isPlaying,
                        onPlayClick = { podcast, bundle -> onPlayClick?.invoke(podcast, bundle) },
                        onDetailsClick = { podcast ->
                            val ep = podcast.latestEpisode
                            if (ep != null) {
                                onEpisodeClick?.invoke(ep, podcast, "home_hero_card")
                            } else {
                                onPodcastClick(podcast, "home_hero_card", null, null)
                            }
                        },
                        onArrowClick = onHeroArrowClick,
                        onToggleSubscription = onToggleSubscription,
                        onTogglePlayback = onTogglePlayback,
                        modifier = Modifier.padding(horizontal = 8.dp) 
                    )
                }

                AnimatedVisibility(
                    visible = showRegionNudge,
                    enter = expandVertically(
                        animationSpec = tween(400),
                        expandFrom = androidx.compose.ui.Alignment.Top
                    ) + fadeIn(animationSpec = tween(400)),
                    exit = shrinkVertically(
                        animationSpec = tween(300),
                        shrinkTowards = androidx.compose.ui.Alignment.Top
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    cx.aswin.boxcast.feature.home.components.RegionMismatchNudgeBanner(
                        systemRegion = systemRegionCode,
                        activeRegion = activeRegionCode,
                        onSwitchRegion = onSwitchRegion,
                        onDismiss = onDismissNudge,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp)
                    )
                }
            }
        }

        // 2. "Your Shows" (Merged: Subscribed + New Episodes) - MOVED ABOVE "On The Rise"
        // 2. "Your Shows" (Interactive selector grid & filtered stack)
        if (isLoading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                YourShowsSkeleton()
            }
        } else if (subscribedItems.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                YourShowsSection(
                    subscribedPodcasts = subscribedItems,
                    latestEpisodes = latestItems,
                    unplayedEpisodeCount = unplayedEpisodeCount,
                    selectedPodcastId = selectedPodcastId,
                    selectedPodcastEpisodes = selectedPodcastEpisodes,
                    isSelectedPodcastLoading = isSelectedPodcastLoading,
                    episodePlaybackState = episodePlaybackState,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    onPodcastSelected = onPodcastSelected,
                    onPodcastClick = { onPodcastClick(it, "home_your_shows", null, null) },
                    onEpisodeClick = { episode, podcast, entryPoint ->
                        onEpisodeClick?.invoke(episode, podcast, entryPoint)
                    },
                    onPlayMix = onPlayMix,
                    onPlayEpisode = onPlayEpisode,
                    onViewLibrary = { onNavigateToLibrary?.invoke() }
                )
            }
        }

        // "For You" Personalized Recommendations Section (Magazine Split Layout)
        if (!isLoading && (recommendations.isNotEmpty() || timeBlock != null)) {
            item(span = StaggeredGridItemSpan.FullLine) {
                ForYouSection(
                    recommendations = recommendations,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    onEpisodeClick = { episode, podcast ->
                        onEpisodeClick?.invoke(episode, podcast, "home_for_you")
                    },
                    onPlayEpisode = onPlayEpisode,
                    timeBlock = timeBlock,
                    onSeeAllClick = {
                        onNavigateToExplore?.invoke(null, "home_for_you_see_all", "foryou")
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // 3. Time-Based Curated Block
        if (isLoading) {
            item(span = StaggeredGridItemSpan.FullLine) {
                TimeBlockSkeleton()
            }
        } else if (timeBlock != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                TimeBlockSection(
                    data = timeBlock,
                    onCuratedEpisodeClick = { episode, podcast, vibeId, pos -> onCuratedEpisodeClick?.invoke(episode, podcast, vibeId, pos) },
                    onImpression = onCuratedImpression
                )
            }
        }

        // "See All Recommendations" button — between timeblock and discover
        if (!isLoading && (recommendations.isNotEmpty() || timeBlock != null)) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            onNavigateToExplore?.invoke(null, "home_for_you_see_all", "foryou")
                        }
                    ) {
                        androidx.compose.material3.Text(
                            text = "See All Recommendations",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        // 4. Discover Section (Header + Chips + Loading State)
        item(span = StaggeredGridItemSpan.FullLine) {
            cx.aswin.boxcast.feature.home.components.DiscoverSection(
                selectedCategory = selectedCategory,
                onCategorySelected = onSelectCategory,
                onHeaderClick = { onNavigateToExplore?.invoke(selectedCategory ?: "All", "home_discover_header", null) }
            )
        }

        // 5. Masonry Grid Content (Discover Podcasts) - LIMITED TO 6
        if (!isLoading && !isFilterLoading && gridItems.isNotEmpty()) {
            val limitedItems = gridItems.distinctBy { it.id }.take(6)
            val showGenreChip = selectedCategory == null // Only show chips for "For You" tab
            itemsIndexed(limitedItems, key = { _, p -> p.id }) { index, podcast ->
                val isTall = podcast.id.hashCode() % 3 == 0
                PodcastCard(
                    podcast = podcast,
                    isTall = isTall,
                    showGenreChip = showGenreChip,
                    onClick = { onPodcastClick(podcast, "home_discover_grid", selectedCategory, index) }
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
                        onClick = { onNavigateToExplore?.invoke(selectedCategory ?: "All", "home_discover_view_all_button", null) }
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
