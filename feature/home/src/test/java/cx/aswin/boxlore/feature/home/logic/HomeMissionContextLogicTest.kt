package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeMissionContextLogicTest {
    @Test
    fun `daypartId lowercases the enum name`() {
        assertEquals("morning", HomeMissionContextLogic.daypartId(ContentDaypart.MORNING))
        assertEquals("late_night", HomeMissionContextLogic.daypartId(ContentDaypart.LATE_NIGHT))
    }

    @Test
    fun `buildContext feeds the previous sticky mission as recent when the slot rolled over`() {
        val context =
            HomeMissionContextLogic.buildContext(
                daypart = ContentDaypart.MORNING,
                stickyMissionId = "short_listens",
                stickySlotKey = "2026-07-21:morning",
                nowSlotKey = "2026-07-22:morning",
            )

        assertEquals("morning", context.daypart)
        assertEquals(listOf("short_listens"), context.recentMissionIds)
    }

    @Test
    fun `buildContext omits recent missions when the slot has not changed`() {
        val context =
            HomeMissionContextLogic.buildContext(
                daypart = ContentDaypart.EVENING,
                stickyMissionId = "deep_dives",
                stickySlotKey = "2026-07-22:evening",
                nowSlotKey = "2026-07-22:evening",
            )

        assertTrue(context.recentMissionIds.isEmpty())
    }

    @Test
    fun `buildContext omits recent missions when there is no sticky mission yet`() {
        val context =
            HomeMissionContextLogic.buildContext(
                daypart = ContentDaypart.AFTERNOON,
                stickyMissionId = null,
                stickySlotKey = null,
                nowSlotKey = "2026-07-22:afternoon",
            )

        assertTrue(context.recentMissionIds.isEmpty())
    }

    @Test
    fun `buildContext forwards the novelty preference`() {
        val context =
            HomeMissionContextLogic.buildContext(
                daypart = ContentDaypart.AFTERNOON,
                stickyMissionId = null,
                stickySlotKey = null,
                nowSlotKey = "2026-07-22:afternoon",
                noveltyPreference = 0.9,
            )

        assertEquals(0.9, context.noveltyPreference)
    }
}
