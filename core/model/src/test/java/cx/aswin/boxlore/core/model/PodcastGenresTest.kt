package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Coverage for [PodcastGenres.canonicalize] — canonical names, aliases, and normalization. */
class PodcastGenresTest {

    @Test
    fun canonicalizeReturnsExactCanonicalName() {
        assertEquals("Technology", PodcastGenres.canonicalize("Technology"))
        assertEquals("Society & Culture", PodcastGenres.canonicalize("Society & Culture"))
    }

    @Test
    fun canonicalizeIsCaseAndWhitespaceInsensitive() {
        assertEquals("Technology", PodcastGenres.canonicalize("  technology  "))
        assertEquals("True Crime", PodcastGenres.canonicalize("TRUE   CRIME"))
    }

    @Test
    fun canonicalizeResolvesAliases() {
        assertEquals("Health", PodcastGenres.canonicalize("Health & Fitness"))
        assertEquals("Health", PodcastGenres.canonicalize("fitness"))
        assertEquals("Society & Culture", PodcastGenres.canonicalize("society"))
        assertEquals("Society & Culture", PodcastGenres.canonicalize("culture"))
        assertEquals("Religion & Spirituality", PodcastGenres.canonicalize("religion"))
        assertEquals("Kids & Family", PodcastGenres.canonicalize("family"))
        assertEquals("TV & Film", PodcastGenres.canonicalize("tv"))
        assertEquals("Technology", PodcastGenres.canonicalize("tech"))
        assertEquals("Technology", PodcastGenres.canonicalize("Technology & Science"))
    }

    @Test
    fun canonicalizeReturnsNullForUnknownOrBlank() {
        assertNull(PodcastGenres.canonicalize(null))
        assertNull(PodcastGenres.canonicalize(""))
        assertNull(PodcastGenres.canonicalize("   "))
        assertNull(PodcastGenres.canonicalize("Underwater Basket Weaving"))
    }

    @Test
    fun allGenresRoundTripThroughCanonicalize() {
        PodcastGenres.all.forEach { genre ->
            assertEquals(genre, PodcastGenres.canonicalize(genre.lowercase()))
        }
    }
}
