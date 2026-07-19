package cx.aswin.boxlore.core.rss

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Parse coverage for [RssFeedClient] driven by hermetic XML fixtures under `src/test/resources`.
 *
 * These exercise the pure-JVM library parser path. The Android `Xml` custom parser is unavailable
 * off-device and fails softly, so the merged result reflects the library parse.
 */
class RssFeedClientParseTest {

    private val client = RssFeedClient()
    private val feedUrl = "https://example.com/feed.xml"

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/$name")) { "missing fixture $name" }
            .readBytes()

    @Test
    fun parsesStandardFeedMetadataAndEpisodes() = runTest {
        val parsed = client.parse(feedUrl, fixture("feed_standard.xml"))

        assertEquals("Test Standard Podcast", parsed.title)
        assertEquals("Test Author", parsed.author)
        assertEquals("episodic", parsed.podcastType)
        assertEquals("Technology", parsed.genre)
        assertEquals("https://example.com/channel-art.jpg", parsed.imageUrl)
        assertEquals(2, parsed.episodes.size)
    }

    @Test
    fun episodesUseNegativeRssIdsAndAreSortedNewestFirst() = runTest {
        val parsed = client.parse(feedUrl, fixture("feed_standard.xml"))

        assertEquals(setOf("Episode One", "Episode Two"), parsed.episodes.map { it.title }.toSet())
        parsed.episodes.forEach { episode ->
            assertTrue(episode.episodeId.toLong() < 0L, "episode id must be negative")
            assertTrue(episode.podcastId.startsWith("rss:"))
        }
        // Feed order is newest-first: published dates are non-increasing.
        val dates = parsed.episodes.map { it.publishedDate }
        assertEquals(dates.sortedDescending(), dates)
    }

    @Test
    fun parsesDurationsInClockAndSecondsForms() = runTest {
        val parsed = client.parse(feedUrl, fixture("feed_standard.xml"))
        val byTitle = parsed.episodes.associateBy { it.title }

        assertEquals(1800, byTitle.getValue("Episode One").duration)
        assertEquals(3600, byTitle.getValue("Episode Two").duration)
    }

    @Test
    fun parsesItunesSeasonAndEpisodeNumbers() = runTest {
        val parsed = client.parse(feedUrl, fixture("feed_standard.xml"))
        val episodeOne = parsed.episodes.single { it.title == "Episode One" }

        assertEquals(1, episodeOne.seasonNumber)
        assertEquals(1, episodeOne.episodeNumber)
        assertEquals("full", episodeOne.episodeType)
    }

    @Test
    fun skipsItemsWithoutPlayableMedia() = runTest {
        val parsed = client.parse(feedUrl, fixture("feed_mixed_playable.xml"))

        assertEquals(listOf("Playable Episode"), parsed.episodes.map { it.title })
    }

    @Test
    fun feedWithoutPlayableEpisodesFailsToParse() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                client.parse(feedUrl, fixture("feed_no_playable.xml"))
            }
        }
    }

    @Test
    fun feedWithoutTitleFailsToParse() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                client.parse(feedUrl, fixture("feed_no_title.xml"))
            }
        }
    }

    @Test
    fun parseDerivesRssPodcastIdWhenNotProvided() = runTest {
        val parsed = client.parse(feedUrl, fixture("feed_standard.xml"))
        val expectedPodcastId = RssIdGenerator.podcastId(feedUrl)

        assertTrue(parsed.episodes.all { it.podcastId == expectedPodcastId })
    }
}
