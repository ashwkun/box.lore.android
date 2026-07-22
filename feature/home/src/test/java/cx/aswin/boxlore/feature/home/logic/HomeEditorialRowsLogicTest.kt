package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeEditorialRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class HomeEditorialRowsLogicTest {
    @Test
    fun `every daypart has three stable editorial definitions without internal terminology`() {
        val definitions = ContentDaypart.entries.associateWith(::editorialRowDefinitionsFor)

        definitions.values.forEach { rows ->
            assertEquals(3, rows.size)
            assertEquals(3, rows.map { it.providerId }.distinct().size)
            assertFalse(
                rows.any { row ->
                    row.title.contains("vibe", ignoreCase = true) ||
                        row.subtitle.contains("vibe", ignoreCase = true)
                },
            )
        }
        assertEquals(
            listOf("morning_news", "morning_motivation", "business_insider"),
            definitions.getValue(ContentDaypart.MORNING).map { it.providerId },
        )
        assertEquals(
            listOf("true_crime_sleep", "history_buff", "mystery_thriller"),
            definitions.getValue(ContentDaypart.LATE_NIGHT).map { it.providerId },
        )
    }

    @Test
    fun `rows preserve order cap items and remove unplayable or repeated content`() {
        val first = podcast(id = "pod-1", episodeId = "ep-1")
        val second = podcast(id = "pod-2", episodeId = "ep-2")
        val overCap = podcast(id = "pod-3", episodeId = "ep-3")
        val nextRow = podcast(id = "pod-4", episodeId = "ep-4")
        val repeatedEpisode = podcast(id = "pod-5", episodeId = "ep-4")
        val noEpisode = TestFixtures.podcast(id = "no-episode")
        val noAudio = podcast(id = "no-audio", episodeId = "ep-no-audio", audioUrl = "")

        val rows =
            buildHomeEditorialRows(
                daypart = ContentDaypart.MORNING,
                podcastsByProvider =
                    mapOf(
                        "morning_news" to
                            listOf(
                                first,
                                first,
                                noEpisode,
                                noAudio,
                                second,
                                overCap,
                            ),
                        "morning_motivation" to listOf(first, nextRow, repeatedEpisode),
                        "business_insider" to emptyList(),
                    ),
                maximumItemsPerRow = 2,
            )

        assertEquals(listOf("morning_news", "morning_motivation"), rows.map { it.providerId })
        assertEquals(listOf("pod-1", "pod-2"), rows[0].podcasts.map { it.id })
        assertEquals(listOf("pod-4"), rows[1].podcasts.map { it.id })
    }

    @Test
    fun `non-positive row cap returns no rows`() {
        assertEquals(
            emptyList<HomeEditorialRow>(),
            buildHomeEditorialRows(
                daypart = ContentDaypart.EVENING,
                podcastsByProvider = emptyMap(),
                maximumItemsPerRow = 0,
            ),
        )
    }

    private fun podcast(
        id: String,
        episodeId: String,
        audioUrl: String = "https://example.com/$episodeId.mp3",
    ): Podcast =
        TestFixtures
            .podcast(id = id)
            .copy(
                latestEpisode =
                    TestFixtures.episode(
                        id = episodeId,
                        podcastId = id,
                        audioUrl = audioUrl,
                    ),
            )
}
