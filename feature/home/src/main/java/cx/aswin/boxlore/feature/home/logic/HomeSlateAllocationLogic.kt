package cx.aswin.boxlore.feature.home.logic

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
        val usedEpisodes = mutableSetOf<String>()
        val usedShows = mutableSetOf<String>()

        fun take(
            limit: Int,
            predicate: (Candidate) -> Boolean,
        ): List<Candidate> {
            if (limit <= 0) return emptyList()
            val out = mutableListOf<Candidate>()
            for (candidate in candidates.sortedByDescending(Candidate::score)) {
                if (out.size >= limit) break
                if (!predicate(candidate)) continue
                if (candidate.episodeId in usedEpisodes) continue
                if (candidate.podcastId in usedShows) continue
                out += candidate
                usedEpisodes += candidate.episodeId
                usedShows += candidate.podcastId
            }
            return out
        }

        val taste =
            take(tasteLimit) { candidate ->
                val hintOk =
                    candidate.moduleHint == null ||
                        candidate.moduleHint == Module.TASTE
                val subOk = !excludeSubscriptionsFromTaste || !candidate.isSubscription
                val consumedOk = !candidate.alreadyConsumedShow
                hintOk && subOk && consumedOk &&
                    !candidate.reason.contains("anchor", ignoreCase = true)
            }

        val byl =
            take(bylLimit) { candidate ->
                val hintOk =
                    candidate.moduleHint == null ||
                        candidate.moduleHint == Module.BECAUSE_YOU_LIKE
                hintOk &&
                    (
                        candidate.moduleHint == Module.BECAUSE_YOU_LIKE ||
                            candidate.reason.contains("anchor", ignoreCase = true) ||
                            candidate.reason.contains("because", ignoreCase = true)
                        )
            }

        val mission =
            take(missionLimit) { candidate ->
                val hintOk =
                    candidate.moduleHint == null ||
                        candidate.moduleHint == Module.MISSION
                hintOk
            }

        return AllocatedSlate(
            taste = taste,
            becauseYouLike = byl,
            mission = mission,
        )
    }
}
