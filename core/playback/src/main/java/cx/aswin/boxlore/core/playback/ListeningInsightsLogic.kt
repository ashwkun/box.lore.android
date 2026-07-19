package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningInsightsDao
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningTimeBucket
import cx.aswin.boxlore.core.model.ListeningTopShow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ListeningSessionRecordLogic {
    const val MIN_CONSUMED_MS = 5_000L
    const val RETENTION_DAYS = 180L

    data class BuildSessionInput(
        val sessionId: String,
        val episodeId: String,
        val podcastId: String,
        val startedAt: Long,
        val endedAt: Long,
        val consumedMs: Long,
        val completed: Boolean,
        val zoneId: ZoneId = ZoneId.systemDefault(),
    )

    fun timeBucketForHour(hour: Int): Int =
        when (hour) {
            in 5..10 -> 0
            in 11..16 -> 1
            in 17..21 -> 2
            else -> 3
        }

    fun localDay(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().toEpochDay()

    fun shouldPersist(consumedMs: Long): Boolean = consumedMs >= MIN_CONSUMED_MS

    fun buildSession(input: BuildSessionInput): ListeningSessionEntity? {
        if (!shouldPersist(input.consumedMs)) return null
        val hour = Instant.ofEpochMilli(input.startedAt).atZone(input.zoneId).hour
        return ListeningSessionEntity(
            sessionId = input.sessionId,
            episodeId = input.episodeId,
            podcastId = input.podcastId,
            startedAt = input.startedAt,
            endedAt = input.endedAt,
            consumedMs = input.consumedMs,
            completed = input.completed,
            localDay = localDay(input.startedAt, input.zoneId),
            timeBucket = timeBucketForHour(hour),
        )
    }

    fun retentionCutoffEndedAtExclusive(
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val cutoffDay = today.minusDays(RETENTION_DAYS)
        return cutoffDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    suspend fun persistSessionAndRollUp(
        dao: ListeningInsightsDao,
        session: ListeningSessionEntity,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        dao.upsertSession(session)
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        dao.rollUpEligibleSessions(
            cutoffEndedAtExclusive = retentionCutoffEndedAtExclusive(nowMs, zoneId),
            todayLocalDay = today.toEpochDay(),
        )
    }
}

object ListeningInsightsLogic {
    data class PodcastMeta(
        val name: String,
        val imageUrl: String?,
    )

    data class HistoryActivityRow(
        val podcastId: String,
        val podcastName: String,
        val podcastImageUrl: String?,
        val progressMs: Long,
        val durationMs: Long,
        val isCompletedFlag: Boolean,
        val lastPlayedAt: Long,
    )

    data class SummarizeInput(
        val period: ListeningPeriod,
        val sessions: List<ListeningSessionEntity>,
        val rollups: List<ListeningRollupEntity>,
        val historyRows: List<HistoryActivityRow>,
        val historyCompleted: Int,
        val historyInProgress: Int,
        val historyLiked: Int,
        val podcastMetaById: Map<String, PodcastMeta>,
        val today: LocalDate = LocalDate.now(),
        val trackingSinceEpochMs: Long?,
        val zoneId: ZoneId = ZoneId.systemDefault(),
    )

    fun periodStartDay(
        period: ListeningPeriod,
        today: LocalDate,
    ): Long? =
        when (period) {
            ListeningPeriod.DAYS_7 -> today.minusDays(6).toEpochDay()
            ListeningPeriod.DAYS_30 -> today.minusDays(29).toEpochDay()
            ListeningPeriod.DAYS_180 -> today.minusDays(179).toEpochDay()
            ListeningPeriod.ALL -> null
        }

    fun previousPeriodBounds(
        period: ListeningPeriod,
        today: LocalDate,
    ): Pair<Long, Long>? {
        val start = periodStartDay(period, today) ?: return null
        val lengthDays =
            when (period) {
                ListeningPeriod.DAYS_7 -> 7L
                ListeningPeriod.DAYS_30 -> 30L
                ListeningPeriod.DAYS_180 -> 180L
                ListeningPeriod.ALL -> return null
            }
        val prevEnd = start - 1
        val prevStart = prevEnd - (lengthDays - 1)
        return prevStart to prevEnd
    }

    fun estimatedMsFromHistoryRow(
        isCompletedFlag: Boolean,
        progressMs: Long,
        durationMs: Long,
    ): Long {
        val completed =
            cx.aswin.boxlore.core.model.ListeningCompletionLogic.isCompleted(
                isCompletedFlag,
                progressMs,
                durationMs,
            )
        return if (completed && durationMs > 0) durationMs else progressMs.coerceAtLeast(0L)
    }

    fun summarize(input: SummarizeInput): ListeningInsightSummary {
        val startDay = periodStartDay(input.period, input.today)
        val filteredSessions =
            input.sessions.filter { startDay == null || it.localDay >= startDay }
        val filteredRollups =
            input.rollups.filter { startDay == null || it.localDay >= startDay }
        val filteredHistory =
            input.historyRows.filter { row ->
                val day =
                    Instant.ofEpochMilli(row.lastPlayedAt).atZone(input.zoneId).toLocalDate().toEpochDay()
                startDay == null || day >= startDay
            }

        val totalConsumed =
            filteredSessions.sumOf { it.consumedMs } + filteredRollups.sumOf { it.consumedMs }
        val sessionCount =
            filteredSessions.size + filteredRollups.sumOf { it.sessionCount }

        val estimatedLibraryMs =
            filteredHistory.sumOf {
                estimatedMsFromHistoryRow(it.isCompletedFlag, it.progressMs, it.durationMs)
            }

        val morning =
            filteredSessions.filter { it.timeBucket == 0 }.sumOf { it.consumedMs } +
                filteredRollups.sumOf { it.morningMs }
        val afternoon =
            filteredSessions.filter { it.timeBucket == 1 }.sumOf { it.consumedMs } +
                filteredRollups.sumOf { it.afternoonMs }
        val evening =
            filteredSessions.filter { it.timeBucket == 2 }.sumOf { it.consumedMs } +
                filteredRollups.sumOf { it.eveningMs }
        val night =
            filteredSessions.filter { it.timeBucket == 3 }.sumOf { it.consumedMs } +
                filteredRollups.sumOf { it.nightMs }

        val peakBucket =
            listOf(
                ListeningTimeBucket.MORNING to morning,
                ListeningTimeBucket.AFTERNOON to afternoon,
                ListeningTimeBucket.EVENING to evening,
                ListeningTimeBucket.NIGHT to night,
            ).maxByOrNull { it.second }
                ?.takeIf { it.second > 0 }
                ?.first

        val previousConsumed =
            previousPeriodBounds(input.period, input.today)?.let { (prevStart, prevEnd) ->
                input.sessions.filter { it.localDay in prevStart..prevEnd }.sumOf { it.consumedMs } +
                    input.rollups.filter { it.localDay in prevStart..prevEnd }.sumOf { it.consumedMs }
            } ?: 0L

        val sessionActiveDays =
            (
                filteredSessions.map { it.localDay } +
                    filteredRollups.map { it.localDay }
            ).toSet()
        val historyActiveDays =
            filteredHistory
                .map {
                    Instant.ofEpochMilli(it.lastPlayedAt).atZone(input.zoneId).toLocalDate().toEpochDay()
                }.toSet()
        val activeDays = if (sessionActiveDays.isNotEmpty()) sessionActiveDays else historyActiveDays
        val streak = computeStreak(activeDays, input.today.toEpochDay())

        val longestRaw = filteredSessions.maxOfOrNull { it.consumedMs } ?: 0L
        val average =
            if (sessionCount > 0) totalConsumed / sessionCount else 0L

        val preciseTop = topShowByConsumed(filteredSessions, filteredRollups, input.podcastMetaById)
        val topShow = preciseTop ?: topShowByHistoryPlays(filteredHistory)

        val hasEnoughData = sessionCount > 0 || totalConsumed > 0L

        val dailyActivity =
            buildDailyActivity(
                hasEnoughData = hasEnoughData,
                filteredSessions = filteredSessions,
                filteredRollups = filteredRollups,
                filteredHistory = filteredHistory,
                startDay = startDay,
                today = input.today,
                zoneId = input.zoneId,
            )

        return ListeningInsightSummary(
            period = input.period,
            trackingSinceEpochMs = input.trackingSinceEpochMs,
            totalConsumedMs = totalConsumed,
            previousPeriodConsumedMs = previousConsumed,
            estimatedLibraryMs = estimatedLibraryMs,
            completedCount = input.historyCompleted,
            inProgressCount = input.historyInProgress,
            likedCount = input.historyLiked,
            sessionCount = sessionCount,
            averageSessionMs = average,
            longestSessionMs = longestRaw,
            streakDays = streak,
            activeDaysInPeriod = activeDays.size,
            peakBucket = peakBucket,
            morningMs = morning,
            afternoonMs = afternoon,
            eveningMs = evening,
            nightMs = night,
            topShow = topShow,
            dailyActivity = dailyActivity,
            hasEnoughData = hasEnoughData,
        )
    }

    fun buildDailyActivity(
        hasEnoughData: Boolean,
        filteredSessions: List<ListeningSessionEntity>,
        filteredRollups: List<ListeningRollupEntity>,
        filteredHistory: List<HistoryActivityRow>,
        startDay: Long?,
        today: LocalDate,
        zoneId: ZoneId,
    ): List<cx.aswin.boxlore.core.model.ListeningDayActivity> {
        val totals = mutableMapOf<Long, Long>()
        if (hasEnoughData) {
            filteredSessions.forEach { row ->
                totals[row.localDay] = (totals[row.localDay] ?: 0L) + row.consumedMs
            }
            filteredRollups.forEach { row ->
                totals[row.localDay] = (totals[row.localDay] ?: 0L) + row.consumedMs
            }
        } else {
            filteredHistory.forEach { row ->
                val day =
                    Instant.ofEpochMilli(row.lastPlayedAt).atZone(zoneId).toLocalDate().toEpochDay()
                totals[day] =
                    (totals[day] ?: 0L) +
                        estimatedMsFromHistoryRow(row.isCompletedFlag, row.progressMs, row.durationMs)
            }
        }
        val fromDay = startDay ?: (totals.keys.minOrNull() ?: today.toEpochDay())
        val toDay = today.toEpochDay()
        if (fromDay > toDay) return emptyList()
        return (fromDay..toDay).map { day ->
            cx.aswin.boxlore.core.model.ListeningDayActivity(
                localDay = day,
                consumedMs = totals[day] ?: 0L,
            )
        }
    }

    fun computeStreak(
        activeDays: Set<Long>,
        todayEpochDay: Long,
    ): Int {
        if (activeDays.isEmpty()) return 0
        val yesterday = todayEpochDay - 1
        val start =
            when {
                todayEpochDay in activeDays -> todayEpochDay
                yesterday in activeDays -> yesterday
                else -> return 0
            }
        var streak = 1
        var cursor = start - 1
        while (cursor in activeDays) {
            streak++
            cursor--
        }
        return streak
    }

    private fun topShowByConsumed(
        sessions: List<ListeningSessionEntity>,
        rollups: List<ListeningRollupEntity>,
        podcastMetaById: Map<String, PodcastMeta>,
    ): ListeningTopShow? {
        data class Acc(var consumed: Long = 0, var sessions: Int = 0)

        val byPodcast = mutableMapOf<String, Acc>()
        sessions.forEach {
            val acc = byPodcast.getOrPut(it.podcastId) { Acc() }
            acc.consumed += it.consumedMs
            acc.sessions += 1
        }
        rollups.forEach {
            val acc = byPodcast.getOrPut(it.podcastId) { Acc() }
            acc.consumed += it.consumedMs
            acc.sessions += it.sessionCount
        }
        val best = byPodcast.maxWithOrNull(compareBy({ it.value.consumed }, { it.key })) ?: return null
        if (best.value.consumed <= 0L) return null
        val meta = podcastMetaById[best.key]
        return ListeningTopShow(
            podcastId = best.key,
            podcastName = meta?.name ?: best.key,
            podcastImageUrl = meta?.imageUrl,
            consumedMs = best.value.consumed,
            sessionCount = best.value.sessions,
        )
    }

    private fun topShowByHistoryPlays(rows: List<HistoryActivityRow>): ListeningTopShow? {
        data class Acc(var plays: Int = 0, var name: String = "", var image: String? = null)

        val byPodcast = mutableMapOf<String, Acc>()
        rows.forEach { row ->
            val acc = byPodcast.getOrPut(row.podcastId) { Acc() }
            acc.plays += 1
            acc.name = row.podcastName
            acc.image = row.podcastImageUrl ?: acc.image
        }
        val best = byPodcast.maxWithOrNull(compareBy({ it.value.plays }, { it.key })) ?: return null
        if (best.value.plays <= 0) return null
        return ListeningTopShow(
            podcastId = best.key,
            podcastName = best.value.name.ifBlank { best.key },
            podcastImageUrl = best.value.image,
            consumedMs = 0L,
            sessionCount = best.value.plays,
        )
    }
}
