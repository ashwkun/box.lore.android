package cx.aswin.boxlore.core.ranking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.ranking.database.AdaptiveRankingDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RankingFeedbackRepositoryTest {
    private lateinit var database: AdaptiveRankingDatabase
    private lateinit var adaptive: AdaptiveRankingRepository
    private lateinit var feedback: RankingFeedbackRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AdaptiveRankingDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        adaptive = AdaptiveRankingRepository.create(context, database)
        feedback = RankingFeedbackRepository.create(adaptive)
        LearningEventLog.configure(false)
        LearningEventLog.clear()
    }

    @After
    fun tearDown() {
        database.close()
        LearningEventLog.configure(false)
        RankingShadowDiagnostics.clear()
    }

    private fun exposureFor(
        episodeId: String,
        podcastId: String = "pod-1",
    ) = RankingExposure(
        episodeId = episodeId,
        podcastId = podcastId,
        objective = RankingObjective.DISCOVERY,
        surface = RankingSurface.HOME,
        source = CandidateSource.SERVER_RECOMMENDATION,
        features = CandidateFeatureBuilder.build(CandidateSignals()),
        online = true,
    )

    @Test
    fun recordExposureDelegatesToAdaptiveRepository() =
        runTest {
            val id = feedback.recordExposure(exposureFor("ep-1"))

            assertTrue(id.isNotBlank())
            assertEquals(1, database.adaptiveRankingDao().getAllExposures().size)
        }

    @Test
    fun recordExposureWithNullRepositoryReturnsEmpty() =
        runTest {
            val orphan = RankingFeedbackRepository.create(null)

            assertEquals("", orphan.recordExposure(exposureFor("ep-1")))
        }

    @Test
    fun recordActionTerminalResolvesExposureAndLearnsFacets() =
        runTest {
            feedback.recordExposure(exposureFor("ep-1", podcastId = "pod-7"))

            feedback.recordAction(
                target =
                    FeedbackTarget(
                        episodeId = "ep-1",
                        podcastId = "pod-7",
                        genre = "Science",
                        source = CandidateSource.SERVER_RECOMMENDATION,
                    ),
                action = RankingAction.LIKE,
            )

            val resolved = database.adaptiveRankingDao().getAllExposures().count { it.resolvedAt != null }
            assertEquals(1, resolved)
            assertTrue(adaptive.facetAffinity(PreferenceFacetType.SHOW, "pod-7") > 0.0)
            assertTrue(adaptive.genreAffinities().containsKey("Science"))
            assertTrue(adaptive.facetAffinity(PreferenceFacetType.SOURCE, CandidateSource.SERVER_RECOMMENDATION.name) > 0.0)
        }

    @Test
    fun recordActionNonTerminalDoesNotResolveExposure() =
        runTest {
            feedback.recordExposure(exposureFor("ep-2", podcastId = "pod-3"))

            feedback.recordAction(
                target = FeedbackTarget(episodeId = "ep-2", podcastId = "pod-3"),
                action = RankingAction.OPEN_DETAILS,
            )

            assertEquals(0, database.adaptiveRankingDao().getAllExposures().count { it.resolvedAt != null })
            assertTrue(adaptive.facetAffinity(PreferenceFacetType.SHOW, "pod-3") > 0.0)
        }

    @Test
    fun recordActionDeduplicatesRepeatWithinWindow() =
        runTest {
            LearningEventLog.configure(true)

            val target = FeedbackTarget(episodeId = "ep-dup", podcastId = "pod-1")
            feedback.recordAction(target, RankingAction.LIKE)
            feedback.recordAction(target, RankingAction.LIKE)

            val events = LearningEventLog.events.value
            assertTrue(events.any { it is LearningEvent.DuplicateIgnored })
        }

    @Test
    fun recordPlaybackMeaningfulPlayLearnsPositiveFacet() =
        runTest {
            feedback.recordPlayback(
                target = FeedbackTarget(episodeId = "ep-3", podcastId = "pod-5", genre = "News"),
                listenSeconds = 120,
                durationSeconds = 600,
                completed = false,
                earlySkip = false,
            )

            assertTrue(adaptive.facetAffinity(PreferenceFacetType.SHOW, "pod-5") > 0.0)
            assertTrue(adaptive.genreAffinities().containsKey("News"))
        }

    @Test
    fun recordPlaybackEarlySkipLearnsNegativeFacet() =
        runTest {
            feedback.recordPlayback(
                target = FeedbackTarget(episodeId = "ep-4", podcastId = "pod-6"),
                listenSeconds = 5,
                durationSeconds = 600,
                completed = false,
                earlySkip = true,
            )

            assertTrue(adaptive.facetAffinity(PreferenceFacetType.SHOW, "pod-6") < 0.0)
        }

    @Test
    fun recordPlaybackWithNoQualifyingSignalsIsNoOp() =
        runTest {
            feedback.recordExposure(exposureFor("ep-5", podcastId = "pod-8"))

            feedback.recordPlayback(
                target = FeedbackTarget(episodeId = "ep-5", podcastId = "pod-8"),
                listenSeconds = 3,
                durationSeconds = 600,
                completed = false,
                earlySkip = false,
            )

            assertEquals(0, database.adaptiveRankingDao().getAllExposures().count { it.resolvedAt != null })
            assertEquals(0.0, adaptive.facetAffinity(PreferenceFacetType.SHOW, "pod-8"), 0.0)
        }

    @Test
    fun recordPlaybackCompletedResolvesExposure() =
        runTest {
            feedback.recordExposure(exposureFor("ep-6", podcastId = "pod-9"))

            feedback.recordPlayback(
                target = FeedbackTarget(episodeId = "ep-6", podcastId = "pod-9"),
                listenSeconds = 600,
                durationSeconds = 600,
                completed = true,
                earlySkip = false,
            )

            assertEquals(1, database.adaptiveRankingDao().getAllExposures().count { it.resolvedAt != null })
        }

    @Test
    fun resetClearsStateAndReportsBackingRepository() =
        runTest {
            feedback.recordExposure(exposureFor("ep-7"))

            val result = feedback.reset()

            assertTrue(result)
            assertTrue(database.adaptiveRankingDao().getAllExposures().isEmpty())
        }

    @Test
    fun resetWithNullRepositoryReturnsFalse() =
        runTest {
            val orphan = RankingFeedbackRepository.create(null)

            assertFalse(orphan.reset())
        }
}
