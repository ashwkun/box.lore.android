package cx.aswin.boxlore.feature.home.settings

import app.cash.turbine.test
import cx.aswin.boxlore.core.domain.RssSubscriptionResult
import cx.aswin.boxlore.core.domain.ports.RankingResetPort
import cx.aswin.boxlore.core.domain.ports.RssSubscriptionPort
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.MainDispatcherExtension
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException

@ExtendWith(MainDispatcherExtension::class)
class SettingsViewModelTest {
    @Test
    fun `addSubscription success without match closes dialog and toasts`() =
        runTest {
            val podcast = TestFixtures.podcast(id = "rss:1", title = "Feed A")
            val rss =
                FakeRssSubscriptionPort(
                    result =
                        RssSubscriptionResult(
                            podcast = podcast,
                            episodeCount = 12,
                            automaticUpdateChecksSupported = true,
                        ),
                )
            val vm = SettingsViewModelAssembler.create(rss, FakeRankingResetPort())

            vm.events.test {
                vm.onRssUrlChange("https://example.com/feed.xml")
                vm.openAddRssDialog()
                vm.addSubscription()

                val toast = awaitItem() as SettingsEvent.ShowToast
                assertTrue(toast.message.contains("Feed A"))
                assertTrue(toast.message.contains("12"))
                cancelAndIgnoreRemainingEvents()
            }

            val state = vm.uiState.value
            assertFalse(state.showAddRssDialog)
            assertFalse(state.isAddingRss)
            assertNull(state.pendingRssMatch)
            assertEquals("", state.rssUrl)
            assertEquals(1, rss.addCalls)
        }

    @Test
    fun `addSubscription with Podcast Index match keeps pending confirmation`() =
        runTest {
            val rssPodcast = TestFixtures.podcast(id = "rss:2", title = "RSS Show")
            val indexMatch = TestFixtures.podcast(id = "99", title = "Index Show")
            val rss =
                FakeRssSubscriptionPort(
                    result =
                        RssSubscriptionResult(
                            podcast = rssPodcast,
                            episodeCount = 3,
                            automaticUpdateChecksSupported = true,
                            potentialPodcastIndexMatch = indexMatch,
                        ),
                )
            val vm = SettingsViewModelAssembler.create(rss, FakeRankingResetPort())

            vm.onRssUrlChange("https://example.com/feed.xml")
            vm.openAddRssDialog()
            vm.addSubscription()

            val state = vm.uiState.value
            assertFalse(state.showAddRssDialog)
            assertEquals(rssPodcast.id, state.pendingRssMatch?.podcast?.id)
            assertEquals(indexMatch.id, state.pendingRssMatch?.potentialPodcastIndexMatch?.id)
        }

    @Test
    fun `addSubscription maps IOException to friendly error`() =
        runTest {
            val rss = FakeRssSubscriptionPort(error = IOException("offline"))
            val vm = SettingsViewModelAssembler.create(rss, FakeRankingResetPort())

            vm.onRssUrlChange("https://example.com/feed.xml")
            vm.openAddRssDialog()
            vm.addSubscription()

            val state = vm.uiState.value
            assertTrue(state.showAddRssDialog)
            assertFalse(state.isAddingRss)
            assertEquals(
                "The RSS feed could not be downloaded. Check your connection and try again.",
                state.rssError,
            )
        }

