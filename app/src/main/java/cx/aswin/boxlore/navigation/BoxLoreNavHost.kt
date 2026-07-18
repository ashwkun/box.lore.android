package cx.aswin.boxlore.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.BoxLoreApplication
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.briefing.BriefingRoute
import cx.aswin.boxlore.feature.home.HomeRoute
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// PixelPlayer-inspired transition specs (same values as MainActivity)
private const val TRANSITION_DURATION = 350
private val TRANSITION_EASING = FastOutSlowInEasing

// ---------------------------------------------------------------------------
// Navigation route constants
// ---------------------------------------------------------------------------

/** Nav graph route pattern for the Explore tab root. */
internal const val ExploreTabRoutePattern =
    "explore?category={category}&entryPoint={entryPoint}&tab={tab}"

/** Concrete explore navigation target used by the bottom-nav tab. */
internal const val ExploreBottomNavRoute = "explore?entryPoint=bottom_nav"

/** File-local navigation path constants (exact string targets / prefixes). */
private object NavRoutes {
    const val LIBRARY_DOWNLOADS = "library/downloads"
    const val LIBRARY_SUBSCRIPTIONS = "library/subscriptions"
    const val LIBRARY_DOWNLOADS_SETTINGS = "library/downloads/settings"
}

internal fun bottomNavTabRoutePattern(tab: String): String? = when (tab) {
    "home" -> "home"
    "learn" -> "learn"
    "explore" -> ExploreTabRoutePattern
    "library" -> "library"
    else -> null
}

/**
 * Resolves which bottom-nav tab owns the current screen.
 * Overlays like settings/debug/podcast detail inherit the tab beneath them.
 */
internal fun resolveBottomNavTab(
    currentRoute: String?,
    backStack: List<androidx.navigation.NavBackStackEntry>,
): String {
    val route = currentRoute ?: return "home"
    return when {
        route == "home" -> "home"
        route.startsWith("learn") -> "learn"
        route.startsWith("explore") -> "explore"
        route.startsWith("library") -> "library"
        route.startsWith("settings") ||
            route.startsWith("debug") ||
            route.startsWith("podcast") ||
            route.startsWith("episode") ||
            route.startsWith("briefing") -> resolveBottomNavTabFromBackStack(backStack)
        else -> "home"
    }
}

/** Overlay screens inherit the tab of the nearest bottom-nav entry beneath them. */
internal fun resolveBottomNavTabFromBackStack(
    backStack: List<androidx.navigation.NavBackStackEntry>,
): String {
    for (i in backStack.size - 2 downTo 0) {
        val prior = backStack.getOrNull(i)?.destination?.route ?: continue
        when {
            prior.startsWith("learn") -> return "learn"
            prior.startsWith("explore") -> return "explore"
            prior.startsWith("library") -> return "library"
            prior == "home" -> return "home"
        }
    }
    return "home"
}

// ---------------------------------------------------------------------------
// Settings state grouping (reduces parameter count on BoxLoreNavHost)
// ---------------------------------------------------------------------------

/** Read-only snapshot of user-preference values needed by the settings route. */
data class NavSettingsState(
    val currentRegion: String,
    val themeConfig: String,
    val useDynamicColor: Boolean,
    val themeBrand: String,
    val surfaceStyle: String,
    val skipBehavior: String,
    val skipBeginningMs: Long,
    val skipEndingMs: Long,
    val seekBackwardMs: Long,
    val seekForwardMs: Long,
    val hideCompletedInHome: Boolean,
    val hideCompletedInSubs: Boolean,
    val hideCompletedInShowDetails: Boolean,
)

/** Callbacks for OPML import state owned by MainActivity. */
data class NavOpmlCallbacks(
    val importState: OpmlImportState,
    val onImportStateChange: (OpmlImportState) -> Unit,
    val triggerKey: Long,
    val onTriggerKeyChange: (Long) -> Unit,
    val onSourceChange: (String) -> Unit,
    val performJsonImport: (android.net.Uri) -> Unit,
)

/** Activity-owned session flags and objects needed by multiple destinations. */
data class NavHostSession(
    val onboardingCompleted: Boolean,
    val onOnboardingCompleted: () -> Unit,
    val onboardingViewModel: cx.aswin.boxlore.feature.onboarding.OnboardingViewModel,
    val hasDeepLink: Boolean,
    val currentEpisode: Episode?,
    val miniPlayerPadding: androidx.compose.ui.unit.Dp,
    val showFeatureDialog: Boolean,
    val hasSeenMarkPlayedTip: Boolean,
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    val appInstanceId: String?,
)

/** Activity-owned action callbacks used across nav destinations. */
data class NavHostActions(
    val onLoreQueueConflictEpisode: (Episode) -> Unit,
    val queueLoreEpisode: (Episode) -> Unit,
    val onShowFeedbackSheet: () -> Unit,
    val onSubmitFeedback: suspend (String, String, String, String) -> Boolean,
)

/** Internal wiring bag shared by NavGraphBuilder destination helpers. */
private class NavGraphWiring(
    val navController: NavHostController,
    val application: BoxLoreApplication,
    val session: NavHostSession,
    val actions: NavHostActions,
    val opmlCallbacks: NavOpmlCallbacks,
    val settingsState: NavSettingsState,
    val scope: kotlinx.coroutines.CoroutineScope,
    val context: android.content.Context,
    val isOnline: Boolean,
    val isSyncingSmartDownloads: androidx.compose.runtime.MutableState<Boolean>,
) {
    val container get() = application.container
    val database get() = container.database
    val podcastRepository get() = container.podcastRepository
    val playbackRepository get() = container.playbackRepository
    val downloadRepository get() = container.downloadRepository
    val subscriptionRepository get() = container.subscriptionRepository
    val userPrefs get() = container.userPreferencesRepository
    val queueManager get() = container.queueManager
    val smartDownloadManager get() = container.smartDownloadManager
}

// ---------------------------------------------------------------------------
// Route-index + transition helpers
// ---------------------------------------------------------------------------

private fun getRouteIndex(route: String?): Int {
    if (route == null) return 0
    if (route == "home") return 0
    if (route.startsWith("learn")) return 1
    if (route.startsWith("explore")) return 2
    if (route == "library" || route.startsWith(NavRoutes.LIBRARY_SUBSCRIPTIONS)) return 3
    if (route.startsWith("podcast/")) return 10
    if (route.startsWith("episode/")) return 11
    if (route.startsWith("briefing")) return 11
    if (route.startsWith("library/")) return 12
    return 0
}

