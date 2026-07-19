package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: ListeningHistoryEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(historyItems: List<ListeningHistoryEntity>)
    
    // Fetch Top 7 items for the new Split UI (1st is Hero, 2-7 are Grid)
    @Query("""
        SELECT episodeId, podcastId, episodeTitle, episodeImageUrl, podcastImageUrl, 
               episodeAudioUrl, podcastName, progressMs, durationMs, isCompleted, 
               isLiked, lastPlayedAt, isDirty, syncedAt, enclosureType, 
               isManualCompletion, isBulkCompletion, NULL as episodeDescription 
        FROM listening_history 
        WHERE isCompleted = 0 AND progressMs > 0 
        ORDER BY lastPlayedAt DESC 
        LIMIT 7
    """)
    fun getResumeItems(): Flow<List<ListeningHistoryEntity>>

    // Suspend version for Android Auto browse tree (non-Flow, one-shot)
    @Query("SELECT * FROM listening_history WHERE isCompleted = 0 AND progressMs > 0 ORDER BY lastPlayedAt DESC LIMIT 20")
    suspend fun getResumeItemsList(): List<ListeningHistoryEntity>
    
    @Query("""
        SELECT episodeId, podcastId, episodeTitle, episodeImageUrl, podcastImageUrl, 
               episodeAudioUrl, podcastName, progressMs, durationMs, isCompleted, 
               isLiked, lastPlayedAt, isDirty, syncedAt, enclosureType, 
               isManualCompletion, isBulkCompletion, NULL as episodeDescription 
        FROM listening_history 
        ORDER BY lastPlayedAt DESC
        LIMIT 300
    """)
    fun getAllHistory(): Flow<List<ListeningHistoryEntity>>
    
    @Query("SELECT * FROM listening_history WHERE isDirty = 1")
    suspend fun getDirtyItems(): List<ListeningHistoryEntity>
    
    @Query("UPDATE listening_history SET isDirty = 0, syncedAt = :timestamp WHERE episodeId IN (:ids)")
    suspend fun markAsSynced(ids: List<String>, timestamp: Long)

    @Query("DELETE FROM listening_history WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)
    
    @Query("DELETE FROM listening_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM listening_history WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getHistoryItem(episodeId: String): ListeningHistoryEntity?

    @Query("SELECT * FROM listening_history WHERE podcastId = :podcastId")
    suspend fun getHistoryForPodcast(podcastId: String): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history WHERE episodeId = :episodeId LIMIT 1")
    fun getHistoryItemFlow(episodeId: String): Flow<ListeningHistoryEntity?>
    
    // Get the most recent incomplete session (for resume cards)
    @Query("SELECT * FROM listening_history WHERE isCompleted = 0 ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun getLastPlayedSession(): ListeningHistoryEntity?

    // Get the most recent session regardless of completion (for miniplayer restore)
    @Query("SELECT * FROM listening_history ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun getLastPlayedSessionAny(): ListeningHistoryEntity?

    // Like Feature
    @Query("""
        SELECT episodeId, podcastId, episodeTitle, episodeImageUrl, podcastImageUrl, 
               episodeAudioUrl, podcastName, progressMs, durationMs, isCompleted, 
               isLiked, lastPlayedAt, isDirty, syncedAt, enclosureType, 
               isManualCompletion, isBulkCompletion, NULL as episodeDescription 
        FROM listening_history 
        WHERE isLiked = 1 
        ORDER BY lastPlayedAt DESC
    """)
    fun getLikedEpisodes(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history WHERE isLiked = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getLikedEpisodesList(limit: Int = 50): List<ListeningHistoryEntity>

    @Query("UPDATE listening_history SET isLiked = :isLiked WHERE episodeId = :episodeId")
    suspend fun setLikeStatus(episodeId: String, isLiked: Boolean)

    @Query("UPDATE listening_history SET progressMs = :progressMs, durationMs = :durationMs, lastPlayedAt = :lastPlayedAt, isDirty = 1 WHERE episodeId = :episodeId")
    suspend fun updateProgress(episodeId: String, progressMs: Long, durationMs: Long, lastPlayedAt: Long)

    @Query("UPDATE listening_history SET isCompleted = :isCompleted WHERE episodeId = :episodeId")
    suspend fun setCompletionStatus(episodeId: String, isCompleted: Boolean)

    // Get all episode IDs that have been fully played (for "unplayed" filtering in queue)
    @Query("SELECT episodeId FROM listening_history WHERE isCompleted = 1")
    fun getCompletedEpisodeIdsFlow(): Flow<List<String>>

    @Query("SELECT episodeId FROM listening_history WHERE isCompleted = 1")
    suspend fun getCompletedEpisodeIds(): List<String>
    
    // Get unique podcast IDs played after the given timestamp
    @Query("SELECT DISTINCT podcastId FROM listening_history WHERE lastPlayedAt > :sinceTimestamp")
    suspend fun getRecentlyPlayedPodcasts(sinceTimestamp: Long): List<String>

    @Query("SELECT * FROM listening_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentHistoryList(limit: Int): List<ListeningHistoryEntity>
}
