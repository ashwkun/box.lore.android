package cx.aswin.boxlore.navigation

import android.Manifest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import cx.aswin.boxlore.feature.briefing.BriefingRoute
import cx.aswin.boxlore.feature.home.HomeRoute
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch as launchCoroutine

internal fun androidx.navigation.NavGraphBuilder.addOnboardingDestination(w: NavGraphWiring) {
    val navController = w.navController
    val onboardingViewModel = w.session.onboardingViewModel
    val onOnboardingCompleted = w.session.onOnboardingCompleted
    val opmlCallbacks = w.opmlCallbacks

    // -----------------------------------------------------------------------
    // Onboarding
    // -----------------------------------------------------------------------
    composable("onboarding") {
        cx.aswin.boxlore.feature.onboarding.OnboardingScreen(
            viewModel = onboardingViewModel,
            onComplete = {
                onOnboardingCompleted()
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            },
            onBack = { navController.popBackStack() },
            onImportClick = {
                opmlCallbacks.onSourceChange("welcome_screen")
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackOnboardingFlowSelected("import_library", "welcome_screen")
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackImportSheetOpened()
                opmlCallbacks.onImportStateChange(OpmlImportState.ShowSelector)
            },
        )
    }
}

internal fun androidx.navigation.NavGraphBuilder.addHomeDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs
    val queueManager = w.queueManager
    val context = w.context
    val opmlCallbacks = w.opmlCallbacks
    val onboardingViewModel = w.session.onboardingViewModel
    val showFeatureDialog = w.session.showFeatureDialog
    val permissionLauncher = w.session.permissionLauncher
    val onSubmitFeedback = w.actions.onSubmitFeedback

    // -----------------------------------------------------------------------
    // Home
    // -----------------------------------------------------------------------
    composable("home") {
        LaunchedEffect(showFeatureDialog) {
            if (shouldRequestNotificationPermission(showFeatureDialog, context)) {
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackNotificationPermissionRequested()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        HomeRoute(
            podcastRepository = podcastRepository,
            playbackRepository = playbackRepository,
            engagementPromptCoordinator = application.engagementPromptCoordinator,
            subscriptionRepository = subscriptionRepository,
            downloadRepository = downloadRepository,
            rssPodcastRepository = container.rssPodcastRepository,
            adaptiveRankingRepository = container.adaptiveRankingRepository,
            adaptiveCandidateScorer = container.adaptiveCandidateScorer,
            rankingFeedbackRepository = container.rankingFeedbackRepository,
            localCatalog = container.localCatalogPort,
            userPreferencesRepository = userPrefs,
            connectivityStatus = container.connectivityStatus,
            navController = navController,
            onPodcastClick = { podcast, entryPointStr, genreStr, depthVal ->
                navigateHomePodcast(navController, podcast, entryPointStr, genreStr, depthVal)
            },
            onPlayClick = { podcast, bundle ->
                val episode = podcast.latestEpisode ?: return@HomeRoute
                queueManager.playEpisode(episode, podcast, entryPointContext = bundle)
            },
            onHeroArrowClick = { heroItem, carouselPos ->
                navigateHomeHeroArrow(navController, heroItem, carouselPos)
            },
            onEpisodeClick = { episode, podcast, entryPointStr ->
                navigateHomeEpisode(navController, episode, podcast, entryPointStr)
            },
            onNavigateToLibrary = {
                navController.navigate(NavRoutes.LIBRARY_SUBSCRIPTIONS) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onNavigateToLatestEpisodes = {
                navController.navigate("library/subscriptions?tab=1")
            },
            onNavigateToExplore = { category, entryPoint, tab ->
                navigateHomeExplore(navController, category, entryPoint, tab)
            },
            onNavigateToSettings = { navController.navigate("settings?page=hub") },
            onNavigateToPlayStoreReview = { launchInAppReview(context) },
            onSubmitFeedback = onSubmitFeedback,
            onNavigateToDebug = { navController.navigate("debug") },
            onImportClick = {
                opmlCallbacks.onSourceChange("home_import_banner")
                opmlCallbacks.onImportStateChange(OpmlImportState.ShowSelector)
            },
            onAiOnboardingClick = {
                onboardingViewModel.startOnboarding("home_import_banner")
                navController.navigate("onboarding")
            },
            onBriefingClick = { region ->
                navController.navigate("briefing?region=$region")
            },
        )
    }
}

internal fun androidx.navigation.NavGraphBuilder.addLearnDestinations(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val scope = w.scope
    val miniPlayerPadding = w.session.miniPlayerPadding
    val onLoreQueueConflictEpisode = w.actions.onLoreQueueConflictEpisode
    val queueLoreEpisode = w.actions.queueLoreEpisode

    // -----------------------------------------------------------------------
    // Learn
    // -----------------------------------------------------------------------
    composable("learn") {
        val learnHistoryStore = remember {
            cx.aswin.boxlore.feature.explore.LearnCuriosityHistoryStore(application)
        }
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.explore.LearnViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.explore.LearnViewModel(
                        podcastRepository = podcastRepository,
                        application = application,
                        rankingFeedback = container.rankingFeedbackRepository,
                        historyStore = learnHistoryStore,
                    ) as T
            },
        )
        cx.aswin.boxlore.feature.explore.LearnScreen(
            viewModel = viewModel,
            playbackRepository = playbackRepository,
            bottomContentPadding = miniPlayerPadding,
            onNavigateToHistory = { navController.navigate("learn/history") },
            onEpisodeClick = { episode ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                val route = "episode/${episode.id}/${encode(episode.title)}/" +
                    "${encode(episode.description.take(500))}/" +
                    "${encode(episode.imageUrl)}/" +
                    "${encode(episode.audioUrl)}/" +
                    "${episode.duration}/${encode(episode.podcastId ?: "learn")}/" +
                    "${encode(episode.podcastTitle ?: "Podcast")}?entryPoint=learn"
                navController.navigate(route)
            },
            onQueueEpisode = { episode ->
                scope.launchCoroutine {
                    try {
                        if (playbackRepository.hasNonLoreQueue()) {
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackLoreQueueConflictShown(
                                episodeId = episode.id,
                                normalQueueSize = playbackRepository.playerState.value.queue.size,
                            )
                            onLoreQueueConflictEpisode(episode)
                        } else {
                            queueLoreEpisode(episode)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BoxLoreNavHost", "Failed to queue Lore episode", e)
                    }
                }
            },
            onPodcastClick = { feedId, _, _, _ ->
                val pId = feedId?.toString() ?: ""
                navController.navigate("podcast/${android.net.Uri.encode(pId)}?entryPoint=learn")
            },
        )
    }

    composable("learn/history") {
        val learnHistoryStore = remember {
            cx.aswin.boxlore.feature.explore.LearnCuriosityHistoryStore(application)
        }
        val historyViewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.explore.LearnHistoryViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.explore.LearnHistoryViewModel(
                        application = application,
                        historyStore = learnHistoryStore,
                    ) as T
            },
        )
        cx.aswin.boxlore.feature.explore.LearnHistoryScreen(
            viewModel = historyViewModel,
            bottomContentPadding = miniPlayerPadding,
            onBack = { navController.popBackStack() },
            onEpisodeClick = { episode ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                val route = "episode/${episode.id}/${encode(episode.title)}/" +
                    "${encode(episode.description.take(500))}/" +
                    "${encode(episode.imageUrl)}/" +
                    "${encode(episode.audioUrl)}/" +
                    "${episode.duration}/${encode(episode.podcastId ?: "learn")}/" +
                    "${encode(episode.podcastTitle ?: "Podcast")}?entryPoint=learn_history"
                navController.navigate(route)
            },
        )
    }
}