private fun isTabToTab(fromRoute: String?, toRoute: String?): Boolean {
    val fromIndex = getRouteIndex(fromRoute)
    val toIndex = getRouteIndex(toRoute)
    return fromIndex < 10 && toIndex < 10
}

private fun navEnterTransition(
    fromRoute: String?,
    toRoute: String?,
): androidx.compose.animation.EnterTransition {
    val fromIndex = getRouteIndex(fromRoute)
    val toIndex = getRouteIndex(toRoute)
    return if (toIndex > fromIndex) {
        slideInHorizontally(
            animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
            initialOffsetX = { it },
        )
    } else {
        slideInHorizontally(
            animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
            initialOffsetX = { -it },
        )
    }
}

private fun navExitTransition(
    fromRoute: String?,
    toRoute: String?,
): androidx.compose.animation.ExitTransition {
    val fromIndex = getRouteIndex(fromRoute)
    val toIndex = getRouteIndex(toRoute)
    return if (isTabToTab(fromRoute, toRoute)) {
        if (toIndex > fromIndex) {
            slideOutHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { -it }
        } else {
            slideOutHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { it }
        }
    } else if (toIndex > fromIndex) {
        slideOutHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { -it / 3 } +
            fadeOut(tween(TRANSITION_DURATION, easing = TRANSITION_EASING))
    } else {
        slideOutHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { it / 3 } +
            fadeOut(tween(TRANSITION_DURATION, easing = TRANSITION_EASING))
    }
}

private fun navPopEnterTransition(
    fromRoute: String?,
    toRoute: String?,
): androidx.compose.animation.EnterTransition {
    val fromIndex = getRouteIndex(fromRoute)
    val toIndex = getRouteIndex(toRoute)
    return if (isTabToTab(fromRoute, toRoute)) {
        if (toIndex > fromIndex) {
            slideInHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { it }
        } else {
            slideInHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { -it }
        }
    } else if (toIndex > fromIndex) {
        slideInHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { it } +
            fadeIn(tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))
    } else {
        slideInHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { -it / 3 } +
            fadeIn(tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))
    }
}

private fun navPopExitTransition(
    fromRoute: String?,
    toRoute: String?,
): androidx.compose.animation.ExitTransition {
    val fromIndex = getRouteIndex(fromRoute)
    val toIndex = getRouteIndex(toRoute)
    return if (isTabToTab(fromRoute, toRoute)) {
        if (toIndex > fromIndex) {
            slideOutHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { -it }
        } else {
            slideOutHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { it }
        }
    } else if (toIndex > fromIndex) {
        slideOutHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { -it } +
            fadeOut(tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))
    } else {
        slideOutHorizontally(tween(TRANSITION_DURATION, easing = TRANSITION_EASING)) { it } +
            fadeOut(tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))
    }
}

// ---------------------------------------------------------------------------
// Navigation argument / route helpers (keeps destination methods under Sonar S3776)
// ---------------------------------------------------------------------------

private fun encodeNavArg(s: String?): String =
    android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")

private fun decodeNavArg(s: String?): String = try {
    android.net.Uri.decode(s ?: "").let { if (it == "_") "" else it }
} catch (_: Exception) {
    s ?: ""
}

private fun episodeFullPathRoute(
    episodeId: String,
    title: String?,
    description: String?,
    imageUrl: String?,
    audioUrl: String?,
    duration: Int,
    podcastId: String?,
    podcastTitle: String?,
    querySuffix: String = "",
): String =
    "episode/$episodeId/${encodeNavArg(title)}/" +
        "${encodeNavArg(description?.take(500))}/" +
        "${encodeNavArg(imageUrl)}/" +
        "${encodeNavArg(audioUrl)}/" +
        "$duration/${encodeNavArg(podcastId)}/" +
        "${encodeNavArg(podcastTitle)}" +
        querySuffix

private fun podcastDetailRoute(
    podcastId: String,
    entryPoint: String,
    genre: String? = null,
    depth: Int? = null,
): String {
    val params = buildList {
        add("entryPoint=$entryPoint")
        if (genre != null) add("genre=$genre")
        if (depth != null) add("depth=$depth")
    }
    return "podcast/$podcastId?" + params.joinToString("&")
}

private fun exploreRoute(category: String?, entryPoint: String, tab: String?): String {
    val catQuery = if (category != null) "category=$category&" else ""
    val tabQuery = if (tab != null) "tab=$tab&" else ""
    return "explore?${catQuery}${tabQuery}entryPoint=$entryPoint"
}

private fun entryPointBundle(
    entryPoint: String?,
    vibeId: String = "",
    carouselPosition: Int = -1,
): android.os.Bundle? {
    if (entryPoint == null) return null
    return android.os.Bundle().apply {
        putString("entry_point", entryPoint)
        if (vibeId.isNotEmpty()) putString("curated_vibe_id", vibeId)
        if (carouselPosition >= 0) putInt("curated_carousel_position", carouselPosition)
    }
}

private fun miniPlayerBottomPadding(isPlayerVisible: Boolean): androidx.compose.ui.unit.Dp =
    if (isPlayerVisible) {
        AppNavigationBarHeight + 64.dp + 2.dp
    } else {
        AppNavigationBarHeight
    }

private fun shouldRequestNotificationPermission(
    showFeatureDialog: Boolean,
    context: android.content.Context,
): Boolean =
    !showFeatureDialog &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

private fun launchInAppReview(context: android.content.Context) {
    val activity = context as? androidx.activity.ComponentActivity ?: return
    val reviewManager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
    reviewManager.requestReviewFlow().addOnCompleteListener { task ->
        if (task.isSuccessful) {
            reviewManager.launchReviewFlow(activity, task.result)
        }
    }
}

private fun navigateHomePodcast(
    navController: NavHostController,
    podcast: cx.aswin.boxlore.core.model.Podcast,
    entryPointStr: String,
    genreStr: String?,
    depthVal: Int?,
) {
    navController.navigate(podcastDetailRoute(podcast.id, entryPointStr, genreStr, depthVal))
}

