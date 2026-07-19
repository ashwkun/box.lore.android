package cx.aswin.boxlore.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure coverage for the internal catalog mapper helpers in `PodcastRepositoryMappers.kt`
 * ([toHttps] and [resolvePrimaryGenre]).
 */
class PodcastRepositoryMappersTest {
    @Test
    fun toHttpsUpgradesInsecureUrls() {
        assertEquals("https://cdn.example/a.jpg", "http://cdn.example/a.jpg".toHttps())
    }

    @Test
    fun toHttpsLeavesSecureUrlsUnchanged() {
        assertEquals("https://cdn.example/a.jpg", "https://cdn.example/a.jpg".toHttps())
    }

    @Test
    fun toHttpsReturnsEmptyForNullOrBlank() {
        assertEquals("", (null as String?).toHttps())
        assertEquals("", "".toHttps())
    }

    @Test
    fun resolvePrimaryGenreDefaultsWhenNoCategories() {
        assertEquals("Podcast", resolvePrimaryGenre(null))
        assertEquals("Podcast", resolvePrimaryGenre(emptyMap()))
    }

    @Test
    fun resolvePrimaryGenrePrefersMostSpecificPriorityMatch() {
        // Both "Sports" and "News" appear; the priority list ranks Sports above News.
        val categories = mapOf("1" to "News", "2" to "Sports")
        assertEquals("Sports", resolvePrimaryGenre(categories))
    }

    @Test
    fun resolvePrimaryGenreIsCaseInsensitive() {
        assertEquals("comedy", resolvePrimaryGenre(mapOf("1" to "comedy")))
    }

    @Test
    fun resolvePrimaryGenreFallsBackToPartialMatch() {
        assertEquals("Technology News", resolvePrimaryGenre(mapOf("1" to "Technology News")))
    }

    @Test
    fun resolvePrimaryGenreSkipsGenericPodcastValues() {
        val categories = mapOf("1" to "Podcasts", "2" to "Knitting")
        assertEquals("Knitting", resolvePrimaryGenre(categories))
    }

    @Test
    fun resolvePrimaryGenreReturnsPodcastWhenOnlyGenericValues() {
        assertEquals("Podcast", resolvePrimaryGenre(mapOf("1" to "Podcasts", "2" to "podcast")))
    }
}
