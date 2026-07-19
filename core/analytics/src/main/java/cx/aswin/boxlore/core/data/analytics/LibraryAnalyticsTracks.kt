package cx.aswin.boxlore.core.data.analytics

import com.posthog.PostHog

internal object LibraryAnalyticsTracks {
    fun trackLibraryHubViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "library_hub_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackLibraryHubSession(timeSpentSeconds: Float, navigatedTo: String?) {
        val props = mutableMapOf<String, Any>("time_spent_seconds" to timeSpentSeconds)
        navigatedTo?.let { props["navigated_to"] = it }
        PostHog.capture(event = "library_hub_session", properties = props)
    }

    fun trackLibrarySubscriptionsViewed(sourceEntryPoint: String, initialTab: String) {
        PostHog.capture(
            event = "library_subscriptions_viewed",
            properties = mapOf(
                "source_entry_point" to sourceEntryPoint,
                "initial_tab" to initialTab
            )
        )
    }

    fun trackLibrarySubscriptionsSession(
        timeSpentSeconds: Float,
        tabSwitchesCount: Int,
        didSearch: Boolean,
        finalSearchQuery: String?,
        podcastsClickedCount: Int,
        episodesClickedCount: Int
    ) {
        val props = mutableMapOf<String, Any>(
            "time_spent_seconds" to timeSpentSeconds,
            "tab_switches_count" to tabSwitchesCount,
            "did_search" to didSearch,
            "podcasts_clicked_count" to podcastsClickedCount,
            "episodes_clicked_count" to episodesClickedCount
        )
        finalSearchQuery?.let { props["final_search_query"] = it }
        PostHog.capture(event = "library_subscriptions_session", properties = props)
    }

    fun trackLibraryLikedViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "library_liked_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackLibraryLikedSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesUnlikedCount: Int) {
        PostHog.capture(
            event = "library_liked_session",
            properties = mapOf(
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "episodes_unliked_count" to episodesUnlikedCount
            )
        )
    }

    fun trackLibraryDownloadsViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "library_downloads_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackLibraryDownloadsSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesDeletedCount: Int) {
        PostHog.capture(
            event = "library_downloads_session",
            properties = mapOf(
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "episodes_deleted_count" to episodesDeletedCount
            )
        )
    }

    fun trackLibraryHistoryViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "library_history_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackLibraryHistorySession(timeSpentSeconds: Float, episodesClickedCount: Int, itemsDeletedCount: Int) {
        PostHog.capture(
            event = "library_history_session",
            properties = mapOf(
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "items_deleted_count" to itemsDeletedCount
            )
        )
    }

    fun trackTopControlbarInteraction(action: String, screen: String) {
        PostHog.capture(
            event = "top_controlbar_interaction",
            properties = mapOf(
                "action" to action,
                "screen" to screen
            )
        )
    }

    fun trackSettingsScreenViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "settings_screen_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackSettingsInteraction(action: String, value: String? = null) {
        val props = mutableMapOf<String, Any>("action" to action)
        if (value != null) props["value"] = value
        PostHog.capture(event = "settings_interaction", properties = props)
    }

    fun trackMiniPlayerInteraction(
        action: String,
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null
    ) {
        val props = mutableMapOf<String, Any>("action" to action)
        if (podcastId != null) props["podcast_id"] = podcastId
        if (episodeId != null) props["episode_id"] = episodeId
        if (podcastName != null) props["podcast_name"] = podcastName
        if (episodeTitle != null) props["episode_title"] = episodeTitle
        PostHog.capture(event = "mini_player_interaction", properties = props)
    }

    fun trackFullPlayerScreenSession(
        podcastId: String?,
        episodeId: String?,
        metrics: Map<String, Any>,
        podcastName: String? = null,
        episodeTitle: String? = null
    ) {
        val props = mutableMapOf<String, Any>()
        if (podcastId != null) props["podcast_id"] = podcastId
        if (episodeId != null) props["episode_id"] = episodeId
        if (podcastName != null) props["podcast_name"] = podcastName
        if (episodeTitle != null) props["episode_title"] = episodeTitle
        props.putAll(metrics)
        PostHog.capture(event = "full_player_screen_session", properties = props)
    }

    // ── 13. System & Background ────────────────────────────────────

    fun trackNotificationTapped() {
        PostHog.capture("notification_tapped")
    }

    fun trackDownloadCompleted(fileSizeMb: Float) {
        PostHog.capture(
            event = "download_completed",
            properties = mapOf("file_size_mb" to fileSizeMb)
        )
    }

    fun trackDownloadFailed(errorReason: String) {
        PostHog.capture(
            event = "download_failed",
            properties = mapOf("error_reason" to errorReason)
        )
    }

    fun trackPlayMixClicked(count: Int) {
        PostHog.capture(
            event = "play_mix_clicked",
            properties = mapOf("episode_count" to count)
        )
    }

    fun trackHomePodcastFiltered(podcastId: String, title: String) {
        PostHog.capture(
            event = "home_podcast_filtered",
            properties = mapOf("podcast_id" to podcastId, "podcast_title" to title)
        )
    }

    fun trackLibrarySubscriptionsLayoutToggled(isGridView: Boolean) {
        PostHog.capture(
            event = "library_subscriptions_layout_toggled",
            properties = mapOf(
                "layout_type" to if (isGridView) "grid" else "list"
            )
        )
    }

    fun trackLibrarySubscriptionsSortChanged(sortMethod: String, tab: String) {
        PostHog.capture(
            event = "library_subscriptions_sort_changed",
            properties = mapOf(
                "sort_method" to sortMethod,
                "tab" to tab
            )
        )
    }

    fun trackLibrarySubscriptionsGenreFiltered(genreName: String, tab: String) {
        PostHog.capture(
            event = "library_subscriptions_genre_filtered",
            properties = mapOf(
                "genre_name" to genreName,
                "tab" to tab
            )
        )
    }
}
