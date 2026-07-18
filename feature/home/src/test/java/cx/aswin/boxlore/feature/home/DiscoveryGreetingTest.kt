package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.data.content.ContentDaypart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Narrow Home greeting path extracted from [HomeViewModel] (pure helper; no Application).
 * Full Home VM bootstrap/category still needs Robolectric + heavy repository fakes (deferred).
 */
class DiscoveryGreetingTest {
    private val weekday = LocalDate.of(2026, 7, 15) // Wednesday
    private val friday = LocalDate.of(2026, 7, 17)
    private val saturday = LocalDate.of(2026, 7, 18)

    @Test
    fun `morning weekday uses start-your-day subtitle`() {
        val greeting = discoveryGreetingFor(ContentDaypart.MORNING, weekday)
        assertEquals("Good Morning", greeting.title)
        assertEquals("Start your day with these updates.", greeting.subtitle)
        assertEquals(ContentDaypart.MORNING, greeting.daypart)
    }

    @Test
    fun `morning weekend uses catch-up subtitle`() {
        val greeting = discoveryGreetingFor(ContentDaypart.MORNING, saturday)
        assertEquals("Catch up on the week.", greeting.subtitle)
    }

    @Test
    fun `evening friday kicks off the weekend`() {
        val greeting = discoveryGreetingFor(ContentDaypart.EVENING, friday)
        assertEquals("Evening Unwind", greeting.title)
        assertEquals("Kick off the weekend.", greeting.subtitle)
    }

    @Test
    fun `evening non-friday uses relax subtitle`() {
        val greeting = discoveryGreetingFor(ContentDaypart.EVENING, weekday)
        assertEquals("Relax, laugh, and catch up.", greeting.subtitle)
    }

    @Test
    fun `afternoon and late night copy is stable`() {
        assertEquals(
            "Smart conversations to keep you going.",
            discoveryGreetingFor(ContentDaypart.AFTERNOON, weekday).subtitle,
        )
        assertEquals(
            "Stories for the dark hours.",
            discoveryGreetingFor(ContentDaypart.LATE_NIGHT, weekday).subtitle,
        )
    }
}
