package cx.aswin.boxcast.core.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import cx.aswin.boxcast.core.network.model.EpisodeItem

@Entity(tableName = "queue_items")
data class QueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Episode Metadata
    val episodeId: String,
    val title: String,
    val podcastId: String,
    val podcastTitle: String,
    val podcastGenre: String = "",
    val podcastArtist: String = "",
    val podcastImageUrl: String? = null,
    val imageUrl: String?,
    val audioUrl: String,
    val duration: Int,
    val pubDate: Long,
    val description: String?,
    
    // Queue Metadata
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
    val contextType: String = "MANUAL",
    val contextSourceId: String? = null,

    // Podcast 2.0 Fields
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val personsJson: String? = null,      // JSON-serialized List<Person>
    val transcriptsJson: String? = null,  // JSON-serialized List<Transcript>
    
    // Episode metadata
    val episodeType: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val enclosureType: String? = null
) {
    fun toEpisodeItem(): EpisodeItem {
        return EpisodeItem(
            id = episodeId.toLongOrNull() ?: 0L,
            title = title,
            enclosureUrl = audioUrl,
            description = description ?: "",
            duration = duration,
            datePublished = pubDate,
            image = imageUrl ?: "",
            feedImage = podcastImageUrl, // Podcast artwork fallback
            episodeType = episodeType,
            season = seasonNumber,
            episodeNumber = episodeNumber,
            enclosureType = enclosureType
        )
    }
}
