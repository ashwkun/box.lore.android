package cx.aswin.boxlore.core.catalog.content

import cx.aswin.boxlore.core.model.PodcastGenres
import cx.aswin.boxlore.core.network.model.HistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentSignalEnrichmentTest {
    @Test
    fun `canonical vocabulary contains every explicit app genre`() {
        assertEquals(19, PodcastGenres.all.size)
        assertEquals(
            listOf(
                "News",
                "Technology",
                "Business",
                "Comedy",
                "True Crime",
                "Sports",
                "Health",
                "History",
                "Arts",
                "Society & Culture",
                "Education",
                "Science",
                "TV & Film",
                "Fiction",
                "Music",
                "Religion & Spirituality",
                "Kids & Family",
                "Leisure",
                "Government",
            ),
            PodcastGenres.all,
        )
    }

    @Test
    fun `genre blend is canonical bounded and preserves negative learning`() {
        val profile =
            buildContentSignalProfile(
                explicitInterests = listOf("Tech", "Technology", "unknown"),
                subscribedGenres = listOf("Sports", "Sports", "Health & Fitness"),
                recentHistory =
                    listOf(
                        history("1", "News", durationMinutes = 30, progressMinutes = 30, liked = true),
                        history("2", "News", durationMinutes = 40, progressMinutes = 25),
                    ),
                subscribedPodcastIds = setOf("subscribed"),
                learnedGenreAffinities =
                    mapOf(
                        "Fiction" to -1.0,
                        "Technology" to 0.8,
                        "unknown" to 1.0,
                    ),
            )

        val weights = profile.tasteSignals.associate { it.genre to it.weight }
        assertTrue(profile.tasteSignals.size <= 19)
        assertTrue(profile.tasteSignals.all { it.genre in PodcastGenres.all })
        assertTrue(profile.tasteSignals.all { it.weight in -1.0..1.0 })
        assertTrue(requireNotNull(weights["Technology"]) > 0.0)
        assertTrue(requireNotNull(weights["News"]) > 0.0)
        assertTrue(requireNotNull(weights["Fiction"]) < 0.0)
    }

    @Test
    fun `duration preference uses robust central range and clamps outlier`() {
        val history =
            listOf(10, 20, 30, 40, 240).mapIndexed { index, minutes ->
                history(
                    id = index.toString(),
                    genre = "Technology",
                    durationMinutes = minutes,
                    progressMinutes = minutes,
                )
            }

        val range = requireNotNull(deriveDurationPreference(history))

        assertEquals(15, range.minimumMinutes)
        assertEquals(50, range.maximumMinutes)
    }

    @Test
    fun `maturity and novelty summaries stay privacy bounded`() {
        assertEquals(0, historyMaturityBucket(0))
        assertEquals(1, historyMaturityBucket(4))
        assertEquals(2, historyMaturityBucket(14))
        assertEquals(3, historyMaturityBucket(29))
        assertEquals(4, historyMaturityBucket(300))

        val history =
            listOf(
                history("1", "News", podcastId = "subscribed"),
                history("2", "News", podcastId = "new-a"),
                history("3", "News", podcastId = "new-b"),
            )
        val novelty = deriveNoveltyPreference(history, setOf("subscribed"))
        assertTrue(novelty in 0.0..1.0)
        assertTrue(novelty > 0.5)
        assertEquals(0.5, deriveNoveltyPreference(emptyList(), emptySet()), 0.0)
    }

    private fun history(
        id: String,
        genre: String,
        podcastId: String = "podcast-$id",
        durationMinutes: Int = 30,
        progressMinutes: Int = 10,
        liked: Boolean = false,
    ) = HistoryItem(
        podcastTitle = "Podcast $id",
        episodeTitle = "Episode $id",
        podcastId = podcastId,
        episodeId = id,
        genre = genre,
        durationMs = durationMinutes * 60_000L,
        progressMs = progressMinutes * 60_000L,
        isCompleted = progressMinutes >= durationMinutes,
        isLiked = liked,
    )
}
