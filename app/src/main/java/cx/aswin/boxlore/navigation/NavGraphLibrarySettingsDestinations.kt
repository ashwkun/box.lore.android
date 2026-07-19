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
import kotlinx.coroutines.launch

import cx.aswin.boxlore.feature.home.settings.DownloadsNavigation

internal fun androidx.navigation.NavGraphBuilder.addSettingsDestination(w: NavGraphWiring) {
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
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
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
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.resetIdentity()
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
                            val backupJson = cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager(
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
                            val opmlXml = cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager(
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

internal fun androidx.navigation.NavGraphBuilder.addDebugDestination(w: NavGraphWiring) {
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
            bottomContentPadding = w.session.miniPlayerPadding,
            onBack = { navController.popBackStack() },
        )
    }
}

internal fun androidx.navigation.NavGraphBuilder.addLibraryDestinations(w: NavGraphWiring) {
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
                    cx.aswin.boxlore.feature.library.HistoryViewModel(
                        playbackRepository,
                        userPrefs,
                    ) as T
            },
        )
        cx.aswin.boxlore.feature.library.HistoryScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            onEpisodeClick = { item ->
                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                val desc = "Resuming from History"
                navController.navigate(
                    "episode/${item.episodeId}/${encode(item.episodeTitle)}/" +
                        "${encode(desc)}/" +
                        "${encode(item.episodeImageUrl ?: item.podcastImageUrl)}/" +
                        "${encode(item.episodeAudioUrl)}/" +
                        "${item.durationMs}/${encode(item.podcastId)}/" +
                        "${encode(item.podcastName)}" +
                        "?entryPoint=library_history",
                )
            },
            onExploreClick = {
                navController.navigate("explore?entryPoint=library_history_empty_state") {
                    popUpTo("home")
                }
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
