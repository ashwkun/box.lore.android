package cx.aswin.boxcast.core.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
    suspend fun updateLatestEpisode(id: String, episode: cx.aswin.boxcast.core.model.Episode?)

    @Query("UPDATE podcasts SET preferredSort = :sort, type = :type WHERE podcastId = :id")
    suspend fun updatePreferredSortAndType(id: String, sort: String?, type: String)

    @Query("UPDATE podcasts SET notificationsEnabled = :enabled WHERE podcastId = :id")
    suspend fun setNotificationsEnabled(id: String, enabled: Boolean)

    @Query("UPDATE podcasts SET autoDownloadEnabled = :enabled WHERE podcastId = :id")
    suspend fun setAutoDownloadEnabled(id: String, enabled: Boolean)

    @Query(
        """
        UPDATE podcasts
        SET feedEtag = :etag,
            feedLastModified = :lastModified,
            feedDeclaredUpdatedAt = :declaredUpdatedAt,
            rssRefreshCapability = :refreshCapability,
            lastRssSyncAt = :syncedAt,
            rssCatalogStale = :catalogStale,
            rssHasNewEpisodes = :hasNewEpisodes
        WHERE podcastId = :id
        """,
    )
    suspend fun updateRssState(
        id: String,
        etag: String?,
        lastModified: String?,
        declaredUpdatedAt: Long?,
        refreshCapability: String,
        syncedAt: Long,
        catalogStale: Boolean,
        hasNewEpisodes: Boolean,
    )

    @Query("SELECT * FROM podcasts WHERE notificationsEnabled = 1")
    suspend fun getNotificationEnabledPodcasts(): List<PodcastEntity>
}
