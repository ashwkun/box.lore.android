package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreBrowseLogicTest {

    @Test
    fun `morning hour puts news vibes first`() {
        val vibes = ExploreBrowseLogic.vibesForHour(8)
        assertEquals("morning_news", vibes.first().first)
    }

    @Test
    fun `late night hour puts crime vibes first`() {
        assertEquals("true_crime_sleep", ExploreBrowseLogic.vibesForHour(1).first().first)
    }

    @Test
    fun `substring filter matches title or artist case-insensitively`() {
        val podcasts = listOf(
            TestFixtures.podcast(id = "1", title = "Alpha Show", artist = "Zed"),
            TestFixtures.podcast(id = "2", title = "Beta", artist = "Alpha Artist"),
            TestFixtures.podcast(id = "3", title = "Gamma", artist = "Other"),
        )
        val matches = ExploreBrowseLogic.filterPodcastsBySubstring("alpha", podcasts)
        assertEquals(listOf("1", "2"), matches.map { it.id })
    }

    @Test
    fun `mergeUniqueById appends only new ids`() {
        val existing = listOf(TestFixtures.podcast(id = "a"), TestFixtures.podcast(id = "b"))
        val incoming = listOf(TestFixtures.podcast(id = "b"), TestFixtures.podcast(id = "c"))
        val merged = ExploreBrowseLogic.mergeUniqueById(existing, incoming) { it.id }
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun `episodeToSearchPodcast maps podcast fields`() {
        val episode = TestFixtures.episode(
            id = "e",
            podcastId = "p",
            podcastTitle = "Show",
        ).copy(podcastArtist = "Host", podcastImageUrl = "img", podcastGenre = "News")
        val podcast = ExploreBrowseLogic.episodeToSearchPodcast(episode)
        assertEquals("p", podcast.id)
        assertEquals("Show", podcast.title)
        assertEquals("Host", podcast.artist)
        assertEquals("img", podcast.imageUrl)
        assertEquals("News", podcast.genre)
        assertTrue(podcast.latestEpisode === episode)
    }
}
