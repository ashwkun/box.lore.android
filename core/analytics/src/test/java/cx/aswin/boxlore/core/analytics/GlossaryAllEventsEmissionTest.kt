package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.model.HomeSlateQualityTelemetry
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry
import cx.aswin.boxlore.core.model.RankingExposureHealthTelemetry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

/**
 * Full glossary coverage: every `emission:` inventory row fires via a façade,
 * and SDK-backed / person-only rows never dual-emit volume events.
 *
 * Inventory: docs/analytics/glossary_emission_coverage.csv
 */
class GlossaryAllEventsEmissionTest {
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("emissionEventNames")
    fun `emission inventory event is captured`(eventName: String) {
        val emitter = EMITTERS[eventName]
        requireNotNull(emitter) { "No emitter registered for $eventName — extend EMITTERS" }
        emitter()
        assertTrue(
            recorder.any { it.first == eventName },
            "Expected capture of $eventName; got ${recorder.map { it.first }}",
        )
    }

    @Test
    fun `inventory emission events all have emitters`() {
        val missing = emissionEventNames().toList() - EMITTERS.keys
        assertTrue(missing.isEmpty(), "Missing emitters for: $missing")
    }

    companion object {
        private val projectRoot: File =
            System
                .getProperty("boxlore.projectRoot")
                ?.let(::File)
                ?.takeIf { it.isDirectory }
                ?: File(".").canonicalFile.let { dir ->
                    generateSequence(dir) { it.parentFile }
                        .firstOrNull { File(it, "docs/analytics/glossary_emission_coverage.csv").isFile }
                        ?: dir
                }

        @JvmStatic
        fun emissionEventNames(): Stream<String> {
            val file = File(projectRoot, "docs/analytics/glossary_emission_coverage.csv")
            return file
                .readLines()
                .drop(1)
                .map { it.split(',') }
                .filter { it.size >= 2 && it[1].trim().startsWith("emission:") }
                .map { it[0].trim() }
                .stream()
        }

        private val EMITTERS: Map<String, () -> Unit> =
            mapOf(
                "deep_link_opened" to {
                    AnalyticsHelper.trackDeepLinkOpened("boxlore", isFirstOpen = true, coldStart = true)
                },
                "onboarding_started" to { AnalyticsHelper.trackOnboardingStarted() },
                "onboarding_flow_selected" to { AnalyticsHelper.trackOnboardingFlowSelected("ai") },
                "onboarding_step_viewed" to {
                    AnalyticsHelper.trackOnboardingStepViewed("genres", "manual_genre", stepIndex = 1)
                },
                "onboarding_abandoned" to {
                    AnalyticsHelper.trackOnboardingAbandoned("genres", "manual_genre")
                },
                "onboarding_completed" to {
                    AnalyticsHelper.trackOnboardingSkipped("welcome", 1f)
                },
                "onboarding_ai_turn_submitted" to {
                    AnalyticsHelper.trackOnboardingAiTurnSubmitted(1, setOf("news"), "hi", 1f)
                },
                "onboarding_ai_response_received" to {
                    AnalyticsHelper.trackOnboardingAiResponseReceived(1, "hello", 2, listOf("a"), 1f, "intent")
                },
                "onboarding_ai_search_redirect" to {
                    AnalyticsHelper.trackOnboardingAiSearchRedirect(1, "query")
                },
                "onboarding_ai_synthesis_completed" to {
                    AnalyticsHelper.trackOnboardingAiSynthesisCompleted(2, 3, 1f)
                },
                "onboarding_ai_synthesis_failed" to {
                    AnalyticsHelper.trackOnboardingAiSynthesisFailed("boom")
                },
                "onboarding_search_performed" to {
                    AnalyticsHelper.trackSearchPerformed("q", 1)
                },
                "onboarding_search_podcast_subscribed" to {
                    AnalyticsHelper.trackSearchPodcastSubscribed("Pod", "p1", 1)
                },
                "onboarding_import_sheet_opened" to { AnalyticsHelper.trackImportSheetOpened() },
                "onboarding_import_failed" to {
                    AnalyticsHelper.trackOnboardingImportFailed("opml", "err")
                },
                "onboarding_manual_step_completed" to {
                    AnalyticsHelper.trackOnboardingManualStepCompleted("genres", 2, listOf("news"), 1f)
                },
                "playback_started" to {
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
                    )
                },
                "playback_heartbeat" to {
                    AnalyticsHelper.trackPlaybackHeartbeat("p1", "Pod", "e1", "Ep", 25f, 100f, 25, "milestone")
                },
                "playback_paused" to {
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
                    )
                },
                "playback_completed" to {
                    AnalyticsHelper.trackPlaybackCompleted("p1", "Pod", "News", "e1", "Ep", 100f)
                },
                "playback_error" to {
                    AnalyticsHelper.trackPlaybackError("code", "msg", "p1", "e1")
                },
                "playback_buffering" to {
                    AnalyticsHelper.trackPlaybackBuffering("e1", "p1", "home_hero_resume", 100L)
                },
                "playback_seeked" to {
                    AnalyticsHelper.trackPlaybackSeeked("p1", "Pod", "e1", "Ep", 10f, 20f, 100f, "scrubber")
                },
                "session_restore_prompt" to {
                    AnalyticsHelper.trackSessionRestorePrompt("shown", "e1", "p1", 12f)
                },
                "podcast_subscription_toggled" to {
                    AnalyticsHelper.trackPodcastSubscriptionToggled("p1", "Pod", true, "home")
                },
                "notification_permission_requested" to {
                    AnalyticsHelper.trackNotificationPermissionRequested()
                },
                "notification_permission_decided" to {
                    AnalyticsHelper.trackNotificationPermissionDecided(true)
                },
                "notification_tapped" to {
                    AnalyticsHelper.trackNotificationTapped("episode", "p1", "e1", "player")
                },
                "notification_received" to {
                    AnalyticsHelper.trackNotificationReceived("episode", "p1", "e1")
                },
                "identity_reset" to {
                    // Avoid PostHog.reset() in JVM tests — emit via allowlisted egress.
                    AnalyticsEmit.event("identity_reset", mapOf("reason" to "user_reset"))
                },
                "app_check_status" to {
                    AnalyticsHelper.trackAppCheckStatus(tokenObtained = true, provider = "debug")
                },
                "first_episode_played" to { AnalyticsHelper.trackFirstEpisodePlayed() },
                "home_surface_tapped" to {
                    AnalyticsHelper.trackHomeRecommendationCardTapped("e1", "T", "p1", "Pod", 0, "Morning")
                },
                "home_surface_impression" to {
                    AnalyticsHelper.trackHomeRecommendationsImpression(1, listOf("e1"), "Morning")
                },
                "search_performed" to {
                    AnalyticsHelper.trackExploreSearchPerformed("q", 2, searchMode = "episode_semantic")
                },
                "search_result_tapped" to {
                    AnalyticsHelper.trackSearchResultTapped(
                        "explore",
                        "episode",
                        "p1",
                        "e1",
                        0,
                        "q",
                        "episode_semantic",
                    )
                },
                "learn_card_action" to {
                    AnalyticsHelper.trackLearnCardPlayClicked("e1", "Ep", "p1", "Pod")
                },
                "learn_screen_viewed" to { AnalyticsHelper.trackLearnScreenViewed() },
                "explore_screen_viewed" to { AnalyticsHelper.trackExploreScreenViewed("for_you") },
                "explore_recommendation_tapped" to {
                    AnalyticsHelper.trackExploreRecommendationCardTapped("e1", "T", "p1", "Pod", 0)
                },
                "queue_modified" to {
                    AnalyticsHelper.trackQueueModified("add", "e1", "p1", queueSize = 2, source = "player")
                },
                "episode_liked_toggled" to {
                    AnalyticsHelper.trackEpisodeLikedToggled("e1", "p1", isLiked = true, surface = "history")
                },
                "episode_mark_played" to {
                    AnalyticsHelper.trackEpisodeMarkPlayed("e1", "p1", isPlayed = true, surface = "history")
                },
                "download_requested" to {
                    AnalyticsHelper.trackDownloadRequested("e1", "p1", "episode_detail")
                },
                "download_completed" to {
                    AnalyticsHelper.trackDownloadCompleted("e1", "p1", "manual", 1.5f)
                },
                "download_failed" to {
                    AnalyticsHelper.trackDownloadFailed("network", "e1", "p1", "manual")
                },
                "smart_download_sync" to {
                    AnalyticsHelper.trackSmartDownloadSync(1, 1, 0, 0, "periodic")
                },
                "show_notification_toggled" to {
                    AnalyticsHelper.trackShowNotificationToggled("p1", enabled = true)
                },
                "share_content" to {
                    AnalyticsHelper.trackShareContent("episode", "p1", "e1", "system", "player")
                },
                "backup_restore_result" to {
                    AnalyticsHelper.trackBackupRestoreResult("export", success = true, itemCount = 3, format = "opml")
                },
                "feedback_submitted" to {
                    AnalyticsHelper.trackEngagementPromptShown("nps", "home", 5)
                },
                "library_destination_viewed" to {
                    AnalyticsHelper.trackLibraryHubViewed("nav")
                },
                "podcast_detail_viewed" to {
                    AnalyticsHelper.trackPodcastInfoScreenViewed("p1")
                },
                "episode_detail_viewed" to {
                    AnalyticsHelper.trackEpisodeInfoScreenViewed(mapOf("episode_id" to "e1"))
                },
                "nav_tab_clicked" to {
                    AnalyticsHelper.trackNavTabClicked("explore", previousTab = "home")
                },
                "settings_interaction" to {
                    AnalyticsHelper.trackSettingsInteraction("toggle_dark", "on")
                },
                "feature_announcement_action" to {
                    AnalyticsHelper.trackFeatureAnnouncementViewed("f1")
                },
                "offline_mode_entered" to { AnalyticsHelper.trackOfflineModeEntered() },
                "player_chrome_interaction" to {
                    AnalyticsHelper.trackMiniPlayerInteraction("expand", "p1", "e1")
                },
                "daily_briefing_action" to {
                    AnalyticsHelper.trackDailyBriefingBannerTapped("us", "2026-07-20")
                },
                "home_import_banner_action" to {
                    AnalyticsHelper.trackHomeImportBannerImpression()
                },
                "library_history_tracking_notice" to {
                    AnalyticsHelper.trackLibraryHistoryTrackingNotice("shown")
                },
                "android_auto_connected" to { AnalyticsHelper.trackAndroidAutoConnected("s1") },
                "android_auto_disconnected" to {
                    AnalyticsHelper.trackAndroidAutoDisconnected("s1", durationSeconds = 10)
                },
                "android_auto_browse" to {
                    AnalyticsHelper.trackAndroidAutoBrowse("queue", "open")
                },
                "adaptive_ranking_status" to {
                    AnalyticsHelper.trackAdaptiveRankingStatus(
                        listOf(
                            RankingAggregateTelemetry(
                                objective = "continue",
                                rankerVersion = 1,
                                learningStage = "cold",
                                outcomeCountBucket = "0",
                                explorationEligible = true,
                            ),
                        ),
                    )
                },
                "learn_caught_up" to { AnalyticsHelper.trackLearnCaughtUp(0) },
                "catalog_miss" to { AnalyticsHelper.trackCatalogMiss("podcast", "p1") },
                "rss_refresh_failed" to {
                    AnalyticsHelper.trackRssRefreshFailed("p1", "network")
                },
                "progress_sync_anomaly" to {
                    AnalyticsHelper.trackProgressSyncAnomaly("rewind", "e1")
                },
                "late_night_safeguard_decision" to {
                    AnalyticsHelper.trackLateNightSafeguardDecision("allow", 30)
                },
                "auto_chapters_lifecycle" to {
                    AnalyticsHelper.trackAutoChaptersRequested("e1", "p1", "https://example.com/a.mp3")
                },
                "auto_transcript_lifecycle" to {
                    AnalyticsHelper.trackAutoTranscriptRequested("e1", "p1", "https://example.com/a.mp3")
                },
                "proxy_fallback_triggered" to {
                    AnalyticsHelper.trackProxyFallbackTriggered("cdn.example.com", 320)
                },
                "home_slate_quality_snapshot" to {
                    AnalyticsHelper.trackHomeSlateQualitySnapshot(
                        HomeSlateQualityTelemetry(
                            mode = "PERSONALIZED",
                            algorithmVersion = "v3",
                            fromCache = false,
                            isFallback = false,
                            requestedModuleCount = 4,
                            nonEmptyModuleCount = 3,
                            allocatedCandidateCount = 12,
                            duplicateRatePercent = 10,
                            noveltyRatePercent = 80,
                            cacheAgeBucket = "not_cached",
                            responseLatencyBucket = "300_999ms",
                        ),
                    )
                },
                "home_learning_attribution_health" to {
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
                },
            )
    }
}

