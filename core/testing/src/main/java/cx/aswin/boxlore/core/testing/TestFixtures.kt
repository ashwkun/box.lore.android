package cx.aswin.boxlore.core.testing

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/** Small shared fixtures for JVM unit tests across modules. */
object TestFixtures {
    fun podcast(
        id: String = "pod-1",
        title: String = "Test Podcast",
        artist: String = "Test Artist",
        imageUrl: String = "https://example.com/art.jpg",
    ): Podcast =
        Podcast(
            id = id,
            title = title,
            artist = artist,
            imageUrl = imageUrl,
        )

    fun episode(
        id: String = "ep-1",
        title: String = "Test Episode",
        podcastId: String = "pod-1",
        podcastTitle: String = "Test Podcast",
        audioUrl: String = "https://example.com/audio.mp3",
        duration: Int = 3600,
    ): Episode =
        Episode(
            id = id,
            title = title,
            description = "desc",
            audioUrl = audioUrl,
            imageUrl = null,
            duration = duration,
            publishedDate = 0L,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
        )
}
