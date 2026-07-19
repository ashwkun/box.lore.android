package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentSectionsV1RequestSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `contract version is explicit while optional enrichment stays omitted`() {
        val encoded =
            json.encodeToString(
                ContentSectionsV1Request(
                    contractVersion = 1,
                    surface = "home",
                    localMinuteOfDay = 600,
                    country = "us",
                ),
            )

        assertTrue("\"contractVersion\":1" in encoded)
        assertFalse("\"tasteSignals\"" in encoded)
        assertFalse("\"durationPreference\"" in encoded)
        assertFalse("\"localDate\"" in encoded)
    }

    @Test
    fun `legacy request decodes with safe enrichment defaults`() {
        val decoded =
            json.decodeFromString<ContentSectionsV1Request>(
                """
                {
                  "contractVersion": 1,
                  "surface": "home",
                  "localMinuteOfDay": 600,
                  "country": "us"
                }
                """.trimIndent(),
            )

        assertEquals(emptyList<ContentTasteSignalDto>(), decoded.tasteSignals)
        assertEquals(emptyList<String>(), decoded.recentSectionIds)
        assertNull(decoded.durationPreference)
        assertNull(decoded.historyMaturity)
        assertNull(decoded.noveltyPreference)
        assertNull(decoded.localDate)
        assertNull(decoded.timezoneOffsetMinutes)
    }

    @Test
    fun `enrichment fields serialize with coordinated names`() {
        val encoded =
            json.encodeToString(
                ContentSectionsV1Request(
                    contractVersion = 1,
                    surface = "home",
                    localMinuteOfDay = 600,
                    country = "us",
                    tasteSignals = listOf(ContentTasteSignalDto("Technology", -0.25)),
                    recentSectionIds = listOf("tech-afternoon-brief"),
                    durationPreference = ContentDurationPreferenceDto(15, 55),
                    historyMaturity = 3,
                    noveltyPreference = 0.6,
                    localDate = "2026-07-17",
                    timezoneOffsetMinutes = 330,
                ),
            )

        assertTrue("\"tasteSignals\":[{\"genre\":\"Technology\",\"weight\":-0.25}]" in encoded)
        assertTrue("\"recentSectionIds\":[\"tech-afternoon-brief\"]" in encoded)
        assertTrue(
            "\"durationPreference\":{\"minimumMinutes\":15,\"maximumMinutes\":55}" in encoded,
        )
        assertTrue("\"historyMaturity\":3" in encoded)
        assertTrue("\"noveltyPreference\":0.6" in encoded)
        assertTrue("\"localDate\":\"2026-07-17\"" in encoded)
        assertTrue("\"timezoneOffsetMinutes\":330" in encoded)
    }
}
