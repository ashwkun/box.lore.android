package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HomeMappingLogicTest {
    @Test
    fun `to recommendation podcast maps episode metadata`() {
        val episode =
            TestFixtures
                .episode(
                    id = "ep-1",
                    podcastId = "pod-1",
                    podcastTitle = "Show",
                ).copy(
                    podcastArtist = "Artist",
                    podcastImageUrl = "https://example.com/podcast.jpg",
                    imageUrl = "https://example.com/episode.jpg",
                )

        val podcast = episode.toRecommendationPodcast()

        assertEquals("pod-1", podcast.id)
        assertEquals("Show", podcast.title)
        assertEquals("Artist", podcast.artist)
        assertEquals("https://example.com/podcast.jpg", podcast.imageUrl)
        assertSame(episode, podcast.latestEpisode)
    }

    @Test
    fun `to recommendation podcast falls back to episode image when podcast image missing`() {
        val episode =
            TestFixtures.episode(id = "ep-2").copy(
                podcastImageUrl = null,
                imageUrl = "https://example.com/fallback.jpg",
            )

        val podcast = episode.toRecommendationPodcast()

        assertEquals("https://example.com/fallback.jpg", podcast.imageUrl)
    }

    @Test
    fun `to recommendation podcast uses empty strings for missing ids`() {
        val episode = TestFixtures.episode().copy(podcastId = null, podcastTitle = null, podcastArtist = null)

        val podcast = episode.toRecommendationPodcast()

        assertEquals("", podcast.id)
        assertEquals("", podcast.title)
        assertEquals("", podcast.artist)
    }
}
