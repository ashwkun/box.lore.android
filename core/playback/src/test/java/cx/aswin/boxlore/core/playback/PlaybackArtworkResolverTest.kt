package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackArtworkResolverTest {
    @Test
    fun `prefers episode image over podcast image`() {
        assertEquals(
            "https://episode",
            PlaybackArtworkResolver.resolveEpisodeImageUrl(
                episodeImageUrl = "https://episode",
                episodePodcastImageUrl = "https://show",
                podcastImageUrl = "https://podcast",
            ),
        )
    }

    @Test
    fun `falls back to episode podcast image then podcast image`() {
        assertEquals(
            "https://show",
            PlaybackArtworkResolver.resolveEpisodeImageUrl(
                episodeImageUrl = "  ",
                episodePodcastImageUrl = "https://show",
                podcastImageUrl = "https://podcast",
            ),
        )
        assertEquals(
            "https://podcast",
            PlaybackArtworkResolver.resolveEpisodeImageUrl(
                episodeImageUrl = null,
                episodePodcastImageUrl = null,
                podcastImageUrl = "https://podcast",
            ),
        )
    }

    @Test
    fun `episode and podcast overload matches scalar resolution`() {
        val episode =
            Episode(
                id = "1",
                title = "Ep",
                description = "",
                audioUrl = "https://audio",
                imageUrl = null,
                podcastImageUrl = "https://show",
            )
        val podcast = Podcast(id = "p1", title = "Show", artist = "Host", imageUrl = "https://podcast")
        assertEquals(
            "https://show",
            PlaybackArtworkResolver.resolveEpisodeImageUrl(episode, podcast),
        )
    }

    @Test
    fun `returns null when no non-blank url`() {
        assertNull(
            PlaybackArtworkResolver.resolveEpisodeImageUrl(
                episodeImageUrl = "",
                episodePodcastImageUrl = " ",
                podcastImageUrl = null,
            ),
        )
    }
}
