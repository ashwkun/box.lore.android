package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.network.model.EpisodeItem

fun EpisodeItem.toEpisode(): Episode {
    return Episode(
        id = id.toString(),
        title = title,
        description = description ?: "",
        audioUrl = enclosureUrl ?: "",
        imageUrl = image ?: feedImage,
        podcastImageUrl = feedImage,
        podcastTitle = feedTitle,
        podcastId = feedId?.toString(),
        duration = duration ?: 0,
        publishedDate = datePublished ?: 0L,
        chaptersUrl = chaptersUrl,
        transcriptUrl = transcriptUrl,
        enclosureType = enclosureType
    )
}
