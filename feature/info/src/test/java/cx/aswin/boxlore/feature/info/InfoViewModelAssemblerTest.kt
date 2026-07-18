package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.domain.ports.PodcastCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Hard-ish catalog port coverage used by Info assemblers / VMs.
 * Full EpisodeInfo/PodcastInfo VMs still need Android Application; this locks the
 * narrow catalog contract Info depends on.
 */
class InfoViewModelAssemblerTest {
    @Test
    fun `fake PodcastCatalogPort returns details and episode`() =
        runTest {
            val podcast = TestFixtures.podcast(id = "pod-9", title = "Catalog Show")
            val episode = TestFixtures.episode(id = "ep-9", podcastId = "pod-9")
            val catalog =
                FakePodcastCatalogPort(
                    podcasts = mapOf(podcast.id to podcast),
                    episodes = mapOf(episode.id to episode),
                    episodesByPodcast = mapOf(podcast.id to listOf(episode)),
                )

            assertEquals("Catalog Show", catalog.getPodcastDetails("pod-9")?.title)
            assertEquals("ep-9", catalog.getEpisode("ep-9")?.id)
            assertEquals(1, catalog.getEpisodes("pod-9").size)
            assertNull(catalog.getPodcastDetails("missing"))
        }

    private class FakePodcastCatalogPort(
        private val podcasts: Map<String, Podcast>,
        private val episodes: Map<String, Episode>,
        private val episodesByPodcast: Map<String, List<Episode>>,
    ) : PodcastCatalogPort {
        override suspend fun getPodcastDetails(feedId: String): Podcast? = podcasts[feedId]

        override suspend fun getEpisode(episodeId: String): Episode? = episodes[episodeId]

        override suspend fun getEpisodes(feedId: String): List<Episode> = episodesByPodcast[feedId].orEmpty()
    }
}
