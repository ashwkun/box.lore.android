package cx.aswin.boxlore.core.data.analytics

import android.content.Context
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry

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

    data class CapturedEvent(val name: String, val properties: Map<String, Any>)

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

    override fun capture(event: String, properties: Map<String, Any>) {
        _events.add(CapturedEvent(event, properties))
    }

    override fun trackFirstLaunchIfNecessary(context: Context) {
        capture("first_launch_check")
    }

    override fun flush() {
        /* no-op in recording mode */
    }

    override fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>) {
        capture(
            "adaptive_ranking_status",
            mapOf("objective_count" to statuses.size),
        )
    }

    override fun trackEngagementPromptShown(
        promptType: String,
        source: String,
        completedEpisodes: Int?,
    ) {
        capture(
            "engagement_prompt_shown",
            buildMap {
                put("prompt_type", promptType)
                put("source", source)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
    }

    override fun trackSurveyNpsEligible(completedEpisodes: Int?, triggerContext: String) {
        capture(
            "survey_nps_eligible",
            buildMap {
                put("trigger_context", triggerContext)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
    }

    override fun trackSurveyNpsManualTrigger(source: String) {
        capture("survey_nps_manual_trigger", mapOf("trigger_source" to source))
    }

    override fun trackPromoterReviewHandoff(npsScore: Int?) {
        capture("promoter_review_handoff", buildMap { npsScore?.let { put("nps_score", it) } })
    }

    override fun trackFirstEpisodePlayed() {
        capture("first_episode_played")
    }

    override fun trackAppCheckStatus(tokenObtained: Boolean, provider: String) {
        capture(
            "app_check_status",
            mapOf("token_obtained" to tokenObtained, "provider" to provider),
        )
    }
}
