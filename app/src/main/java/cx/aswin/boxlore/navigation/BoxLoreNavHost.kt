package cx.aswin.boxlore.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import cx.aswin.boxlore.BoxLoreApplication

@Composable
private fun rememberIsOnline(application: BoxLoreApplication): Boolean {
    val observer = application.container.connectivityObserver
    val isOnline by observer.isOnlineFlow.collectAsState(initial = observer.isOnline())
    return isOnline
}

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
    val isOnline = rememberIsOnline(application)
    val isSyncingSmartDownloads = remember { mutableStateOf(false) }

    val isOfflineOnLaunch = remember { !isOnline }
    val computedStartDestination =
        remember {
            when {
                !session.onboardingCompleted -> "onboarding"
                isOfflineOnLaunch && !session.hasDeepLink -> NavRoutes.LIBRARY_DOWNLOADS
                else -> "home"
            }
        }

    val wiring =
        NavGraphWiring(
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
