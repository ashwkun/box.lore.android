package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PodcastAffinityLogicTest {
    @Test
    fun `history score rewards completion likes and progress tiers`() {
        val completed =
            PodcastAffinityLogic.HistorySignal(
                podcastId = "a",
                podcastName = "A",
                podcastImageUrl = null,
                progressMs = 0L,
                lastPlayedAt = 1L,
                isCompleted = true,
                isLiked = false,
            )
        val likedPartial =
            completed.copy(
                podcastId = "b",
                isCompleted = false,
                progressMs = 90_000L,
                isLiked = true,
            )
        val shortPlay =
            completed.copy(
                podcastId = "c",
                isCompleted = false,
                progressMs = 30_000L,
                isLiked = false,
            )

        assertEquals(20, PodcastAffinityLogic.historyScoreIncrement(completed))
        assertEquals(45, PodcastAffinityLogic.historyScoreIncrement(likedPartial)) // 5 + 40
        assertEquals(0, PodcastAffinityLogic.historyScoreIncrement(shortPlay))
        assertEquals(
            15,
            PodcastAffinityLogic.historyScoreIncrement(
                completed.copy(isCompleted = false, progressMs = 300_000L, isLiked = false),
            ),
        )
    }

    @Test
    fun `subscriptions get base boost and history aggregates per podcast`() {
        val subs = listOf(TestFixtures.podcast(id = "sub-1", title = "Sub"))
        val history =
            listOf(
                PodcastAffinityLogic.HistorySignal(
                    podcastId = "hist-1",
                    podcastName = "Hist",
                    podcastImageUrl = "img",
                    progressMs = 300_000L,
                    lastPlayedAt = 50L,
                    isCompleted = false,
                    isLiked = false,
                ),
                PodcastAffinityLogic.HistorySignal(
                    podcastId = "hist-1",
                    podcastName = "Hist",
                    podcastImageUrl = "img2",
                    progressMs = 0L,
                    lastPlayedAt = 100L,
                    isCompleted = true,
                    isLiked = true,
                ),
            )
        val lastPlayed = mutableMapOf<String, Long>()
        val names = mutableMapOf<String, String>()
        val images = mutableMapOf<String, String>()

        val scores =
            PodcastAffinityLogic.calculatePodcastAffinityScores(
                subscriptions = subs,
                historyList = history,
                lastPlayedMap = lastPlayed,
                podcastNameMap = names,
                podcastImageMap = images,
            )

        assertEquals(100, scores["sub-1"])
        assertEquals(15 + 20 + 40, scores["hist-1"])
        assertEquals(100L, lastPlayed["hist-1"])
        assertEquals("Sub", names["sub-1"])
        assertEquals("img2", images["hist-1"])
    }

    @Test
    fun `top affinity respects min score and last-played tie break`() {
        assertNull(PodcastAffinityLogic.topAffinityPodcastId(emptyMap(), emptyMap()))
        assertNull(
            PodcastAffinityLogic.topAffinityPodcastId(
                mapOf("low" to 10),
                mapOf("low" to 999L),
            ),
        )
        val winner =
            PodcastAffinityLogic.topAffinityPodcastId(
                scores = mapOf("a" to 20, "b" to 20),
                lastPlayedMap = mapOf("a" to 1L, "b" to 9L),
            )
        assertEquals("b", winner)
    }
}
