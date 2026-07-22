package cx.aswin.boxlore.core.catalog.home

import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.network.model.HomeCandidateModulesDto
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeCandidatesRequestBuilderTest {
    @Test
    fun buildSeedsPreferCompletedAndLikedAndDropWeakSamples() {
        val seeds =
            HomeCandidatesRequestBuilder.buildSeeds(
                listOf(
                    HistoryItem(
                        episodeId = "1",
                        podcastId = "a",
                        episodeTitle = "Weak",
                        podcastTitle = "A",
                        progressMs = 1_000L,
                        durationMs = 100_000L,
                    ),
                    HistoryItem(
                        episodeId = "2",
                        podcastId = "b",
                        episodeTitle = "Liked",
                        podcastTitle = "B",
                        isLiked = true,
                    ),
                    HistoryItem(
                        episodeId = "3",
                        podcastId = "c",
                        episodeTitle = "Done",
                        podcastTitle = "C",
                        isCompleted = true,
                    ),
                ),
            )
        assertEquals(2, seeds.size)
        assertEquals("2", seeds.first().episodeId)
        assertTrue(seeds.first().weight >= 0.9)
    }

    @Test
    fun isValidRequiresSuccessfulContract() {
        assertFalse(
            HomeCandidatesRequestBuilder.isValid(
                HomeCandidatesV1Response(status = "false"),
            ),
        )
        assertTrue(
            HomeCandidatesRequestBuilder.isValid(
                HomeCandidatesV1Response(
                    status = "true",
                    contractVersion = 1,
                    algorithmVersion = "home-candidates-v1",
                    modules = HomeCandidateModulesDto(),
                ),
            ),
        )
    }

    @Test
    fun cacheTtlIsAtLeastFourHours() {
        assertTrue(HomeCandidatesRequestBuilder.CACHE_TTL_MILLIS >= 4L * 60L * 60L * 1_000L)
    }

    @Test
    fun recommendationLanguagesIncludeRegionalTags() {
        assertEquals(listOf("fr", "en"), recommendationLanguagesForCountry("fr"))
        assertEquals(listOf("en", "hi"), recommendationLanguagesForCountry("IN"))
        assertEquals(listOf("en"), recommendationLanguagesForCountry("us"))
    }
}
