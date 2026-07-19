package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixtapeEngineTest {

    private val nowMs = 1_700_000_000_000L

    private fun episode(
        id: String,
        podcastId: String = "pod-1",
        publishedSeconds: Long = nowMs / 1_000 - 3_600,
    ): Episode = TestFixtures.episode(
        id = id,
        podcastId = podcastId,
        publishedDate = publishedSeconds,
    )

    private fun history(
        episodeId: String,
        podcastId: String = "pod-1",
        progressMs: Long = 20 * 60 * 1000L,
        durationMs: Long = 60 * 60 * 1000L,
        isCompleted: Boolean = false,
        lastPlayedAt: Long = nowMs - 60_000L,
        audioUrl: String? = "https://example.com/${episodeId}.mp3",
    ): ListeningHistoryEntity = ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode $episodeId",
        episodeImageUrl = "https://example.com/$episodeId.jpg",
        podcastImageUrl = "https://example.com/$podcastId.jpg",
        episodeAudioUrl = audioUrl,
        podcastName = "Podcast $podcastId",
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        lastPlayedAt = lastPlayedAt,
    )

    @Test
    fun emptyInputsProduceEmptyResult() = runTest {
        val result = MixtapeEngine.build(
            subscriptions = emptyList(),
            history = emptyList(),
            nowMs = nowMs,
        )
        assertTrue(result.podcasts.isEmpty())
        assertTrue(result.episodes.isEmpty())
        assertEquals(0, result.unplayedCount)
    }

    @Test
    fun inProgressHistoryBecomesResumeCandidate() = runTest {
        val result = MixtapeEngine.build(
            subscriptions = emptyList(),
            history = listOf(history(episodeId = "ep-1")),
            nowMs = nowMs,
        )
        assertEquals(1, result.episodes.size)
        assertEquals("ep-1", result.episodes.single().id)
        val podcast = result.podcasts.single()
        assertEquals(EpisodeStatus.IN_PROGRESS, podcast.episodeStatus)
        assertNotNull(podcast.resumeProgress)
        assertTrue(podcast.resumeProgress!! in 0f..1f)
        assertEquals(1, result.unplayedCount)
    }

    @Test
    fun resumeWithoutAudioUrlIsSkipped() = runTest {
        val result = MixtapeEngine.build(
            subscriptions = emptyList(),
            history = listOf(history(episodeId = "ep-1", audioUrl = null)),
            nowMs = nowMs,
        )
        assertTrue(result.episodes.isEmpty())
    }

    @Test
    fun completedOrStaleOrOutOfRangeResumesAreFilteredOut() = runTest {
        val result = MixtapeEngine.build(
            subscriptions = emptyList(),
            history = listOf(
                history(episodeId = "done", isCompleted = true),
                history(episodeId = "stale", lastPlayedAt = nowMs - 40L * 24 * 60 * 60 * 1000L),
                history(episodeId = "barely-started", progressMs = 60_000L, durationMs = 60 * 60 * 1000L),
                history(episodeId = "almost-done", progressMs = 59 * 60 * 1000L, durationMs = 60 * 60 * 1000L),
                history(episodeId = "short-remaining", progressMs = 50_000L, durationMs = 60_000L),
            ),
            nowMs = nowMs,
        )
        assertTrue(result.episodes.isEmpty())
    }

    @Test
    fun unplayedSubscriptionEpisodeBecomesCandidate() = runTest {
        val podcast = TestFixtures.podcast(id = "pod-1", subscribedAt = nowMs - 10L * 24 * 60 * 60 * 1000L)
            .copy(latestEpisode = episode(id = "ep-new", podcastId = "pod-1"))
        val result = MixtapeEngine.build(
            subscriptions = listOf(podcast),
            history = emptyList(),
            nowMs = nowMs,
        )
        assertEquals(1, result.episodes.size)
        assertEquals("ep-new", result.episodes.single().id)
        assertEquals(EpisodeStatus.UNPLAYED, result.podcasts.single().episodeStatus)
        assertNull(result.podcasts.single().resumeProgress)
    }

    @Test
    fun subscriptionWithPlayedLatestEpisodeIsSkipped() = runTest {
        val podcast = TestFixtures.podcast(id = "pod-1")
            .copy(latestEpisode = episode(id = "ep-played", podcastId = "pod-1"))
        val result = MixtapeEngine.build(
            subscriptions = listOf(podcast),
            history = listOf(history(episodeId = "ep-played", progressMs = 5_000L, durationMs = 60 * 60 * 1000L)),
            nowMs = nowMs,
        )
        assertTrue(result.episodes.none { it.id == "ep-played" })
    }

    @Test
    fun oldestPreferredSortUsesResolvedSerialEpisode() = runTest {
        val podcast = TestFixtures.podcast(id = "pod-serial", subscribedAt = nowMs - 5L * 24 * 60 * 60 * 1000L)
            .copy(preferredSort = "oldest", latestEpisode = episode(id = "latest", podcastId = "pod-serial"))
        val serial = episode(id = "serial-ep-1", podcastId = "pod-serial")
        val result = MixtapeEngine.build(
            subscriptions = listOf(podcast),
            history = emptyList(),
            resolvedSerialEpisodes = mapOf("pod-serial" to serial),
            nowMs = nowMs,
        )
        assertEquals("serial-ep-1", result.episodes.single().id)
    }

    @Test
    fun recommendationFallbacksFillWhenCandidatesAreThin() = runTest {
        val recommendations = listOf(
            episode(id = "rec-1", podcastId = "rec-pod").copy(podcastTitle = "Rec Show"),
            episode(id = "rec-2", podcastId = "rec-pod").copy(podcastTitle = "Rec Show"),
        )
        val result = MixtapeEngine.build(
            subscriptions = emptyList(),
            history = listOf(history(episodeId = "ep-1")),
            recommendations = recommendations,
            nowMs = nowMs,
        )
        assertTrue(result.episodes.any { it.id == "rec-1" })
        assertTrue(result.episodes.size >= 2)
    }

    @Test
    fun resumeIsImmediatelyFollowedBySameShowUnplayed() = runTest {
        val podcast = TestFixtures.podcast(id = "pod-1", subscribedAt = nowMs - 10L * 24 * 60 * 60 * 1000L)
            .copy(latestEpisode = episode(id = "ep-unplayed", podcastId = "pod-1"))
        val result = MixtapeEngine.build(
            subscriptions = listOf(podcast),
            history = listOf(history(episodeId = "ep-resume", podcastId = "pod-1")),
            nowMs = nowMs,
        )
        val ids = result.episodes.map(Episode::id)
        assertEquals("ep-resume", ids.first())
        assertTrue(ids.contains("ep-unplayed"))
        assertEquals("ep-unplayed", ids[ids.indexOf("ep-resume") + 1])
    }

    @Test
    fun resultIsCappedAtMaxItems() = runTest {
        val subscriptions = (1..25).map { i ->
            TestFixtures.podcast(id = "pod-$i", subscribedAt = nowMs - 10L * 24 * 60 * 60 * 1000L)
                .copy(latestEpisode = episode(id = "ep-$i", podcastId = "pod-$i"))
        }
        val result = MixtapeEngine.build(
            subscriptions = subscriptions,
            history = emptyList(),
            nowMs = nowMs,
        )
        assertTrue(result.episodes.size <= 15)
    }
}
