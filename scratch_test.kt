import com.posthog.PostHog
fun test() {
    PostHog.reloadFeatureFlags {
        val f = PostHog.getFeatureFlag("test")
    }
}
