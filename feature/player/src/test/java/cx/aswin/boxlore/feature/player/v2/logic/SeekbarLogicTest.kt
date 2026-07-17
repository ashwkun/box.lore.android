package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeekbarLogicTest {
    private val chapters = listOf(
        chapter(10.0, "Opening"),
        chapter(60.0, "Main story"),
        chapter(120.5, "Interview")
    )

    @Test
    fun chapterSelectionHandlesTimelineBoundaries() {
        assertNull(chapterAtPosition(chapters, 9_999L))
        assertEquals("Opening", chapterAtPosition(chapters, 10_000L)?.title)
        assertEquals("Opening", chapterAtPosition(chapters, 59_999L)?.title)
        assertEquals("Main story", chapterAtPosition(chapters, 60_000L)?.title)
        assertEquals("Interview", chapterAtPosition(chapters, 120_500L)?.title)
        assertEquals("Interview", chapterAtPosition(chapters, 999_999L)?.title)
    }

    @Test
    fun emptyChapterListHasNoMatch() {
        assertNull(chapterAtPosition(emptyList(), 100_000L))
    }

    @Test
    fun seekPositionClampsFraction() {
        assertEquals(0L, seekPosition(-1f, 100_000L))
        assertEquals(0L, seekPosition(0f, 100_000L))
        assertEquals(25_000L, seekPosition(0.25f, 100_000L))
        assertEquals(100_000L, seekPosition(1f, 100_000L))
        assertEquals(100_000L, seekPosition(2f, 100_000L))
    }

    @Test
    fun seekPositionHandlesInvalidDuration() {
        assertEquals(0L, seekPosition(0.5f, 0L))
        assertEquals(0L, seekPosition(0.5f, -100L))
    }

    @Test
    fun previewTextIncludesChapterWhenPresent() {
        assertEquals("01:00 • Main story", seekPreviewText(60_000L, chapters[1]))
        assertEquals("01:00", seekPreviewText(60_000L, null))
    }

    @Test
    fun playbackFractionClampsAndHandlesInvalidDuration() {
        assertEquals(0f, playbackFraction(100L, 0L), 0.001f)
        assertEquals(0f, playbackFraction(-100L, 1_000L), 0.001f)
        assertEquals(0.5f, playbackFraction(500L, 1_000L), 0.001f)
        assertEquals(1f, playbackFraction(2_000L, 1_000L), 0.001f)
    }
}
