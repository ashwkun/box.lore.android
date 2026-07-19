package cx.aswin.boxlore.core.ranking

import kotlin.math.ln

enum class RankingAction {
    OPEN_DETAILS,
    MEANINGFUL_PLAY,
    COMPLETE,
    LIKE,
    UNLIKE,
    SUBSCRIBE,
    UNSUBSCRIBE,
    EXPLICIT_QUEUE,
    MANUAL_DOWNLOAD,
    EARLY_SKIP,
    REMOVE_AUTOFILLED,
    MOVE_UP,
    MOVE_DOWN,
    DISMISS,
}

data class RankingOutcome(
    val actions: Set<RankingAction> = emptySet(),
    val listenSeconds: Long = 0,
    val durationSeconds: Long = 0,
)

object RankingReward {
    private val actionWeights = mapOf(
        RankingAction.OPEN_DETAILS to 0.08,
        RankingAction.MEANINGFUL_PLAY to 0.22,
        RankingAction.COMPLETE to 0.35,
        RankingAction.LIKE to 0.65,
        RankingAction.UNLIKE to -0.5,
        RankingAction.SUBSCRIBE to 0.8,
        RankingAction.UNSUBSCRIBE to -0.7,
        RankingAction.EXPLICIT_QUEUE to 0.55,
        RankingAction.MANUAL_DOWNLOAD to 0.55,
        RankingAction.EARLY_SKIP to -0.7,
        RankingAction.REMOVE_AUTOFILLED to -0.8,
        RankingAction.MOVE_UP to 0.25,
        RankingAction.MOVE_DOWN to -0.25,
        RankingAction.DISMISS to -0.75,
    )

    fun calculate(outcome: RankingOutcome): Double {
        val actionReward = outcome.actions.sumOf { actionWeights[it] ?: 0.0 }
        val listenReward = listeningValue(outcome.listenSeconds, outcome.durationSeconds)
        return (actionReward + listenReward).coerceIn(-1.0, 1.0)
    }

    private fun listeningValue(
        listenSeconds: Long,
        durationSeconds: Long,
    ): Double {
        if (listenSeconds <= 0) return 0.0
        val absoluteValue = (ln(1.0 + listenSeconds.coerceAtMost(3_600)) / ln(3_601.0)) * 0.2
        val progressValue = if (durationSeconds > 0) {
            (listenSeconds.toDouble() / durationSeconds)
                .coerceIn(0.0, 1.0) * 0.2
        } else {
            0.0
        }
        return absoluteValue + progressValue
    }
}
