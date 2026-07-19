package cx.aswin.boxlore.core.rss

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure helper coverage for [RssFeedClient] duration/date parsers (Phase 2 PR3).
 *
 * Full fetch/parse against MockWebServer needs HTTPS + Android Xml (Robolectric);
 * those stay follow-ups. Helpers are the deterministic JVM slice.
 */
class RssFeedClientHelpersTest {

    @Test
    fun `parseDuration accepts seconds integer`() {
        assertEquals(90, parseDuration("90"))
        assertEquals(0, parseDuration("0"))
        assertEquals(0, parseDuration("not-a-duration"))
    }

    @Test
    fun `parseDuration accepts hms clock format`() {
        assertEquals(90, parseDuration("00:01:30"))
        assertEquals(65, parseDuration("1:05"))
        assertEquals(3661, parseDuration("1:01:01"))
    }

    @Test
    fun `parseDate accepts RFC1123 and epoch seconds`() {
        val rfc = parseDate("Wed, 01 Jan 2020 00:00:00 GMT")
        assertTrue(rfc != null && rfc > 0L)
        assertEquals(1_577_836_800L, parseDate("1577836800"))
        assertEquals(1_577_836_800L, parseDate("1577836800000"))
        assertNull(parseDate(""))
        assertNull(parseDate("not-a-date"))
    }
}
