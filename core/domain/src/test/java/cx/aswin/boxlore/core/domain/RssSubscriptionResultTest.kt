package cx.aswin.boxlore.core.domain

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RssSubscriptionResultTest {
    @Test
    fun `result carries podcast and optional podcast index match`() {
        val podcast = TestFixtures.podcast(id = "rss:-1", title = "Local Feed")
        val match = TestFixtures.podcast(id = "42", title = "Index Match")
        val result =
            RssSubscriptionResult(
                podcast = podcast,
                episodeCount = 3,
                automaticUpdateChecksSupported = true,
                potentialPodcastIndexMatch = match,
            )

        assertEquals(3, result.episodeCount)
        assertEquals("Local Feed", result.podcast.title)
        assertEquals("42", result.potentialPodcastIndexMatch?.id)
        assertNull(result.linkedPodcastIndexId)
    }
}
