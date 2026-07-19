package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.analytics.AnalyticsEmit
import cx.aswin.boxlore.core.analytics.AnalyticsGlossary
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.RecordingAnalytics
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecordingAnalyticsTest {

    private lateinit var analytics: RecordingAnalytics

    @BeforeEach
    fun setUp() {
        analytics = RecordingAnalytics()
    }

    @Test
    fun `capture records event with properties`() {
        analytics.capture("playback_started", mapOf("key" to "value"))

        assertEquals(1, analytics.eventCount("playback_started"))
        assertEquals("value", analytics.events.first().properties["key"])
    }

    @Test
    fun `capture with no properties records empty map`() {
        analytics.capture("nav_tab_clicked")

        assertEquals(1, analytics.eventCount("nav_tab_clicked"))
        assertTrue(analytics.events.first().properties.isEmpty())
    }

    @Test
    fun `capture drops non glossary event names`() {
        analytics.capture("legacy_home_recommendation_card_tapped")
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `multiple captures are all recorded`() {
        repeat(3) { analytics.capture("search_performed") }

        assertEquals(3, analytics.eventCount("search_performed"))
    }

    @Test
    fun `eventsNamed filters correctly`() {
        analytics.capture("learn_card_action")
        analytics.capture("queue_modified")
        analytics.capture("learn_card_action", mapOf("x" to 1))

        assertEquals(2, analytics.eventsNamed("learn_card_action").size)
        assertEquals(1, analytics.eventsNamed("queue_modified").size)
    }

    @Test
    fun `lastEvent returns most recently captured event`() {
        analytics.capture("app_open")
        analytics.capture("app_background")

        assertEquals("app_background", analytics.lastEvent?.name)
    }

    @Test
    fun `clear removes all events`() {
        analytics.capture("app_open")
        analytics.capture("app_background")

        analytics.clear()

        assertTrue(analytics.events.isEmpty())
        assertNull(analytics.lastEvent)
    }

    @Test
    fun `trackAdaptiveRankingStatus emits adaptive_ranking_status`() {
        val telemetry = listOf(
            RankingAggregateTelemetry("DISCOVERY", 1, "adaptive", "50_199", true),
            RankingAggregateTelemetry("COMPLETION", 1, "learning", "10_49", false),
        )

        analytics.trackAdaptiveRankingStatus(telemetry)

        assertEquals(1, analytics.eventCount("adaptive_ranking_status"))
        assertEquals(
            "DISCOVERY:adaptive,COMPLETION:learning",
            analytics.lastEvent!!.properties["status"],
        )
    }

    @Test
    fun `trackEngagementPromptShown emits feedback_submitted`() {
        analytics.trackEngagementPromptShown("nps", "home_screen", 5)

        val event = analytics.lastEvent!!
        assertEquals("feedback_submitted", event.name)
        assertEquals("nps", event.properties["feedback_type"])
        assertEquals("home_screen", event.properties["source"])
        assertEquals(5, event.properties["completed_episodes"])
    }

    @Test
    fun `trackEngagementPromptShown omits completedEpisodes when null`() {
        analytics.trackEngagementPromptShown("review", "settings")

        val event = analytics.lastEvent!!
        assertTrue("completed_episodes" !in event.properties)
    }

    @Test
    fun `flush is a no-op and does not throw`() {
        analytics.flush()
        assertTrue(analytics.events.isEmpty())
    }
}

class DeriveGenrePersonaTest {

    @Test
    fun `single knowledge genre gives highly_focused knowledge_seeker`() {
        val result = AnalyticsHelper.deriveGenrePersona(setOf("News"))
        assertEquals("highly_focused", result["genre_breadth"])
        assertEquals("knowledge_seeker", result["listener_profile"])
        assertEquals("casual", result["genre_enthusiasm"])
    }

    @Test
    fun `mixed genres across all three categories gives broad_explorer`() {
        val result = AnalyticsHelper.deriveGenrePersona(setOf("News", "Comedy", "Health"))
        assertEquals("broad_explorer", result["genre_breadth"])
    }

    @Test
    fun `true_crime plus comedy gives lighthearted_detective`() {
        val result = AnalyticsHelper.deriveGenrePersona(setOf("True Crime", "Comedy"))
        assertEquals("lighthearted_detective", result["listener_profile"])
    }

    @Test
    fun `six or more genres gives obsessive enthusiasm`() {
        val result = AnalyticsHelper.deriveGenrePersona(
            setOf("News", "Technology", "Business", "Education", "Science", "History"),
        )
        assertEquals("obsessive", result["genre_enthusiasm"])
    }

    @Test
    fun `empty genres gives unknown breadth and eclectic_explorer profile`() {
        val result = AnalyticsHelper.deriveGenrePersona(emptySet())
        assertEquals("unknown", result["genre_breadth"])
        assertEquals("eclectic_explorer", result["listener_profile"])
    }
}

class AnalyticsGlossaryAllowlistTest {

    @Test
    fun `phase A union B has expected cardinality`() {
        // 35 Phase A + 1 A/B (search_performed) + 28 Phase B = 64
        assertEquals(64, AnalyticsGlossary.PHASE_A_UNION_B.size)
    }

    @Test
    fun `legacy names are not in allowlist`() {
        assertFalse(AnalyticsGlossary.isAllowedEvent("home_recommendation_card_tapped"))
        assertFalse(AnalyticsGlossary.isAllowedEvent("learn_card_dismissed"))
        assertFalse(AnalyticsGlossary.isAllowedEvent("explore_search_performed"))
        assertFalse(AnalyticsGlossary.isAllowedEvent("queue_reordered"))
        assertFalse(AnalyticsGlossary.isAllowedEvent("survey_nps_eligible"))
        assertTrue(AnalyticsGlossary.isAllowedEvent("adaptive_ranking_status"))
        assertTrue(AnalyticsGlossary.isAllowedEvent("android_auto_connected"))
        assertTrue(AnalyticsGlossary.isAllowedEvent("android_auto_browse"))
        assertEquals(12, AnalyticsGlossary.PHASE_C.size)
    }

    @Test
    fun `consolidated Phase B names are allowed`() {
        assertTrue(AnalyticsGlossary.isAllowedEvent("home_surface_tapped"))
        assertTrue(AnalyticsGlossary.isAllowedEvent("learn_card_action"))
        assertTrue(AnalyticsGlossary.isAllowedEvent("queue_modified"))
        assertTrue(AnalyticsGlossary.isAllowedEvent("search_performed"))
        assertTrue(AnalyticsGlossary.isAllowedEvent("feedback_submitted"))
        assertTrue(AnalyticsGlossary.isAllowedEvent("\$set"))
    }

    @Test
    fun `facade emissions after representative tracks are only glossary Phase A union B union C`() {
        val captured = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsEmit.installRecordingSink(captured)
        try {
            AnalyticsHelper.trackHomeRecommendationCardTapped(
                episodeId = "e1",
                episodeTitle = "Title",
                podcastId = "p1",
                podcastName = "Pod",
                positionIndex = 0,
                timeBlockTitle = "Morning",
            )
            AnalyticsHelper.trackLearnCardPlayClicked("e1", "Title", "p1", "Pod")
            AnalyticsHelper.trackQueueReordered("e1", 0, 1, "MANUAL")
            AnalyticsHelper.trackExploreSearchPerformed("kotlin podcasts", 3)
            AnalyticsHelper.trackSearchPerformed("true crime", 5)
            AnalyticsHelper.trackOnboardingAiTurnSubmitted(1, setOf("News"), "I like tech news", 2.5f)
            AnalyticsHelper.trackPlaybackStarted(
                podcastId = "p1",
                podcastName = "Pod",
                podcastGenre = "News",
                episodeId = "e1",
                episodeTitle = "Ep",
                startPositionSeconds = 0f,
                totalDurationSeconds = 100f,
                isRepeating = false,
                isSubscribed = true,
                entryPoint = "home_hero_resume_grid",
            )
            AnalyticsHelper.trackEngagementPromptShown("nps", "home", 3)
            AnalyticsHelper.trackAdaptiveRankingStatus(
                listOf(RankingAggregateTelemetry("DISCOVERY", 1, "adaptive", "50_199", true)),
            )
            AnalyticsHelper.trackLateNightSafeguardDecision("prompt_shown")
            AnalyticsHelper.trackProxyFallbackTriggered("cdn.example", 200)
            AnalyticsHelper.trackAutoChaptersRequested("e1", "p1", "https://example/audio.mp3")
        } finally {
            restore()
        }

        val eventNames = captured.map { it.first }.filter { it != AnalyticsGlossary.PERSON_SET_EVENT }
        assertTrue(eventNames.isNotEmpty())
        eventNames.forEach { name ->
            assertTrue(
                AnalyticsGlossary.isAllowedEvent(name),
                "Unexpected non-glossary event: $name",
            )
        }
        assertTrue(eventNames.contains("home_surface_tapped"))
        assertTrue(eventNames.contains("learn_card_action"))
        assertTrue(eventNames.contains("queue_modified"))
        assertTrue(eventNames.contains("search_performed"))
        assertTrue(eventNames.contains("onboarding_search_performed"))
        assertTrue(eventNames.contains("onboarding_ai_turn_submitted"))
        assertTrue(eventNames.contains("playback_started"))
        assertTrue(eventNames.contains("feedback_submitted"))
        assertTrue(eventNames.contains("adaptive_ranking_status"))
        assertTrue(eventNames.contains("late_night_safeguard_decision"))
        assertTrue(eventNames.contains("proxy_fallback_triggered"))
        assertTrue(eventNames.contains("auto_chapters_lifecycle"))
        assertFalse(eventNames.contains("auto_chapters_requested"))
    }
}

class AnalyticsRawTextAndEntryPointTest {

    @Test
    fun `normalizeEntryPoint maps legacy strings`() {
        assertEquals("home_hero_resume", AnalyticsGlossary.normalizeEntryPoint("home_hero_resume_grid"))
        assertEquals("home_hero_new_episodes", AnalyticsGlossary.normalizeEntryPoint("home_hero_new_episodes_grid"))
        assertEquals("mini_player", AnalyticsGlossary.normalizeEntryPoint("resume_mini_player"))
        assertEquals("episode_detail", AnalyticsGlossary.normalizeEntryPoint("episode_info_screen"))
        assertEquals("notification", AnalyticsGlossary.normalizeEntryPoint("resume_notification"))
        assertEquals("unknown", AnalyticsGlossary.normalizeEntryPoint(null))
        assertEquals("unknown", AnalyticsGlossary.normalizeEntryPoint("generic"))
        assertEquals("learn", AnalyticsGlossary.normalizeEntryPoint("learn"))
        assertEquals("android_auto_discover", AnalyticsGlossary.normalizeEntryPoint("android_auto_drive_picks"))
    }

    @Test
    fun `onboarding AI turn attaches raw user_input_text when typed`() {
        val captured = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsEmit.installRecordingSink(captured)
        try {
            AnalyticsHelper.trackOnboardingAiTurnSubmitted(
                turnNumber = 2,
                selectedOptions = emptySet(),
                customInputText = "show me science podcasts",
                timeSpentSeconds = 4f,
            )
        } finally {
            restore()
        }

        val event = captured.first { it.first == "onboarding_ai_turn_submitted" }
        assertEquals("show me science podcasts", event.second["user_input_text"])
        assertEquals(true, event.second["has_custom_input"])
        assertFalse(event.second.containsKey("custom_input_text"))
    }

    @Test
    fun `onboarding AI turn still sends user_input_text when empty`() {
        val captured = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsEmit.installRecordingSink(captured)
        try {
            AnalyticsHelper.trackOnboardingAiTurnSubmitted(
                turnNumber = 1,
                selectedOptions = setOf("Comedy"),
                customInputText = "",
                timeSpentSeconds = 1f,
            )
        } finally {
            restore()
        }

        val event = captured.first { it.first == "onboarding_ai_turn_submitted" }
        assertEquals("", event.second["user_input_text"])
        assertEquals(false, event.second["has_custom_input"])
    }

    @Test
    fun `search helpers attach raw search_query when non-empty`() {
        val captured = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsEmit.installRecordingSink(captured)
        try {
            AnalyticsHelper.trackExploreSearchPerformed("best tech shows", 12)
            AnalyticsHelper.trackSearchPerformed("history wars", 4)
        } finally {
            restore()
        }

        val explore = captured.first { it.first == "search_performed" && it.second["surface"] == "explore" }
        assertEquals("best tech shows", explore.second["search_query"])
        assertEquals(12, explore.second["results_count"])

        val onboardingUnified = captured.filter {
            it.first == "search_performed" && it.second["surface"] == "onboarding"
        }.first()
        assertEquals("history wars", onboardingUnified.second["search_query"])

        val onboardingLegacy = captured.first { it.first == "onboarding_search_performed" }
        assertEquals("history wars", onboardingLegacy.second["search_query"])
    }

    @Test
    fun `playback_started always includes normalized entry_point`() {
        val captured = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsEmit.installRecordingSink(captured)
        try {
            AnalyticsHelper.trackPlaybackStarted(
                podcastId = "p",
                podcastName = null,
                podcastGenre = null,
                episodeId = "e",
                episodeTitle = null,
                startPositionSeconds = 0f,
                totalDurationSeconds = 60f,
                isRepeating = false,
                isSubscribed = false,
                entryPoint = "resume_mini_player",
            )
            AnalyticsHelper.trackPlaybackStarted(
                podcastId = "p",
                podcastName = null,
                podcastGenre = null,
                episodeId = "e2",
                episodeTitle = null,
                startPositionSeconds = 0f,
                totalDurationSeconds = 60f,
                isRepeating = false,
                isSubscribed = false,
                entryPoint = null,
            )
        } finally {
            restore()
        }

        val events = captured.filter { it.first == "playback_started" }
        assertEquals("mini_player", events[0].second["entry_point"])
        assertEquals("unknown", events[1].second["entry_point"])
    }
}
