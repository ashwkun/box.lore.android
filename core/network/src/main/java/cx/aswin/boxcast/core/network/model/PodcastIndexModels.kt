package cx.aswin.boxcast.core.network.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============== TRENDING ==============

@Serializable
data class TrendingResponse(
    @SerialName("status")
    val status: String,
    
    @SerialName("feeds")
    val feeds: List<TrendingFeed> = emptyList()
)

@Serializable
data class TrendingFeed(
    @SerialName("id")
    val id: Long,
    
    @SerialName("title")
    val title: String,
    
    @SerialName("author")
    val author: String? = null,
    
    @SerialName("description")
    val description: String? = null,
    
    @SerialName("image")
    val image: String? = null,
    
    @SerialName("artwork")
    val artwork: String? = null,
    
    @SerialName("language")
    val language: String? = null,
    
    @SerialName("categories")
    val categories: Map<String, String> = emptyMap(),
    
    @SerialName("itunesId")
    val itunesId: Long? = null,
    
    @SerialName("trendScore")
    val trendScore: Int? = null,
    
    @SerialName("newestItemPublishTime")
    val newestItemPublishTime: Long? = null,
    
    @SerialName("latestEpisode")
    val latestEpisode: EpisodeItem? = null
)

// ============== SEARCH ==============

@Serializable
data class SearchResponse(
    @SerialName("status")
    val status: String,
    
    @SerialName("feeds")
    val feeds: List<SearchFeed> = emptyList()
)

@Serializable
data class SearchFeed(
    @SerialName("id")
    val id: Long,
    
    @SerialName("title")
    val title: String,
    
    @SerialName("author")
    val author: String? = null,
    
    @SerialName("description")
    val description: String? = null,
    
    @SerialName("image")
    val image: String? = null,
    
    @SerialName("artwork")
    val artwork: String? = null,
    
    @SerialName("categories")
    val categories: Map<String, String> = emptyMap()
)

// ============== EPISODES ==============

@Serializable
data class EpisodesResponse(
    @SerialName("status")
    val status: String,
    
    @SerialName("items")
    val items: List<EpisodeItem> = emptyList()
)

@Serializable
data class EpisodesPaginatedResponse(
    @SerialName("items")
    val items: List<EpisodeItem> = emptyList(),
    
    @SerialName("hasMore")
    val hasMore: Boolean = false,
    
    @SerialName("offset")
    val offset: Int = 0,
    
    @SerialName("limit")
    val limit: Int = 20
)

@Serializable
data class SingleEpisodeResponse(
    @SerialName("status")
    val status: String,
    
    @SerialName("episode")
    val episode: EpisodeItem? = null
)

@Serializable
data class EpisodeItem(
    @SerialName("id")
    val id: Long,
    
    @SerialName("title")
    val title: String,
    
    @SerialName("description")
    val description: String? = null,
    
    @SerialName("enclosureUrl")
    val enclosureUrl: String? = null,
    
    @SerialName("enclosureLength")
    val enclosureLength: Long? = null,
    
    @SerialName("enclosureType")
    val enclosureType: String? = null,
    
    @SerialName("duration")
    val duration: Int? = null,
    
    @SerialName("datePublished")
    val datePublished: Long? = null,
    
    @SerialName("image")
    val image: String? = null,
    
    @SerialName("feedImage")
    val feedImage: String? = null,
    
    @SerialName("feedId")
    val feedId: Long? = null,
    
    @SerialName("chaptersUrl")
    val chaptersUrl: String? = null,
    
    @SerialName("transcriptUrl")
    val transcriptUrl: String? = null,

    // --- Podcast 2.0 Fields ---
    @SerialName("persons")
    val persons: List<PersonItem>? = null,

    @SerialName("transcripts")
    val transcripts: List<TranscriptItem>? = null,

    @SerialName("socialInteract")
    val socialInteract: List<SocialInteractItem>? = null,

    @SerialName("value")
    val value: ValueModel? = null,

    @SerialName("season")
    val season: Int? = null,

    @SerialName("episode")
    val episodeNumber: Int? = null,

    @SerialName("episodeType")
    val episodeType: String? = null  // "full", "trailer", "bonus"
)

// ============== SINGLE PODCAST ==============

@Serializable
data class PodcastResponse(
    @SerialName("status")
    val status: String,
    
    @SerialName("feed")
    val feed: PodcastFeed? = null
)

@Serializable
data class PodcastFeed(
    @SerialName("id")
    val id: Long,
    
    @SerialName("title")
    val title: String,
    
    @SerialName("url")
    val url: String? = null,
    
    @SerialName("author")
    val author: String? = null,
    
    @SerialName("description")
    val description: String? = null,
    
    @SerialName("image")
    val image: String? = null,
    
    @SerialName("artwork")
    val artwork: String? = null,
    
    @SerialName("language")
    val language: String? = null,
    
    @SerialName("categories")
    val categories: Map<String, String> = emptyMap(),

    // --- Podcast 2.0 Fields ---
    @SerialName("funding")
    val funding: FundingItem? = null,

    @SerialName("value")
    val value: ValueModel? = null,

    @SerialName("podcastGuid")
    val podcastGuid: String? = null,

    @SerialName("medium")
    val medium: String? = null,  // "podcast", "music", "video"

    @SerialName("locked")
    val locked: Int? = null,

    @SerialName("ownerName")
    val ownerName: String? = null
)

// ============== METADATA ==============

@Serializable
data class PodcastMetaResponse(
    @SerialName("id")
    val id: Long,
    
    @SerialName("type")
    val type: String? = "episodic", // serial or episodic
    
    @SerialName("title")
    val title: String? = null
)

// ============== PODCAST 2.0 TYPES ==============

@Serializable
data class PersonItem(
    @SerialName("id")
    val id: Long? = null,

    @SerialName("name")
    val name: String,

    @SerialName("role")
    val role: String? = null,  // "host", "guest", "editor"

    @SerialName("group")
    val group: String? = null, // "cast", "writing", "visuals"

    @SerialName("href")
    val href: String? = null,

    @SerialName("img")
    val img: String? = null
)

@Serializable
data class TranscriptItem(
    @SerialName("url")
    val url: String,

    @SerialName("type")
    val type: String  // "application/srt", "text/vtt", "application/json"
)

@Serializable
data class FundingItem(
    @SerialName("url")
    val url: String,

    @SerialName("message")
    val message: String? = null
)

@Serializable
data class SocialInteractItem(
    @SerialName("uri")
    val uri: String,

    @SerialName("protocol")
    val protocol: String? = null, // "activitypub", "twitter"

    @SerialName("accountId")
    val accountId: String? = null,

    @SerialName("accountUrl")
    val accountUrl: String? = null
)

@Serializable
data class ValueModel(
    @SerialName("model")
    val model: ValueModelType? = null,

    @SerialName("destinations")
    val destinations: List<ValueDestination>? = null
)

@Serializable
data class ValueModelType(
    @SerialName("type")
    val type: String? = null,  // "lightning"

    @SerialName("method")
    val method: String? = null, // "keysend"

    @SerialName("suggested")
    val suggested: String? = null
)

@Serializable
data class ValueDestination(
    @SerialName("name")
    val name: String? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("address")
    val address: String? = null,

    @SerialName("split")
    val split: Int? = null
)
