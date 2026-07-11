package cx.aswin.boxcast.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimeTest {

    @Test
    fun `formatTime shows mm ss under one hour`() {
        assertEquals("00:00", formatTime(0))
        assertEquals("00:05", formatTime(5_000))
        assertEquals("01:30", formatTime(90_000))
        assertEquals("59:59", formatTime(3_599_000))
    }

    @Test
    fun `formatTime shows h mm ss at or above one hour`() {
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("1:05:09", formatTime(3_909_000))
        assertEquals("10:00:00", formatTime(36_000_000))
    }

    @Test
    fun `formatTime truncates sub-second millis`() {
        assertEquals("00:01", formatTime(1_999))
        assertEquals("01:00", formatTime(60_999))
    }

    @Test
    fun `formatTime handles negative millis as truncated toward zero seconds`() {
        assertEquals("00:00", formatTime(-500))
    }
}
