package cx.aswin.boxcast.core.data

/**
 * Shared thresholds for NPS scoring and proactive engagement cooldowns.
 */
object EngagementPromptConstants {
    /** Minimum NPS score (inclusive) that qualifies for Play Store promoter handoff. */
    const val PROMOTER_SCORE_THRESHOLD = 8

    /** Minimum days between unrelated proactive prompts (NPS, milestone review). */
    const val ENGAGEMENT_COOLDOWN_DAYS = 14

    /** Maximum NPS score (inclusive) treated as a detractor for milestone review gating. */
    const val DETRACTOR_SCORE_MAX = 7
}
