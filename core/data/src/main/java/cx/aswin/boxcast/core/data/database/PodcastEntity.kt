package cx.aswin.boxcast.core.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey
    val podcastId: String,
    
    val title: String,
    val author: String,
    val imageUrl: String,
    val description: String?,
    
    // Subscription State
    val isSubscribed: Boolean = false,
    
    val genre: String? = null,
    
    val lastRefreshed: Long = 0,
    
    // Cached latest episode for instantaneous home screen rendering
    val latestEpisode: cx.aswin.boxcast.core.model.Episode? = null,
    
    // Podcasting 2.0 Fields
    val podcastGuid: String? = null,
    val fundingUrl: String? = null,
    val fundingMessage: String? = null,
    val medium: String? = null,
    val hasValue: Boolean = false
)
