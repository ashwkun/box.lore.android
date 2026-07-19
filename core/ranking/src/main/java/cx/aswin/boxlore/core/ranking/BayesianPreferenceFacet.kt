package cx.aswin.boxlore.core.ranking

import kotlin.math.pow

enum class PreferenceFacetType {
    SHOW,
    GENRE,
    SOURCE,
    DURATION_BUCKET,
    TIME_CONTEXT,
    INTENT,
}

data class BayesianPreferenceFacet(
    val positiveEvidence: Double = 0.0,
    val negativeEvidence: Double = 0.0,
    val updatedAt: Long,
) {
    fun decayed(
        now: Long,
        halfLifeMillis: Long = DEFAULT_HALF_LIFE_MILLIS,
    ): BayesianPreferenceFacet {
        if (now <= updatedAt) return copy(updatedAt = now)
        val elapsed = now - updatedAt
        val factor = 2.0.pow(-elapsed.toDouble() / halfLifeMillis)
        return copy(
            positiveEvidence = positiveEvidence * factor,
            negativeEvidence = negativeEvidence * factor,
            updatedAt = now,
        )
    }

    fun update(
        reward: Double,
        now: Long,
    ): BayesianPreferenceFacet {
        val current = decayed(now)
        val boundedReward = reward.coerceIn(-1.0, 1.0)
        return current.copy(
            positiveEvidence = current.positiveEvidence + boundedReward.coerceAtLeast(0.0),
            negativeEvidence = current.negativeEvidence + (-boundedReward).coerceAtLeast(0.0),
        )
    }

    fun affinity(
        now: Long,
        priorStrength: Double = 2.0,
    ): Double {
        val current = decayed(now)
        val boundedPrior = priorStrength.coerceAtLeast(0.001)
        val alpha = boundedPrior / 2.0 + current.positiveEvidence
        val beta = boundedPrior / 2.0 + current.negativeEvidence
        val posterior = alpha / (alpha + beta)
        return ((posterior - 0.5) * 2.0).coerceIn(-1.0, 1.0)
    }

    companion object {
        const val DEFAULT_HALF_LIFE_MILLIS: Long = 90L * 24L * 60L * 60L * 1_000L
    }
}
