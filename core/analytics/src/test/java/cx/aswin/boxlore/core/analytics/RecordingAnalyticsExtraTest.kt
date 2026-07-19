package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Additional coverage for [RecordingAnalytics] feedback / lifecycle helpers not exercised by
 * [RecordingAnalyticsTest].
 */
class RecordingAnalyticsExtraTest {
    private lateinit var analytics: RecordingAnalytics

    @BeforeEach
    fun setUp() {
        analytics = RecordingAnalytics()
    }

    @Test
    fun trackSurveyNpsEligibleEmitsFeedbackSubmitted() {
        analytics.trackSurveyNpsEligible(completedEpisodes = 7, triggerContext = "home")

        val event = analytics.lastEvent!!
        assertEquals("feedback_submitted", event.name)
        assertEquals("nps_eligible", event.properties["feedback_type"])
        assertEquals("home", event.properties["source"])
        assertEquals(7, event.properties["completed_episodes"])
    }

    @Test
    fun trackSurveyNpsEligibleOmitsCompletedWhenNull() {
        analytics.trackSurveyNpsEligible(completedEpisodes = null, triggerContext = "settings")

        assertFalse("completed_episodes" in analytics.lastEvent!!.properties)
    }

    @Test
    fun trackSurveyNpsManualTriggerEmitsFeedbackSubmitted() {
        analytics.trackSurveyNpsManualTrigger("about_screen")

        val event = analytics.lastEvent!!
        assertEquals("nps_manual", event.properties["feedback_type"])
        assertEquals("about_screen", event.properties["source"])
    }

    @Test
    fun trackPromoterReviewHandoffIncludesScoreWhenPresent() {
        analytics.trackPromoterReviewHandoff(npsScore = 10)

        val event = analytics.lastEvent!!
        assertEquals("promoter_review_handoff", event.properties["feedback_type"])
        assertEquals(10, event.properties["score"])
    }

    @Test
    fun trackPromoterReviewHandoffOmitsScoreWhenNull() {
        analytics.trackPromoterReviewHandoff(npsScore = null)

        assertFalse("score" in analytics.lastEvent!!.properties)
    }

    @Test
    fun trackFirstEpisodePlayedEmitsEvent() {
        analytics.trackFirstEpisodePlayed()

        assertEquals(1, analytics.eventCount("first_episode_played"))
    }

    @Test
    fun trackAppCheckStatusCapturesTokenAndProvider() {
        analytics.trackAppCheckStatus(tokenObtained = true, provider = "play_integrity")

        val event = analytics.lastEvent!!
        assertEquals("app_check_status", event.name)
        assertEquals(true, event.properties["token_obtained"])
        assertEquals("play_integrity", event.properties["provider"])
    }

    @Test
    fun nonGlossaryEventsRemainDropped() {
        analytics.capture("totally_made_up_event")
        assertTrue(analytics.events.isEmpty())
        assertNull(analytics.lastEvent)
    }
}
