package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.catalog.home.HomeAnchorSelectionLogic
import cx.aswin.boxlore.core.ranking.FacetConfidence

/**
 * Bridges [cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository.topShowAffinities] (affinity +
 * evidence mass, no confidence) into [HomeAnchorSelectionLogic.ShowCandidate] for Because-You-Like
 * automatic anchor selection.
 *
 * Confidence is a simple Bayesian shrinkage of evidence mass so a show with a single strong play
 * does not immediately qualify as a confident anchor — it must clear both
 * [HomeAnchorSelectionLogic.MIN_EVIDENCE] and [HomeAnchorSelectionLogic.MIN_CONFIDENCE].
 */
internal object HomeAnchorConfidenceLogic {
    /** Evidence mass at which confidence reaches 0.5; tuned against [HomeAnchorSelectionLogic.MIN_EVIDENCE]. */
    private const val EVIDENCE_PRIOR = 3.0

    fun confidence(evidence: Double): Double {
        val nonNegative = evidence.coerceAtLeast(0.0)
        return nonNegative / (nonNegative + EVIDENCE_PRIOR)
    }

    fun toShowCandidates(
        topShowAffinities: List<FacetConfidence>,
        hiddenPodcastIds: Set<String>,
        hardExcludedPodcastIds: Set<String>,
    ): List<HomeAnchorSelectionLogic.ShowCandidate> =
        topShowAffinities
            .filter { it.key.isNotBlank() }
            .map { facet ->
                HomeAnchorSelectionLogic.ShowCandidate(
                    podcastId = facet.key,
                    affinity = facet.affinity,
                    confidence = confidence(facet.evidence),
                    evidence = facet.evidence,
                    isHidden = facet.key in hiddenPodcastIds || facet.key in hardExcludedPodcastIds,
                )
            }
}
