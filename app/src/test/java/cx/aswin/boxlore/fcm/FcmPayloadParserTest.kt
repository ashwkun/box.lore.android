package cx.aswin.boxlore.fcm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FcmPayloadParserTest {

    @Test
    fun testParse_emptyPayloadResolvesDefaults() {
        val payload = emptyMap<String, String>()
        val parsed = FcmPayloadParser.parse(payload)

        assertEquals("boxlore Update", parsed.title)
        assertEquals("Check out what's new in boxlore!", parsed.body)
        assertEquals("both", parsed.type)
        assertNull(parsed.route)
        assertNull(parsed.imageUrl)
        assertEquals("default", parsed.sound)
        assertEquals("View", parsed.actionLabel)
        assertTrue(parsed.showActionInPush)
        assertTrue(parsed.showActionInApp)
        assertEquals("WHAT'S NEW", parsed.category)
    }

    @Test
    fun testParse_readsValuesFromPayload() {
        val payload = mapOf(
            "title" to "Custom Title",
            "body" to "Custom body content",
            "type" to "push",
            "route" to "boxlore://podcast/123",
            "image" to "https://example.com/banner.png",
            "sound" to "chime",
            "action_label" to "Listen Now",
            "show_action_in_push" to "false",
            "show_action_in_app" to "true",
            "category" to "UPDATE"
        )
        val parsed = FcmPayloadParser.parse(payload)

        assertEquals("Custom Title", parsed.title)
        assertEquals("Custom body content", parsed.body)
        assertEquals("push", parsed.type)
        assertEquals("boxlore://podcast/123", parsed.route)
        assertEquals("https://example.com/banner.png", parsed.imageUrl)
        assertEquals("chime", parsed.sound)
        assertEquals("Listen Now", parsed.actionLabel)
        assertFalse(parsed.showActionInPush)
        assertTrue(parsed.showActionInApp)
        assertEquals("UPDATE", parsed.category)
    }

    @Test
    fun testParse_readsOtherToggleStates() {
        val payload = mapOf(
            "show_action_in_push" to "true",
            "show_action_in_app" to "false"
        )
        val parsed = FcmPayloadParser.parse(payload)

        assertTrue(parsed.showActionInPush)
        assertFalse(parsed.showActionInApp)
        assertEquals("WHAT'S NEW", parsed.category)
    }
}
