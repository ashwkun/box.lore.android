package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.BootstrapRequest
import cx.aswin.boxlore.core.network.model.ContentSectionsV1Request
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Request
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/**
 * MockWebServer contract tests for critical [BoxLoreApi] endpoints and DTO decoding.
 */
class BoxLoreApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BoxLoreApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        api = NetworkModule.createBoxLoreApi(server.url("/").toString(), client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getTrending decodes feeds and query params`() {
        enqueueFixture("fixtures/trending.json")

        val response =
            api
                .getTrending(
                    publicKey = APP_KEY,
                    country = "us",
                    limit = 10,
                    category = "News",
                    offset = 0,
                ).execute()

        assertTrue(response.isSuccessful)
        val body = requireNotNull(response.body())
        assertEquals("true", body.status)
        assertEquals(1, body.feeds.size)
        val feed = body.feeds.single()
        assertEquals(920666L, feed.id)
        assertEquals("The Daily", feed.title)
        assertEquals("The New York Times", feed.author)
        assertEquals(42, feed.trendScore)
        assertEquals("News", feed.categories?.get("55"))

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.startsWith("/trending"))
        assertEquals("us", recorded.requestUrl?.queryParameter("country"))
        assertEquals("10", recorded.requestUrl?.queryParameter("limit"))
        assertEquals("News", recorded.requestUrl?.queryParameter("cat"))
        assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
    }

    @Test
    fun `search decodes feeds for query`() {
        enqueueFixture("fixtures/search.json")

        val response = api.search(publicKey = APP_KEY, query = "reply all").execute()

        assertTrue(response.isSuccessful)
        val body = requireNotNull(response.body())
        assertEquals("true", body.status)
        val feed = body.feeds.single()
        assertEquals(75075L, feed.id)
        assertEquals("Reply All", feed.title)
        assertEquals("https://feeds.example.com/replyall.xml", feed.url)
        assertEquals(994802953L, feed.itunesId)

        val recorded = server.takeRequest()
        assertEquals("/search?q=reply%20all", recorded.path)
        assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
    }

    @Test
    fun `getPodcast decodes feed detail`() {
        enqueueFixture("fixtures/podcast.json")

        val response = api.getPodcast(publicKey = APP_KEY, feedId = "920666").execute()

        assertTrue(response.isSuccessful)
        val feed = requireNotNull(response.body()?.feed)
        assertEquals(920666L, feed.id)
        assertEquals("The Daily", feed.title)
        assertEquals("episodic", feed.type)
        assertEquals("abcdef12-3456-7890-abcd-ef1234567890", feed.podcastGuid)
        assertEquals("The New York Times", feed.ownerName)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("920666", recorded.requestUrl?.queryParameter("id"))
    }

    @Test
    fun `getEpisode decodes nested episode fields`() {
        enqueueFixture("fixtures/episode.json")

        val response = api.getEpisode(publicKey = APP_KEY, id = "7777").execute()

        assertTrue(response.isSuccessful)
        val episode = requireNotNull(response.body()?.episode)
        assertEquals(7777L, episode.id)
        assertEquals("Episode One", episode.title)
        assertEquals("https://example.com/one.mp3", episode.enclosureUrl)
        assertEquals(2400, episode.duration)
        assertEquals(1, episode.season)
        assertEquals(1, episode.episodeNumber)
        assertEquals("full", episode.episodeType)
        assertEquals(920666L, episode.feedId)
    }

    @Test
    fun `getHomeBootstrap decodes briefing trending vibes and recommendations`() {
        enqueueFixture("fixtures/bootstrap.json")

        val response =
            api
                .getHomeBootstrap(
                    publicKey = APP_KEY,
                    deviceUuid = DEVICE_UUID,
                    request =
                        BootstrapRequest(
                            country = "us",
                            vibeIds = listOf("focus"),
                            deviceUuid = DEVICE_UUID,
                            contractVersion = 1,
                            intentIds = listOf("learn"),
                        ),
                ).execute()

        assertTrue(response.isSuccessful)
        val body = requireNotNull(response.body())
        assertEquals(1, body.contractVersion)
        assertEquals(3, body.catalogVersion)
        assertEquals("recs-v2", body.recommendationAlgorithmVersion)
        assertFalse(body.isRecommendationsFallback == true)

        val briefing = requireNotNull(body.briefing)
        assertEquals("Morning Brief", briefing.title)
        assertEquals("us", briefing.region)
        assertEquals("https://example.com/brief.mp3", briefing.audioUrl)
        assertEquals(1, briefing.sources.size)

        assertEquals(1, body.trending.size)
        assertEquals("Trending Show", body.trending.single().title)
        assertEquals(1, body.curatedVibes["focus"]?.size)
        assertEquals("Focus Feed", body.curatedVibes["focus"]?.single()?.title)
        assertEquals(1, body.recommendations.size)
        assertEquals(3003L, body.recommendations.single().id)
        assertEquals("Learn Show", body.intentCandidates["learn"]?.single()?.title)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/home/bootstrap", recorded.path)
        assertEquals(DEVICE_UUID, recorded.getHeader("X-Device-UUID"))
        assertNotNull(recorded.body)
        assertTrue(recorded.body.readUtf8().contains("\"country\":\"us\""))
    }

    @Test
    fun `getContentSectionsV1 decodes sections and items`() =
        runTest {
            enqueueFixture("fixtures/content_sections_v1.json")

            val body =
                api.getContentSectionsV1(
                    publicKey = APP_KEY,
                    deviceUuid = DEVICE_UUID,
                    request =
                        ContentSectionsV1Request(
                            contractVersion = 1,
                            surface = "home",
                            localMinuteOfDay = 540,
                            country = "us",
                        ),
                )

            assertEquals("true", body.status)
            assertEquals(1, body.contractVersion)
            assertEquals("morning", body.resolvedDaypart)
            assertFalse(body.isFallback)
            assertEquals(1, body.sections.size)

            val section = body.sections.single()
            assertEquals("morning-brief", section.intent.id)
            assertEquals("Morning brief", section.intent.titleFallback)
            assertEquals("rail", section.layout)
            val item = section.items.single()
            assertEquals(5555L, item.id)
            assertEquals("Section Episode", item.title)
            assertEquals(0.91, item.retrievalScore, 0.0001)
            assertEquals(1, item.serverRank)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/content/sections/v1", recorded.path)
            assertEquals(DEVICE_UUID, recorded.getHeader("X-Device-UUID"))
        }

    @Test
    fun `getHomeCandidatesV1 decodes module pools`() =
        runTest {
            enqueueFixture("fixtures/home_candidates_v1.json")

            val body =
                api.getHomeCandidatesV1(
                    publicKey = APP_KEY,
                    deviceUuid = DEVICE_UUID,
                    request =
                        HomeCandidatesV1Request(
                            contractVersion = 1,
                            country = "us",
                            languages = listOf("en"),
                        ),
                )

            assertEquals("true", body.status)
            assertEquals("home-candidates-v1.0", body.algorithmVersion)
            assertEquals(1, body.modules.taste.size)
            assertEquals(
                "1001",
                body.modules.taste
                    .single()
                    .episodeId,
            )
            assertEquals(1, body.modules.becauseYouLike.size)
            assertEquals(
                "1002",
                body.modules.becauseYouLike
                    .single()
                    .episodeId,
            )
            assertEquals(1, body.modules.mission.size)
            assertEquals(1, body.modules.regional.size)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/home/candidates/v1", recorded.path)
            assertEquals(DEVICE_UUID, recorded.getHeader("X-Device-UUID"))
            assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
        }

    @Test
    fun `Call endpoint surfaces non-success for 404`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":"not found"}"""),
        )

        val response = api.getPodcast(publicKey = APP_KEY, feedId = "missing").execute()

        assertFalse(response.isSuccessful)
        assertEquals(404, response.code())
    }

    @Test
    fun `suspend endpoint throws HttpException on 500`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"status":"false","error":"boom"}"""),
            )

            val error =
                assertThrows<HttpException> {
                    api.getContentSectionsV1(
                        publicKey = APP_KEY,
                        deviceUuid = DEVICE_UUID,
                        request =
                            ContentSectionsV1Request(
                                contractVersion = 1,
                                surface = "home",
                                localMinuteOfDay = 600,
                                country = "us",
                            ),
                    )
                }
            assertEquals(500, error.code())
        }

    private fun enqueueFixture(resourcePath: String) {
        val json =
            requireNotNull(javaClass.classLoader?.getResource(resourcePath)) {
                "Missing test fixture: $resourcePath"
            }.readText()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json),
        )
    }

    companion object {
        private const val APP_KEY = "test-app-key"
        private const val DEVICE_UUID = "device-uuid-test"
    }
}
