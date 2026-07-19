package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Coverage for the stateful [PlayerSessionAggregator] singleton. Each test drives a full
 * session (start → actions → end) and asserts the metrics emitted via [AnalyticsEmit].
 */
class PlayerSessionAggregatorTest {
    private val captured = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        // Ensure no stale session from another test leaks into this one.
        PlayerSessionAggregator.endSession()
        captured.clear()
        restore = AnalyticsEmit.installRecordingSink(captured)
    }

    @AfterEach
    fun tearDown() {
        PlayerSessionAggregator.endSession()
        restore()
    }

    private fun lastSession() = captured.last { it.first == "player_chrome_interaction" }.second

    @Test
    fun endSessionAggregatesActionCounts() {
        PlayerSessionAggregator.startSession("pod-1", "ep-1", "Podcast", "Episode")
        PlayerSessionAggregator.logAction("play_pause")
        PlayerSessionAggregator.logAction("play_pause")
        PlayerSessionAggregator.logAction("seek")
        PlayerSessionAggregator.logAction("like")

        PlayerSessionAggregator.endSession()

        val session = lastSession()
        assertEquals("pod-1", session["podcast_id"])
        assertEquals("ep-1", session["episode_id"])
        assertEquals(2, session["play_pause_count"])
        assertEquals(1, session["seek_count"])
        assertEquals(1, session["like_count"])
        assertEquals(0, session["next_count"])
    }

    @Test
    fun terminalPropertyValuesAreEmittedAsFinalValues() {
        PlayerSessionAggregator.startSession("pod-1", "ep-1")
        PlayerSessionAggregator.logAction("speed_change", "1.5")
        PlayerSessionAggregator.logAction("speed_change", "2.0")

        PlayerSessionAggregator.endSession()

        val session = lastSession()
        assertEquals("2.0", session["final_speed_change_value"])
        assertEquals(2, session["speed_change_count"])
    }

    @Test
    fun logActionWithoutActiveSessionIsIgnored() {
        PlayerSessionAggregator.logAction("play_pause")
        // No active session means no emission on the (no-op) endSession.
        PlayerSessionAggregator.endSession()

        assertTrue(captured.none { it.first == "player_chrome_interaction" })
    }

    @Test
    fun endSessionWithoutStartEmitsNothing() {
        PlayerSessionAggregator.endSession()
        assertTrue(captured.isEmpty())
    }

    @Test
    fun startingNewEpisodeFlushesPreviousSession() {
        PlayerSessionAggregator.startSession("pod-1", "ep-1")
        PlayerSessionAggregator.logAction("seek")

        // Switching episodes should flush the first session before starting the second.
        PlayerSessionAggregator.startSession("pod-2", "ep-2")

        val flushed = lastSession()
        assertEquals("ep-1", flushed["episode_id"])
        assertEquals(1, flushed["seek_count"])
    }

    @Test
    fun restartingSameEpisodeDoesNotResetCounts() {
        PlayerSessionAggregator.startSession("pod-1", "ep-1")
        PlayerSessionAggregator.logAction("next")
        // Same episode: the second start is a no-op that preserves accumulated counts.
        PlayerSessionAggregator.startSession("pod-1", "ep-1")
        PlayerSessionAggregator.logAction("next")

        PlayerSessionAggregator.endSession()

        assertEquals(2, lastSession()["next_count"])
    }

    @Test
    fun timeSpentSecondsIsNonNegative() {
        PlayerSessionAggregator.startSession("pod-1", "ep-1")
        PlayerSessionAggregator.endSession()

        val elapsed = lastSession()["time_spent_seconds"] as Float
        assertTrue(elapsed >= 0f)
    }
}
