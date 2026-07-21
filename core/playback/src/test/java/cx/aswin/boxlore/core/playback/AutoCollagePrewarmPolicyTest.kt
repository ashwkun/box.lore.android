package cx.aswin.boxlore.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCollagePrewarmPolicyTest {
    @Test
    fun forceAlwaysRuns() {
        assertTrue(
            AutoCollagePrewarmPolicy.shouldRun(
                force = true,
                lastPrewarmAtMs = 1_000L,
                nowMs = 1_001L,
            ),
        )
    }

    @Test
    fun throttlesWithinInterval() {
        val last = 10_000L
        assertFalse(
            AutoCollagePrewarmPolicy.shouldRun(
                force = false,
                lastPrewarmAtMs = last,
                nowMs = last + AutoCollagePrewarmPolicy.MIN_REFRESH_INTERVAL_MS - 1,
            ),
        )
        assertTrue(
            AutoCollagePrewarmPolicy.shouldRun(
                force = false,
                lastPrewarmAtMs = last,
                nowMs = last + AutoCollagePrewarmPolicy.MIN_REFRESH_INTERVAL_MS,
            ),
        )
    }
}