private fun navigateHomeHeroArrow(
    navController: NavHostController,
    heroItem: cx.aswin.boxlore.feature.home.SmartHeroItem,
    carouselPos: Int,
) {
    val ep = heroItem.podcast.latestEpisode
    if (ep == null) {
        navController.navigate("podcast/${android.net.Uri.encode(heroItem.podcast.id)}")
        return
    }
    val entryPointStr = "home_hero_${heroItem.type.name.lowercase()}"
    navController.navigate(
        episodeFullPathRoute(
            episodeId = ep.id,
            title = ep.title,
            description = ep.description,
            imageUrl = ep.imageUrl,
            audioUrl = ep.audioUrl,
            duration = ep.duration,
            podcastId = heroItem.podcast.id,
            podcastTitle = heroItem.podcast.title,
            querySuffix = "?entryPoint=$entryPointStr&carouselPosition=$carouselPos",
        ),
    )
}

private fun navigateHomeEpisode(
    navController: NavHostController,
    episode: Episode,
    podcast: cx.aswin.boxlore.core.model.Podcast,
    entryPointStr: String?,
) {
    val entryPointQuery = if (entryPointStr != null) "?entryPoint=$entryPointStr" else ""
    navController.navigate(
        episodeFullPathRoute(
            episodeId = encodeNavArg(episode.id),
            title = episode.title,
            description = episode.description,
            imageUrl = episode.imageUrl,
            audioUrl = episode.audioUrl,
            duration = episode.duration,
            podcastId = podcast.id,
            podcastTitle = podcast.title,
            querySuffix = entryPointQuery,
        ),
    )
}

private fun navigateHomeExplore(
    navController: NavHostController,
    category: String?,
    entryPoint: String,
    tab: String?,
) {
    navController.navigate(exploreRoute(category, entryPoint, tab)) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun podcastIdFromInfoUiState(
    state: cx.aswin.boxlore.feature.info.PodcastInfoUiState,
    fallback: String,
): String =
    (state as? cx.aswin.boxlore.feature.info.PodcastInfoUiState.Success)?.podcast?.id ?: fallback

private fun podcastTitleFromInfoUiState(
    state: cx.aswin.boxlore.feature.info.PodcastInfoUiState,
    fallback: String,
): String =
    (state as? cx.aswin.boxlore.feature.info.PodcastInfoUiState.Success)?.podcast?.title ?: fallback

private fun navigatePodcastInfoEpisode(
    navController: NavHostController,
    episode: Episode,
    entryPointStr: String,
    index: Int?,
    podcastId: String,
    podcastTitle: String,
) {
    var route = episodeFullPathRoute(
        episodeId = episode.id,
        title = episode.title,
        description = episode.description,
        imageUrl = episode.imageUrl,
        audioUrl = episode.audioUrl,
        duration = episode.duration,
        podcastId = podcastId,
        podcastTitle = podcastTitle,
        querySuffix = "?entryPoint=$entryPointStr",
    )
    if (index != null) route += "&carouselPosition=$index"
    navController.navigate(route)
}

private fun navigateRelatedEpisode(
    navController: NavHostController,
    ep: Episode,
    fallbackPodcastId: String,
    fallbackPodcastTitle: String,
) {
    val targetPodcastId = ep.podcastId?.takeIf { it.isNotEmpty() } ?: fallbackPodcastId
    val targetPodcastTitle = ep.podcastTitle?.takeIf { it.isNotEmpty() } ?: fallbackPodcastTitle
    navController.navigate(
        episodeFullPathRoute(
            episodeId = ep.id,
            title = ep.title,
            description = ep.description,
            imageUrl = ep.imageUrl,
            audioUrl = ep.audioUrl,
            duration = ep.duration,
            podcastId = targetPodcastId,
            podcastTitle = targetPodcastTitle,
            querySuffix = "?entryPoint=episode_related_episodes",
        ),
    )
}

private fun briefingRegionFromPodcastId(podcastId: String): String? {
    if (!podcastId.startsWith("briefing_")) return null
    return podcastId.removePrefix("briefing_")
}

private fun briefingRegionFromEpisodeId(episodeId: String): String? {
    if (!episodeId.startsWith("briefing_")) return null
    return episodeId.removePrefix("briefing_").substringBefore("_")
}

private suspend fun resolveLocalOrFallbackPodcast(
    localCatalog: cx.aswin.boxlore.core.domain.ports.LocalCatalogPort,
    podcastId: String,
    podcastTitle: String,
    fallbackImageUrl: String,
): cx.aswin.boxlore.core.model.Podcast {
    val local = localCatalog.getLocalPodcast(podcastId)
    return local?.let {
        cx.aswin.boxlore.core.model.Podcast(
            id = it.id,
            title = it.title,
            artist = it.artist,
            imageUrl = it.imageUrl,
        )
    } ?: cx.aswin.boxlore.core.model.Podcast(
        id = podcastId,
        title = podcastTitle,
        artist = "",
        imageUrl = fallbackImageUrl,
    )
}

private suspend fun autoplayDeepLinkEpisodeIfNeeded(
    playbackRepository: cx.aswin.boxlore.core.data.PlaybackRepository,
    queueManager: cx.aswin.boxlore.core.data.QueueManager,
    localCatalog: cx.aswin.boxlore.core.domain.ports.LocalCatalogPort,
    episodeId: String,
    autoplay: String,
    t: Long?,
    start: Long?,
    success: cx.aswin.boxlore.feature.info.EpisodeInfoUiState.Success,
) {
    if (success.episode.id != episodeId) return
    val playerState = playbackRepository.playerState.value
    if (autoplay == "true" && playerState.currentEpisode?.id != episodeId) {
        val podcast = resolveLocalOrFallbackPodcast(
            localCatalog = localCatalog,
            podcastId = success.podcastId,
            podcastTitle = success.podcastTitle,
            fallbackImageUrl = success.episode.podcastImageUrl ?: "",
        )
        queueManager.playEpisode(success.episode, podcast)
    }
    when {
        t != null && t > 0L -> playbackRepository.seekTo(t * 1000L, play = autoplay == "true")
        start != null && start > 0L -> playbackRepository.seekTo(start * 1000L, play = autoplay == "true")
    }
}

@Composable
private fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    var isOnline by remember {
        mutableStateOf(
            try {
                val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as? android.net.ConnectivityManager
                val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } catch (_: Exception) {
                true
            },
        )
    }

    androidx.compose.runtime.DisposableEffect(context) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                isOnline = true
            }
            override fun onLost(network: android.net.Network) {
                val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                isOnline = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
        }
        try {
            cm?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) { /* ignore */ }
        onDispose {
            try { cm?.unregisterNetworkCallback(callback) } catch (_: Exception) { /* ignore */ }
        }
    }
    return isOnline
}

