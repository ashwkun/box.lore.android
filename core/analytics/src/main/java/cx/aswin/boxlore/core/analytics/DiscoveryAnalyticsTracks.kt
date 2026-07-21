package cx.aswin.boxlore.core.analytics

@Suppress("LongParameterList")
internal object DiscoveryAnalyticsTracks {
    fun trackFeatureAnnouncementViewed(featureId: String) {
        AnalyticsEmit.event(
            "feature_announcement_action",
            mapOf("action" to "viewed", "feature_id" to featureId),
        )
    }

    fun trackFeatureAnnouncementDismissed(featureId: String) {
        AnalyticsEmit.event(
            "feature_announcement_action",
            mapOf("action" to "dismissed", "feature_id" to featureId),
        )
    }

    fun trackInAppAnnouncementViewed(
        category: String,
        hasImage: Boolean,
        hasAction: Boolean,
    ) {
        AnalyticsEmit.event(
            "feature_announcement_action",
            mapOf(
                "action" to "viewed",
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
        AnalyticsEmit.event(
            "feature_announcement_action",
            mapOf(
                "action" to "dismissed",
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
        AnalyticsEmit.event(
            "feature_announcement_action",
            mapOf(
                "action" to "acted",
                "category" to category,
                "has_image" to hasImage,
                "feature_id" to actionLabel,
                "action_label" to actionLabel,
            ),
        )
    }

    fun trackNotificationPermissionRequested() {
        AnalyticsEmit.event("notification_permission_requested")
    }

    fun trackNotificationPermissionDecided(isGranted: Boolean) {
        AnalyticsEmit.event(
            "notification_permission_decided",
            mapOf("granted" to isGranted),
        )
        AnalyticsEmit.personSet(mapOf("notifications_enabled" to isGranted))
    }

    fun trackHomeHeroCarouselSwiped(
        maxCardIndexViewed: Int,
        totalCardsAvailable: Int,
    ) {
        AnalyticsEmit.event(
            "home_surface_tapped",
            mapOf(
                "surface_component" to "hero_carousel_swipe",
                "position_index" to maxCardIndexViewed,
                "items_count" to totalCardsAvailable,
            ),
        )
    }

    fun trackCuratedBlockImpression(
        blockTitle: String,
        vibeIds: List<String>,
    ) {
        AnalyticsEmit.event(
            "home_surface_impression",
            mapOf(
                "surface_component" to "curated_block",
                "items_count" to vibeIds.size,
                "block_title" to blockTitle,
                "vibe_ids" to vibeIds,
            ),
        )
    }

    fun trackHomeRecommendationsImpression(
        recommendationsCount: Int,
        episodeIds: List<String>,
        timeBlockTitle: String?,
    ) {
        val props =
            mutableMapOf<String, Any>(
                "surface_component" to "recommendations",
                "items_count" to recommendationsCount,
                "episode_ids" to episodeIds,
            )
        timeBlockTitle?.let { props["rail_intent"] = it }
        AnalyticsEmit.event("home_surface_impression", props)
    }

    fun trackHomeRecommendationCardTapped(
        episodeId: String,
        episodeTitle: String?,
        podcastId: String,
        podcastName: String?,
        positionIndex: Int,
        timeBlockTitle: String?,
    ) {
        val props =
            mutableMapOf<String, Any>(
                "surface_component" to "recommendations",
                "content_id" to episodeId,
                "episode_id" to episodeId,
                "podcast_id" to podcastId,
                "position_index" to positionIndex,
            )
        episodeTitle?.let { props["episode_title"] = it }
        podcastName?.let { props["podcast_name"] = it }
        timeBlockTitle?.let { props["rail_intent"] = it }
        AnalyticsEmit.event("home_surface_tapped", props)
    }

    /**
     * Explicit long-press / menu feedback on a Home recommendation card.
     * Uses [home_surface_tapped] with surface_component=recommendation_feedback.
     */
    fun trackHomeRecommendationFeedback(
        episodeId: String,
        podcastId: String,
        action: String,
        personalizationMode: String?,
        exposureId: String? = null,
    ) {
        val props =
            mutableMapOf<String, Any>(
                "surface_component" to "recommendation_feedback",
                "content_id" to episodeId,
                "episode_id" to episodeId,
                "podcast_id" to podcastId,
                "feedback_action" to action,
            )
        personalizationMode?.let { props["personalization_mode"] = it }
        exposureId?.let { props["exposure_id"] = it }
        AnalyticsEmit.event("home_surface_tapped", props)
    }

    fun trackHomePersonalizationMode(
        mode: String,
        meaningfulPlayCount: Int,
        isFallback: Boolean,
        candidateRequestOk: Boolean,
    ) {
        AnalyticsEmit.event(
            "home_surface_impression",
            mapOf(
                "surface_component" to "personalization_mode",
                "personalization_mode" to mode,
                "meaningful_play_count" to meaningfulPlayCount,
                "is_fallback" to isFallback,
                "candidate_request_ok" to candidateRequestOk,
                "items_count" to 0,
            ),
        )
    }

    fun trackExploreRecommendationsImpression(
        recommendationsCount: Int,
        episodeIds: List<String>,
    ) {
        AnalyticsEmit.event(
            "explore_recommendation_tapped",
            mapOf(
                "rail" to "for_you",
                "interaction" to "impression",
                "position_index" to -1,
                "recommendations_count" to recommendationsCount,
                "episode_ids" to episodeIds,
            ),
        )
    }

    fun trackExploreRecommendationCardTapped(
        episodeId: String,
        episodeTitle: String?,
        podcastId: String,
        podcastName: String?,
        positionIndex: Int,
    ) {
        val props =
            mutableMapOf<String, Any>(
                "podcast_id" to podcastId,
                "episode_id" to episodeId,
                "position_index" to positionIndex,
                "rail" to "for_you",
                "interaction" to "tap",
            )
        episodeTitle?.let { props["episode_title"] = it }
        podcastName?.let { props["podcast_name"] = it }
        AnalyticsEmit.event("explore_recommendation_tapped", props)
    }

    fun trackPodcastInfoScreenViewed(
        podcastId: String,
        podcastName: String? = null,
        entryPoint: String? = null,
        genreFilter: String? = null,
        scrollDepth: Int? = null,
        searchQuery: String? = null,
    ) {
        val props = mutableMapOf<String, Any>("podcast_id" to podcastId)
        podcastName?.let { props["podcast_name"] = it }
        entryPoint?.let { props["source"] = it }
        genreFilter?.let { props["genre_filter"] = it }
        scrollDepth?.let { props["scroll_depth"] = it }
        searchQuery?.let { props["search_query"] = it }
        AnalyticsEmit.event("podcast_detail_viewed", props)
    }

    fun trackPodcastSubscriptionToggled(
        podcastId: String,
        podcastName: String?,
        isSubscribed: Boolean,
        entryPoint: String,
    ) {
        val props =
            mutableMapOf<String, Any>(
                "podcast_id" to podcastId,
                "is_subscribed" to isSubscribed,
                "source" to entryPoint,
                "surface" to entryPoint,
            )
        podcastName?.let { props["podcast_name"] = it }
        AnalyticsEmit.event("podcast_subscription_toggled", props)
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
        episodesClickedCount: Int,
    ) {
        AnalyticsEmit.event(
            "podcast_detail_viewed",
            mapOf(
                "podcast_id" to podcastId,
                "podcast_name" to podcastName,
                "is_subscribed" to wasSubscribed,
                "time_spent_seconds" to timeSpentSeconds,
                "did_subscribe" to didSubscribe,
                "did_unsubscribe" to didUnsubscribe,
                "did_search" to didSearch,
                "did_sort_episodes" to didSortEpisodes,
                "episodes_played_count" to episodesPlayedCount,
                "episodes_clicked_count" to episodesClickedCount,
            ),
        )
    }

    fun trackCuratedCardTapped(
        podcastId: String,
        podcastName: String?,
        vibeId: String,
        positionIndex: Int,
    ) {
        val props =
            mutableMapOf<String, Any>(
                "surface_component" to "curated_card",
                "content_id" to podcastId,
                "podcast_id" to podcastId,
                "rail_intent" to vibeId,
                "position_index" to positionIndex,
            )
        if (podcastName != null) props["podcast_name"] = podcastName
        AnalyticsEmit.event("home_surface_tapped", props)
    }

    fun trackEpisodeInfoScreenViewed(properties: Map<String, Any>) {
        val props = properties.toMutableMap()
        // Prefer glossary keys when callers still send legacy names.
        if (!props.containsKey("episode_id") && props.containsKey("id")) {
            props["episode_id"] = props["id"] as Any
        }
        AnalyticsEmit.event("episode_detail_viewed", props)
    }

    fun trackEpisodeInfoScreenSession(properties: Map<String, Any>) {
        AnalyticsEmit.event("episode_detail_viewed", properties)
    }

    fun trackProxyFallbackTriggered(
        imageHost: String,
        proxyWidth: Int,
        sampleMultiplier: Int = 10,
    ) {
        PhaseCAnalyticsTracks.trackProxyFallbackTriggered(imageHost, proxyWidth, sampleMultiplier)
    }
}
