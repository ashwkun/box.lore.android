package cx.aswin.boxcast.feature.player.v2.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMetadataFormatTest {

    private val now = 1_700_000_000L

    @Test
    fun `formatRelativeDate returns empty for zero timestamp`() {
        assertEquals("", PlayerMetadataFormat.formatRelativeDate(0L, now))
    }

    @Test
    fun `formatRelativeDate returns just now for future timestamps`() {
        assertEquals("just now", PlayerMetadataFormat.formatRelativeDate(now + 60, now))
    }

    @Test
    fun `formatRelativeDate formats minutes hours days weeks months and years`() {
        assertEquals("5m ago", PlayerMetadataFormat.formatRelativeDate(now - 300, now))
        assertEquals("2h ago", PlayerMetadataFormat.formatRelativeDate(now - 7_200, now))
        assertEquals("3d ago", PlayerMetadataFormat.formatRelativeDate(now - 259_200, now))
        assertEquals("1w ago", PlayerMetadataFormat.formatRelativeDate(now - 604_800, now))
        assertEquals("1mo ago", PlayerMetadataFormat.formatRelativeDate(now - 2_592_000, now))
        assertEquals("1y ago", PlayerMetadataFormat.formatRelativeDate(now - 31_536_000, now))
    }

    @Test
    fun `formatRelativeDate uses floor division for each bucket`() {
        assertEquals("59m ago", PlayerMetadataFormat.formatRelativeDate(now - 3_540, now))
        assertEquals("23h ago", PlayerMetadataFormat.formatRelativeDate(now - 82_800, now))
    }

    @Test
    fun `formatDurationLabel returns empty for zero or negative duration`() {
        assertEquals("", PlayerMetadataFormat.formatDurationLabel(0))
        assertEquals("", PlayerMetadataFormat.formatDurationLabel(-10))
    }

    @Test
    fun `formatDurationLabel shows minutes only under one hour`() {
        assertEquals("45m", PlayerMetadataFormat.formatDurationLabel(2_700))
    }

    @Test
    fun `formatDurationLabel shows hours and minutes at or above one hour`() {
        assertEquals("1h 30m", PlayerMetadataFormat.formatDurationLabel(5_400))
        assertEquals("2h 5m", PlayerMetadataFormat.formatDurationLabel(7_500))
    }
}
