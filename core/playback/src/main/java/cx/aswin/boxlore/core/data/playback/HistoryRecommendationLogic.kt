package cx.aswin.boxlore.core.data.playback

/**
 * Pure history filtering for personalized recommendation requests.
 * Extracted from [cx.aswin.boxlore.core.data.PlaybackRepository.getHistoryForRecommendations].
 */
object HistoryRecommendationLogic {
    const val MIN_PROGRESS_MS = 60_000L

    fun isEligible(
        isManualCompletion: Boolean,
        isBulkCompletion: Boolean,
        progressMs: Long,
        isCompleted: Boolean,
    ): Boolean {
        if (isManualCompletion || isBulkCompletion) return false
        return progressMs >= MIN_PROGRESS_MS || isCompleted
    }

    fun <T> selectEligible(
        raw: List<T>,
        limit: Int,
        isEligible: (T) -> Boolean,
    ): List<T> =
        raw
            .asSequence()
            .filter(isEligible)
            .take(limit)
            .toList()
}