// ---------------------------------------------------------------------------
// BoxLoreNavHost
// ---------------------------------------------------------------------------

/**
 * Application nav graph.
 *
 * Owns all composable route definitions. State that must survive navigation
 * (OPML import progress, lore-queue conflict, etc.) is owned by the calling
 * Activity and passed in via callbacks so dialogs drawn above the NavHost
 * can also react to it.
 *
 * Routes:
 *  - onboarding
 *  - home
 *  - learn / learn/history
 *  - briefing
 *  - settings
 *  - debug
 *  - explore
 *  - library / library/history / library/liked / library/subscriptions
 *  - library/downloads / library/downloads/settings / library/downloads/show
 *  - library/auto_downloads/settings
 *  - podcast/{podcastId}  (+ deep links: boxlore://, boxcast://, https://aswin.cx/...)
 *  - episode/{episodeId}/...  (full path + deep links)
 *  - episode/{episodeId}      (simplified deep-link route)
 */
@Composable
fun BoxLoreNavHost(
    navController: NavHostController,
    application: BoxLoreApplication,
    session: NavHostSession,
    opmlCallbacks: NavOpmlCallbacks,
    actions: NavHostActions,
    settingsState: NavSettingsState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isOnline = rememberIsOnline()
    val isSyncingSmartDownloads = remember { mutableStateOf(false) }

    val isOfflineOnLaunch = remember { !isOnline }
    val computedStartDestination = remember {
        when {
            !session.onboardingCompleted -> "onboarding"
            isOfflineOnLaunch && !session.hasDeepLink -> NavRoutes.LIBRARY_DOWNLOADS
            else -> "home"
        }
    }

    val wiring = NavGraphWiring(
        navController = navController,
        application = application,
        session = session,
        actions = actions,
        opmlCallbacks = opmlCallbacks,
        settingsState = settingsState,
        scope = scope,
        context = context,
        isOnline = isOnline,
        isSyncingSmartDownloads = isSyncingSmartDownloads,
    )

    NavHost(
        navController = navController,
        startDestination = computedStartDestination,
        modifier = androidx.compose.ui.Modifier,
        enterTransition = {
            navEnterTransition(initialState.destination.route, targetState.destination.route)
        },
        exitTransition = {
            navExitTransition(initialState.destination.route, targetState.destination.route)
        },
        popEnterTransition = {
            navPopEnterTransition(initialState.destination.route, targetState.destination.route)
        },
        popExitTransition = {
            navPopExitTransition(initialState.destination.route, targetState.destination.route)
        },
    ) {
        addOnboardingDestination(wiring)
        addHomeDestination(wiring)
        addLearnDestinations(wiring)
        addBriefingDestination(wiring)
        addSettingsDestination(wiring)
        addDebugDestination(wiring)
        addExploreDestination(wiring)
        addLibraryDestinations(wiring)
        addPodcastDestination(wiring)
        addEpisodeFullPathDestination(wiring)
        addEpisodeDeepLinkDestination(wiring)
    }
}


