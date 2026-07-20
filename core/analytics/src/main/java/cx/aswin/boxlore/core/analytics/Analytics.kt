package cx.aswin.boxlore.core.analytics

import android.content.Context
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry

/**
 * Façade for analytics event capture. New call-sites should depend on this interface
 * rather than [AnalyticsHelper] directly, enabling substitution with [RecordingAnalytics]
 * in tests and integration points.
 *
 * The current [AnalyticsHelper] object implements this interface so existing call-sites
 * continue to work without changes.
 */
interface Analytics {
    /** Capture a generic event with an arbitrary property map. */
    fun capture(
        event: String,
        properties: Map<String, Any> = emptyMap(),
    )

    /** First-launch detection and user-property initialisation. */
    fun trackFirstLaunchIfNecessary(context: Context)

    /** Flush buffered events to the PostHog server. */
    fun flush()

    fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>)

    fun trackEngagementPromptShown(
        promptType: String,
        source: String,
        completedEpisodes: Int? = null,
    )

    fun trackSurveyNpsEligible(
        completedEpisodes: Int?,
        triggerContext: String,
    )

    fun trackSurveyNpsManualTrigger(source: String)

    fun trackPromoterReviewHandoff(npsScore: Int?)

    fun trackFirstEpisodePlayed()

    fun trackAppCheckStatus(
        tokenObtained: Boolean,
        provider: String,
    )
}
