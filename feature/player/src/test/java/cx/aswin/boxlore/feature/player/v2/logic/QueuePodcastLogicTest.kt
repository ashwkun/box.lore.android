package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class QueuePodcastLogicTest {
    @Test
    fun missingPodcastIdUsesCurrentPodcast() {
        val current = podcast()

        assertSame(current, resolveQueuePodcast(episode(podcastId = null), current))
    }

    @Test
    fun matchingPodcastIdUsesCurrentPodcast() {
        val current = podcast(id = "same")

        assertSame(current, resolveQueuePodcast(episode(podcastId = "same"), current))
    }

    @Test
    fun differentPodcastBuildsQueueContextPodcast() {
        val result =
            resolveQueuePodcast(
                episode(
                    podcastId = "other",
                    podcastTitle = "Other Podcast",
                    podcastArtist = "Other Artist",
                    podcastImageUrl = "https://example.com/other.jpg",
                    podcastGenre = "Science",
                ),
                podcast(),
            )

        assertEquals("other", result.id)
        assertEquals("Other Podcast", result.title)
        assertEquals("Other Artist", result.artist)
        assertEquals("https://example.com/other.jpg", result.imageUrl)
        assertEquals("Science", result.genre)
    }

    @Test
    fun missingQueueMetadataUsesSafeFallbacks() {
        val result =
            resolveQueuePodcast(
                episode(
                    podcastId = "other",
                    podcastTitle = null,
                    podcastArtist = null,
                    podcastImageUrl = null,
                    podcastGenre = null,
                ),
                podcast(),
            )

        assertEquals("other", result.id)
        assertEquals("Unknown", result.title)
        assertEquals("", result.artist)
        assertEquals("", result.imageUrl)
        assertEquals("", result.genre)
    }
}
