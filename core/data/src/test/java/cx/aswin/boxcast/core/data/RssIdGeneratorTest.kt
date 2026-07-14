package cx.aswin.boxcast.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssIdGeneratorTest {
    @Test
    fun episodeId_isDeterministicNegativeAndNonZero() {
        val first = RssIdGenerator.episodeId(
            feedUrl = "https://example.com/feed.xml",
            guid = "episode-guid",
            enclosureUrl = "https://cdn.example.com/episode.mp3",
            publishedDate = 1_700_000_000L,
            title = "Episode",
        )
        val second = RssIdGenerator.episodeId(
            feedUrl = "https://example.com/feed.xml",
            guid = "episode-guid",
            enclosureUrl = "https://cdn.example.com/episode.mp3",
            publishedDate = 1_700_000_000L,
            title = "Episode",
        )

        assertEquals(first, second)
        assertTrue(first.toLong() < 0L)
        assertNotEquals(0L, first.toLong())
        assertNotEquals(Long.MIN_VALUE, first.toLong())
    }

    @Test
    fun rssIdsCannotOverlapPositivePodcastIndexIds() {
        val rssId = RssIdGenerator.episodeId(
            feedUrl = "https://example.com/feed.xml",
            guid = "guid",
            enclosureUrl = null,
            publishedDate = 0L,
            title = "Episode",
        ).toLong()

        assertTrue(rssId < 0L)
        assertTrue(1L > 0L)
        assertTrue(Long.MAX_VALUE > 0L)
    }

    @Test
    fun feedNamespaceChangesEpisodeIdentity() {
        val first = RssIdGenerator.episodeId(
            feedUrl = "https://one.example/feed.xml",
            guid = "shared-guid",
            enclosureUrl = null,
            publishedDate = 0L,
            title = "Episode",
        )
        val second = RssIdGenerator.episodeId(
            feedUrl = "https://two.example/feed.xml",
            guid = "shared-guid",
            enclosureUrl = null,
            publishedDate = 0L,
            title = "Episode",
        )

        assertNotEquals(first, second)
    }
}
