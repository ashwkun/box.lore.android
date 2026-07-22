package cx.aswin.boxlore.feature.home

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.components.ChangeRecommendationPodcastSheet
import cx.aswin.boxlore.feature.home.components.LocalLastSeenEpisodes
import cx.aswin.boxlore.feature.home.components.TopControlBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
data class StableEditorialRowList(
    val list: List<HomeEditorialRow>,
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
    podcastRepository: cx.aswin.boxlore.core.catalog.PodcastRepository,
    playbackRepository: cx.aswin.boxlore.core.playback.PlaybackRepository,
    engagementPromptCoordinator: cx.aswin.boxlore.core.catalog.EngagementPromptCoordinator,
    subscriptionRepository: cx.aswin.boxlore.core.catalog.SubscriptionRepository,
    downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository,
    rssPodcastRepository: cx.aswin.boxlore.core.rss.RssPodcastRepository,
    adaptiveCandidateScorer: cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer,
    localCatalog: cx.aswin.boxlore.core.domain.ports.LocalCatalogPort,
    userPreferencesRepository: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
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
                            adaptiveScorer = adaptiveCandidateScorer,
                            localCatalog = localCatalog,
                            userPreferencesRepository = userPreferencesRepository,
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
        content =
            PodcastFeedContent(
                heroItems = StableHeroList(uiState.heroItems),
                latestItems = StablePodcastList(uiState.latestEpisodes),
                subscribedItems = StablePodcastList(uiState.subscribedPodcasts),
                editorialRows = StableEditorialRowList(uiState.editorialRows),
                gridItems = StablePodcastList(uiState.discoverPodcasts),
                recommendations = StableEpisodeList(uiState.recommendations),
                selectedPodcastEpisodes = StableEpisodeList(uiState.selectedPodcastEpisodes),
            ),
        feedState =
            PodcastFeedUiState(
                discoveryGreeting = uiState.discoveryGreeting,
                selectedCategory = uiState.selectedCategory,
                selectedPodcastId = uiState.selectedPodcastId,
                briefing = uiState.briefing,
                briefingChapters = uiState.briefingChapters,
                seemsToLikePodcast = uiState.seemsToLikePodcast,
                showImportBanner = uiState.showImportBanner,
            ),
        recommendationState =
            PodcastFeedRecommendationState(
                becauseYouLikeRecommendations = StableEpisodeList(uiState.becauseYouLikeRecommendations),
                becauseYouLikePodcasts = StablePodcastList(uiState.becauseYouLikePodcasts),
                isRecommendationsLoading = uiState.isRecommendationsLoading,
                isRecommendationsFallback = uiState.isRecommendationsFallback,
                onChangePodcastClick = onChangePodcastClick,
            ),
        loadingState =
            PodcastFeedLoadingState(
                isEditorialRowsLoading = uiState.isEditorialRowsLoading,
                isFilterLoading = uiState.isFilterLoading,
                isSelectedPodcastLoading = uiState.isSelectedPodcastLoading,
                isSelectedRssRefreshing = uiState.isSelectedRssRefreshing,
                isLoading = uiState.isLoading,
            ),
        playback =
            PodcastFeedPlayback(
                player = playback,
                episodePlaybackState = StablePlaybackStateMap(uiState.episodePlaybackState),
            ),
        callbacks = callbacks,
        layout = PodcastFeedLayout(gridState = gridState),
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
