package cx.aswin.boxcast.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RssEpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<RssEpisodeEntity>)

    @Query("SELECT * FROM rss_episodes WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getEpisode(episodeId: String): RssEpisodeEntity?

    @Query(
        """
        SELECT * FROM rss_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate DESC, episodeId ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getNewestPage(podcastId: String, limit: Int, offset: Int): List<RssEpisodeEntity>

    @Query(
        """
        SELECT * FROM rss_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate ASC, episodeId DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getOldestPage(podcastId: String, limit: Int, offset: Int): List<RssEpisodeEntity>

    @Query(
        """
        SELECT * FROM rss_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate DESC, episodeId ASC
        """,
    )
    suspend fun getAllNewest(podcastId: String): List<RssEpisodeEntity>

    @Query(
        """
        SELECT * FROM rss_episodes
        WHERE podcastId = :podcastId
          AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY publishedDate DESC, episodeId ASC
        """,
    )
    suspend fun search(podcastId: String, query: String): List<RssEpisodeEntity>

    @Query(
        """
        SELECT * FROM rss_episodes
        WHERE podcastId = :podcastId
        ORDER BY publishedDate DESC, episodeId ASC
        LIMIT 1
        """,
    )
    suspend fun getNewest(podcastId: String): RssEpisodeEntity?

    @Query("SELECT COUNT(*) FROM rss_episodes WHERE podcastId = :podcastId")
    suspend fun count(podcastId: String): Int

    @Query("SELECT episodeId FROM rss_episodes WHERE podcastId = :podcastId")
    suspend fun getEpisodeIds(podcastId: String): List<String>

    @Query("DELETE FROM rss_episodes WHERE podcastId = :podcastId")
    suspend fun deleteForPodcast(podcastId: String)
}
