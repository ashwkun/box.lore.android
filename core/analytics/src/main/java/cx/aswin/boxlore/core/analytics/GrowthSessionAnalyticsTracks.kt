package cx.aswin.boxlore.core.analytics

/**
 * Session / growth glossary events that are custom emits (not SDK-backed opens/installs).
 *
 * Volume for opens/background/installs stays on PostHog SDK lifecycle events
 * (`Application Opened` / `Application Backgrounded` / `Application Installed`).
 * Install channel is person `$set_once` only — never a second install-count event.
 */
internal object GrowthSessionAnalyticsTracks {
    /** Person props only — do not emit `install_attributed` (avoids double-counting installs). */
    fun trackInstallChannelAttributed(
        installChannel: String,
        referrerRaw: String? = null,
        utmSource: String? = null,
        shareToken: String? = null,
    ) {
        AnalyticsEmit.personSet(
            buildMap {
                put("\$set_once", buildMap {
                    put("install_channel", installChannel)
                    referrerRaw?.takeIf { it.isNotBlank() }?.let { put("referrer_raw", it) }
                    utmSource?.takeIf { it.isNotBlank() }?.let { put("utm_source", it) }
                    shareToken?.takeIf { it.isNotBlank() }?.let { put("share_token", it) }
                })
            },
        )
    }

    fun trackDeepLinkOpened(
        linkScheme: String,
        isFirstOpen: Boolean,
        linkHost: String? = null,
        contentType: String? = null,
        podcastId: String? = null,
        episodeId: String? = null,
        coldStart: Boolean? = null,
    ) {
        AnalyticsEmit.event(
            "deep_link_opened",
            buildMap {
                put("link_scheme", linkScheme)
                put("is_first_open", isFirstOpen)
                linkHost?.let { put("link_host", it) }
                contentType?.let { put("content_type", it) }
                podcastId?.let { put("podcast_id", it) }
                episodeId?.let { put("episode_id", it) }
                coldStart?.let { put("cold_start", it) }
            },
        )
    }

    fun trackOnboardingAbandoned(
        lastStep: String,
        flowType: String,
        timeSpentSeconds: Int? = null,
        subscribedCount: Int? = null,
    ) {
        AnalyticsEmit.event(
            "onboarding_abandoned",
            buildMap {
                put("last_step", lastStep)
                put("flow_type", flowType)
                timeSpentSeconds?.let { put("time_spent_seconds", it) }
                subscribedCount?.let { put("subscribed_count", it) }
            },
        )
    }

    fun trackSessionRestorePrompt(
        action: String,
        episodeId: String? = null,
        podcastId: String? = null,
        positionSeconds: Float? = null,
    ) {
        AnalyticsEmit.event(
            "session_restore_prompt",
            buildMap {
                put("action", action)
                episodeId?.let { put("episode_id", it) }
                podcastId?.let { put("podcast_id", it) }
                positionSeconds?.let { put("position_seconds", it) }
            },
        )
    }

    fun trackLaunchPersonEnrichment(
        onboardingStatus: String? = null,
        subscriptionCountBucket: String? = null,
    ) {
        if (onboardingStatus == null && subscriptionCountBucket == null) return
        AnalyticsEmit.personSet(
            buildMap {
                onboardingStatus?.let { put("onboarding_status", it) }
                subscriptionCountBucket?.let { put("subscription_count_bucket", it) }
            },
        )
    }
}
