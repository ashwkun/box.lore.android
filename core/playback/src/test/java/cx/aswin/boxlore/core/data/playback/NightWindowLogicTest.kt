package cx.aswin.boxlore.core.data.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Calendar

class NightWindowLogicTest {
    @Test
    fun `outside night window returns null`() {
        val cal =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
            }
        assertNull(NightWindowLogic.currentNightWindowId(cal))
    }

    @Test
    fun `late evening uses same calendar day`() {
        val cal =
            Calendar.getInstance().apply {
                set(2026, Calendar.JULY, 18, 23, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        assertEquals("2026-07-18", NightWindowLogic.currentNightWindowId(cal))
    }

    @Test
    fun `early morning shares previous calendar day window id`() {
        val cal =
            Calendar.getInstance().apply {
                set(2026, Calendar.JULY, 19, 2, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        assertEquals("2026-07-18", NightWindowLogic.currentNightWindowId(cal))
    }
}