private fun androidx.navigation.NavGraphBuilder.addOnboardingDestination(w: NavGraphWiring) {
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

private fun androidx.navigation.NavGraphBuilder.addHomeDestination(w: NavGraphWiring) {
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

private fun androidx.navigation.NavGraphBuilder.addLearnDestinations(w: NavGraphWiring) {
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
                scope.launch {
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

private fun androidx.navigation.NavGraphBuilder.addBriefingDestination(w: NavGraphWiring) {
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

private fun androidx.navigation.NavGraphBuilder.addSettingsDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs
    val scope = w.scope
    val opmlCallbacks = w.opmlCallbacks
    val settingsState = w.settingsState
    val appInstanceId = w.session.appInstanceId

    // -----------------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------------
    composable(
        route = "settings?page={page}",
        arguments = listOf(
            navArgument("page") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val settingsPage = backStackEntry.arguments?.getString("page")

        fun trackAndPersistPlaybackDuration(
            eventName: String,
            value: Long,
            persist: suspend (Long) -> Unit,
        ) {
            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                .trackSettingsInteraction(eventName, value.toString())
            scope.launch { persist(value) }
        }

        cx.aswin.boxlore.feature.home.settings.SettingsScreen(
            repositories = cx.aswin.boxlore.feature.home.settings.SettingsRepositories(
                rssPodcastRepository = container.rssPodcastRepository,
                rankingFeedbackRepository = container.rankingFeedbackRepository,
            ),
            config = cx.aswin.boxlore.feature.home.settings.SettingsScreenConfig(
                onBack = { navController.popBackStack() },
                onResetAnalytics = {
                    try {
                        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.resetIdentity()
                    } catch (e: Exception) {
                        android.util.Log.e("Settings", "Failed to reset analytics", e)
                    }
                },
                appInstanceId = appInstanceId,
                initialPage = settingsPage,
            ),
            regionSettings = cx.aswin.boxlore.feature.home.settings.RegionSettings(
                currentRegion = settingsState.currentRegion,
                onSetRegion = { region -> scope.launch { userPrefs.setRegion(region) } },
            ),
            appearanceSettings = cx.aswin.boxlore.feature.home.settings.AppearanceSettings(
                state = cx.aswin.boxlore.feature.home.settings.pages.AppearanceUiState(
                    currentThemeConfig = settingsState.themeConfig,
                    isDynamicColorEnabled = settingsState.useDynamicColor,
                    currentThemeBrand = settingsState.themeBrand,
                    currentSurfaceStyle = settingsState.surfaceStyle,
                ),
                actions = cx.aswin.boxlore.feature.home.settings.pages.AppearanceActions(
                    onSetThemeConfig = { config -> scope.launch { userPrefs.setThemeConfig(config) } },
                    onToggleDynamicColor = { enabled -> scope.launch { userPrefs.setUseDynamicColor(enabled) } },
                    onSetThemeBrand = { brand -> scope.launch { userPrefs.setThemeBrand(brand) } },
                    onSetSurfaceStyle = { style -> scope.launch { userPrefs.setSurfaceStyle(style) } },
                ),
            ),
            playbackSettings = cx.aswin.boxlore.feature.home.settings.PlaybackSettings(
                state = cx.aswin.boxlore.feature.home.settings.pages.PlaybackUiState(
                    skipBehavior = settingsState.skipBehavior,
                    skipBeginningMs = settingsState.skipBeginningMs,
                    skipEndingMs = settingsState.skipEndingMs,
                    seekBackwardMs = settingsState.seekBackwardMs,
                    seekForwardMs = settingsState.seekForwardMs,
                    hideCompletedInHome = settingsState.hideCompletedInHome,
                    hideCompletedInSubs = settingsState.hideCompletedInSubs,
                    hideCompletedInShowDetails = settingsState.hideCompletedInShowDetails,
                ),
                actions = cx.aswin.boxlore.feature.home.settings.pages.PlaybackActions(
                    onSetSkipBehavior = { behavior -> scope.launch { userPrefs.setSkipBehavior(behavior) } },
                    onSetSkipBeginningMs = { value ->
                        trackAndPersistPlaybackDuration("skip_beginning_changed", value, userPrefs::setSkipBeginningMs)
                    },
                    onSetSkipEndingMs = { value ->
                        trackAndPersistPlaybackDuration("skip_ending_changed", value, userPrefs::setSkipEndingMs)
                    },
                    onSetSeekBackwardMs = { value ->
                        trackAndPersistPlaybackDuration("seek_backward_changed", value, userPrefs::setSeekBackwardMs)
                    },
                    onSetSeekForwardMs = { value ->
                        trackAndPersistPlaybackDuration("seek_forward_changed", value, userPrefs::setSeekForwardMs)
                    },
                    onSetHideCompletedInHome = { hide -> scope.launch { userPrefs.setHideCompletedInHome(hide) } },
                    onSetHideCompletedInSubs = { hide -> scope.launch { userPrefs.setHideCompletedInSubs(hide) } },
                    onSetHideCompletedInShowDetails = { hide -> scope.launch { userPrefs.setHideCompletedInShowDetails(hide) } },
                ),
            ),
            libraryBackupWriters = cx.aswin.boxlore.feature.home.settings.LibraryBackupWriters(
                onExportJson = { uri ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            val backupJson = cx.aswin.boxlore.core.data.backup.LibraryBackupManager(
                                subscriptionRepository, playbackRepository, podcastRepository, userPrefs, application,
                            ).exportLibraryAsJson()
                            (application.contentResolver.openOutputStream(uri)
                                ?: error("Unable to open export destination")).use { it.write(backupJson.toByteArray()) }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(application, "Library Exported Successfully", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(application, "Failed to export: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onExportOpml = { uri ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            val opmlXml = cx.aswin.boxlore.core.data.backup.LibraryBackupManager(
                                subscriptionRepository, playbackRepository, podcastRepository, context = application,
                            ).exportLibraryAsOpml()
                            (application.contentResolver.openOutputStream(uri)
                                ?: error("Unable to open export destination")).use { it.write(opmlXml.toByteArray()) }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(application, "Subscriptions Exported as OPML", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(application, "Failed to export OPML: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onImportJson = { uri -> opmlCallbacks.performJsonImport(uri) },
                onImportOpml = { uri ->
                    opmlCallbacks.onImportStateChange(OpmlImportState.Parsing(uri))
                    opmlCallbacks.onTriggerKeyChange(System.currentTimeMillis())
                },
            ),
            downloadsNavigation = cx.aswin.boxlore.feature.home.settings.DownloadsNavigation(
                onNavigateToSmartDownloads = { navController.navigate(NavRoutes.LIBRARY_DOWNLOADS_SETTINGS) },
                onNavigateToAutoDownloads = { navController.navigate("library/auto_downloads/settings") },
            ),
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.addDebugDestination(w: NavGraphWiring) {
    val navController = w.navController
    val container = w.container
    val playbackRepository = w.playbackRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs

    // -----------------------------------------------------------------------
    // Debug
    // -----------------------------------------------------------------------
    composable("debug") {
        cx.aswin.boxlore.feature.home.DebugScreen(
            playbackRepository = playbackRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPrefs,
            adaptiveRankingRepository = container.adaptiveRankingRepository,
            onBack = { navController.popBackStack() },
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.addExploreDestination(w: NavGraphWiring) {
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

private fun androidx.navigation.NavGraphBuilder.addLibraryDestinations(w: NavGraphWiring) {
    val navController = w.navController
    val container = w.container
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs
    val smartDownloadManager = w.smartDownloadManager
    val queueManager = w.queueManager
    val scope = w.scope
    val currentEpisode = w.session.currentEpisode
    val isOnline = w.isOnline
    var isSyncingSmartDownloads by w.isSyncingSmartDownloads

    // -----------------------------------------------------------------------
    // Library
    // -----------------------------------------------------------------------
    composable("library") {
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                    ) as T
            },
        )
        cx.aswin.boxlore.feature.library.LibraryScreen(
            viewModel = viewModel,
            onNavigateToLiked = { navController.navigate("library/liked") },
            onNavigateToSubscriptions = { navController.navigate(NavRoutes.LIBRARY_SUBSCRIPTIONS) },
            onNavigateToDownloads = { navController.navigate(NavRoutes.LIBRARY_DOWNLOADS) },
            onNavigateToHistory = { navController.navigate("library/history") },
        )
    }

    composable("library/history") {
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.HistoryViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.library.HistoryViewModel(playbackRepository) as T
            },
        )
        cx.aswin.boxlore.feature.library.HistoryScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onEpisodeClick = { entity ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                val desc = "Resuming from History"
                navController.navigate(
                    "episode/${entity.episodeId}/${encode(entity.episodeTitle)}/" +
                        "${encode(desc)}/" +
                        "${encode(entity.episodeImageUrl ?: entity.podcastImageUrl)}/" +
                        "${encode(entity.episodeAudioUrl)}/" +
                        "${entity.durationMs}/${encode(entity.podcastId)}/" +
                        "${encode(entity.podcastName)}" +
                        "?entryPoint=library_history",
                )
            },
        )
    }

    composable("library/liked") {
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                    ) as T
            },
        )
        cx.aswin.boxlore.feature.library.LikedEpisodesScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onEpisodeClick = { episode, podcast ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                navController.navigate(
                    "episode/${episode.id}/${encode(episode.title)}/" +
                        "${encode(episode.description.take(500))}/" +
                        "${encode(episode.imageUrl)}/" +
                        "${encode(episode.audioUrl)}/" +
                        "${episode.duration}/${encode(podcast.id)}/" +
                        "${encode(podcast.title)}" +
                        "?entryPoint=library_liked_episodes",
                )
            },
            onExploreClick = {
                navController.navigate("explore?entryPoint=library_history_empty_state") {
                    popUpTo("home")
                }
            },
        )
    }

    composable(
        "library/subscriptions?tab={tab}",
        arguments = listOf(
            navArgument("tab") {
                type = NavType.IntType
                defaultValue = 0
            },
        ),
    ) { backStackEntry ->
        val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                    ) as T
            },
        )
        cx.aswin.boxlore.feature.library.SubscriptionsScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onPodcastClick = { podcastId ->
                navController.navigate(
                    "podcast/${android.net.Uri.encode(podcastId)}?entryPoint=library_subscriptions",
                )
            },
            onExploreClick = {
                navController.navigate("explore?entryPoint=library_subscriptions_empty_state") {
                    popUpTo("home")
                }
            },
            onPlayEpisode = { episode, podcast -> queueManager.playEpisode(episode, podcast) },
            onPlayEpisodes = { episodes, fallbackPodcast -> queueManager.playEpisodes(episodes, fallbackPodcast) },
            onEpisodeClick = { episode, podcast, entryPointStr ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                val entryPointQuery = if (entryPointStr != null) "?entryPoint=$entryPointStr" else ""
                navController.navigate(
                    "episode/${encode(episode.id)}/${encode(episode.title)}/" +
                        "${encode(episode.description.take(500))}/" +
                        "${encode(episode.imageUrl)}/" +
                        "${encode(episode.audioUrl)}/" +
                        "${episode.duration}/${encode(podcast.id)}/" +
                        "${encode(podcast.title)}" +
                        entryPointQuery,
                )
            },
            isPlayerActive = currentEpisode != null,
            initialTab = initialTab,
        )
    }

    composable(NavRoutes.LIBRARY_DOWNLOADS) {
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                    ) as T
            },
        )
        cx.aswin.boxlore.feature.library.DownloadedEpisodesScreen(
            viewModel = viewModel,
            userPrefs = userPrefs,
            isOffline = !isOnline,
            onBack = { navController.popBackStack() },
            isPlayerActive = currentEpisode != null,
            onPodcastShowClick = { podcastId, podcastTitle ->
                android.util.Log.d("NavHost", "onPodcastShowClick: id=$podcastId title=$podcastTitle")
                val encodedTitle = android.net.Uri.encode(podcastTitle.ifEmpty { "_" })
                val encodedId = android.net.Uri.encode(podcastId.ifEmpty { "_" })
                navController.navigate("library/downloads/show?podcastId=$encodedId&podcastTitle=$encodedTitle")
            },
            onExploreClick = {
                navController.navigate("explore?entryPoint=library_downloads_empty_state") {
                    popUpTo("home")
                }
            },
            onSettingsClick = { navController.navigate(NavRoutes.LIBRARY_DOWNLOADS_SETTINGS) },
            isSyncing = isSyncingSmartDownloads,
            onSyncNow = {
                scope.launch(Dispatchers.IO) {
                    isSyncingSmartDownloads = true
                    try {
                        smartDownloadManager.performSync(isManual = true)
                    } finally {
                        isSyncingSmartDownloads = false
                    }
                }
            },
        )
    }

    composable(NavRoutes.LIBRARY_DOWNLOADS_SETTINGS) {
        cx.aswin.boxlore.feature.library.SmartDownloadsSettingsScreen(
            userPrefs = userPrefs,
            onBack = { navController.popBackStack() },
        )
    }

    composable("library/auto_downloads/settings") {
        cx.aswin.boxlore.feature.library.AutoDownloadSettingsScreen(
            userPrefs = userPrefs,
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = "library/downloads/show?podcastId={podcastId}&podcastTitle={podcastTitle}",
        arguments = listOf(
            navArgument("podcastId") { type = NavType.StringType; defaultValue = "" },
            navArgument("podcastTitle") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { backStackEntry ->
        val podcastId = backStackEntry.arguments?.getString("podcastId") ?: ""
        val podcastTitle = backStackEntry.arguments?.getString("podcastTitle") ?: ""

        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.library.LibraryViewModel>(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    cx.aswin.boxlore.feature.library.LibraryViewModel(
                        subscriptionRepository,
                        playbackRepository,
                        downloadRepository,
                        userPrefs,
                        container.adaptiveCandidateScorer,
                    ) as T
            },
        )
        cx.aswin.boxlore.feature.library.DownloadedShowEpisodesScreen(
            viewModel = viewModel,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            onBack = { navController.popBackStack() },
            isPlayerActive = currentEpisode != null,
            onEpisodeClick = { episode, podcast ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                navController.navigate(
                    "episode/${episode.id}/${encode(episode.title)}/" +
                        "${encode(episode.description.take(500))}/" +
                        "${encode(episode.imageUrl)}/" +
                        "${encode(episode.audioUrl)}/" +
                        "${episode.duration}/${encode(podcast.id)}/" +
                        "${encode(podcast.title)}" +
                        "?entryPoint=library_downloaded_episodes",
                )
            },
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.addPodcastDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs
    val queueManager = w.queueManager

    // -----------------------------------------------------------------------
    // Podcast info (+ deep links)
    // -----------------------------------------------------------------------
    composable(
        route = "podcast/{podcastId}?entryPoint={entryPoint}&genre={genre}&depth={depth}&query={query}",
        arguments = listOf(
            navArgument("podcastId") { type = NavType.StringType },
            navArgument("entryPoint") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("genre") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("depth") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "boxlore://podcast/{podcastId}" },
            navDeepLink { uriPattern = "boxcast://podcast/{podcastId}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=podcast&id={podcastId}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=podcast&id={podcastId}" },
        ),
    ) { backStackEntry ->
        val podcastId = backStackEntry.arguments?.getString("podcastId") ?: return@composable
        val briefingRegion = briefingRegionFromPodcastId(podcastId)
        if (briefingRegion != null) {
            LaunchedEffect(podcastId) {
                navController.navigate("briefing?region=$briefingRegion") {
                    popUpTo("podcast/{podcastId}?entryPoint={entryPoint}&genre={genre}&depth={depth}&query={query}") {
                        inclusive = true
                    }
                }
            }
            return@composable
        }

        val entryPoint = backStackEntry.arguments?.getString("entryPoint")
        val genre = backStackEntry.arguments?.getString("genre")
        val depth = backStackEntry.arguments?.getString("depth")?.toIntOrNull()
        val query = backStackEntry.arguments?.getString("query")

        val infoSharedDeps = cx.aswin.boxlore.feature.info.InfoSharedDeps(
            podcastRepository = podcastRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            queueManager = queueManager,
            localCatalog = container.localCatalogPort,
            episodeOfflineLookup = container.episodeOfflineLookupPort,
        )
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.info.PodcastInfoViewModel>(
            factory = cx.aswin.boxlore.feature.info.InfoViewModelAssembler.podcastInfoFactory(
                application = application,
                deps = infoSharedDeps,
                subscriptionRepository = subscriptionRepository,
                rssRepository = container.rssPodcastRepository,
                userPrefs = userPrefs,
                routeArgs = cx.aswin.boxlore.feature.info.PodcastInfoRouteArgs(
                    entryPoint = entryPoint,
                    genreFilter = genre,
                    scrollDepth = depth,
                    searchQuery = query,
                ),
            ),
        )

        val isPlayerVisible by remember(playbackRepository) {
            playbackRepository.playerState.map { it.currentEpisode != null }.distinctUntilChanged()
        }.collectAsState(initial = false)

        cx.aswin.boxlore.feature.info.PodcastInfoScreen(
            podcastId = podcastId,
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            bottomContentPadding = miniPlayerBottomPadding(isPlayerVisible),
            onPodcastClick = { pId ->
                navController.navigate("podcast/${android.net.Uri.encode(pId)}?entryPoint=podroll")
            },
            onEpisodeClick = { episode, entryPointStr, index ->
                val state = viewModel.uiState.value
                navigatePodcastInfoEpisode(
                    navController = navController,
                    episode = episode,
                    entryPointStr = entryPointStr,
                    index = index,
                    podcastId = podcastIdFromInfoUiState(state, podcastId),
                    podcastTitle = podcastTitleFromInfoUiState(state, "Podcast"),
                )
            },
            onPlayEpisode = { episode ->
                val state = viewModel.uiState.value as? cx.aswin.boxlore.feature.info.PodcastInfoUiState.Success
                    ?: return@PodcastInfoScreen
                queueManager.playEpisode(episode, state.podcast)
            },
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.addEpisodeFullPathDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val userPrefs = w.userPrefs
    val queueManager = w.queueManager
    val scope = w.scope
    val hasSeenMarkPlayedTip = w.session.hasSeenMarkPlayedTip

    // -----------------------------------------------------------------------
    // Episode info — full path route
    // -----------------------------------------------------------------------
    composable(
        route = "episode/{episodeId}/{episodeTitle}/{episodeDescription}/{episodeImageUrl}/{episodeAudioUrl}/{episodeDuration}/{podcastId}/{podcastTitle}?entryPoint={entryPoint}&vibeId={vibeId}&carouselPosition={carouselPosition}",
        arguments = listOf(
            navArgument("episodeId") { type = NavType.StringType },
            navArgument("episodeTitle") { type = NavType.StringType },
            navArgument("episodeDescription") { type = NavType.StringType },
            navArgument("episodeImageUrl") { type = NavType.StringType },
            navArgument("episodeAudioUrl") { type = NavType.StringType },
            navArgument("episodeDuration") { type = NavType.IntType },
            navArgument("podcastId") { type = NavType.StringType },
            navArgument("podcastTitle") { type = NavType.StringType },
            navArgument("entryPoint") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("vibeId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("carouselPosition") { type = NavType.IntType; defaultValue = -1 },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments ?: return@composable
        val episodeId = args.getString("episodeId") ?: ""
        val briefingRegion = briefingRegionFromEpisodeId(episodeId)
        if (briefingRegion != null) {
            LaunchedEffect(episodeId) {
                navController.navigate("briefing?region=$briefingRegion") {
                    popUpTo(
                        "episode/{episodeId}/{episodeTitle}/{episodeDescription}/{episodeImageUrl}/{episodeAudioUrl}/{episodeDuration}/{podcastId}/{podcastTitle}?entryPoint={entryPoint}&vibeId={vibeId}&carouselPosition={carouselPosition}",
                    ) { inclusive = true }
                }
            }
            return@composable
        }

        val episodeInfoDeps = cx.aswin.boxlore.feature.info.InfoSharedDeps(
            podcastRepository = podcastRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            queueManager = queueManager,
            localCatalog = container.localCatalogPort,
            episodeOfflineLookup = container.episodeOfflineLookupPort,
        )
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.info.EpisodeInfoViewModel>(
            factory = cx.aswin.boxlore.feature.info.InfoViewModelAssembler.episodeInfoFactory(
                application = application,
                deps = episodeInfoDeps,
                userPrefs = userPrefs,
            ),
        )

        val podcastId = decodeNavArg(args.getString("podcastId"))
        val podcastTitle = decodeNavArg(args.getString("podcastTitle"))
        val episodeTitle = decodeNavArg(args.getString("episodeTitle"))
        val episodeImageUrl = decodeNavArg(args.getString("episodeImageUrl"))
        val episodeAudioUrl = decodeNavArg(args.getString("episodeAudioUrl"))
        val episodeDuration = args.getInt("episodeDuration")
        val entryPoint = args.getString("entryPoint")
        val vibeId = decodeNavArg(args.getString("vibeId"))
        val carouselPosition = args.getInt("carouselPosition", -1)
        val playContext = entryPointBundle(entryPoint, vibeId, carouselPosition)

        cx.aswin.boxlore.feature.info.EpisodeInfoScreen(
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            episodeDescription = decodeNavArg(args.getString("episodeDescription")),
            episodeImageUrl = episodeImageUrl,
            episodeAudioUrl = episodeAudioUrl,
            episodeDuration = episodeDuration,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onPodcastClick = { pId ->
                navController.navigate("podcast/${android.net.Uri.encode(pId)}?entryPoint=episode_info")
            },
            onEpisodeClick = { ep ->
                navigateRelatedEpisode(navController, ep, podcastId, podcastTitle)
            },
            onPlay = {
                val episode = cx.aswin.boxlore.core.model.Episode(
                    id = episodeId,
                    title = episodeTitle,
                    description = "",
                    imageUrl = episodeImageUrl,
                    audioUrl = episodeAudioUrl,
                    duration = episodeDuration,
                    publishedDate = 0L,
                )
                val podcast = cx.aswin.boxlore.core.model.Podcast(
                    id = podcastId,
                    title = podcastTitle,
                    artist = "",
                    imageUrl = "",
                    description = "",
                    genre = "",
                )
                queueManager.playEpisode(episode, podcast, entryPointContext = playContext)
            },
            entryPointContext = playContext,
            showMarkPlayedTip = !hasSeenMarkPlayedTip,
            onMarkPlayedTipDismissed = { scope.launch { userPrefs.markMarkPlayedTipSeen() } },
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.addEpisodeDeepLinkDestination(w: NavGraphWiring) {
    val navController = w.navController
    val application = w.application
    val container = w.container
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val downloadRepository = w.downloadRepository
    val userPrefs = w.userPrefs
    val queueManager = w.queueManager

    // -----------------------------------------------------------------------
    // Episode info — simplified deep-link route
    // -----------------------------------------------------------------------
    composable(
        route = "episode/{episodeId}?entryPoint={entryPoint}&t={t}&start={start}&end={end}&autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}",
        arguments = listOf(
            navArgument("episodeId") { type = NavType.StringType },
            navArgument("entryPoint") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("t") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("start") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("end") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("autoplay") { type = NavType.StringType; nullable = true; defaultValue = "true" },
            navArgument("podcastId") { type = NavType.StringType; nullable = true; defaultValue = "" },
            navArgument("podcastTitle") { type = NavType.StringType; nullable = true; defaultValue = "" },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?podcastId={podcastId}&podcastTitle={podcastTitle}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?t={t}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}?autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxlore://episode/{episodeId}" },
            navDeepLink { uriPattern = "boxcast://episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxcast://episode/{episodeId}?t={t}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxcast://episode/{episodeId}?autoplay={autoplay}" },
            navDeepLink { uriPattern = "boxcast://episode/{episodeId}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=episode&id={episodeId}&t={t}&start={start}&end={end}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=episode&id={episodeId}&t={t}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=episode&id={episodeId}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=episode&id={episodeId}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=episode&id={episodeId}&t={t}&start={start}&end={end}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=episode&id={episodeId}&t={t}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=episode&id={episodeId}&autoplay={autoplay}" },
            navDeepLink { uriPattern = "https://aswin.cx/boxcast/share?type=episode&id={episodeId}" },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments ?: return@composable
        val episodeId = args.getString("episodeId") ?: ""
        val t = args.getString("t")?.toLongOrNull()
        val start = args.getString("start")?.toLongOrNull()
        val autoplay = args.getString("autoplay") ?: "true"
        val podcastIdArg = args.getString("podcastId") ?: ""
        val podcastTitleArg = args.getString("podcastTitle") ?: ""

        val deepLinkEpisodeDeps = cx.aswin.boxlore.feature.info.InfoSharedDeps(
            podcastRepository = podcastRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            queueManager = queueManager,
            localCatalog = container.localCatalogPort,
            episodeOfflineLookup = container.episodeOfflineLookupPort,
        )
        val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxlore.feature.info.EpisodeInfoViewModel>(
            factory = cx.aswin.boxlore.feature.info.InfoViewModelAssembler.episodeInfoFactory(
                application = application,
                deps = deepLinkEpisodeDeps,
                userPrefs = userPrefs,
            ),
        )

        LaunchedEffect(episodeId, podcastIdArg, podcastTitleArg) {
            viewModel.loadEpisode(episodeId = episodeId, podcastId = podcastIdArg, podcastTitle = podcastTitleArg)
        }

        val coroutineScope = rememberCoroutineScope()
        val state by viewModel.uiState.collectAsState()

        LaunchedEffect(state, t, start, autoplay) {
            val success = state as? cx.aswin.boxlore.feature.info.EpisodeInfoUiState.Success ?: return@LaunchedEffect
            autoplayDeepLinkEpisodeIfNeeded(
                playbackRepository = playbackRepository,
                queueManager = queueManager,
                localCatalog = container.localCatalogPort,
                episodeId = episodeId,
                autoplay = autoplay,
                t = t,
                start = start,
                success = success,
            )
        }

        val isPlayerVisible by remember(playbackRepository) {
            playbackRepository.playerState.map { it.currentEpisode != null }.distinctUntilChanged()
        }.collectAsState(initial = false)

        val successState = state as? cx.aswin.boxlore.feature.info.EpisodeInfoUiState.Success

        cx.aswin.boxlore.feature.info.EpisodeInfoScreen(
            episodeId = episodeId,
            episodeTitle = successState?.episode?.title ?: "",
            episodeDescription = successState?.episode?.description ?: "",
            episodeImageUrl = successState?.episode?.imageUrl ?: "",
            episodeAudioUrl = successState?.episode?.audioUrl ?: "",
            episodeDuration = successState?.episode?.duration ?: 0,
            podcastId = successState?.podcastId ?: "",
            podcastTitle = successState?.podcastTitle ?: "Podcast",
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onPodcastClick = { pId ->
                navController.navigate("podcast/${android.net.Uri.encode(pId)}?entryPoint=episode_info")
            },
            onEpisodeClick = { ep ->
                navigateRelatedEpisode(
                    navController,
                    ep,
                    successState?.podcastId ?: "",
                    successState?.podcastTitle ?: "Podcast",
                )
            },
            onPlay = {
                val current = successState ?: return@EpisodeInfoScreen
                coroutineScope.launch {
                    val podcast = resolveLocalOrFallbackPodcast(
                        localCatalog = container.localCatalogPort,
                        podcastId = current.podcastId,
                        podcastTitle = current.podcastTitle,
                        fallbackImageUrl = current.episode.podcastImageUrl ?: "",
                    )
                    queueManager.playEpisode(current.episode, podcast)
                }
            },
            bottomContentPadding = miniPlayerBottomPadding(isPlayerVisible),
        )
    }
}
