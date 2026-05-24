package cx.aswin.boxcast

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class BoxCastApplication : Application() {

    override fun onCreate() {
        super.onCreate()

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
}
