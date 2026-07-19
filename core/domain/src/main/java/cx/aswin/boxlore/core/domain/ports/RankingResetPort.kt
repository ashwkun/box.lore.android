package cx.aswin.boxlore.core.domain.ports

/**
 * Narrow ranking reset seam for Settings (and tests).
 *
 * Production: [cx.aswin.boxlore.core.ranking.RankingFeedbackRepository].
 */
fun interface RankingResetPort {
    suspend fun reset(): Boolean
}
