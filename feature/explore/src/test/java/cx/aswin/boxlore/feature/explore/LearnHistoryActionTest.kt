package cx.aswin.boxlore.feature.explore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LearnHistoryActionTest {
    @Test
    fun `toStorageValue lowercases the enum name`() {
        assertEquals("dismiss", LearnHistoryAction.DISMISS.toStorageValue())
        assertEquals("queue", LearnHistoryAction.QUEUE.toStorageValue())
    }

    @Test
    fun `fromStorageValue round-trips known values`() {
        assertEquals(LearnHistoryAction.DISMISS, LearnHistoryAction.fromStorageValue("dismiss"))
        assertEquals(LearnHistoryAction.QUEUE, LearnHistoryAction.fromStorageValue("queue"))
    }

    @Test
    fun `fromStorageValue defaults to dismiss for null unknown or wrong case`() {
        assertEquals(LearnHistoryAction.DISMISS, LearnHistoryAction.fromStorageValue(null))
        assertEquals(LearnHistoryAction.DISMISS, LearnHistoryAction.fromStorageValue("garbage"))
        assertEquals(LearnHistoryAction.DISMISS, LearnHistoryAction.fromStorageValue("QUEUE"))
    }
}
