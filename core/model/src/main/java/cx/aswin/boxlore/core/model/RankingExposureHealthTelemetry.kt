package cx.aswin.boxlore.core.model

/**
 * Privacy-safe, aggregate-only view of how well exact-exposure-token learning attribution
 * is keeping up for one [surface]. Reports rates and buckets only — never episode/podcast
 * identifiers — so it is safe to emit as an analytics event (see
 * `home_learning_attribution_health` in docs/ANALYTICS_EVENT_GLOSSARY.md).
 */
data class RankingExposureHealthTelemetry(
    val surface: String,
    val totalExposures: Int,
    val resolutionRatePercent: Int,
    val pendingExposureCount: Int,
    val oldestPendingAgeBucket: String,
    val unmatchedOutcomeRatePercent: Int,
)
