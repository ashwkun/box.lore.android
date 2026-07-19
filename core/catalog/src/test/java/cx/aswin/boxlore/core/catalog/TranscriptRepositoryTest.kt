package cx.aswin.boxlore.core.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranscriptRepositoryTest {
    @Test
    fun testParseVtt_stripsHtmlAndVttTags() {
        val vttContent =
            """
            WEBVTT

            00:00:01.000 --> 00:00:04.000
            <v Host>Welcome to the <i>podcast</i>!</v>

            00:00:04.500 --> 00:00:08.000
            <c.yellow>Thank you for having me.</c>
            """.trimIndent()

        val segments = TranscriptRepository.parseVtt(vttContent)

        assertEquals(2, segments.size)
        assertEquals(1000L, segments[0].startMs)
        assertEquals(4000L, segments[0].endMs)
        assertEquals("Welcome to the podcast!", segments[0].text)

        assertEquals(4500L, segments[1].startMs)
        assertEquals(8000L, segments[1].endMs)
        assertEquals("Thank you for having me.", segments[1].text)
    }

    @Test
    fun testParseSrt_stripsHtmlTags() {
        val srtContent =
            """
            1
            00:00:01,000 --> 00:00:04,000
            Hello <b>world</b>!

            2
            00:00:05,000 --> 00:00:09,000
            This is a <font color="red">test</font>.
            """.trimIndent()

        val segments = TranscriptRepository.parseSrt(srtContent)

        assertEquals(2, segments.size)
        assertEquals(1000L, segments[0].startMs)
        assertEquals(4000L, segments[0].endMs)
        assertEquals("Hello world!", segments[0].text)

        assertEquals(5000L, segments[1].startMs)
        assertEquals(9000L, segments[1].endMs)
        assertEquals("This is a test.", segments[1].text)
    }

    @Test
    fun testParseVtt_timelineHealing() {
        // Test timeline healing where end time is before start time or non-chronological
        val vttContent =
            """
            WEBVTT

            00:00:05.000 --> 00:00:02.000
            Malformed timing segment
            """.trimIndent()

        val segments = TranscriptRepository.parseVtt(vttContent)

        assertEquals(1, segments.size)
        assertTrue(segments[0].startMs < segments[0].endMs, "Start time should be before end time after healing")
    }
}
