package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure coverage for [buildRecommendationSeeds] in `PodcastRepositoryRecommendations.kt`. */
class PodcastRecommendationSeedsTest {
    @Test
    fun likedEpisodesWeightedHighestAndSortedDescending() {
        val history =
            listOf(
                TestFixtures.historyItem(episodeId = "10", isLiked = false, isCompleted = false),
                TestFixtures.historyItem(episodeId = "11", isLiked = true, isCompleted = false),
            )

        val seeds = buildRecommendationSeeds(history, subscribedPodcastIds = emptyList())

        assertEquals(2, seeds.size)
        assertEquals(11L, seeds.first().id)
        assertEquals(1.0, seeds.first().weight)
        assertTrue(seeds.all { it.kind == "episode" })
    }

    @Test
    fun progressRatioDrivesWeightBuckets() {
        val completed =
            TestFixtures.historyItem(
                episodeId = "1",
                isLiked = false,
                isCompleted = true,
            )
        val halfway =
            TestFixtures.historyItem(
                episodeId = "2",
                isLiked = false,
                isCompleted = false,
                durationMs = 100L,
                progressMs = 60L,
            )
        val barelyStarted =
            TestFixtures.historyItem(
                episodeId = "3",
                isLiked = false,
                isCompleted = false,
                durationMs = 100L,
                progressMs = 5L,
            )

        val seeds =
            buildRecommendationSeeds(
                listOf(completed, halfway, barelyStarted),
                subscribedPodcastIds = emptyList(),
            ).associateBy { it.id }

        assertEquals(0.9, seeds.getValue(1L).weight)
        assertEquals(0.75, seeds.getValue(2L).weight)
        assertEquals(0.35, seeds.getValue(3L).weight)
    }

    @Test
    fun podcastSeedsBackfillWhenEpisodeSeedsBelowMax() {
        val history = listOf(TestFixtures.historyItem(episodeId = "1"))

        val seeds =
            buildRecommendationSeeds(
                history,
                subscribedPodcastIds = listOf("100", "200", "invalid", "-1"),
            )

        val podcastSeeds = seeds.filter { it.kind == "podcast" }
        assertEquals(listOf(100L, 200L), podcastSeeds.map { it.id })
        assertTrue(podcastSeeds.all { it.weight == 0.35 })
    }

    @Test
    fun episodeSeedsAreBoundedByMaximum() {
        val history = (1..20).map { TestFixtures.historyItem(episodeId = it.toString(), isLiked = true) }

        val seeds = buildRecommendationSeeds(history, subscribedPodcastIds = listOf("999"), maximumSeeds = 5)

        assertEquals(5, seeds.size)
        assertTrue(seeds.all { it.kind == "episode" })
    }

    @Test
    fun invalidEpisodeIdsAreSkipped() {
        val history =
            listOf(
                TestFixtures.historyItem(episodeId = null),
                TestFixtures.historyItem(episodeId = "0"),
                TestFixtures.historyItem(episodeId = "abc"),
            )

        val seeds = buildRecommendationSeeds(history, subscribedPodcastIds = emptyList())

        assertTrue(seeds.isEmpty())
    }

    @Test
    fun requiresPositiveMaximum() {
        assertThrows(IllegalArgumentException::class.java) {
            buildRecommendationSeeds(emptyList(), emptyList(), maximumSeeds = 0)
        }
    }
}