    @Test
    fun `confirmPodcastIndexLink clears pending and toasts`() =
        runTest {
            val rssPodcast = TestFixtures.podcast(id = "rss:3", title = "Linked Show")
            val indexMatch = TestFixtures.podcast(id = "42", title = "Index")
            val pending =
                RssSubscriptionResult(
                    podcast = rssPodcast,
                    episodeCount = 1,
                    automaticUpdateChecksSupported = true,
                    potentialPodcastIndexMatch = indexMatch,
                )
            val rss = FakeRssSubscriptionPort(result = pending)
            val vm = SettingsViewModelAssembler.create(rss, FakeRankingResetPort())

            // Seed pending match via successful add
            vm.onRssUrlChange("https://example.com/feed.xml")
            vm.addSubscription()

            vm.events.test {
                vm.confirmPodcastIndexLink()
                val toast = awaitItem() as SettingsEvent.ShowToast
                assertTrue(toast.message.contains("Linked Show"))
                cancelAndIgnoreRemainingEvents()
            }

            assertNull(vm.uiState.value.pendingRssMatch)
            assertEquals(1, rss.confirmCalls)
            assertEquals("rss:3", rss.lastConfirmRssId)
            assertEquals("42", rss.lastConfirmIndexId)
        }

    @Test
    fun `resetRecommendations emits success toast when port returns true`() =
        runTest {
            val ranking = FakeRankingResetPort(result = true)
            val vm = SettingsViewModelAssembler.create(FakeRssSubscriptionPort(), ranking)

            vm.events.test {
                vm.resetRecommendations()
                assertEquals(
                    SettingsEvent.ShowToast("Recommendations reset"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, ranking.resetCalls)
        }

    @Test
    fun `resetRecommendations emits failure toast when port returns false`() =
        runTest {
            val ranking = FakeRankingResetPort(result = false)
            val vm = SettingsViewModelAssembler.create(FakeRssSubscriptionPort(), ranking)

            vm.events.test {
                vm.resetRecommendations()
                assertEquals(
                    SettingsEvent.ShowToast("Couldn't reset recommendations"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, ranking.resetCalls)
        }

    @Test
    fun `assembler factory creates SettingsViewModel with default state`() {
        val factory =
            SettingsViewModelAssembler.factory(
                FakeRssSubscriptionPort(),
                FakeRankingResetPort(),
            )
        val vm = factory.create(SettingsViewModel::class.java)
        assertEquals("", vm.uiState.value.rssUrl)
        assertFalse(vm.uiState.value.showAddRssDialog)
    }

    @Test
    fun `keepRssMatchSeparate clears pending match`() =
        runTest {
            val rssPodcast = TestFixtures.podcast(id = "rss:4", title = "Separate")
            val indexMatch = TestFixtures.podcast(id = "7", title = "Other")
            val rss =
                FakeRssSubscriptionPort(
                    result =
                        RssSubscriptionResult(
                            podcast = rssPodcast,
                            episodeCount = 1,
                            automaticUpdateChecksSupported = false,
                            potentialPodcastIndexMatch = indexMatch,
                        ),
                )
            val vm = SettingsViewModelAssembler.create(rss, FakeRankingResetPort())
            vm.addSubscription()

            vm.events.test {
                vm.keepRssMatchSeparate()
                assertEquals(
                    SettingsEvent.ShowToast("Kept both subscriptions separate."),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(vm.uiState.value.pendingRssMatch)
        }

    private class FakeRssSubscriptionPort(
        private val result: RssSubscriptionResult =
            RssSubscriptionResult(
                podcast = TestFixtures.podcast(),
                episodeCount = 0,
                automaticUpdateChecksSupported = true,
            ),
        private val error: Exception? = null,
    ) : RssSubscriptionPort {
        var addCalls = 0
        var confirmCalls = 0
        var lastConfirmRssId: String? = null
        var lastConfirmIndexId: String? = null

        override suspend fun addSubscription(rawUrl: String): RssSubscriptionResult {
            addCalls++
            error?.let { throw it }
            return result
        }

        override suspend fun confirmPodcastIndexLink(
            rssPodcastId: String,
            podcastIndexId: String,
        ): Podcast {
            confirmCalls++
            lastConfirmRssId = rssPodcastId
            lastConfirmIndexId = podcastIndexId
            return result.podcast
        }
    }

    private class FakeRankingResetPort(
        private val result: Boolean = true,
    ) : RankingResetPort {
        var resetCalls = 0

        override suspend fun reset(): Boolean {
            resetCalls++
            return result
        }
    }
}
