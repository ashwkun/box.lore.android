package cx.aswin.boxlore.core.testing

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.HistoryItem

/** Small shared fixtures for JVM unit tests across modules. */
object TestFixtures {
    fun podcast(
        id: String = "pod-1",
        title: String = "Test Podcast",
        artist: String = "Test Artist",
        imageUrl: String = "https://example.com/art.jpg",
        sourceType: String = Podcast.SOURCE_PODCAST_INDEX,
        subscribedAt: Long = 0L,
        genre: String = "Podcast",
    ): Podcast =
        Podcast(
            id = id,
            title = title,
            artist = artist,
            imageUrl = imageUrl,
            sourceType = sourceType,
            subscribedAt = subscribedAt,
            genre = genre,
        )

    /** Stable RSS identity: negative id + `rss:` prefix convention. */
    fun rssPodcast(
        id: String = "-1001",
        title: String = "RSS Podcast",
        feedUrl: String = "https://example.com/feed.xml",
    ): Podcast =
        podcast(
            id = id,
            title = title,
            artist = "RSS Artist",
            imageUrl = "https://example.com/rss-art.jpg",
            sourceType = Podcast.SOURCE_RSS,
        ).copy(
            // feed URL often carried via description/funding in some paths; keep title stable
            description = feedUrl,
        )

    fun episode(
        id: String = "ep-1",
        title: String = "Test Episode",
        podcastId: String = "pod-1",
        podcastTitle: String = "Test Podcast",
        audioUrl: String = "https://example.com/audio.mp3",
        duration: Int = 3600,
        publishedDate: Long = 0L,
        imageUrl: String? = null,
        description: String = "desc",
    ): Episode =
        Episode(
            id = id,
            title = title,
            description = description,
            audioUrl = audioUrl,
            imageUrl = imageUrl,
            duration = duration,
            publishedDate = publishedDate,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
        )

    fun rssEpisode(
        id: String = "rss:ep-1",
        title: String = "RSS Episode",
        podcastId: String = "-1001",
        podcastTitle: String = "RSS Podcast",
    ): Episode =
        episode(
            id = id,
            title = title,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            audioUrl = "https://example.com/rss-audio.mp3",
        )

    fun historyItem(
        podcastTitle: String = "Test Podcast",
        episodeTitle: String = "Test Episode",
        podcastId: String? = "pod-1",
        episodeId: String? = "ep-1",
        genre: String? = "Podcast",
        durationMs: Long? = 3_600_000L,
        progressMs: Long? = 60_000L,
        isCompleted: Boolean? = false,
        isLiked: Boolean? = false,
    ): HistoryItem =
        HistoryItem(
            podcastTitle = podcastTitle,
            episodeTitle = episodeTitle,
            podcastId = podcastId,
            episodeId = episodeId,
            genre = genre,
            durationMs = durationMs,
            progressMs = progressMs,
            isCompleted = isCompleted,
            isLiked = isLiked,
        )

    fun briefingChapterTitle(index: Int = 0): String = "Briefing chapter ${index + 1}"
}
