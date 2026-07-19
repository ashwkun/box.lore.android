package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeBecauseYouLikeLogicTest {
    @Test
    fun `toAffinitySignal copies every history field`() {
        val item =
            HomeListeningHistoryItem(
                episodeId = "ep-1",
                podcastId = "pod-1",
                podcastName = "Show One",
                podcastImageUrl = "https://img/1.jpg",
                progressMs = 42_000L,
                durationMs = 100_000L,
                isCompleted = true,
                isLiked = true,
                lastPlayedAt = 999L,
            )

        val signal = HomeBecauseYouLikeLogic.toAffinitySignal(item)

        assertEquals("pod-1", signal.podcastId)
        assertEquals("Show One", signal.podcastName)
        assertEquals("https://img/1.jpg", signal.podcastImageUrl)
        assertEquals(42_000L, signal.progressMs)
        assertEquals(999L, signal.lastPlayedAt)
        assertTrue(signal.isCompleted)
        assertTrue(signal.isLiked)
    }

    @Test
    fun `distinctByIdAndTitle removes duplicate ids then case-insensitive titles`() {
        val podcasts =
            listOf(
                TestFixtures.podcast(id = "a", title = "Alpha"),
                TestFixtures.podcast(id = "a", title = "Alpha duplicate id"),
                TestFixtures.podcast(id = "b", title = " alpha "),
                TestFixtures.podcast(id = "c", title = "Beta"),
            )

        val result =
            HomeBecauseYouLikeLogic.distinctByIdAndTitle(
                podcasts,
                id = { it.id },
                title = { it.title },
            )

        // "a" dedup by id (keeps first), then "b" dropped as same title as "Alpha".
        assertEquals(listOf("a", "c"), result.map { it.id })
    }

    @Test
    fun `sortPodcastsByEpisodeScores orders by latest episode score descending`() {
        val high =
            TestFixtures.podcast(id = "high").copy(latestEpisode = TestFixtures.episode(id = "e-high"))
        val low =
            TestFixtures.podcast(id = "low").copy(latestEpisode = TestFixtures.episode(id = "e-low"))
        val unscored =
            TestFixtures.podcast(id = "none").copy(latestEpisode = TestFixtures.episode(id = "e-none"))

        val sorted =
            HomeBecauseYouLikeLogic.sortPodcastsByEpisodeScores(
                podcasts = listOf(unscored, low, high),
                scores = mapOf("e-high" to 9.0, "e-low" to 1.0),
            )

        assertEquals(listOf("high", "low", "none"), sorted.map { it.id })
    }

    @Test
    fun `sortEpisodesByScores orders by episode score descending with default zero`() {
        val a = TestFixtures.episode(id = "a")
        val b = TestFixtures.episode(id = "b")
        val c = TestFixtures.episode(id = "c")

        val sorted =
            HomeBecauseYouLikeLogic.sortEpisodesByScores(
                episodes = listOf(a, b, c),
                scores = mapOf("b" to 5.0, "c" to 2.0),
            )

        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun `candidatePodcastsFromHistory merges subs and played and drops blank ids`() {
        val subs = listOf(TestFixtures.podcast(id = "sub-1", title = "Subbed"))
        val history =
            listOf(
                historyItem(podcastId = "played-1", name = "Played"),
                historyItem(podcastId = "sub-1", name = "Subbed again"),
                historyItem(podcastId = "", name = "Blank"),
            )

        val result = HomeBecauseYouLikeLogic.candidatePodcastsFromHistory(subs, history)

        // Subs first, then played-1; sub-1 not duplicated; blank id filtered out.
        assertEquals(listOf("sub-1", "played-1"), result.map { it.id })
    }

    @Test
    fun `resolveFavoriteFromMaps prefers subscription then synthesizes from maps`() {
        val sub = TestFixtures.podcast(id = "sub-1", title = "Real Sub")

        val fromSub =
            HomeBecauseYouLikeLogic.resolveFavoriteFromMaps(
                topPodId = "sub-1",
                subscriptions = listOf(sub),
                podcastNameMap = emptyMap(),
                podcastImageMap = emptyMap(),
            )
        assertEquals("Real Sub", fromSub.title)

        val synthesized =
            HomeBecauseYouLikeLogic.resolveFavoriteFromMaps(
                topPodId = "history-1",
                subscriptions = listOf(sub),
                podcastNameMap = mapOf("history-1" to "History Show"),
                podcastImageMap = mapOf("history-1" to "https://img/h.jpg"),
            )
        assertEquals("history-1", synthesized.id)
        assertEquals("History Show", synthesized.title)
        assertEquals("https://img/h.jpg", synthesized.imageUrl)

        val fallback =
            HomeBecauseYouLikeLogic.resolveFavoriteFromMaps(
                topPodId = "unknown",
                subscriptions = emptyList(),
                podcastNameMap = emptyMap(),
                podcastImageMap = emptyMap(),
            )
        assertEquals("Podcast", fallback.title)
    }

    private fun historyItem(
        podcastId: String,
        name: String,
    ) = HomeListeningHistoryItem(
        episodeId = "ep-$podcastId",
        podcastId = podcastId,
        podcastName = name,
        podcastImageUrl = null,
        progressMs = 0L,
        durationMs = 0L,
        isCompleted = false,
        isLiked = false,
        lastPlayedAt = 0L,
    )
}