internal fun androidx.navigation.NavGraphBuilder.addBriefingDestination(w: NavGraphWiring) {
    val navController = w.navController
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val queueManager = w.queueManager
    val miniPlayerPadding = w.session.miniPlayerPadding
    val onShowFeedbackSheet = w.actions.onShowFeedbackSheet

    // -----------------------------------------------------------------------
    // Briefing
    // -----------------------------------------------------------------------
    composable(
        route = "briefing?region={region}",
        arguments = listOf(
            navArgument("region") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val region = backStackEntry.arguments?.getString("region")
        BriefingRoute(
            podcastRepository = podcastRepository,
            playbackRepository = playbackRepository,
            queueManager = queueManager,
            initialRegion = region,
            onBackClick = { navController.popBackStack() },
            onFeedbackClick = { onShowFeedbackSheet() },
            bottomContentPadding = miniPlayerPadding,
            onEpisodeClick = { episode ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                val route = "episode/${episode.id}/${encode(episode.title)}/" +
                    "${encode(episode.description.take(500))}/" +
                    "${encode(episode.imageUrl)}/" +
                    "${encode(episode.audioUrl)}/" +
                    "${episode.duration}/${encode(episode.podcastId ?: "briefing")}/" +
                    "${encode(episode.podcastTitle ?: "Podcast")}?entryPoint=briefing"
                navController.navigate(route)
            },
        )
    }
}

internal fun androidx.navigation.NavGraphBuilder.addExploreDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs

    // -----------------------------------------------------------------------
    // Explore
    // -----------------------------------------------------------------------
    composable(
        route = "explore?category={category}&entryPoint={entryPoint}&tab={tab}",
        arguments = listOf(
            navArgument("category") {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument("entryPoint") {
                type = NavType.StringType; nullable = true; defaultValue = "bottom_nav"
            },
            navArgument("tab") {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val category = backStackEntry.arguments?.getString("category")
        val entryPoint = backStackEntry.arguments?.getString("entryPoint") ?: "bottom_nav"
        val tab = backStackEntry.arguments?.getString("tab")

        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.explore.ExploreViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.explore.ExploreViewModel(
                        application = application,
                        podcastRepository = podcastRepository,
                        subscriptionRepository = subscriptionRepository,
                        userPrefs = userPrefs,
                        playbackRepository = playbackRepository,
                        adaptiveScorer = container.adaptiveCandidateScorer,
                        initialCategory = category,
                        initialTab = tab,
                    ) as T
            },
        )

        cx.aswin.boxlore.feature.explore.ExploreScreen(
            viewModel = viewModel,
            entryPoint = entryPoint,
            onPodcastClick = { podcastId, entryPointStr, genreStr, depthVal ->
                var route = "podcast/$podcastId"
                val params = mutableListOf<String>()
                params.add("entryPoint=$entryPointStr")
                if (genreStr != null) params.add("genre=$genreStr")
                if (depthVal != null) params.add("depth=$depthVal")
                if (params.isNotEmpty()) route += "?" + params.joinToString("&")
                navController.navigate(route)
            },
            onEpisodeClick = { episode, podcast ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                navController.navigate(
                    "episode/${encode(episode.id)}/${encode(episode.title)}/" +
                        "${encode(episode.description.take(500))}/" +
                        "${encode(episode.imageUrl)}/" +
                        "${encode(episode.audioUrl)}/" +
                        "${episode.duration}/${encode(podcast.id)}/" +
                        "${encode(podcast.title)}" +
                        "?entryPoint=explore_for_you",
                )
            },
            onNavigateToRegionSettings = { navController.navigate("settings?page=library") },
        )
    }
}
