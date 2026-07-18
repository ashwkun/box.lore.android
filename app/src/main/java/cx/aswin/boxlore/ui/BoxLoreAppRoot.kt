package cx.aswin.boxlore.ui

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.posthog.PostHog
import cx.aswin.boxlore.BoxLoreApplication
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.core.data.PlaybackRepository
import cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerHeight
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerNavGap
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHeight
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.PredictiveBackWrapper
import cx.aswin.boxlore.core.designsystem.components.SleepTimerPopup
import cx.aswin.boxlore.core.designsystem.components.SleepTimerPopupDismissReason
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.ModeSwitchState
import cx.aswin.boxlore.feature.home.components.FeedbackSheet
import cx.aswin.boxlore.feature.player.v2.MiniPlayerHeight
import cx.aswin.boxlore.feature.player.v2.PlayerSheetActions
import cx.aswin.boxlore.feature.player.v2.PlayerSheetLayout
import cx.aswin.boxlore.feature.player.v2.PlayerSheetScaffold
import cx.aswin.boxlore.fcm.FcmTopicHelper
import cx.aswin.boxlore.lifecycle.DownloadBandwidthEffect
import cx.aswin.boxlore.navigation.BoxLoreNavHost
import cx.aswin.boxlore.navigation.NavHostActions
import cx.aswin.boxlore.navigation.NavHostSession
import cx.aswin.boxlore.navigation.NavOpmlCallbacks
import cx.aswin.boxlore.navigation.NavSettingsState
import cx.aswin.boxlore.navigation.navigateBottomNavTab
import cx.aswin.boxlore.navigation.resolveBottomNavTab
import cx.aswin.boxlore.navigation.snapshotNavBackStack
import cx.aswin.boxlore.navigation.PushTargetRouteAllowlist
import cx.aswin.boxlore.ui.announcement.FeatureAnnouncementOverlay
import cx.aswin.boxlore.ui.announcement.InAppAnnouncementDialog
import cx.aswin.boxlore.ui.announcement.shouldSuppressWhatsNewOnPlay
import cx.aswin.boxlore.ui.libraryimport.OpmlImportDialog
import cx.aswin.boxlore.ui.libraryimport.OpmlImportEffects
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState
import cx.aswin.boxlore.ui.libraryimport.performJsonLibraryImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Application UI root hosted by [cx.aswin.boxlore.MainActivity]: theme, nav host,
 * player overlay, bottom nav, and activity-scoped dialogs.
 *
 * Repositories come from [BoxLoreApplication.container] — never constructed here.
 */
