package cx.aswin.boxlore

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test for AppContainer construction-order / identity invariants
 * (documented in app/README.md). Avoids Firebase Application onCreate.
 *
 * Invariant: DB → RSS/ranking peers → PodcastRepository → QueueRepository →
 * PlaybackRepository → QueueManager → SmartDownloadManager; shared prefs
 * instance is reused when provided.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppContainerSmokeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun sharedUserPreferencesIsSameInstanceAsContainer() {
        val prefs = UserPreferencesRepository(context)
        val container = AppContainer(
            context = context,
            apiBaseUrl = "https://example.test",
            publicKey = "test-key",
            sharedUserPreferences = prefs,
        )
        assertSame(prefs, container.userPreferencesRepository)
    }

    @Test
    fun rssAndRankingPeersAreStableSingletonsWithinContainer() {
        val prefs = UserPreferencesRepository(context)
        val container = AppContainer(
            context = context,
            apiBaseUrl = "https://example.test",
            publicKey = "test-key",
            sharedUserPreferences = prefs,
        )

        val rss1 = container.rssPodcastRepository
        val rss2 = container.rssPodcastRepository
        assertSame(rss1, rss2)

        val ranking1 = container.adaptiveRankingRepository
        val ranking2 = container.adaptiveRankingRepository
        assertSame(ranking1, ranking2)

        val controls1 = container.rankingRuntimeControls
        val controls2 = container.rankingRuntimeControls
        assertSame(controls1, controls2)

        val feedback1 = container.rankingFeedbackRepository
        val feedback2 = container.rankingFeedbackRepository
        assertSame(feedback1, feedback2)
    }
}
