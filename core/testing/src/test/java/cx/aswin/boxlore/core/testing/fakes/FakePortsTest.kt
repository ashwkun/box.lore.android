package cx.aswin.boxlore.core.testing.fakes

import cx.aswin.boxlore.core.domain.ports.OfflineEpisodeSnapshot
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakePortsTest {
    @Test
    fun `FakeLocalCatalogPort stores and links podcasts`() =
        runTest {
            val fake = FakeLocalCatalogPort()
            val podcast = TestFixtures.podcast(id = "p1")
            fake.put(podcast)
            assertEquals(podcast, fake.getLocalPodcast("p1"))
            assertNull(fake.getLocalPodcast("missing"))

            val rss = TestFixtures.rssPodcast()
            fake.linkRss("pi-1", rss)
            assertEquals(rss, fake.getSubscribedRssLinkedTo("pi-1"))

            fake.upsertSubscribedPodcast(podcast.copy(title = "Updated"))
            assertEquals(1, fake.upsertCalls)
            assertEquals("Updated", fake.getLocalPodcast("p1")?.title)
        }

    @Test
    fun `FakePodcastCatalogPort tracks calls and errors`() =
        runTest {
            val fake = FakePodcastCatalogPort()
            assertEquals(TestFixtures.podcast().id, fake.getPodcastDetails("feed")?.id)
            assertEquals(1, fake.detailsCalls)
            assertEquals("feed", fake.lastDetailsId)

            fake.detailsError = IllegalStateException("boom")
            try {
                fake.getPodcastDetails("x")
                error("expected")
            } catch (e: IllegalStateException) {
                assertEquals("boom", e.message)
            }
        }

    @Test
    fun `FakeRssSubscriptionPort and ranking reset`() =
        runTest {
            val rss = FakeRssSubscriptionPort()
            val result = rss.addSubscription("https://example.com/feed.xml")
            assertEquals(1, rss.addCalls)
            assertTrue(result.episodeCount >= 0)

            val ranking = FakeRankingResetPort(result = false)
            assertFalse(ranking.reset())
            assertEquals(1, ranking.resetCalls)
        }

    @Test
    fun `FakeHistoryRecommendationSource respects limit`() =
        runTest {
            val source =
                FakeHistoryRecommendationSource(
                    items =
                        listOf(
                            TestFixtures.historyItem(episodeId = "e1"),
                            TestFixtures.historyItem(episodeId = "e2"),
                            TestFixtures.historyItem(episodeId = "e3"),
                        ),
                )
            assertEquals(2, source.getHistoryForRecommendations(2).size)
            assertEquals(2, source.lastLimit)
        }

    @Test
    fun `FakeEpisodeOfflineLookup and connectivity`() =
        runTest {
            val snapshot =
                OfflineEpisodeSnapshot(
                    podcastId = "p1",
                    podcastName = "P",
                    episodeTitle = "E",
                    episodeImageUrl = null,
                    episodeDescription = null,
                    audioUrl = "file:///tmp/a.mp3",
                    durationMs = 1000L,
                )
            val offline = FakeEpisodeOfflineLookup(fromDownload = snapshot)
            assertEquals(snapshot, offline.fromDownload("e1"))
            assertNull(offline.fromHistory("e1"))

            val connectivity = FakeConnectivityStatusPort(online = false)
            assertFalse(connectivity.isOnline())
            assertEquals(1, connectivity.calls)
        }

    @Test
    fun `TestFixtures rss helpers use stable identities`() {
        val rss = TestFixtures.rssPodcast()
        assertTrue(rss.id.startsWith("-") || rss.id.toLongOrNull()?.let { it < 0 } == true)
        val ep = TestFixtures.rssEpisode()
        assertTrue(ep.id.startsWith("rss:") || (ep.podcastId?.startsWith("-") == true))
    }
}
