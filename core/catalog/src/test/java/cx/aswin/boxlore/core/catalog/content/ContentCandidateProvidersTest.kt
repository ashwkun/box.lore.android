package cx.aswin.boxlore.core.catalog.content

import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentCandidateProvidersTest {
    private fun intent(): ContentIntent =
        ContentIntent(
            id = "discover",
            objective = RankingObjective.DISCOVERY,
            eligibleSurfaces = setOf(RankingSurface.HOME),
            title = "Discover",
            layout = ContentLayout.PODCAST_RAIL,
        )

    private fun context(): ContentContext =
        ContentContext(
            surface = RankingSurface.HOME,
            localMinuteOfDay = 600,
            weekday = 3,
            daypart = ContentDaypart.MORNING,
            region = "us",
            isDriving = false,
            isOnline = true,
            availableMinutes = null,
            currentEpisodeId = null,
            currentPodcastId = null,
            historyMaturity = 0,
            subscriptionCount = 0,
            sessionId = "s",
        )

    @Test
    fun podcastProviderUsesLatestEpisodeIdAndMarksNovelWhenUnsubscribed() =
        runTest {
            val subscribed =
                TestFixtures
                    .podcast(id = "p1", subscribedAt = 5L)
                    .copy(latestEpisode = TestFixtures.episode(id = "ep-latest"))
            val novel = TestFixtures.podcast(id = "p2", subscribedAt = 0L)

            val provider =
                PodcastCandidateProvider(CandidateSource.TRENDING) { _, _ ->
                    listOf(subscribed, novel)
                }

            val candidates = provider.candidates(intent(), context())

            assertEquals(listOf("ep-latest", "podcast:p2"), candidates.map { it.id })
            assertFalse(candidates[0].isNovel)
            assertTrue(candidates[1].isNovel)
            // Reciprocal-rank retrieval scores decrease with position.
            assertTrue(candidates[0].retrievalScore > candidates[1].retrievalScore)
        }

    @Test
    fun episodeProviderPrefersEpisodeRetrievalScoreAndReason() =
        runTest {
            val episode =
                TestFixtures.episode(id = "e1").copy(
                    retrievalScore = 0.42,
                    recommendationReason = "because",
                )
            val podcast = TestFixtures.podcast(id = "p1", subscribedAt = 0L)

            val provider =
                EpisodeCandidateProvider(CandidateSource.SERVER_RECOMMENDATION) { _, _ ->
                    listOf(episode to podcast)
                }

            val candidates = provider.candidates(intent(), context())

            assertEquals(1, candidates.size)
            assertEquals("e1", candidates[0].id)
            assertEquals(0.42, candidates[0].retrievalScore)
            assertTrue(candidates[0].isNovel)
            assertTrue("because" in candidates[0].explanationTokens)
        }

    @Test
    fun episodeProviderFallsBackToReciprocalRankWithoutScore() =
        runTest {
            val episode = TestFixtures.episode(id = "e1")
            val podcast = TestFixtures.podcast(id = "p1", subscribedAt = 99L)

            val provider =
                EpisodeCandidateProvider(CandidateSource.TRENDING) { _, _ ->
                    listOf(episode to podcast)
                }

            val candidates = provider.candidates(intent(), context())

            assertEquals(1.0, candidates[0].retrievalScore)
            assertFalse(candidates[0].isNovel)
            assertTrue(candidates[0].explanationTokens.isEmpty())
        }

    @Test
    fun emptyLoadersProduceNoCandidates() =
        runTest {
            val podcastProvider = PodcastCandidateProvider(CandidateSource.TRENDING) { _, _ -> emptyList() }
            val episodeProvider = EpisodeCandidateProvider(CandidateSource.TRENDING) { _, _ -> emptyList() }

            assertTrue(podcastProvider.candidates(intent(), context()).isEmpty())
            assertTrue(episodeProvider.candidates(intent(), context()).isEmpty())
        }
}
