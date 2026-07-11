package cx.aswin.boxcast.feature.player.v2.logic

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast

internal object PlayerTestFixtures {
    fun episode(
        id: String = "ep1",
        title: String = "Episode $id",
        description: String = "Description for $id",
        audioUrl: String = "https://example.com/$id.mp3",
        imageUrl: String? = null,
        podcastImageUrl: String? = null,
        podcastTitle: String? = "Test Podcast",
        podcastId: String? = "pod1",
        podcastGenre: String? = "Technology",
        duration: Int = 3600,
        publishedDate: Long = 0L,
        contextType: String? = null,
        contextSourceId: String? = null,
    ): Episode = Episode(
        id = id,
        title = title,
        description = description,
        audioUrl = audioUrl,
        imageUrl = imageUrl,
        podcastImageUrl = podcastImageUrl,
        podcastTitle = podcastTitle,
        podcastId = podcastId,
        podcastGenre = podcastGenre,
        duration = duration,
        publishedDate = publishedDate,
        contextType = contextType,
        contextSourceId = contextSourceId,
    )

    fun podcast(
        id: String = "pod1",
        title: String = "Test Podcast",
        imageUrl: String = "https://example.com/$id.jpg",
    ): Podcast = Podcast(
        id = id,
        title = title,
        artist = "Test Artist",
        imageUrl = imageUrl,
    )
}
