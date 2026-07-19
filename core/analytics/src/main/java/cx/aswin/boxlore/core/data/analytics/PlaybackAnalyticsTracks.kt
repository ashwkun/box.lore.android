package cx.aswin.boxlore.core.data.analytics

@Suppress("LongParameterList")
internal object PlaybackAnalyticsTracks {
    fun trackPlaybackStarted(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        startPositionSeconds: Float,
        totalDurationSeconds: Float,
        isRepeating: Boolean,
        isSubscribed: Boolean,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null,
        playbackMode: String = "stream",
        clientSurface: String = "phone",
        speed: Float = 1.0f,
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "entry_point" to AnalyticsGlossary.normalizeEntryPoint(entryPoint),
            "is_resume" to isRepeating,
            "playback_mode" to playbackMode,
            "client_surface" to clientSurface,
            "speed" to speed,
            "is_subscribed" to isSubscribed,
            "position_seconds" to startPositionSeconds,
            "duration_seconds" to totalDurationSeconds,
            // Legacy-compatible aliases retained for transition queries
            "start_position_seconds" to startPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "is_repeating" to isRepeating,
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        entryPointContext?.let { props.putAll(it) }

        AnalyticsEmit.event("playback_started", props)
    }

    fun trackPlaybackPaused(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        durationPlayedSeconds: Float,
        totalBufferedTimeSeconds: Float,
        totalDurationSeconds: Float,
        isCompleted: Boolean,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null,
        queueSize: Int? = null,
        pauseReason: String = "user_voluntary",
        playbackMode: String = "stream",
        clientSurface: String = "phone",
    ) {
        val percent =
            if (totalDurationSeconds > 0f) {
                (durationPlayedSeconds / totalDurationSeconds * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "entry_point" to AnalyticsGlossary.normalizeEntryPoint(entryPoint),
            "position_seconds" to durationPlayedSeconds,
            "duration_seconds" to totalDurationSeconds,
            "percent_complete" to percent,
            "listened_delta_seconds" to durationPlayedSeconds,
            "pause_reason" to pauseReason,
            "playback_mode" to playbackMode,
            "client_surface" to clientSurface,
            "duration_played_seconds" to durationPlayedSeconds,
            "total_buffered_time_seconds" to totalBufferedTimeSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "is_completed" to isCompleted,
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        entryPointContext?.let { props.putAll(it) }
        queueSize?.let { props["queue_size"] = it }

        AnalyticsEmit.event("playback_paused", props)
    }

    fun trackPlaybackCompleted(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        totalDurationSeconds: Float,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null,
        listenedDeltaSeconds: Float? = null,
        playbackMode: String = "stream",
        clientSurface: String = "phone",
        speed: Float? = null,
        isSubscribed: Boolean? = null,
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "entry_point" to AnalyticsGlossary.normalizeEntryPoint(entryPoint),
            "listened_delta_seconds" to (listenedDeltaSeconds ?: totalDurationSeconds),
            "duration_seconds" to totalDurationSeconds,
            "playback_mode" to playbackMode,
            "client_surface" to clientSurface,
            "total_duration_seconds" to totalDurationSeconds,
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        speed?.let { props["speed"] = it }
        isSubscribed?.let { props["is_subscribed"] = it }
        entryPointContext?.let { props.putAll(it) }

        AnalyticsEmit.event("playback_completed", props)
    }

    fun trackPlaybackHeartbeat(
        podcastId: String?,
        podcastName: String?,
        episodeId: String,
        episodeTitle: String?,
        currentPositionSeconds: Float,
        totalDurationSeconds: Float,
        heartbeatPercentage: Int,
        heartbeatType: String,
        entryPoint: String? = null,
        playbackMode: String = "stream",
        clientSurface: String = "phone",
        speed: Float? = null,
        isSubscribed: Boolean? = null,
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "entry_point" to AnalyticsGlossary.normalizeEntryPoint(entryPoint),
            "position_seconds" to currentPositionSeconds,
            "duration_seconds" to totalDurationSeconds,
            "percent_complete" to heartbeatPercentage.toFloat(),
            "milestone" to heartbeatPercentage,
            "playback_mode" to playbackMode,
            "client_surface" to clientSurface,
            "current_position_seconds" to currentPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "heartbeat_percentage" to heartbeatPercentage,
            "heartbeat_type" to heartbeatType,
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        speed?.let { props["speed"] = it }
        isSubscribed?.let { props["is_subscribed"] = it }

        AnalyticsEmit.event("playback_heartbeat", props)
    }

    fun trackPlaybackSeeked(
        podcastId: String?,
        podcastName: String?,
        episodeId: String,
        episodeTitle: String?,
        fromPositionSeconds: Float,
        toPositionSeconds: Float,
        totalDurationSeconds: Float,
        seekSource: String,
        entryPoint: String? = null,
        clientSurface: String = "phone",
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "entry_point" to AnalyticsGlossary.normalizeEntryPoint(entryPoint),
            "from_seconds" to fromPositionSeconds,
            "to_seconds" to toPositionSeconds,
            "seek_source" to seekSource,
            "client_surface" to clientSurface,
            "from_position_seconds" to fromPositionSeconds,
            "to_position_seconds" to toPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }

        AnalyticsEmit.event("playback_seeked", props)
    }

    fun trackPlaybackError(
        errorCode: String,
        errorMessage: String,
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null,
        entryPoint: String? = null,
        playbackMode: String? = null,
        clientSurface: String? = null,
    ) {
        val props = mutableMapOf<String, Any>(
            "error_type" to errorCode,
            "error_message" to errorMessage,
            "error_code" to errorCode,
            "entry_point" to AnalyticsGlossary.normalizeEntryPoint(entryPoint),
        )
        podcastId?.let { props["podcast_id"] = it }
        episodeId?.let { props["episode_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        playbackMode?.let { props["playback_mode"] = it }
        clientSurface?.let { props["client_surface"] = it }
        AnalyticsEmit.event("playback_error", props)
    }

    fun trackPlaybackBuffering(
        episodeId: String? = null,
        podcastId: String? = null,
        entryPoint: String? = null,
        bufferDurationMs: Long? = null,
        playbackMode: String? = null,
        clientSurface: String? = null,
    ) {
        val props = mutableMapOf<String, Any>(
            "entry_point" to AnalyticsGlossary.normalizeEntryPoint(entryPoint),
        )
        episodeId?.let { props["episode_id"] = it }
        podcastId?.let { props["podcast_id"] = it }
        bufferDurationMs?.let { props["buffer_duration_ms"] = it }
        playbackMode?.let { props["playback_mode"] = it }
        clientSurface?.let { props["client_surface"] = it }
        AnalyticsEmit.event("playback_buffering", props)
    }

    fun trackExploreScreenViewed(sourceEntryPoint: String? = null) {
        val props = mutableMapOf<String, Any>()
        sourceEntryPoint?.let { props["tab"] = it }
        AnalyticsEmit.event("explore_screen_viewed", props)
    }

    fun trackExploreSearchPerformed(query: String, resultsCount: Int) {
        val trimmed = query.trim()
        AnalyticsEmit.event(
            "search_performed",
            buildMap {
                put("surface", "explore")
                put("search_mode", "show_keyword")
                put("search_query", trimmed)
                put("results_count", resultsCount)
                put("query_length", trimmed.length)
            },
        )
    }

    fun trackExploreScreenSession(
        timeSpentSeconds: Float,
        categoriesClickedCount: Int,
        vibesClickedCount: Int,
        searchesPerformedCount: Int,
        podcastsClickedCount: Int,
        maxScrollDepth: Int,
        finalCategoryState: String,
        finalVibeState: String?,
        finalSearchQuery: String?,
    ) {
        // Fold session summary into explore_screen_viewed (glossary Phase B).
        val props = mutableMapOf<String, Any>(
            "tab" to finalCategoryState,
            "time_spent_seconds" to timeSpentSeconds,
            "categories_clicked_count" to categoriesClickedCount,
            "vibes_clicked_count" to vibesClickedCount,
            "searches_performed_count" to searchesPerformedCount,
            "podcasts_clicked_count" to podcastsClickedCount,
            "max_scroll_depth" to maxScrollDepth,
        )
        finalVibeState?.let { props["final_vibe_state"] = it }
        finalSearchQuery?.let { props["final_search_query"] = it }
        AnalyticsEmit.event("explore_screen_viewed", props)
    }
}
