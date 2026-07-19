package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranscriptDialogLogicTest {
    @Test
    fun unknownAndNegativeDurationsUseSafeEstimate() {
        assertEquals("~1-2 min", estimateTranscriptTime(-1L))
        assertEquals("~1-2 min", estimateTranscriptTime(0L))
    }

    @Test
    fun estimateBoundariesAreStable() {
        assertEquals("~30s", estimateTranscriptTime(1L))
        assertEquals("~30s", estimateTranscriptTime(599L))
        assertEquals("~1 min", estimateTranscriptTime(600L))
        assertEquals("~1 min", estimateTranscriptTime(1_799L))
        assertEquals("~1-2 min", estimateTranscriptTime(1_800L))
        assertEquals("~1-2 min", estimateTranscriptTime(3_599L))
        assertEquals("~2-3 min", estimateTranscriptTime(3_600L))
        assertEquals("~2-3 min", estimateTranscriptTime(20_000L))
    }

    @Test
    fun unknownLimitAllowsGeneration() {
        val state = TranscriptDialogState("~1 min", null)

        assertTrue(state.canGenerate)
        assertFalse(state.limitReached)
        assertEquals("AI transcription is in beta and may contain errors.", state.supportingText)
    }

    @Test
    fun positiveLimitAllowsGeneration() {
        val state = TranscriptDialogState("~1 min", 3)

        assertTrue(state.canGenerate)
        assertFalse(state.limitReached)
    }

    @Test
    fun zeroLimitBlocksGenerationAndExplainsWhy() {
        val state = TranscriptDialogState("~1 min", 0)

        assertFalse(state.canGenerate)
        assertTrue(state.limitReached)
        assertEquals(
            "Daily AI limit reached. Please try again tomorrow.",
            state.supportingText,
        )
    }

    @Test
    fun negativeLimitIsDefensivelyBlocked() {
        val state = TranscriptDialogState("~1 min", -1)

        assertFalse(state.canGenerate)
        assertFalse(state.limitReached)
    }
}
