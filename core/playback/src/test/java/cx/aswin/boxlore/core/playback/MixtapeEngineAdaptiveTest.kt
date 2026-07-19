package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.ranking.database.AdaptiveRankingDatabase
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MixtapeEngineAdaptiveTest {

    private lateinit var database: AdaptiveRankingDatabase
    private lateinit var scorer: AdaptiveCandidateScorer

    private val nowMs = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("adaptive_ranking_runtime", Context.MODE_PRIVATE)
            .edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AdaptiveRankingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scorer = AdaptiveCandidateScorer.create(
            rankingRepository = cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository.create(context, database),
            runtimeControls = RankingRuntimeControls.create(context),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun adaptiveRankingReordersAndCapsCandidates() = runTest {
        val subscriptions = (1..20).map { i ->
            TestFixtures.podcast(id = "pod-$i", subscribedAt = nowMs - 10L * 24 * 60 * 60 * 1000L)
                .copy(
                    latestEpisode = TestFixtures.episode(
                        id = "ep-$i",
                        podcastId = "pod-$i",
                        publishedDate = nowMs / 1_000 - 3_600,
                    ),
                )
        }
        val result = MixtapeEngine.build(
            subscriptions = subscriptions,
            history = emptyList(),
            adaptiveRanking = MixtapeEngine.AdaptiveRanking(
                scorer = scorer,
                objective = RankingObjective.CONTINUATION,
                surface = RankingSurface.HOME,
            ),
            nowMs = nowMs,
        )
        assertTrue(result.episodes.isNotEmpty())
        assertTrue(result.episodes.size <= 15)
        assertTrue(result.episodes.map(Episode::id).distinct().size == result.episodes.size)
    }
}
