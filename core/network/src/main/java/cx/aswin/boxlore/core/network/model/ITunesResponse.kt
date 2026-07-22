package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ITunesSearchResponse(
    val resultCount: Int,
    val results: List<ITunesPodcastDto>,
)

@Serializable
data class ITunesPodcastDto(
    @SerialName("collectionId") val id: Long,
    @SerialName("collectionName") val name: String,
    @SerialName("artistName") val artist: String,
    @SerialName("artworkUrl600") val artworkUrl: String?,
    @SerialName("feedUrl") val feedUrl: String?,
    @SerialName("primaryGenreName") val genre: String?,
)
