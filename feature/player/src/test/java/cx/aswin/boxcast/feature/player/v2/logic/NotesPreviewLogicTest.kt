package cx.aswin.boxcast.feature.player.v2.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesPreviewLogicTest {

    @Test
    fun `stripHtml returns empty for null or blank input`() {
        assertEquals("", NotesPreviewLogic.stripHtml(null))
        assertEquals("", NotesPreviewLogic.stripHtml(""))
        assertEquals("", NotesPreviewLogic.stripHtml("   "))
    }

    @Test
    fun `stripHtml removes tags and collapses whitespace`() {
        val html = "<p>Hello <b>world</b></p>\n\n<p>Second line</p>"
        assertEquals("Hello world Second line", NotesPreviewLogic.stripHtml(html))
    }

    @Test
    fun `stripHtml decodes common entities`() {
        assertEquals("Tom & Jerry", NotesPreviewLogic.stripHtml("Tom &amp; Jerry"))
    }

    @Test
    fun `canExpand returns false for blank text`() {
        assertFalse(NotesPreviewLogic.canExpand(""))
        assertFalse(NotesPreviewLogic.canExpand("   "))
    }

    @Test
    fun `canExpand returns true when text exceeds min length`() {
        val longText = "a".repeat(121)
        assertTrue(NotesPreviewLogic.canExpand(longText))
    }

    @Test
    fun `canExpand returns false when text is short with no extra lines`() {
        assertFalse(NotesPreviewLogic.canExpand("Short note"))
    }

    @Test
    fun `canExpand returns true when newline count reaches collapsed max lines`() {
        val multiline = "line1\nline2\nline3\nline4"
        assertTrue(NotesPreviewLogic.canExpand(multiline, collapsedMaxLines = 3))
    }

    @Test
    fun `canExpand respects custom min length threshold`() {
        val text = "a".repeat(50)
        assertFalse(NotesPreviewLogic.canExpand(text, minLengthForExpand = 100))
        assertTrue(NotesPreviewLogic.canExpand(text, minLengthForExpand = 40))
    }
}
