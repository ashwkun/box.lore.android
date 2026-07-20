package cx.aswin.boxlore.core.analytics

object PlayerSessionAggregator {
    private var isSessionActive = false
    private var podcastId: String? = null
    private var episodeId: String? = null
    private var podcastName: String? = null
    private var episodeTitle: String? = null
    private var sessionStartTimeMs: Long = 0L

    private val actionCounts = mutableMapOf<String, Int>()
    private val propertyValues = mutableMapOf<String, String>()

    fun startSession(
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null,
    ) {
        // If a session is already active for a different episode, flush it first
        if (isSessionActive && (this.episodeId != episodeId || this.podcastId != podcastId)) {
            endSession()
        }

        if (!isSessionActive) {
            isSessionActive = true
            this.podcastId = podcastId
            this.episodeId = episodeId
            this.podcastName = podcastName
            this.episodeTitle = episodeTitle
            this.sessionStartTimeMs = System.currentTimeMillis()
            actionCounts.clear()
            propertyValues.clear()
        }
    }

    fun logAction(
        action: String,
        value: String? = null,
    ) {
        if (!isSessionActive) return // Ignore clicks if no session is active

        actionCounts[action] = (actionCounts[action] ?: 0) + 1

        if (value != null) {
            // Keep the last set value for things like sleep_timer or speed_change
            propertyValues[action] = value
        }
    }

    fun endSession() {
        if (!isSessionActive) return

        val timeSpentSeconds = (System.currentTimeMillis() - sessionStartTimeMs) / 1000f

        // Map local counters to the expected schema
        val metrics =
            mutableMapOf<String, Any>(
                "time_spent_seconds" to timeSpentSeconds,
                "play_pause_count" to (actionCounts["play_pause"] ?: 0),
                "seek_count" to (actionCounts["seek"] ?: 0),
                "previous_count" to (actionCounts["previous"] ?: 0),
                "next_count" to (actionCounts["next"] ?: 0),
                "skip_previous_episode_count" to (actionCounts["skip_previous_episode"] ?: 0),
                "skip_next_episode_count" to (actionCounts["skip_next_episode"] ?: 0),
                "speed_change_count" to (actionCounts["speed_change"] ?: 0),
                "sleep_timer_count" to (actionCounts["sleep_timer"] ?: 0),
                "like_count" to (actionCounts["like"] ?: 0),
                "download_count" to (actionCounts["download"] ?: 0),
                "queue_count" to (actionCounts["queue"] ?: 0),
                "episode_info_clicks" to (actionCounts["episode_info"] ?: 0),
                "podcast_info_clicks" to (actionCounts["podcast_info"] ?: 0),
                "chapters_sheet_toggles" to (actionCounts["chapters_sheet"] ?: 0),
                "transcript_view_toggles" to (actionCounts["transcript_view"] ?: 0),
            )

        // Include any terminal property values (like the final speed or sleep timer duration)
        propertyValues.forEach { (action, value) ->
            metrics["final_${action}_value"] = value
        }

        AnalyticsHelper.trackFullPlayerScreenSession(
            podcastId = podcastId,
            episodeId = episodeId,
            metrics = metrics,
            podcastName = podcastName,
            episodeTitle = episodeTitle,
        )

        // Reset
        isSessionActive = false
        podcastId = null
        episodeId = null
        podcastName = null
        episodeTitle = null
        sessionStartTimeMs = 0L
        actionCounts.clear()
        propertyValues.clear()
    }
}
