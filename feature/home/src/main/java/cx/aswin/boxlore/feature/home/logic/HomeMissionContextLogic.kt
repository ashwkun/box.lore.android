package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.catalog.home.HomeDiscoveryMissionLogic

/**
 * Bridges Home's [ContentDaypart] clock context and sticky-mission DataStore state into
 * [HomeDiscoveryMissionLogic.MissionContext] for the greeting discovery mission rail.
 */
internal object HomeMissionContextLogic {
    fun daypartId(daypart: ContentDaypart): String = daypart.name.lowercase()

    /**
     * Builds the rotation context for the current slot. When the sticky slot has rolled over to a
     * new day/daypart, the previous day's mission id is fed back in as "recent" so the rotation
     * does not repeat it on the very first slot of the new day.
     */
    fun buildContext(
        daypart: ContentDaypart,
        stickyMissionId: String?,
        stickySlotKey: String?,
        nowSlotKey: String,
        noveltyPreference: Double = 0.5,
    ): HomeDiscoveryMissionLogic.MissionContext =
        HomeDiscoveryMissionLogic.MissionContext(
            daypart = daypartId(daypart),
            recentMissionIds =
                if (stickyMissionId != null && stickySlotKey != nowSlotKey) {
                    listOf(stickyMissionId)
                } else {
                    emptyList()
                },
            noveltyPreference = noveltyPreference,
        )
}
