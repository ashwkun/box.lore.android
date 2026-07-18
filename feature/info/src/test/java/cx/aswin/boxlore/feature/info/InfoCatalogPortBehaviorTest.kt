package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.domain.ports.PodcastCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Behavioral coverage of the [PodcastCatalogPort] seam Info assemblers / VMs depend on.
 * Full PodcastInfo/EpisodeInfo VMs still need Android Application (deferred).
 */
class InfoCatalogPortBehaviorTest {
    @Test
    fun `catalog returns podcast details episodes and single episode`() =
        runTest {
            val podcast = TestFixtures.podcast(id = "pod-1", title = "Show One")
            val ep1 = TestFixtures.episode(id = "ep-1", podcastId = "pod-1", title = "First")
            val ep2 = TestFixtures.episode(id = "ep-2", podcastId = "pod-1", title = "Second")
            val catalog =
                FakePodcastCatalogPort(
                    podcasts = mapOf(podcast.id to podcast),
                    episodes = mapOf(ep1.id to ep1, ep2.id to ep2),
                    episodesByPodcast = mapOf(podcast.id to listOf(ep1, ep2)),
                )

            assertEquals("Show One", catalog.getPodcastDetails("pod-1")?.title)
            assertEquals(listOf("ep-1", "ep-2"), catalog.getEpisodes("pod-1").map { it.id })
            assertEquals("First", catalog.getEpisode("ep-1")?.title)
        }

    @Test
    fun `missing ids return null or empty without throwing`() =
        runTest {
            val catalog = FakePodcastCatalogPort()

            assertNull(catalog.getPodcastDetails("missing"))
            assertNull(catalog.getEpisode("missing"))
            assertTrue(catalog.getEpisodes("missing").isEmpty())
        }

    @Test
    fun `episodes for podcast ignore other podcasts catalog entries`() =
        runTest {
            val a = TestFixtures.podcast(id = "a")
            val b = TestFixtures.podcast(id = "b")
            val epA = TestFixtures.episode(id = "ep-a", podcastId = "a")
            val epB = TestFixtures.episode(id = "ep-b", podcastId = "b")
            val catalog =
                FakePodcastCatalogPort(
                    podcasts = mapOf(a.id to a, b.id to b),
                    episodes = mapOf(epA.id to epA, epB.id to epB),
                    episodesByPodcast = mapOf(a.id to listOf(epA), b.id to listOf(epB)),
                )

            assertEquals(listOf("ep-a"), catalog.getEpisodes("a").map { it.id })
            assertEquals("ep-b", catalog.getEpisode("ep-b")?.id)
            assertEquals("ep-a", catalog.getEpisode("ep-a")?.id)
        }

    private class FakePodcastCatalogPort(
        private val podcasts: Map<String, Podcast> = emptyMap(),
        private val episodes: Map<String, Episode> = emptyMap(),
        private val episodesByPodcast: Map<String, List<Episode>> = emptyMap(),
    ) : PodcastCatalogPort {
        override suspend fun getPodcastDetails(feedId: String): Podcast? = podcasts[feedId]

        override suspend fun getEpisode(episodeId: String): Episode? = episodes[episodeId]

        override suspend fun getEpisodes(feedId: String): List<Episode> = episodesByPodcast[feedId].orEmpty()
    }
}
