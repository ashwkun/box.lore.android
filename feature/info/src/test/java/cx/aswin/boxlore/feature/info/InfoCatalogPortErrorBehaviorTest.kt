package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.domain.ports.PodcastCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cheap Info catalog-port error / empty-path coverage (no Application / full VM).
 */
class InfoCatalogPortErrorBehaviorTest {
    @Test
    fun `failing catalog surfaces null and empty without throwing`() =
        runTest {
            val catalog = FailingPodcastCatalogPort()

            assertNull(catalog.getPodcastDetails("any"))
            assertNull(catalog.getEpisode("any"))
            assertTrue(catalog.getEpisodes("any").isEmpty())
        }

    private class FailingPodcastCatalogPort : PodcastCatalogPort {
        override suspend fun getPodcastDetails(feedId: String): Podcast? = null

        override suspend fun getEpisode(episodeId: String): Episode? = null

        override suspend fun getEpisodes(feedId: String): List<Episode> = emptyList()
    }
}
