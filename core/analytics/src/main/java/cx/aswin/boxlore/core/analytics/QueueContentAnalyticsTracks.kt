package cx.aswin.boxlore.core.analytics

internal object QueueContentAnalyticsTracks {
    fun trackSmartQueueRefilled(event: AnalyticsHelper.SmartQueueRefillEvent) {
        val props = mutableMapOf<String, Any>(
            "action" to "refill",
            "episode_id" to event.triggeringEpisodeId,
            "queue_size" to event.refilledCount,
            "source" to "smart_queue",
            "triggering_podcast_genre" to event.triggeringPodcastGenre,
            "recommendation_sources" to event.recommendationSources,
            "refilled_episode_ids" to event.refilledEpisodeIds,
            "used_server_recommendations" to event.usedServerRecommendations,
        )
        event.region?.let { props["region"] = it }
        event.sourceCounts.forEach { (source, count) -> props["source_count_$source"] = count }
        AnalyticsEmit.event("queue_modified", props)
    }

    fun trackQueueReordered(
        episodeId: String,
        fromPosition: Int,
        toPosition: Int,
        contextType: String?,
    ) {
        AnalyticsEmit.event(
            "queue_modified",
            mapOf(
                "action" to "reorder",
                "episode_id" to episodeId,
                "from_position" to fromPosition,
                "to_position" to toPosition,
                "source" to (contextType ?: "unknown"),
            ),
        )
    }

    fun trackLoreQueueConflictShown(episodeId: String, normalQueueSize: Int) {
        AnalyticsEmit.event(
            "queue_modified",
            mapOf(
                "action" to "conflict_shown",
                "episode_id" to episodeId,
                "queue_size" to normalQueueSize,
                "source" to "learn",
            ),
        )
    }

    fun trackLoreQueueConflictResult(episodeId: String, result: String) {
        AnalyticsEmit.event(
            "queue_modified",
            mapOf(
                "action" to "conflict_$result",
                "episode_id" to episodeId,
                "source" to "learn",
            ),
        )
    }

    fun trackSmartQueueEpisodeSkipped(
        episodeId: String,
        recommendationSource: String,
        positionInQueue: Int,
    ) {
        AnalyticsEmit.event(
            "queue_modified",
            mapOf(
                "action" to "skip",
                "episode_id" to episodeId,
                "source" to recommendationSource,
                "position_index" to positionInQueue,
            ),
        )
    }

    fun trackOfflineModeEntered() {
        AnalyticsEmit.event("offline_mode_entered")
    }

    fun trackDiscoverCategoryFiltered(categoryName: String) {
        AnalyticsEmit.event(
            "explore_recommendation_tapped",
            mapOf(
                "rail" to "category",
                "category_name" to categoryName,
            ),
        )
    }

    fun trackAutoChaptersRequested(episodeId: String, podcastId: String?, audioUrl: String) {
        PhaseCAnalyticsTracks.trackAutoChaptersLifecycle(
            stage = "requested",
            episodeId = episodeId,
            errorMessage = listOfNotNull(
                podcastId?.let { "podcast_id=$it" },
                audioUrl.takeIf { it.isNotBlank() }?.let { "has_audio=true" },
            ).joinToString(";").ifBlank { null },
        )
    }

    fun trackAutoChaptersCompleted(
        episodeId: String,
        podcastId: String?,
        durationSeconds: Float,
        chaptersCount: Int,
    ) {
        PhaseCAnalyticsTracks.trackAutoChaptersLifecycle(
            stage = "completed",
            episodeId = episodeId,
            errorMessage = listOfNotNull(
                podcastId?.let { "podcast_id=$it" },
                "chapters=$chaptersCount",
                "duration=$durationSeconds",
            ).joinToString(";"),
        )
    }

