package cx.aswin.boxlore.feature.home.logic

/**
 * Fixed greeting discovery mission catalog. Exactly one mission is active at a time.
 * Rotate only when daypart / exposure / novelty warrant — not on every recomposition.
 */
enum class HomeDiscoveryMission(
    val id: String,
    val title: String,
    val subtitle: String,
    val maxDurationSeconds: Int? = null,
    val preferFresh: Boolean = false,
    val stretchBeyondTaste: Boolean = false,
) {
    FRESH_DROPS(
        id = "fresh_drops",
        title = "Fresh drops",
        subtitle = "Recently published picks adjacent to your taste",
        preferFresh = true,
    ),
    SHORT_LISTENS(
        id = "short_listens",
        title = "Short listens",
        subtitle = "High-confidence episodes under about 25 minutes",
        maxDurationSeconds = 25 * 60,
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
        stretchBeyondTaste = true,
    ),
}

object HomeDiscoveryMissionLogic {
    data class MissionContext(
        val daypart: String,
        val recentMissionIds: List<String> = emptyList(),
        val noveltyPreference: Double = 0.5,
        val preferShort: Boolean = false,
    )

    fun select(
        context: MissionContext,
        nowSlotKey: String,
        stickyMissionId: String?,
        stickySlotKey: String?,
    ): HomeDiscoveryMission {
        // Keep sticky mission within the same daypart slot.
        if (stickyMissionId != null && stickySlotKey == nowSlotKey) {
            HomeDiscoveryMission.entries
                .firstOrNull { it.id == stickyMissionId }
                ?.let { return it }
        }
        val recent = context.recentMissionIds.toSet()
        val ranked = rankedCandidates(context)
            .filterNot { it.id in recent }
            .ifEmpty { rankedCandidates(context) }
        return ranked.first()
    }

    fun slotKey(daypart: String, localDate: String): String = "$localDate:$daypart"

    private fun rankedCandidates(context: MissionContext): List<HomeDiscoveryMission> {
        val base =
            when {
                context.preferShort ||
                    context.daypart.equals("morning", ignoreCase = true) ->
                    listOf(
                        HomeDiscoveryMission.SHORT_LISTENS,
                        HomeDiscoveryMission.FRESH_DROPS,
                        HomeDiscoveryMission.DEEP_DIVES,
                        HomeDiscoveryMission.ADJACENT_STRETCH,
                    )
                context.daypart.equals("evening", ignoreCase = true) ||
                    context.daypart.equals("night", ignoreCase = true) ->
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
        return if (context.noveltyPreference >= 0.65) {
            listOf(HomeDiscoveryMission.ADJACENT_STRETCH) +
                base.filterNot { it == HomeDiscoveryMission.ADJACENT_STRETCH }
        } else {
            base
        }
    }
}
