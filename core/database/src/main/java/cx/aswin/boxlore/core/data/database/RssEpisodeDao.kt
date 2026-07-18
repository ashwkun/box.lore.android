package cx.aswin.boxlore.core.data.database

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

    /**
     * [query] must already be escaped for SQL LIKE (see [cx.aswin.boxlore.core.data.escapeForSqlLike])
     * so literal `%`/`_` characters in a user's search don't get treated as wildcards.
     *
     * Full-text search (FTS4) was deliberately deferred here: Room's external-content FTS4
     * tables need hand-maintained insert/update/delete triggers to stay in sync with this
     * table, plus a new destructive-free migration — a bigger, riskier change than this
     * quality-fix pass should carry. This LIKE-based search is still podcastId-scoped and
     * bounded by a per-show episode count, so it stays cheap in practice.
     */
    @Query(
        """
        SELECT * FROM rss_episodes
        WHERE podcastId = :podcastId
          AND (title LIKE '%' || :query || '%' ESCAPE '\' OR description LIKE '%' || :query || '%' ESCAPE '\')
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
