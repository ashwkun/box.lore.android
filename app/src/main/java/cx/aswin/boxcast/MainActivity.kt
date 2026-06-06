package cx.aswin.boxcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.google.firebase.messaging.FirebaseMessaging
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.URLEncoder
import java.net.URLDecoder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cx.aswin.boxcast.core.designsystem.component.BoxCastNavigationBar
import cx.aswin.boxcast.core.designsystem.component.bottomNavDestinations
import cx.aswin.boxcast.core.designsystem.theme.BoxCastTheme
import cx.aswin.boxcast.core.designsystem.component.PredictiveBackWrapper
import cx.aswin.boxcast.feature.home.HomeRoute
import cx.aswin.boxcast.feature.player.PlayerRoute
import cx.aswin.boxcast.core.designsystem.component.ExpressiveAnimatedBackground
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning

// PixelPlayer-inspired transition specs
private const val TRANSITION_DURATION = 350
private val TRANSITION_EASING = FastOutSlowInEasing

class MainActivity : ComponentActivity() {
    // State for Player Expansion (Notification handling)
    private var expandPlayerTrigger by androidx.compose.runtime.mutableLongStateOf(0L)
    
    // Analytics: Deduplicate cold vs warm starts
    private var isFirstResumeAfterLaunch = true
    


