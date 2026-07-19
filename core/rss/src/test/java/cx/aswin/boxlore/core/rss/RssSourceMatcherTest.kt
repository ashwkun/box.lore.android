package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RssSourceMatcherTest {
    @Test
    fun `exact feed URL ignores fragments and trailing slash`() {
        val candidate =
            podcast(
                feedUrl = "https://example.com/show/feed.xml/",
            )

        assertTrue(
            RssSourceMatcher.feedIdentityMatches(
                rssFeedUrl = "https://example.com/show/feed.xml#latest",
                rssPodcastGuid = null,
                candidate = candidate,
            ),
        )
    }

    @Test
    fun `podcast GUID is an exact identity signal`() {
        val candidate =
            podcast(
                feedUrl = null,
                podcastGuid = "ABC-123",
            )

        assertTrue(
            RssSourceMatcher.feedIdentityMatches(
                rssFeedUrl = "https://other.example/feed",
                rssPodcastGuid = "abc-123",
                candidate = candidate,
            ),
        )
    }

    @Test
    fun `title-only match with conflicting authors is rejected`() {
        val candidate = podcast(title = "Daily Tech", author = "Studio One")

        assertFalse(
            RssSourceMatcher.likelySameShow(
                rssTitle = "Daily Tech",
                rssAuthor = "Another Studio",
                candidate = candidate,
            ),
        )
    }

    @Test
    fun `episode enclosure URL wins over title differences`() {
        val expected =
            episode(
                id = "-1",
                title = "Renamed episode",
                audioUrl = "https://cdn.example/ep.mp3",
                publishedDate = 100L,
            )

        assertEquals(
            expected,
            RssSourceMatcher.findMatchingEpisode(
                episodes = listOf(expected),
                title = "Original episode",
                audioUrl = "https://cdn.example/ep.mp3",
                publishedDate = null,
            ),
        )
    }

    @Test
    fun `ambiguous title without date remains unlinked`() {
        val episodes =
            listOf(
                episode("-1", "News update", "https://cdn.example/1.mp3", 100L),
                episode("-2", "News update", "https://cdn.example/2.mp3", 200L),
            )

        assertNull(
            RssSourceMatcher.findMatchingEpisode(
                episodes = episodes,
                title = "News update",
                audioUrl = null,
                publishedDate = null,
            ),
        )
    }

    private fun podcast(
        title: String = "Example Show",
        author: String = "Example Studio",
        feedUrl: String? = null,
        podcastGuid: String? = null,
    ) = PodcastEntity(
        podcastId = "123",
        title = title,
        author = author,
        imageUrl = "",
        description = null,
        feedUrl = feedUrl,
        podcastGuid = podcastGuid,
    )

    private fun episode(
        id: String,
        title: String,
        audioUrl: String,
        publishedDate: Long,
    ) = RssEpisodeEntity(
        episodeId = id,
        podcastId = "rss:test",
        guid = null,
        title = title,
        description = "",
        audioUrl = audioUrl,
        imageUrl = null,
        duration = 60,
        publishedDate = publishedDate,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = "full",
        enclosureType = "audio/mpeg",
    )
}
