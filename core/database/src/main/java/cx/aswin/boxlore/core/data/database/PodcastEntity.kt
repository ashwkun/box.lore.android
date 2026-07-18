package cx.aswin.boxlore.core.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcasts",
    indices = [Index(value = ["linkedPodcastIndexId"])],
)
data class PodcastEntity(
    @PrimaryKey
    val podcastId: String,
    
    val title: String,
    val author: String,
    val imageUrl: String,
    val description: String?,
    
    // Subscription State
    val isSubscribed: Boolean = false,
    val subscribedAt: Long = 0L,
    
    val genre: String? = null,
    val type: String = "episodic",
    
    val lastRefreshed: Long = 0,
    
    // Cached latest episode for instantaneous home screen rendering
    val latestEpisode: cx.aswin.boxlore.core.model.Episode? = null,
    
    // Podcasting 2.0 Fields
    val podcastGuid: String? = null,
    val fundingUrl: String? = null,
    val fundingMessage: String? = null,
    val medium: String? = null,
    val hasValue: Boolean = false,
    val updateFrequency: String? = null,
    val location: String? = null,
    val license: String? = null,
    val isLocked: Boolean = false,
    
    // User listening style preference: "newest" / "oldest" / null (use type-based default)
    val preferredSort: String? = null,
    val notificationsEnabled: Boolean = false,
    val autoDownloadEnabled: Boolean = false,
    // Null inherits the global playback setting; zero explicitly disables trimming.
    val skipBeginningOverrideMs: Long? = null,
    val skipEndingOverrideMs: Long? = null,

    // Catalog source. Podcast Index remains the default for existing rows.
    val sourceType: String = SOURCE_PODCAST_INDEX,
    val feedUrl: String? = null,
    val feedEtag: String? = null,
    val feedLastModified: String? = null,
    val feedDeclaredUpdatedAt: Long? = null,
    val rssRefreshCapability: String = RSS_REFRESH_MANUAL,
    val lastRssSyncAt: Long = 0L,
    val rssCatalogStale: Boolean = false,
    val rssHasNewEpisodes: Boolean = false,
    val linkedPodcastIndexId: String? = null,
) {
    val isRss: Boolean
        get() = sourceType == SOURCE_RSS

    companion object {
        const val SOURCE_PODCAST_INDEX = "podcast_index"
        const val SOURCE_RSS = "rss"
        const val RSS_REFRESH_HEAD_VALIDATORS = "head_validators"
        const val RSS_REFRESH_MANUAL = "manual"
    }
}
