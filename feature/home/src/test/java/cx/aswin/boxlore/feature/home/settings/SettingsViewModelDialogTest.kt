package cx.aswin.boxlore.feature.home.settings

import app.cash.turbine.test
import cx.aswin.boxlore.core.domain.ports.RankingResetPort
import cx.aswin.boxlore.core.domain.ports.RssSubscriptionPort
import cx.aswin.boxlore.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class SettingsViewModelDialogTest {
    @Test
    fun `openAddRssDialog sets show flag`() {
        val vm = SettingsViewModelAssembler.create(FakePorts.rss(), FakePorts.ranking())

        vm.openAddRssDialog()

        assertTrue(vm.uiState.value.showAddRssDialog)
    }

    @Test
    fun `dismissAddRssDialog clears dialog and error`() {
        val vm = SettingsViewModelAssembler.create(FakePorts.rss(), FakePorts.ranking())
        vm.openAddRssDialog()
        vm.onRssUrlChange("https://example.com/feed.xml")

        vm.dismissAddRssDialog()

        val state = vm.uiState.value
        assertFalse(state.showAddRssDialog)
        assertNull(state.rssError)
    }

    @Test
    fun `dismissAddRssDialog is ignored while adding`() {
        val vm = SettingsViewModelAssembler.create(FakePorts.rss(), FakePorts.ranking())
        vm.openAddRssDialog()
        vm.onRssUrlChange("https://example.com/feed.xml")
        vm.addSubscription()

        vm.dismissAddRssDialog()

        assertTrue(vm.uiState.value.showAddRssDialog)
        assertTrue(vm.uiState.value.isAddingRss)
    }

    @Test
    fun `onRssUrlChange clears prior error`() {
        val vm = SettingsViewModelAssembler.create(FakePorts.rss(), FakePorts.ranking())
        vm.openAddRssDialog()
        vm.onRssUrlChange("bad")

        vm.onRssUrlChange("https://fixed.example/feed.xml")

        assertNull(vm.uiState.value.rssError)
        assertEquals("https://fixed.example/feed.xml", vm.uiState.value.rssUrl)
    }

    @Test
    fun `keepRssMatchSeparate is no-op without pending match`() =
        runTest {
            val vm = SettingsViewModelAssembler.create(FakePorts.rss(), FakePorts.ranking())

            vm.events.test {
                vm.keepRssMatchSeparate()
                expectNoEvents()
            }
        }

    private object FakePorts {
        fun rss(): RssSubscriptionPort =
            object : RssSubscriptionPort {
                override suspend fun addSubscription(rawUrl: String): cx.aswin.boxlore.core.domain.RssSubscriptionResult {
                    kotlinx.coroutines.delay(Long.MAX_VALUE)
                    error("unreachable")
                }

                override suspend fun confirmPodcastIndexLink(
                    rssPodcastId: String,
                    podcastIndexId: String,
                ): cx.aswin.boxlore.core.model.Podcast =
                    cx.aswin.boxlore.core.testing.TestFixtures.podcast()
            }

        fun ranking(): RankingResetPort =
            object : RankingResetPort {
                override suspend fun reset(): Boolean = true
            }
    }
}
