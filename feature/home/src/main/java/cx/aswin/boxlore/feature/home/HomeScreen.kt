package cx.aswin.boxlore.feature.home

import android.content.pm.PackageManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cx.aswin.boxlore.core.data.BuildConfig
import cx.aswin.boxlore.core.data.content.ContentCandidate
import cx.aswin.boxlore.core.data.content.ContentDaypart
import cx.aswin.boxlore.core.data.content.ContentSection
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.domain.ports.AlwaysOnlineConnectivity
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.components.BecauseYouLikeSection
import cx.aswin.boxlore.feature.home.components.ChangeRecommendationPodcastSheet
import cx.aswin.boxlore.feature.home.components.CuratedEpisodeCard
import cx.aswin.boxlore.feature.home.components.DailyBriefingCard
import cx.aswin.boxlore.feature.home.components.GridSkeletonItem
import cx.aswin.boxlore.feature.home.components.HeroCarousel
import cx.aswin.boxlore.feature.home.components.HomeChildHeaderTone
import cx.aswin.boxlore.feature.home.components.HomeChildSectionHeader
import cx.aswin.boxlore.feature.home.components.HomeTopLevelSectionHeader
import cx.aswin.boxlore.feature.home.components.LocalLastSeenEpisodes
import cx.aswin.boxlore.feature.home.components.PodcastCard
import cx.aswin.boxlore.feature.home.components.TopControlBar
import cx.aswin.boxlore.feature.home.components.YourShowsSection
import cx.aswin.boxlore.feature.home.components.YourShowsSkeleton
import cx.aswin.boxlore.feature.home.components.forYouItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.lazy.items as lazyRowItems

@androidx.compose.runtime.Stable
data class StableHeroList(
    val list: List<SmartHeroItem>,
)

@androidx.compose.runtime.Stable
data class StablePodcastList(
    val list: List<Podcast>,
)

@androidx.compose.runtime.Stable
data class StableEpisodeList(
    val list: List<Episode>,
)

@androidx.compose.runtime.Stable
data class StableContentSectionList(
    val list: List<ContentSection>,
)

@androidx.compose.runtime.Stable
data class StablePlaybackStateMap(
    val map: Map<String, Pair<EpisodeStatus, Float>>,
)

@androidx.compose.runtime.Stable
data class HomePlaybackUi(
    val currentPlayingPodcastId: String?,
    val currentPlayingEpisodeId: String?,
    val isPlaying: Boolean,
    val isPlayerLoading: Boolean = false,
    val downloadedEpisodeIds: Set<String> = emptySet(),
)

@androidx.compose.runtime.Stable
data class HomeSheetUi(
    val showReviewPrompt: Boolean = false,
    val reviewPromptVariant: cx.aswin.boxlore.feature.home.components.ReviewPromptVariant =
        cx.aswin.boxlore.feature.home.components.ReviewPromptVariant.Milestone,
    val showPostReview: Boolean = false,
    val showFeedback: Boolean = false,
    val candidatePodcasts: List<Podcast> = emptyList(),
)

@androidx.compose.runtime.Stable
data class HomeFeedCallbacks(
    val onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    val onHeroArrowClick: (SmartHeroItem, Int) -> Unit,
    val onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    val onAdaptiveSectionVisible: (ContentSection, Set<String>) -> Unit,
    val onPlayClick: ((Podcast, android.os.Bundle?) -> Unit)?,
    val onNavigateToLibrary: (() -> Unit)?,
    val onNavigateToExplore: ((String?, String, String?) -> Unit)?,
    val onToggleSubscription: (String) -> Unit,
    val onTogglePlayback: (android.os.Bundle?) -> Unit,
    val onSelectCategory: (String?) -> Unit,
    val onPodcastSelected: (String?) -> Unit,
    val onPlayMix: () -> Unit,
    val onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
    val onImportClick: () -> Unit,
    val onAiOnboardingClick: () -> Unit,
    val onDismissImportBanner: () -> Unit,
    val onBriefingClick: (String) -> Unit,
    val onDismissBriefing: () -> Unit,
    val onDismissBriefingForever: () -> Unit,
    val onFeedbackClick: () -> Unit,
)

