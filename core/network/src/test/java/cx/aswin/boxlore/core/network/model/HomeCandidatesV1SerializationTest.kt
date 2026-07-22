package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeCandidatesV1SerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `scoped because you like request omits unused modules`() {
        val encoded =
            json.encodeToString(
                HomeCandidatesV1Request(
                    contractVersion = 1,
                    requestedModules = listOf("because_you_like"),
                    anchorPodcastId = "555427",
                    country = "us",
                    languages = listOf("en"),
                    becauseYouLikeBudget = 18,
                    revision = "anchor:555427",
                ),
            )

        assertTrue("\"requestedModules\":[\"because_you_like\"]" in encoded)
        assertTrue("\"anchorPodcastId\":\"555427\"" in encoded)
        assertFalse("\"seeds\"" in encoded)
    }

    @Test
    fun `response decodes module pools and metadata`() {
        val decoded =
            json.decodeFromString<HomeCandidatesV1Response>(
                """
                {
                  "status": "true",
                  "contractVersion": 1,
                  "algorithmVersion": "home-candidates-v1.0",
                  "inventoryVersion": "sync-2026-07-21",
                  "catalogVersion": 3,
                  "requestId": "abc123",
                  "resolvedModules": ["regional"],
                  "modules": {
                    "regional": [
                      {
                        "episodeId": "1",
                        "podcastId": "2",
                        "title": "Hello",
                        "priorScore": 0.5,
                        "retrievalScore": 0.8,
                        "source": "region_popular",
                        "reason": "top_in_us",
                        "isNovel": true
                      }
                    ]
                  },
                  "calibratedPriors": {
                    "noveltyPreference": 0.4,
                    "seedCount": 0,
                    "seedVectorCount": 0
                  }
                }
                """.trimIndent(),
            )

        assertEquals("true", decoded.status)
        assertEquals("home-candidates-v1.0", decoded.algorithmVersion)
        assertEquals(listOf("regional"), decoded.resolvedModules)
        assertEquals(1, decoded.modules.regional.size)
        assertEquals(
            "1",
            decoded.modules.regional
                .single()
                .episodeId,
        )
        val novelty = requireNotNull(decoded.calibratedPriors?.noveltyPreference)
        assertEquals(0.4, novelty, 1e-9)
    }
}
