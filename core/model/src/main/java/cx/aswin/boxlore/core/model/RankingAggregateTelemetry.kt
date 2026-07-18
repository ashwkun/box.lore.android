package cx.aswin.boxlore.core.model

data class RankingAggregateTelemetry(
    val objective: String,
    val rankerVersion: Int,
    val learningStage: String,
    val outcomeCountBucket: String,
    val explorationEligible: Boolean,
)
