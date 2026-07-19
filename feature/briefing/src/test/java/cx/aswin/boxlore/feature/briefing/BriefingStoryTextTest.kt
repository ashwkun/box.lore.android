package cx.aswin.boxlore.feature.briefing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BriefingStoryTextTest {
    @Test
    fun `splits on blank lines and drops empties`() {
        val script = "First paragraph.\n\n\n\nSecond paragraph."

        val paragraphs = briefingStoryParagraphs(script)

        assertEquals(listOf("First paragraph.", "Second paragraph."), paragraphs)
    }

    @Test
    fun `removes greeting sentence from first paragraph`() {
        val script =
            "This is the boxlore brief for January 1st. Today's top story is cats.\n\n" +
                "A middle paragraph.\n\n" +
                "The closing note."

        val paragraphs = briefingStoryParagraphs(script)

        assertEquals("Today's top story is cats.", paragraphs.first())
        assertEquals("A middle paragraph.", paragraphs[1])
    }

    @Test
    fun `keeps first paragraph untouched when it has no greeting prefix`() {
        val script = "Today we discuss cats. And dogs.\n\nWrap up here."

        val paragraphs = briefingStoryParagraphs(script)

        assertEquals("Today we discuss cats. And dogs.", paragraphs.first())
    }

    @Test
    fun `keeps greeting paragraph when it has no period`() {
        val script = "Welcome to the daily brief for today\n\nSecond paragraph."

        val paragraphs = briefingStoryParagraphs(script)

        assertEquals("Welcome to the daily brief for today", paragraphs.first())
    }

    @Test
    fun `removes closing outro from last paragraph`() {
        val script =
            "An opening line.\n\n" +
                "Finally, the market rose today. See you tomorrow."

        val paragraphs = briefingStoryParagraphs(script)

        assertEquals("Finally, the market rose today.", paragraphs.last())
    }

    @Test
    fun `trims trailing brief self-reference on last paragraph`() {
        val script =
            "An opening line.\n\n" +
                "Great episode today. Thanks for the boxlore brief."

        val paragraphs = briefingStoryParagraphs(script)

        assertEquals("Great episode today.", paragraphs.last())
    }

    @Test
    fun `single paragraph strips both greeting and outro`() {
        val script =
            "This is the boxlore brief for Monday. Big news happened. See you tomorrow."

        val paragraphs = briefingStoryParagraphs(script)

        assertEquals(listOf("Big news happened."), paragraphs)
    }

    @Test
    fun `blank script yields no paragraphs`() {
        assertTrue(briefingStoryParagraphs("").isEmpty())
        assertTrue(briefingStoryParagraphs("\n\n   \n\n").isEmpty())
    }
}
