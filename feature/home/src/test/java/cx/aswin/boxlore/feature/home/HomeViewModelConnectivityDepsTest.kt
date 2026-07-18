package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.domain.ports.AlwaysOnlineConnectivity
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Verifies Home adaptive `isOnline` wiring defaults and override (Phase 2 PR3).
 * Full HomeViewModel still deferred (Application + heavy repos).
 */
class HomeViewModelConnectivityDepsTest {
    @Test
    fun `deps default connectivity is always online`() {
        val deps = sampleDeps()
        assertSame(AlwaysOnlineConnectivity, deps.connectivityStatus)
        assertTrue(deps.connectivityStatus.isOnline())
    }

    @Test
    fun `deps accept offline connectivity double`() {
        val offline = ConnectivityStatusPort { false }
        val deps = sampleDeps(connectivityStatus = offline)
        assertFalse(deps.connectivityStatus.isOnline())
    }

    private fun sampleDeps(connectivityStatus: ConnectivityStatusPort = AlwaysOnlineConnectivity): HomeViewModelDeps =
        HomeViewModelDeps(
            podcastRepository = mock(),
            playbackRepository = mock(),
            engagementCoordinator = mock(),
            subscriptionRepository = mock(),
            downloadRepository = mock(),
            rssRepository = mock(),
            adaptiveRankingRepository = mock(),
            adaptiveScorer = mock(),
            rankingFeedback = mock(),
            localCatalog = mock(),
            userPreferencesRepository = mock(),
            connectivityStatus = connectivityStatus,
        )
}
