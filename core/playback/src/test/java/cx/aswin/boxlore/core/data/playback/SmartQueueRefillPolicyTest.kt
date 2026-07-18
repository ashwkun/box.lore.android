package cx.aswin.boxlore.core.data.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartQueueRefillPolicyTest {
    @Test
    fun `refills when remaining low and not blocked`() {
        assertTrue(
            SmartQueueRefillPolicy.shouldRefill(
                remainingUpcoming = 2,
                isRefilling = false,
                mediaItemCount = 5,
                isLearnEpisode = false,
                sleepingAtEndOfEpisode = false,
            ),
        )
    }

    @Test
    fun `blocks refill for learn sleep or in-flight refill`() {
        assertFalse(
            SmartQueueRefillPolicy.shouldRefill(1, isRefilling = true, 5, false, false),
        )
        assertFalse(
            SmartQueueRefillPolicy.shouldRefill(1, false, mediaItemCount = 0, false, false),
        )
        assertFalse(
            SmartQueueRefillPolicy.shouldRefill(1, false, 5, isLearnEpisode = true, false),
        )
        assertFalse(
            SmartQueueRefillPolicy.shouldRefill(1, false, 5, false, sleepingAtEndOfEpisode = true),
        )
        assertFalse(
            SmartQueueRefillPolicy.shouldRefill(3, false, 5, false, false),
        )
    }

    @Test
    fun `stripQueuePrefixes removes known media id prefixes`() {
        assertEquals("42", SmartQueueRefillPolicy.stripQueuePrefixes("learn:42"))
        assertEquals("42", SmartQueueRefillPolicy.stripQueuePrefixes("episode:42"))
        assertEquals("42", SmartQueueRefillPolicy.stripQueuePrefixes("queue:42"))
        assertEquals("42", SmartQueueRefillPolicy.stripQueuePrefixes("42"))
    }
}
