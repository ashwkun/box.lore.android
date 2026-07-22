package cx.aswin.boxlore.core.ranking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RankingExposureHealthLogicTest {
    @Test
    fun `no exposures yet defaults to a healthy resolution rate`() {
        val telemetry =
            RankingExposureHealthLogic.compute(
                surface = RankingSurface.HOME,
                raw =
                    RankingExposureHealthLogic.RawCounts(
                        totalExposures = 0,
                        resolvedExposures = 0,
                        oldestPendingShownAt = null,
                        totalOutcomes = 0,
                        unmatchedOutcomes = 0,
                    ),
                now = 10_000L,
            )
        assertEquals(100, telemetry.resolutionRatePercent)
        assertEquals(0, telemetry.pendingExposureCount)
        assertEquals("none_pending", telemetry.oldestPendingAgeBucket)
        assertEquals(0, telemetry.unmatchedOutcomeRatePercent)
    }

    @Test
    fun `partial resolution reports pending count and rate`() {
        val telemetry =
            RankingExposureHealthLogic.compute(
                surface = RankingSurface.HOME,
                raw =
                    RankingExposureHealthLogic.RawCounts(
                        totalExposures = 10,
                        resolvedExposures = 7,
                        oldestPendingShownAt = null,
                        totalOutcomes = 5,
                        unmatchedOutcomes = 1,
                    ),
                now = 10_000L,
            )
        assertEquals(70, telemetry.resolutionRatePercent)
        assertEquals(3, telemetry.pendingExposureCount)
        assertEquals(20, telemetry.unmatchedOutcomeRatePercent)
    }

    @Test
    fun `oldest pending exposure buckets by age`() {
        val now = 100_000_000L
        val fiveHoursAgo = now - 5 * 3_600_000L
        val telemetry =
            RankingExposureHealthLogic.compute(
                surface = RankingSurface.HOME,
                raw =
                    RankingExposureHealthLogic.RawCounts(
                        totalExposures = 4,
                        resolvedExposures = 2,
                        oldestPendingShownAt = fiveHoursAgo,
                        totalOutcomes = 0,
                        unmatchedOutcomes = 0,
                    ),
                now = now,
            )
        assertEquals("1_5h", telemetry.oldestPendingAgeBucket)
    }

    @Test
    fun `three day old pending exposure buckets to 3d plus`() {
        val now = 100_000_000_000L
        val threeDaysAgo = now - 4 * 24 * 3_600_000L
        val telemetry =
            RankingExposureHealthLogic.compute(
                surface = RankingSurface.HOME,
                raw =
                    RankingExposureHealthLogic.RawCounts(
                        totalExposures = 1,
                        resolvedExposures = 0,
                        oldestPendingShownAt = threeDaysAgo,
                        totalOutcomes = 0,
                        unmatchedOutcomes = 0,
                    ),
                now = now,
            )
        assertEquals("3d_plus", telemetry.oldestPendingAgeBucket)
    }

    @Test
    fun `surface name is carried through verbatim`() {
        val telemetry =
            RankingExposureHealthLogic.compute(
                surface = RankingSurface.EXPLORE,
                raw =
                    RankingExposureHealthLogic.RawCounts(
                        totalExposures = 0,
                        resolvedExposures = 0,
                        oldestPendingShownAt = null,
                        totalOutcomes = 0,
                        unmatchedOutcomes = 0,
                    ),
                now = 0L,
            )
        assertEquals("EXPLORE", telemetry.surface)
    }
}
