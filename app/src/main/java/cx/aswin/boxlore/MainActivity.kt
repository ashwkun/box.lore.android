package cx.aswin.boxlore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.posthog.PostHog
import cx.aswin.boxlore.core.data.DownloadSpeedLimiter
import cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerHeight
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerNavGap
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHeight
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.ExpressiveAnimatedBackground
import cx.aswin.boxlore.core.designsystem.component.PredictiveBackWrapper
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLogo
import cx.aswin.boxlore.core.designsystem.components.SleepTimerPopup
import cx.aswin.boxlore.core.designsystem.components.SleepTimerPopupDismissReason
import cx.aswin.boxlore.core.designsystem.theme.BoxCastTheme
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.ModeSwitchState
import cx.aswin.boxlore.feature.home.components.FeedbackSheet
import cx.aswin.boxlore.feature.player.v2.MiniPlayerHeight
import cx.aswin.boxlore.feature.player.v2.PlayerSheetActions
import cx.aswin.boxlore.feature.player.v2.PlayerSheetLayout
import cx.aswin.boxlore.feature.player.v2.PlayerSheetScaffold
import cx.aswin.boxlore.navigation.BoxLoreNavHost
import cx.aswin.boxlore.navigation.ExploreTabRoutePattern
import cx.aswin.boxlore.navigation.NavOpmlCallbacks
import cx.aswin.boxlore.navigation.NavSettingsState
import cx.aswin.boxlore.navigation.bottomNavTabRoutePattern
import cx.aswin.boxlore.navigation.resolveBottomNavTab
import cx.aswin.boxlore.ui.announcement.InAppAnnouncementDialog
import cx.aswin.boxlore.ui.announcement.shouldSuppressWhatsNewOnPlay
import cx.aswin.boxlore.ui.libraryimport.OpmlImportDialog
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning

class MainActivity : ComponentActivity() {
    // Reactive intent state for deep-link handling
    private val intentState = mutableStateOf<android.content.Intent?>(null)
    private val warmStartIntent = mutableStateOf<android.content.Intent?>(null)

    // Player expansion trigger from notification
    private var expandPlayerTrigger by mutableLongStateOf(0L)

    // Analytics: deduplicate cold vs warm starts
    private var isFirstResumeAfterLaunch = true

    /**
     * NPS survey prefs — uses the shared instance from the application so there is only one
     * DataStore client for [cx.aswin.boxlore.core.data.UserPreferencesRepository].
     */
    private val surveyPrefs by lazy {
        (application as BoxLoreApplication).userPreferencesRepository
    }
    private val engagementCoordinator by lazy {
        (application as BoxLoreApplication).engagementPromptCoordinator
    }

    @Volatile
    private var playbackRepositoryRef: cx.aswin.boxlore.core.data.PlaybackRepository? = null

    private fun isCurrentlyPlaying(): Boolean =
        playbackRepositoryRef?.playerState?.value?.isPlaying == true

