package cx.aswin.boxlore.navigation

import android.annotation.SuppressLint
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import cx.aswin.boxlore.BoxLoreApplication
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState

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
internal object NavRoutes {
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
 * Snapshot of the nav back stack for bottom-tab highlighting on overlay routes.
 * Uses the library-group API because Navigation Compose exposes no public full-stack accessor.
 */
@SuppressLint("RestrictedApi")
internal fun snapshotNavBackStack(navController: NavController): List<androidx.navigation.NavBackStackEntry> =
    navController.currentBackStack.value

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
internal class NavGraphWiring(
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

internal fun getRouteIndex(route: String?): Int {
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

internal fun isTabToTab(fromRoute: String?, toRoute: String?): Boolean {
    val fromIndex = getRouteIndex(fromRoute)
    val toIndex = getRouteIndex(toRoute)
    return fromIndex < 10 && toIndex < 10
}

internal fun navEnterTransition(
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

internal fun navExitTransition(
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

internal fun navPopEnterTransition(
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

internal fun navPopExitTransition(
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

internal fun encodeNavArg(s: String?): String =
    android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")

internal fun decodeNavArg(s: String?): String = try {
    android.net.Uri.decode(s ?: "").let { if (it == "_") "" else it }
} catch (_: Exception) {
    s ?: ""
}

internal fun episodeFullPathRoute(
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

internal fun podcastDetailRoute(
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

internal fun exploreRoute(category: String?, entryPoint: String, tab: String?): String {
    val catQuery = if (category != null) "category=$category&" else ""
    val tabQuery = if (tab != null) "tab=$tab&" else ""
    return "explore?${catQuery}${tabQuery}entryPoint=$entryPoint"
}

internal fun entryPointBundle(
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

internal fun miniPlayerBottomPadding(isPlayerVisible: Boolean): androidx.compose.ui.unit.Dp =
    if (isPlayerVisible) {
        AppNavigationBarHeight + 64.dp + 2.dp
    } else {
        AppNavigationBarHeight
    }

internal fun shouldRequestNotificationPermission(
    showFeatureDialog: Boolean,
    context: android.content.Context,
): Boolean =
    !showFeatureDialog &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

internal fun launchInAppReview(context: android.content.Context) {
    val activity = context as? androidx.activity.ComponentActivity ?: return
    val reviewManager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
    reviewManager.requestReviewFlow().addOnCompleteListener { task ->
        if (task.isSuccessful) {
            reviewManager.launchReviewFlow(activity, task.result)
        }
    }
}

internal fun navigateHomePodcast(
    navController: NavHostController,
    podcast: cx.aswin.boxlore.core.model.Podcast,
    entryPointStr: String,
    genreStr: String?,
    depthVal: Int?,
) {
    navController.navigate(podcastDetailRoute(podcast.id, entryPointStr, genreStr, depthVal))
}

internal fun navigateHomeHeroArrow(
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

internal fun navigateHomeEpisode(
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

internal fun navigateHomeExplore(
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

internal fun podcastIdFromInfoUiState(
    state: cx.aswin.boxlore.feature.info.PodcastInfoUiState,
    fallback: String,
): String =
    (state as? cx.aswin.boxlore.feature.info.PodcastInfoUiState.Success)?.podcast?.id ?: fallback

internal fun podcastTitleFromInfoUiState(
    state: cx.aswin.boxlore.feature.info.PodcastInfoUiState,
    fallback: String,
): String =
    (state as? cx.aswin.boxlore.feature.info.PodcastInfoUiState.Success)?.podcast?.title ?: fallback

internal fun navigatePodcastInfoEpisode(
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

internal fun navigateRelatedEpisode(
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

internal fun briefingRegionFromPodcastId(podcastId: String): String? {
    if (!podcastId.startsWith("briefing_")) return null
    return podcastId.removePrefix("briefing_")
}

internal fun briefingRegionFromEpisodeId(episodeId: String): String? {
    if (!episodeId.startsWith("briefing_")) return null
    return episodeId.removePrefix("briefing_").substringBefore("_")
}

internal suspend fun resolveLocalOrFallbackPodcast(
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

internal suspend fun autoplayDeepLinkEpisodeIfNeeded(
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
