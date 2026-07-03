package cx.aswin.boxcast.core.data.database

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

    @Query("SELECT * FROM downloaded_episodes WHERE episodeId = :episodeId")
    suspend fun getDownload(episodeId: String): DownloadedEpisodeEntity?

    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE episodeId = :episodeId AND status = 2")
    fun isDownloadedFlow(episodeId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM downloaded_episodes WHERE episodeId = :episodeId AND status IN (0, 1)")
    fun isDownloadingFlow(episodeId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloaded_episodes")
    fun getTotalSizeBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedEpisodeEntity)

    @Query("DELETE FROM downloaded_episodes WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)
}
