package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RssRepositoryHelpersTest {
    @Test
    fun escapeForSqlLikeEscapesWildcardsAndBackslash() {
        assertEquals("100\\% done", "100% done".escapeForSqlLike())
        assertEquals("a\\_b", "a_b".escapeForSqlLike())
        assertEquals("path\\\\to", "path\\to".escapeForSqlLike())
        assertEquals("plain", "plain".escapeForSqlLike())
    }

    @Test
    fun likelySameShowAllowsBlankAuthorOnEitherSide() {
        val candidate = podcast(title = "Daily Tech", author = "")

        assertTrue(
            RssSourceMatcher.likelySameShow(
                rssTitle = "Daily Tech",
                rssAuthor = "Any Studio",
                candidate = candidate,
            ),
        )
    }

    @Test
    fun likelySameShowRejectsDifferentTitles() {
        val candidate = podcast(title = "Daily Tech", author = "Studio")

        assertFalse(
            RssSourceMatcher.likelySameShow(
                rssTitle = "Weekly Tech",
                rssAuthor = "Studio",
                candidate = candidate,
            ),
        )
    }

    @Test
    fun feedIdentityDoesNotMatchWhenNeitherUrlNorGuidAlign() {
        val candidate = podcast(feedUrl = "https://a.example/feed", podcastGuid = "guid-a")

        assertFalse(
            RssSourceMatcher.feedIdentityMatches(
                rssFeedUrl = "https://b.example/feed",
                rssPodcastGuid = "guid-b",
                candidate = candidate,
            ),
        )
    }

    @Test
    fun findMatchingEpisodeUsesDateWhenTitlesAmbiguous() {
        val episodes =
            listOf(
                episode("-1", "News update", "https://cdn.example/1.mp3", 1_000L),
                episode("-2", "News update", "https://cdn.example/2.mp3", 90_000L),
            )

        val match =
            RssSourceMatcher.findMatchingEpisode(
                episodes = episodes,
                title = "News update",
                audioUrl = null,
                publishedDate = 90_050L,
            )

        assertSame(episodes[1], match)
    }

    @Test
    fun findMatchingEpisodeReturnsNullWhenDateTooFarApart() {
        val episodes =
            listOf(
                episode("-1", "News update", "https://cdn.example/1.mp3", 1_000L),
                episode("-2", "News update", "https://cdn.example/2.mp3", 2_000L),
            )

        assertNull(
            RssSourceMatcher.findMatchingEpisode(
                episodes = episodes,
                title = "News update",
                audioUrl = null,
                publishedDate = 5_000_000L,
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
