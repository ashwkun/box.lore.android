package cx.aswin.boxlore.feature.explore.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class LearnDeckLogicTest {
    @Test
    fun filterAndShuffleNewItemsDropsDismissedAndExistingCards() {
        val result =
            filterAndShuffleNewItems(
                rawItems = listOf(card("1"), card("2"), card("3"), card("4")),
                currentStack = listOf(card("3")),
                dismissedIds = setOf("2"),
                episodeId = TestCard::id,
                curiosityScore = TestCard::score,
                random = FixedRandom(0.5, 0.5, 0.5),
            )

        assertEquals(setOf("1", "4"), result.map { it.id }.toSet())
    }

    @Test
    fun filterAndShuffleNewItemsReturnsEmptyWhenAllRawItemsAreDismissed() {
        val result =
            filterAndShuffleNewItems(
                rawItems = listOf(card("1"), card("2")),
                currentStack = emptyList(),
                dismissedIds = setOf("1", "2"),
                episodeId = TestCard::id,
                curiosityScore = TestCard::score,
                random = FixedRandom(),
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun weightedShuffleUsesCuriosityScoreAsWeight() {
        val lowScore = card("low", score = 0)
        val highScore = card("high", score = 9)

        val result =
            weightedShuffle(
                list = listOf(lowScore, highScore),
                curiosityScore = TestCard::score,
                random = FixedRandom(0.9, 0.5),
            )

        assertEquals(listOf("high", "low"), result.map { it.id })
    }

    @Test
    fun weightedShuffleKeepsSingletonListUnchanged() {
        val input = listOf(card("1"))

        val result =
            weightedShuffle(
                list = input,
                curiosityScore = TestCard::score,
                random = FixedRandom(),
            )

        assertSame(input, result)
    }

    private data class TestCard(
        val id: String,
        val score: Int = 0,
    )

    private fun card(
        id: String,
        score: Int = 0,
    ): TestCard = TestCard(id, score)

    private class FixedRandom(
        private vararg val values: Double,
    ) : Random() {
        private var index = 0

        override fun nextDouble(): Double = values.getOrElse(index++) { 0.5 }
    }
}
