package cx.aswin.boxlore.feature.explore

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LearnPaginationTest {
    @Test
    fun firstPageWithUnseenCardsReturnsImmediately() = runTest {
        val requestedPages = mutableListOf<Int>()

        val result = findFirstUnseenCuriosityDeck(emptySet()) { page ->
            requestedPages += page
            response(card(1), card(2))
        }

        assertTrue(result is InitialCuriosityDeckResult.Found)
        result as InitialCuriosityDeckResult.Found
        assertEquals(1, result.page)
        assertEquals(listOf("1", "2"), result.unseenItems.map { it.episodeId })
        assertEquals(listOf(1), requestedPages)
    }

    @Test
    fun fullyDismissedPageIsSkipped() = runTest {
        val requestedPages = mutableListOf<Int>()

        val result = findFirstUnseenCuriosityDeck(setOf("1", "2")) { page ->
            requestedPages += page
            when (page) {
                1 -> response(card(1), card(2))
                else -> response(card(3), card(4))
            }
        }

        assertTrue(result is InitialCuriosityDeckResult.Found)
        result as InitialCuriosityDeckResult.Found
        assertEquals(2, result.page)
        assertEquals(listOf("3", "4"), result.unseenItems.map { it.episodeId })
        assertEquals(listOf(1, 2), requestedPages)
    }

    @Test
    fun mixedPageReturnsOnlyUnseenCards() = runTest {
        val result = findFirstUnseenCuriosityDeck(setOf("2")) {
            response(card(1), card(2), card(3))
        }

        assertTrue(result is InitialCuriosityDeckResult.Found)
        result as InitialCuriosityDeckResult.Found
        assertEquals(listOf("1", "3"), result.unseenItems.map { it.episodeId })
    }

    @Test
    fun emptyServerPageMarksDeckExhausted() = runTest {
        val result = findFirstUnseenCuriosityDeck(emptySet()) {
            response()
        }

        assertEquals(
            InitialCuriosityDeckResult.Exhausted(lastPage = 1),
            result
        )
    }

    @Test
    fun fiveDismissedPagesStopAtSafetyLimit() = runTest {
        val requestedPages = mutableListOf<Int>()
        val dismissedIds = (1L..5L).map { it.toString() }.toSet()

        val result = findFirstUnseenCuriosityDeck(dismissedIds) { page ->
            requestedPages += page
            response(card(page.toLong()))
        }

        assertEquals(
            InitialCuriosityDeckResult.Exhausted(lastPage = 5),
            result
        )
        assertEquals(listOf(1, 2, 3, 4, 5), requestedPages)
    }

    @Test
    fun failedLaterPageReportsItsPage() = runTest {
        val result = findFirstUnseenCuriosityDeck(setOf("1")) { page ->
            if (page == 1) response(card(1)) else null
        }

        assertEquals(InitialCuriosityDeckResult.Failed(page = 2), result)
    }

    private fun response(
        vararg cards: LearnCuriosityCard
    ): List<LearnCuriosityCard> = cards.toList()

    private fun card(id: Long): LearnCuriosityCard = LearnCuriosityCard(
        episodeId = id.toString(),
        question = "Question $id?",
        explanation = "Explanation $id",
        curiosityScore = 8,
        episodeTitle = "Episode $id",
        podcastTitle = "Podcast $id",
        imageUrl = null,
        feedImage = null,
        podcastId = null,
        audioUrl = "https://example.com/$id.mp3",
        duration = 0,
        description = null,
    )
}
