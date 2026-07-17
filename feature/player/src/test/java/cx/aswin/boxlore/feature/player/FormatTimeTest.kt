package cx.aswin.boxlore.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatTimeTest {
    @Test
    fun zeroIsFormattedAsMinutesAndSeconds() {
        assertEquals("00:00", formatTime(0L))
    }

    @Test
    fun millisecondsAreTruncated() {
        assertEquals("00:00", formatTime(999L))
        assertEquals("00:01", formatTime(1_999L))
    }

    @Test
    fun subHourDurationsUseTwoFields() {
        assertEquals("01:00", formatTime(60_000L))
        assertEquals("09:05", formatTime(545_000L))
        assertEquals("59:59", formatTime(3_599_000L))
    }

    @Test
    fun hourBoundaryAddsHourField() {
        assertEquals("1:00:00", formatTime(3_600_000L))
        assertEquals("1:01:01", formatTime(3_661_000L))
    }

    @Test
    fun multipleHoursAreNotCapped() {
        assertEquals("12:34:56", formatTime(45_296_000L))
        assertEquals("100:00:00", formatTime(360_000_000L))
    }
}
