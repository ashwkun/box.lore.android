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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Brush
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Briefing
import cx.aswin.boxcast.feature.home.components.DailyBriefingCard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape

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
    onImportClick: () -> Unit = {},
    onAiOnboardingClick: () -> Unit = {},
    onBriefingClick: (String) -> Unit = {},
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
        onImportClick = onImportClick,
        onAiOnboardingClick = onAiOnboardingClick,
        onDismissImportBanner = viewModel::dismissHomeImportBanner,
        onBriefingClick = onBriefingClick,

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
    onImportClick: () -> Unit = {},
    onAiOnboardingClick: () -> Unit = {},
    onDismissImportBanner: () -> Unit = {},
    onBriefingClick: (String) -> Unit = {},

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
                            briefing = uiState.briefing,
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
                            showImportBanner = uiState.showImportBanner,
                            onImportClick = onImportClick,
                            onAiOnboardingClick = onAiOnboardingClick,
                            onDismissImportBanner = onDismissImportBanner,
                            onBriefingClick = onBriefingClick,
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
    showImportBanner: Boolean = false,
    onImportClick: () -> Unit = {},
    onAiOnboardingClick: () -> Unit = {},
    onDismissImportBanner: () -> Unit = {},
    briefing: Briefing? = null,
    onBriefingClick: (String) -> Unit = {},
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    modifier: Modifier = Modifier
) {
    LogRecomposition(name = "PodcastFeed")

    // Track whether initial content has loaded (for staggered entrance animation)
    val heroLoaded = !isLoading && heroItems.isNotEmpty()

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
                // Crossfade between skeleton and hero carousel
                androidx.compose.animation.Crossfade(
                    targetState = heroLoaded,
                    animationSpec = tween(500),
                    label = "hero_crossfade"
                ) { loaded ->
                    if (!loaded) {
                        cx.aswin.boxcast.feature.home.components.HeroSkeleton(
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
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

        // 2. "Your Shows" (Interactive selector grid & filtered stack)
        item(span = StaggeredGridItemSpan.FullLine) {
            // Crossfade: skeleton → your shows → empty (nothing)
            androidx.compose.animation.Crossfade(
                targetState = when {
                    isLoading -> "skeleton"
                    subscribedItems.isNotEmpty() -> "content"
                    showImportBanner -> "banner"
                    else -> "empty"
                },
                animationSpec = tween(500),
                label = "your_shows_crossfade"
            ) { state ->
                when (state) {
                    "skeleton" -> YourShowsSkeleton(subscribedCount = subscribedItems.size)
                    "content" -> YourShowsSection(
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
                    "banner" -> {
                        LaunchedEffect(Unit) {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackHomeImportBannerImpression()
                        }
                        HomeImportBanner(
                            onAiOnboardingClick = {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackHomeImportBannerClicked("ai")
                                onAiOnboardingClick()
                            },
                            onSearchClick = {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackHomeImportBannerClicked("search")
                                onNavigateToExplore?.invoke(null, "home_banner", null)
                            },
                            onImportClick = {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackHomeImportBannerClicked("import")
                                onImportClick()
                            },
                            onDismiss = {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackHomeImportBannerDismissed()
                                onDismissImportBanner()
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    else -> {} // No subs, render nothing
                }
            }
        }

        // "For You" Personalized Recommendations Section (Magazine Split Layout)
        // Render this item slot unconditionally so its skeleton is shown during loading
        item(span = StaggeredGridItemSpan.FullLine) {
            AnimatedVisibility(
                visible = isLoading || recommendations.isNotEmpty(),
                enter = fadeIn(animationSpec = tween(600)) + expandVertically(
                    animationSpec = tween(500),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(
                    animationSpec = tween(300),
                    shrinkTowards = Alignment.Top
                )
            ) {
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

        // 3. Time-Based Curated Block — Crossfade skeleton → content
        item(span = StaggeredGridItemSpan.FullLine) {
            androidx.compose.animation.Crossfade(
                targetState = when {
                    isLoading -> "skeleton"
                    timeBlock != null -> "content"
                    else -> "skeleton" // Still loading curated (show shimmer)
                },
                animationSpec = tween(600),
                label = "timeblock_crossfade"
            ) { state ->
                when (state) {
                    "skeleton" -> TimeBlockSkeleton()
                    "content" -> TimeBlockSection(
                        data = timeBlock!!,
                        onCuratedEpisodeClick = { episode, podcast, vibeId, pos -> onCuratedEpisodeClick?.invoke(episode, podcast, vibeId, pos) },
                        onImpression = onCuratedImpression,
                        onSeeAllClick = {
                            onNavigateToExplore?.invoke(null, "home_time_block_see_all", "foryou")
                        }
                    )
                }
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

        // Daily Briefing Card
        if (briefing != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    // Section Header matching M3 styling
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Daily Briefing",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            letterSpacing = (-0.5).sp
                        )
                    }

                    DailyBriefingCard(
                        briefing = briefing,
                        isPlaying = isPlaying && currentPlayingEpisodeId == "briefing_${briefing.region}_${briefing.date}",
                        onPlayPauseClick = {
                            val isCurrentPlaying = isPlaying && currentPlayingEpisodeId == "briefing_${briefing.region}_${briefing.date}"
                            if (isCurrentPlaying) {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingPauseClicked(
                                    region = briefing.region,
                                    date = briefing.date,
                                    source = "home_banner"
                                )
                            } else {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingPlayClicked(
                                    region = briefing.region,
                                    date = briefing.date,
                                    source = "home_banner"
                                )
                            }

                            val publishedDate = try {
                                java.time.LocalDate.parse(briefing.date)
                                    .atStartOfDay(java.time.ZoneOffset.UTC)
                                    .toEpochSecond()
                            } catch (e: Exception) {
                                System.currentTimeMillis() / 1000
                            }
                            val audioUri = android.net.Uri.parse(briefing.audioUrl)
                            val version = audioUri.getQueryParameter("v")
                            val versionParam = if (version != null) "&v=$version" else ""
                            onPlayEpisode(
                                cx.aswin.boxcast.core.model.Episode(
                                    id = "briefing_${briefing.region}_${briefing.date}",
                                    title = briefing.title,
                                    description = "Your daily AI-generated news briefing for ${briefing.region.uppercase()}.",
                                    audioUrl = briefing.audioUrl,
                                    imageUrl = briefing.coverUrl,
                                    podcastId = "briefing_${briefing.region}",
                                    podcastTitle = "The Boxcast Brief",
                                    podcastImageUrl = briefing.coverUrl,
                                    podcastArtist = "BoxCast AI",
                                    duration = 180,
                                    publishedDate = publishedDate,
                                    transcriptUrl = "https://api.aswin.cx/briefings/transcript/${briefing.region}?d=${briefing.date}$versionParam",
                                    chaptersUrl = "https://api.aswin.cx/briefings/chapters/${briefing.region}?d=${briefing.date}$versionParam"
                                ),
                                cx.aswin.boxcast.core.model.Podcast(
                                    id = "briefing_${briefing.region}",
                                    title = "The Boxcast Brief",
                                    artist = "BoxCast AI",
                                    imageUrl = briefing.coverUrl
                                )
                            )
                        },
                        onClick = {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingBannerTapped(
                                region = briefing.region,
                                date = briefing.date
                            )
                            onBriefingClick(briefing.region)
                        }
                    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeImportBanner(
    onAiOnboardingClick: () -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Dismiss button in top right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 40.dp) // Avoid overlapping with dismiss button
                ) {
                    Text(
                        text = "it's a bit quiet in here...",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "let's find your favorite shows so we can build your daily mix and curate better episode recommendations.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Primary full-width action button
                    Button(
                        onClick = onAiOnboardingClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveClickable(onClick = onAiOnboardingClick)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Find shows with AI",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // Secondary split-width action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSearchClick,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .expressiveClickable(onClick = onSearchClick)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Search",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = onImportClick,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .expressiveClickable(onClick = onImportClick)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DriveFolderUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Import",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}


