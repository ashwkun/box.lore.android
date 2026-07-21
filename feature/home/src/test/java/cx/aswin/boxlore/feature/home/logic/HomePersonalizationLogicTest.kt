package cx.aswin.boxlore.feature.home.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePersonalizationModeLogicTest {
    @Test
    fun zeroOrOnePlayStaysRegional() {
        assertEquals(
            HomePersonalizationMode.REGIONAL,
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = 0,
                hasEligiblePositiveShow = false,
                personalizedCandidatesLoaded = false,
                personalizedRequestFailed = false,
            ),
        )
        assertEquals(
            HomePersonalizationMode.REGIONAL,
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = 1,
                hasEligiblePositiveShow = true,
                personalizedCandidatesLoaded = true,
                personalizedRequestFailed = false,
            ),
        )
    }

    @Test
    fun secondPlayAttemptsPersonalizing() {
        assertEquals(
            HomePersonalizationMode.PERSONALIZING,
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = 2,
                hasEligiblePositiveShow = true,
                personalizedCandidatesLoaded = false,
                personalizedRequestFailed = false,
            ),
        )
    }

    @Test
    fun thirdPlayWithoutEligibleShowStaysRegional() {
        assertEquals(
            HomePersonalizationMode.REGIONAL,
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = 3,
                hasEligiblePositiveShow = false,
                personalizedCandidatesLoaded = false,
                personalizedRequestFailed = false,
            ),
        )
    }

    @Test
    fun loadedCandidatesBecomePersonalized() {
        assertEquals(
            HomePersonalizationMode.PERSONALIZED,
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = 3,
                hasEligiblePositiveShow = true,
                personalizedCandidatesLoaded = true,
                personalizedRequestFailed = false,
            ),
        )
        assertTrue(
            HomePersonalizationModeLogic.showBecauseYouLike(
                HomePersonalizationMode.PERSONALIZED,
            ),
        )
        assertFalse(
            HomePersonalizationModeLogic.showBecauseYouLike(
                HomePersonalizationMode.REGIONAL,
            ),
        )
    }

    @Test
    fun titlesStayHonestInRegional() {
        assertEquals(
            "Popular in your Region",
            HomePersonalizationModeLogic.tasteSectionTitle(HomePersonalizationMode.REGIONAL),
        )
        assertEquals(
            "Based on Your Taste",
            HomePersonalizationModeLogic.tasteSectionTitle(HomePersonalizationMode.PERSONALIZED),
        )
    }
}

class HomeDiscoveryMissionLogicTest {
    @Test
    fun stickyMissionPersistsWithinSlot() {
        val selected =
            HomeDiscoveryMissionLogic.select(
                context =
                    HomeDiscoveryMissionLogic.MissionContext(
                        daypart = "morning",
                        preferShort = true,
                    ),
                nowSlotKey = "2026-07-21:morning",
                stickyMissionId = HomeDiscoveryMission.DEEP_DIVES.id,
                stickySlotKey = "2026-07-21:morning",
            )
        assertEquals(HomeDiscoveryMission.DEEP_DIVES, selected)
    }

    @Test
    fun morningPrefersShortListensWhenNoSticky() {
        val selected =
            HomeDiscoveryMissionLogic.select(
                context =
                    HomeDiscoveryMissionLogic.MissionContext(
                        daypart = "morning",
                        preferShort = true,
                    ),
                nowSlotKey = "2026-07-21:morning",
                stickyMissionId = null,
                stickySlotKey = null,
            )
        assertEquals(HomeDiscoveryMission.SHORT_LISTENS, selected)
    }

    @Test
    fun highNoveltyPrefersAdjacentStretch() {
        val selected =
            HomeDiscoveryMissionLogic.select(
                context =
                    HomeDiscoveryMissionLogic.MissionContext(
                        daypart = "afternoon",
                        noveltyPreference = 0.8,
                    ),
                nowSlotKey = "2026-07-21:afternoon",
                stickyMissionId = null,
                stickySlotKey = null,
            )
        assertEquals(HomeDiscoveryMission.ADJACENT_STRETCH, selected)
    }
}

class HomeAnchorSelectionLogicTest {
    @Test
    fun manualOverrideWins() {
        val selection =
            HomeAnchorSelectionLogic.selectAutomatic(
                candidates =
                    listOf(
                        show("a", affinity = 0.9, confidence = 0.9, evidence = 2.0),
                        show("b", affinity = 0.2, confidence = 0.4, evidence = 1.0),
                    ),
                currentAnchorId = "a",
                manualOverrideId = "b",
            )
        assertEquals("b", selection?.podcastId)
        assertEquals("manual_override", selection?.reason)
    }

