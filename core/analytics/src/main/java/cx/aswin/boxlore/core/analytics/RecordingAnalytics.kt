package cx.aswin.boxlore.core.analytics

import android.content.Context
import cx.aswin.boxlore.core.model.HomeSlateQualityTelemetry
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry
import cx.aswin.boxlore.core.model.RankingExposureHealthTelemetry

/**
 * Test-double [Analytics] implementation that records every captured event in-memory.
 * Suitable for use in unit tests and `:core:testing` helpers — no PostHog SDK required.
 *
 * Usage:
 * ```kotlin
 * val analytics = RecordingAnalytics()
 * analytics.capture("my_event", mapOf("key" to "value"))
 * assertEquals(1, analytics.eventCount("my_event"))
 * ```
 */
class RecordingAnalytics : Analytics {
    data class CapturedEvent(
        val name: String,
        val properties: Map<String, Any>,
    )

    private val _events = mutableListOf<CapturedEvent>()

    /** All events captured since construction (or last [clear]). */
    val events: List<CapturedEvent> get() = _events.toList()

    /** Returns the number of times [eventName] was captured. */
    fun eventCount(eventName: String): Int = _events.count { it.name == eventName }

    /** Returns all captured events with the given [eventName]. */
    fun eventsNamed(eventName: String): List<CapturedEvent> = _events.filter { it.name == eventName }

    /** Returns the last captured event, or null if none. */
    val lastEvent: CapturedEvent? get() = _events.lastOrNull()

    /** Clears all recorded events. */
    fun clear() = _events.clear()

    // ── Analytics interface ────────────────────────────────────────

    override fun capture(
        event: String,
        properties: Map<String, Any>,
    ) {
        if (!AnalyticsGlossary.isAllowedEvent(event)) return
        _events.add(CapturedEvent(event, properties))
    }

    override fun trackFirstLaunchIfNecessary(context: Context) {
        // Mirror production: person props only — never emit app_open (SDK-backed).
    }

    override fun flush() {
        // no-op in recording mode
    }

    /** Phase C — `adaptive_ranking_status`. */
    override fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>) {
        val statusSummary =
            statuses
                .joinToString(separator = ",") { "${it.objective}:${it.learningStage}" }
                .ifBlank { "empty" }
        capture(
            "adaptive_ranking_status",
            mapOf(
                "status" to statusSummary,
                "details" to "count=${statuses.size}",
            ),
        )
    }

    override fun trackEngagementPromptShown(
        promptType: String,
        source: String,
        completedEpisodes: Int?,
    ) {
        capture(
            "feedback_submitted",
            buildMap {
                put("feedback_type", promptType)
                put("source", source)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
    }

    override fun trackSurveyNpsEligible(
        completedEpisodes: Int?,
        triggerContext: String,
    ) {
        capture(
            "feedback_submitted",
            buildMap {
                put("feedback_type", "nps_eligible")
                put("source", triggerContext)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
    }

    override fun trackSurveyNpsManualTrigger(source: String) {
        capture(
            "feedback_submitted",
            mapOf(
                "feedback_type" to "nps_manual",
                "source" to source,
            ),
        )
    }

    override fun trackPromoterReviewHandoff(npsScore: Int?) {
        capture(
            "feedback_submitted",
            buildMap {
                put("feedback_type", "promoter_review_handoff")
                npsScore?.let { put("score", it) }
            },
        )
    }

    override fun trackFirstEpisodePlayed() {
        capture("first_episode_played")
    }

    /** Phase D — `home_slate_quality_snapshot`. */
    override fun trackHomeSlateQualitySnapshot(telemetry: HomeSlateQualityTelemetry) {
        capture(
            "home_slate_quality_snapshot",
            mapOf(
                "mode" to telemetry.mode,
                "algorithm_version" to (telemetry.algorithmVersion ?: "unknown"),
                "from_cache" to telemetry.fromCache,
                "is_fallback" to telemetry.isFallback,
                "requested_module_count" to telemetry.requestedModuleCount,
                "non_empty_module_count" to telemetry.nonEmptyModuleCount,
                "allocated_candidate_count" to telemetry.allocatedCandidateCount,
                "duplicate_rate_percent" to telemetry.duplicateRatePercent,
                "novelty_rate_percent" to telemetry.noveltyRatePercent,
                "cache_age_bucket" to telemetry.cacheAgeBucket,
                "response_latency_bucket" to telemetry.responseLatencyBucket,
            ),
        )
    }

    /** Phase D — `home_learning_attribution_health`. */
    override fun trackHomeLearningAttributionHealth(telemetry: RankingExposureHealthTelemetry) {
        capture(
            "home_learning_attribution_health",
            mapOf(
                "surface" to telemetry.surface,
                "total_exposures" to telemetry.totalExposures,
                "resolution_rate_percent" to telemetry.resolutionRatePercent,
                "pending_exposure_count" to telemetry.pendingExposureCount,
                "oldest_pending_age_bucket" to telemetry.oldestPendingAgeBucket,
                "unmatched_outcome_rate_percent" to telemetry.unmatchedOutcomeRatePercent,
            ),
        )
    }

    override fun trackAppCheckStatus(
        tokenObtained: Boolean,
        provider: String,
    ) {
        capture(
            "app_check_status",
            mapOf("token_obtained" to tokenObtained, "provider" to provider),
        )
    }
}
