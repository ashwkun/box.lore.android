package cx.aswin.boxlore.core.catalog

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
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
 * Regression coverage for the `recommendations/v2` language fix: [fetchRecommendationV2] used to
 * hardcode `languages = listOf("en")` regardless of listener region. Verifies the outgoing
 * request body now carries [recommendationLanguagesForCountry]'s region mapping, matching the
 * Home candidates pipeline's language handling (`HomeViewModelSlate`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastRepositoryRecommendationsLanguageTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: PodcastRepository
    private val testDispatcher = UnconfinedTestDispatcher()

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
        val api = NetworkModule.createBoxLoreApi(server.url("/").toString(), client)
        repository =
            PodcastRepository(
                baseUrl = server.url("/").toString(),
                publicKey = "test-app-key",
                context = fakeContext(),
                rssRepository = RssPodcastRepository.createForTests(context = fakeContext(), database = fakeDatabase()),
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
    fun `fetchRecommendationV2 maps a non-English chart country to its languages`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"false"}"""))

            repository.fetchRecommendationV2(
                history = listOf(seedHistoryItem()),
                interests = emptyList(),
                country = "fr",
                subscribedPodcastIds = emptyList(),
            )

            val body = server.takeRequest().body.readUtf8()
            assertTrue(
                body.contains(""""languages":["fr","en"]"""),
                "Expected fr,en languages in request body, got: $body",
            )
        }

    @Test
    fun `fetchRecommendationV2 defaults to English for an unmapped country`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"false"}"""))

            repository.fetchRecommendationV2(
                history = listOf(seedHistoryItem()),
                interests = emptyList(),
                country = "us",
                subscribedPodcastIds = emptyList(),
            )

            val body = server.takeRequest().body.readUtf8()
            assertTrue(
                body.contains(""""languages":["en"]"""),
                "Expected en-only languages in request body, got: $body",
            )
        }

    private fun seedHistoryItem() =
        HistoryItem(
            podcastTitle = "Podcast",
            episodeTitle = "Episode",
            episodeId = "1",
            durationMs = 1_000L,
            progressMs = 500L,
        )

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
}
