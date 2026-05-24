package cx.aswin.boxcast.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val imageUrl: String? = null,
    val podcastImageUrl: String? = null,
    val podcastTitle: String? = null, // For Queue context
    val podcastId: String? = null,    // For Queue fallback refill
    val podcastGenre: String? = null, // For Queue genre matching
    val podcastArtist: String? = null, // For display
    val duration: Int = 0, // seconds
    val publishedDate: Long = 0L, // Unix timestamp
    // --- Podcast 2.0 ---
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val transcripts: List<Transcript>? = null,
    val persons: List<Person>? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeType: String? = null,  // "full", "trailer", "bonus"
    val contextType: String? = null,
    val contextSourceId: String? = null
)
