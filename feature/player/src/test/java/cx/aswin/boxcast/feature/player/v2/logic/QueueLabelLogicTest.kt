package cx.aswin.boxcast.feature.player.v2.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueLabelLogicTest {

    @Test
    fun `sourceLabel returns From Lore for lore context`() {
        val episode = PlayerTestFixtures.episode(contextType = "LORE")
        assertEquals("From Lore", QueueLabelLogic.sourceLabel(episode))
    }

    @Test
    fun `sourceLabel maps auto fill source ids to human labels`() {
        assertEquals(
            "Continuing series",
            QueueLabelLogic.sourceLabel(
                PlayerTestFixtures.episode(contextType = "AUTO_FILL", contextSourceId = "same_podcast"),
            ),
        )
        assertEquals(
            "Pick up where you left off",
            QueueLabelLogic.sourceLabel(
                PlayerTestFixtures.episode(contextType = "AUTO_FILL", contextSourceId = "resume"),
            ),
        )
        assertEquals(
            "From your subscriptions",
            QueueLabelLogic.sourceLabel(
                PlayerTestFixtures.episode(contextType = "AUTO_FILL", contextSourceId = "subscription"),
            ),
        )
        assertEquals(
            "Recommended for you",
            QueueLabelLogic.sourceLabel(
                PlayerTestFixtures.episode(contextType = "AUTO_FILL", contextSourceId = "server_rec"),
            ),
        )
        assertEquals(
            "Recommended for you",
            QueueLabelLogic.sourceLabel(
                PlayerTestFixtures.episode(contextType = "AUTO_FILL", contextSourceId = "personalized_rec"),
            ),
        )
        assertEquals(
            "Based on what you're playing",
            QueueLabelLogic.sourceLabel(
                PlayerTestFixtures.episode(contextType = "AUTO_FILL", contextSourceId = "similar_episode"),
            ),
        )
        assertEquals(
            "Based on something you liked",
            QueueLabelLogic.sourceLabel(
                PlayerTestFixtures.episode(contextType = "AUTO_FILL", contextSourceId = "similar_liked"),
            ),
        )
    }

    @Test
    fun `sourceLabel uses genre for trending when available`() {
        val episode = PlayerTestFixtures.episode(
            contextType = "AUTO_FILL",
            contextSourceId = "trending",
            podcastGenre = "Science",
        )
        assertEquals("Trending in Science", QueueLabelLogic.sourceLabel(episode))
    }

    @Test
    fun `sourceLabel falls back to Trending now for generic podcast genre`() {
        val episode = PlayerTestFixtures.episode(
            contextType = "AUTO_FILL",
            contextSourceId = "trending",
            podcastGenre = "Podcast",
        )
        assertEquals("Trending now", QueueLabelLogic.sourceLabel(episode))
    }

    @Test
    fun `sourceLabel falls back to Added for you for unknown auto fill source`() {
        val episode = PlayerTestFixtures.episode(
            contextType = "AUTO_FILL",
            contextSourceId = "mystery_source",
        )
        assertEquals("Added for you", QueueLabelLogic.sourceLabel(episode))
    }

    @Test
    fun `sourceLabel returns null for manual or unknown context types`() {
        assertNull(QueueLabelLogic.sourceLabel(PlayerTestFixtures.episode(contextType = "MANUAL")))
        assertNull(QueueLabelLogic.sourceLabel(PlayerTestFixtures.episode(contextType = null)))
    }
}
