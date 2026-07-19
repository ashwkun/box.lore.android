package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryRecommendationLogicTest {
    @Test
    fun `manual and bulk completions are excluded`() {
        assertFalse(
            HistoryRecommendationLogic.isEligible(
                isManualCompletion = true,
                isBulkCompletion = false,
                progressMs = 120_000L,
                isCompleted = true,
            ),
        )
        assertFalse(
            HistoryRecommendationLogic.isEligible(
                isManualCompletion = false,
                isBulkCompletion = true,
                progressMs = 120_000L,
                isCompleted = true,
            ),
        )
    }

    @Test
    fun `short accidental plays are excluded unless completed`() {
        assertFalse(
            HistoryRecommendationLogic.isEligible(
                isManualCompletion = false,
                isBulkCompletion = false,
                progressMs = 10_000L,
                isCompleted = false,
            ),
        )
        assertTrue(
            HistoryRecommendationLogic.isEligible(
                isManualCompletion = false,
                isBulkCompletion = false,
                progressMs = 10_000L,
                isCompleted = true,
            ),
        )
        assertTrue(
            HistoryRecommendationLogic.isEligible(
                isManualCompletion = false,
                isBulkCompletion = false,
                progressMs = HistoryRecommendationLogic.MIN_PROGRESS_MS,
                isCompleted = false,
            ),
        )
    }

    @Test
    fun `selectEligible preserves order and respects limit`() {
        data class Row(
            val id: String,
            val ok: Boolean,
        )
        val raw =
            listOf(
                Row("a", true),
                Row("b", false),
                Row("c", true),
                Row("d", true),
            )
        val selected = HistoryRecommendationLogic.selectEligible(raw, limit = 2) { it.ok }
        assertEquals(listOf("a", "c"), selected.map { it.id })
    }
}
