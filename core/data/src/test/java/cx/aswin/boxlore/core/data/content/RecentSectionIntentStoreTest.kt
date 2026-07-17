package cx.aswin.boxlore.core.data.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecentSectionIntentStoreTest {
    @Test
    fun `recent intent pruning keeps ordered recency and newest duplicate`() {
        val now = 10_000L
        val records = listOf(
            RecentSectionIntentRecord("older", 8_000L),
            RecentSectionIntentRecord("repeat", 7_000L),
            RecentSectionIntentRecord("newest", 9_000L),
            RecentSectionIntentRecord("repeat", 8_500L),
            RecentSectionIntentRecord("expired", 1_000L),
        )

        val pruned = pruneRecentSectionIntents(
            records = records,
            now = now,
            maximum = 3,
            ttlMillis = 5_000L,
        )

        assertEquals(listOf("newest", "repeat", "older"), pruned.map { it.id })
        assertEquals(8_500L, pruned.first { it.id == "repeat" }.exposedAt)
    }

    @Test
    fun `recent intent pruning enforces endpoint cap and rejects invalid dates`() {
        val now = 1_000_000L
        val records = (0 until 30).map { index ->
            RecentSectionIntentRecord("section-$index", now - index)
        } + listOf(
            RecentSectionIntentRecord("", now),
            RecentSectionIntentRecord("future", now + 1),
        )

        val pruned = pruneRecentSectionIntents(records, now)

        assertEquals(24, pruned.size)
        assertEquals("section-0", pruned.first().id)
        assertEquals("section-23", pruned.last().id)
        assertTrue(pruned.none { it.id == "future" })
    }
}
