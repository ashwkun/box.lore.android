package cx.aswin.boxlore.core.domain.ports

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeOfflineLookupPortTest {
    @Test
    fun `fake lookup returns download and history snapshots`() =
        runTest {
            val download =
                OfflineEpisodeSnapshot(
                    podcastId = "p",
                    podcastName = "Show",
                    episodeTitle = "Ep",
                    episodeImageUrl = null,
                    episodeDescription = "d",
                    audioUrl = "/file",
                    durationMs = 1_000L,
                )
            val history = download.copy(audioUrl = "https://stream", durationMs = 2_000L)
            val port =
                FakeEpisodeOfflineLookup(
                    downloads = mapOf("ep-d" to download),
                    history = mapOf("ep-h" to history),
                )

            assertEquals("/file", port.fromDownload("ep-d")?.audioUrl)
            assertEquals("https://stream", port.fromHistory("ep-h")?.audioUrl)
            assertNull(port.fromDownload("missing"))
            assertNull(port.fromHistory("missing"))
        }

    private class FakeEpisodeOfflineLookup(
        private val downloads: Map<String, OfflineEpisodeSnapshot>,
        private val history: Map<String, OfflineEpisodeSnapshot>,
    ) : EpisodeOfflineLookupPort {
        override suspend fun fromDownload(episodeId: String): OfflineEpisodeSnapshot? = downloads[episodeId]

        override suspend fun fromHistory(episodeId: String): OfflineEpisodeSnapshot? = history[episodeId]
    }
}
