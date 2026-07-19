package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeCatchUpLogicTest {
    @Test
    fun `resolve fresh episode uses serial map for oldest sort`() {
        val serial = TestFixtures.episode(id = "serial-ep")
        val latest = TestFixtures.episode(id = "latest-ep")
        val pod =
            TestFixtures.podcast(id = "pod-1").copy(
                preferredSort = "oldest",
                latestEpisode = latest,
            )

        assertEquals("serial-ep", HomeCatchUpLogic.resolveFreshEpisode(pod, mapOf("pod-1" to serial))?.id)
        assertEquals("latest-ep", HomeCatchUpLogic.resolveFreshEpisode(pod.copy(preferredSort = "newest"), mapOf("pod-1" to serial))?.id)
        assertNull(HomeCatchUpLogic.resolveFreshEpisode(pod.copy(latestEpisode = null), emptyMap()))
    }

    @Test
    fun `build catch up buckets splits unplayed in progress and completed`() {
        val unplayedEp = TestFixtures.episode(id = "ep-u", podcastId = "u")
        val progressEp = TestFixtures.episode(id = "ep-p", podcastId = "p")
        val doneEp = TestFixtures.episode(id = "ep-d", podcastId = "d")
        val subs =
            listOf(
                TestFixtures.podcast(id = "u", title = "U").copy(latestEpisode = unplayedEp),
                TestFixtures.podcast(id = "p", title = "P").copy(latestEpisode = progressEp),
                TestFixtures.podcast(id = "d", title = "D").copy(latestEpisode = doneEp),
            )
        val history =
            listOf(
                history(episodeId = "ep-p", podcastId = "p", progressMs = 10_000L, durationMs = 100_000L, completed = false),
                history(episodeId = "ep-d", podcastId = "d", progressMs = 100_000L, durationMs = 100_000L, completed = true),
            )

        val buckets = HomeCatchUpLogic.buildCatchUpBuckets(subs, history, resolvedSerial = emptyMap())

        assertEquals(listOf("u"), buckets.unplayed.map { it.id })
        assertEquals(EpisodeStatus.UNPLAYED, buckets.unplayed.single().episodeStatus)
        assertEquals(listOf("p"), buckets.inProgress.map { it.first.id })
        assertEquals(
            0.1f,
            buckets.inProgress
                .single()
                .first.resumeProgress,
        )
        assertEquals(listOf("d"), buckets.completed.map { it.first.id })
        assertEquals(
            EpisodeStatus.COMPLETED,
            buckets.completed
                .single()
                .first.episodeStatus,
        )
    }

    private fun history(
        episodeId: String,
        podcastId: String,
        progressMs: Long,
        durationMs: Long,
        completed: Boolean,
    ) = HomeListeningHistoryItem(
        episodeId = episodeId,
        podcastId = podcastId,
        podcastName = "Show",
        podcastImageUrl = null,
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = completed,
        isLiked = false,
        lastPlayedAt = 1L,
    )
}
