package cx.aswin.boxlore.core.catalog.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomePersonalizationLogicTest {
    @Test
    fun modeStaysRegionalUntilTwoMeaningfulPlays() {
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
    fun modePersonalizesAfterTwoPlaysWhenCandidatesLoad() {
        assertEquals(
            HomePersonalizationMode.PERSONALIZED,
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = 2,
                hasEligiblePositiveShow = false,
                personalizedCandidatesLoaded = true,
                personalizedRequestFailed = false,
            ),
        )
    }

    @Test
    fun thirdPlayRequiresEligibleShow() {
        assertEquals(
            HomePersonalizationMode.REGIONAL,
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = 3,
                hasEligiblePositiveShow = false,
                personalizedCandidatesLoaded = true,
                personalizedRequestFailed = false,
            ),
        )
        assertEquals(
            HomePersonalizationMode.PERSONALIZED,
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = 3,
                hasEligiblePositiveShow = true,
                personalizedCandidatesLoaded = true,
                personalizedRequestFailed = false,
            ),
        )
    }

    @Test
    fun allocateDedupesShowsAcrossModules() {
        val slate =
            HomeSlateAllocationLogic.allocate(
                candidates =
                    listOf(
                        HomeSlateAllocationLogic.Candidate(
                            episodeId = "e1",
                            podcastId = "p1",
                            score = 1.0,
                            reason = "taste",
                            moduleHint = HomeSlateAllocationLogic.Module.TASTE,
                        ),
                        HomeSlateAllocationLogic.Candidate(
                            episodeId = "e2",
                            podcastId = "p1",
                            score = 0.9,
                            reason = "anchor",
                            moduleHint = HomeSlateAllocationLogic.Module.BECAUSE_YOU_LIKE,
                        ),
                        HomeSlateAllocationLogic.Candidate(
                            episodeId = "e3",
                            podcastId = "p2",
                            score = 0.8,
                            reason = "mission",
                            moduleHint = HomeSlateAllocationLogic.Module.MISSION,
                        ),
                    ),
                tasteLimit = 5,
                bylLimit = 5,
                missionLimit = 5,
            )
        assertEquals(listOf("e1"), slate.taste.map { it.episodeId })
        assertTrue(slate.becauseYouLike.isEmpty())
        assertEquals(listOf("e3"), slate.mission.map { it.episodeId })
    }

    @Test
    fun anchorManualOverrideWins() {
        val selection =
            HomeAnchorSelectionLogic.selectAutomatic(
                candidates =
                    listOf(
                        HomeAnchorSelectionLogic.ShowCandidate(
                            podcastId = "auto",
                            affinity = 0.9,
                            confidence = 0.9,
                            evidence = 2.0,
                        ),
                        HomeAnchorSelectionLogic.ShowCandidate(
                            podcastId = "manual",
                            affinity = 0.2,
                            confidence = 0.4,
                            evidence = 1.0,
                        ),
                    ),
                currentAnchorId = "auto",
                manualOverrideId = "manual",
            )
        assertEquals("manual", selection?.podcastId)
        assertEquals("manual_override", selection?.reason)
    }

    @Test
    fun anchorHysteresisKeepsCurrentUnlessMarginBeaten() {
        val selection =
            HomeAnchorSelectionLogic.selectAutomatic(
                candidates =
                    listOf(
                        HomeAnchorSelectionLogic.ShowCandidate(
                            podcastId = "current",
                            affinity = 0.5,
                            confidence = 0.8,
                            evidence = 2.0,
                        ),
                        HomeAnchorSelectionLogic.ShowCandidate(
                            podcastId = "challenger",
                            affinity = 0.55,
                            confidence = 0.8,
                            evidence = 2.0,
                        ),
                    ),
                currentAnchorId = "current",
                manualOverrideId = null,
            )
        assertEquals("current", selection?.podcastId)
        assertEquals("hysteresis", selection?.reason)
    }

    @Test
    fun weakShowIsNotEligibleAnchor() {
        assertNull(
            HomeAnchorSelectionLogic.selectAutomatic(
                candidates =
                    listOf(
                        HomeAnchorSelectionLogic.ShowCandidate(
                            podcastId = "weak",
                            affinity = 0.1,
                            confidence = 0.2,
                            evidence = 0.1,
                        ),
                    ),
                currentAnchorId = null,
                manualOverrideId = null,
            ),
        )
    }

    @Test
    fun tasteTitlesStayHonestInRegionalMode() {
        assertEquals(
            "Popular in your Region",
            HomePersonalizationModeLogic.tasteSectionTitle(HomePersonalizationMode.REGIONAL),
        )
        assertFalse(
            HomePersonalizationModeLogic.showBecauseYouLike(HomePersonalizationMode.REGIONAL),
        )
    }

    @Test
    fun missionStaysStickyWithinSameSlot() {
        val slot = HomeDiscoveryMissionLogic.slotKey(daypart = "morning", localDate = "2026-07-22")
        val selected =
            HomeDiscoveryMissionLogic.select(
                context = HomeDiscoveryMissionLogic.MissionContext(daypart = "morning"),
                nowSlotKey = slot,
                stickyMissionId = HomeDiscoveryMission.DEEP_DIVES.id,
                stickySlotKey = slot,
            )
        assertEquals(HomeDiscoveryMission.DEEP_DIVES, selected)
    }

    @Test
    fun missionRotatesAwayFromRecentWhenSlotChanges() {
        val selected =
            HomeDiscoveryMissionLogic.select(
                context =
                    HomeDiscoveryMissionLogic.MissionContext(
                        daypart = "morning",
                        recentMissionIds = listOf(HomeDiscoveryMission.SHORT_LISTENS.id),
                    ),
                nowSlotKey = "2026-07-22:morning",
                stickyMissionId = HomeDiscoveryMission.SHORT_LISTENS.id,
                stickySlotKey = "2026-07-21:morning",
            )
        assertFalse(selected == HomeDiscoveryMission.SHORT_LISTENS)
    }

    @Test
    fun missionPrefersMorningShortListensWhenNoRecentHistory() {
        val selected =
            HomeDiscoveryMissionLogic.select(
                context = HomeDiscoveryMissionLogic.MissionContext(daypart = "morning"),
                nowSlotKey = "2026-07-22:morning",
                stickyMissionId = null,
                stickySlotKey = null,
            )
        assertEquals(HomeDiscoveryMission.SHORT_LISTENS, selected)
    }

    @Test
    fun highNoveltyPreferenceSurfacesAdjacentStretchFirst() {
        val selected =
            HomeDiscoveryMissionLogic.select(
                context =
                    HomeDiscoveryMissionLogic.MissionContext(
                        daypart = "afternoon",
                        noveltyPreference = 0.9,
                    ),
                nowSlotKey = "2026-07-22:afternoon",
                stickyMissionId = null,
                stickySlotKey = null,
            )
        assertEquals(HomeDiscoveryMission.ADJACENT_STRETCH, selected)
    }

    @Test
    fun missionFromIdRoundTrips() {
        assertEquals(HomeDiscoveryMission.ADJACENT_STRETCH, HomeDiscoveryMission.fromId("adjacent_stretch"))
        assertNull(HomeDiscoveryMission.fromId("unknown_mission"))
    }
}
