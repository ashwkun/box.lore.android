package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.domain.ports.OfflineEpisodeSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeOfflineMergeLogicTest {
    private val emptySeed =
        EpisodeOfflineMergeLogic.EpisodeFieldSeed(
            podcastId = "",
            podcastTitle = "Podcast",
            episodeTitle = "",
            episodeImageUrl = "",
            episodeDescription = "",
            episodeAudioUrl = "",
            episodeDurationSec = 0,
        )

    @Test
    fun `null snapshot leaves seed unchanged`() {
        assertEquals(emptySeed, EpisodeOfflineMergeLogic.merge(emptySeed, null))
    }

    @Test
    fun `download snapshot fills missing fields and replaces generic podcast title`() {
        val snapshot =
            OfflineEpisodeSnapshot(
                podcastId = "pod-1",
                podcastName = "Show",
                episodeTitle = "Ep",
                episodeImageUrl = "img",
                episodeDescription = "desc",
                audioUrl = "/local/path",
                durationMs = 125_000L,
            )
        val merged = EpisodeOfflineMergeLogic.merge(emptySeed, snapshot)
        assertEquals("pod-1", merged.podcastId)
        assertEquals("Show", merged.podcastTitle)
        assertEquals("Ep", merged.episodeTitle)
        assertEquals("img", merged.episodeImageUrl)
        assertEquals("desc", merged.episodeDescription)
        assertEquals("/local/path", merged.episodeAudioUrl)
        assertEquals(125, merged.episodeDurationSec)
    }

    @Test
    fun `existing non-empty fields win over snapshot`() {
        val seed =
            EpisodeOfflineMergeLogic.EpisodeFieldSeed(
                podcastId = "keep-pod",
                podcastTitle = "Keep Title",
                episodeTitle = "Keep Ep",
                episodeImageUrl = "keep-img",
                episodeDescription = "keep-desc",
                episodeAudioUrl = "https://keep",
                episodeDurationSec = 40,
            )
        val snapshot =
            OfflineEpisodeSnapshot(
                podcastId = "other",
                podcastName = "Other",
                episodeTitle = "Other Ep",
                episodeImageUrl = "other-img",
                episodeDescription = "other-desc",
                audioUrl = "/other",
                durationMs = 999_000L,
            )
        assertEquals(seed, EpisodeOfflineMergeLogic.merge(seed, snapshot))
    }
}
