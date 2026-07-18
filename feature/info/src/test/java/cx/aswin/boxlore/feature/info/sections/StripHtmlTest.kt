package cx.aswin.boxlore.feature.info.sections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StripHtmlTest {
    @Test
    fun `stripHtml returns empty string for null`() {
        assertEquals("", stripHtml(null))
    }

    @Test
    fun `stripHtml returns empty string for empty input`() {
        assertEquals("", stripHtml(""))
    }
}
