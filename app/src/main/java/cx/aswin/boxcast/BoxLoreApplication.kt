package cx.aswin.boxcast

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import cx.aswin.boxcast.core.network.NetworkModule
import java.util.concurrent.TimeUnit

class BoxLoreApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        setupAppCheck()

        val config = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host = BuildConfig.POSTHOG_HOST
        ).apply {
            captureApplicationLifecycleEvents = true
            captureScreenViews = false
            captureDeepLinks = false
            debug = BuildConfig.DEBUG
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
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackOfflineModeEntered()
                        }
                        hasInternet = false
                    }
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("BoxCastApp", "Failed to register connectivity observer", e)
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
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAppCheckStatus(true, provider)
                }
                .addOnFailureListener { e ->
                    android.util.Log.w("BoxCastApp", "App Check pre-warm failed: ${e.message}")
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAppCheckStatus(false, provider)
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
