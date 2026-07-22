package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.model.HomeSlateQualityTelemetry
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry
import cx.aswin.boxlore.core.model.RankingExposureHealthTelemetry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises the full [AnalyticsHelper] façade, which in turn delegates into the
 * `*AnalyticsTracks` bodies. Events are intercepted via [AnalyticsEmit.installRecordingSink]
 * so no PostHog SDK is required.
 */
class AnalyticsTracksFacadeTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
    }

    private fun names() = recorder.map { it.first }

    private fun firstProps(event: String): Map<String, Any> = recorder.first { it.first == event }.second

    // ── Onboarding ─────────────────────────────────────────────────

    @Test
    fun onboardingBannerAndFlowEvents() {
        AnalyticsHelper.trackHomeImportBannerImpression()
        AnalyticsHelper.trackHomeImportBannerClicked("import")
        AnalyticsHelper.trackHomeImportBannerDismissed()
        AnalyticsHelper.trackOnboardingStarted()
        AnalyticsHelper.trackOnboardingFlowSelected("ai")
        AnalyticsHelper.trackOnboardingSkipped("welcome", 12.5f)

        assertTrue("home_import_banner_action" in names())
        assertTrue("home_surface_tapped" in names())
        assertTrue("onboarding_started" in names())
        assertTrue("onboarding_flow_selected" in names())
        assertTrue("onboarding_completed" in names())
        // skip emits a person set
        assertTrue(AnalyticsGlossary.PERSON_SET_EVENT in names())
    }

    @Test
    fun onboardingAiFlowEvents() {
        AnalyticsHelper.trackOnboardingAiTurnSubmitted(1, setOf("news"), "custom", 3f)
        AnalyticsHelper.trackOnboardingAiResponseReceived(1, "hello", 2, listOf("a", "b"), 1f, "intent")
        AnalyticsHelper.trackOnboardingAiSearchRedirect(2, "query")
        AnalyticsHelper.trackOnboardingAiSearchRedirect(2, null)
        AnalyticsHelper.trackOnboardingAiSynthesisCompleted(3, 4, 2f)
        AnalyticsHelper.trackOnboardingAiSynthesisFailed("boom")
        AnalyticsHelper.trackOnboardingAiDone(2, listOf("p1", "p2"), true, 30f, listOf("news"))

        assertTrue("onboarding_ai_turn_submitted" in names())
        assertTrue("onboarding_ai_response_received" in names())
        assertTrue("onboarding_ai_search_redirect" in names())
        assertTrue("onboarding_ai_synthesis_completed" in names())
        assertTrue("onboarding_ai_synthesis_failed" in names())
    }

    @Test
    fun onboardingSearchImportManualEvents() {
        AnalyticsHelper.trackSearchPerformed("  q  ", 5)
        AnalyticsHelper.trackSearchPodcastSubscribed("Pod", "id", 1)
        AnalyticsHelper.trackOnboardingSearchDone("welcome_screen", 2, listOf("a"), 3, 4f, 30f)
        AnalyticsHelper.trackImportSheetOpened()
        AnalyticsHelper.trackOnboardingImportCompleted("opml", 3, listOf("a", "b"), 30f)
        AnalyticsHelper.trackOnboardingImportCompleted("opml", 3, listOf("a"), 30f, "home_import_banner")
        AnalyticsHelper.trackOnboardingImportFailed("opml", null)
        AnalyticsHelper.trackOnboardingImportFailed("opml", "err")
        AnalyticsHelper.trackOnboardingManualStepCompleted("genres", 2, listOf("news"), 5f)
        AnalyticsHelper.trackOnboardingManualDone(2, listOf("a"), 30f, false, setOf("news", "comedy"))
        AnalyticsHelper.setOnboardingImportCompletedUserProperties(4)

        assertTrue("onboarding_search_performed" in names())
        assertTrue("search_performed" in names())
        assertTrue("onboarding_search_podcast_subscribed" in names())
        assertTrue("onboarding_import_sheet_opened" in names())
        assertTrue("onboarding_import_failed" in names())
        assertTrue("onboarding_manual_step_completed" in names())
        assertTrue("onboarding_step_viewed" in names())
        assertEquals(1, firstProps("onboarding_step_viewed")["step_index"])
        // trimmed query length reflected
        assertEquals(1, firstProps("onboarding_search_performed")["query_length"])
    }

    // ── Discovery ──────────────────────────────────────────────────

    @Test
    fun discoveryAnnouncementAndNotificationEvents() {
        AnalyticsHelper.trackFeatureAnnouncementViewed("f1")
        AnalyticsHelper.trackFeatureAnnouncementDismissed("f1")
        AnalyticsHelper.trackInAppAnnouncementViewed("news", hasImage = true, hasAction = false)
        AnalyticsHelper.trackInAppAnnouncementDismissed("news", hasImage = false, hasAction = true)
        AnalyticsHelper.trackInAppAnnouncementAction("news", hasImage = true, actionLabel = "open")
        AnalyticsHelper.trackNotificationPermissionRequested()
        AnalyticsHelper.trackNotificationPermissionDecided(true)

        assertTrue("feature_announcement_action" in names())
        assertTrue("notification_permission_requested" in names())
        assertTrue("notification_permission_decided" in names())
        assertTrue(AnalyticsGlossary.PERSON_SET_EVENT in names())
    }

    @Test
    fun discoveryHomeAndExploreEvents() {
        AnalyticsHelper.trackHomeHeroCarouselSwiped(3, 5)
        AnalyticsHelper.trackCuratedBlockImpression("Block", listOf("v1", "v2"))
        AnalyticsHelper.trackHomeRecommendationsImpression(3, listOf("e1"), "Morning")
        AnalyticsHelper.trackHomeRecommendationsImpression(3, listOf("e1"), null)
        AnalyticsHelper.trackHomeRecommendationCardTapped("e1", "Title", "p1", "Pod", 0, "Morning")
        AnalyticsHelper.trackHomeRecommendationCardTapped("e1", null, "p1", null, 0, null)
        AnalyticsHelper.trackExploreRecommendationsImpression(2, listOf("e1"))
        AnalyticsHelper.trackExploreRecommendationCardTapped("e1", "Title", "p1", "Pod", 1)
        AnalyticsHelper.trackExploreRecommendationCardTapped("e1", null, "p1", null, 1)

        assertTrue("home_surface_impression" in names())
        assertTrue("home_surface_tapped" in names())
        assertTrue("explore_recommendation_tapped" in names())
    }

    @Test
    fun discoveryPodcastAndEpisodeEvents() {
        AnalyticsHelper.trackPodcastInfoScreenViewed("p1")
        AnalyticsHelper.trackPodcastInfoScreenViewed("p1", "Pod", "home", "News", 5, "q")
        AnalyticsHelper.trackPodcastSubscriptionToggled("p1", "Pod", true, "home")
        AnalyticsHelper.trackPodcastSubscriptionToggled("p1", null, false, "home")
        AnalyticsHelper.trackPodcastInfoScreenSession(
            "p1",
            "Pod",
            12f,
            wasSubscribed = true,
            didSubscribe = false,
            didUnsubscribe = true,
            didSearch = true,
            didSortEpisodes = false,
            episodesPlayedCount = 2,
            episodesClickedCount = 3,
        )
        AnalyticsHelper.trackCuratedCardTapped("p1", "Pod", "vibe", 0)
        AnalyticsHelper.trackCuratedCardTapped("p1", null, "vibe", 0)
        AnalyticsHelper.trackEpisodeInfoScreenViewed(mapOf("id" to "e1"))
        AnalyticsHelper.trackEpisodeInfoScreenViewed(mapOf("episode_id" to "e1"))
        AnalyticsHelper.trackEpisodeInfoScreenSession(mapOf("episode_id" to "e1"))
        AnalyticsHelper.trackProxyFallbackTriggered("cdn.example.com", 320)

        assertTrue("podcast_detail_viewed" in names())
        assertTrue("podcast_subscription_toggled" in names())
        assertTrue("episode_detail_viewed" in names())
        assertTrue("proxy_fallback_triggered" in names())
        assertEquals("e1", firstProps("episode_detail_viewed")["episode_id"])
    }

    // ── Playback ───────────────────────────────────────────────────

    @Test
    fun playbackLifecycleEvents() {
        AnalyticsHelper.trackPlaybackStarted(
            "p1",
            "Pod",
            "News",
            "e1",
            "Ep",
            0f,
            100f,
            isRepeating = false,
            isSubscribed = true,
            entryPoint = "home_hero_resume",
            entryPointContext = mapOf("extra" to "v"),
        )
        AnalyticsHelper.trackPlaybackStarted(
            null,
            null,
            null,
            "e1",
            null,
            0f,
            100f,
            isRepeating = true,
            isSubscribed = false,
        )
        AnalyticsHelper.trackPlaybackPaused(
            "p1",
            "Pod",
            "News",
            "e1",
            "Ep",
            50f,
            10f,
            100f,
            isCompleted = false,
            queueSize = 3,
            pauseReason = "user_voluntary",
        )
        AnalyticsHelper.trackPlaybackPaused(
            null,
            null,
            null,
            "e1",
            null,
            0f,
            0f,
            0f,
            isCompleted = true,
        )
        AnalyticsHelper.trackPlaybackCompleted("p1", "Pod", "News", "e1", "Ep", 100f)
        AnalyticsHelper.trackPlaybackCompleted(null, null, null, "e1", null, 100f)
        AnalyticsHelper.trackPlaybackHeartbeat("p1", "Pod", "e1", "Ep", 25f, 100f, 25, "milestone")
        AnalyticsHelper.trackPlaybackHeartbeat(null, null, "e1", null, 25f, 100f, 25, "milestone")
        AnalyticsHelper.trackPlaybackSeeked("p1", "Pod", "e1", "Ep", 10f, 20f, 100f, "scrubber")
        AnalyticsHelper.trackPlaybackSeeked(null, null, "e1", null, 10f, 20f, 100f, "scrubber")
        AnalyticsHelper.trackPlaybackError("code", "msg", "p1", "e1", "Pod", "Ep")
        AnalyticsHelper.trackPlaybackError("code", "msg", null, null)
        AnalyticsHelper.trackPlaybackBuffering("e1", "p1", "home_hero_resume", 500L)
        AnalyticsHelper.trackPlaybackBuffering()

        assertTrue("playback_started" in names())
        assertTrue("playback_paused" in names())
        assertTrue("playback_completed" in names())
        assertTrue("playback_heartbeat" in names())
        assertTrue("playback_seeked" in names())
        assertTrue("playback_error" in names())
        assertTrue("playback_buffering" in names())
        // percent_complete clamped for zero-duration pause
        assertNotNull(firstProps("playback_paused")["percent_complete"])
    }

    @Test
    fun exploreScreenEvents() {
        AnalyticsHelper.trackExploreScreenViewed("for_you")
        AnalyticsHelper.trackExploreScreenViewed()
        AnalyticsHelper.trackExploreSearchPerformed("  q  ", 5)
        AnalyticsHelper.trackExploreSearchPerformed("semantic", 3, searchMode = "episode_semantic")
        AnalyticsHelper.trackOnboardingStepViewed("genres", "manual_genre", stepIndex = 1)
        AnalyticsHelper.trackExploreScreenSession(
            30f,
            1,
            2,
            3,
            4,
            5,
            "all",
            "vibe",
            "q",
        )
        AnalyticsHelper.trackExploreScreenSession(
            30f,
            1,
            2,
            3,
            4,
            5,
            "all",
            null,
            null,
        )

        assertTrue("explore_screen_viewed" in names())
        assertTrue("search_performed" in names())
        assertEquals("episode_semantic", recorder.last { it.first == "search_performed" }.second["search_mode"])
        assertTrue(recorder.any { it.first == "onboarding_step_viewed" && it.second["step_index"] == 1 })
    }

    // ── Library ────────────────────────────────────────────────────

    @Test
    fun libraryDestinationEvents() {
        AnalyticsHelper.trackLibraryHubViewed("nav")
        AnalyticsHelper.trackLibraryHubSession(10f, "subscriptions")
        AnalyticsHelper.trackLibraryHubSession(10f, null)
        AnalyticsHelper.trackLibrarySubscriptionsViewed("nav", "shows")
        AnalyticsHelper.trackLibrarySubscriptionsSession(10f, 1, true, "q", 2, 3)
        AnalyticsHelper.trackLibrarySubscriptionsSession(10f, 1, false, null, 2, 3)
        AnalyticsHelper.trackLibraryLikedViewed("nav")
        AnalyticsHelper.trackLibraryLikedSession(10f, 1, 2)
        AnalyticsHelper.trackLibraryDownloadsViewed("nav")
        AnalyticsHelper.trackLibraryDownloadsSession(10f, 1, 2)
        AnalyticsHelper.trackLibraryHistoryViewed("nav")
        AnalyticsHelper.trackLibraryHistorySession(10f, 1, 2)
        AnalyticsHelper.trackLibraryHistoryTrackingNotice("shown")
        AnalyticsHelper.trackLibraryHistoryTrackingNotice("dismissed")
        AnalyticsHelper.trackLibrarySubscriptionsLayoutToggled(true)
        AnalyticsHelper.trackLibrarySubscriptionsLayoutToggled(false)
        AnalyticsHelper.trackLibrarySubscriptionsSortChanged("recent", "shows")
        AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered("News", "shows")

        assertTrue("library_destination_viewed" in names())
        assertTrue("library_history_tracking_notice" in names())
        assertEquals(16, names().count { it == "library_destination_viewed" })
        assertEquals(2, names().count { it == "library_history_tracking_notice" })
    }

    @Test
    fun libraryPlayerAndSettingsEvents() {
        AnalyticsHelper.trackTopControlbarInteraction("back", "home")
        AnalyticsHelper.trackSettingsScreenViewed("nav")
        AnalyticsHelper.trackSettingsInteraction("toggle_dark", "on")
        AnalyticsHelper.trackSettingsInteraction("toggle_dark")
        AnalyticsHelper.trackMiniPlayerInteraction("expand", "p1", "e1", "Pod", "Ep")
        AnalyticsHelper.trackMiniPlayerInteraction("expand", null, null)
        AnalyticsHelper.trackFullPlayerScreenSession("p1", "e1", mapOf("k" to "v"), "Pod", "Ep")
        AnalyticsHelper.trackFullPlayerScreenSession(null, null, emptyMap())
        AnalyticsHelper.trackNotificationTapped()
        AnalyticsHelper.trackDownloadCompleted("e1", "p1", source = "manual", fileSizeMb = 12.5f)
        AnalyticsHelper.trackDownloadFailed("network")
        AnalyticsHelper.trackPlayMixClicked(5)
        AnalyticsHelper.trackHomePodcastFiltered("p1", "Pod")

        assertTrue("player_chrome_interaction" in names())
        assertTrue("settings_interaction" in names())
        assertTrue("notification_tapped" in names())
        assertTrue("download_completed" in names())
        assertTrue("download_failed" in names())
        assertTrue("home_surface_tapped" in names())
    }

    // ── Queue / content ────────────────────────────────────────────

    @Test
    fun queueEvents() {
        AnalyticsHelper.trackSmartQueueRefilled(
            AnalyticsHelper.SmartQueueRefillEvent(
                triggeringEpisodeId = "e1",
                triggeringPodcastGenre = "News",
                refilledCount = 3,
                recommendationSources = listOf("server"),
                refilledEpisodeIds = listOf("e2", "e3"),
                region = "us",
                sourceCounts = mapOf("server" to 2),
                usedServerRecommendations = true,
            ),
        )
        AnalyticsHelper.trackQueueReordered("e1", 0, 2, "queue")
        AnalyticsHelper.trackQueueReordered("e1", 0, 2, null)
        AnalyticsHelper.trackLoreQueueConflictShown("e1", 4)
        AnalyticsHelper.trackLoreQueueConflictResult("e1", "accepted")
        AnalyticsHelper.trackSmartQueueEpisodeSkipped("e1", "server", 1)
        AnalyticsHelper.trackOfflineModeEntered()
        AnalyticsHelper.trackDiscoverCategoryFiltered("News")

        assertTrue("queue_modified" in names())
        assertTrue("offline_mode_entered" in names())
        assertTrue("explore_recommendation_tapped" in names())
        assertTrue(firstProps("queue_modified").containsKey("source_count_server"))
    }

    @Test
    fun autoChaptersTranscriptEvents() {
        AnalyticsHelper.trackAutoChaptersRequested("e1", "p1", "http://a")
        AnalyticsHelper.trackAutoChaptersRequested("e1", null, "")
        AnalyticsHelper.trackAutoChaptersCompleted("e1", "p1", 12f, 5)
        AnalyticsHelper.trackAutoChaptersFailed("e1", "p1", "err")
        AnalyticsHelper.trackAutoTranscriptRequested("e1", "p1", "http://a")
        AnalyticsHelper.trackAutoTranscriptRequested("e1", null, "")
        AnalyticsHelper.trackAutoTranscriptCompleted("e1", "p1", 12f, 30)
        AnalyticsHelper.trackAutoTranscriptFailed("e1", "p1", "err")

        assertTrue("auto_chapters_lifecycle" in names())
        assertTrue("auto_transcript_lifecycle" in names())
    }

    @Test
    fun dailyBriefingEvents() {
        AnalyticsHelper.trackDailyBriefingBannerTapped("us", "2024-01-01")
        AnalyticsHelper.trackDailyBriefingPlayClicked("us", "2024-01-01", "banner")
        AnalyticsHelper.trackDailyBriefingPauseClicked("us", "2024-01-01", "banner")
        AnalyticsHelper.trackDailyBriefingInteraction("scroll", "us", "2024-01-01", mapOf("x" to 1))
        AnalyticsHelper.trackDailyBriefingRegionChanged("us", "gb", "2024-01-01")
        AnalyticsHelper.trackDailyBriefingRelatedEpisodeClicked("us", "2024-01-01", 1, "e1", "Ep", "p1", "Pod")
        AnalyticsHelper.trackDailyBriefingCardImpression("us", "2024-01-01", "playing")
        AnalyticsHelper.trackDailyBriefingScreenViewed("us", "2024-01-01", "home")
        AnalyticsHelper.trackDailyBriefingScreenViewed("us", "2024-01-01")

        assertTrue("daily_briefing_action" in names())
        assertTrue("home_surface_tapped" in names())
        assertTrue("home_surface_impression" in names())
    }

    @Test
    fun navAndLearnEvents() {
        AnalyticsHelper.trackNavTabClicked("home")
        AnalyticsHelper.trackLearnScreenViewed()
        AnalyticsHelper.trackLearnCardDismissed("e1", "Ep", "p1", "Pod")
        AnalyticsHelper.trackLearnCardQueued("e1", null, null, null)
        AnalyticsHelper.trackLearnCardInfoClicked("e1", "Ep", "p1", "Pod")
        AnalyticsHelper.trackLearnCardPodcastClicked("p1", "Pod")
        AnalyticsHelper.trackLearnCardPodcastClicked(null, null)
        AnalyticsHelper.trackLearnCardPlayClicked("e1", "Ep", "p1", "Pod")
        AnalyticsHelper.trackLearnScreenSession(30f, 1, 2, 3, 4, 5)

        assertTrue("nav_tab_clicked" in names())
        assertTrue("learn_screen_viewed" in names())
        assertTrue("learn_card_action" in names())
    }

    // ── Phase C ────────────────────────────────────────────────────

    @Test
    fun phaseCEvents() {
        AnalyticsHelper.trackLateNightSafeguardDecision("continue", 30)
        AnalyticsHelper.trackLateNightSafeguardDecision("continue")
        AnalyticsHelper.trackAndroidAutoConnected("s1")
        AnalyticsHelper.trackAndroidAutoConnected()
        AnalyticsHelper.trackAndroidAutoDisconnected("s1", 120)
        AnalyticsHelper.trackAndroidAutoDisconnected()
        AnalyticsHelper.trackAndroidAutoBrowse("root", "browse")
        AnalyticsHelper.trackAndroidAutoBrowse("root")
        AnalyticsHelper.trackLearnCaughtUp(3)
        AnalyticsHelper.trackLearnCaughtUp()
        AnalyticsHelper.trackCatalogMiss("podcast", "p1")
        AnalyticsHelper.trackCatalogMiss("podcast")
        AnalyticsHelper.trackRssRefreshFailed("p1", "timeout")
        AnalyticsHelper.trackRssRefreshFailed()
        AnalyticsHelper.trackProgressSyncAnomaly("overflow", "e1")
        AnalyticsHelper.trackProgressSyncAnomaly("overflow")

        assertTrue("late_night_safeguard_decision" in names())
        assertTrue("android_auto_connected" in names())
        assertTrue("android_auto_disconnected" in names())
        assertTrue("android_auto_browse" in names())
        assertTrue("learn_caught_up" in names())
        assertTrue("catalog_miss" in names())
        assertTrue("rss_refresh_failed" in names())
        assertTrue("progress_sync_anomaly" in names())
    }

    @Test
    fun adaptiveRankingStatusEvents() {
        AnalyticsHelper.trackAdaptiveRankingStatus(emptyList())
        AnalyticsHelper.trackAdaptiveRankingStatus(
            listOf(
                RankingAggregateTelemetry(
                    objective = "discovery",
                    rankerVersion = 1,
                    learningStage = "warm",
                    outcomeCountBucket = "10-50",
                    explorationEligible = true,
                ),
            ),
        )

        val events = recorder.filter { it.first == "adaptive_ranking_status" }
        assertEquals(2, events.size)
        assertEquals("empty", events[0].second["status"])
        assertEquals("discovery:warm", events[1].second["status"])
    }

    // ── quality-observability (Phase D) ───────────────────────────

    @Test
    fun homeSlateQualitySnapshotEvent() {
        AnalyticsHelper.trackHomeSlateQualitySnapshot(
            HomeSlateQualityTelemetry(
                mode = "PERSONALIZED",
                algorithmVersion = "v3",
                fromCache = true,
                isFallback = false,
                requestedModuleCount = 4,
                nonEmptyModuleCount = 3,
                allocatedCandidateCount = 12,
                duplicateRatePercent = 10,
                noveltyRatePercent = 80,
                cacheAgeBucket = "under_15m",
                responseLatencyBucket = "300_999ms",
            ),
        )

        assertTrue("home_slate_quality_snapshot" in names())
        val props = firstProps("home_slate_quality_snapshot")
        assertEquals("PERSONALIZED", props["mode"])
        assertEquals("v3", props["algorithm_version"])
        assertEquals(true, props["from_cache"])
        assertEquals(false, props["is_fallback"])
        assertEquals(4, props["requested_module_count"])
        assertEquals(3, props["non_empty_module_count"])
        assertEquals(12, props["allocated_candidate_count"])
        assertEquals(10, props["duplicate_rate_percent"])
        assertEquals(80, props["novelty_rate_percent"])
        assertEquals("under_15m", props["cache_age_bucket"])
        assertEquals("300_999ms", props["response_latency_bucket"])
    }

    @Test
    fun homeLearningAttributionHealthEvent() {
        AnalyticsHelper.trackHomeLearningAttributionHealth(
            RankingExposureHealthTelemetry(
                surface = "HOME",
                totalExposures = 100,
                resolutionRatePercent = 85,
                pendingExposureCount = 15,
                oldestPendingAgeBucket = "1_5h",
                unmatchedOutcomeRatePercent = 5,
            ),
        )

        assertTrue("home_learning_attribution_health" in names())
        val props = firstProps("home_learning_attribution_health")
        assertEquals("HOME", props["surface"])
        assertEquals(100, props["total_exposures"])
        assertEquals(85, props["resolution_rate_percent"])
        assertEquals(15, props["pending_exposure_count"])
        assertEquals("1_5h", props["oldest_pending_age_bucket"])
        assertEquals(5, props["unmatched_outcome_rate_percent"])
    }
}
