package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.model.HomeSlateQualityTelemetry
import cx.aswin.boxlore.core.model.RankingExposureHealthTelemetry

/**
 * Phase D — Home candidates-v1 quality-observability glossary events. Both events are
 * aggregate/privacy-safe by construction: their [HomeSlateQualityTelemetry] /
 * [RankingExposureHealthTelemetry] inputs carry only counts, rates, and buckets — never
 * episode/podcast identifiers or titles. Purely observational: they feed dashboards, not
 * hard kill switches (see [RankingRuntimeControls] for the actual rollout controls).
 */
internal object HomeQualityAnalyticsTracks {
    fun trackHomeSlateQualitySnapshot(telemetry: HomeSlateQualityTelemetry) {
        AnalyticsEmit.event(
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

    fun trackHomeLearningAttributionHealth(telemetry: RankingExposureHealthTelemetry) {
        AnalyticsEmit.event(
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
}
