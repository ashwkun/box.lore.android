package cx.aswin.boxlore.core.model

/**
 * Privacy-safe, aggregate-only quality snapshot for one Home candidates-v1 slate load.
 * Never carries episode/podcast titles or ids — only counts, rates, and buckets so it is
 * safe to emit as an analytics event (see `home_slate_quality_snapshot` in
 * docs/ANALYTICS_EVENT_GLOSSARY.md).
 */
data class HomeSlateQualityTelemetry(
    val mode: String,
    val algorithmVersion: String?,
    val fromCache: Boolean,
    val isFallback: Boolean,
    val requestedModuleCount: Int,
    val nonEmptyModuleCount: Int,
    val allocatedCandidateCount: Int,
    val duplicateRatePercent: Int,
    val noveltyRatePercent: Int,
    val cacheAgeBucket: String,
    val responseLatencyBucket: String,
)
