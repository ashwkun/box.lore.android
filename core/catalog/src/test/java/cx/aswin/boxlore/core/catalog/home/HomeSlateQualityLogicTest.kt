package cx.aswin.boxlore.core.catalog.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeSlateQualityLogicTest {
    private fun candidate(
        episodeId: String,
        alreadyConsumedShow: Boolean = false,
    ) = HomeSlateAllocationLogic.Candidate(
        episodeId = episodeId,
        podcastId = "p-$episodeId",
        score = 1.0,
        reason = "",
        moduleHint = HomeSlateAllocationLogic.Module.TASTE,
        alreadyConsumedShow = alreadyConsumedShow,
    )

    private fun fetchMeta(
        fromCache: Boolean = false,
        cacheAgeMillis: Long? = null,
        responseLatencyMillis: Long = 0L,
        requestedModules: List<String> = emptyList(),
    ) = HomePersonalizationCoordinator.HomeCandidatesFetchMeta(
        fromCache = fromCache,
        cacheAgeMillis = cacheAgeMillis,
        responseLatencyMillis = responseLatencyMillis,
        requestedModules = requestedModules,
    )

    @Test
    fun `no duplicates and all novel yields zero duplicate rate and full novelty`() {
        val allocated = listOf(candidate("e1"), candidate("e2"), candidate("e3"))
        val telemetry =
            HomeSlateQualityLogic.compute(
                mode = HomePersonalizationMode.PERSONALIZED,
                algorithmVersion = "v3",
                isFallback = false,
                fetchMeta =
                    fetchMeta(
                        responseLatencyMillis = 150L,
                        requestedModules = listOf("taste", "because_you_like", "mission", "regional"),
                    ),
                rawCounts = HomeSlateQualityLogic.RawModuleCounts(taste = 3, becauseYouLike = 0, mission = 0, regional = 5),
                allocated = allocated,
            )
        assertEquals(0, telemetry.duplicateRatePercent)
        assertEquals(100, telemetry.noveltyRatePercent)
        assertEquals(3, telemetry.allocatedCandidateCount)
        // taste + regional non-empty, because_you_like + mission empty out of 4 requested.
        assertEquals(2, telemetry.nonEmptyModuleCount)
        assertEquals(4, telemetry.requestedModuleCount)
        assertEquals("under_300ms", telemetry.responseLatencyBucket)
        assertEquals("not_cached", telemetry.cacheAgeBucket)
    }

    @Test
    fun `cross-module de-dup drop is reflected as duplicate rate`() {
        // 10 raw candidates fed into allocation, only 4 survive cross-module de-dup.
        val allocated = listOf(candidate("e1"), candidate("e2"), candidate("e3"), candidate("e4"))
        val telemetry =
            HomeSlateQualityLogic.compute(
                mode = HomePersonalizationMode.PERSONALIZED,
                algorithmVersion = "v3",
                isFallback = false,
                fetchMeta =
                    fetchMeta(
                        fromCache = true,
                        cacheAgeMillis = 20 * 60_000L,
                        requestedModules = listOf("taste", "because_you_like", "mission"),
                    ),
                rawCounts = HomeSlateQualityLogic.RawModuleCounts(taste = 5, becauseYouLike = 3, mission = 2, regional = 0),
                allocated = allocated,
            )
        // (10 - 4) / 10 = 60%
        assertEquals(60, telemetry.duplicateRatePercent)
        assertEquals("15_59m", telemetry.cacheAgeBucket)
    }

    @Test
    fun `already-consumed shows lower the novelty rate`() {
        val allocated = listOf(candidate("e1", alreadyConsumedShow = true), candidate("e2"), candidate("e3"), candidate("e4"))
        val telemetry =
            HomeSlateQualityLogic.compute(
                mode = HomePersonalizationMode.PERSONALIZED,
                algorithmVersion = null,
                isFallback = false,
                fetchMeta = fetchMeta(responseLatencyMillis = 1_500L, requestedModules = listOf("taste")),
                rawCounts = HomeSlateQualityLogic.RawModuleCounts(taste = 4, becauseYouLike = 0, mission = 0, regional = 0),
                allocated = allocated,
            )
        // 3 of 4 allocated are novel -> 75%.
        assertEquals(75, telemetry.noveltyRatePercent)
        assertEquals("1_3s", telemetry.responseLatencyBucket)
    }

    @Test
    fun `empty response yields zero rates without dividing by zero`() {
        val telemetry =
            HomeSlateQualityLogic.compute(
                mode = HomePersonalizationMode.REGIONAL,
                algorithmVersion = null,
                isFallback = true,
                fetchMeta = fetchMeta(responseLatencyMillis = 5_000L, requestedModules = listOf("regional")),
                rawCounts = HomeSlateQualityLogic.RawModuleCounts(taste = 0, becauseYouLike = 0, mission = 0, regional = 0),
                allocated = emptyList(),
            )
        assertEquals(0, telemetry.duplicateRatePercent)
        assertEquals(0, telemetry.noveltyRatePercent)
        assertEquals(0, telemetry.nonEmptyModuleCount)
        assertEquals("3s_plus", telemetry.responseLatencyBucket)
    }

    @Test
    fun `long cache age buckets to 4h plus`() {
        val telemetry =
            HomeSlateQualityLogic.compute(
                mode = HomePersonalizationMode.PERSONALIZED,
                algorithmVersion = "v3",
                isFallback = false,
                fetchMeta =
                    fetchMeta(
                        fromCache = true,
                        cacheAgeMillis = 5 * 60 * 60_000L,
                        responseLatencyMillis = 10L,
                        requestedModules = listOf("regional"),
                    ),
                rawCounts = HomeSlateQualityLogic.RawModuleCounts(taste = 0, becauseYouLike = 0, mission = 0, regional = 1),
                allocated = emptyList(),
            )
        assertEquals("4h_plus", telemetry.cacheAgeBucket)
    }
}
