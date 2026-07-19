package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Chapter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pure-JVM coverage for [ChapterRepository]'s in-memory cache and the description parser
 * ([ChapterRepository.parseChaptersFromDescription]) — no network is touched.
 */
class ChapterRepositoryTest {
    @BeforeEach
    fun setUp() {
        ChapterRepository.clearCache()
    }

    @AfterEach
    fun tearDown() {
        ChapterRepository.clearCache()
    }

    // ---- Cache ----

    @Test
    fun cacheRoundTripsAndClears() {
        val chapters = listOf(Chapter(startTime = 0.0, title = "Intro"))
        ChapterRepository.setCachedChapters("k", chapters)

        assertSame(chapters, ChapterRepository.getCachedChapters("k"))
        ChapterRepository.clearCache()
        assertNull(ChapterRepository.getCachedChapters("k"))
    }

    // ---- parseChaptersFromDescription ----

    @Test
    fun parseReturnsEmptyForNullOrBlank() {
        assertTrue(ChapterRepository.parseChaptersFromDescription(null).isEmpty())
        assertTrue(ChapterRepository.parseChaptersFromDescription("").isEmpty())
    }

    @Test
    fun parseRequiresAtLeastTwoTimestampsToAvoidFalsePositives() {
        val single = ChapterRepository.parseChaptersFromDescription("<p>00:00 Only Intro</p>")
        assertTrue(single.isEmpty())
    }

    @Test
    fun parseExtractsHtmlSeparatedTimestampsSortedByStartTime() {
        val html = "<p>01:30 Second</p><p>00:00 Intro</p><br/>1:02:03 Finale"

        val chapters = ChapterRepository.parseChaptersFromDescription(html)

        assertEquals(listOf("Intro", "Second", "Finale"), chapters.map { it.title })
        assertEquals(listOf(0.0, 90.0, 3723.0), chapters.map { it.startTime })
    }

    @Test
    fun parseHandlesTitleBeforeTimestamp() {
        val html = "Introduction 00:00\nChapter Two 05:00"

        val chapters = ChapterRepository.parseChaptersFromDescription(html)

        assertEquals(listOf("Introduction", "Chapter Two"), chapters.map { it.title })
    }

    @Test
    fun parseSkipsInvalidMinuteAndSecondFields() {
        // 00:60 has 60 seconds (invalid), 99:99 minutes/seconds out of range — both dropped,
        // leaving fewer than two valid entries so the guard yields an empty list.
        val html = "<p>00:60 Bad Seconds</p><p>99:99 Bad Both</p>"

        assertTrue(ChapterRepository.parseChaptersFromDescription(html).isEmpty())
    }

    @Test
    fun parseStripsSurroundingPunctuationFromTitles() {
        val html = "<li>00:00 - Intro:</li><li>02:15 :: Deep Dive --</li>"

        val chapters = ChapterRepository.parseChaptersFromDescription(html)

        assertEquals(listOf("Intro", "Deep Dive"), chapters.map { it.title })
    }

    @Test
    fun parseDropsTimestampsWithoutTitles() {
        // Bare timestamps with no accompanying text produce empty titles and are ignored,
        // so with only one real chapter the >= 2 guard returns empty.
        val html = "<p>00:00</p><p>01:00 Real Chapter</p>"

        assertTrue(ChapterRepository.parseChaptersFromDescription(html).isEmpty())
    }
}
