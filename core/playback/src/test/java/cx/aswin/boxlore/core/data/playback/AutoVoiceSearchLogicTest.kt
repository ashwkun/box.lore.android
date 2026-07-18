package cx.aswin.boxlore.core.data.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoVoiceSearchLogicTest {
    @Test
    fun `searchScore ranks exact title highest`() {
        assertEquals(100, AutoVoiceSearchLogic.searchScore("News Daily", null, "news daily"))
        assertEquals(80, AutoVoiceSearchLogic.searchScore("News Daily Extra", null, "news daily"))
        assertEquals(50, AutoVoiceSearchLogic.searchScore("The News Daily Show", null, "news daily"))
        assertEquals(0, AutoVoiceSearchLogic.searchScore("Science", "Lab", "sports"))
    }

    @Test
    fun `normalizeVoiceQuery strips play verbs and app fluff`() {
        assertEquals(
            "serial",
            AutoVoiceSearchLogic.normalizeVoiceQuery("Please play the podcast Serial on the boxlore app"),
        )
        assertEquals(
            "this american life",
            AutoVoiceSearchLogic.normalizeVoiceQuery("play the latest episode of This American Life"),
        )
    }

    @Test
    fun `voiceMatchScore uses token overlap when substring fails`() {
        val score =
            AutoVoiceSearchLogic.voiceMatchScore(
                title = "Hardcore History",
                author = "Dan Carlin",
                query = "history hardcore show",
            )
        assertTrue(score >= 20)
    }
}
