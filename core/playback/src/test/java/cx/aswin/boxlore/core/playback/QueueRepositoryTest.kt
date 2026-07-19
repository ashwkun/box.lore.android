package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.Transcript
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-memory Room coverage for [QueueRepository]. The [PodcastRepository] collaborator is stored
 * but never touched by the exercised methods, so a throwaway instance (no network) suffices.
 * The Podcast 2.0 persons/transcripts JSON codecs use Android's `org.json`, provided by Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueRepositoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var repository: QueueRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val rss = RssPodcastRepository.createForTests(context = context, database = database)
        val api = NetworkModule.createBoxLoreApi("http://localhost/", context)
        val podcastRepository =
            PodcastRepository(
                baseUrl = "http://localhost/",
                publicKey = "test-key",
                context = context,
                rssRepository = rss,
                ioDispatcher = UnconfinedTestDispatcher(),
                boxLoreApi = api,
            )
        repository = QueueRepository(database, podcastRepository)
    }

    @After
    fun tearDown() {
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    private fun episodeItem(
        id: Long,
        title: String = "Episode $id",
        audioUrl: String = "https://example.com/$id.mp3",
    ) = EpisodeItem(
        id = id,
        title = title,
        enclosureUrl = audioUrl,
        duration = 120,
        datePublished = id,
    )

    private fun podcast(id: String = "pod-1") =
        Podcast(
            id = id,
            title = "Podcast",
            artist = "Artist",
            imageUrl = "https://example.com/art.jpg",
            genre = "Technology",
        )

    @Test
    fun addToQueueAppendsAndExposesViaFlow() =
        runTest {
            repository.addToQueue(episodeItem(1), podcast())
            repository.addToQueue(episodeItem(2), podcast())

            val queued = repository.queue.first()
            assertEquals(listOf("1", "2"), queued.map { it.id.toString() })
        }

    @Test
    fun addToQueueSkipsDuplicateEpisode() =
        runTest {
            repository.addToQueue(episodeItem(1), podcast())
            repository.addToQueue(episodeItem(1), podcast())

            assertEquals(1, database.queueDao().getAllQueueItemsSync().size)
        }

    @Test
    fun addToQueueAssignsIncrementingPositions() =
        runTest {
            repository.addToQueue(episodeItem(1), podcast())
            repository.addToQueue(episodeItem(2), podcast())

            val positions = database.queueDao().getAllQueueItemsSync().associate { it.episodeId to it.position }
            assertEquals(1, positions["1"])
            assertEquals(2, positions["2"])
        }

    @Test
    fun clearQueueRemovesAll() =
        runTest {
            repository.addToQueue(episodeItem(1), podcast())
            repository.clearQueue()

            assertTrue(repository.queue.first().isEmpty())
        }

    @Test
    fun replaceQueueDeduplicatesAndOrders() =
        runTest {
            repository.replaceQueue(
                listOf(
                    domainEpisode("1"),
                    domainEpisode("2"),
                    domainEpisode("1"),
                ),
            )

            val snapshot = repository.getQueueSnapshot()
            assertEquals(listOf("1", "2"), snapshot.map { it.id })
        }

    @Test
    fun replaceQueueRoundTripsPodcast20Metadata() =
        runTest {
            val episode =
                domainEpisode("1").copy(
                    persons = listOf(Person(name = "Host", role = "host", img = "https://img", href = "https://href")),
                    transcripts = listOf(Transcript(url = "https://t.vtt", type = "text/vtt")),
                )

            repository.replaceQueue(listOf(episode))

            val restored = repository.getQueueSnapshot().single()
            assertEquals("Host", restored.persons?.single()?.name)
            assertEquals("host", restored.persons?.single()?.role)
            assertEquals("https://t.vtt", restored.transcripts?.single()?.url)
            assertEquals("text/vtt", restored.transcripts?.single()?.type)
        }

    @Test
    fun getQueueSnapshotRepairsDuplicateRows() =
        runTest {
            // Insert duplicate episodeIds directly (bypassing addToQueue's dedup guard).
            database.queueDao().insertQueueItem(rawItem("dup", position = 0))
            database.queueDao().insertQueueItem(rawItem("dup", position = 1))
            database.queueDao().insertQueueItem(rawItem("unique", position = 2))

            val snapshot = repository.getQueueSnapshot()

            assertEquals(setOf("dup", "unique"), snapshot.map { it.id }.toSet())
            assertEquals(2, database.queueDao().getAllQueueItemsSync().size)
        }

    @Test
    fun reorderQueueRewritesPositions() =
        runTest {
            repository.addToQueue(episodeItem(1), podcast())
            repository.addToQueue(episodeItem(2), podcast())
            repository.addToQueue(episodeItem(3), podcast())

            repository.reorderQueue(listOf("3", "1", "2"))

            assertEquals(listOf("3", "1", "2"), repository.queue.first().map { it.id.toString() })
        }

    @Test
    fun reorderQueueAppendsUnlistedRowsAtTail() =
        runTest {
            repository.addToQueue(episodeItem(1), podcast())
            repository.addToQueue(episodeItem(2), podcast())
            repository.addToQueue(episodeItem(3), podcast())

            repository.reorderQueue(listOf("2"))

            val ordered = repository.queue.first().map { it.id.toString() }
            assertEquals("2", ordered.first())
            assertEquals(setOf("1", "3"), ordered.drop(1).toSet())
        }

    @Test
    fun getQueueItemByEpisodeIdReturnsRowOrNull() =
        runTest {
            repository.addToQueue(episodeItem(1), podcast())

            assertEquals("Episode 1", repository.getQueueItemByEpisodeId("1")?.title)
            assertNull(repository.getQueueItemByEpisodeId("missing"))
        }

    private fun domainEpisode(id: String) =
        Episode(
            id = id,
            title = "Episode $id",
            description = "desc $id",
            audioUrl = "https://example.com/$id.mp3",
            podcastId = "pod-1",
            podcastTitle = "Podcast",
            duration = 120,
            publishedDate = id.hashCode().toLong(),
        )

    private fun rawItem(
        episodeId: String,
        position: Int,
    ) = QueueItem(
        episodeId = episodeId,
        title = "Episode $episodeId",
        podcastId = "pod-1",
        podcastTitle = "Podcast",
        imageUrl = null,
        audioUrl = "https://example.com/$episodeId.mp3",
        duration = 120,
        pubDate = 0L,
        description = null,
        position = position,
    )
}