    @Test
    fun weakEvidenceIsIneligible() {
        assertNull(
            HomeAnchorSelectionLogic.selectAutomatic(
                candidates =
                    listOf(
                        show("a", affinity = 0.9, confidence = 0.1, evidence = 0.1),
                    ),
                currentAnchorId = null,
                manualOverrideId = null,
            ),
        )
    }

    @Test
    fun hysteresisKeepsCurrentUnlessMarginExceeded() {
        val kept =
            HomeAnchorSelectionLogic.selectAutomatic(
                candidates =
                    listOf(
                        show("current", affinity = 0.5, confidence = 0.8, evidence = 2.0),
                        show("challenger", affinity = 0.55, confidence = 0.8, evidence = 2.0),
                    ),
                currentAnchorId = "current",
                manualOverrideId = null,
            )
        assertEquals("current", kept?.podcastId)

        val switched =
            HomeAnchorSelectionLogic.selectAutomatic(
                candidates =
                    listOf(
                        show("current", affinity = 0.4, confidence = 0.8, evidence = 2.0),
                        show("challenger", affinity = 0.8, confidence = 0.9, evidence = 2.0),
                    ),
                currentAnchorId = "current",
                manualOverrideId = null,
            )
        assertEquals("challenger", switched?.podcastId)
    }

    private fun show(
        id: String,
        affinity: Double,
        confidence: Double,
        evidence: Double,
    ) = HomeAnchorSelectionLogic.ShowCandidate(
        podcastId = id,
        affinity = affinity,
        confidence = confidence,
        evidence = evidence,
    )
}

class HomeSlateAllocationLogicTest {
    @Test
    fun deduplicatesShowsAcrossModules() {
        val slate =
            HomeSlateAllocationLogic.allocate(
                candidates =
                    listOf(
                        candidate("e1", "p1", 1.0, "taste", HomeSlateAllocationLogic.Module.TASTE),
                        candidate("e2", "p1", 0.9, "anchor", HomeSlateAllocationLogic.Module.BECAUSE_YOU_LIKE),
                        candidate("e3", "p2", 0.8, "mission", HomeSlateAllocationLogic.Module.MISSION),
                        candidate("e4", "p3", 0.7, "anchor", HomeSlateAllocationLogic.Module.BECAUSE_YOU_LIKE),
                    ),
                tasteLimit = 2,
                bylLimit = 2,
                missionLimit = 2,
            )
        assertEquals(listOf("e1"), slate.taste.map { it.episodeId })
        assertEquals(listOf("e4"), slate.becauseYouLike.map { it.episodeId })
        assertEquals(listOf("e3"), slate.mission.map { it.episodeId })
    }

    @Test
    fun tasteExcludesSubscriptions() {
        val slate =
            HomeSlateAllocationLogic.allocate(
                candidates =
                    listOf(
                        candidate(
                            "e1",
                            "p1",
                            1.0,
                            "taste",
                            HomeSlateAllocationLogic.Module.TASTE,
                            isSubscription = true,
                        ),
                        candidate("e2", "p2", 0.5, "taste", HomeSlateAllocationLogic.Module.TASTE),
                    ),
                tasteLimit = 2,
                bylLimit = 0,
                missionLimit = 0,
            )
        assertEquals(listOf("e2"), slate.taste.map { it.episodeId })
    }

    private fun candidate(
        episodeId: String,
        podcastId: String,
        score: Double,
        reason: String,
        hint: HomeSlateAllocationLogic.Module,
        isSubscription: Boolean = false,
    ) = HomeSlateAllocationLogic.Candidate(
        episodeId = episodeId,
        podcastId = podcastId,
        score = score,
        reason = reason,
        moduleHint = hint,
        isSubscription = isSubscription,
    )
}

class HomeMeaningfulPlayLogicTest {
    @Test
    fun countsCompletedLikedAndProgress() {
        val history =
            listOf(
                cx.aswin.boxlore.core.network.model.HistoryItem(
                    podcastTitle = "a",
                    episodeTitle = "e1",
                    podcastId = "1",
                    progressMs = 10_000L,
                    durationMs = 100_000L,
                ),
                cx.aswin.boxlore.core.network.model.HistoryItem(
                    podcastTitle = "b",
                    episodeTitle = "e2",
                    podcastId = "2",
                    isCompleted = true,
                ),
                cx.aswin.boxlore.core.network.model.HistoryItem(
                    podcastTitle = "c",
                    episodeTitle = "e3",
                    podcastId = "3",
                    isLiked = true,
                ),
                cx.aswin.boxlore.core.network.model.HistoryItem(
                    podcastTitle = "d",
                    episodeTitle = "e4",
                    podcastId = "4",
                    progressMs = 70_000L,
                    durationMs = 100_000L,
                ),
            )
        assertEquals(3, HomeMeaningfulPlayLogic.countMeaningfulPlays(history))
    }
}