    fun trackAutoChaptersFailed(episodeId: String, podcastId: String?, errorMessage: String) {
        PhaseCAnalyticsTracks.trackAutoChaptersLifecycle(
            stage = "failed",
            episodeId = episodeId,
            errorMessage = listOfNotNull(podcastId?.let { "podcast_id=$it" }, errorMessage).joinToString(";"),
        )
    }

    fun trackAutoTranscriptRequested(episodeId: String, podcastId: String?, audioUrl: String) {
        PhaseCAnalyticsTracks.trackAutoTranscriptLifecycle(
            stage = "requested",
            episodeId = episodeId,
            errorMessage = listOfNotNull(
                podcastId?.let { "podcast_id=$it" },
                audioUrl.takeIf { it.isNotBlank() }?.let { "has_audio=true" },
            ).joinToString(";").ifBlank { null },
        )
    }

    fun trackAutoTranscriptCompleted(
        episodeId: String,
        podcastId: String?,
        durationSeconds: Float,
        linesCount: Int,
    ) {
        PhaseCAnalyticsTracks.trackAutoTranscriptLifecycle(
            stage = "completed",
            episodeId = episodeId,
            errorMessage = listOfNotNull(
                podcastId?.let { "podcast_id=$it" },
                "lines=$linesCount",
                "duration=$durationSeconds",
            ).joinToString(";"),
        )
    }

    fun trackAutoTranscriptFailed(episodeId: String, podcastId: String?, errorMessage: String) {
        PhaseCAnalyticsTracks.trackAutoTranscriptLifecycle(
            stage = "failed",
            episodeId = episodeId,
            errorMessage = listOfNotNull(podcastId?.let { "podcast_id=$it" }, errorMessage).joinToString(";"),
        )
    }

    fun trackDailyBriefingBannerTapped(region: String, date: String) {
        AnalyticsEmit.event(
            "daily_briefing_action",
            mapOf("action" to "banner_tapped", "region" to region, "date" to date),
        )
        AnalyticsEmit.event(
            "home_surface_tapped",
            mapOf(
                "surface_component" to "daily_briefing_banner",
                "content_id" to date,
                "rail_intent" to region,
            ),
        )
    }

    fun trackDailyBriefingPlayClicked(region: String, date: String, source: String) {
        AnalyticsEmit.event(
            "daily_briefing_action",
            mapOf(
                "action" to "play",
                "region" to region,
                "date" to date,
                "source" to source,
            ),
        )
        AnalyticsEmit.event(
            "home_surface_tapped",
            mapOf(
                "surface_component" to "daily_briefing_play",
                "content_id" to date,
                "rail_intent" to region,
            ),
        )
    }

    fun trackDailyBriefingPauseClicked(region: String, date: String, source: String) {
        AnalyticsEmit.event(
            "daily_briefing_action",
            mapOf(
                "action" to "pause",
                "region" to region,
                "date" to date,
                "source" to source,
            ),
        )
    }

    fun trackDailyBriefingInteraction(
        action: String,
        region: String,
        date: String,
        extraProps: Map<String, Any> = emptyMap(),
    ) {
        val props = mutableMapOf<String, Any>(
            "action" to action,
            "region" to region,
            "date" to date,
        )
        props.putAll(extraProps)
        AnalyticsEmit.event("daily_briefing_action", props)
    }

    fun trackDailyBriefingRegionChanged(previousRegion: String, newRegion: String, date: String) {
        AnalyticsEmit.event(
            "daily_briefing_action",
            mapOf(
                "action" to "region_changed",
                "region" to newRegion,
                "previous_region" to previousRegion,
                "date" to date,
            ),
        )
    }

    fun trackDailyBriefingRelatedEpisodeClicked(
        region: String,
        date: String,
        chapterIndex: Int,
        episodeId: String,
        episodeTitle: String,
        podcastId: String,
        podcastTitle: String,
    ) {
        AnalyticsEmit.event(
            "daily_briefing_action",
            mapOf(
                "action" to "related_episode_clicked",
                "region" to region,
                "date" to date,
                "content_id" to episodeId,
                "chapter_index" to chapterIndex,
                "episode_id" to episodeId,
                "episode_title" to episodeTitle,
                "podcast_id" to podcastId,
                "podcast_title" to podcastTitle,
            ),
        )
    }

