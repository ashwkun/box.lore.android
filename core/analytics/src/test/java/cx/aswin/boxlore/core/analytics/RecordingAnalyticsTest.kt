package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.data.analytics.RecordingAnalytics
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry
import org.junit.jupiter.api.Assertions.assertEquals
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
        analytics.capture("test_event", mapOf("key" to "value"))

        assertEquals(1, analytics.eventCount("test_event"))
        assertEquals("value", analytics.events.first().properties["key"])
    }

    @Test
    fun `capture with no properties records empty map`() {
        analytics.capture("no_props_event")

        assertEquals(1, analytics.eventCount("no_props_event"))
        assertTrue(analytics.events.first().properties.isEmpty())
    }

    @Test
    fun `multiple captures are all recorded`() {
        repeat(3) { analytics.capture("repeated_event") }

        assertEquals(3, analytics.eventCount("repeated_event"))
    }

    @Test
    fun `eventsNamed filters correctly`() {
        analytics.capture("event_a")
        analytics.capture("event_b")
        analytics.capture("event_a", mapOf("x" to 1))

        assertEquals(2, analytics.eventsNamed("event_a").size)
        assertEquals(1, analytics.eventsNamed("event_b").size)
    }

    @Test
    fun `lastEvent returns most recently captured event`() {
        analytics.capture("first")
        analytics.capture("second")

        assertEquals("second", analytics.lastEvent?.name)
    }

    @Test
    fun `clear removes all events`() {
        analytics.capture("event_1")
        analytics.capture("event_2")

        analytics.clear()

        assertTrue(analytics.events.isEmpty())
        assertNull(analytics.lastEvent)
    }

    @Test
    fun `trackAdaptiveRankingStatus captures event with objective count`() {
        val telemetry = listOf(
            RankingAggregateTelemetry("DISCOVERY", 1, "adaptive", "50_199", true),
            RankingAggregateTelemetry("COMPLETION", 1, "learning", "10_49", false),
        )

        analytics.trackAdaptiveRankingStatus(telemetry)

        assertEquals(1, analytics.eventCount("adaptive_ranking_status"))
        assertEquals(2, analytics.events.first().properties["objective_count"])
    }

    @Test
    fun `trackEngagementPromptShown includes all properties`() {
        analytics.trackEngagementPromptShown("nps", "home_screen", 5)

        val event = analytics.lastEvent!!
        assertEquals("engagement_prompt_shown", event.name)
        assertEquals("nps", event.properties["prompt_type"])
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
