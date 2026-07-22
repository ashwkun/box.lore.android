package cx.aswin.boxlore.core.catalog.home

import cx.aswin.boxlore.core.model.HomeSlateQualityTelemetry

/**
 * Pure, privacy-safe quality aggregation for one Home candidates-v1 slate load. Feeds
 * [HomeSlateQualityTelemetry] (analytics-safe: counts/rates/buckets only, no episode or
 * podcast identifiers) from the raw per-module fetch sizes and the final cross-module
 * de-duplicated allocation produced by [HomeSlateAllocationLogic].
 *
 * Kept out of [HomePersonalizationCoordinator] so that file stays focused on orchestration;
 * this object is pure JVM logic and unit tested directly (see `HomeSlateQualityLogicTest`).
 */
object HomeSlateQualityLogic {
    /** Raw (pre-allocation, per-module-deduped) candidate counts fetched from the response. */
    data class RawModuleCounts(
        val taste: Int,
        val becauseYouLike: Int,
        val mission: Int,
        val regional: Int,
    ) {
        /** Only taste/because-you-like/mission compete in [HomeSlateAllocationLogic.allocate]; regional does not. */
        val allocatablePool: Int get() = taste + becauseYouLike + mission
    }

    fun compute(
        mode: HomePersonalizationMode,
        algorithmVersion: String?,
        isFallback: Boolean,
        fetchMeta: HomePersonalizationCoordinator.HomeCandidatesFetchMeta,
        rawCounts: RawModuleCounts,
        allocated: List<HomeSlateAllocationLogic.Candidate>,
    ): HomeSlateQualityTelemetry {
        val requestedModules = fetchMeta.requestedModules
        val nonEmptyModuleCount =
            requestedModules.count { module ->
                when (module) {
                    "taste" -> rawCounts.taste > 0
                    "because_you_like" -> rawCounts.becauseYouLike > 0
                    "mission" -> rawCounts.mission > 0
                    "regional" -> rawCounts.regional > 0
                    else -> false
                }
            }
        val duplicateRatePercent =
            ratePercent(
                numerator = (rawCounts.allocatablePool - allocated.size).coerceAtLeast(0),
                denominator = rawCounts.allocatablePool,
            )
        val noveltyRatePercent =
            ratePercent(
                numerator = allocated.count { !it.alreadyConsumedShow },
                denominator = allocated.size,
            )
        return HomeSlateQualityTelemetry(
            mode = mode.name,
            algorithmVersion = algorithmVersion,
            fromCache = fetchMeta.fromCache,
            isFallback = isFallback,
            requestedModuleCount = requestedModules.size,
            nonEmptyModuleCount = nonEmptyModuleCount,
            allocatedCandidateCount = allocated.size,
            duplicateRatePercent = duplicateRatePercent,
            noveltyRatePercent = noveltyRatePercent,
            cacheAgeBucket = cacheAgeBucket(fetchMeta.cacheAgeMillis),
            responseLatencyBucket = latencyBucket(fetchMeta.responseLatencyMillis),
        )
    }

    private fun ratePercent(
        numerator: Int,
        denominator: Int,
    ): Int {
        if (denominator <= 0) return 0
        return ((numerator * 100) / denominator).coerceIn(0, 100)
    }

    private fun cacheAgeBucket(cacheAgeMillis: Long?): String {
        if (cacheAgeMillis == null) return "not_cached"
        val minutes = cacheAgeMillis / 60_000L
        return when {
            minutes < 15 -> "under_15m"
            minutes < 60 -> "15_59m"
            minutes < 240 -> "1_4h"
            else -> "4h_plus"
        }
    }

    private fun latencyBucket(latencyMillis: Long): String =
        when {
            latencyMillis < 300 -> "under_300ms"
            latencyMillis < 1_000 -> "300_999ms"
            latencyMillis < 3_000 -> "1_3s"
            else -> "3s_plus"
        }
}