@androidx.compose.runtime.Stable
data class HomeScreenCallbacks(
    val feed: HomeFeedCallbacks,
    val onNavigateToSettings: (() -> Unit)? = null,
    val onForceReviewPrompt: () -> Unit = {},
    val onForceSurveyNps: () -> Unit = {},
    val onDismissReviewPrompt: () -> Unit = {},
    val onDismissPostReview: () -> Unit = {},
    val onDismissFeedback: () -> Unit = {},
    val onNavigateToPlayStoreReview: () -> Unit = {},
    val onSubmitFeedback: suspend (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    val onNavigateToDebug: () -> Unit = {},
    val onOverrideRecommendationPodcast: (String?) -> Unit = {},
    val onNavigateToLatestEpisodes: (() -> Unit)? = null,
)

@Composable
@Suppress("LongParameterList", "LongMethod")
fun HomeRoute(
    podcastRepository: cx.aswin.boxlore.core.data.PodcastRepository,
    playbackRepository: cx.aswin.boxlore.core.data.PlaybackRepository,
    engagementPromptCoordinator: cx.aswin.boxlore.core.data.EngagementPromptCoordinator,
    subscriptionRepository: cx.aswin.boxlore.core.data.SubscriptionRepository,
    downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository,
    rssPodcastRepository: cx.aswin.boxlore.core.rss.RssPodcastRepository,
    adaptiveRankingRepository: cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository,
    adaptiveCandidateScorer: cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer,
    rankingFeedbackRepository: cx.aswin.boxlore.core.ranking.RankingFeedbackRepository,
    localCatalog: cx.aswin.boxlore.core.domain.ports.LocalCatalogPort,
    userPreferencesRepository: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    connectivityStatus: ConnectivityStatusPort = AlwaysOnlineConnectivity,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onHeroArrowClick: (SmartHeroItem, Int) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)? = null, // Navigate to EpisodeInfo
    onPlayClick: ((Podcast, android.os.Bundle?) -> Unit)? = null, // Navigate directly to Player (Resume)
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToLatestEpisodes: (() -> Unit)? = null,
    onNavigateToExplore: ((String?, String, String?) -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToPlayStoreReview: () -> Unit = {},
    onSubmitFeedback: suspend (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    onNavigateToDebug: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onAiOnboardingClick: () -> Unit = {},
    onBriefingClick: (String) -> Unit = {},
    navController: NavController? = null,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel: HomeViewModel =
        viewModel(
            factory =
                HomeViewModelAssembler.factory(
                    application = application,
                    deps =
                        HomeViewModelDeps(
                            podcastRepository = podcastRepository,
                            playbackRepository = playbackRepository,
                            engagementCoordinator = engagementPromptCoordinator,
                            subscriptionRepository = subscriptionRepository,
                            downloadRepository = downloadRepository,
                            rssRepository = rssPodcastRepository,
                            adaptiveRankingRepository = adaptiveRankingRepository,
                            adaptiveScorer = adaptiveCandidateScorer,
                            rankingFeedback = rankingFeedbackRepository,
                            localCatalog = localCatalog,
                            userPreferencesRepository = userPreferencesRepository,
                            connectivityStatus = connectivityStatus,
                        ),
                ),
        )

    if (navController != null) {
        DisposableEffect(navController) {
            val listener =
                NavController.OnDestinationChangedListener { _, destination, _ ->
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
    val isPlayerLoading by remember(viewModel) {
        viewModel.playerState.map { it.isLoading }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val showReviewPrompt by viewModel.showReviewPrompt.collectAsState()
    val reviewPromptVariant by viewModel.reviewPromptVariant.collectAsState()
    val showPostReview by viewModel.showPostReview.collectAsState()
    val showFeedback by viewModel.showFeedback.collectAsState()

    val candidatePodcasts by viewModel.candidatePodcasts.collectAsState(initial = emptyList())

    val downloadedEpisodeIds by viewModel.downloadedEpisodeIds.collectAsState(initial = emptySet())
    val lastSeenEpisodes by viewModel.lastSeenEpisodes.collectAsState()

    val callbacks =
        remember(
            viewModel,
            onPodcastClick,
            onHeroArrowClick,
            onEpisodeClick,
            onPlayClick,
            onNavigateToLibrary,
            onNavigateToExplore,
            onNavigateToSettings,
            onNavigateToPlayStoreReview,
            onSubmitFeedback,
            onNavigateToDebug,
            onImportClick,
            onAiOnboardingClick,
            onBriefingClick,
            onNavigateToLatestEpisodes,
        ) {
            HomeScreenCallbacks(
                feed =
                    HomeFeedCallbacks(
                        onPodcastClick = { podcast, entryPoint, category, index ->
                            podcast.latestEpisode?.id?.let { episodeId ->
                                viewModel.markPodcastEpisodeAsSeen(podcast.id, episodeId)
                            }
                            onPodcastClick(podcast, entryPoint, category, index)
                        },
                        onHeroArrowClick = onHeroArrowClick,
                        onEpisodeClick = onEpisodeClick,
                        onAdaptiveSectionVisible = viewModel::trackAdaptiveSectionVisible,
                        onPlayClick = onPlayClick,
                        onNavigateToLibrary = onNavigateToLibrary,
                        onNavigateToExplore = onNavigateToExplore,
                        onToggleSubscription = viewModel::toggleSubscription,
                        onTogglePlayback = viewModel::togglePlayback,
                        onSelectCategory = viewModel::selectCategory,
                        onPodcastSelected = viewModel::selectPodcast,
                        onPlayMix = viewModel::playUnplayedMix,
                        onPlayEpisode = viewModel::playEpisode,
                        onImportClick = onImportClick,
                        onAiOnboardingClick = onAiOnboardingClick,
                        onDismissImportBanner = viewModel::dismissHomeImportBanner,
                        onBriefingClick = onBriefingClick,
                        onDismissBriefing = viewModel::dismissBriefingForToday,
                        onDismissBriefingForever = viewModel::dismissBriefingForever,
                        onFeedbackClick = viewModel::triggerFeedback,
                    ),
                onNavigateToSettings = onNavigateToSettings,
                onForceReviewPrompt = viewModel::forceReviewPrompt,
                onForceSurveyNps = viewModel::forceSurveyNps,
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
                onNavigateToDebug = onNavigateToDebug,
                onOverrideRecommendationPodcast = viewModel::setOverriddenRecPodcast,
                onNavigateToLatestEpisodes = onNavigateToLatestEpisodes,
            )
        }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLastSeenEpisodes provides lastSeenEpisodes,
    ) {
        HomeScreen(
            uiState = uiState,
            playback =
                HomePlaybackUi(
                    currentPlayingPodcastId = currentPlayingPodcastId,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    isPlayerLoading = isPlayerLoading,
                    downloadedEpisodeIds = downloadedEpisodeIds,
                ),
            sheets =
                HomeSheetUi(
                    showReviewPrompt = showReviewPrompt,
                    reviewPromptVariant = reviewPromptVariant,
                    showPostReview = showPostReview,
                    showFeedback = showFeedback,
                    candidatePodcasts = candidatePodcasts,
                ),
            callbacks = callbacks,
            modifier = modifier,
        )
    }
}

private fun homeScrollFraction(
    gridState: LazyStaggeredGridState,
    collapseThresholdPx: Float,
): Float {
    if (gridState.firstVisibleItemIndex > 0) {
        return 1f
    }
    return (gridState.firstVisibleItemScrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
}

@Composable
private fun HomeScreenFeedContent(
    uiState: HomeUiState,
    gridState: LazyStaggeredGridState,
    playback: HomePlaybackUi,
    callbacks: HomeFeedCallbacks,
    onChangePodcastClick: () -> Unit,
) {
    if (uiState.isError) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error loading content", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    PodcastFeed(
        heroItems = StableHeroList(uiState.heroItems),
        latestItems = StablePodcastList(uiState.latestEpisodes),
        subscribedItems = StablePodcastList(uiState.subscribedPodcasts),
        discoveryGreeting = uiState.discoveryGreeting,
        adaptiveSections = StableContentSectionList(uiState.adaptiveSections),
        isAdaptiveSectionsLoading = uiState.isAdaptiveSectionsLoading,
        onAdaptiveSectionVisible = callbacks.onAdaptiveSectionVisible,
        briefing = uiState.briefing,
        briefingChapters = uiState.briefingChapters,
        gridItems = StablePodcastList(uiState.discoverPodcasts),
        selectedCategory = uiState.selectedCategory,
        currentPlayingPodcastId = playback.currentPlayingPodcastId,
        currentPlayingEpisodeId = playback.currentPlayingEpisodeId,
        recommendations = StableEpisodeList(uiState.recommendations),
        isRecommendationsFallback = uiState.isRecommendationsFallback,
        isPlaying = playback.isPlaying,
        isPlayerLoading = playback.isPlayerLoading,
        isFilterLoading = uiState.isFilterLoading,
        selectedPodcastId = uiState.selectedPodcastId,
        selectedPodcastEpisodes = StableEpisodeList(uiState.selectedPodcastEpisodes),
        isSelectedPodcastLoading = uiState.isSelectedPodcastLoading,
        isSelectedRssRefreshing = uiState.isSelectedRssRefreshing,
        episodePlaybackState = StablePlaybackStateMap(uiState.episodePlaybackState),
        isLoading = uiState.isLoading,
        isRecommendationsLoading = uiState.isRecommendationsLoading,
        seemsToLikePodcast = uiState.seemsToLikePodcast,
        becauseYouLikeRecommendations = StableEpisodeList(uiState.becauseYouLikeRecommendations),
        becauseYouLikePodcasts = StablePodcastList(uiState.becauseYouLikePodcasts),
        onChangePodcastClick = onChangePodcastClick,
        onPodcastSelected = callbacks.onPodcastSelected,
        downloadedEpisodeIds = playback.downloadedEpisodeIds,
        onPlayMix = callbacks.onPlayMix,
        onPlayEpisode = callbacks.onPlayEpisode,
        onPodcastClick = callbacks.onPodcastClick,
        onHeroArrowClick = callbacks.onHeroArrowClick,
        onEpisodeClick = callbacks.onEpisodeClick,
        onPlayClick = callbacks.onPlayClick,
        onNavigateToLibrary = callbacks.onNavigateToLibrary,
        onNavigateToExplore = callbacks.onNavigateToExplore,
        onToggleSubscription = callbacks.onToggleSubscription,
        onTogglePlayback = callbacks.onTogglePlayback,
        onSelectCategory = callbacks.onSelectCategory,
        showImportBanner = uiState.showImportBanner,
        onImportClick = callbacks.onImportClick,
        onAiOnboardingClick = callbacks.onAiOnboardingClick,
        onDismissImportBanner = callbacks.onDismissImportBanner,
        onBriefingClick = callbacks.onBriefingClick,
        onDismissBriefing = callbacks.onDismissBriefing,
        onDismissBriefingForever = callbacks.onDismissBriefingForever,
        onFeedbackClick = callbacks.onFeedbackClick,
        gridState = gridState,
    )
}

@Composable
private fun HomeScreenBottomSheets(
    uiState: HomeUiState,
    sheets: HomeSheetUi,
    showChangePodcastSheet: Boolean,
    callbacks: HomeScreenCallbacks,
    onDismissChangePodcastSheet: () -> Unit,
) {
    if (sheets.showReviewPrompt) {
        cx.aswin.boxlore.feature.home.components.ReviewPromptSheet(
            completedCount = uiState.completedEpisodeCount,
            variant = sheets.reviewPromptVariant,
            onDismissRequest = callbacks.onDismissReviewPrompt,
            onNavigateToReview = callbacks.onNavigateToPlayStoreReview,
            onNavigateToFeedback = {
                callbacks.onDismissReviewPrompt()
                callbacks.feed.onFeedbackClick()
            },
        )
    }

    if (sheets.showPostReview) {
        cx.aswin.boxlore.feature.home.components.PostReviewSheet(
            onDismissRequest = callbacks.onDismissPostReview,
            onNavigateToFeedback = {
                callbacks.onDismissPostReview()
                callbacks.feed.onFeedbackClick()
            },
        )
    }

    if (sheets.showFeedback) {
        val context = LocalContext.current
        val versionStr =
            remember(context) {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
                } catch (_: PackageManager.NameNotFoundException) {
                    "unknown"
                }
            }

        cx.aswin.boxlore.feature.home.components.FeedbackSheet(
            appVersion = versionStr,
            onSubmit = callbacks.onSubmitFeedback,
            onRateInstead = {
                callbacks.onDismissFeedback()
                val pkgName = context.packageName
                try {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkgName")),
                    )
                } catch (e: Exception) {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkgName"),
                        ),
                    )
                }
            },
            onDismissRequest = callbacks.onDismissFeedback,
        )
    }

    if (showChangePodcastSheet) {
        ChangeRecommendationPodcastSheet(
            candidatePodcasts = sheets.candidatePodcasts,
            onDismissRequest = onDismissChangePodcastSheet,
            onPodcastSelect = { podcast ->
                onDismissChangePodcastSheet()
                callbacks.onOverrideRecommendationPodcast(podcast?.id)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    playback: HomePlaybackUi,
    sheets: HomeSheetUi,
    callbacks: HomeScreenCallbacks,
    modifier: Modifier = Modifier,
) {
    // Track scroll state for collapsing top bar
    val gridState = rememberLazyStaggeredGridState()
    var showChangePodcastSheet by remember { androidx.compose.runtime.mutableStateOf(false) }

    val collapseThresholdPx = with(LocalDensity.current) { 100.dp.toPx() }
    val scrollFraction by remember(gridState, collapseThresholdPx) {
        derivedStateOf { homeScrollFraction(gridState, collapseThresholdPx) }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopControlBar(
                scrollFractionProvider = { scrollFraction },
                onFeedbackClick = {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .trackTopControlbarInteraction("feedback_clicked", "home")
                    callbacks.feed.onFeedbackClick()
                },
                onFeedbackLongClick = {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .trackTopControlbarInteraction("feedback_long_clicked", "home")
                    callbacks.onForceSurveyNps()
                },
                onAvatarClick = {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .trackTopControlbarInteraction("settings_clicked", "home")
                    callbacks.onNavigateToSettings?.invoke()
                },
                onAvatarLongClick = {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .trackTopControlbarInteraction("avatar_long_clicked", "home")
                    callbacks.onNavigateToDebug()
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                HomeScreenFeedContent(
                    uiState = uiState,
                    gridState = gridState,
                    playback = playback,
                    callbacks = callbacks.feed,
                    onChangePodcastClick = { showChangePodcastSheet = true },
                )
            }
        }
    }

    HomeScreenBottomSheets(
        uiState = uiState,
        sheets = sheets,
        showChangePodcastSheet = showChangePodcastSheet,
        callbacks = callbacks,
        onDismissChangePodcastSheet = { showChangePodcastSheet = false },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastFeed(
    heroItems: StableHeroList,
    latestItems: StablePodcastList,
    subscribedItems: StablePodcastList,
    discoveryGreeting: DiscoveryGreeting,
    adaptiveSections: StableContentSectionList,
    isAdaptiveSectionsLoading: Boolean = false,
    onAdaptiveSectionVisible: (ContentSection, Set<String>) -> Unit,
    gridItems: StablePodcastList,
    selectedCategory: String?,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    recommendations: StableEpisodeList,
    isPlaying: Boolean,
    isPlayerLoading: Boolean = false,
    isFilterLoading: Boolean,
    selectedPodcastId: String? = null,
    selectedPodcastEpisodes: StableEpisodeList,
    isSelectedPodcastLoading: Boolean = false,
    isSelectedRssRefreshing: Boolean = false,
    episodePlaybackState: StablePlaybackStateMap,
    isLoading: Boolean,
    isRecommendationsLoading: Boolean = true,
    isRecommendationsFallback: Boolean = true,
    onPodcastSelected: (String?) -> Unit = {},
    onPlayMix: () -> Unit = {},
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit = { _, _, _ -> },
    downloadedEpisodeIds: Set<String> = emptySet(),
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onHeroArrowClick: (SmartHeroItem, Int) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    onPlayClick: ((Podcast, android.os.Bundle?) -> Unit)?,
    onNavigateToLibrary: (() -> Unit)?,
    onNavigateToExplore: ((String?, String, String?) -> Unit)?,
    onToggleSubscription: (String) -> Unit,
    onTogglePlayback: (android.os.Bundle?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    showImportBanner: Boolean = false,
    onImportClick: () -> Unit = {},
    onAiOnboardingClick: () -> Unit = {},
    onDismissImportBanner: () -> Unit = {},
    briefing: Briefing? = null,
    briefingChapters: List<cx.aswin.boxlore.core.model.Chapter> = emptyList(),
    onBriefingClick: (String) -> Unit = {},
    onDismissBriefing: () -> Unit = {},
    onDismissBriefingForever: () -> Unit = {},
    seemsToLikePodcast: Podcast? = null,
    becauseYouLikeRecommendations: StableEpisodeList,
    becauseYouLikePodcasts: StablePodcastList,
    onChangePodcastClick: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    gridState: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Coordinated first-viewport reveal: hero + Your Shows flip from skeleton to
    // content on the same signal so the top of the feed appears in sync.
    val viewportReady = !isLoading
    val heroLoaded = viewportReady && heroItems.list.isNotEmpty()

    val hasBecauseYouLike =
        seemsToLikePodcast != null && (becauseYouLikeRecommendations.list.isNotEmpty() || becauseYouLikePodcasts.list.isNotEmpty())
    val hasRecommendations = isRecommendationsLoading || recommendations.list.isNotEmpty()

    // Discover grid content, memoized so scrolling/recomposition doesn't re-derive it.
    val discoverItems =
        remember(gridItems.list, selectedCategory) {
            gridItems.list.distinctBy { it.id }.take(10)
        }
    val showDiscoverContent = !isLoading && !isFilterLoading && discoverItems.isNotEmpty()
    val discoverGenreChip = selectedCategory == null
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        // 1. Smart Hero (Personalized Content)
        // Crossfade between skeleton and hero carousel. FullLine bottom padding of
        // 12dp + the grid's 12dp spacing yields the original 24dp section gap.
        item(span = StaggeredGridItemSpan.FullLine, key = "hero", contentType = "hero") {
            // Pin the hero so the grid keeps it composed even when scrolled off-screen.
            // The 420dp image carousel can't compose within a frame from scratch, so
            // rebuilding it on scroll-back-up caused the large freeze. Pinned off-screen
            // items stay in composition (but aren't measured/drawn), so re-entry is cheap
            // and the carousel keeps its swipe position.
            val heroPinnable = androidx.compose.ui.layout.LocalPinnableContainer.current
            androidx.compose.runtime.DisposableEffect(heroPinnable) {
                val handle = heroPinnable?.pin()
                onDispose { handle?.release() }
            }
            androidx.compose.animation.Crossfade(
                targetState = heroLoaded,
                animationSpec = tween(500),
                label = "hero_crossfade",
                modifier = Modifier.padding(bottom = 12.dp),
            ) { loaded ->
                if (!loaded) {
                    cx.aswin.boxlore.feature.home.components
                        .HeroSkeleton()
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
                        modifier = Modifier,
                    )
                }
            }
        }

        // 2. "Your Shows" (Interactive selector grid & filtered stack)
        // Crossfades with the hero on the same viewportReady signal.
        if (isLoading || subscribedItems.list.isNotEmpty() || showImportBanner) {
            item(span = StaggeredGridItemSpan.FullLine, key = "your_shows", contentType = "your_shows") {
                // Pinned like the hero: Your Shows (selector + episode stack) is heavy and
                // sits near the top, so keeping it composed avoids a rebuild spike on scroll-up.
                val yourShowsPinnable = androidx.compose.ui.layout.LocalPinnableContainer.current
                androidx.compose.runtime.DisposableEffect(yourShowsPinnable) {
                    val handle = yourShowsPinnable?.pin()
                    onDispose { handle?.release() }
                }
                androidx.compose.animation.Crossfade(
                    targetState = viewportReady,
                    animationSpec = tween(500),
                    label = "your_shows_crossfade",
                    modifier = Modifier.padding(bottom = 12.dp),
                ) { ready ->
                    when {
                        !ready -> YourShowsSkeleton(subscribedCount = subscribedItems.list.size)
                        subscribedItems.list.isNotEmpty() ->
                            YourShowsSection(
                                subscribedPodcasts = subscribedItems,
                                latestEpisodes = latestItems,
                                selectedPodcastId = selectedPodcastId,
                                selectedPodcastEpisodes = selectedPodcastEpisodes,
                                isSelectedPodcastLoading = isSelectedPodcastLoading,
                                isSelectedRssRefreshing = isSelectedRssRefreshing,
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
                                downloadedEpisodeIds = downloadedEpisodeIds,
                                onViewLibrary = { onNavigateToLibrary?.invoke() },
                            )
                        showImportBanner -> {
                            LaunchedEffect(Unit) {
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                                    .trackHomeImportBannerImpression()
                            }
                            HomeImportBanner(
                                onAiOnboardingClick = {
                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                                        .trackHomeImportBannerClicked("ai")
                                    onAiOnboardingClick()
                                },
                                onSearchClick = {
                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                                        .trackHomeImportBannerClicked("search")
                                    onNavigateToExplore?.invoke(null, "home_banner", null)
                                },
                                onImportClick = {
                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                                        .trackHomeImportBannerClicked("import")
                                    onImportClick()
                                },
                                onDismiss = {
                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                                        .trackHomeImportBannerDismissed()
                                    onDismissImportBanner()
                                },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // Daily Briefing Card
        if (briefing != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "briefing", contentType = "briefing") {
                val briefingId = "briefing_${briefing.region}_${briefing.date}"
                val playbackState = episodePlaybackState.map[briefingId]
                LaunchedEffect(briefing.region, briefing.date) {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingCardImpression(
                        region = briefing.region,
                        date = briefing.date,
                        playbackStatus = playbackState?.first?.name ?: "NOT_STARTED",
                    )
                }
                DailyBriefingCard(
                    briefing = briefing,
                    chapters = briefingChapters,
                    isPlaying = isPlaying && currentPlayingEpisodeId == briefingId,
                    playbackStatus = playbackState?.first,
                    playbackProgress = playbackState?.second,
                    isBuffering = isPlayerLoading && currentPlayingEpisodeId == briefingId,
                    onPlayPauseClick = {
                        val isCurrentPlaying = isPlaying && currentPlayingEpisodeId == briefingId
                        if (isCurrentPlaying) {
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingPauseClicked(
                                region = briefing.region,
                                date = briefing.date,
                                source = "home_banner",
                            )
                        } else {
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingPlayClicked(
                                region = briefing.region,
                                date = briefing.date,
                                source = "home_banner",
                            )
                        }

                        val publishedDate =
                            try {
                                java.time.LocalDate
                                    .parse(briefing.date)
                                    .atStartOfDay(java.time.ZoneOffset.UTC)
                                    .toEpochSecond()
                            } catch (e: Exception) {
                                System.currentTimeMillis() / 1000
                            }
                        val audioUri = android.net.Uri.parse(briefing.audioUrl)
                        val version = audioUri.getQueryParameter("v")
                        val versionParam = if (version != null) "&v=$version" else ""

                        val packageName = context.packageName
                        val resId =
                            when (briefing.region.lowercase()) {
                                "in", "ind" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_india
                                "uk", "gb" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_uk
                                "us", "usa" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_usa
                                else -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_global
                            }
                        val localCoverUrl = "android.resource://$packageName/$resId"

                        onPlayEpisode(
                            cx.aswin.boxlore.core.model.Episode(
                                id = briefingId,
                                title = briefing.title,
                                description = "Your daily AI-generated news briefing for ${briefing.region.uppercase()}.",
                                audioUrl = briefing.audioUrl,
                                imageUrl = localCoverUrl,
                                podcastId = "briefing_${briefing.region}",
                                podcastTitle = "The Boxlore Brief",
                                podcastImageUrl = localCoverUrl,
                                podcastArtist = "BoxCast AI",
                                duration = 180,
                                publishedDate = publishedDate,
                                transcriptUrl =
                                    briefing.transcriptUrl
                                        ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/transcript/${briefing.region}?d=${briefing.date}$versionParam",
                                chaptersUrl =
                                    briefing.chaptersUrl
                                        ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/${briefing.region}?d=${briefing.date}$versionParam",
                            ),
                            cx.aswin.boxlore.core.model.Podcast(
                                id = "briefing_${briefing.region}",
                                title = "The Boxlore Brief",
                                artist = "BoxCast AI",
                                imageUrl = localCoverUrl,
                            ),
                            cx.aswin.boxlore.core.model.PlaybackEntryPoint.GENERIC,
                        )
                    },
                    onClick = {
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingBannerTapped(
                            region = briefing.region,
                            date = briefing.date,
                        )
                        onBriefingClick(briefing.region)
                    },
                    onDismiss = onDismissBriefing,
                    onDismissForever = onDismissBriefingForever,
                    onFeedbackClick = onFeedbackClick,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        // Curated For You Main Header + Sections
        if (hasBecauseYouLike || hasRecommendations) {
            item(span = StaggeredGridItemSpan.FullLine, key = "curated_header", contentType = "section_header") {
                HomeTopLevelSectionHeader(
                    title = "Curated For You",
                    icon = Icons.Rounded.AutoAwesome,
                    seeAllIcon = Icons.Rounded.ChevronRight,
                    seeAllContentDescription = "See all curated recommendations",
                    onSeeAllClick = {
                        onNavigateToExplore?.invoke(null, "home_for_you_see_all", "foryou")
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // "Because You Like" Section
            if (seemsToLikePodcast != null && hasBecauseYouLike) {
                item(span = StaggeredGridItemSpan.FullLine, key = "because_you_like", contentType = "because_you_like") {
                    BecauseYouLikeSection(
                        podcast = seemsToLikePodcast,
                        recommendations = becauseYouLikeRecommendations,
                        suggestedPodcasts = becauseYouLikePodcasts,
                        currentPlayingEpisodeId = currentPlayingEpisodeId,
                        isPlaying = isPlaying,
                        onEpisodeClick = { episode, podcast ->
                            onEpisodeClick?.invoke(episode, podcast, "home_because_you_like")
                        },
                        onPlayEpisode = { ep, pod -> onPlayEpisode(ep, pod, cx.aswin.boxlore.core.model.PlaybackEntryPoint.GENERIC) },
                        onPodcastClick = { podcast ->
                            onPodcastClick(podcast, "home_because_you_like", null, null)
                        },
                        onChangePodcastClick = onChangePodcastClick,
                        modifier =
                            Modifier.padding(
                                bottom = 16.dp,
                            ),
                    )
                }
            }

            // "For You" normal recommendations section — flattened into individual
            // staggered items so masonry cards compose lazily as they scroll in
            // (avoids the atomic ~9-card compose spike that janked the scroll).
            if (hasRecommendations) {
                forYouItems(
                    recommendations = recommendations,
                    onEpisodeClick = { episode, podcast ->
                        onEpisodeClick?.invoke(episode, podcast, "home_for_you")
                    },
                    discoveryContextTitle = discoveryGreeting.title,
                    showTasteHeader = hasBecauseYouLike,
                    isFallback = isRecommendationsFallback,
                )
            }
        }

        item(
            span = StaggeredGridItemSpan.FullLine,
            key = "discovery_greeting",
            contentType = "discovery_greeting",
        ) {
            DiscoveryGreetingHeader(
                greeting = discoveryGreeting,
                onSeeAllClick = {
                    onNavigateToExplore?.invoke(null, "home_discovery_greeting", "foryou")
                },
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        if (isAdaptiveSectionsLoading && adaptiveSections.list.isEmpty()) {
            item(
                span = StaggeredGridItemSpan.FullLine,
                key = "adaptive_sections_skeleton",
                contentType = "adaptive_sections_skeleton",
            ) {
                cx.aswin.boxlore.feature.home.components
                    .AdaptiveRailsSkeleton()
            }
        }

        adaptiveSections.list.forEachIndexed { index, section ->
            adaptiveSectionItem(
                section = section,
                gridState = gridState,
                showHeader = true,
                isLastInGroup = index == adaptiveSections.list.lastIndex,
                onAdaptiveSectionVisible = onAdaptiveSectionVisible,
                onPodcastClick = onPodcastClick,
                onEpisodeClick = onEpisodeClick,
            )
        }

        // 3. Discover Section header (title + category chips)
        item(span = StaggeredGridItemSpan.FullLine, key = "discover_header", contentType = "section_header") {
            cx.aswin.boxlore.feature.home.components.DiscoverSection(
                selectedCategory = selectedCategory,
                onCategorySelected = onSelectCategory,
                onHeaderClick = { onNavigateToExplore?.invoke(selectedCategory ?: "All", "home_discover_header", null) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Discover masonry grid: each card is its own staggered-grid item so nothing
        // composes a big chunk mid-scroll, while the native staggered layout keeps the
        // original bento look.
        if (showDiscoverContent) {
            itemsIndexed(
                discoverItems,
                key = { _, podcast -> "discover_${podcast.id}" },
                contentType = { _, _ -> "discover_card" },
            ) { index, podcast ->
                PodcastCard(
                    podcast = podcast,
                    showGenreChip = discoverGenreChip,
                    onClick = { onPodcastClick(podcast, "home_discover_grid", selectedCategory, index) },
                )
            }

            // "View More" Button (Full Line)
            item(span = StaggeredGridItemSpan.FullLine, key = "discover_view_more", contentType = "discover_view_more") {
                androidx.compose.foundation.layout.Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { onNavigateToExplore?.invoke(selectedCategory ?: "All", "home_discover_view_all_button", null) },
                    ) {
                        Text("View more in ${selectedCategory ?: "Explore"}")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        } else {
            // Skeleton cards — also individual staggered items so they animate cheaply.
            items(6, key = { "discover_skel_$it" }, contentType = { "discover_skel" }) {
                GridSkeletonItem()
            }
        }
    }
}

@Composable
private fun DiscoveryGreetingHeader(
    greeting: DiscoveryGreeting,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (greeting.daypart) {
            ContentDaypart.MORNING,
            ContentDaypart.AFTERNOON,
            -> Icons.Rounded.WbSunny
            ContentDaypart.EVENING -> Icons.Rounded.WbTwilight
            ContentDaypart.LATE_NIGHT -> Icons.Rounded.NightsStay
        }
    HomeTopLevelSectionHeader(
        title = greeting.title,
        // Personalized rails are no longer daypart-specific; omit time-claiming subcopy.
        subtitle = null,
        icon = icon,
        seeAllIcon = Icons.Rounded.ChevronRight,
        seeAllContentDescription = "See all discoveries",
        onSeeAllClick = onSeeAllClick,
        modifier = modifier,
    )
}

private fun LazyStaggeredGridScope.adaptiveSectionItem(
    section: ContentSection,
    gridState: LazyStaggeredGridState,
    showHeader: Boolean,
    isLastInGroup: Boolean,
    onAdaptiveSectionVisible: (ContentSection, Set<String>) -> Unit,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
) {
    item(
        span = StaggeredGridItemSpan.FullLine,
        key = "adaptive_${section.stableId}",
        contentType = "adaptive_section",
    ) {
        AdaptiveSectionContent(
            section = section,
            gridState = gridState,
            showHeader = showHeader,
            isLastInGroup = isLastInGroup,
            onAdaptiveSectionVisible = onAdaptiveSectionVisible,
            onPodcastClick = onPodcastClick,
            onEpisodeClick = onEpisodeClick,
        )
    }
}

@Composable
private fun AdaptiveSectionContent(
    section: ContentSection,
    gridState: LazyStaggeredGridState,
    showHeader: Boolean,
    isLastInGroup: Boolean,
    onAdaptiveSectionVisible: (ContentSection, Set<String>) -> Unit,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
) {
    val rowState = rememberLazyListState()
    AdaptiveSectionVisibilityEffect(
        section = section,
        gridState = gridState,
        rowState = rowState,
        onAdaptiveSectionVisible = onAdaptiveSectionVisible,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = if (isLastInGroup) 20.dp else 12.dp),
    ) {
        if (showHeader) {
            AdaptiveSectionHeader(section)
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lazyRowItems(
                items = section.items,
                key = ContentCandidate::id,
            ) { candidate ->
                AdaptiveCandidateCard(
                    candidate = candidate,
                    source = "home_adaptive_${section.intent.id}",
                    onPodcastClick = onPodcastClick,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }
    }
}

@Composable
private fun AdaptiveSectionVisibilityEffect(
    section: ContentSection,
    gridState: LazyStaggeredGridState,
    rowState: androidx.compose.foundation.lazy.LazyListState,
    onAdaptiveSectionVisible: (ContentSection, Set<String>) -> Unit,
) {
    val sectionKey = "adaptive_${section.stableId}"
    val currentOnAdaptiveSectionVisible = rememberUpdatedState(onAdaptiveSectionVisible)
    LaunchedEffect(section, gridState, rowState) {
        snapshotFlow {
            val sectionVisible =
                gridState.layoutInfo.visibleItemsInfo.any {
                    it.key == sectionKey
                }
            if (sectionVisible) {
                rowState.layoutInfo.visibleItemsInfo
                    .mapNotNull { it.key as? String }
                    .toSet()
            } else {
                emptySet()
            }
        }.distinctUntilChanged().collect { visibleCandidateIds ->
            if (visibleCandidateIds.isNotEmpty()) {
                currentOnAdaptiveSectionVisible.value(section, visibleCandidateIds)
            }
        }
    }
}

@Composable
private fun AdaptiveSectionHeader(section: ContentSection) {
    HomeChildSectionHeader(
        title = section.intent.title,
        subtitle = section.intent.subtitle,
        icon = section.intent.icon.toHomeSectionIcon(),
        tone = HomeChildHeaderTone.TERTIARY,
    )
}

private fun String?.toHomeSectionIcon(): ImageVector =
    when (this?.lowercase()) {
        "news" -> Icons.AutoMirrored.Rounded.Article
        "bolt" -> Icons.Rounded.Bolt
        "commute" -> Icons.Rounded.DirectionsCar
        "devices" -> Icons.Rounded.Devices
        "neurology" -> Icons.Rounded.Psychology
        "science" -> Icons.Rounded.Science
        "sentiment_very_satisfied" -> Icons.Rounded.SentimentVerySatisfied
        "sports" -> Icons.Rounded.SportsSoccer
        "history_edu" -> Icons.Rounded.HistoryEdu
        "record_voice_over" -> Icons.Rounded.RecordVoiceOver
        "movie" -> Icons.Rounded.Movie
        "moon" -> Icons.Rounded.NightsStay
        "mystery" -> Icons.Rounded.Search
        "explore" -> Icons.Rounded.Explore
        else -> Icons.Rounded.School
    }

@Composable
private fun AdaptiveCandidateCard(
    candidate: ContentCandidate,
    source: String,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
) {
    val episode = candidate.episode
    val onCandidateClick: () -> Unit = {
        if (episode == null) {
            onPodcastClick(candidate.podcast, source, null, null)
        } else {
            onEpisodeClick?.invoke(episode, candidate.podcast, source)
        }
    }
    if (episode == null) {
        PodcastCard(
            podcast = candidate.podcast,
            onClick = onCandidateClick,
            modifier = Modifier.width(156.dp),
            showGenreChip = false,
        )
    } else {
        CuratedEpisodeCard(
            podcast = candidate.podcast,
            episode = episode,
            onClick = onCandidateClick,
            modifier = Modifier.width(156.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeImportBanner(
    onAiOnboardingClick: () -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
        ) {
            // Dismiss button in top right
            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape,
                        ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(end = 40.dp), // Avoid overlapping with dismiss button
                ) {
                    Text(
                        text = "it's a bit quiet in here...",
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                    Text(
                        text = "let's find your favorite shows so we can build your daily mix and curate better episode recommendations.",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Primary full-width action button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .expressiveClickable(shape = CircleShape, onClick = onAiOnboardingClick),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Find shows with AI",
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                            )
                        }
                    }

                    // Secondary split-width action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .expressiveClickable(shape = CircleShape, onClick = onSearchClick),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Search",
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .expressiveClickable(shape = CircleShape, onClick = onImportClick),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DriveFolderUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Import",
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