    fun trackDailyBriefingCardImpression(region: String, date: String, playbackStatus: String) {
        AnalyticsEmit.event(
            "daily_briefing_action",
            mapOf(
                "action" to "impression",
                "region" to region,
                "date" to date,
                "playback_status" to playbackStatus,
            ),
        )
        AnalyticsEmit.event(
            "home_surface_impression",
            mapOf(
                "surface_component" to "daily_briefing",
                "items_count" to 1,
            ),
        )
    }

    fun trackDailyBriefingScreenViewed(region: String, date: String, source: String? = null) {
        val props = mutableMapOf<String, Any>(
            "action" to "screen_viewed",
            "region" to region,
            "date" to date,
        )
        source?.let { props["source"] = it }
        AnalyticsEmit.event("daily_briefing_action", props)
    }

    fun trackNavTabClicked(tabName: String) {
        AnalyticsEmit.event(
            "nav_tab_clicked",
            mapOf("tab" to tabName),
        )
    }

    fun trackLearnScreenViewed() {
        AnalyticsEmit.event("learn_screen_viewed")
    }

    private fun trackLearnCardAction(
        action: String,
        episodeId: String,
        episodeTitle: String?,
        podcastId: String?,
        podcastTitle: String?,
        positionIndex: Int? = null,
    ) {
        val props = mutableMapOf<String, Any>(
            "action" to action,
            "episode_id" to episodeId,
            "podcast_id" to (podcastId ?: "unknown"),
        )
        episodeTitle?.let { props["episode_title"] = it }
        podcastTitle?.let { props["podcast_title"] = it }
        positionIndex?.let { props["position_index"] = it }
        AnalyticsEmit.event("learn_card_action", props)
    }

    fun trackLearnCardDismissed(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) {
        trackLearnCardAction("dismiss", episodeId, episodeTitle, podcastId, podcastTitle)
    }

    fun trackLearnCardQueued(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) {
        trackLearnCardAction("queue", episodeId, episodeTitle, podcastId, podcastTitle)
    }

    fun trackLearnCardInfoClicked(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) {
        trackLearnCardAction("info", episodeId, episodeTitle, podcastId, podcastTitle)
    }

    fun trackLearnCardPodcastClicked(podcastId: String?, podcastTitle: String?) {
        AnalyticsEmit.event(
            "learn_card_action",
            buildMap {
                put("action", "podcast")
                put("episode_id", "unknown")
                put("podcast_id", podcastId ?: "unknown")
                podcastTitle?.let { put("podcast_title", it) }
            },
        )
    }

    fun trackLearnCardPlayClicked(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) {
        trackLearnCardAction("play", episodeId, episodeTitle, podcastId, podcastTitle)
    }

    fun trackLearnScreenSession(
        timeSpentSeconds: Float,
        cardsDismissedCount: Int,
        cardsQueuedCount: Int,
        playsCount: Int,
        podcastsClickedCount: Int,
        infosClickedCount: Int,
    ) {
        AnalyticsEmit.event(
            "learn_screen_viewed",
            mapOf(
                "cards_available" to (
                    cardsDismissedCount + cardsQueuedCount + playsCount +
                        podcastsClickedCount + infosClickedCount
                    ),
                "time_spent_seconds" to timeSpentSeconds,
                "cards_dismissed_count" to cardsDismissedCount,
                "cards_queued_count" to cardsQueuedCount,
                "plays_count" to playsCount,
                "podcasts_clicked_count" to podcastsClickedCount,
                "infos_clicked_count" to infosClickedCount,
            ),
        )
    }
}