/**
 * Regression: growth/session helpers must never dual-emit SDK volume events.
 */
class LifecycleSdkMappingTest {
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

    @Test
    fun `growth helpers never emit app_open or app_background`() {
        AnalyticsHelper.trackDeepLinkOpened("boxlore", isFirstOpen = true)
        AnalyticsHelper.trackOnboardingAbandoned("welcome", "ai")
        AnalyticsHelper.trackSessionRestorePrompt("dismiss")
        AnalyticsHelper.trackLaunchPersonEnrichment("pending", "1_3")
        AnalyticsHelper.trackInstallChannelAttributed("play_store")

        val names = recorder.map { it.first }.toSet()
        assertFalse("app_open" in names)
        assertFalse("app_background" in names)
        assertFalse("install_attributed" in names)
    }

    @Test
    fun `BoxLoreApplication keeps SDK lifecycle on and deep link capture off`() {
        val source =
            File(
                projectRoot(),
                "app/src/main/java/cx/aswin/boxlore/BoxLoreApplication.kt",
            ).readText()
        assertTrue(
            source.contains("captureApplicationLifecycleEvents = true"),
            "SDK Application Opened/Backgrounded must stay enabled",
        )
        assertTrue(
            source.contains("captureDeepLinks = false"),
            "Deep links stay custom (deep_link_opened) — SDK deep-link capture off",
        )
    }

    private fun projectRoot(): File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: File(".").canonicalFile.let { dir ->
                generateSequence(dir) { it.parentFile }
                    .firstOrNull { File(it, "settings.gradle.kts").isFile }
                    ?: dir
            }
}

/** Install channel is person `$set_once` only — never install_attributed volume. */
class InstallChannelAttributionTest {
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

    @Test
    fun `install channel sets person props without install_attributed event`() {
        AnalyticsHelper.trackInstallChannelAttributed(
            installChannel = "share_referrer",
            referrerRaw = "utm_source=share",
            utmSource = "share",
            shareToken = "abc",
        )
        assertFalse(recorder.any { it.first == "install_attributed" })
        assertTrue(recorder.any { it.first == AnalyticsGlossary.PERSON_SET_EVENT })
        val props = recorder.first { it.first == AnalyticsGlossary.PERSON_SET_EVENT }.second

        @Suppress("UNCHECKED_CAST")
        val once = props["\$set_once"] as Map<String, Any>
        assertTrue(once["install_channel"] == "share_referrer")
    }
}