    // Google Play In-App Updates
    private val appUpdateManager by lazy { com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(this) }
    private val updateLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            android.util.Log.e("AppUpdate", "Update flow failed or cancelled. Result code: ${result.resultCode}")
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayerIntent(intent)
    }

    private fun handlePlayerIntent(intent: android.content.Intent) {
        val shouldOpenPlayer = intent.getBooleanExtra("EXTRA_OPEN_PLAYER", false)
        if (shouldOpenPlayer) {
            expandPlayerTrigger = System.currentTimeMillis()
            // Clear extra to prevent re-triggering on rotation/re-creation
            intent.removeExtra("EXTRA_OPEN_PLAYER") 
        }

        if (intent.getBooleanExtra("from_push", false)) {
            intent.removeExtra("from_push")
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackNotificationTapped()
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Analytics: Track warm resumes (skip the first one since onCreate handles cold starts)
        // Native PostHog session tracking handles warm resumes now
        isFirstResumeAfterLaunch = false
        
        // Register the local time of day as a Super Property so it attaches to ALL events
        com.posthog.PostHog.register("local_time_of_day", java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
        
        // Check for in-progress updates
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                // For FLEXIBLE updates, check if downloaded and ready to install
                if (appUpdateInfo.installStatus() == com.google.android.play.core.install.model.InstallStatus.DOWNLOADED) {
                    // Optional: If you want to force install when it finishes downloading while the app is in background
                    // appUpdateManager.completeUpdate()
                }
                
                // For IMMEDIATE updates, resume if stalled/interrupted
                if (appUpdateInfo.updateAvailability() == com.google.android.play.core.install.model.UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        com.google.android.play.core.appupdate.AppUpdateOptions.newBuilder(com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE).build()
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppUpdate", "Error checking update status", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // On first launch (onboarding not completed), skip the splash screen entirely
        // so the cinematic onboarding animation is the only brand moment.
        val onboardingPrefs = getSharedPreferences("boxcast_prefs", MODE_PRIVATE)
        val isOnboardingDone = onboardingPrefs.getBoolean("onboarding_completed", false)
        if (!isOnboardingDone) {
            splashScreen.setKeepOnScreenCondition { false }
        }
        
        enableEdgeToEdge()
        
        // Analytics: Track cold start
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFirstLaunchIfNecessary(this)
        
        // Handle initial launch intent
        handlePlayerIntent(intent)
        
        // Check for App Updates on launch
        checkForUpdates()
        
        // Configure global Coil ImageLoader for better performance and reliability
        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // Bumped from 2% to 5% since proxy WebPs are much smaller
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder().apply {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(20, TimeUnit.SECONDS)
                    if (cx.aswin.boxcast.BuildConfig.DEBUG) {
                        try {
                            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                                object : javax.net.ssl.X509TrustManager {
                                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                                }
                            )
                            
                            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
                            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                            
                            sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                            hostnameVerifier { _, _ -> true }
                        } catch (e: Exception) {
                            android.util.Log.e("CoilLoader", "Failed to configure trust-all certificates for debug", e)
                        }
                    }
                }.build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // Force caching even if servers say no
            .build()
            
        Coil.setImageLoader(imageLoader)
        
        setContent {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: "home"
            
            // API config from BuildConfig
            val apiBaseUrl = BuildConfig.BOXCAST_API_BASE_URL
            val publicKey = BuildConfig.BOXCAST_PUBLIC_KEY
            
            LaunchedEffect(Unit) {
                // Save to SharedPreferences so the background Service can access them
                val prefs = getSharedPreferences("boxcast_api_config", MODE_PRIVATE)
                prefs.edit()
                    .putString("base_url", apiBaseUrl)
                    .putString("public_key", publicKey)
                    .apply()
            }
            
            // Show bottom nav on all screens except player and onboarding
            val showBottomNav = !currentRoute.startsWith("player") && currentRoute != "onboarding"
            
            // Check if we can go back (for predictive back)
            val canGoBack = navController.previousBackStackEntry != null
            
            // App-level Repositories
            val application = (applicationContext as android.app.Application)
            val database = remember { cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(application) }
            
            // 1. Core Data Sources
            // Create a shared PodcastRepository instance
            val podcastRepository = remember { cx.aswin.boxcast.core.data.PodcastRepository(apiBaseUrl, publicKey, application) }
            
            // 2. Queue Repository (Must come before PlaybackRepo)
            val queueRepository = remember { cx.aswin.boxcast.core.data.QueueRepository(database, podcastRepository) }

            // 3. Playback Repository (Depends on QueueRepo)
            val playbackRepository = remember { cx.aswin.boxcast.core.data.PlaybackRepository(application, database.listeningHistoryDao(), queueRepository, podcastRepository) }
            val downloadRepository = remember { cx.aswin.boxcast.core.data.DownloadRepository(application, database) }
            
            // 4. Subscription Repository
            val subscriptionRepository = remember { cx.aswin.boxcast.core.data.SubscriptionRepository(database.podcastDao()) }
            
            // Privacy & Preferences
            val consentManager = remember { cx.aswin.boxcast.core.data.privacy.ConsentManager(application) }
            
            val userPrefs = remember { cx.aswin.boxcast.core.data.UserPreferencesRepository(application) }
            
            // 6. Onboarding ViewModel
            val onboardingViewModel = remember {
                cx.aswin.boxcast.feature.onboarding.OnboardingViewModel(application, podcastRepository, subscriptionRepository, userPrefs)
            }
            val onboardingCompleted = remember { onboardingViewModel.isOnboardingCompleted() }
            
            // 5. Smart Queue Engine
            val smartQueueEngine = remember { cx.aswin.boxcast.core.data.DefaultSmartQueueEngine(podcastRepository, database.listeningHistoryDao(), subscriptionRepository) }
            
            // QueueManager (Singleton-ish) - Needs to be provided to ViewModels/Screens
            val queueManager = remember { 
                cx.aswin.boxcast.core.data.QueueManager(queueRepository, smartQueueEngine, playbackRepository, podcastRepository)
            }
            
            // Check Consent Status
            // Initial = true to prevent flashing dialog while DataStore loads.
            // If user hasn't set consent, this will become false shortly and show dialog.
            val hasUserSetConsent by consentManager.hasUserSetConsent.collectAsState(initial = true)


            val crashlyticsConsent by consentManager.isCrashReportingConsented.collectAsState(initial = false)
            val currentRegion by userPrefs.regionStream.collectAsState(initial = "us")
            
            // Theme Preferences
            val themeConfig by userPrefs.themeConfigStream.collectAsState(initial = "system")
            val skipBehavior by userPrefs.skipBehaviorStream.collectAsState(initial = "just_skip")
            val hideCompletedInHome by userPrefs.hideCompletedInHomeStream.collectAsState(initial = true)
            val hideCompletedInSubs by userPrefs.hideCompletedInSubsStream.collectAsState(initial = true)
            val hideCompletedInShowDetails by userPrefs.hideCompletedInShowDetailsStream.collectAsState(initial = false)
            val useDynamicColor by userPrefs.useDynamicColorStream.collectAsState(initial = true)
            val themeBrand by userPrefs.themeBrandStream.collectAsState(initial = "violet")
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
            val isRadioMode by userPrefs.isRadioModeStream.collectAsState(initial = false)
            val isModeSwitching by cx.aswin.boxcast.feature.home.ModeSwitchState.isSwitching.collectAsState()
            
            val darkTheme = when(themeConfig) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            var appInstanceId by remember { mutableStateOf<String?>(null) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackNotificationPermissionDecided(isGranted)
            }
            
            LaunchedEffect(Unit) {
                // Firebase Analytics removed for PostHog migration
                
                try {
                    // Subscribe to FCM topic for global announcements
                    FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                    
                    // Subscribe to environment-specific topic safely by clearing the antagonist topic
                    if (cx.aswin.boxcast.BuildConfig.DEBUG) {
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

            var opmlImportState by remember { mutableStateOf<OpmlImportState>(OpmlImportState.Idle) }
            var importTriggerKey by remember { mutableStateOf(0L) }

            LaunchedEffect(importTriggerKey) {
                if (importTriggerKey == 0L) return@LaunchedEffect
                val state = opmlImportState
                if (state is OpmlImportState.Parsing) {
                    val uri = state.uri
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val inputStream = applicationContext.contentResolver.openInputStream(uri)
                            if (inputStream == null) {
                                opmlImportState = OpmlImportState.Error("Failed to open the selected file.")
                                return@withContext
                            }
                            val backupManager = cx.aswin.boxcast.core.data.backup.LibraryBackupManager(
                                subscriptionRepository,
                                playbackRepository,
                                podcastRepository
                            )
                            val feeds = backupManager.parseOpmlFeeds(inputStream)
                            inputStream.close()
                            
                            if (feeds.isEmpty()) {
                                opmlImportState = OpmlImportState.Error("No podcast feeds found in the OPML file.")
                                return@withContext
                            }
                            
                            opmlImportState = OpmlImportState.Importing(
                                currentFeedTitle = feeds.first().title,
                                progress = 0f,
                                currentCount = 0,
                                totalCount = feeds.size,
                                importedPodcasts = emptyList()
                            )
                            
                            val importedList = mutableListOf<cx.aswin.boxcast.core.model.Podcast>()
                            for (index in feeds.indices) {
                                val feed = feeds[index]
                                opmlImportState = OpmlImportState.Importing(
                                    currentFeedTitle = feed.title,
                                    progress = index.toFloat() / feeds.size,
                                    currentCount = index,
                                    totalCount = feeds.size,
                                    importedPodcasts = importedList.toList()
                                )
                                val imported = backupManager.importSingleOpmlFeed(feed)
                                if (imported != null) {
                                    importedList.add(imported)
                                }
                            }
                            
                            if (importedList.isEmpty()) {
                                opmlImportState = OpmlImportState.Error("Could not resolve or subscribe to any podcasts from this OPML file.")
                            } else {
                                opmlImportState = OpmlImportState.AskCompleted(
                                    importedPodcasts = importedList.toList(),
                                    selectedIds = importedList.map { it.id }.toSet()
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("OPML_IMPORT", "Error during parsing/importing", e)
                            opmlImportState = OpmlImportState.Error("OPML Import failed: ${e.message}")
                        }
                    }
                } else if (state is OpmlImportState.Completing) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val backupManager = cx.aswin.boxcast.core.data.backup.LibraryBackupManager(
                                subscriptionRepository,
                                playbackRepository,
                                podcastRepository
                            )
                            val total = state.podcastsToMark.size
                            for (index in state.podcastsToMark.indices) {
                                val podcast = state.podcastsToMark[index]
                                opmlImportState = state.copy(
                                    progress = index.toFloat() / total,
                                    currentShowTitle = podcast.title
                                )
                                backupManager.markAllEpisodesCompleted(podcast)
                            }
                            
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    applicationContext,
                                    "Successfully imported ${state.totalImportedCount} podcasts. Marked ${total} as completed.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                
                                if (currentRoute == "onboarding") {
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackOnboardingImportCompleted(
                                        "opml",
                                        state.totalImportedCount,
                                        onboardingViewModel.getGenreScreenTimeSpent(),
                                        onboardingViewModel.getTotalOnboardingTime()
                                    )
                                    onboardingViewModel.markOnboardingCompletedSilent {
                                        navController.navigate("home") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                }
                            }
                            opmlImportState = OpmlImportState.Idle
                        } catch (e: Exception) {
                            android.util.Log.e("OPML_IMPORT", "Error marking completed", e)
                            opmlImportState = OpmlImportState.Error("Failed to mark episodes as completed: ${e.message}")
                        }
                    }
                }
            }
            
            val scope = rememberCoroutineScope() // Scope for playback actions
            
            // Restore last session on app startup
            LaunchedEffect(Unit) {
                playbackRepository.restoreLastSession()
            }

            // Activation Tracking (first_episode_played)
            LaunchedEffect(isPlaying, hasLoggedFirstPlay) {
                if (isPlaying && !hasLoggedFirstPlay) {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFirstEpisodePlayed()
                    userPrefs.markFirstPlayLogged()
                }
            }

            BoxCastTheme(
                darkTheme = darkTheme,
                dynamicColor = useDynamicColor,
                themeBrand = themeBrand
            ) {
                // Show Announcement Dialog if onboarding is completed
                if (onboardingCompleted && activeAnnouncement != null) {
                    val announcement = activeAnnouncement!!
                    val context = LocalContext.current
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { 
                            scope.launch { userPrefs.clearAnnouncement() }
                        },
                        title = { Text(text = announcement.title) },
                        text = { 

                            androidx.compose.foundation.layout.Column {
                                if (!announcement.imageUrl.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = announcement.imageUrl,
                                        contentDescription = "Announcement Image",
                                        modifier = androidx.compose.ui.Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .padding(bottom = 16.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                @androidx.compose.runtime.Composable
                                fun parseSimpleMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
                                    return androidx.compose.ui.text.buildAnnotatedString {
                                        var currentIndex = 0
                                        val regex = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*")
                                        val matches = regex.findAll(text)
                                        for (match in matches) {
                                            append(text.substring(currentIndex, match.range.first))
                                            if (match.groups[1] != null) {
                                                withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                                                    append(match.groupValues[1])
                                                }
                                            } else if (match.groups[2] != null) {
                                                withStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                                                    append(match.groupValues[2])
                                                }
                                            }
                                            currentIndex = match.range.last + 1
                                        }
                                        append(text.substring(currentIndex))
                                    }
                                }
                                
                                Text(text = parseSimpleMarkdown(announcement.body)) 
                            }
                        },
                        confirmButton = {
                            if (!announcement.route.isNullOrBlank()) {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        scope.launch { userPrefs.clearAnnouncement() }
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(announcement.route)
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("Announcement", "Failed to open route", e)
                                        }
                                    }
                                ) {
                                    Text("View")
                                }
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { scope.launch { userPrefs.clearAnnouncement() } }
                            ) {
                                Text("Dismiss")
                            }
                        }
                    )
                }

                // Currently bundled feature announcements in this app version
                val featureAnnouncementId = "android_auto_1_3_6"
                
                // We now check PostHog for the currently active feature announcement flag.
                // It should return a string matching one of our bundled features.
                var activeFeatureFlag by remember { mutableStateOf<String?>(null) }
                
                LaunchedEffect(dismissedFeatureVersion) {
                    // Only fetch the flag (and consume an event) if the user hasn't already dismissed it
                    if (dismissedFeatureVersion != featureAnnouncementId && dismissedFeatureVersion != "android_auto_1.3.6") {
                        activeFeatureFlag = com.posthog.PostHog.getFeatureFlag("active_feature_announcement") as? String
                        com.posthog.PostHog.reloadFeatureFlags {
                            activeFeatureFlag = com.posthog.PostHog.getFeatureFlag("active_feature_announcement") as? String
                        }
                    }
                }
                
                val showFeatureDialog = currentRoute == "home" && 
                                        activeFeatureFlag == featureAnnouncementId && 
                                        dismissedFeatureVersion != featureAnnouncementId && 
                                        dismissedFeatureVersion != "android_auto_1.3.6" && // Prevent redisplay for users on older builds
                                        activeAnnouncement == null
                

                
                if (showFeatureDialog) {
                    // Staggered animation phases with smooth entrance
                    val overlayAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
                    val phase1 = remember { androidx.compose.animation.core.Animatable(0f) }
                    val phase2 = remember { androidx.compose.animation.core.Animatable(0f) }
                    val phase3 = remember { androidx.compose.animation.core.Animatable(0f) }
                    
                    LaunchedEffect(featureAnnouncementId) {
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFeatureAnnouncementViewed(featureAnnouncementId)
                        // First: smooth overlay fade-in
                        overlayAlpha.animateTo(1f, androidx.compose.animation.core.tween(600))
                        // Then: coordinated cascading fades (Header -> Body -> Action)
                        kotlinx.coroutines.delay(200)
                        phase1.animateTo(1f, ExpressiveMotion.SleekFadeSpec) // Phase 1: Brand (Logo + Label)
                        kotlinx.coroutines.delay(100)
                        phase2.animateTo(1f, ExpressiveMotion.SleekFadeSpec) // Phase 2: Context (Hero + Description)
                        kotlinx.coroutines.delay(100)
                        phase3.animateTo(1f, ExpressiveMotion.SleekFadeSpec) // Phase 3: Action (Continue Button)
                    }
                    
                    // Full-screen overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(100f)
                            .graphicsLayer { alpha = overlayAlpha.value }
                            .pointerInput(Unit) { /* Block all touch-through to content below */ }
                            .then(
                                Modifier.padding(
                                    WindowInsets.navigationBars.asPaddingValues()
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Dynamic Expressive Background (Morphing shapes + Dynamic Colors)
                        ExpressiveAnimatedBackground(
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        ) {
                            // Phase 1: Header Group (Logo + Label)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer { alpha = phase1.value }
                            ) {
                                cx.aswin.boxcast.core.designsystem.components.BoxCastLogo()
                                
                                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                                
                                // Divider line
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.3f)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                )
                                
                                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                                
                                Text(
                                    text = "now works with",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        letterSpacing = 3.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            
                            androidx.compose.foundation.layout.Spacer(Modifier.height(32.dp))
                            
                            // Phase 2: Body Group (Hero + Description)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer { alpha = phase2.value }
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_android_auto),
                                    contentDescription = "Android Auto",
                                    modifier = Modifier.height(140.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                                
                                androidx.compose.foundation.layout.Spacer(Modifier.height(40.dp))
                                
                                Text(
                                    text = "Your podcasts, on the road.\nConnect to your car and listen hands-free.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }
                            
                            androidx.compose.foundation.layout.Spacer(Modifier.height(40.dp))
                            
                            // Phase 3: Action Group (Button)
                            Box(
                                modifier = Modifier.graphicsLayer { alpha = phase3.value }
                            ) {
                                
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = {
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFeatureAnnouncementDismissed(featureAnnouncementId)
                                        scope.launch { userPrefs.dismissFeatureAnnouncement(featureAnnouncementId) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .expressiveClickable {
                                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackFeatureAnnouncementDismissed(featureAnnouncementId)
                                            scope.launch { userPrefs.dismissFeatureAnnouncement(featureAnnouncementId) }
                                        }
                                ) {
                                    Text(
                                        text = "Continue",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface // Match content background
                    ) { innerPadding ->
                        PredictiveBackWrapper(
                            enabled = canGoBack,
                            onBack = { navController.popBackStack() }
                        ) {

                            
                            // Define route order for directional transitions
                            val routeOrder = mapOf(
                                "home" to 0,
                                "explore" to 1,
                                "library" to 2
                            )
                            
                            fun getRouteIndex(route: String?): Int {
                                if (route == null) return 0
                                if (route == "home") return 0
                                if (route.startsWith("explore")) return 1
                                if (route == "library" || route.startsWith("library/subscriptions")) return 2
                                
                                // Detail screens are "deeper" -> higher index
                                if (route.startsWith("podcast/")) return 10
                                if (route.startsWith("episode/")) return 11
                                if (route.startsWith("library/")) return 12 // Library sub-screens
                                
                                return routeOrder[route] ?: 0
                            }

                            fun isTabToTab(fromRoute: String?, toRoute: String?): Boolean {
                                val fromIndex = getRouteIndex(fromRoute)
                                val toIndex = getRouteIndex(toRoute)
                                return fromIndex < 10 && toIndex < 10
                            }

                            NavHost(
                                navController = navController,
                                startDestination = if (onboardingCompleted) "home" else "onboarding",
                                modifier = Modifier, // No padding(innerPadding) -> Fixes GAP issue
                                enterTransition = {
                                    val fromRoute = initialState.destination.route
                                    val toRoute = targetState.destination.route
                                    val fromIndex = getRouteIndex(fromRoute)
                                    val toIndex = getRouteIndex(toRoute)
                                    if (isTabToTab(fromRoute, toRoute)) {
                                        if (toIndex > fromIndex) {
                                            slideInHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), initialOffsetX = { it }) 
                                        } else {
                                            slideInHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), initialOffsetX = { -it })
                                        }
                                    } else {
                                        if (toIndex > fromIndex) {
                                            // Moving Right (Push Left)
                                            slideInHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), initialOffsetX = { it }) 
                                        } else {
                                            // Moving Left (Push Right)
                                            slideInHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), initialOffsetX = { -it })
                                        }
                                    }
                                },
                                exitTransition = {
                                    val fromRoute = initialState.destination.route
                                    val toRoute = targetState.destination.route
                                    val fromIndex = getRouteIndex(fromRoute)
                                    val toIndex = getRouteIndex(toRoute)
                                    if (isTabToTab(fromRoute, toRoute)) {
                                        if (toIndex > fromIndex) {
                                            slideOutHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), targetOffsetX = { -it })
                                        } else {
                                            slideOutHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), targetOffsetX = { it })
                                        }
                                    } else {
                                        if (toIndex > fromIndex) {
                                            // Moving Right (Push Left) -> Exit to Left
                                            slideOutHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), targetOffsetX = { -it / 3 }) + fadeOut(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING))
                                        } else {
                                            // Moving Left (Push Right) -> Exit to Right
                                            slideOutHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), targetOffsetX = { it / 3 }) + fadeOut(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING))
                                        }
                                    }
                                },
                                popEnterTransition = {
                                    val fromRoute = initialState.destination.route
                                    val toRoute = targetState.destination.route
                                    val fromIndex = getRouteIndex(fromRoute)
                                    val toIndex = getRouteIndex(toRoute)
                                    if (isTabToTab(fromRoute, toRoute)) {
                                        if (toIndex > fromIndex) {
                                            slideInHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), initialOffsetX = { it })
                                        } else {
                                            slideInHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), initialOffsetX = { -it })
                                        }
                                    } else {
                                        if (toIndex > fromIndex) {
                                            // Popping "Forward" (rare, usually popping back) -> Slide In Right
                                             slideInHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), initialOffsetX = { it }) + fadeIn(animationSpec = tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))
                                        } else {
                                            // Popping Back (e.g. Back from Detail) -> Slide In Left (or Center)
                                            slideInHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), initialOffsetX = { -it / 3 }) + fadeIn(animationSpec = tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))
                                        }
                                    }
                                },
                                popExitTransition = {
                                    val fromRoute = initialState.destination.route
                                    val toRoute = targetState.destination.route
                                    val fromIndex = getRouteIndex(fromRoute)
                                    val toIndex = getRouteIndex(toRoute)
                                    if (isTabToTab(fromRoute, toRoute)) {
                                        if (toIndex > fromIndex) {
                                            slideOutHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), targetOffsetX = { -it })
                                        } else {
                                            slideOutHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), targetOffsetX = { it })
                                        }
                                    } else {
                                         if (toIndex > fromIndex) {
                                            // Popping Forward -> Exit Left
                                            slideOutHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), targetOffsetX = { -it }) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))
                                        } else {
                                            // Popping Back -> Exit Right
                                            slideOutHorizontally(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING), targetOffsetX = { it }) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))
                                        }
                                    }
                                }
                            ) {
                            // Onboarding
                            composable("onboarding") {
                                cx.aswin.boxcast.feature.onboarding.OnboardingScreen(
                                    viewModel = onboardingViewModel,
                                    onComplete = {
                                        navController.navigate("home") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    },
                                    onImportJson = { uri ->
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { android.widget.Toast.makeText(application, "Importing JSON...", android.widget.Toast.LENGTH_SHORT).show() }
                                                val jsonStr = application.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return@launch
                                                val count = cx.aswin.boxcast.core.data.backup.LibraryBackupManager(subscriptionRepository, playbackRepository, podcastRepository).importLibraryFromJson(jsonStr)
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { 
                                                    android.widget.Toast.makeText(application, "Imported $count items", android.widget.Toast.LENGTH_SHORT).show()
                                                    // Analytics: Track import completion
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackOnboardingImportCompleted("json", count, onboardingViewModel.getGenreScreenTimeSpent(), onboardingViewModel.getTotalOnboardingTime())
                                                    onboardingViewModel.markOnboardingCompletedSilent {
                                                        navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                                                    }
                                                }
                                            } catch(e: Exception) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { 
                                                    android.widget.Toast.makeText(application, "Import Failed", android.widget.Toast.LENGTH_SHORT).show() 
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackOnboardingImportFailed("json", e.message)
                                                }
                                            }
                                        }
                                    },
                                    onImportOpml = { uri ->
                                        opmlImportState = OpmlImportState.Parsing(uri)
                                        importTriggerKey = System.currentTimeMillis()
                                    }
                                )
                            }

                            // Main tabs
                            composable("home") {
                                androidx.compose.runtime.LaunchedEffect(showFeatureDialog) {
                                    // Request Notification Permission for Android 13+ only after feature dialog is dismissed
                                    if (!showFeatureDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                         if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                             cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackNotificationPermissionRequested()
                                             permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                         }
                                    }
                                }
                                HomeRoute(
                                    apiBaseUrl = apiBaseUrl,
                                    publicKey = publicKey,
                                    playbackRepository = playbackRepository,
                                    navController = navController,
                                    onPodcastClick = { podcast, entryPointStr, genreStr, depthVal ->
                                        if (entryPointStr == "home_hero_card") {
                                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTimeBlockTapped()
                                        }
                                        var route = "podcast/${podcast.id}"
                                        val params = mutableListOf<String>()
                                        if (entryPointStr != null) params.add("entryPoint=$entryPointStr")
                                        if (genreStr != null) params.add("genre=$genreStr")
                                        if (depthVal != null) params.add("depth=$depthVal")
                                        if (params.isNotEmpty()) route += "?" + params.joinToString("&")
                                        navController.navigate(route)
                                    },
                                    onPlayClick = { podcast, bundle -> 
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTimeBlockTapped()
                                        // Start Playback via QueueManager (Smart Queue)
                                        val episode = podcast.latestEpisode
                                        if (episode != null) {
                                            // No scope needed? QueueManager launches on its own scope Main? 
                                            // Wait, playEpisode is not suspend, it launches scope.
                                            queueManager.playEpisode(episode, podcast, entryPointContext = bundle)
                                        }
                                        // Do not navigate, just play. Mini player appears.
                                    },
                                    onHeroArrowClick = { heroItem, carouselPos ->
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTimeBlockTapped()
                                        val ep = heroItem.podcast.latestEpisode
                                        if (ep != null) {
                                            fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                                            val entryPointStr = "home_hero_${heroItem.type.name.lowercase()}"
                                            navController.navigate(
                                                "episode/${ep.id}/${encode(ep.title)}/" +
                                                "${encode(ep.description.take(500))}/" +
                                                "${encode(ep.imageUrl)}/" +
                                                "${encode(ep.audioUrl)}/" +
                                                "${ep.duration}/${heroItem.podcast.id}/" +
                                                "${encode(heroItem.podcast.title)}" +
                                                "?entryPoint=$entryPointStr&carouselPosition=$carouselPos"
                                            )
                                        } else {
                                            navController.navigate("podcast/${heroItem.podcast.id}")
                                        }
                                    },
                                    onEpisodeClick = { episode, podcast, entryPointStr ->
                                        if (entryPointStr == "home_hero_card") {
                                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTimeBlockTapped()
                                        }
                                        fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                                        val entryPointQuery = if (entryPointStr != null) "?entryPoint=$entryPointStr" else ""
                                        navController.navigate(
                                            "episode/${encode(episode.id)}/${encode(episode.title)}/" +
                                            "${encode(episode.description.take(500))}/" +
                                            "${encode(episode.imageUrl)}/" +
                                            "${encode(episode.audioUrl)}/" +
                                            "${episode.duration}/${encode(podcast.id)}/" +
                                            "${encode(podcast.title)}" +
                                            entryPointQuery
                                        )
                                    },
                                    onCuratedEpisodeClick = { episode, podcast, vibeId, carouselPos ->
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackTimeBlockTapped()
                                        fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                                        navController.navigate(
                                            "episode/${encode(episode.id)}/${encode(episode.title)}/" +
                                            "${encode(episode.description.take(500))}/" +
                                            "${encode(episode.imageUrl)}/" +
                                            "${encode(episode.audioUrl)}/" +
                                            "${episode.duration}/${encode(podcast.id)}/" +
                                            "${encode(podcast.title)}" +
                                            "?entryPoint=home_curated_timeblock" +
                                            "&vibeId=${encode(vibeId)}" +
                                            "&carouselPosition=$carouselPos"
                                        )
                                    },
                                    onNavigateToLibrary = {
                                        navController.navigate("library/subscriptions") {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToLatestEpisodes = {
                                        navController.navigate("library/subscriptions?tab=1")
                                    },
                                    onNavigateToExplore = { category, entryPoint, tab ->
                                        val catQuery = if (category != null) "category=$category&" else ""
                                        val tabQuery = if (tab != null) "tab=$tab&" else ""
                                        val route = "explore?${catQuery}${tabQuery}entryPoint=${entryPoint ?: "home"}"
                                        navController.navigate(route) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    },
                                    onNavigateToPlayStoreReview = {
                                        // Launch Google Play In-App Review API
                                        val activity = this@MainActivity
                                        val reviewManager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
                                        reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                reviewManager.launchReviewFlow(activity, task.result)
                                            }
                                        }
                                    },
                                    onSubmitFeedback = { category, message, version, email ->
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val feedbackUrl = "${apiBaseUrl}/feedback"
                                                android.util.Log.d("Feedback", "Posting to: $feedbackUrl")
                                                val url = java.net.URL(feedbackUrl)
                                                val conn = url.openConnection() as java.net.HttpURLConnection
                                                conn.requestMethod = "POST"
                                                conn.setRequestProperty("Content-Type", "application/json")
                                                conn.setRequestProperty("X-App-Key", publicKey)
                                                conn.doOutput = true
                                                conn.connectTimeout = 10000
                                                conn.readTimeout = 10000
                                                
                                                val json = org.json.JSONObject().apply {
                                                    put("category", category)
                                                    put("message", message)
                                                    put("appVersion", version)
                                                    if (email.isNotBlank()) {
                                                        put("email", email)
                                                    }
                                                }
                                                
                                                conn.outputStream.bufferedWriter().use { it.write(json.toString()) }
                                                val code = conn.responseCode
                                                android.util.Log.d("Feedback", "Response code: $code")
                                                if (code !in 200..299) {
                                                    val errBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "n/a" }
                                                    android.util.Log.e("Feedback", "Server error $code: $errBody")
                                                }
                                                conn.disconnect()
                                                code in 200..299
                                            } catch (e: Exception) {
                                                android.util.Log.e("Feedback", "Submit failed: ${e.javaClass.simpleName}: ${e.message}", e)
                                                false
                                            }
                                        }
                                    },
                                    onResetSleepNudge = { playbackRepository.resetSleepNudgeForTesting() },
                                    onClearSleepTimer = { playbackRepository.setSleepTimer(0) }
                                )
                            }
                            
                            composable("settings") {
                                cx.aswin.boxcast.feature.home.ProfileScreen(
                                    currentRegion = currentRegion,
                                    onSetRegion = { region -> 
                                        scope.launch { userPrefs.setRegion(region) }
                                    },
                                    onBack = { navController.popBackStack() },
                                    onResetAnalytics = {
                                        scope.launch {
                                            try {
                                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.resetIdentity()
                                                consentManager.clearConsent()
                                            } catch (e: Exception) {
                                                android.util.Log.e("Settings", "Failed to reset analytics", e)
                                            }
                                        }
                                    },
                                    appInstanceId = appInstanceId,
                                    // Theme Props
                                    currentThemeConfig = themeConfig,
                                    isDynamicColorEnabled = useDynamicColor,
                                    currentThemeBrand = themeBrand,
                                    onSetThemeConfig = { config -> scope.launch { userPrefs.setThemeConfig(config) } },
                                    onToggleDynamicColor = { enabled -> scope.launch { userPrefs.setUseDynamicColor(enabled) } },
                                    onSetThemeBrand = { brand -> scope.launch { userPrefs.setThemeBrand(brand) } },
                                    skipBehavior = skipBehavior,
                                    onSetSkipBehavior = { behavior -> scope.launch { userPrefs.setSkipBehavior(behavior) } },
                                     hideCompletedInHome = hideCompletedInHome,
                                     onSetHideCompletedInHome = { hide -> scope.launch { userPrefs.setHideCompletedInHome(hide) } },
                                     hideCompletedInSubs = hideCompletedInSubs,
                                     onSetHideCompletedInSubs = { hide -> scope.launch { userPrefs.setHideCompletedInSubs(hide) } },
                                     hideCompletedInShowDetails = hideCompletedInShowDetails,
                                     onSetHideCompletedInShowDetails = { hide -> scope.launch { userPrefs.setHideCompletedInShowDetails(hide) } },
                                    onExportJson = { uri -> 
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val backupJson = cx.aswin.boxcast.core.data.backup.LibraryBackupManager(subscriptionRepository, playbackRepository, podcastRepository).exportLibraryAsJson()
                                                application.contentResolver.openOutputStream(uri)?.use { it.write(backupJson.toByteArray()) }
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { android.widget.Toast.makeText(application, "Library Exported Successfully", android.widget.Toast.LENGTH_SHORT).show() }
                                            } catch(e: Exception){
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { android.widget.Toast.makeText(application, "Failed to export: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
                                            }
                                        }
                                    },
                                    onImportJson = { uri ->
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { android.widget.Toast.makeText(application, "Importing JSON...", android.widget.Toast.LENGTH_SHORT).show() }
                                                val jsonStr = application.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return@launch
                                                val count = cx.aswin.boxcast.core.data.backup.LibraryBackupManager(subscriptionRepository, playbackRepository, podcastRepository).importLibraryFromJson(jsonStr)
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { android.widget.Toast.makeText(application, "Imported $count items", android.widget.Toast.LENGTH_SHORT).show() }
                                            } catch(e: Exception) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { android.widget.Toast.makeText(application, "Import Failed", android.widget.Toast.LENGTH_SHORT).show() }
                                            }
                                        }
                                    },
                                    onImportOpml = { uri ->
                                        opmlImportState = OpmlImportState.Parsing(uri)
                                        importTriggerKey = System.currentTimeMillis()
                                    }
                                )
                            }
                            
                            composable(
                                 route = "explore?category={category}&entryPoint={entryPoint}&tab={tab}",
                                 arguments = listOf(
                                     navArgument("category") { 
                                         type = NavType.StringType
                                         nullable = true
                                         defaultValue = null 
                                     },
                                     navArgument("entryPoint") {
                                         type = NavType.StringType
                                         nullable = true
                                         defaultValue = "bottom_nav"
                                     },
                                     navArgument("tab") {
                                         type = NavType.StringType
                                         nullable = true
                                         defaultValue = null
                                     }
                                 )
                             ) { backStackEntry -> 

                                val podcastDao = remember { database.podcastDao() }
                                val subscriptionRepository = remember { cx.aswin.boxcast.core.data.SubscriptionRepository(podcastDao) }
                                val podcastRepository = remember { cx.aswin.boxcast.core.data.PodcastRepository(apiBaseUrl, publicKey, application) }
                                
                                // Handle Argument
                                val category = backStackEntry.arguments?.getString("category")
                                val entryPoint = backStackEntry.arguments?.getString("entryPoint") ?: "bottom_nav"
                                val tab = backStackEntry.arguments?.getString("tab")
                                
                                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxcast.feature.explore.ExploreViewModel>(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return cx.aswin.boxcast.feature.explore.ExploreViewModel(
                                                application,
                                                podcastRepository,
                                                subscriptionRepository, // Updated to take repo
                                                userPrefs,
                                                playbackRepository,
                                                initialCategory = category,
                                                initialTab = tab
                                            ) as T
                                        }
                                    }
                                )
                                
                                cx.aswin.boxcast.feature.explore.ExploreScreen(
                                    viewModel = viewModel,
                                    entryPoint = entryPoint,
                                    onPodcastClick = { podcastId, entryPointStr, genreStr, depthVal ->
                                        var route = "podcast/$podcastId"
                                        val params = mutableListOf<String>()
                                        if (entryPointStr != null) params.add("entryPoint=$entryPointStr")
                                        if (genreStr != null) params.add("genre=$genreStr")
                                        if (depthVal != null) params.add("depth=$depthVal")
                                        if (params.isNotEmpty()) route += "?" + params.joinToString("&")
                                        navController.navigate(route)
                                    },
                                    onEpisodeClick = { episode, podcast ->
                                        fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                                        navController.navigate(
                                            "episode/${encode(episode.id)}/${encode(episode.title)}/" +
                                            "${encode(episode.description?.take(500))}/" +
                                            "${encode(episode.imageUrl)}/" +
                                            "${encode(episode.audioUrl)}/" +
                                            "${episode.duration}/${encode(podcast.id)}/" +
                                            "${encode(podcast.title)}" +
                                            "?entryPoint=explore_for_you"
                                        )
                                    }
                                )
                            }
                            composable("library") { 
                                val podcastDao = remember { database.podcastDao() }
                                val subscriptionRepository = remember { cx.aswin.boxcast.core.data.SubscriptionRepository(podcastDao) }
                                val downloadRepository = remember { cx.aswin.boxcast.core.data.DownloadRepository(application, database) }
                                
                                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxcast.feature.library.LibraryViewModel>(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return cx.aswin.boxcast.feature.library.LibraryViewModel(
                                                subscriptionRepository, 
                                                playbackRepository,
                                                downloadRepository,
                                                userPrefs
                                            ) as T
                                        }
                                    }
                                )
                                
                                cx.aswin.boxcast.feature.library.LibraryScreen(
                                    viewModel = viewModel,
                                    onNavigateToLiked = {
                                        navController.navigate("library/liked")
                                    },
                                    onNavigateToSubscriptions = {
                                        navController.navigate("library/subscriptions")
                                    },
                                    onNavigateToDownloads = {
                                        navController.navigate("library/downloads")
                                    },
                                    onNavigateToHistory = {
                                        navController.navigate("library/history")
                                    }
                                )
                            }
                            composable("library/history") {
                                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxcast.feature.library.HistoryViewModel>(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return cx.aswin.boxcast.feature.library.HistoryViewModel(playbackRepository) as T
                                        }
                                    }
                                )
                                cx.aswin.boxcast.feature.library.HistoryScreen(
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
                                            "?entryPoint=library_history"
                                        )
                                    }
                                )
                            }
                            
                            composable("library/liked") {
                                val podcastDao = remember { database.podcastDao() }
                                val subscriptionRepository = remember { cx.aswin.boxcast.core.data.SubscriptionRepository(podcastDao) }
                                val downloadRepository = remember { cx.aswin.boxcast.core.data.DownloadRepository(application, database) }
                                
                                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxcast.feature.library.LibraryViewModel>(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return cx.aswin.boxcast.feature.library.LibraryViewModel(
                                                subscriptionRepository, 
                                                playbackRepository,
                                                downloadRepository,
                                                userPrefs
                                            ) as T
                                        }
                                    }
                                )
                                
                                cx.aswin.boxcast.feature.library.LikedEpisodesScreen(
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
                                            "?entryPoint=library_liked_episodes"
                                        )
                                    },
                                    onExploreClick = {
                                        navController.navigate("explore?entryPoint=library_history_empty_state") {
                                            popUpTo("home")
                                        }
                                    }
                                )
                            }

                            composable(
                                "library/subscriptions?tab={tab}",
                                arguments = listOf(
                                    androidx.navigation.navArgument("tab") {
                                        type = androidx.navigation.NavType.IntType
                                        defaultValue = 0
                                    }
                                )
                            ) { backStackEntry ->
                                val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
                                val podcastDao = remember { database.podcastDao() }
                                val subscriptionRepository = remember { cx.aswin.boxcast.core.data.SubscriptionRepository(podcastDao) }
                                val downloadRepository = remember { cx.aswin.boxcast.core.data.DownloadRepository(application, database) }
                                
                                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxcast.feature.library.LibraryViewModel>(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return cx.aswin.boxcast.feature.library.LibraryViewModel(
                                                subscriptionRepository,
                                                playbackRepository,
                                                downloadRepository,
                                                userPrefs
                                            ) as T
                                        }
                                    }
                                )
                                
                                cx.aswin.boxcast.feature.library.SubscriptionsScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onPodcastClick = { podcastId ->
                                        navController.navigate("podcast/$podcastId?entryPoint=library_subscriptions")
                                    },
                                    onExploreClick = {
                                        navController.navigate("explore?entryPoint=library_subscriptions_empty_state") {
                                            popUpTo("home")
                                        }
                                    },
                                    onPlayEpisode = { episode, podcast ->
                                        queueManager.playEpisode(episode, podcast)
                                    },
                                    onPlayEpisodes = { episodes, fallbackPodcast ->
                                        queueManager.playEpisodes(episodes, fallbackPodcast)
                                    },
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
                                            entryPointQuery
                                        )
                                    },
                                    isPlayerActive = currentEpisode != null,
                                    initialTab = initialTab
                                )
                            }

                            composable("library/downloads") {
                                val podcastDao = remember { database.podcastDao() }
                                val subscriptionRepository = remember { cx.aswin.boxcast.core.data.SubscriptionRepository(podcastDao) }
                                val downloadRepository = remember { cx.aswin.boxcast.core.data.DownloadRepository(application, database) }
                                
                                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxcast.feature.library.LibraryViewModel>(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return cx.aswin.boxcast.feature.library.LibraryViewModel(
                                                subscriptionRepository,
                                                playbackRepository,
                                                downloadRepository,
                                                userPrefs
                                            ) as T
                                        }
                                    }
                                )
                                
                                cx.aswin.boxcast.feature.library.DownloadedEpisodesScreen(
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
                                            "?entryPoint=library_downloaded_episodes"
                                        )
                                    },
                                    onExploreClick = {
                                        navController.navigate("explore?entryPoint=library_downloads_empty_state") {
                                            popUpTo("home")
                                        }
                                    }
                                )
                            }

                            
                            // REMOVED PlayerRoute logic from NavGraph

                            // Podcast Info Screen
                            composable(
                                route = "podcast/{podcastId}?entryPoint={entryPoint}&genre={genre}&depth={depth}&query={query}", 
                                arguments = listOf(
                                    navArgument("podcastId") { type = NavType.StringType },
                                    navArgument("entryPoint") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("genre") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("depth") { type = NavType.StringType; nullable = true; defaultValue = null },
                                    navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null }
                                )
                            ) { backStackEntry ->
                                val podcastId = backStackEntry.arguments?.getString("podcastId") ?: return@composable
                                val entryPoint = backStackEntry.arguments?.getString("entryPoint")
                                val genre = backStackEntry.arguments?.getString("genre")
                                val depthStr = backStackEntry.arguments?.getString("depth")
                                val depth = depthStr?.toIntOrNull()
                                val query = backStackEntry.arguments?.getString("query")

                                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxcast.feature.info.PodcastInfoViewModel>(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return cx.aswin.boxcast.feature.info.PodcastInfoViewModel(
                                                application, 
                                                apiBaseUrl, 
                                                publicKey, 
                                                playbackRepository, // Pass Shared Instance
                                                downloadRepository,
                                                queueManager,
                                                entryPoint,
                                                genre,
                                                depth,
                                                query
                                            ) as T
                                        }
                                    }
                                )
                                     // Calculate bottom padding for Mini Player
                                     // PlayerState is a data class. If currentEpisode is not null, player is active.
                                     val isPlayerVisible by remember(playbackRepository) {
                                         playbackRepository.playerState.map { it.currentEpisode != null }.distinctUntilChanged()
                                      }.collectAsState(initial = false)
                                      
                                      // Base: NavBar clearance (62dp) + optional MiniPlayer (64dp) + MiniPlayer bottom margin (8dp)
                                      val miniPlayerPadding = if (isPlayerVisible) (62 + 64 + 8).dp else 62.dp
                                     
                                     cx.aswin.boxcast.feature.info.PodcastInfoScreen(
                                        podcastId = podcastId,
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() },
                                        bottomContentPadding = miniPlayerPadding,
                                        onPodcastClick = { pId ->
                                            navController.navigate("podcast/$pId?entryPoint=podroll")
                                        },
                                        onEpisodeClick = { episode, entryPointStr, index ->
                                            fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                                            var route = "episode/${episode.id}/${encode(episode.title)}/" +
                                                "${encode(episode.description.take(500))}/" +
                                                "${encode(episode.imageUrl)}/" +
                                                "${encode(episode.audioUrl)}/" +
                                                "${episode.duration}/${encode(viewModel.uiState.value.let { if (it is cx.aswin.boxcast.feature.info.PodcastInfoUiState.Success) it.podcast.id else podcastId })}/" +
                                                "${encode(viewModel.uiState.value.let { if (it is cx.aswin.boxcast.feature.info.PodcastInfoUiState.Success) it.podcast.title else "Podcast" })}" +
                                                "?entryPoint=$entryPointStr"
                                                
                                            if (index != null) {
                                                route += "&carouselPosition=$index"
                                            }
                                            navController.navigate(route)
                                        },
                                        onPlayEpisode = { episode ->
                                            // Start Playback -> Mini Player
                                            val state = viewModel.uiState.value
                                            if (state is cx.aswin.boxcast.feature.info.PodcastInfoUiState.Success) {
                                                // QueueManager handles scope internally or launches immediately
                                                queueManager.playEpisode(episode, state.podcast)
                                            }
                                        }
                                    )
                            }

                            // Episode Info Screen
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
                                    navArgument("carouselPosition") { type = NavType.IntType; defaultValue = -1 }
                                )
                            ) { backStackEntry ->
                                val args = backStackEntry.arguments ?: return@composable
                                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<cx.aswin.boxcast.feature.info.EpisodeInfoViewModel>(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return cx.aswin.boxcast.feature.info.EpisodeInfoViewModel(
                                                application, 
                                                apiBaseUrl, 
                                                publicKey, 
                                                playbackRepository, // Pass Shared Instance
                                                downloadRepository,
                                                queueManager
                                            ) as T
                                        }
                                    }
                                )
                                fun decode(s: String?) = try { android.net.Uri.decode(s ?: "").let { if (it == "_") "" else it } } catch (_: Exception) { s ?: "" }
                                
                                val podcastId = decode(args.getString("podcastId"))
                                val podcastTitle = decode(args.getString("podcastTitle"))
                                val episodeId = args.getString("episodeId") ?: ""
                                val episodeTitle = decode(args.getString("episodeTitle"))
                                val entryPoint = args.getString("entryPoint")
                                val vibeId = decode(args.getString("vibeId"))
                                val carouselPosition = args.getInt("carouselPosition", -1)
                                
                                
                                cx.aswin.boxcast.feature.info.EpisodeInfoScreen(
                                    episodeId = episodeId,
                                    episodeTitle = episodeTitle,
                                    episodeDescription = decode(args.getString("episodeDescription")),
                                    episodeImageUrl = decode(args.getString("episodeImageUrl")),
                                    episodeAudioUrl = decode(args.getString("episodeAudioUrl")),
                                    episodeDuration = args.getInt("episodeDuration"),
                                    podcastId = podcastId,
                                    podcastTitle = podcastTitle,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onPodcastClick = { pId -> navController.navigate("podcast/$pId?entryPoint=episode_info") },
                                    onEpisodeClick = { ep ->
                                        // Navigate to the clicked episode
                                        fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                                        navController.navigate(
                                            "episode/${ep.id}/${encode(ep.title)}/${encode(ep.description)}/${encode(ep.imageUrl)}/${encode(ep.audioUrl)}/${ep.duration}/${encode(podcastId)}/${encode(podcastTitle)}" +
                                            "?entryPoint=episode_related_episodes"
                                        )
                                    },
                                    onPlay = {
                                        // Construct objects for playback
                                        val episode = cx.aswin.boxcast.core.model.Episode(
                                            id = episodeId,
                                            title = episodeTitle,
                                            description = "",
                                            imageUrl = decode(args.getString("episodeImageUrl")),
                                            audioUrl = decode(args.getString("episodeAudioUrl")),
                                            duration = args.getInt("episodeDuration"),
                                            publishedDate = 0L
                                        )
                                        val podcast = cx.aswin.boxcast.core.model.Podcast(
                                            id = podcastId,
                                            title = podcastTitle,
                                            artist = "",
                                            imageUrl = "", // We might not have this here, but repository cache will fill it if available, or we use episode art
                                            description = "",
                                            genre = ""
                                        )
                                        val bundle = if (entryPoint != null) {
                                            android.os.Bundle().apply {
                                                putString("entry_point", entryPoint)
                                                if (vibeId.isNotEmpty()) putString("curated_vibe_id", vibeId)
                                                if (carouselPosition >= 0) putInt("curated_carousel_position", carouselPosition)
                                            }
                                        } else {
                                            null
                                        }
                                        queueManager.playEpisode(episode, podcast, entryPointContext = bundle)
                                    },
                                    entryPointContext = if (entryPoint != null) {
                                        android.os.Bundle().apply {
                                            putString("entry_point", entryPoint)
                                            if (vibeId.isNotEmpty()) putString("curated_vibe_id", vibeId)
                                            if (carouselPosition >= 0) putInt("curated_carousel_position", carouselPosition)
                                        }
                                    } else null,
                                    showMarkPlayedTip = !hasSeenMarkPlayedTip,
                                    onMarkPlayedTipDismissed = { scope.launch { userPrefs.markMarkPlayedTipSeen() } }
                                )
                            }
                        }
                    }
                    }

                    // Calculate sheet positions
                    val configuration = LocalConfiguration.current
                    val density = LocalDensity.current
                    val screenHeightDp = maxHeight // Use BoxWithConstraints maxHeight
                    
                    // Get system nav bar height for full-screen expanded player
                    val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    
                    // Only app navbar height - matches BoxCastNavigationBar content height
                    val appNavBarHeight = 62.dp 
                    
                    // Container height = full screen + system nav bar + extra buffer to ensure full coverage
                    val containerHeight = screenHeightDp + systemNavBarHeight + 50.dp
                    
                    // Collapsed position: mini player sits above app navbar with a small margin
                    val miniPlayerBottomMargin = 8.dp
                    val collapsedTargetY = with(density) {
                        (screenHeightDp - cx.aswin.boxcast.feature.player.MiniPlayerHeight - appNavBarHeight - systemNavBarHeight - miniPlayerBottomMargin).toPx()
                    }
                    

                    // Navigation Bar
                    if (showBottomNav) {
                        val activeTab = when {
                            currentRoute == "home" -> "home"
                            currentRoute.startsWith("explore") -> "explore"
                            currentRoute.startsWith("library") -> "library"
                            currentRoute.startsWith("podcast") || currentRoute.startsWith("episode") -> {
                                val backStack = navController.currentBackStack.value
                                var foundTab = "home"
                                for (i in backStack.size - 2 downTo 0) {
                                    val entry = backStack.getOrNull(i)
                                    val route = entry?.destination?.route ?: continue
                                    if (route.startsWith("explore")) {
                                        foundTab = "explore"
                                        break
                                    } else if (route.startsWith("library")) {
                                        foundTab = "library"
                                        break
                                    } else if (route == "home") {
                                        foundTab = "home"
                                        break
                                    }
                                }
                                foundTab
                            }
                            else -> "home"
                        }

                        BoxCastNavigationBar(
                            currentRoute = activeTab,
                            onNavigate = { route ->
                                // Navigation logic for bottom tabs
                                // podcast/ and episode/ routes are "detail" screens that can be reached from multiple tabs
                                
                                when {
                                    // Same route - pop back to the root of the tab
                                    activeTab == route -> {
                                        if (route == "home") {
                                            navController.popBackStack("home", inclusive = false)
                                        } else if (route == "explore") {
                                            val popped = navController.popBackStack("explore?category={category}&entryPoint={entryPoint}", inclusive = false)
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
                                    
                                    // Navigating to Home
                                    route == "home" -> {
                                        navController.navigate("home") {
                                            popUpTo("home") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    
                                    // Default: Navigate to the new tab
                                    else -> {
                                        navController.navigate(
                                            if (route == "explore") "explore?entryPoint=bottom_nav" else route
                                        ) {
                                            popUpTo("home") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    // Late Night Sleep Timer Nudge
                    val isPlayerActive = currentEpisode != null
                    
                    var isTimerSetConfirmation by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(showLateNightNudge) {
                        android.util.Log.d("LateNightNudge", "LaunchedEffect(showLateNightNudge) triggered: $showLateNightNudge")
                        if (!showLateNightNudge) {
                            isTimerSetConfirmation = false
                        }
                    }
                    
                    // Confirmation auto-dismiss: when timer is set, delay 2.5 seconds and dismiss nudge cleanly
                    LaunchedEffect(isTimerSetConfirmation) {
                        android.util.Log.d("LateNightNudge", "LaunchedEffect(isTimerSetConfirmation) triggered: $isTimerSetConfirmation")
                        if (isTimerSetConfirmation) {
                            android.util.Log.d("LateNightNudge", "Starting 2500ms delay for confirmation auto-dismiss")
                            kotlinx.coroutines.delay(2500)
                            android.util.Log.d("LateNightNudge", "Delay finished. Calling dismissLateNightNudge()")
                            playbackRepository.dismissLateNightNudge()
                            isTimerSetConfirmation = false
                        }
                    }
                    
                    // Reactive Auto-hide: dismiss nudge after 8 seconds of inactivity (extended from 5s)
                    LaunchedEffect(showLateNightNudge, currentEpisode?.id, isTimerSetConfirmation) {
                        android.util.Log.d("LateNightNudge", "LaunchedEffect(auto-hide) triggered: showLateNightNudge=$showLateNightNudge, episodeId=${currentEpisode?.id}, isTimerSetConfirmation=$isTimerSetConfirmation")
                        if (showLateNightNudge && !isTimerSetConfirmation) {
                            android.util.Log.d("LateNightNudge", "Starting 8000ms inactivity delay")
                            kotlinx.coroutines.delay(8000)
                            android.util.Log.d("LateNightNudge", "Inactivity delay finished. Calling dismissLateNightNudge()")
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLateNightSafeguardDecision("ignore")
                            playbackRepository.dismissLateNightNudge()
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showLateNightNudge && isPlayerActive && !isRadioMode && !isModeSwitching,
                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) + 
                                androidx.compose.animation.slideInVertically(
                                    initialOffsetY = { it }, 
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    )
                                ),
                        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) + 
                               androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = appNavBarHeight + systemNavBarHeight + cx.aswin.boxcast.feature.player.MiniPlayerHeight + miniPlayerBottomMargin + 16.dp,
                                start = 16.dp,
                                end = 16.dp
                            )
                            .zIndex(10f)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                if (isTimerSetConfirmation) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Timer set. Good night!",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                letterSpacing = 0.15.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    // Row 1: Text on Left, Dismiss Cross on Right
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Late Night Listening?",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Set a timer to prevent episodes from playing all night.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                                .clickable {
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLateNightSafeguardDecision("dismiss")
                                                    playbackRepository.dismissLateNightNudge() // Manually dismiss
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✕",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    // Row 2: "Start timer:" label + [30m] [1hr] [2hr] option pills
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Start timer:",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        
                                        // 30m Button
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                .border(
                                                    1.dp, 
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), 
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLateNightSafeguardDecision("timer_set", 30)
                                                    playbackRepository.setSleepTimer(30, dismissNudge = false)
                                                    isTimerSetConfirmation = true
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "30m",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        }
                                        
                                        // 1hr Button
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                .border(
                                                    1.dp, 
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), 
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLateNightSafeguardDecision("timer_set", 60)
                                                    playbackRepository.setSleepTimer(60, dismissNudge = false)
                                                    isTimerSetConfirmation = true
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "1hr",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        }
                                        
                                        // 2hr Button
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                .border(
                                                    1.dp, 
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), 
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLateNightSafeguardDecision("timer_set", 120)
                                                    playbackRepository.setSleepTimer(120, dismissNudge = false)
                                                    isTimerSetConfirmation = true
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "2hr",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Unified Player Sheet - PixelPlayer architecture (Last so it draws ON TOP)
                    // Hidden in Radio Mode and during mode switch animation
                    if (!isRadioMode && !isModeSwitching) {
                    cx.aswin.boxcast.feature.player.UnifiedPlayerSheet(
                        playbackRepository = playbackRepository,
                        downloadRepository = downloadRepository,
                        userPrefs = userPrefs,
                        sheetCollapsedTargetY = collapsedTargetY,
                        containerHeight = containerHeight,
                        collapsedStateHorizontalPadding = 12.dp,
                        expandTrigger = expandPlayerTrigger, // Pass the trigger here
                        isDarkTheme = darkTheme,
                        onEpisodeInfoClick = { episode ->
                            // Navigate to episode info
                            val podcast = playbackRepository.playerState.value.currentPodcast
                            fun encode(s: String?) = android.net.Uri.encode(s?.ifEmpty { "_" } ?: "_")
                            navController.navigate(
                                "episode/${encode(episode.id)}/${encode(episode.title)}/" +
                                "${encode(episode.description.take(500))}/" +
                                "${encode(episode.imageUrl)}/" +
                                "${encode(episode.audioUrl)}/" +
                                "${episode.duration}/${encode(podcast?.id ?: "unknown")}/" +
                                "${encode(podcast?.title ?: "Podcast")}" +
                                "?entryPoint=player_ui"
                            ) {
                                launchSingleTop = true
                            }
                        },
                        onPodcastInfoClick = { podcast ->
                            // Navigate to podcast info
                            navController.navigate("podcast/${podcast.id}?entryPoint=player_ui") {
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    } // end !isRadioMode
                }

                OpmlImportDialog(
                    state = opmlImportState,
                    onDismissRequest = {
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
                                scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        applicationContext,
                                        "Successfully imported ${currentState.importedPodcasts.size} podcasts.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    
                                    if (currentRoute == "onboarding") {
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackOnboardingImportCompleted(
                                            "opml",
                                            currentState.importedPodcasts.size,
                                            onboardingViewModel.getGenreScreenTimeSpent(),
                                            onboardingViewModel.getTotalOnboardingTime()
                                        )
                                        onboardingViewModel.markOnboardingCompletedSilent {
                                            navController.navigate("home") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                    }
                                    opmlImportState = OpmlImportState.Idle
                                }
                            } else {
                                opmlImportState = OpmlImportState.Completing(
                                    progress = 0f,
                                    currentShowTitle = podcastsToMark.first().title,
                                    podcastsToMark = podcastsToMark,
                                    totalImportedCount = currentState.importedPodcasts.size
                                )
                                importTriggerKey = System.currentTimeMillis()
                            }
                        }
                    },
                    onSkipCompleted = {
                        val currentState = opmlImportState
                        if (currentState is OpmlImportState.AskCompleted) {
                            if (currentRoute == "onboarding") {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackOnboardingImportCompleted(
                                    "opml",
                                    currentState.importedPodcasts.size,
                                    onboardingViewModel.getGenreScreenTimeSpent(),
                                    onboardingViewModel.getTotalOnboardingTime()
                                )
                                onboardingViewModel.markOnboardingCompletedSilent {
                                    navController.navigate("home") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    applicationContext,
                                    "Imported ${currentState.importedPodcasts.size} podcasts.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            opmlImportState = OpmlImportState.Idle
                        }
                    }
                )
            }
        }
    }

    private fun checkForUpdates() {
        try {
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                val isUpdateAvailable = appUpdateInfo.updateAvailability() == com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE
                
                // Determine if we should use IMMEDIATE (mandatory) or FLEXIBLE (optional)
                // Priority >= 4 is usually considered a critical/mandatory update.
                // Fallback: If the update has been available for 7+ days, force IMMEDIATE.
                val updatePriority = appUpdateInfo.updatePriority()
                val daysStale = appUpdateInfo.clientVersionStalenessDays() ?: 0
                val isHighPriority = updatePriority >= 4 || daysStale >= 7
                
                val updateType = if (isHighPriority && appUpdateInfo.isUpdateTypeAllowed(com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE)) {
                    com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE
                } else if (appUpdateInfo.isUpdateTypeAllowed(com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE)) {
                    com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE
                } else {
                    null
                }

                if (isUpdateAvailable && updateType != null) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            updateLauncher,
                            com.google.android.play.core.appupdate.AppUpdateOptions.newBuilder(updateType).build()
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
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    Text(
        text = "Box.Cast",
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BoxCastTheme {
        Greeting()
    }
}

sealed interface OpmlImportState {
    object Idle : OpmlImportState
    data class Parsing(val uri: android.net.Uri) : OpmlImportState
    data class Importing(
        val currentFeedTitle: String,
        val progress: Float,
        val currentCount: Int,
        val totalCount: Int,
        val importedPodcasts: List<cx.aswin.boxcast.core.model.Podcast>
    ) : OpmlImportState
    data class AskCompleted(
        val importedPodcasts: List<cx.aswin.boxcast.core.model.Podcast>,
        val selectedIds: Set<String>
    ) : OpmlImportState
    data class Completing(
        val progress: Float,
        val currentShowTitle: String,
        val podcastsToMark: List<cx.aswin.boxcast.core.model.Podcast>,
        val totalImportedCount: Int
    ) : OpmlImportState
    data class Success(val importedCount: Int, val completedCount: Int) : OpmlImportState
    data class Error(val message: String) : OpmlImportState
}

@Composable
fun OpmlImportDialog(
    state: OpmlImportState,
    onDismissRequest: () -> Unit,
    onSelectionChanged: (selectedIds: Set<String>) -> Unit,
    onConfirmCompleted: () -> Unit,
    onSkipCompleted: () -> Unit
) {
    if (state is OpmlImportState.Idle || state is OpmlImportState.Success) return

    Dialog(
        onDismissRequest = {
            if (state is OpmlImportState.AskCompleted || state is OpmlImportState.Error) {
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val isSelectionState = state is OpmlImportState.AskCompleted

        Card(
            modifier = if (isSelectionState) {
                Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            } else {
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            if (isSelectionState) {
                val selectionState = state as OpmlImportState.AskCompleted
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header Area
                    Text(
                        text = "Start fresh with your shows?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Toggle shows to mark all past episodes as completed. This keeps your feed organized and ready for new releases.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Quick Action Selection Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.SuggestionChip(
                            onClick = {
                                onSelectionChanged(selectionState.importedPodcasts.map { it.id }.toSet())
                            },
                            label = { Text("Select All") },
                            icon = { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(50.dp)
                        )
                        androidx.compose.material3.SuggestionChip(
                            onClick = {
                                onSelectionChanged(emptySet())
                            },
                            label = { Text("Deselect All") },
                            icon = { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(50.dp)
                        )
                    }

                    // Scrollable List of Podcasts
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                        ) {
                            items(selectionState.importedPodcasts, key = { it.id }) { podcast ->
                                val isChecked = podcast.id in selectionState.selectedIds
                                ListItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newSelected = if (isChecked) {
                                                selectionState.selectedIds - podcast.id
                                            } else {
                                                selectionState.selectedIds + podcast.id
                                            }
                                            onSelectionChanged(newSelected)
                                        },
                                    headlineContent = {
                                        Text(
                                            text = podcast.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = podcast.artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            cx.aswin.boxcast.core.designsystem.components.OptimizedImage(
                                                url = podcast.imageUrl,
                                                proxyWidth = 150,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                val newSelected = if (checked == true) {
                                                    selectionState.selectedIds + podcast.id
                                                } else {
                                                    selectionState.selectedIds - podcast.id
                                                }
                                                onSelectionChanged(newSelected)
                                            }
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                                )
                                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Stacked Action Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = onConfirmCompleted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Mark Selected (${selectionState.selectedIds.size}) as Played",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = onSkipCompleted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Keep All Unplayed",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is OpmlImportState.Parsing -> {
                            BoxCastLoader.CircularWavy(
                                size = 56.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Reading OPML File...",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Extracting podcast feeds from document",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        is OpmlImportState.Importing -> {
                            BoxCastLoader.CircularWavy(
                                progress = state.progress,
                                size = 64.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Importing Podcasts",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Resolving & subscribing: ${state.currentCount + 1} of ${state.totalCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = state.currentFeedTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                            }
                        }
                        is OpmlImportState.Completing -> {
                            BoxCastLoader.CircularWavy(
                                progress = state.progress,
                                size = 64.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Marking History Played",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Fetching and completing all episodes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = state.currentShowTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                            }
                        }
                        is OpmlImportState.Error -> {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Import Failed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDismissRequest,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

