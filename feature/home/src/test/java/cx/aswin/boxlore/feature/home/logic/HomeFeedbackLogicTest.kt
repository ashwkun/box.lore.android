package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeFeedbackLogicTest {
    @Test
    fun `hideShowEverywhere removes hidden podcast from all three rails`() {
        val hiddenEpisode = TestFixtures.episode(id = "hidden-ep", podcastId = "hidden")
        val keptEpisode = TestFixtures.episode(id = "kept-ep", podcastId = "kept")
        val hiddenPodcast = TestFixtures.podcast(id = "hidden", title = "Hidden Show")
        val keptPodcast = TestFixtures.podcast(id = "kept", title = "Kept Show")

        val result =
            HomeFeedbackLogic.hideShowEverywhere(
                taste = listOf(hiddenEpisode, keptEpisode),
                becauseYouLikeEpisodes = listOf(hiddenEpisode, keptEpisode),
                becauseYouLikePodcasts = listOf(hiddenPodcast, keptPodcast),
                missionEpisodes = listOf(hiddenEpisode, keptEpisode),
                anchorPodcastId = "kept",
                hiddenPodcastId = "hidden",
            )

        assertEquals(listOf("kept-ep"), result.taste.map { it.id })
        assertEquals(listOf("kept-ep"), result.becauseYouLikeEpisodes.map { it.id })
        assertEquals(listOf("kept"), result.becauseYouLikePodcasts.map { it.id })
        assertEquals(listOf("kept-ep"), result.missionEpisodes.map { it.id })
        assertFalse(result.anchorCleared)
    }

    @Test
    fun `hideShowEverywhere clears anchor when the hidden show was the current anchor`() {
        val episode = TestFixtures.episode(id = "ep", podcastId = "anchor")

        val result =
            HomeFeedbackLogic.hideShowEverywhere(
                taste = listOf(episode),
                becauseYouLikeEpisodes = listOf(episode),
                becauseYouLikePodcasts = emptyList(),
                missionEpisodes = emptyList(),
                anchorPodcastId = "anchor",
                hiddenPodcastId = "anchor",
            )

        assertTrue(result.anchorCleared)
        assertTrue(result.taste.isEmpty())
    }

    @Test
    fun `hideShowEverywhere is a no-op when the hidden podcast is not present`() {
        val episode = TestFixtures.episode(id = "ep", podcastId = "other")

        val result =
            HomeFeedbackLogic.hideShowEverywhere(
                taste = listOf(episode),
                becauseYouLikeEpisodes = listOf(episode),
                becauseYouLikePodcasts = emptyList(),
                missionEpisodes = listOf(episode),
                anchorPodcastId = "other",
                hiddenPodcastId = "unrelated",
            )

        assertEquals(listOf("ep"), result.taste.map { it.id })
        assertEquals(listOf("ep"), result.missionEpisodes.map { it.id })
        assertFalse(result.anchorCleared)
    }
}
