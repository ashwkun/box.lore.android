package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.data.SharedAppDependencies
import cx.aswin.boxlore.core.data.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM worker tests via fake [SharedAppDependenciesHolder].
 * When smart downloads are disabled the worker returns success without touching downloads deps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartDownloadWorkerTest {

    private lateinit var context: Context
    private lateinit var userPrefs: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        userPrefs = UserPreferencesRepository(context)
        SharedAppDependenciesHolder.instance = null
        DownloadsDependenciesHolder.instance = null
    }

    @After
    fun tearDown() {
        SharedAppDependenciesHolder.instance = null
        DownloadsDependenciesHolder.instance = null
    }

    @Test
    fun `doWork returns success when smart downloads disabled`() {
        runBlocking { userPrefs.setSmartDownloadsEnabled(false) }
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(userPrefs)

        val worker = TestListenableWorkerBuilder<SmartDownloadWorker>(context).build()
        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns failure when enabled but downloads holder missing`() {
        runBlocking { userPrefs.setSmartDownloadsEnabled(true) }
        SharedAppDependenciesHolder.instance = FakeSharedAppDependencies(userPrefs)
        // DownloadsDependenciesHolder intentionally unset → require() throws → Result.failure()

        val worker = TestListenableWorkerBuilder<SmartDownloadWorker>(context).build()
        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    private class FakeSharedAppDependencies(
        override val userPreferencesRepository: UserPreferencesRepository,
    ) : SharedAppDependencies {
        override val database: BoxLoreDatabase get() = error("unused")
        override val podcastRepository: PodcastRepository get() = error("unused")
        override val subscriptionRepository: SubscriptionRepository get() = error("unused")
        override val rssPodcastRepository: RssPodcastRepository get() = error("unused")
        override val adaptiveCandidateScorer: AdaptiveCandidateScorer get() = error("unused")
        override val rankingFeedbackRepository: RankingFeedbackRepository get() = error("unused")
        override val adaptiveRankingRepository: AdaptiveRankingRepository get() = error("unused")
        override val rankingRuntimeControls: RankingRuntimeControls get() = error("unused")
        override val historyRecommendationSource: HistoryRecommendationSource get() = error("unused")
    }
}