    // Google Play In-App Updates
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    private val updateLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            android.util.Log.e("AppUpdate", "Update flow failed or cancelled. Result code: ${result.resultCode}")
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
        warmStartIntent.value = intent
        handlePlayerIntent(intent)
    }

    private fun handlePlayerIntent(intent: android.content.Intent) {
        val shouldOpenPlayer = intent.getBooleanExtra("EXTRA_OPEN_PLAYER", false)
        if (shouldOpenPlayer) {
            expandPlayerTrigger = System.currentTimeMillis()
            intent.removeExtra("EXTRA_OPEN_PLAYER")
        }
        if (intent.getBooleanExtra("from_push", false)) {
            intent.removeExtra("from_push")
            AnalyticsHelper.trackNotificationTapped()
        }
    }

    override fun onResume() {
        super.onResume()
        isFirstResumeAfterLaunch = false
        PostHog.register("local_time_of_day", java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
        checkNpsSurveyTriggers()

        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    // Optional: appUpdateManager.completeUpdate() for background downloads
                }
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppUpdate", "Error checking update status", e)
        }
    }

    /**
     * Fires NPS survey trigger events on app open.
     *
     * 1. Console remote trigger: if the `survey-nps-remote-trigger` flag is enabled for this
     *    user, fire the manual trigger and reload flags so the one-shot trigger doesn't repeat.
     * 2. Deferred auto trigger: if the survey was marked pending (ep 3 reached, possibly during
     *    background playback) and hasn't fired yet, fire it now and mark it fired.
     *
     * PostHog owns the survey's display conditions (onboarding status, audience flag, wait
     * period), so this only emits the trigger events.
     */
    private fun checkNpsSurveyTriggers() {
        try {
            if (
                PostHog.isFeatureEnabled("survey-nps-remote-trigger") &&
                engagementCoordinator.canShowProactivePrompt(isCurrentlyPlaying())
            ) {
                AnalyticsHelper.trackSurveyNpsManualTrigger(source = "remote_flag")
                PostHog.reloadFeatureFlags()
            }
        } catch (e: Exception) {
            android.util.Log.e("NpsSurvey", "Remote trigger check failed", e)
        }

        lifecycleScope.launch {
            try {
                if (
                    surveyPrefs.isNpsSurveyPending() &&
                    !surveyPrefs.hasNpsSurveyFired() &&
                    engagementCoordinator.canShowProactivePrompt(isCurrentlyPlaying()) &&
                    surveyPrefs.isEngagementCooldownElapsed()
                ) {
                    AnalyticsHelper.trackSurveyNpsEligible(
                        completedEpisodes = surveyPrefs.npsSurveyCompletedCount(),
                        triggerContext = "app_open",
                    )
                    surveyPrefs.markNpsSurveyFired()
                }
            } catch (e: Exception) {
                android.util.Log.e("NpsSurvey", "Deferred trigger check failed", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        intentState.value = intent

        try {
            enableEdgeToEdge()
        } catch (e: NoClassDefFoundError) {
            android.util.Log.w("MainActivity", "enableEdgeToEdge() failed due to missing framework class, skipping", e)
        }

        AnalyticsHelper.trackFirstLaunchIfNecessary(this)
        handlePlayerIntent(intent)
        checkForUpdates()

        // Configure global Coil ImageLoader
        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder().apply {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(20, TimeUnit.SECONDS)
                }.build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            val scope = rememberCoroutineScope()
            val navController = rememberNavController()

            // Warm-start deep links when the activity is already running
            val currentWarmIntent = warmStartIntent.value
            LaunchedEffect(currentWarmIntent) {
                if (currentWarmIntent != null && currentWarmIntent.data != null) {
                    navController.handleDeepLink(currentWarmIntent)
                    warmStartIntent.value = null
                }
            }

            val navBackStackEntry = navController.currentBackStackEntryAsState().value
            val currentRoute = navBackStackEntry?.destination?.route ?: "home"

            val application = applicationContext as BoxLoreApplication
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
                val prefs = getSharedPreferences("boxcast_api_config", MODE_PRIVATE)
                prefs.edit()
                    .putString("base_url", BuildConfig.BOXCAST_API_BASE_URL)
                    .putString("public_key", BuildConfig.BOXCAST_PUBLIC_KEY)
                    .apply()
            }

            val showBottomNav = !currentRoute.startsWith("player") && currentRoute != "onboarding"
            val canGoBack = navController.previousBackStackEntry != null

            SideEffect { playbackRepositoryRef = playbackRepository }

            // Reconcile FCM topic subscriptions after a backup restore
            LaunchedEffect(Unit) {
                val sentinel = java.io.File(noBackupFilesDir, "fcm_topics_synced")
                if (!sentinel.exists()) {
                    subscriptionRepository.reconcileFcmTopicSubscriptions()
                    try {
                        sentinel.createNewFile()
                    } catch (e: Exception) {
                        android.util.Log.e("FCM_Topic", "Failed to write sentinel", e)
                    }
                }
            }

            // Onboarding ViewModel
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

            // Deferred deep linking via Install Referrer API
            LaunchedEffect(Unit) { installReferrerManager.checkInstallReferrer() }
            LaunchedEffect(installReferrerManager) {
                installReferrerManager.referralFlow.collect { referral ->
                    android.util.Log.d("MainActivityReferrer", "Received referral: $referral")
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

            // Lore queue independence: conflict dialog when queuing over a normal queue
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

            // Catch-up foreground check (respects Wi-Fi constraints, bypasses charging)
            LaunchedEffect(smartDownloadManager) {
                try {
                    val lastSyncTime = userPrefs.smartDownloadsLastSyncTimeStream.first()
                    val isEnabled = userPrefs.smartDownloadsEnabledStream.first()
                    if (isEnabled && System.currentTimeMillis() - lastSyncTime > 24 * 3600 * 1000L) {
                        android.util.Log.d("MainActivity", "Last smart sync > 24h ago. Triggering graceful catch-up sync.")
                        scope.launch(Dispatchers.IO) {
                            smartDownloadManager.performSync(isForeground = true)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to check catch-up sync status", e)
                }
            }

            val hasUserSetConsent by consentManager.hasUserSetConsent.collectAsState(initial = true)
            val crashlyticsConsent by consentManager.isCrashReportingConsented.collectAsState(initial = false)
            val currentRegion by userPrefs.regionStream.collectAsState(initial = "us")

            // Theme preferences
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

            // Bandwidth-adaptive download throttling
            var isConnectionFast by remember { mutableStateOf(true) }
            val connectivityManager = remember {
                getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            }
            DisposableEffect(connectivityManager) {
                val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: android.net.Network,
                        networkCapabilities: android.net.NetworkCapabilities,
                    ) {
                        isConnectionFast = networkCapabilities.linkDownstreamBandwidthKbps > 15000
                    }
                }
                try {
                    connectivityManager.registerDefaultNetworkCallback(callback)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to register network callback", e)
                }
                onDispose {
                    try { connectivityManager.unregisterNetworkCallback(callback) } catch (e: Exception) { /* ignore */ }
                }
            }
            LaunchedEffect(isPlaying, isConnectionFast) {
                if (isConnectionFast) {
                    DownloadSpeedLimiter.speedLimitBps = 0L
                } else if (isPlaying) {
                    DownloadSpeedLimiter.speedLimitBps = 250 * 1024L
                } else {
                    DownloadSpeedLimiter.speedLimitBps = 750 * 1024L
                }
            }

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

            // FCM topic subscriptions
            LaunchedEffect(Unit) {
                try {
                    FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                    if (BuildConfig.DEBUG) {
                        FirebaseMessaging.getInstance().subscribeToTopic("debug_users")
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("prod_users")
                    } else {
                        FirebaseMessaging.getInstance().subscribeToTopic("prod_users")
                        FirebaseMessaging.getInstance().unsubscribeFromTopic("debug_users")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Firebase", "Failed FCM init", e)
                }
            }

            // OPML import state (managed here; dialog rendered below NavHost)
            var opmlImportState by remember { mutableStateOf<OpmlImportState>(OpmlImportState.Idle) }
            var opmlImportSource by remember { mutableStateOf("welcome_screen") }
            var importTriggerKey by remember { mutableStateOf(0L) }

            fun performJsonImport(uri: android.net.Uri) {
                opmlImportState = OpmlImportState.ImportingJson
                scope.launch(Dispatchers.IO) {
                    try {
                        val jsonStr = applicationContext.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        }
                        if (jsonStr == null) {
                            AnalyticsHelper.trackOnboardingImportFailed("json", "Failed to read the JSON file.")
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                opmlImportState = OpmlImportState.Error("Failed to read the JSON file.")
                            }
                            return@launch
                        }
                        val (count, hasNotificationsEnabled) =
                            cx.aswin.boxlore.core.data.backup.LibraryBackupManager(
                                subscriptionRepository,
                                playbackRepository,
                                podcastRepository,
                                userPrefs,
                                applicationContext,
                            ).importLibraryFromJson(jsonStr)
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            opmlImportState = OpmlImportState.Success(
                                importedCount = count,
                                completedCount = 0,
                                isJson = true,
                                hasNotificationsEnabled = hasNotificationsEnabled,
                            )
                        }
                    } catch (e: Exception) {
                        AnalyticsHelper.trackOnboardingImportFailed("json", e.message)
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            opmlImportState = OpmlImportState.Error("JSON Import failed: ${e.message}")
                        }
                    }
                }
            }

            LaunchedEffect(importTriggerKey) {
                if (importTriggerKey == 0L) return@LaunchedEffect
                val state = opmlImportState
                if (state is OpmlImportState.Parsing) {
                    val uri = state.uri
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            val inputStream = applicationContext.contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                AnalyticsHelper.trackOnboardingImportFailed("opml", "Failed to open the selected file.")
                                opmlImportState = OpmlImportState.Error("Failed to open the selected file.")
                                return@withContext
                            }
                            val backupManager = cx.aswin.boxlore.core.data.backup.LibraryBackupManager(
                                subscriptionRepository,
                                playbackRepository,
                                podcastRepository,
                                context = applicationContext,
                            )
                            val feeds = backupManager.parseOpmlFeeds(inputStream)
                            inputStream.close()

                            if (feeds.isEmpty()) {
                                AnalyticsHelper.trackOnboardingImportFailed("opml", "No podcast feeds found in the OPML file.")
                                opmlImportState = OpmlImportState.Error("No podcast feeds found in the OPML file.")
                                return@withContext
                            }

                            opmlImportState = OpmlImportState.Importing(
                                currentFeedTitle = feeds.first().title,
                                progress = 0f,
                                currentCount = 0,
                                totalCount = feeds.size,
                                importedPodcasts = emptyList(),
                            )

                            val importedList = mutableListOf<Podcast>()
                            for (index in feeds.indices) {
                                val feed = feeds[index]
                                opmlImportState = OpmlImportState.Importing(
                                    currentFeedTitle = feed.title,
                                    progress = index.toFloat() / feeds.size,
                                    currentCount = index,
                                    totalCount = feeds.size,
                                    importedPodcasts = importedList.toList(),
                                )
                                val imported = backupManager.importSingleOpmlFeed(feed)
                                if (imported != null) importedList.add(imported)
                            }

                            if (importedList.isEmpty()) {
                                AnalyticsHelper.trackOnboardingImportFailed("opml", "Could not resolve or subscribe to any podcasts from this OPML file.")
                                opmlImportState = OpmlImportState.Error("Could not resolve or subscribe to any podcasts from this OPML file.")
                            } else {
                                opmlImportState = OpmlImportState.AskCompleted(
                                    importedPodcasts = importedList.toList(),
                                    selectedIds = importedList.map { it.id }.toSet(),
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("OPML_IMPORT", "Error during parsing/importing", e)
                            AnalyticsHelper.trackOnboardingImportFailed("opml", e.message)
                            opmlImportState = OpmlImportState.Error("OPML Import failed: ${e.message}")
                        }
                    }
                } else if (state is OpmlImportState.Completing) {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            val backupManager = cx.aswin.boxlore.core.data.backup.LibraryBackupManager(
                                subscriptionRepository,
                                playbackRepository,
                                podcastRepository,
                                context = applicationContext,
                            )
                            val total = state.podcastsToMark.size
                            for (index in state.podcastsToMark.indices) {
                                val podcast = state.podcastsToMark[index]
                                opmlImportState = OpmlImportState.Completing(
                                    progress = index.toFloat() / total,
                                    currentShowTitle = podcast.title,
                                    podcastsToMark = state.podcastsToMark,
                                    totalImportedCount = state.totalImportedCount,
                                    importedPodcasts = state.importedPodcasts,
                                )
                                backupManager.markAllEpisodesCompleted(podcast)
                            }
                            opmlImportState = OpmlImportState.Success(
                                importedCount = state.totalImportedCount,
                                completedCount = total,
                                isJson = false,
                                importedPodcasts = state.importedPodcasts,
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("OPML_IMPORT", "Error marking completed", e)
                            AnalyticsHelper.trackOnboardingImportFailed("opml", e.message)
                            opmlImportState = OpmlImportState.Error("Failed to mark episodes as completed: ${e.message}")
                        }
                    }
                }
            }

            // Restore last session on startup
            LaunchedEffect(Unit) { playbackRepository.restoreLastSession() }

            // Activation tracking: first_episode_played
            LaunchedEffect(isPlaying, hasLoggedFirstPlay) {
                if (isPlaying && !hasLoggedFirstPlay) {
                    AnalyticsHelper.trackFirstEpisodePlayed()
                    userPrefs.markFirstPlayLogged()
                }
            }

            // Feature announcement flag
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

            // -----------------------------------------------------------------------
            // UI
            // -----------------------------------------------------------------------
            BoxCastTheme(
                darkTheme = darkTheme,
                dynamicColor = useDynamicColor,
                themeBrand = themeBrand,
                surfaceStyle = surfaceStyle,
            ) {
                // Lore queue conflict dialog
                loreQueueConflictEpisode?.let { pendingLoreEpisode ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {
                            AnalyticsHelper.trackLoreQueueConflictResult(pendingLoreEpisode.id, "cancelled")
                            loreQueueConflictEpisode = null
                        },
                        icon = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(12.dp).size(24.dp),
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Start a Lore queue?",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        text = {
                            Text(
                                text = "This starts a fresh Lore queue and clears your current queue. " +
                                    "To keep it, open the episode and use Add to Queue instead.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        confirmButton = {
                            FilledTonalButton(
                                onClick = {
                                    loreQueueConflictEpisode = null
                                    AnalyticsHelper.trackLoreQueueConflictResult(pendingLoreEpisode.id, "start_lore_queue")
                                    scope.launch {
                                        try {
                                            playbackRepository.stopAndClearQueue()
                                            queueLoreEpisode(pendingLoreEpisode)
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "Failed to start Lore queue", e)
                                        }
                                    }
                                },
                                shape = CircleShape,
                            ) {
                                Text("Start Lore queue")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    AnalyticsHelper.trackLoreQueueConflictResult(pendingLoreEpisode.id, "cancelled")
                                    loreQueueConflictEpisode = null
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text("Keep current queue")
                            }
                        },
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // In-app announcement dialog
                if (onboardingCompleted && activeAnnouncement != null) {
                    val announcement = activeAnnouncement!!
                    val suppressWhatsNewOnPlay = remember(announcement.category) {
                        this@MainActivity.shouldSuppressWhatsNewOnPlay(announcement.category)
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
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("Announcement", "Failed to open route", e)
                                }
                            },
                        )
                    }
                }

                // Feature announcement full-screen overlay
                if (showFeatureDialog) {
                    val overlayAlpha = remember { Animatable(0f) }
                    val phase1 = remember { Animatable(0f) }
                    val phase2 = remember { Animatable(0f) }
                    val phase3 = remember { Animatable(0f) }

                    LaunchedEffect(featureAnnouncementId) {
                        AnalyticsHelper.trackFeatureAnnouncementViewed(featureAnnouncementId)
                        overlayAlpha.animateTo(1f, androidx.compose.animation.core.tween(600))
                        kotlinx.coroutines.delay(200)
                        phase1.animateTo(1f, ExpressiveMotion.SleekFadeSpec)
                        kotlinx.coroutines.delay(100)
                        phase2.animateTo(1f, ExpressiveMotion.SleekFadeSpec)
                        kotlinx.coroutines.delay(100)
                        phase3.animateTo(1f, ExpressiveMotion.SleekFadeSpec)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(100f)
                            .graphicsLayer { alpha = overlayAlpha.value }
                            .pointerInput(Unit) { /* Block touch-through */ }
                            .then(Modifier.padding(WindowInsets.navigationBars.asPaddingValues())),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpressiveAnimatedBackground(
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 40.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer { alpha = phase1.value },
                            ) {
                                BoxLoreLogo()
                                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.3f)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "now works with",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        letterSpacing = 3.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }

                            androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer { alpha = phase2.value },
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_android_auto),
                                    contentDescription = "Android Auto",
                                    modifier = Modifier.height(140.dp),
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
                                Text(
                                    text = "Android Auto",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "BoxLore now plays on Android Auto. Listen hands-free while driving.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }

                            androidx.compose.foundation.layout.Spacer(Modifier.height(40.dp))

                            Box(modifier = Modifier.graphicsLayer { alpha = phase3.value }) {
                                FilledTonalButton(
                                    onClick = {
                                        scope.launch { userPrefs.dismissFeatureAnnouncement(featureAnnouncementId) }
                                    },
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        text = "Continue",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                // -----------------------------------------------------------------------
                // Main content: NavHost (all routes) + overlays (NavBar, Player)
                // -----------------------------------------------------------------------
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) { _ ->
                        PredictiveBackWrapper(
                            enabled = canGoBack,
                            onBack = { navController.popBackStack() },
                        ) {
                            BoxLoreNavHost(
                                navController = navController,
                                application = application,
                                onboardingCompleted = onboardingCompleted,
                                onOnboardingCompleted = { onboardingCompleted = true },
                                onboardingViewModel = onboardingViewModel,
                                hasDeepLink = hasDeepLink,
                                currentEpisode = currentEpisode,
                                miniPlayerPadding = miniPlayerPadding,
                                isModeSwitching = isModeSwitching,
                                opmlCallbacks = NavOpmlCallbacks(
                                    importState = opmlImportState,
                                    onImportStateChange = { opmlImportState = it },
                                    triggerKey = importTriggerKey,
                                    onTriggerKeyChange = { importTriggerKey = it },
                                    onSourceChange = { opmlImportSource = it },
                                    performJsonImport = ::performJsonImport,
                                ),
                                onLoreQueueConflictEpisode = { loreQueueConflictEpisode = it },
                                queueLoreEpisode = queueLoreEpisode,
                                onShowFeedbackSheet = { showFeedbackSheet = true },
                                onSubmitFeedback = onSubmitFeedback,
                                showFeatureDialog = showFeatureDialog,
                                hasSeenMarkPlayedTip = hasSeenMarkPlayedTip,
                                permissionLauncher = permissionLauncher,
                                appInstanceId = appInstanceId,
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

                    // Calculate player sheet positions
                    val density = LocalDensity.current
                    val screenHeightDp = maxHeight
                    val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val appNavBarHeight = AppNavigationBarHeight
                    val containerHeight = screenHeightDp + systemNavBarHeight + 50.dp
                    val miniPlayerBottomMargin = 2.dp
                    val collapsedTargetY = with(density) {
                        (screenHeightDp - MiniPlayerHeight - appNavBarHeight - systemNavBarHeight - miniPlayerBottomMargin).toPx()
                    }

                    // Bottom navigation bar
                    if (showBottomNav) {
                        val activeTab = resolveBottomNavTab(
                            currentRoute = currentRoute,
                            backStack = navController.currentBackStack.value,
                        )
                        BoxLoreNavigationBar(
                            currentRoute = activeTab,
                            onNavigate = { route ->
                                AnalyticsHelper.trackNavTabClicked(route)
                                when {
                                    activeTab == route -> {
                                        if (route == "home") {
                                            navController.popBackStack("home", inclusive = false)
                                        } else if (route == "learn") {
                                            val popped = navController.popBackStack("learn", inclusive = false)
                                            if (!popped) {
                                                navController.navigate("learn") {
                                                    popUpTo("home") { saveState = false }
                                                    launchSingleTop = true
                                                }
                                            }
                                        } else if (route == "explore") {
                                            val popped = navController.popBackStack(ExploreTabRoutePattern, inclusive = false)
                                            if (!popped) {
                                                navController.navigate("explore?entryPoint=bottom_nav") {
                                                    popUpTo("home") { saveState = false }
                                                    launchSingleTop = true
                                                }
                                            }
                                        } else if (route == "library") {
                                            val popped = navController.popBackStack("library", inclusive = false)
                                            if (!popped) {
                                                navController.navigate("library") {
                                                    popUpTo("home") { saveState = false }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    }
                                    route == "home" -> {
                                        navController.navigate("home") {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    route == "learn" -> {
                                        navController.navigate("learn") {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    route == "explore" -> {
                                        navController.navigate("explore?entryPoint=bottom_nav") {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    route == "library" -> {
                                        navController.navigate("library") {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    else -> {
                                        val tabPattern = bottomNavTabRoutePattern(route)
                                        val popped = tabPattern != null &&
                                            navController.popBackStack(tabPattern, inclusive = false)
                                        if (!popped) {
                                            navController.navigate(
                                                if (route == "explore") "explore?entryPoint=bottom_nav" else route,
                                            ) {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }

                    // Late Night Sleep Timer nudge
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

                    // Unified Player Sheet — drawn last so it renders on top
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

                // OPML import dialog (shown above everything)
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
                                    this@MainActivity.recreate()
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

                // Feedback sheet
                if (showFeedbackSheet) {
                    val versionStr = remember {
                        try {
                            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
                        } catch (e: Exception) {
                            "unknown"
                        }
                    }
                    FeedbackSheet(
                        appVersion = versionStr,
                        onSubmit = onSubmitFeedback,
                        onRateInstead = {
                            showFeedbackSheet = false
                            try {
                                startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("market://details?id=$packageName"),
                                    ),
                                )
                            } catch (e: Exception) {
                                startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                                    ),
                                )
                            }
                        },
                        onDismissRequest = { showFeedbackSheet = false },
                    )
                }
            }
        }
    }

    private fun checkForUpdates() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                val isUpdateAvailable =
                    appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                val updatePriority = appUpdateInfo.updatePriority()
                val daysStale = appUpdateInfo.clientVersionStalenessDays() ?: 0
                val isHighPriority = updatePriority >= 4 || daysStale >= 7

                val updateType = when {
                    isHighPriority && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                        AppUpdateType.IMMEDIATE
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                        AppUpdateType.FLEXIBLE
                    else -> null
                }

                if (isUpdateAvailable && updateType != null) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            updateLauncher,
                            AppUpdateOptions.newBuilder(updateType).build(),
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("AppUpdate", "Failed to start update flow", e)
                    }
                }
            }.addOnFailureListener {
                android.util.Log.e("AppUpdate", "Failed to check for updates", it)
            }
        } catch (e: Exception) {
            android.util.Log.e("AppUpdate", "App update initialization failed", e)
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    Text(
        text = "Box.Lore",
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BoxCastTheme {
        Greeting()
    }
}
