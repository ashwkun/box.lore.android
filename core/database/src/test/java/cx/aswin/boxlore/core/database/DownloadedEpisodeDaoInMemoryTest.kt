package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class DownloadedEpisodeDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: DownloadedEpisodeDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.downloadedEpisodeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun download(
        episodeId: String,
        podcastId: String = "pod-1",
        downloadedAt: Long = 0L,
        sizeBytes: Long = 1_000L,
        status: Int = DownloadedEpisodeEntity.STATUS_COMPLETED,
    ) = DownloadedEpisodeEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode $episodeId",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Podcast",
        podcastImageUrl = null,
        durationMs = 1_000L,
        publishedDate = 0L,
        localFilePath = "/tmp/$episodeId",
        downloadId = 1L,
        downloadedAt = downloadedAt,
        sizeBytes = sizeBytes,
        status = status,
    )

    @Test
    fun insertAndGetDownload() =
        runTest {
            dao.insert(download("ep-1"))
            assertEquals("Episode ep-1", dao.getDownload("ep-1")?.episodeTitle)
            assertNull(dao.getDownload("missing"))
        }

    @Test
    fun getAllDownloadsOrderedByDownloadedAtDesc() =
        runTest {
            dao.insert(download("a", downloadedAt = 1L))
            dao.insert(download("b", downloadedAt = 3L))
            dao.insert(download("c", downloadedAt = 2L))

            assertEquals(listOf("b", "c", "a"), dao.getAllDownloads().first().map { it.episodeId })
            assertEquals(3, dao.getAllDownloadsSync().size)
        }

    @Test
    fun getCompletedDownloadsFiltersByStatus() =
        runTest {
            dao.insert(download("done", status = DownloadedEpisodeEntity.STATUS_COMPLETED))
            dao.insert(download("queued", status = DownloadedEpisodeEntity.STATUS_QUEUED))
            dao.insert(download("failed", status = DownloadedEpisodeEntity.STATUS_FAILED))

            assertEquals(listOf("done"), dao.getCompletedDownloads().map { it.episodeId })
        }

    @Test
    fun getDownloadsForPodcast() =
        runTest {
            dao.insert(download("a", podcastId = "p1"))
            dao.insert(download("b", podcastId = "p2"))
            assertEquals(listOf("a"), dao.getDownloadsForPodcast("p1").map { it.episodeId })
        }

    @Test
    fun isDownloadedFlowCountsCompletedOnly() =
        runTest {
            dao.insert(download("ep-1", status = DownloadedEpisodeEntity.STATUS_COMPLETED))
            assertEquals(1, dao.isDownloadedFlow("ep-1").first())
            assertEquals(0, dao.isDownloadingFlow("ep-1").first())
        }

    @Test
    fun isDownloadingFlowCountsQueuedAndDownloading() =
        runTest {
            dao.insert(download("ep-1", status = DownloadedEpisodeEntity.STATUS_DOWNLOADING))
            assertEquals(1, dao.isDownloadingFlow("ep-1").first())
            assertEquals(0, dao.isDownloadedFlow("ep-1").first())
        }

    @Test
    fun totalSizeBytesSumsAllRows() =
        runTest {
            assertEquals(0L, dao.getTotalSizeBytes().first())
            dao.insert(download("a", sizeBytes = 100L))
            dao.insert(download("b", sizeBytes = 250L))
            assertEquals(350L, dao.getTotalSizeBytes().first())
        }

    @Test
    fun countOthersByPodcastIdExcludesGivenEpisode() =
        runTest {
            dao.insert(download("a", podcastId = "p1"))
            dao.insert(download("b", podcastId = "p1"))
            dao.insert(download("c", podcastId = "p2"))

            assertEquals(1, dao.countOthersByPodcastId("p1", excludeEpisodeId = "a"))
            assertEquals(0, dao.countOthersByPodcastId("p2", excludeEpisodeId = "c"))
        }

    @Test
    fun deleteRemovesRow() =
        runTest {
            dao.insert(download("ep-1"))
            dao.delete("ep-1")
            assertNull(dao.getDownload("ep-1"))
            assertTrue(dao.getAllDownloadsSync().isEmpty())
        }

    @Test
    fun insertReplacesOnConflict() =
        runTest {
            dao.insert(download("ep-1", sizeBytes = 100L))
            dao.insert(download("ep-1", sizeBytes = 200L))
            assertEquals(200L, dao.getDownload("ep-1")?.sizeBytes)
            assertEquals(1, dao.getAllDownloadsSync().size)
        }
}
