package cx.aswin.boxlore.core.catalog.home

import cx.aswin.boxlore.core.catalog.toHttps
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.HomeCandidateItemDto

internal fun HomeCandidateItemDto.toEpisodeOrNull(
    algorithmVersion: String?,
): Episode? {
    val id = episodeId?.takeIf { it.isNotBlank() } ?: return null
    val audio = audioUrl?.takeIf { it.isNotBlank() }?.toHttps() ?: return null
    return Episode(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "Episode",
        description = "",
        audioUrl = audio,
        imageUrl = imageUrl?.toHttps()?.takeIf { it.isNotBlank() },
        podcastImageUrl = (podcastImageUrl ?: imageUrl)?.toHttps()?.takeIf { it.isNotBlank() },
        podcastTitle = podcastTitle,
        podcastId = podcastId,
        podcastGenre = genre,
        duration = durationSeconds ?: 0,
        publishedDate = publishedDate ?: 0L,
        retrievalScore = retrievalScore,
        recommendationSource = source,
        recommendationReason = reason,
        recommendationAlgorithmVersion = algorithmVersion,
    )
}

internal fun HomeCandidateItemDto.toPodcastOrNull(): Podcast? {
    val id = podcastId?.takeIf { it.isNotBlank() } ?: return null
    return Podcast(
        id = id,
        title = podcastTitle?.takeIf { it.isNotBlank() } ?: title ?: "Podcast",
        artist = "",
        imageUrl = (podcastImageUrl ?: imageUrl)?.toHttps().orEmpty(),
        description = "",
        genre = genre ?: "Podcast",
    )
}
