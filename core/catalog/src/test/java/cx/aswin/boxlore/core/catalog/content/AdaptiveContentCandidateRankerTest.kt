package cx.aswin.boxlore.core.catalog.content

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.CandidateFeatureBuilder
import cx.aswin.boxlore.core.ranking.CandidateSignals
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.RankingExposure
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.ranking.database.AdaptiveRankingDatabase
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveContentCandidateRankerTest {
    private lateinit var database: AdaptiveRankingDatabase
    private lateinit var rankingRepository: AdaptiveRankingRepository
    private lateinit var scorer: AdaptiveCandidateScorer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("adaptive_ranking_runtime", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AdaptiveRankingDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        rankingRepository = AdaptiveRankingRepository.create(context, database)
        scorer =
            AdaptiveCandidateScorer.create(
                rankingRepository = rankingRepository,
                runtimeControls = RankingRuntimeControls.create(context),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

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

    private fun episodeCandidate(
        id: String,
        retrieval: Double,
    ): ContentCandidate =
        ContentCandidate(
            id = id,
            episode = TestFixtures.episode(id = id),
            podcast = TestFixtures.podcast(id = "pod-$id", subscribedAt = 5L),
            source = CandidateSource.SERVER_RECOMMENDATION,
            intentId = "discover",
            retrievalScore = retrieval,
        )

    private fun podcastCandidate(
        id: String,
        retrieval: Double,
    ): ContentCandidate =
        ContentCandidate(
            id = "podcast:$id",
            episode = null,
            podcast = TestFixtures.podcast(id = id, subscribedAt = 0L),
            source = CandidateSource.TRENDING,
            intentId = "discover",
            retrievalScore = retrieval,
        )

    @Test
    fun emptyCandidatesReturnEmpty() =
        runTest {
            val ranker = AdaptiveContentCandidateRanker(scorer) { emptyList() }
            assertTrue(ranker.rank(emptyList(), intent(), context()).isEmpty())
        }

    @Test
    fun mixedCandidatesAreRankedAndPreserved() =
        runTest {
            val ranker = AdaptiveContentCandidateRanker(scorer) { emptyList() }
            val candidates =
                listOf(
                    episodeCandidate("e1", 0.9),
                    episodeCandidate("e2", 0.3),
                    podcastCandidate("p1", 0.7),
                )
            val ranked = ranker.rank(candidates, intent(), context())
            assertEquals(candidates.map { it.id }.toSet(), ranked.map { it.id }.toSet())
            assertTrue(ranked.all { it.rankingScore.isFinite() })
        }

    @Test
    fun podcastOnlyCandidatesAreOrdered() =
        runTest {
            val ranker =
                AdaptiveContentCandidateRanker(scorer) {
                    listOf(
                        cx.aswin.boxlore.core.database.ListeningHistoryEntity(
                            episodeId = "h1",
                            podcastId = "p1",
                            episodeTitle = "t",
                            episodeImageUrl = null,
                            podcastImageUrl = null,
                            episodeAudioUrl = null,
                            podcastName = "n",
                            progressMs = 10_000L,
                            durationMs = 60_000L,
                            isCompleted = false,
                            lastPlayedAt = 1L,
                        ),
                    )
                }
            val candidates =
                listOf(
                    podcastCandidate("p1", 0.5),
                    podcastCandidate("p2", 0.8),
                )
            val ranked = ranker.rank(candidates, intent(), context())
            assertEquals(2, ranked.size)
        }

    @Test
    fun positiveResolvesRaiseLikedCandidateAboveEqualPriorPeer() =
        runTest {
            val likedFeatures =
                CandidateFeatureBuilder.build(CandidateSignals(showAffinity = 1.0))
            val objective = RankingObjective.YOUR_SHOWS
            repeat(60) { index ->
                val exposureId =
                    rankingRepository.recordExposure(
                        RankingExposure(
                            episodeId = "train-$index",
                            podcastId = "pod-e1",
                            objective = objective,
                            surface = RankingSurface.HOME,
                            source = CandidateSource.SERVER_RECOMMENDATION,
                            features = likedFeatures,
                            entryPoint = "home",
                            online = true,
                            shownAt = index.toLong(),
                        ),
                    )
                assertTrue(rankingRepository.resolveExposure(exposureId, reward = 1.0))
            }
            rankingRepository.updateFacet(
                cx.aswin.boxlore.core.ranking.PreferenceFacetType.SHOW,
                "pod-e1",
                reward = 1.0,
            )

            val yourShowsIntent =
                intent().copy(id = "your-shows", objective = objective, title = "Your shows")
            val ranker = AdaptiveContentCandidateRanker(scorer) { emptyList() }
            val ranked =
                ranker.rank(
                    listOf(
                        episodeCandidate("e1", 0.5),
                        episodeCandidate("e2", 0.5),
                    ),
                    yourShowsIntent,
                    context(),
                )

            assertEquals(listOf("e1", "e2"), ranked.map(ContentCandidate::id))
            assertTrue(ranked[0].rankingScore > ranked[1].rankingScore)
        }
}
