package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

internal fun episode(
    id: String = "episode-1",
    podcastId: String? = "podcast-1",
    podcastTitle: String? = "Test Podcast",
    podcastArtist: String? = "Test Artist",
    podcastImageUrl: String? = "https://example.com/podcast.jpg",
    podcastGenre: String? = "Technology",
    contextType: String? = null,
    contextSourceId: String? = null,
): Episode =
    Episode(
        id = id,
        title = "Test Episode",
        description = "Description",
        audioUrl = "https://example.com/audio.mp3",
        podcastId = podcastId,
        podcastTitle = podcastTitle,
        podcastArtist = podcastArtist,
        podcastImageUrl = podcastImageUrl,
        podcastGenre = podcastGenre,
        contextType = contextType,
        contextSourceId = contextSourceId,
    )

internal fun podcast(
    id: String = "podcast-1",
    title: String = "Current Podcast",
    artist: String = "Current Artist",
    imageUrl: String = "https://example.com/current.jpg",
    genre: String = "News",
): Podcast =
    Podcast(
        id = id,
        title = title,
        artist = artist,
        imageUrl = imageUrl,
        genre = genre,
    )

internal fun chapter(
    startSeconds: Double,
    title: String,
    imageUrl: String? = null,
): Chapter =
    Chapter(
        startTime = startSeconds,
        title = title,
        img = imageUrl,
    )
