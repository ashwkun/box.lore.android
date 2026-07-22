package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.model.RankingExposureHealthTelemetry

/**
 * Pure aggregation from raw exact-exposure-token counters into a privacy-safe
 * [RankingExposureHealthTelemetry] (rates/buckets only — never episode/podcast identifiers).
 *
 * Surfaces how well [RankingFeedbackRepository.resolveTargetExposure] is keeping up: what
 * fraction of shown exposures ever resolve into a model update, how old the longest-waiting
 * pending exposure is, and what fraction of outcomes had no exposure token to attach to
 * (episode-latest fallback matching, see [AdaptiveRankingRepository.resolveLatestExposure]).
 *
 * Kept separate from [AdaptiveRankingRepository] so the aggregation math is unit testable
 * without a Room database (see `RankingExposureHealthLogicTest`).
 */
object RankingExposureHealthLogic {
    data class RawCounts(
        val totalExposures: Int,
        val resolvedExposures: Int,
        val oldestPendingShownAt: Long?,
        val totalOutcomes: Int,
        val unmatchedOutcomes: Int,
    )

    fun compute(
        surface: RankingSurface,
        raw: RawCounts,
        now: Long,
    ): RankingExposureHealthTelemetry {
        val resolutionRatePercent =
            ratePercent(numerator = raw.resolvedExposures, denominator = raw.totalExposures, whenEmpty = 100)
        val pendingCount = (raw.totalExposures - raw.resolvedExposures).coerceAtLeast(0)
        val unmatchedRatePercent =
            ratePercent(numerator = raw.unmatchedOutcomes, denominator = raw.totalOutcomes, whenEmpty = 0)
        return RankingExposureHealthTelemetry(
            surface = surface.name,
            totalExposures = raw.totalExposures,
            resolutionRatePercent = resolutionRatePercent,
            pendingExposureCount = pendingCount,
            oldestPendingAgeBucket = ageBucket(raw.oldestPendingShownAt, now),
            unmatchedOutcomeRatePercent = unmatchedRatePercent,
        )
    }

    private fun ratePercent(
        numerator: Int,
        denominator: Int,
        whenEmpty: Int,
    ): Int {
        if (denominator <= 0) return whenEmpty
        return ((numerator * 100) / denominator).coerceIn(0, 100)
    }

    private fun ageBucket(
        oldestPendingShownAt: Long?,
        now: Long,
    ): String {
        if (oldestPendingShownAt == null) return "none_pending"
        val ageMillis = (now - oldestPendingShownAt).coerceAtLeast(0)
        val hours = ageMillis / 3_600_000L
        return when {
            hours < 1 -> "under_1h"
            hours < 6 -> "1_5h"
            hours < 24 -> "6_23h"
            hours < 72 -> "1_2d"
            else -> "3d_plus"
        }
    }
}
