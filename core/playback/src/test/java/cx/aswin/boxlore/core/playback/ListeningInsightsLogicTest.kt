package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningTimeBucket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class ListeningSessionRecordLogicTest {
    @Test
    fun rejectsNoiseSessionsUnderFiveSeconds() {
        assertFalse(ListeningSessionRecordLogic.shouldPersist(4_999))
        assertTrue(ListeningSessionRecordLogic.shouldPersist(5_000))
        assertNull(
            ListeningSessionRecordLogic.buildSession(
                ListeningSessionRecordLogic.BuildSessionInput(
                    sessionId = "s",
                    episodeId = "e",
                    podcastId = "p",
                    startedAt = 1_000,
                    endedAt = 2_000,
                    consumedMs = 100,
                    completed = false,
                ),
            ),
        )
    }

    @Test
    fun mapsTimeBuckets() {
        assertEquals(0, ListeningSessionRecordLogic.timeBucketForHour(7))
        assertEquals(1, ListeningSessionRecordLogic.timeBucketForHour(12))
        assertEquals(2, ListeningSessionRecordLogic.timeBucketForHour(19))
        assertEquals(3, ListeningSessionRecordLogic.timeBucketForHour(23))
        assertEquals(3, ListeningSessionRecordLogic.timeBucketForHour(2))
    }

    @Test
    fun retentionCutoffIsLocalDayBoundary() {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 7, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val cutoff = ListeningSessionRecordLogic.retentionCutoffEndedAtExclusive(now, zone)
        val expected =
            LocalDate.of(2026, 7, 19)
                .minusDays(180)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        assertEquals(expected, cutoff)
    }
}

class ListeningInsightsLogicTest {
    private fun session(
        day: Long,
        consumed: Long,
        podcastId: String = "pod-a",
        bucket: Int = 0,
        completed: Boolean = false,
    ) = ListeningSessionEntity(
        sessionId = "s-$day-$consumed",
        episodeId = "ep-$day",
        podcastId = podcastId,
        startedAt = day * 86_400_000,
        endedAt = day * 86_400_000 + consumed,
        consumedMs = consumed,
        completed = completed,
        localDay = day,
        timeBucket = bucket,
    )

    @Test
    fun mergesRawAndRollupWithoutDoubleCount() {
        val today = LocalDate.ofEpochDay(100)
        val summary =
            ListeningInsightsLogic.summarize(
                ListeningInsightsLogic.SummarizeInput(
                    period = ListeningPeriod.DAYS_30,
                    sessions = listOf(session(day = 100, consumed = 400, bucket = 1)),
                    rollups =
                        listOf(
                            ListeningRollupEntity(
                                localDay = 90,
                                episodeId = "ep-old",
                                podcastId = "pod-a",
                                consumedMs = 600,
                                sessionCount = 2,
                                completionCount = 1,
                                lastListenedAt = 1,
                                morningMs = 600,
                            ),
                        ),
                    historyRows = emptyList(),
                    historyCompleted = 3,
                    historyInProgress = 1,
                    historyLiked = 2,
                    podcastMetaById =
                        mapOf("pod-a" to ListeningInsightsLogic.PodcastMeta("Show A", null)),
                    today = today,
                    trackingSinceEpochMs = 1L,
                ),
            )
        assertEquals(1_000L, summary.totalConsumedMs)
        assertEquals(3, summary.sessionCount)
        assertEquals(ListeningTimeBucket.MORNING, summary.peakBucket)
        assertEquals("Show A", summary.topShow?.podcastName)
        assertEquals(3, summary.completedCount)
        assertTrue(summary.hasEnoughData)
    }

    @Test
    fun fallsBackToHistoryEstimateWhenNoSessions() {
        val today = LocalDate.ofEpochDay(100)
        val summary =
            ListeningInsightsLogic.summarize(
                ListeningInsightsLogic.SummarizeInput(
                    period = ListeningPeriod.ALL,
                    sessions = emptyList(),
                    rollups = emptyList(),
                    historyRows =
                        listOf(
                            ListeningInsightsLogic.HistoryActivityRow(
                                podcastId = "pod-a",
                                podcastName = "Show A",
                                podcastImageUrl = null,
                                progressMs = 3_600_000,
                                durationMs = 3_600_000,
                                isCompletedFlag = true,
                                lastPlayedAt = today.toEpochDay() * 86_400_000,
                            ),
                        ),
                    historyCompleted = 1,
                    historyInProgress = 0,
                    historyLiked = 0,
                    podcastMetaById = emptyMap(),
                    today = today,
                    trackingSinceEpochMs = null,
                ),
            )
        assertFalse(summary.hasEnoughData)
        assertEquals(3_600_000L, summary.estimatedLibraryMs)
        assertEquals(0L, summary.totalConsumedMs)
        assertEquals("Show A", summary.topShow?.podcastName)
    }

    @Test
    fun streakRequiresTodayOrYesterdayAnchor() {
        val today = 50L
        assertEquals(0, ListeningInsightsLogic.computeStreak(setOf(40, 41), today))
        assertEquals(2, ListeningInsightsLogic.computeStreak(setOf(49, 50), today))
        assertEquals(3, ListeningInsightsLogic.computeStreak(setOf(47, 48, 49), today))
    }

    @Test
    fun previousPeriodComparisonUsesEqualLengthWindow() {
        val today = LocalDate.of(2026, 7, 19)
        val bounds = ListeningInsightsLogic.previousPeriodBounds(ListeningPeriod.DAYS_7, today)!!
        assertEquals(today.minusDays(13).toEpochDay(), bounds.first)
        assertEquals(today.minusDays(7).toEpochDay(), bounds.second)
        assertNull(ListeningInsightsLogic.previousPeriodBounds(ListeningPeriod.ALL, today))
    }
}
