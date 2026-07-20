package cx.aswin.boxlore.core.analytics

import android.content.Context
import com.posthog.PostHog
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import java.time.Instant

object AnalyticsHelper : Analytics {
    @Volatile private var activeSeekSource: String = "scrubber"

    @Volatile private var activePauseReason: String = "user_voluntary"

    fun setSeekSource(source: String) {
        activeSeekSource = source
    }

    fun consumeSeekSource(): String {
        val src = activeSeekSource
        activeSeekSource = "scrubber" // Reset to default
        return src
    }

    fun setPauseReason(reason: String) {
        activePauseReason = reason
    }

    fun consumePauseReason(): String {
        val reason = activePauseReason
        activePauseReason = "user_voluntary" // Reset to default
        return reason
    }

    private const val PREFS_NAME = PrefsFileMigrator.Files.ANALYTICS
    private const val KEY_FIRST_LAUNCH = "is_first_launch"

    fun deriveGenrePersona(selectedGenres: Set<String>): Map<String, String> = GenrePersonaLogic.deriveGenrePersona(selectedGenres)

    // ── 1. App Launch ──────────────────────────────────────────────

    override fun trackFirstLaunchIfNecessary(context: Context) {
        val prefs =
            PrefsFileMigrator.open(
                context,
                newName = PREFS_NAME,
                oldName = PrefsFileMigrator.LegacyFiles.ANALYTICS,
            )
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            AnalyticsEmit.personSet(
                mapOf(
                    "first_seen_at" to Instant.now().toString(),
                    "onboarding_status" to "pending",
                ),
            )
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
    }

    /**
     * App Check health/adoption. Captured once per launch on builds that ship
     * App Check. `token_obtained` = whether the SDK produced an attestation
     * token; `provider` distinguishes debug vs Play Integrity. App version is
     * attached automatically by PostHog, so adoption can be sliced by build.
     */
    override fun trackAppCheckStatus(
        tokenObtained: Boolean,
        provider: String,
    ) {
        AnalyticsEmit.event(
            "app_check_status",
            mapOf(
                "token_obtained" to tokenObtained,
                "provider" to provider,
            ),
        )
    }

    override fun trackFirstEpisodePlayed() {
        AnalyticsEmit.event(
            "first_episode_played",
            mapOf(
                "\$set_once" to
                    mapOf(
                        "first_episode_played_logged" to true,
                        "first_play_at" to Instant.now().toString(),
                    ),
            ),
        )
    }

    // ── Feedback / NPS (glossary: feedback_submitted) ───────────────
    // PostHog survey display conditions should key off feedback_submitted
    // after the PR7 dashboard rebuild (see glossary checklist).

    /** Deferred automatic trigger, fired on app open once the user hits the eligibility milestone. */
    override fun trackSurveyNpsEligible(
        completedEpisodes: Int?,
        triggerContext: String,
    ) {
        AnalyticsEmit.event(
            "feedback_submitted",
            buildMap {
                put("feedback_type", "nps_eligible")
                put("source", triggerContext)
                put("trigger_type", "automatic")
                put("trigger_context", triggerContext)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
        deliverSurveyTriggerEvent()
    }

    /** Manual trigger from a long-press or a remote console feature flag. */
    override fun trackSurveyNpsManualTrigger(source: String) {
        AnalyticsEmit.event(
            "feedback_submitted",
            mapOf(
                "feedback_type" to "nps_manual",
                "source" to source,
                "trigger_source" to source,
                "trigger_type" to "manual",
                "trigger_context" to if (source == "remote_flag") "console" else "manual",
            ),
        )
        deliverSurveyTriggerEvent()
    }

    /** Flush and refresh flags so the SDK evaluates survey display conditions immediately. */
    private fun deliverSurveyTriggerEvent() {
        try {
            PostHog.flush()
            PostHog.reloadFeatureFlags()
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsHelper", "Failed to deliver survey trigger", e)
        }
    }

    /** Tracks when a proactive engagement modal (NPS, review, etc.) is shown. */
    override fun trackEngagementPromptShown(
        promptType: String,
        source: String,
        completedEpisodes: Int?,
    ) {
        AnalyticsEmit.event(
            "feedback_submitted",
            buildMap {
                put("feedback_type", promptType)
                put("source", source)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
    }

    /** Fired when a promoter (NPS 8+) is routed to the Play Store review sheet on a later open. */
    override fun trackPromoterReviewHandoff(npsScore: Int?) {
        AnalyticsEmit.event(
            "feedback_submitted",
            buildMap {
                put("feedback_type", "promoter_review_handoff")
                npsScore?.let {
                    put("score", it)
                    put("nps_score", it)
                }
            },
        )
    }

    fun resetIdentity() {
        AnalyticsEmit.event("identity_reset", mapOf("reason" to "user_reset"))
        PostHog.reset()
    }

    fun getDistinctId(): String = PostHog.distinctId()

    /** Phase C — Auto + polish (PR9). */
    fun trackLateNightSafeguardDecision(
        decision: String,
        durationMinutes: Int? = null,
    ) = PhaseCAnalyticsTracks.trackLateNightSafeguardDecision(decision, durationMinutes)

    fun trackAndroidAutoConnected(sessionId: String? = null) = PhaseCAnalyticsTracks.trackAndroidAutoConnected(sessionId)

    fun trackAndroidAutoDisconnected(
        sessionId: String? = null,
        durationSeconds: Int? = null,
    ) = PhaseCAnalyticsTracks.trackAndroidAutoDisconnected(sessionId, durationSeconds)

    fun trackAndroidAutoBrowse(
        node: String,
        action: String? = null,
    ) = PhaseCAnalyticsTracks.trackAndroidAutoBrowse(node, action)

    fun trackLearnCaughtUp(cardsRemaining: Int? = null) = PhaseCAnalyticsTracks.trackLearnCaughtUp(cardsRemaining)

    fun trackCatalogMiss(
        lookupType: String,
        key: String? = null,
    ) = PhaseCAnalyticsTracks.trackCatalogMiss(lookupType, key)

    fun trackRssRefreshFailed(
        podcastId: String? = null,
        errorType: String? = null,
    ) = PhaseCAnalyticsTracks.trackRssRefreshFailed(podcastId, errorType)

    fun trackProgressSyncAnomaly(
        anomalyType: String,
        episodeId: String? = null,
    ) = PhaseCAnalyticsTracks.trackProgressSyncAnomaly(anomalyType, episodeId)

    data class SmartQueueRefillEvent(
        val triggeringEpisodeId: String,
        val triggeringPodcastGenre: String,
        val refilledCount: Int,
        val recommendationSources: List<String>,
        val refilledEpisodeIds: List<String>,
        val region: String? = null,
        val sourceCounts: Map<String, Int> = emptyMap(),
        val usedServerRecommendations: Boolean = false,
    )

    /** Phase C — `adaptive_ranking_status` (PR9). */
    override fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>) {
        PhaseCAnalyticsTracks.trackAdaptiveRankingStatus(statuses)
    }

    override fun flush() {
        try {
            PostHog.flush()
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsHelper", "Failed to flush PostHog", e)
        }
    }

    override fun capture(
        event: String,
        properties: Map<String, Any>,
    ) {
        AnalyticsEmit.event(event, properties)
    }
}
