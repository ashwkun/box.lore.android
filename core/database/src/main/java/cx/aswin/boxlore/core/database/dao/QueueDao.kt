package cx.aswin.boxlore.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import cx.aswin.boxlore.core.database.entities.QueueItem
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    fun getAllQueueItems(): Flow<List<QueueItem>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun getAllQueueItemsSync(): List<QueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: QueueItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItem>)

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun deleteQueueItem(id: Long)

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Update
    suspend fun updateQueueItem(item: QueueItem)

    @Transaction
    suspend fun updateQueuePositions(items: List<QueueItem>) {
        items.forEach { updateQueueItem(it) }
    }
    
    @Query("SELECT MAX(position) FROM queue_items")
    suspend fun getMaxPosition(): Int?

    @Query("SELECT COUNT(*) FROM queue_items WHERE episodeId = :episodeId")
    suspend fun countEpisode(episodeId: String): Int

    @Query("SELECT * FROM queue_items WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getQueueItemByEpisodeId(episodeId: String): QueueItem?
}
