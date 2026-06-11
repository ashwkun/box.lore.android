package cx.aswin.boxcast.core.network.model

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingPart(
    @SerialName("text") val text: String
)

@Serializable
data class OnboardingHistoryEntry(
    @SerialName("role") val role: String, // "user" or "model"
    @SerialName("parts") val parts: List<OnboardingPart>
)

@Serializable
data class OnboardingNextTurnRequest(
    @SerialName("history") val history: List<OnboardingHistoryEntry>
)

@Serializable
data class OnboardingNextTurnResponse(
    @SerialName("assistantMessage") val assistantMessage: String,
    @SerialName("options") val options: List<String> = emptyList()
)

@Serializable
data class OnboardingSynthesizeRequest(
    @SerialName("history") val history: List<OnboardingHistoryEntry>
)

@Serializable
data class OnboardingQuery(
    @SerialName("rowTitle") val rowTitle: String,
    @SerialName("searchQuery") val searchQuery: String,
    @SerialName("popularSuggestions") val popularSuggestions: List<String> = emptyList()
)

@Serializable
data class OnboardingCurriculumRequest(
    @SerialName("queries") val queries: List<OnboardingQuery>,
    @SerialName("country") val country: String? = null
)

@Serializable
data class OnboardingCurriculumPodcastDto(
    @SerialName("id") val id: Long,
    @SerialName("itunesId") val itunesId: Long? = null,
    @SerialName("title") val title: String,
    @SerialName("author") val author: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("artwork") val artwork: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("categories") val categories: Map<String, String>? = null,
    @SerialName("description") val description: String? = null
)

@Serializable
data class OnboardingCurriculumEpisodeDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("datePublished") val datePublished: Long? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("enclosureUrl") val enclosureUrl: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("feedImage") val feedImage: String? = null,
    @SerialName("feedId") val feedId: Long? = null,
    @SerialName("feedTitle") val feedTitle: String? = null,
    @SerialName("description") val description: String? = null
)

@Serializable
data class OnboardingCurriculumRowDto(
    @SerialName("rowTitle") val rowTitle: String,
    @SerialName("podcasts") val podcasts: List<OnboardingCurriculumPodcastDto> = emptyList(),
    @SerialName("episodes") val episodes: List<OnboardingCurriculumEpisodeDto> = emptyList()
)

// Mapping extensions
fun OnboardingCurriculumPodcastDto.toPodcast(): Podcast {
    val cleanUrl = (this.artwork ?: this.image ?: "").let { url ->
        if (url.startsWith("//")) "https:$url" else url
    }
    return Podcast(
        id = this.id.toString(),
        title = this.title,
        artist = this.author ?: "Unknown",
        imageUrl = cleanUrl,
        description = this.description,
        genre = this.categories?.values?.firstOrNull() ?: "Podcast"
    )
}

fun OnboardingCurriculumEpisodeDto.toEpisode(): Episode {
    val cleanEpImg = this.image ?: this.feedImage ?: ""
    val cleanFeedImg = this.feedImage ?: ""
    return Episode(
        id = this.id.toString(),
        title = this.title,
        description = this.description ?: "",
        audioUrl = this.enclosureUrl ?: "",
        imageUrl = if (cleanEpImg.startsWith("//")) "https:$cleanEpImg" else cleanEpImg,
        podcastImageUrl = if (cleanFeedImg.startsWith("//")) "https:$cleanFeedImg" else cleanFeedImg,
        podcastTitle = this.feedTitle,
        podcastId = this.feedId?.toString(),
        duration = this.duration ?: 0,
        publishedDate = this.datePublished ?: 0L
    )
}

@Serializable
data class OnboardingGenreSynthRequest(
    @SerialName("genres") val genres: List<String>,
    @SerialName("subGenres") val subGenres: List<String>,
    @SerialName("activity") val activity: String,
    @SerialName("length") val length: String,
    @SerialName("country") val country: String? = null,
    @SerialName("skipGemini") val skipGemini: Boolean = false
)

