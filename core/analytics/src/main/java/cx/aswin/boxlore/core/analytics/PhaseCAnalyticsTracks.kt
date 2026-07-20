package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.model.RankingAggregateTelemetry

/**
 * Phase C glossary events (Auto + polish) — PR9.
 */
internal object PhaseCAnalyticsTracks {
    fun trackAndroidAutoConnected(sessionId: String? = null) {
        AnalyticsEmit.event(
            "android_auto_connected",
            buildMap {
                sessionId?.let { put("session_id", it) }
            },
        )
    }

    fun trackAndroidAutoDisconnected(
        sessionId: String? = null,
        durationSeconds: Int? = null,
    ) {
        AnalyticsEmit.event(
            "android_auto_disconnected",
            buildMap {
                sessionId?.let { put("session_id", it) }
                durationSeconds?.let { put("duration_seconds", it) }
            },
        )
    }

    fun trackAndroidAutoBrowse(
        node: String,
        action: String? = null,
    ) {
        AnalyticsEmit.event(
            "android_auto_browse",
            buildMap {
                put("node", node)
                action?.let { put("action", it) }
            },
        )
    }

    fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>) {
        val statusSummary =
            statuses
                .joinToString(separator = ",") { "${it.objective}:${it.learningStage}" }
                .ifBlank { "empty" }
        AnalyticsEmit.event(
            "adaptive_ranking_status",
            mapOf(
                "status" to statusSummary,
                "details" to "count=${statuses.size}",
            ),
        )
    }

    fun trackLearnCaughtUp(cardsRemaining: Int? = null) {
        AnalyticsEmit.event(
            "learn_caught_up",
            buildMap {
                cardsRemaining?.let { put("cards_remaining", it) }
            },
        )
    }

    fun trackCatalogMiss(
        lookupType: String,
        key: String? = null,
    ) {
        AnalyticsEmit.event(
            "catalog_miss",
            buildMap {
                put("lookup_type", lookupType)
                key?.let { put("key", it) }
            },
        )
    }

    fun trackRssRefreshFailed(
        podcastId: String? = null,
        errorType: String? = null,
    ) {
        AnalyticsEmit.event(
            "rss_refresh_failed",
            buildMap {
                podcastId?.let { put("podcast_id", it) }
                errorType?.let { put("error_type", it) }
            },
        )
    }

    fun trackProgressSyncAnomaly(
        anomalyType: String,
        episodeId: String? = null,
    ) {
        AnalyticsEmit.event(
            "progress_sync_anomaly",
            buildMap {
                put("anomaly_type", anomalyType)
                episodeId?.let { put("episode_id", it) }
            },
        )
    }

    fun trackLateNightSafeguardDecision(
        decision: String,
        durationMinutes: Int? = null,
    ) {
        AnalyticsEmit.event(
            "late_night_safeguard_decision",
            buildMap {
                put("decision", decision)
                durationMinutes?.let { put("duration_minutes", it) }
            },
        )
    }

    fun trackAutoChaptersLifecycle(
        stage: String,
        episodeId: String? = null,
        errorMessage: String? = null,
    ) {
        AnalyticsEmit.event(
            "auto_chapters_lifecycle",
            buildMap {
                put("stage", stage)
                episodeId?.let { put("episode_id", it) }
                errorMessage?.let { put("error_message", it) }
            },
        )
    }

    fun trackAutoTranscriptLifecycle(
        stage: String,
        episodeId: String? = null,
        errorMessage: String? = null,
    ) {
        AnalyticsEmit.event(
            "auto_transcript_lifecycle",
            buildMap {
                put("stage", stage)
                episodeId?.let { put("episode_id", it) }
                errorMessage?.let { put("error_message", it) }
            },
        )
    }

    fun trackProxyFallbackTriggered(
        imageHost: String,
        proxyWidth: Int,
        sampleMultiplier: Int = 10,
    ) {
        AnalyticsEmit.event(
            "proxy_fallback_triggered",
            mapOf(
                "reason" to "host=$imageHost;width=$proxyWidth;sample=$sampleMultiplier",
            ),
        )
    }
}
