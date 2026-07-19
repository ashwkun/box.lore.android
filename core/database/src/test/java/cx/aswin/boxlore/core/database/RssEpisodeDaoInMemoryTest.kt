package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RssEpisodeDaoInMemoryTest {

    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: RssEpisodeDao
    private lateinit var podcastDao: PodcastDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.rssEpisodeDao()
        podcastDao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedPodcast(podcastId: String = "-1001") {
        podcastDao.upsert(
            PodcastEntity(
                podcastId = podcastId,
                title = "RSS Show",
                author = "Author",
                imageUrl = "https://example.com/art.jpg",
                description = "desc",
                sourceType = PodcastEntity.SOURCE_RSS,
            ),
        )
    }

    private fun episode(
        episodeId: String,
        podcastId: String = "-1001",
        title: String = "Title $episodeId",
        description: String = "Description $episodeId",
        publishedDate: Long = 0L,
    ) = RssEpisodeEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        guid = "guid-$episodeId",
        title = title,
        description = description,
        audioUrl = "https://example.com/$episodeId.mp3",
        imageUrl = null,
        duration = 100,
        publishedDate = publishedDate,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    @Test
    fun rssEpisodeRequiresNegativeId() {
        assertThrows(IllegalArgumentException::class.java) {
            episode(episodeId = "12345")
        }
    }

    @Test
    fun upsertAllAndGetEpisode() = runTest {
        seedPodcast()
        dao.upsertAll(listOf(episode("-1"), episode("-2")))

        assertEquals("Title -1", dao.getEpisode("-1")?.title)
        assertNull(dao.getEpisode("-999"))
        assertEquals(2, dao.count("-1001"))
    }

    @Test
    fun newestPageOrdersByPublishedDateDesc() = runTest {
        seedPodcast()
        dao.upsertAll(
            listOf(
                episode("-1", publishedDate = 100L),
                episode("-2", publishedDate = 300L),
                episode("-3", publishedDate = 200L),
            ),
        )

        val page = dao.getNewestPage("-1001", limit = 2, offset = 0).map { it.episodeId }
        assertEquals(listOf("-2", "-3"), page)

        val second = dao.getNewestPage("-1001", limit = 2, offset = 2).map { it.episodeId }
        assertEquals(listOf("-1"), second)
    }

    @Test
    fun oldestPageOrdersByPublishedDateAsc() = runTest {
        seedPodcast()
        dao.upsertAll(
            listOf(
                episode("-1", publishedDate = 100L),
                episode("-2", publishedDate = 300L),
                episode("-3", publishedDate = 200L),
            ),
        )

        assertEquals(listOf("-1", "-3", "-2"), dao.getOldestPage("-1001", limit = 10, offset = 0).map { it.episodeId })
    }

    @Test
    fun getAllNewestReturnsEveryEpisode() = runTest {
        seedPodcast()
        dao.upsertAll(listOf(episode("-1", publishedDate = 1L), episode("-2", publishedDate = 2L)))
        assertEquals(listOf("-2", "-1"), dao.getAllNewest("-1001").map { it.episodeId })
    }

    @Test
    fun searchMatchesTitleOrDescription() = runTest {
        seedPodcast()
        dao.upsertAll(
            listOf(
                episode("-1", title = "Kotlin coroutines", description = "async"),
                episode("-2", title = "Gardening", description = "kotlin tips inside"),
                episode("-3", title = "Cooking", description = "food"),
            ),
        )

        val matches = dao.search("-1001", "kotlin").map { it.episodeId }.toSet()
        assertEquals(setOf("-1", "-2"), matches)
    }

    @Test
    fun getNewestReturnsSingleTopEpisode() = runTest {
        seedPodcast()
        dao.upsertAll(listOf(episode("-1", publishedDate = 1L), episode("-2", publishedDate = 5L)))
        assertEquals("-2", dao.getNewest("-1001")?.episodeId)
    }

    @Test
    fun getEpisodeIdsReturnsAllForPodcast() = runTest {
        seedPodcast()
        dao.upsertAll(listOf(episode("-1"), episode("-2")))
        assertEquals(setOf("-1", "-2"), dao.getEpisodeIds("-1001").toSet())
    }

    @Test
    fun deleteForPodcastClearsRows() = runTest {
        seedPodcast()
        dao.upsertAll(listOf(episode("-1"), episode("-2")))
        dao.deleteForPodcast("-1001")
        assertEquals(0, dao.count("-1001"))
    }

    @Test
    fun deletingParentPodcastCascadesToEpisodes() = runTest {
        seedPodcast()
        dao.upsertAll(listOf(episode("-1")))

        podcastDao.deleteRssEpisodes("-1001")

        assertTrue(dao.getEpisodeIds("-1001").isEmpty())
    }
}
