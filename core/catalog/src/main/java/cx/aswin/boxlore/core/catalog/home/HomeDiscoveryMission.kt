package cx.aswin.boxlore.core.catalog.home

/**
 * Fixed greeting discovery mission catalog. Exactly one mission is active at a time and its
 * [id] is sent as `missionId` on `/home/candidates/v1` so the proxy resolves matching retrieval.
 * UI copy may diverge from [id] (e.g. [ADJACENT_STRETCH] reads "Something adjacent").
 */
enum class HomeDiscoveryMission(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    FRESH_DROPS(
        id = "fresh_drops",
        title = "Fresh drops",
        subtitle = "Recently published picks adjacent to your taste",
    ),
    SHORT_LISTENS(
        id = "short_listens",
        title = "Short listens",
        subtitle = "High-confidence episodes under about 25 minutes",
    ),
    DEEP_DIVES(
        id = "deep_dives",
        title = "Deep dives",
        subtitle = "Longer episodes for a focused listening window",
    ),
    ADJACENT_STRETCH(
        id = "adjacent_stretch",
        title = "Something adjacent",
        subtitle = "One step outside your closest taste neighborhood",
    ),
    ;

    companion object {
        fun fromId(id: String?): HomeDiscoveryMission? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Rotates exactly one greeting discovery mission per daypart slot. Pure and JVM-testable;
 * empty-mission omission (no candidates returned) is handled by the caller after retrieval.
 */
object HomeDiscoveryMissionLogic {
    data class MissionContext(
        val daypart: String,
        val recentMissionIds: List<String> = emptyList(),
        val noveltyPreference: Double = 0.5,
        val preferShort: Boolean = false,
    )

    fun slotKey(
        daypart: String,
        localDate: String,
    ): String = "$localDate:$daypart"

    /**
     * Keeps [stickyMissionId] for the remainder of [stickySlotKey] (same daypart/day) so the
     * mission does not flip on every recomposition or background refresh. Once the slot
     * changes, ranks candidates and skips missions shown in [MissionContext.recentMissionIds]
     * when possible.
     */
    fun select(
        context: MissionContext,
        nowSlotKey: String,
        stickyMissionId: String?,
        stickySlotKey: String?,
    ): HomeDiscoveryMission {
        if (stickyMissionId != null && stickySlotKey == nowSlotKey) {
            HomeDiscoveryMission.fromId(stickyMissionId)?.let { return it }
        }
        val recent = context.recentMissionIds.toSet()
        val ranked = rankedCandidates(context)
        return ranked.firstOrNull { it.id !in recent } ?: ranked.first()
    }

    private fun rankedCandidates(context: MissionContext): List<HomeDiscoveryMission> {
        val base =
            when {
                context.preferShort || context.daypart.equals("morning", ignoreCase = true) ->
                    listOf(
                        HomeDiscoveryMission.SHORT_LISTENS,
                        HomeDiscoveryMission.FRESH_DROPS,
                        HomeDiscoveryMission.DEEP_DIVES,
                        HomeDiscoveryMission.ADJACENT_STRETCH,
                    )
                context.daypart.equals("evening", ignoreCase = true) ||
                    context.daypart.equals("night", ignoreCase = true) ||
                    context.daypart.equals("late_night", ignoreCase = true) ->
                    listOf(
                        HomeDiscoveryMission.DEEP_DIVES,
                        HomeDiscoveryMission.FRESH_DROPS,
                        HomeDiscoveryMission.SHORT_LISTENS,
                        HomeDiscoveryMission.ADJACENT_STRETCH,
                    )
                else ->
                    listOf(
                        HomeDiscoveryMission.FRESH_DROPS,
                        HomeDiscoveryMission.SHORT_LISTENS,
                        HomeDiscoveryMission.DEEP_DIVES,
                        HomeDiscoveryMission.ADJACENT_STRETCH,
                    )
            }
        if (context.noveltyPreference < HIGH_NOVELTY_THRESHOLD) return base
        return listOf(HomeDiscoveryMission.ADJACENT_STRETCH) +
            base.filterNot { it == HomeDiscoveryMission.ADJACENT_STRETCH }
    }

    private const val HIGH_NOVELTY_THRESHOLD = 0.65
}
