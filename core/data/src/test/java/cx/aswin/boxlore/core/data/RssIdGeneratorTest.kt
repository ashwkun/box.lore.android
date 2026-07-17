package cx.aswin.boxlore.core.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        // Podcast Index ids are always positive Longs (as returned by their API); RSS ids must
        // never collide with any of them, no matter which feed/episode input produced them.
        val podcastIndexSampleIds = listOf(1L, 42L, 123_456_789L, Long.MAX_VALUE)
        val rssIds = (0 until 20).map { index ->
            RssIdGenerator.episodeId(
                feedUrl = "https://example.com/feed.xml",
                guid = "guid-$index",
                enclosureUrl = null,
                publishedDate = index.toLong(),
                title = "Episode $index",
            ).toLong()
        }

        rssIds.forEach { rssId ->
            assertTrue(rssId < 0L, "RSS id $rssId must be negative")
            assertTrue(
                rssId !in podcastIndexSampleIds,
                "RSS id $rssId must never collide with a positive Podcast Index id",
            )
        }
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
