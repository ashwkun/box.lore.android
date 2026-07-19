package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure JVM tests for the skip-memory store. Persistence is injected as lambdas, so
 * these run without Android (no SharedPreferences involved).
 */
class QueueSkipMemoryTest {

    private var store: String? = null
    private var now: Long = 1_000_000_000_000L

    private fun newMemory() = QueueSkipMemory(
        readRaw = { store },
        writeRaw = { store = it },
        nowMs = { now }
    )

    @Test
    fun `recorded skips are returned and never re-suggested`() {
        val memory = newMemory()
        memory.recordSkip("ep1", "podA", "trending")
        memory.recordSkip("ep2", "podB", "subscription")

        assertEquals(setOf("ep1", "ep2"), memory.skippedEpisodeIds())
    }

    @Test
    fun `entries survive a reload through the persisted format`() {
        newMemory().recordSkip("ep1", "podA", "trending")
        // A brand-new instance reading the same raw store sees the entry.
        assertEquals(setOf("ep1"), newMemory().skippedEpisodeIds())
    }

    @Test
    fun `entries expire after seven days`() {
        val memory = newMemory()
        memory.recordSkip("old", "podA", "trending")

        now += QueueSkipMemory.EXPIRY_MS + 1
        memory.recordSkip("fresh", "podA", "trending")

        assertEquals(setOf("fresh"), memory.skippedEpisodeIds())
    }

    @Test
    fun `store is capped at the max entry count keeping newest`() {
        val memory = newMemory()
        repeat(QueueSkipMemory.MAX_ENTRIES + 50) { i ->
            now += 1
            memory.recordSkip("ep$i", "pod$i", "trending")
        }
        val ids = memory.skippedEpisodeIds()
        assertEquals(QueueSkipMemory.MAX_ENTRIES, ids.size)
        assertTrue("ep${QueueSkipMemory.MAX_ENTRIES + 49}" in ids, "newest entry kept")
        assertFalse("ep0" in ids, "oldest entry dropped")
    }

    @Test
    fun `podcasts with two or more skips are down-ranked`() {
        val memory = newMemory()
        memory.recordSkip("ep1", "podA", "trending")
        memory.recordSkip("ep2", "podA", "subscription")
        memory.recordSkip("ep3", "podB", "trending")

        assertEquals(setOf("podA"), memory.downRankedPodcastIds())
    }

    @Test
    fun `re-recording the same episode does not double count its podcast`() {
        val memory = newMemory()
        memory.recordSkip("ep1", "podA", "trending")
        memory.recordSkip("ep1", "podA", "trending")

        assertTrue(memory.downRankedPodcastIds().isEmpty())
    }

    @Test
    fun `blank episode ids are ignored`() {
        val memory = newMemory()
        memory.recordSkip("", "podA", "trending")
        assertTrue(memory.skippedEpisodeIds().isEmpty())
    }

    @Test
    fun `corrupt raw data degrades to empty instead of throwing`() {
        store = "garbage-without-separators\nanother|line"
        val memory = newMemory()
        assertTrue(memory.skippedEpisodeIds().isEmpty())
    }
}
