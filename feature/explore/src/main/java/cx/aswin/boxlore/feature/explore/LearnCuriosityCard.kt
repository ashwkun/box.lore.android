package cx.aswin.boxlore.feature.explore

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.model.DailyCuriosityDto

data class LearnCuriosityCard(
    val episodeId: String,
    val question: String,
    val explanation: String?,
    val curiosityScore: Int,
    val episodeTitle: String,
    val podcastTitle: String?,
    val imageUrl: String?,
    val feedImage: String?,
    val podcastId: String?,
    val audioUrl: String?,
    val duration: Int,
    val description: String?,
)

fun DailyCuriosityDto.toLearnCuriosityCard(): LearnCuriosityCard {
    val episode = episode
    return LearnCuriosityCard(
        episodeId = episode.id.toString(),
        question = question,
        explanation = explanation,
        curiosityScore = curiosityScore ?: 0,
        episodeTitle = episode.title,
        podcastTitle = episode.feedTitle,
        imageUrl = episode.image,
        feedImage = episode.feedImage,
        podcastId = episode.feedId?.toString(),
        audioUrl = episode.enclosureUrl,
        duration = episode.duration ?: 0,
        description = episode.description,
    )
}

fun LearnHistoryEntry.toLearnCuriosityCard(): LearnCuriosityCard = LearnCuriosityCard(
    episodeId = episodeId,
    question = question,
    explanation = explanation,
    curiosityScore = curiosityScore,
    episodeTitle = episodeTitle,
    podcastTitle = podcastTitle,
    imageUrl = imageUrl,
    feedImage = feedImage,
    podcastId = podcastId,
    audioUrl = audioUrl,
    duration = duration,
    description = description,
)

fun LearnCuriosityCard.toEpisode(): Episode = Episode(
    id = episodeId,
    title = episodeTitle,
    description = description.orEmpty(),
    audioUrl = audioUrl.orEmpty(),
    imageUrl = imageUrl ?: feedImage,
    podcastImageUrl = feedImage ?: imageUrl,
    podcastTitle = podcastTitle,
    podcastId = podcastId,
    duration = duration,
)
