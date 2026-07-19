package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: QueueDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.queueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun item(
        episodeId: String,
        position: Int,
        podcastId: String = "pod-1",
    ) = QueueItem(
        episodeId = episodeId,
        title = "Episode $episodeId",
        podcastId = podcastId,
        podcastTitle = "Podcast",
        imageUrl = null,
        audioUrl = "https://example.com/$episodeId.mp3",
        duration = 100,
        pubDate = 0L,
        description = null,
        position = position,
    )

    @Test
    fun insertAndGetAllOrderedByPosition() =
        runTest {
            dao.insertQueueItem(item("b", position = 2))
            dao.insertQueueItem(item("a", position = 1))
            dao.insertQueueItem(item("c", position = 3))

            assertEquals(listOf("a", "b", "c"), dao.getAllQueueItemsSync().map { it.episodeId })
            assertEquals(listOf("a", "b", "c"), dao.getAllQueueItems().first().map { it.episodeId })
        }

    @Test
    fun insertQueueItemsBulk() =
        runTest {
            dao.insertQueueItems(listOf(item("a", 1), item("b", 2)))
            assertEquals(2, dao.getAllQueueItemsSync().size)
        }

    @Test
    fun getMaxPositionReflectsHighest() =
        runTest {
            assertNull(dao.getMaxPosition())
            dao.insertQueueItems(listOf(item("a", 1), item("b", 5)))
            assertEquals(5, dao.getMaxPosition())
        }

    @Test
    fun countEpisodeAndLookupByEpisodeId() =
        runTest {
            dao.insertQueueItem(item("a", 1))
            assertEquals(1, dao.countEpisode("a"))
            assertEquals(0, dao.countEpisode("missing"))
            assertEquals("Episode a", dao.getQueueItemByEpisodeId("a")?.title)
            assertNull(dao.getQueueItemByEpisodeId("missing"))
        }

    @Test
    fun deleteQueueItemByGeneratedId() =
        runTest {
            dao.insertQueueItem(item("a", 1))
            val stored = dao.getQueueItemByEpisodeId("a")!!
            dao.deleteQueueItem(stored.id)
            assertNull(dao.getQueueItemByEpisodeId("a"))
        }

    @Test
    fun clearQueueRemovesEverything() =
        runTest {
            dao.insertQueueItems(listOf(item("a", 1), item("b", 2)))
            dao.clearQueue()
            assertTrue(dao.getAllQueueItemsSync().isEmpty())
        }

    @Test
    fun updateQueuePositionsPersistsNewOrder() =
        runTest {
            dao.insertQueueItems(listOf(item("a", 1), item("b", 2)))
            val stored = dao.getAllQueueItemsSync()
            val reordered =
                stored.map {
                    if (it.episodeId == "a") it.copy(position = 5) else it.copy(position = 0)
                }

            dao.updateQueuePositions(reordered)

            assertEquals(listOf("b", "a"), dao.getAllQueueItemsSync().map { it.episodeId })
        }

    @Test
    fun updateQueueItemMutatesRow() =
        runTest {
            dao.insertQueueItem(item("a", 1))
            val stored = dao.getQueueItemByEpisodeId("a")!!
            dao.updateQueueItem(stored.copy(title = "Renamed"))
            assertEquals("Renamed", dao.getQueueItemByEpisodeId("a")?.title)
        }
}
