package cx.aswin.boxlore.core.data.analytics

import com.posthog.PostHog

// ── 8. Feature Announcements ───────────────────────────────────

internal object DiscoveryAnalyticsTracks {
    fun trackFeatureAnnouncementViewed(featureId: String) {
        PostHog.capture(
            event = "feature_announcement_viewed",
            properties = mapOf("feature_id" to featureId)
        )
    }

    fun trackFeatureAnnouncementDismissed(featureId: String) {
        PostHog.capture(
            event = "feature_announcement_dismissed",
            properties = mapOf("feature_id" to featureId)
        )
    }

    fun trackInAppAnnouncementViewed(
        category: String,
        hasImage: Boolean,
        hasAction: Boolean,
    ) {
        PostHog.capture(
            event = "in_app_announcement_viewed",
            properties =
                mapOf(
                    "category" to category,
                    "has_image" to hasImage,
                    "has_action" to hasAction,
                ),
        )
    }

    fun trackInAppAnnouncementDismissed(
        category: String,
        hasImage: Boolean,
        hasAction: Boolean,
    ) {
        PostHog.capture(
            event = "in_app_announcement_dismissed",
            properties =
                mapOf(
                    "category" to category,
                    "has_image" to hasImage,
                    "has_action" to hasAction,
                ),
        )
    }

    fun trackInAppAnnouncementAction(
        category: String,
        hasImage: Boolean,
        actionLabel: String,
    ) {
        PostHog.capture(
            event = "in_app_announcement_action",
            properties =
                mapOf(
                    "category" to category,
                    "has_image" to hasImage,
                    "action_label" to actionLabel,
                ),
        )
    }

    // ── 9. Permissions ──────────────────────────────────────────────

    fun trackNotificationPermissionRequested() {
        PostHog.capture("notification_permission_requested")
    }

    fun trackNotificationPermissionDecided(isGranted: Boolean) {
        PostHog.capture(
            event = "notification_permission_decided",
            properties = mapOf("granted" to isGranted)
        )
    
        PostHog.capture(
            event = "\$set",
            userProperties = mapOf("notifications_enabled" to isGranted)
        )
    }

    // ── 10. Playback ───────────────────────────────────────────────

    fun trackHomeHeroCarouselSwiped(maxCardIndexViewed: Int, totalCardsAvailable: Int) {
        PostHog.capture(
            event = "home_hero_carousel_swiped",
            properties = mapOf(
                "\$screen_name" to "App Home",
                "max_card_index_viewed" to maxCardIndexViewed,
                "total_cards_available" to totalCardsAvailable
            )
        )
    }

    fun trackCuratedBlockImpression(blockTitle: String, vibeIds: List<String>) {
        PostHog.capture(
            event = "curated_block_impression",
            properties = mapOf(
                "block_title" to blockTitle,
                "vibes_shown_count" to vibeIds.size,
                "vibe_ids" to vibeIds
            )
        )
    }

    fun trackHomeRecommendationsImpression(
        recommendationsCount: Int,
        episodeIds: List<String>,
        timeBlockTitle: String?
    ) {
        val props = mutableMapOf<String, Any>(
            "recommendations_count" to recommendationsCount,
            "episode_ids" to episodeIds
        )
        timeBlockTitle?.let { props["time_block_title"] = it }
        PostHog.capture(event = "home_recommendations_impression", properties = props)
    }

