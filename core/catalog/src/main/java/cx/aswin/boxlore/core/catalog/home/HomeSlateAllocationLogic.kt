package cx.aswin.boxlore.core.catalog.home

/**
 * Allocates retrieved candidates across Taste / Because You Like / Mission with
 * global show+episode de-duplication. Pure JVM logic for unit tests.
 */
object HomeSlateAllocationLogic {
    enum class Module {
        TASTE,
        BECAUSE_YOU_LIKE,
        MISSION,
    }

    data class Candidate(
        val episodeId: String,
        val podcastId: String,
        val score: Double,
        val reason: String,
        val moduleHint: Module?,
        val isNovel: Boolean = false,
        val isSubscription: Boolean = false,
        val alreadyConsumedShow: Boolean = false,
    )

    data class AllocatedSlate(
        val taste: List<Candidate>,
        val becauseYouLike: List<Candidate>,
        val mission: List<Candidate>,
    )

    fun allocate(
        candidates: List<Candidate>,
        tasteLimit: Int,
        bylLimit: Int,
        missionLimit: Int,
        excludeSubscriptionsFromTaste: Boolean = true,
    ): AllocatedSlate {
        val ranked = candidates.sortedByDescending(Candidate::score)
        val usedEpisodes = mutableSetOf<String>()
        val usedShows = mutableSetOf<String>()

        fun take(
            limit: Int,
            predicate: (Candidate) -> Boolean,
        ): List<Candidate> {
            if (limit <= 0) return emptyList()
            return ranked
                .asSequence()
                .filter { candidate ->
                    val available =
                        candidate.episodeId !in usedEpisodes &&
                            candidate.podcastId !in usedShows &&
                            predicate(candidate)
                    if (available) {
                        usedEpisodes += candidate.episodeId
                        usedShows += candidate.podcastId
                    }
                    available
                }.take(limit)
                .toList()
        }

        val taste =
            take(tasteLimit) { candidate ->
                isTasteEligible(candidate, excludeSubscriptionsFromTaste)
            }
        val byl = take(bylLimit, ::isBecauseYouLikeEligible)
        val mission = take(missionLimit, ::isMissionEligible)

        return AllocatedSlate(
            taste = taste,
            becauseYouLike = byl,
            mission = mission,
        )
    }

    private fun isTasteEligible(
        candidate: Candidate,
        excludeSubscriptionsFromTaste: Boolean,
    ): Boolean {
        val hintOk =
            candidate.moduleHint == null ||
                candidate.moduleHint == Module.TASTE
        val subOk = !excludeSubscriptionsFromTaste || !candidate.isSubscription
        val consumedOk = !candidate.alreadyConsumedShow
        return hintOk &&
            subOk &&
            consumedOk &&
            !candidate.reason.contains("anchor", ignoreCase = true)
    }

    private fun isBecauseYouLikeEligible(candidate: Candidate): Boolean {
        val hintOk =
            candidate.moduleHint == null ||
                candidate.moduleHint == Module.BECAUSE_YOU_LIKE
        val reasonOk =
            candidate.moduleHint == Module.BECAUSE_YOU_LIKE ||
                candidate.reason.contains("anchor", ignoreCase = true) ||
                candidate.reason.contains("because", ignoreCase = true)
        return hintOk && reasonOk
    }

    private fun isMissionEligible(candidate: Candidate): Boolean = candidate.moduleHint == null || candidate.moduleHint == Module.MISSION
}
