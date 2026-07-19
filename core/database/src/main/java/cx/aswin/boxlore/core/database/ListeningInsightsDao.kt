package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningInsightsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ListeningSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<ListeningSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRollup(rollup: ListeningRollupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRollups(rollups: List<ListeningRollupEntity>)

    @Query("SELECT * FROM listening_sessions WHERE endedAt >= :sinceMs ORDER BY endedAt DESC")
    fun observeSessionsSince(sinceMs: Long): Flow<List<ListeningSessionEntity>>

    @Query("SELECT * FROM listening_sessions ORDER BY endedAt DESC")
    fun observeAllSessions(): Flow<List<ListeningSessionEntity>>

    @Query("SELECT * FROM listening_sessions WHERE endedAt >= :sinceMs ORDER BY endedAt DESC")
    suspend fun getSessionsSince(sinceMs: Long): List<ListeningSessionEntity>

    @Query("SELECT * FROM listening_sessions ORDER BY endedAt DESC")
    suspend fun getAllSessions(): List<ListeningSessionEntity>

    @Query(
        """
        SELECT * FROM listening_sessions
        WHERE endedAt < :cutoffEndedAtExclusive
          AND localDay < :todayLocalDay
        ORDER BY localDay ASC, episodeId ASC
        """,
    )
    suspend fun getSessionsEligibleForRollup(
        cutoffEndedAtExclusive: Long,
        todayLocalDay: Long,
    ): List<ListeningSessionEntity>

    @Query("SELECT * FROM listening_rollups WHERE localDay >= :sinceDay ORDER BY localDay DESC")
    fun observeRollupsSince(sinceDay: Long): Flow<List<ListeningRollupEntity>>

    @Query("SELECT * FROM listening_rollups ORDER BY localDay DESC")
    fun observeAllRollups(): Flow<List<ListeningRollupEntity>>

    @Query("SELECT * FROM listening_rollups WHERE localDay >= :sinceDay ORDER BY localDay DESC")
    suspend fun getRollupsSince(sinceDay: Long): List<ListeningRollupEntity>

    @Query("SELECT * FROM listening_rollups ORDER BY localDay DESC")
    suspend fun getAllRollups(): List<ListeningRollupEntity>

    @Query("SELECT * FROM listening_rollups WHERE localDay = :localDay AND episodeId = :episodeId LIMIT 1")
    suspend fun getRollup(localDay: Long, episodeId: String): ListeningRollupEntity?

    @Query("SELECT * FROM listening_sessions WHERE episodeId = :episodeId")
    suspend fun getSessionsForEpisode(episodeId: String): List<ListeningSessionEntity>

    @Query("SELECT * FROM listening_rollups WHERE episodeId = :episodeId")
    suspend fun getRollupsForEpisode(episodeId: String): List<ListeningRollupEntity>

    @Query("SELECT MIN(lastListenedAt) FROM listening_rollups")
    suspend fun getEarliestRollupListenedAt(): Long?

    @Query(
        """
        DELETE FROM listening_sessions
        WHERE endedAt < :cutoffEndedAtExclusive
          AND localDay < :todayLocalDay
        """,
    )
    suspend fun deleteSessionsEligibleForRollup(
        cutoffEndedAtExclusive: Long,
        todayLocalDay: Long,
    )

    @Query("DELETE FROM listening_sessions WHERE episodeId = :episodeId")
    suspend fun deleteSessionsForEpisode(episodeId: String)

    @Query("DELETE FROM listening_rollups WHERE episodeId = :episodeId")
    suspend fun deleteRollupsForEpisode(episodeId: String)

    @Query("DELETE FROM listening_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM listening_rollups")
    suspend fun deleteAllRollups()

    @Query("SELECT MIN(endedAt) FROM listening_sessions")
    suspend fun getEarliestSessionEndedAt(): Long?

    @Transaction
    suspend fun deleteEpisodeAnalytics(episodeId: String) {
        deleteSessionsForEpisode(episodeId)
        deleteRollupsForEpisode(episodeId)
    }

    @Transaction
    suspend fun clearAllAnalytics() {
        deleteAllSessions()
        deleteAllRollups()
    }

    /**
     * Merge eligible raw sessions into daily episode rollups and delete those raw rows.
     * Never touches sessions from [todayLocalDay] or active in-progress rows (only closed rows
     * are persisted as sessions).
     */
    @Transaction
    suspend fun rollUpEligibleSessions(
        cutoffEndedAtExclusive: Long,
        todayLocalDay: Long,
    ): Int {
        val eligible = getSessionsEligibleForRollup(cutoffEndedAtExclusive, todayLocalDay)
        if (eligible.isEmpty()) return 0

        val grouped = eligible.groupBy { it.localDay to it.episodeId }
        for ((key, sessions) in grouped) {
            val (localDay, episodeId) = key
            val existing = getRollup(localDay, episodeId)
            val podcastId = sessions.first().podcastId
            var consumedMs = existing?.consumedMs ?: 0L
            var sessionCount = existing?.sessionCount ?: 0
            var completionCount = existing?.completionCount ?: 0
            var lastListenedAt = existing?.lastListenedAt ?: 0L
            var morningMs = existing?.morningMs ?: 0L
            var afternoonMs = existing?.afternoonMs ?: 0L
            var eveningMs = existing?.eveningMs ?: 0L
            var nightMs = existing?.nightMs ?: 0L

            for (session in sessions) {
                consumedMs += session.consumedMs
                sessionCount += 1
                if (session.completed) completionCount += 1
                if (session.endedAt > lastListenedAt) lastListenedAt = session.endedAt
                when (session.timeBucket) {
                    0 -> morningMs += session.consumedMs
                    1 -> afternoonMs += session.consumedMs
                    2 -> eveningMs += session.consumedMs
                    else -> nightMs += session.consumedMs
                }
            }

            upsertRollup(
                ListeningRollupEntity(
                    localDay = localDay,
                    episodeId = episodeId,
                    podcastId = podcastId,
                    consumedMs = consumedMs,
                    sessionCount = sessionCount,
                    completionCount = completionCount,
                    lastListenedAt = lastListenedAt,
                    morningMs = morningMs,
                    afternoonMs = afternoonMs,
                    eveningMs = eveningMs,
                    nightMs = nightMs,
                ),
            )
        }

        deleteSessionsEligibleForRollup(cutoffEndedAtExclusive, todayLocalDay)
        return eligible.size
    }
}
