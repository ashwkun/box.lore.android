package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CuratedCuriosityResponseDto(
    @SerialName("questionOfTheDay")
    val questionOfTheDay: DailyCuriosityDto? = null,
    @SerialName("questionsStack")
    val questionsStack: List<DailyCuriosityDto> = emptyList(),
    @SerialName("categories")
    val categories: List<CuratedCuriosityCategoryDto> = emptyList(),
)

@Serializable
data class DailyCuriosityDto(
    @SerialName("date")
    val date: String,
    @SerialName("question")
    val question: String,
    @SerialName("explanation")
    val explanation: String? = null,
    @SerialName("curiosityScore")
    val curiosityScore: Int? = 0,
    @SerialName("episode")
    val episode: EpisodeItem,
)

@Serializable
data class CuratedCuriosityCategoryDto(
    @SerialName("title")
    val title: String,
    @SerialName("shows")
    val shows: List<CuratedCuriosityPodcastDto> = emptyList(),
)

@Serializable
data class CuratedCuriosityPodcastDto(
    @SerialName("id")
    val id: Long,
    @SerialName("itunesId")
    val itunesId: Long? = null,
    @SerialName("title")
    val title: String,
    @SerialName("author")
    val author: String? = null,
    @SerialName("image")
    val image: String? = null,
    @SerialName("artwork")
    val artwork: String? = null,
    @SerialName("feedUrl")
    val feedUrl: String? = null,
)
