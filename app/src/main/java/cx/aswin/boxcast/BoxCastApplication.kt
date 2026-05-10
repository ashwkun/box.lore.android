package cx.aswin.boxcast

import android.app.Application
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
    }
}
