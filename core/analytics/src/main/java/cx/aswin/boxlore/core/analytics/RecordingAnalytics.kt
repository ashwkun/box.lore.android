package cx.aswin.boxlore.core.analytics

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
        if (!AnalyticsGlossary.isAllowedEvent(event)) return
        _events.add(CapturedEvent(event, properties))
    }

    override fun trackFirstLaunchIfNecessary(context: Context) {
        capture("app_open", mapOf("is_first_open" to true))
    }

    override fun flush() {
        /* no-op in recording mode */
    }

    /** Phase C — no-op until PR9. */
    override fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>) = Unit

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

    override fun trackSurveyNpsEligible(completedEpisodes: Int?, triggerContext: String) {
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

    override fun trackAppCheckStatus(tokenObtained: Boolean, provider: String) {
        capture(
            "app_check_status",
            mapOf("token_obtained" to tokenObtained, "provider" to provider),
        )
    }
}
