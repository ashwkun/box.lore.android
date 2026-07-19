package cx.aswin.boxlore.core.data.analytics

internal object LibraryAnalyticsTracks {
    fun trackLibraryHubViewed(sourceEntryPoint: String) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf(
                "destination" to "hub",
                "source" to sourceEntryPoint,
            ),
        )
    }

    fun trackLibraryHubSession(timeSpentSeconds: Float, navigatedTo: String?) {
        val props = mutableMapOf<String, Any>(
            "destination" to "hub",
            "time_spent_seconds" to timeSpentSeconds,
        )
        navigatedTo?.let { props["navigated_to"] = it }
        AnalyticsEmit.event("library_destination_viewed", props)
    }

    fun trackLibrarySubscriptionsViewed(sourceEntryPoint: String, initialTab: String) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf(
                "destination" to "subscriptions",
                "source" to sourceEntryPoint,
                "initial_tab" to initialTab,
            ),
        )
    }

    fun trackLibrarySubscriptionsSession(
        timeSpentSeconds: Float,
        tabSwitchesCount: Int,
        didSearch: Boolean,
        finalSearchQuery: String?,
        podcastsClickedCount: Int,
        episodesClickedCount: Int,
    ) {
        val props = mutableMapOf<String, Any>(
            "destination" to "subscriptions",
            "time_spent_seconds" to timeSpentSeconds,
            "tab_switches_count" to tabSwitchesCount,
            "did_search" to didSearch,
            "podcasts_clicked_count" to podcastsClickedCount,
            "episodes_clicked_count" to episodesClickedCount,
        )
        finalSearchQuery?.let { props["final_search_query"] = it }
        AnalyticsEmit.event("library_destination_viewed", props)
    }

    fun trackLibraryLikedViewed(sourceEntryPoint: String) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf("destination" to "liked", "source" to sourceEntryPoint),
        )
    }

    fun trackLibraryLikedSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesUnlikedCount: Int) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf(
                "destination" to "liked",
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "episodes_unliked_count" to episodesUnlikedCount,
            ),
        )
    }

    fun trackLibraryDownloadsViewed(sourceEntryPoint: String) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf("destination" to "downloads", "source" to sourceEntryPoint),
        )
    }

    fun trackLibraryDownloadsSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesDeletedCount: Int) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf(
                "destination" to "downloads",
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "episodes_deleted_count" to episodesDeletedCount,
            ),
        )
    }

    fun trackLibraryHistoryViewed(sourceEntryPoint: String) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf("destination" to "history", "source" to sourceEntryPoint),
        )
    }

    fun trackLibraryHistorySession(timeSpentSeconds: Float, episodesClickedCount: Int, itemsDeletedCount: Int) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf(
                "destination" to "history",
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "items_deleted_count" to itemsDeletedCount,
            ),
        )
    }

    fun trackTopControlbarInteraction(action: String, screen: String) {
        AnalyticsEmit.event(
            "player_chrome_interaction",
            mapOf(
                "surface" to "top_controlbar",
                "action" to action,
                "screen" to screen,
            ),
        )
    }

    fun trackSettingsScreenViewed(sourceEntryPoint: String) {
        AnalyticsEmit.event(
            "settings_interaction",
            mapOf(
                "action" to "screen_viewed",
                "setting_key" to sourceEntryPoint,
            ),
        )
    }

    fun trackSettingsInteraction(action: String, value: String? = null) {
        val props = mutableMapOf<String, Any>(
            "action" to action,
            "setting_key" to action,
        )
        if (value != null) props["value"] = value
        AnalyticsEmit.event("settings_interaction", props)
    }

    fun trackMiniPlayerInteraction(
        action: String,
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null,
    ) {
        val props = mutableMapOf<String, Any>(
            "surface" to "player_mini",
            "action" to action,
        )
        if (podcastId != null) props["podcast_id"] = podcastId
        if (episodeId != null) props["episode_id"] = episodeId
        if (podcastName != null) props["podcast_name"] = podcastName
        if (episodeTitle != null) props["episode_title"] = episodeTitle
        AnalyticsEmit.event("player_chrome_interaction", props)
    }

    fun trackFullPlayerScreenSession(
        podcastId: String?,
        episodeId: String?,
        metrics: Map<String, Any>,
        podcastName: String? = null,
        episodeTitle: String? = null,
    ) {
        val props = mutableMapOf<String, Any>(
            "surface" to "player_full",
            "action" to "session_end",
        )
        if (podcastId != null) props["podcast_id"] = podcastId
        if (episodeId != null) props["episode_id"] = episodeId
        if (podcastName != null) props["podcast_name"] = podcastName
        if (episodeTitle != null) props["episode_title"] = episodeTitle
        props.putAll(metrics)
        AnalyticsEmit.event("player_chrome_interaction", props)
    }

    fun trackNotificationTapped() {
        AnalyticsEmit.event(
            "notification_tapped",
            mapOf("notification_type" to "unknown"),
        )
    }

    fun trackDownloadCompleted(fileSizeMb: Float) {
        AnalyticsEmit.event(
            "download_completed",
            mapOf(
                "episode_id" to "unknown",
                "podcast_id" to "unknown",
                "bytes" to (fileSizeMb * 1024f * 1024f).toLong(),
                "file_size_mb" to fileSizeMb,
            ),
        )
    }

    fun trackDownloadFailed(errorReason: String) {
        AnalyticsEmit.event(
            "download_failed",
            mapOf(
                "error_type" to errorReason,
                "error_message" to errorReason,
                "error_reason" to errorReason,
            ),
        )
    }

    fun trackPlayMixClicked(count: Int) {
        AnalyticsEmit.event(
            "home_surface_tapped",
            mapOf(
                "surface_component" to "mixtape_play",
                "items_count" to count,
            ),
        )
    }

    fun trackHomePodcastFiltered(podcastId: String, title: String) {
        AnalyticsEmit.event(
            "home_surface_tapped",
            mapOf(
                "surface_component" to "podcast_filter",
                "content_id" to podcastId,
                "podcast_id" to podcastId,
                "podcast_title" to title,
            ),
        )
    }

    fun trackLibrarySubscriptionsLayoutToggled(isGridView: Boolean) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf(
                "destination" to "subscriptions",
                "layout_type" to if (isGridView) "grid" else "list",
                "action" to "layout_toggled",
            ),
        )
    }

    fun trackLibrarySubscriptionsSortChanged(sortMethod: String, tab: String) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf(
                "destination" to "subscriptions",
                "sort_method" to sortMethod,
                "tab" to tab,
                "action" to "sort_changed",
            ),
        )
    }

    fun trackLibrarySubscriptionsGenreFiltered(genreName: String, tab: String) {
        AnalyticsEmit.event(
            "library_destination_viewed",
            mapOf(
                "destination" to "subscriptions",
                "genre_name" to genreName,
                "tab" to tab,
                "action" to "genre_filtered",
            ),
        )
    }
}
