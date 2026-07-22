package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.ranking.FacetConfidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeAnchorConfidenceLogicTest {
    @Test
    fun `confidence is zero with no evidence and rises toward one as evidence grows`() {
        assertEquals(0.0, HomeAnchorConfidenceLogic.confidence(0.0))
        assertEquals(0.5, HomeAnchorConfidenceLogic.confidence(3.0), 0.0001)
        assertTrue(HomeAnchorConfidenceLogic.confidence(30.0) > 0.9)
    }

    @Test
    fun `confidence clamps negative evidence to zero`() {
        assertEquals(0.0, HomeAnchorConfidenceLogic.confidence(-5.0))
    }

    @Test
    fun `toShowCandidates maps affinity and evidence into a Bayesian confidence`() {
        val candidates =
            HomeAnchorConfidenceLogic.toShowCandidates(
                topShowAffinities = listOf(FacetConfidence(affinity = 0.8, evidence = 3.0, key = "show-1")),
                hiddenPodcastIds = emptySet(),
                hardExcludedPodcastIds = emptySet(),
            )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals("show-1", candidate.podcastId)
        assertEquals(0.8, candidate.affinity)
        assertEquals(3.0, candidate.evidence)
        assertEquals(0.5, candidate.confidence, 0.0001)
        assertEquals(false, candidate.isHidden)
    }

    @Test
    fun `toShowCandidates marks hidden and hard-excluded shows`() {
        val candidates =
            HomeAnchorConfidenceLogic.toShowCandidates(
                topShowAffinities =
                    listOf(
                        FacetConfidence(affinity = 0.6, evidence = 2.0, key = "hidden-show"),
                        FacetConfidence(affinity = 0.7, evidence = 2.0, key = "excluded-show"),
                        FacetConfidence(affinity = 0.9, evidence = 2.0, key = "eligible-show"),
                    ),
                hiddenPodcastIds = setOf("hidden-show"),
                hardExcludedPodcastIds = setOf("excluded-show"),
            )

        val byId = candidates.associateBy { it.podcastId }
        assertTrue(byId.getValue("hidden-show").isHidden)
        assertTrue(byId.getValue("excluded-show").isHidden)
        assertTrue(!byId.getValue("eligible-show").isHidden)
    }

    @Test
    fun `toShowCandidates drops blank keys`() {
        val candidates =
            HomeAnchorConfidenceLogic.toShowCandidates(
                topShowAffinities =
                    listOf(
                        FacetConfidence(affinity = 0.5, evidence = 1.0, key = ""),
                        FacetConfidence(affinity = 0.5, evidence = 1.0, key = "valid"),
                    ),
                hiddenPodcastIds = emptySet(),
                hardExcludedPodcastIds = emptySet(),
            )

        assertEquals(listOf("valid"), candidates.map { it.podcastId })
    }
}
