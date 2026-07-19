package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Bundled params for [PodcastDao.updateRssState] — Room's `@Query` bind parameters can't
 * reference object fields (no `:state.field` syntax), so this is applied via a partial-entity
 * [Update] instead: field names below must match [PodcastEntity] column names exactly, and
 * [podcastId] doubles as the `@PrimaryKey` Room matches the update against.
 */
data class RssFeedStateUpdate(
    val podcastId: String,
    val feedEtag: String?,
    val feedLastModified: String?,
    val feedDeclaredUpdatedAt: Long?,
    val rssRefreshCapability: String,
    val lastRssSyncAt: Long,
    val rssCatalogStale: Boolean,
    val rssHasNewEpisodes: Boolean,
)

@Dao
interface PodcastDao {
    
    @Upsert
    suspend fun upsert(podcast: PodcastEntity)
    
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY title ASC")
    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>>

    // Suspend version for Android Auto browse tree (non-Flow, one-shot)
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY title ASC")
    suspend fun getSubscribedPodcastsList(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 AND sourceType = 'rss' ORDER BY title ASC")
    suspend fun getSubscribedRssPodcasts(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 AND sourceType = 'podcast_index' ORDER BY title ASC")
    suspend fun getSubscribedPodcastIndexPodcasts(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE linkedPodcastIndexId = :podcastIndexId LIMIT 1")
    suspend fun getRssPodcastLinkedTo(podcastIndexId: String): PodcastEntity?
    
    @Query("SELECT * FROM podcasts WHERE podcastId = :id")
    suspend fun getPodcast(id: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE podcastId IN (:ids)")
    suspend fun getPodcastsByIds(ids: List<String>): List<PodcastEntity>
    
    @Query("UPDATE podcasts SET isSubscribed = :isSubscribed WHERE podcastId = :id")
    suspend fun setSubscribed(id: String, isSubscribed: Boolean)

    @Query(
        """
        UPDATE podcasts
        SET isSubscribed = 0,
            subscribedAt = 0,
            notificationsEnabled = 0,
            autoDownloadEnabled = 0
        WHERE podcastId = :id
        """,
    )
    suspend fun retireLinkedPodcastIndexSubscription(id: String)

    @Query("DELETE FROM rss_episodes WHERE podcastId = :podcastId")
    suspend fun deleteRssEpisodes(podcastId: String)
    
    @Query("UPDATE podcasts SET latestEpisode = :episode WHERE podcastId = :id")
    suspend fun updateLatestEpisode(id: String, episode: cx.aswin.boxlore.core.model.Episode?)

    @Query("UPDATE podcasts SET preferredSort = :sort, type = :type WHERE podcastId = :id")
    suspend fun updatePreferredSortAndType(id: String, sort: String?, type: String)

    @Query("UPDATE podcasts SET notificationsEnabled = :enabled WHERE podcastId = :id")
    suspend fun setNotificationsEnabled(id: String, enabled: Boolean)

    @Query("UPDATE podcasts SET autoDownloadEnabled = :enabled WHERE podcastId = :id")
    suspend fun setAutoDownloadEnabled(id: String, enabled: Boolean)

    @Query(
        """
        UPDATE podcasts
        SET skipBeginningOverrideMs = :skipBeginningMs,
            skipEndingOverrideMs = :skipEndingMs
        WHERE podcastId = :id
        """,
    )
    suspend fun setPlaybackSkipOverrides(
        id: String,
        skipBeginningMs: Long?,
        skipEndingMs: Long?,
    )

    /** Partial-entity update — see [RssFeedStateUpdate] for why this isn't a plain `@Query`. */
    @Update(entity = PodcastEntity::class)
    suspend fun updateRssState(state: RssFeedStateUpdate)

    @Query("SELECT * FROM podcasts WHERE notificationsEnabled = 1")
    suspend fun getNotificationEnabledPodcasts(): List<PodcastEntity>

    /** Clears the "new episodes" RSS badge once the user has opened/dismissed the podcast. */
    @Query("UPDATE podcasts SET rssHasNewEpisodes = 0 WHERE podcastId = :id")
    suspend fun clearRssNewEpisodesFlag(id: String)
}
