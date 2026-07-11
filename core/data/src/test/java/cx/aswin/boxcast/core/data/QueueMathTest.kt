package cx.aswin.boxcast.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Index-mapping and queue-type detection rules shared by the queue sheet, the player
 * repository, and the playback service. These mappings are where the reorder feature
 * can silently corrupt the queue, so they're pinned down here.
 */
class QueueMathTest {

    // ── media-id prefix handling ────────────────────────────────────────────

    @Test
    fun `strips learn, episode and queue prefixes`() {
        assertEquals("123", QueueMath.stripMediaIdPrefixes("learn:123"))
        assertEquals("123", QueueMath.stripMediaIdPrefixes("episode:123"))
        assertEquals("123", QueueMath.stripMediaIdPrefixes("queue:123"))
        assertEquals("123", QueueMath.stripMediaIdPrefixes("123"))
    }

    @Test
    fun `finds media index regardless of prefix`() {
        val mediaIds = listOf("episode:1", "learn:2", "3")
        assertEquals(0, QueueMath.mediaIndexOfEpisode(mediaIds, "1"))
        assertEquals(1, QueueMath.mediaIndexOfEpisode(mediaIds, "2"))
        assertEquals(2, QueueMath.mediaIndexOfEpisode(mediaIds, "3"))
        assertEquals(-1, QueueMath.mediaIndexOfEpisode(mediaIds, "4"))
    }

    // ── UI index <-> queue index (sheet hides the playing item) ─────────────

    @Test
    fun `ui index maps to queue index with hidden-current offset`() {
        assertEquals(1, QueueMath.uiIndexToQueueIndex(0))
        assertEquals(5, QueueMath.uiIndexToQueueIndex(4))
        assertEquals(0, QueueMath.queueIndexToUiIndex(1))
        assertEquals(4, QueueMath.queueIndexToUiIndex(5))
    }

    @Test
    fun `ui and queue index mappings are inverses`() {
        for (ui in 0..10) {
            assertEquals(ui, QueueMath.queueIndexToUiIndex(QueueMath.uiIndexToQueueIndex(ui)))
        }
    }

    // ── Lore queue detection ────────────────────────────────────────────────

    @Test
    fun `queue with any non-learn media id is a normal queue`() {
        assertTrue(QueueMath.hasNonLoreMediaIds(listOf("learn:1", "42")))
        assertTrue(QueueMath.hasNonLoreMediaIds(listOf("episode:9")))
    }

    @Test
    fun `lore-only or empty media ids are not a normal queue`() {
        assertFalse(QueueMath.hasNonLoreMediaIds(listOf("learn:1", "learn:2")))
        assertFalse(QueueMath.hasNonLoreMediaIds(emptyList()))
    }

    @Test
    fun `persisted context types detect normal queues across restarts`() {
        assertTrue(QueueMath.hasNonLoreContextTypes(listOf("LORE", "MANUAL")))
        assertTrue(QueueMath.hasNonLoreContextTypes(listOf(null)))       // legacy rows
        assertTrue(QueueMath.hasNonLoreContextTypes(listOf("AUTO_FILL")))
        assertFalse(QueueMath.hasNonLoreContextTypes(listOf("LORE", "LORE")))
        assertFalse(QueueMath.hasNonLoreContextTypes(emptyList()))
    }

    // ── list reordering ─────────────────────────────────────────────────────

    @Test
    fun `moveItem moves forward and backward`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), QueueMath.moveItem(list, 0, 2))
        assertEquals(listOf("c", "a", "b", "d"), QueueMath.moveItem(list, 2, 0))
    }

    @Test
    fun `moveItem with same or invalid indices returns the list unchanged`() {
        val list = listOf("a", "b", "c")
        assertEquals(list, QueueMath.moveItem(list, 1, 1))
        assertEquals(list, QueueMath.moveItem(list, -1, 2))
        assertEquals(list, QueueMath.moveItem(list, 0, 3))
    }
}