@Composable
fun BoxLoreAppRoot(
    activity: ComponentActivity,
    application: BoxLoreApplication,
    expandPlayerTrigger: Long,
    intentState: MutableState<android.content.Intent?>,
    warmStartIntent: MutableState<android.content.Intent?>,
    onPlaybackRepositoryReady: (PlaybackRepository) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val currentWarmIntent = warmStartIntent.value
    LaunchedEffect(currentWarmIntent) {
        val intent = currentWarmIntent ?: return@LaunchedEffect
        if (intent.data != null) {
            navController.handleDeepLink(intent)
            warmStartIntent.value = null
            return@LaunchedEffect
        }
        val rawTarget = intent.getStringExtra("target_route")
        val allowed = PushTargetRouteAllowlist.sanitize(rawTarget)
        if (allowed != null) {
            if (PushTargetRouteAllowlist.isAppOrWebUri(allowed)) {
                val deepLinkIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                ).apply {
                    data = android.net.Uri.parse(allowed)
                }
                navController.handleDeepLink(deepLinkIntent)
            } else {
                runCatching { navController.navigate(allowed) }
                    .onFailure {
                        Log.w("BoxLoreAppRoot", "Ignoring invalid target_route=$allowed", it)
                    }
            }
            intent.removeExtra("target_route")
        } else if (!rawTarget.isNullOrBlank()) {
            Log.w("BoxLoreAppRoot", "Rejected non-allowlisted target_route=$rawTarget")
            intent.removeExtra("target_route")
        }
        warmStartIntent.value = null
    }

    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val container = application.container
    val podcastRepository = container.podcastRepository
    val playbackRepository = container.playbackRepository
    val downloadRepository = container.downloadRepository
    val subscriptionRepository = container.subscriptionRepository
    val consentManager = container.consentManager
    val userPrefs = container.userPreferencesRepository
    val queueManager = container.queueManager
    val smartDownloadManager = container.smartDownloadManager
    val installReferrerManager = container.installReferrerManager

    var showFeedbackSheet by remember { mutableStateOf(false) }
    val onSubmitFeedback: suspend (String, String, String, String) -> Boolean =
        remember(podcastRepository) {
            { category, message, version, email ->
                podcastRepository.submitFeedback(category, message, version, email.ifBlank { null })
            }
        }

    LaunchedEffect(Unit) {
        val prefs = activity.getSharedPreferences("boxcast_api_config", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("base_url", BuildConfig.BOXLORE_API_BASE_URL)
            .putString("public_key", BuildConfig.BOXLORE_PUBLIC_KEY)
            .apply()
    }

    val showBottomNav = !currentRoute.startsWith("player") && currentRoute != "onboarding"
    val canGoBack = navController.previousBackStackEntry != null

    SideEffect { onPlaybackRepositoryReady(playbackRepository) }

    LaunchedEffect(Unit) {
        FcmTopicHelper.reconcileAfterRestoreIfNeeded(activity, subscriptionRepository)
    }

    val onboardingViewModel = remember {
        cx.aswin.boxlore.feature.onboarding.OnboardingViewModel(
            application,
            podcastRepository,
            subscriptionRepository,
            userPrefs,
        )
    }

    val currentIntent = intentState.value
    val hasDeepLink = currentIntent?.data != null
    var onboardingCompleted by remember {
        mutableStateOf(onboardingViewModel.isOnboardingCompleted() || hasDeepLink)
    }

    LaunchedEffect(hasDeepLink) {
        if (hasDeepLink) {
            onboardingViewModel.markOnboardingCompletedSilent {
                onboardingCompleted = true
            }
        }
    }

    LaunchedEffect(Unit) { installReferrerManager.checkInstallReferrer() }
    LaunchedEffect(installReferrerManager) {
        installReferrerManager.referralFlow.collect { referral ->
            Log.d("MainActivityReferrer", "Received referral: $referral")
            val path = when (referral.type) {
                "podcast" -> "podcast/${referral.id}?entryPoint=install_referrer"
                "episode" -> {
                    val tQuery = if (referral.timestamp != null) "&t=${referral.timestamp}" else ""
                    val startQuery = if (referral.start != null) "&start=${referral.start}" else ""
                    val endQuery = if (referral.end != null) "&end=${referral.end}" else ""
                    "episode/${referral.id}?entryPoint=install_referrer$tQuery$startQuery$endQuery"
                }
                else -> null
            }
            if (path != null) {
                onboardingViewModel.markOnboardingCompletedSilent { onboardingCompleted = true }
                navController.navigate(path) { launchSingleTop = true }
            }
        }
    }

    var loreQueueConflictEpisode by remember { mutableStateOf<Episode?>(null) }
    val queueLoreEpisode: (Episode) -> Unit = { episode ->
        val podcast = Podcast(
            id = episode.podcastId ?: "unknown",
            title = episode.podcastTitle ?: "Unknown Podcast",
            artist = episode.podcastTitle ?: "Unknown",
            imageUrl = episode.podcastImageUrl ?: episode.imageUrl ?: "",
        )
        queueManager.addToQueue(episode, podcast, PlaybackEntryPoint.LEARN)
    }

    LaunchedEffect(smartDownloadManager) {
        try {
            val lastSyncTime = userPrefs.smartDownloadsLastSyncTimeStream.first()
            val isEnabled = userPrefs.smartDownloadsEnabledStream.first()
            if (isEnabled && System.currentTimeMillis() - lastSyncTime > 24 * 3600 * 1000L) {
                Log.d("MainActivity", "Last smart sync > 24h ago. Triggering graceful catch-up sync.")
                scope.launch(Dispatchers.IO) {
                    smartDownloadManager.performSync(isForeground = true)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to check catch-up sync status", e)
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val hasUserSetConsent by consentManager.hasUserSetConsent.collectAsState(initial = true)
    @Suppress("UNUSED_VARIABLE")
    val crashlyticsConsent by consentManager.isCrashReportingConsented.collectAsState(initial = false)
    val currentRegion by userPrefs.regionStream.collectAsState(initial = "us")

    val themeConfig by userPrefs.themeConfigStream.collectAsState(
        initial = remember { userPrefs.cachedThemeConfig },
    )
    val skipBehavior by userPrefs.skipBehaviorStream.collectAsState(initial = "just_skip")
    val skipBeginningMs by userPrefs.skipBeginningMsStream.collectAsState(initial = 0L)
    val skipEndingMs by userPrefs.skipEndingMsStream.collectAsState(initial = 0L)
    val seekBackwardMs by userPrefs.seekBackwardMsStream.collectAsState(initial = 10_000L)
    val seekForwardMs by userPrefs.seekForwardMsStream.collectAsState(initial = 30_000L)
    val hideCompletedInHome by userPrefs.hideCompletedInHomeStream.collectAsState(initial = true)
    val hideCompletedInSubs by userPrefs.hideCompletedInSubsStream.collectAsState(initial = true)
    val hideCompletedInShowDetails by userPrefs.hideCompletedInShowDetailsStream.collectAsState(initial = false)
    val useDynamicColor by userPrefs.useDynamicColorStream.collectAsState(
        initial = remember { userPrefs.cachedUseDynamicColor },
    )
    val themeBrand by userPrefs.themeBrandStream.collectAsState(
        initial = remember { userPrefs.cachedThemeBrand },
    )
    val surfaceStyle by userPrefs.surfaceStyleStream.collectAsState(
        initial = remember { userPrefs.cachedSurfaceStyle },
    )
    val hasSeenMarkPlayedTip by userPrefs.hasSeenMarkPlayedTip.collectAsState(initial = true)
    val hasLoggedFirstPlay by userPrefs.hasLoggedFirstPlay.collectAsState(initial = true)
    val activeAnnouncement by userPrefs.activeAnnouncementStream.collectAsState(initial = null)
    val dismissedFeatureVersion by userPrefs.dismissedFeatureVersion.collectAsState(initial = "")

    val isPlaying by remember(playbackRepository) {
        playbackRepository.playerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val showLateNightNudge by remember(playbackRepository) {
        playbackRepository.playerState.map { it.showLateNightNudge }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val currentEpisode by remember(playbackRepository) {
        playbackRepository.playerState.map { it.currentEpisode }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val isModeSwitching by ModeSwitchState.isSwitching.collectAsState()

    DownloadBandwidthEffect(isPlaying = isPlaying)

    val miniPlayerPadding = remember(currentEpisode) {
        if (currentEpisode != null) {
            AppNavigationBarHeight + AppMiniPlayerHeight + AppMiniPlayerNavGap
        } else {
            AppNavigationBarHeight
        }
    }

    val darkTheme = when (themeConfig) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    var appInstanceId by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        AnalyticsHelper.trackNotificationPermissionDecided(isGranted)
    }

    LaunchedEffect(Unit) {
        FcmTopicHelper.subscribeDefaultTopics()
    }

    var opmlImportState by remember { mutableStateOf<OpmlImportState>(OpmlImportState.Idle) }
    var opmlImportSource by remember { mutableStateOf("welcome_screen") }
    var importTriggerKey by remember { mutableStateOf(0L) }

    fun performJsonImport(uri: android.net.Uri) {
        scope.launch {
            performJsonLibraryImport(
                uri = uri,
                context = activity,
                subscriptionRepository = subscriptionRepository,
                playbackRepository = playbackRepository,
                podcastRepository = podcastRepository,
                userPrefs = userPrefs,
                onStateChange = { opmlImportState = it },
            )
        }
    }

    OpmlImportEffects(
        importTriggerKey = importTriggerKey,
        opmlImportState = opmlImportState,
        onStateChange = { opmlImportState = it },
        context = activity,
        subscriptionRepository = subscriptionRepository,
        playbackRepository = playbackRepository,
        podcastRepository = podcastRepository,
    )

    LaunchedEffect(Unit) { playbackRepository.restoreLastSession() }

    LaunchedEffect(isPlaying, hasLoggedFirstPlay) {
        if (isPlaying && !hasLoggedFirstPlay) {
            AnalyticsHelper.trackFirstEpisodePlayed()
            userPrefs.markFirstPlayLogged()
        }
    }

    val featureAnnouncementId = "android_auto_1_3_6"
    var activeFeatureFlag by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dismissedFeatureVersion) {
        if (dismissedFeatureVersion != featureAnnouncementId && dismissedFeatureVersion != "android_auto_1.3.6") {
            activeFeatureFlag = PostHog.getFeatureFlag("active_feature_announcement") as? String
            PostHog.reloadFeatureFlags {
                activeFeatureFlag = PostHog.getFeatureFlag("active_feature_announcement") as? String
            }
        }
    }
    val showFeatureDialog = currentRoute == "home" &&
        activeFeatureFlag == featureAnnouncementId &&
        dismissedFeatureVersion != featureAnnouncementId &&
        dismissedFeatureVersion != "android_auto_1.3.6" &&
        activeAnnouncement == null

    BoxLoreTheme(
        darkTheme = darkTheme,
        dynamicColor = useDynamicColor,
        themeBrand = themeBrand,
        surfaceStyle = surfaceStyle,
    ) {
        loreQueueConflictEpisode?.let { pendingLoreEpisode ->
            LoreQueueConflictDialog(
                pendingLoreEpisode = pendingLoreEpisode,
                onDismiss = { loreQueueConflictEpisode = null },
                onConfirmStart = { episode ->
                    playbackRepository.stopAndClearQueue()
                    queueLoreEpisode(episode)
                },
            )
        }

        if (onboardingCompleted && activeAnnouncement != null) {
            val announcement = activeAnnouncement!!
            val suppressWhatsNewOnPlay = remember(announcement.category) {
                activity.shouldSuppressWhatsNewOnPlay(announcement.category)
            }
            if (suppressWhatsNewOnPlay) {
                LaunchedEffect(announcement.timestamp, announcement.category) {
                    userPrefs.clearAnnouncement()
                }
            } else {
                InAppAnnouncementDialog(
                    announcement = announcement,
                    onDismiss = { scope.launch { userPrefs.clearAnnouncement() } },
                    onAction = { route ->
                        scope.launch { userPrefs.clearAnnouncement() }
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(route),
                            )
                            activity.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("Announcement", "Failed to open route", e)
                        }
                    },
                )
            }
        }

        if (showFeatureDialog) {
            FeatureAnnouncementOverlay(
                featureAnnouncementId = featureAnnouncementId,
                userPrefs = userPrefs,
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
            ) { innerPadding ->
                PredictiveBackWrapper(
                    enabled = canGoBack,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(innerPadding),
                ) {
                    BoxLoreNavHost(
                        navController = navController,
                        application = application,
                        session = NavHostSession(
                            onboardingCompleted = onboardingCompleted,
                            onOnboardingCompleted = { onboardingCompleted = true },
                            onboardingViewModel = onboardingViewModel,
                            hasDeepLink = hasDeepLink,
                            currentEpisode = currentEpisode,
                            miniPlayerPadding = miniPlayerPadding,
                            showFeatureDialog = showFeatureDialog,
                            hasSeenMarkPlayedTip = hasSeenMarkPlayedTip,
                            permissionLauncher = permissionLauncher,
                            appInstanceId = appInstanceId,
                        ),
                        opmlCallbacks = NavOpmlCallbacks(
                            importState = opmlImportState,
                            onImportStateChange = { opmlImportState = it },
                            triggerKey = importTriggerKey,
                            onTriggerKeyChange = { importTriggerKey = it },
                            onSourceChange = { opmlImportSource = it },
                            performJsonImport = ::performJsonImport,
                        ),
                        actions = NavHostActions(
                            onLoreQueueConflictEpisode = { loreQueueConflictEpisode = it },
                            queueLoreEpisode = queueLoreEpisode,
                            onShowFeedbackSheet = { showFeedbackSheet = true },
                            onSubmitFeedback = onSubmitFeedback,
                        ),
                        settingsState = NavSettingsState(
                            currentRegion = currentRegion,
                            themeConfig = themeConfig,
                            useDynamicColor = useDynamicColor,
                            themeBrand = themeBrand,
                            surfaceStyle = surfaceStyle,
                            skipBehavior = skipBehavior,
                            skipBeginningMs = skipBeginningMs,
                            skipEndingMs = skipEndingMs,
                            seekBackwardMs = seekBackwardMs,
                            seekForwardMs = seekForwardMs,
                            hideCompletedInHome = hideCompletedInHome,
                            hideCompletedInSubs = hideCompletedInSubs,
                            hideCompletedInShowDetails = hideCompletedInShowDetails,
                        ),
                    )
                }
            }

            val density = LocalDensity.current
            val screenHeightDp = maxHeight
            val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val appNavBarHeight = AppNavigationBarHeight
            val containerHeight = screenHeightDp + systemNavBarHeight + 50.dp
            val miniPlayerBottomMargin = 2.dp
            val collapsedTargetY = with(density) {
                (screenHeightDp - MiniPlayerHeight - appNavBarHeight - systemNavBarHeight - miniPlayerBottomMargin).toPx()
            }

            if (showBottomNav) {
                val backStack = remember(navBackStackEntry) { snapshotNavBackStack(navController) }
                val activeTab = resolveBottomNavTab(
                    currentRoute = currentRoute,
                    backStack = backStack,
                )
                BoxLoreNavigationBar(
                    currentRoute = activeTab,
                    onNavigate = { route ->
                        navController.navigateBottomNavTab(route, activeTab)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            val isPlayerActive = currentEpisode != null
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            SleepTimerPopup(
                visible = showLateNightNudge && isPlayerActive && !isModeSwitching,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarHeight + 8.dp, start = 16.dp, end = 16.dp)
                    .zIndex(10f),
                onSelectDuration = { minutes ->
                    AnalyticsHelper.trackLateNightSafeguardDecision("timer_set", minutes)
                    playbackRepository.setSleepTimer(minutes, dismissNudge = false)
                },
                onDismiss = { reason ->
                    when (reason) {
                        SleepTimerPopupDismissReason.Manual ->
                            AnalyticsHelper.trackLateNightSafeguardDecision("dismiss")
                        SleepTimerPopupDismissReason.Timeout ->
                            AnalyticsHelper.trackLateNightSafeguardDecision("ignore")
                        SleepTimerPopupDismissReason.Confirmation -> Unit
                    }
                    playbackRepository.dismissLateNightNudge()
                },
            )

            if (!isModeSwitching) {
                PlayerSheetScaffold(
                    playbackRepository = playbackRepository,
                    downloadRepository = downloadRepository,
                    userPrefs = userPrefs,
                    layout = PlayerSheetLayout(
                        collapsedTargetY = collapsedTargetY,
                        containerHeight = containerHeight,
                        collapsedHorizontalPadding = 12.dp,
                        expandTrigger = expandPlayerTrigger,
                    ),
                    actions = PlayerSheetActions(
                        onEpisodeInfoClick = { episode ->
                            if (episode.id.startsWith("briefing_")) {
                                val region = episode.id.removePrefix("briefing_").substringBefore("_")
                                navController.navigate("briefing?region=$region") {
                                    launchSingleTop = true
                                }
                            } else {
                                val podcast = playbackRepository.playerState.value.currentPodcast
                                fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                                navController.navigate(
                                    "episode/${encode(episode.id)}/${encode(episode.title)}/" +
                                        "${encode(episode.description.take(500))}/" +
                                        "${encode(episode.imageUrl)}/" +
                                        "${encode(episode.audioUrl)}/" +
                                        "${episode.duration}/${encode(podcast?.id ?: "unknown")}/" +
                                        "${encode(podcast?.title ?: "Podcast")}" +
                                        "?entryPoint=player_ui",
                                ) { launchSingleTop = true }
                            }
                        },
                        onPodcastInfoClick = { podcast ->
                            if (podcast.id.startsWith("briefing_")) {
                                val region = podcast.id.removePrefix("briefing_")
                                navController.navigate("briefing?region=$region") {
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(
                                    "podcast/${android.net.Uri.encode(podcast.id)}?entryPoint=player_ui",
                                ) { launchSingleTop = true }
                            }
                        },
                    ),
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }

        OpmlImportDialog(
            state = opmlImportState,
            onDismissRequest = {
                val currentState = opmlImportState
                if (currentState is OpmlImportState.Success) {
                    if (currentState.isJson) {
                        if (currentRoute == "onboarding") {
                            onboardingCompleted = true
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                        onboardingViewModel.markOnboardingCompletedSilent {
                            activity.recreate()
                        }
                    } else {
                        if (currentRoute == "onboarding") {
                            AnalyticsHelper.trackOnboardingImportCompleted(
                                importType = "opml",
                                importedPodcastCount = currentState.importedCount,
                                importedPodcastsList = currentState.importedPodcasts.map { it.title },
                                totalOnboardingTimeSeconds = onboardingViewModel.getTotalOnboardingTime(),
                                entryPoint = opmlImportSource,
                            )
                            onboardingViewModel.generateRecommendationsFromOpml(currentState.importedPodcasts)
                        } else if (opmlImportSource == "home_import_banner") {
                            AnalyticsHelper.trackOnboardingImportCompleted(
                                importType = "opml",
                                importedPodcastCount = currentState.importedCount,
                                importedPodcastsList = currentState.importedPodcasts.map { it.title },
                                totalOnboardingTimeSeconds = 0f,
                                entryPoint = "home_import_banner",
                            )
                        }
                    }
                }
                opmlImportState = OpmlImportState.Idle
            },
            onSelectionChanged = { newSelection ->
                val currentState = opmlImportState
                if (currentState is OpmlImportState.AskCompleted) {
                    opmlImportState = currentState.copy(selectedIds = newSelection)
                }
            },
            onConfirmCompleted = {
                val currentState = opmlImportState
                if (currentState is OpmlImportState.AskCompleted) {
                    val selectedIds = currentState.selectedIds
                    val podcastsToMark = currentState.importedPodcasts.filter { it.id in selectedIds }
                    if (podcastsToMark.isEmpty()) {
                        opmlImportState = OpmlImportState.Success(
                            importedCount = currentState.importedPodcasts.size,
                            completedCount = 0,
                            isJson = false,
                            importedPodcasts = currentState.importedPodcasts,
                        )
                    } else {
                        opmlImportState = OpmlImportState.Completing(
                            progress = 0f,
                            currentShowTitle = podcastsToMark.first().title,
                            podcastsToMark = podcastsToMark,
                            totalImportedCount = currentState.importedPodcasts.size,
                            importedPodcasts = currentState.importedPodcasts,
                        )
                        importTriggerKey = System.currentTimeMillis()
                    }
                }
            },
            onSkipCompleted = {
                val currentState = opmlImportState
                if (currentState is OpmlImportState.AskCompleted) {
                    opmlImportState = OpmlImportState.Success(
                        importedCount = currentState.importedPodcasts.size,
                        completedCount = 0,
                        isJson = false,
                        importedPodcasts = currentState.importedPodcasts,
                    )
                }
            },
            onImportJsonSelected = { uri -> performJsonImport(uri) },
            onImportOpmlSelected = { uri ->
                opmlImportState = OpmlImportState.Parsing(uri)
                importTriggerKey = System.currentTimeMillis()
            },
        )

        if (showFeedbackSheet) {
            val versionStr = remember {
                try {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                        ?: "unknown"
                } catch (_: Exception) {
                    "unknown"
                }
            }
            FeedbackSheet(
                appVersion = versionStr,
                onSubmit = onSubmitFeedback,
                onRateInstead = {
                    showFeedbackSheet = false
                    try {
                        activity.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=${activity.packageName}"),
                            ),
                        )
                    } catch (_: Exception) {
                        activity.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://play.google.com/store/apps/details?id=${activity.packageName}",
                                ),
                            ),
                        )
                    }
                },
                onDismissRequest = { showFeedbackSheet = false },
            )
        }
    }
}
