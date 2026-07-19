package cx.aswin.boxlore.core.catalog

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.TimeUnit

/**
 * Hermetic [PodcastRepository] catalog paths against MockWebServer (B2).
 *
 * Uses Mockito doubles for [Context] / Room (no Robolectric) so OkHttp MockWebServer
 * stays on the plain JVM classpath. Unit-test resolution pins OkHttp 4.12 to match
 * MockWebServer 4.x (rssparser otherwise upgrades production deps to OkHttp 5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastRepositoryCatalogTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: PodcastRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val context = fakeContext()
        val database = fakeDatabase()
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        val api = NetworkModule.createBoxLoreApi(server.url("/").toString(), client)
        val rss =
            RssPodcastRepository.createForTests(
                context = context,
                database = database,
            )

        repository =
            PodcastRepository(
                baseUrl = server.url("/").toString(),
                publicKey = APP_KEY,
                context = context,
                rssRepository = rss,
                ioDispatcher = testDispatcher,
                boxLoreApi = api,
            )
    }

    @AfterEach
    fun tearDown() {
        if (::server.isInitialized) {
            server.shutdown()
        }
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun `getTrendingPodcasts maps feeds from MockWebServer`() =
        runTest(testDispatcher) {
            enqueueFixture("fixtures/trending.json")

            val podcasts = repository.getTrendingPodcasts(country = "us", limit = 10, category = "News")

            assertEquals(1, podcasts.size)
            assertEquals("920666", podcasts.single().id)
            assertEquals("The Daily", podcasts.single().title)
            assertEquals("The New York Times", podcasts.single().artist)

            val recorded = server.takeRequest()
            assertTrue(recorded.path!!.startsWith("/trending"))
            assertEquals("us", recorded.requestUrl?.queryParameter("country"))
            assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
        }

    @Test
    fun `getTrendingPodcasts returns empty list on HTTP error`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"status":"false","error":"boom"}"""),
            )

            val podcasts = repository.getTrendingPodcasts(country = "us")

            assertTrue(podcasts.isEmpty())
        }

    @Test
    fun `getHomeBootstrapDataFast maps trending and briefing`() =
        runTest(testDispatcher) {
            enqueueFixture("fixtures/bootstrap.json")

            val data = repository.getHomeBootstrapDataFast(country = "us")

            assertEquals("Morning Brief", data.briefing?.title)
            assertEquals(1, data.trending.size)
            assertEquals("Trending Show", data.trending.single().title)
            assertEquals(false, data.isRecommendationsFallback)

            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertTrue(recorded.path!!.startsWith("/home/bootstrap"))
            assertEquals("us", recorded.requestUrl?.queryParameter("country"))
        }

    @Test
    fun `getHomeBootstrapDataFast returns empty shell on transport failure`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .setBody("""{"error":"unavailable"}"""),
            )

            val data = repository.getHomeBootstrapDataFast(country = "us")

            assertEquals(null, data.briefing)
            assertTrue(data.trending.isEmpty())
            assertTrue(data.recommendations.isEmpty())
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

    private fun fakeContext(): Context {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenReturn(null)
        `when`(prefs.getAll()).thenReturn(emptyMap())
        `when`(prefs.contains(anyString())).thenReturn(false)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)
        `when`(editor.apply()).then { }

        val appContext = mock(Context::class.java)
        `when`(appContext.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)

        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(appContext)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }

    private fun fakeDatabase(): BoxLoreDatabase {
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return database
    }

    companion object {
        private const val APP_KEY = "test-app-key"
    }
}
