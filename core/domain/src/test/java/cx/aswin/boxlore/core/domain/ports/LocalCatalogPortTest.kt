package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalCatalogPortTest {
    @Test
    fun `fake local catalog returns stored podcasts and linked rss`() =
        runTest {
            val local = TestFixtures.podcast(id = "42", title = "Index")
            val rss =
                TestFixtures
                    .podcast(id = "rss:1", title = "RSS")
                    .copy(linkedPodcastIndexId = "42")
            val port =
                FakeLocalCatalogPort(
                    byId = mapOf(local.id to local, rss.id to rss),
                    linkedRssByIndexId = mapOf("42" to rss),
                )

            assertEquals("Index", port.getLocalPodcast("42")?.title)
            assertEquals("rss:1", port.getSubscribedRssLinkedTo("42")?.id)
            assertNull(port.getLocalPodcast("missing"))
            assertNull(port.getSubscribedRssLinkedTo("99"))
        }

    @Test
    fun `upsert records podcast for later get`() =
        runTest {
            val port = FakeLocalCatalogPort()
            val podcast = TestFixtures.podcast(id = "p1", title = "Saved")
            port.upsertSubscribedPodcast(podcast)
            assertEquals("Saved", port.getLocalPodcast("p1")?.title)
            assertEquals(1, port.upsertCalls)
        }

    private class FakeLocalCatalogPort(
        byId: Map<String, Podcast> = emptyMap(),
        private val linkedRssByIndexId: Map<String, Podcast> = emptyMap(),
    ) : LocalCatalogPort {
        private val store = byId.toMutableMap()
        var upsertCalls = 0
            private set

        override suspend fun getLocalPodcast(id: String): Podcast? = store[id]

        override suspend fun getSubscribedRssLinkedTo(podcastIndexId: String): Podcast? = linkedRssByIndexId[podcastIndexId]

        override suspend fun upsertSubscribedPodcast(podcast: Podcast) {
            upsertCalls++
            store[podcast.id] = podcast
        }
    }
}