    fun trackHomeRecommendationCardTapped(
        episodeId: String,
        episodeTitle: String?,
        podcastId: String,
        podcastName: String?,
        positionIndex: Int,
        timeBlockTitle: String?
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "podcast_id" to podcastId,
            "position_index" to positionIndex
        )
        episodeTitle?.let { props["episode_title"] = it }
        podcastName?.let { props["podcast_name"] = it }
        timeBlockTitle?.let { props["time_block_title"] = it }
        PostHog.capture(event = "home_recommendation_card_tapped", properties = props)
    }

    fun trackExploreRecommendationsImpression(
        recommendationsCount: Int,
        episodeIds: List<String>
    ) {
        PostHog.capture(
            event = "explore_recommendations_impression",
            properties = mapOf(
                "recommendations_count" to recommendationsCount,
                "episode_ids" to episodeIds
            )
        )
    }

    fun trackExploreRecommendationCardTapped(
        episodeId: String,
        episodeTitle: String?,
        podcastId: String,
        podcastName: String?,
        positionIndex: Int
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "podcast_id" to podcastId,
            "position_index" to positionIndex
        )
        episodeTitle?.let { props["episode_title"] = it }
        podcastName?.let { props["podcast_name"] = it }
        PostHog.capture(event = "explore_recommendation_card_tapped", properties = props)
    }


    // ── 11. Podcast Info Screen ────────────────────────────────────

    fun trackPodcastInfoScreenViewed(
        podcastId: String,
        podcastName: String? = null,
        entryPoint: String? = null,
        genreFilter: String? = null,
        scrollDepth: Int? = null,
        searchQuery: String? = null
    ) {
        val props = mutableMapOf<String, Any>("podcast_id" to podcastId)
        podcastName?.let { props["podcast_name"] = it }
        entryPoint?.let { props["source_entry_point"] = it }
        genreFilter?.let { props["genre_filter"] = it }
        scrollDepth?.let { props["scroll_depth"] = it }
        searchQuery?.let { props["search_query"] = it }

        PostHog.capture(event = "podcast_info_screen_viewed", properties = props)
    }

    fun trackPodcastSubscriptionToggled(
        podcastId: String,
        podcastName: String?,
        isSubscribed: Boolean,
        entryPoint: String
    ) {
        val props = mutableMapOf<String, Any>(
            "podcast_id" to podcastId,
            "is_subscribed" to isSubscribed,
            "entry_point" to entryPoint
        )
        podcastName?.let { props["podcast_name"] = it }
        PostHog.capture(event = "podcast_subscription_toggled", properties = props)
    }

    fun trackPodcastInfoScreenSession(
        podcastId: String,
        podcastName: String,
        timeSpentSeconds: Float,
        wasSubscribed: Boolean,
        didSubscribe: Boolean,
        didUnsubscribe: Boolean,
        didSearch: Boolean,
        didSortEpisodes: Boolean,
        episodesPlayedCount: Int,
        episodesClickedCount: Int
    ) {
        PostHog.capture(
            event = "podcast_info_screen_session",
            properties = mapOf(
                "podcast_id" to podcastId,
                "podcast_name" to podcastName,
                "time_spent_seconds" to timeSpentSeconds,
                "was_subscribed" to wasSubscribed,
                "did_subscribe" to didSubscribe,
                "did_unsubscribe" to didUnsubscribe,
                "did_search" to didSearch,
                "did_sort_episodes" to didSortEpisodes,
                "episodes_played_count" to episodesPlayedCount,
                "episodes_clicked_count" to episodesClickedCount
            )
        )
    }

    fun trackCuratedCardTapped(
        podcastId: String,
        podcastName: String?,
        vibeId: String,
        positionIndex: Int
    ) {
        val props = mutableMapOf<String, Any>(
            "podcast_id" to podcastId,
            "vibe_id" to vibeId,
            "carousel_position" to positionIndex
        )
        if (podcastName != null) props["podcast_name"] = podcastName
        PostHog.capture(event = "curated_card_tapped", properties = props)
    }

    fun trackEpisodeInfoScreenViewed(properties: Map<String, Any>) {
        PostHog.capture(event = "episode_info_screen_viewed", properties = properties)
    }

    fun trackEpisodeInfoScreenSession(properties: Map<String, Any>) {
        PostHog.capture(event = "episode_info_screen_session", properties = properties)
    }

    fun trackProxyFallbackTriggered(
        imageHost: String,
        proxyWidth: Int,
        sampleMultiplier: Int = 10,
    ) {
        PostHog.capture(
            event = "proxy_fallback_triggered",
            properties =
                mapOf(
                    "image_host" to imageHost,
                    "proxy_width" to proxyWidth,
                    "sample_multiplier" to sampleMultiplier,
                ),
        )
    }
}
