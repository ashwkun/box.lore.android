package cx.aswin.boxlore.core.catalog.home

/**
 * Because You Like automatic anchor selection using learner SHOW affinity + evidence.
 * Replaces the legacy subscription +100 heuristic.
 */
object HomeAnchorSelectionLogic {
    data class ShowCandidate(
        val podcastId: String,
        val affinity: Double,
        val confidence: Double,
        val evidence: Double,
        val lastPlayedAt: Long = 0L,
        val isHidden: Boolean = false,
    )

    data class Selection(
        val podcastId: String,
        val reason: String,
    )

    /** Minimum evidence mass before a show can become an automatic anchor. */
    const val MIN_EVIDENCE = 0.75

    /** Minimum confidence (0–1) for automatic anchor. */
    const val MIN_CONFIDENCE = 0.35

    /** Minimum affinity in [-1, 1]. */
    const val MIN_AFFINITY = 0.15

    /**
     * Hysteresis: keep [currentAnchorId] unless another candidate exceeds it by this margin.
     */
    const val SWITCH_MARGIN = 0.12

    fun selectAutomatic(
        candidates: List<ShowCandidate>,
        currentAnchorId: String?,
        manualOverrideId: String?,
    ): Selection? {
        if (manualOverrideId != null) {
            val stillPresent = candidates.any { it.podcastId == manualOverrideId && !it.isHidden }
            if (stillPresent || candidates.isEmpty()) {
                return Selection(manualOverrideId, "manual_override")
            }
        }
        val eligible =
            candidates
                .filterNot(ShowCandidate::isHidden)
                .filter {
                    it.affinity >= MIN_AFFINITY &&
                        it.confidence >= MIN_CONFIDENCE &&
                        it.evidence >= MIN_EVIDENCE
                }
        if (eligible.isEmpty()) return null

        val ranked =
            eligible.sortedWith(
                compareByDescending<ShowCandidate> { it.affinity * it.confidence }
                    .thenByDescending { it.lastPlayedAt },
            )
        val top = ranked.first()
        if (currentAnchorId != null) {
            val current = eligible.firstOrNull { it.podcastId == currentAnchorId }
            if (current != null) {
                val topScore = top.affinity * top.confidence
                val currentScore = current.affinity * current.confidence
                if (topScore < currentScore + SWITCH_MARGIN) {
                    return Selection(current.podcastId, "hysteresis")
                }
            }
        }
        return Selection(top.podcastId, "learner_show_affinity")
    }
}
