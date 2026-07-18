package cx.aswin.boxlore

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.Configuration
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import cx.aswin.boxlore.core.data.EngagementPromptCoordinator
import cx.aswin.boxlore.core.data.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.data.UserPreferencesRepository
import cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
import cx.aswin.boxlore.core.data.ranking.LearningEventLog
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.surveys.BoxcastPostHogSurveysDelegate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BoxLoreApplication : Application(), Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    /** Shared orchestrator for NPS and Play review proactive prompts. */
    lateinit var engagementPromptCoordinator: EngagementPromptCoordinator
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(LegacyWorkerFactory())
            .build()

    override fun onCreate() {
        super.onCreate()

        // Single prefs instance shared with AppContainer (theme fast-cache + engagement).
        userPreferencesRepository = UserPreferencesRepository(this)
        container = AppContainer(
            context = this,
            apiBaseUrl = BuildConfig.BOXCAST_API_BASE_URL,
            publicKey = BuildConfig.BOXCAST_PUBLIC_KEY,
            sharedUserPreferences = userPreferencesRepository,
        )
        SharedAppDependenciesHolder.instance = container
        DownloadsDependenciesHolder.instance = container
        engagementPromptCoordinator = EngagementPromptCoordinator(userPreferencesRepository)
        // Eagerly touch the container ranking façade so create/install runs its no-op
        // fallback if Room initialization fails — same startup behavior as before, without
        // a second RankingFeedbackRepository client diverging from the container.
        container.rankingFeedbackRepository

        // Live learner signal log: on by default in debug builds, off for release users
        // unless they explicitly opt in via the debug screen toggle. A persisted choice wins.
        LearningEventLog.configure(
            cx.aswin.boxlore.core.data.BoxcastPrefs(this)
                .isLearnerLogEnabled(default = BuildConfig.DEBUG),
        )

        val config = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host = BuildConfig.POSTHOG_HOST
        ).apply {
            captureApplicationLifecycleEvents = true
            captureScreenViews = false
            captureDeepLinks = false
            debug = BuildConfig.DEBUG
            // PostHog Surveys with a Material3 1.5-compatible delegate (the published
            // posthog-android-surveys-compose:0.1.0 module crashes against our M3 pin).
            surveys = true
            surveysConfig.surveysDelegate =
                BoxcastPostHogSurveysDelegate(
                    context = this@BoxLoreApplication,
                    userPrefs = userPreferencesRepository,
                    engagementCoordinator = engagementPromptCoordinator,
                )
        }
        PostHogAndroid.setup(this, config)

        // Tag internal/test users so they can be filtered in PostHog settings
        if (BuildConfig.DEBUG) {
            PostHog.register("is_internal", true)
            PostHog.register("app_environment", "debug")
        } else {
            PostHog.register("is_internal", false)
            PostHog.register("app_environment", "production")
        }
        reportAdaptiveRankingStatus()

        setupAppCheck()

        // Setup active connectivity listener for offline tracking
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val initialNetwork = connectivityManager.activeNetwork
            val initialCapabilities = connectivityManager.getNetworkCapabilities(initialNetwork)
            var hasInternet = initialCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    hasInternet = true
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    val activeNetwork = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    if (!isConnected) {
                        if (hasInternet) {
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackOfflineModeEntered()
                        }
                        hasInternet = false
                    }
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("BoxCastApp", "Failed to register connectivity observer", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reportAdaptiveRankingStatus() {
        applicationScope.launch {
            try {
                val statuses = container.adaptiveRankingRepository
                    .aggregateTelemetry()
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                    .trackAdaptiveRankingStatus(statuses)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                // Aggregate telemetry is optional and must never destabilize app startup.
                android.util.Log.w(
                    "BoxLoreApplication",
                    "Failed to report adaptive ranking status",
                    error,
                )
            }
        }
    }

    /**
     * Firebase App Check attests that requests come from the genuine app.
     * Debug builds use the debug provider (token must be registered in the
     * Firebase console); release builds attest via Play Integrity. Tokens are
     * attached to API calls as X-Firebase-AppCheck. Everything fails open:
     * requests still go out without the header if attestation is unavailable,
     * since the Worker is in log-only mode.
     */
    private fun setupAppCheck() {
        try {
            // Expose the build to the network layer so requests carry
            // X-App-Version and the proxy can slice App Check adoption by build.
            NetworkModule.appVersion = BuildConfig.VERSION_NAME
            val provider = if (BuildConfig.DEBUG) "debug" else "play_integrity"
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            } else {
                appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            }
            // Keep a valid token in the SDK's persistent cache at all times and
            // refresh it in the background before expiry, so the interceptor gets
            // an instant cache read instead of a live (and sometimes failing)
            // fetch. With the 24h token TTL this is ~1 mint/user/day.
            appCheck.setTokenAutoRefreshEnabled(true)
            // Pre-warm: start the token exchange at launch so it's cached before
            // the first API request, closing the cold-start gap. The result is
            // reported once per launch to PostHog for adoption/health tracking.
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener {
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAppCheckStatus(true, provider)
                }
                .addOnFailureListener { e ->
                    android.util.Log.w("BoxCastApp", "App Check pre-warm failed: ${e.message}")
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAppCheckStatus(false, provider)
                }
            NetworkModule.appCheckTokenProvider = {
                try {
                    // Called from OkHttp's background threads; returns the cached
                    // token instantly unless it needs a refresh
                    val task = FirebaseAppCheck.getInstance().getAppCheckToken(false)
                    Tasks.await(task, 5, TimeUnit.SECONDS).token
                } catch (e: Exception) {
                    android.util.Log.w("BoxCastApp", "App Check token unavailable: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BoxCastApp", "App Check setup failed", e)
        }
    }
}
