package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.network.model.HistoryItem

/** Counts meaningful listens used by [HomePersonalizationModeLogic]. */
object HomeMeaningfulPlayLogic {
    const val MIN_PROGRESS_MS = 60_000L
    const val MIN_PROGRESS_RATIO = 0.20

    fun countMeaningfulPlays(history: List<HistoryItem>): Int =
        history.count(::isMeaningful)

    fun isMeaningful(item: HistoryItem): Boolean {
        if (item.isCompleted == true || item.isLiked == true) return true
        val duration = item.durationMs ?: 0L
        val progress = item.progressMs ?: 0L
        if (progress >= MIN_PROGRESS_MS) return true
        if (duration <= 0L) return false
        return progress.toDouble() / duration >= MIN_PROGRESS_RATIO
    }
}
