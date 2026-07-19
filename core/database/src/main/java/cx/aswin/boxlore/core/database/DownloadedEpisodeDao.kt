package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedEpisodeDao {
    @Query("SELECT * FROM downloaded_episodes ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT * FROM downloaded_episodes")
    suspend fun getAllDownloadsSync(): List<DownloadedEpisodeEntity>

    @Query("SELECT * FROM downloaded_episodes WHERE status = 2 ORDER BY downloadedAt DESC LIMIT :limit")
    suspend fun getCompletedDownloads(limit: Int = 50): List<DownloadedEpisodeEntity>

    @Query("SELECT * FROM downloaded_episodes WHERE episodeId = :episodeId")
    suspend fun getDownload(episodeId: String): DownloadedEpisodeEntity?

    @Query("SELECT * FROM downloaded_episodes WHERE podcastId = :podcastId")
    suspend fun getDownloadsForPodcast(podcastId: String): List<DownloadedEpisodeEntity>

    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE episodeId = :episodeId AND status = 2")
    fun isDownloadedFlow(episodeId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE episodeId = :episodeId AND status IN (0, 1)")
    fun isDownloadingFlow(episodeId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloaded_episodes")
    fun getTotalSizeBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE podcastId = :podcastId AND episodeId != :excludeEpisodeId")
    suspend fun countOthersByPodcastId(podcastId: String, excludeEpisodeId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedEpisodeEntity)

    @Query("DELETE FROM downloaded_episodes WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)
}
