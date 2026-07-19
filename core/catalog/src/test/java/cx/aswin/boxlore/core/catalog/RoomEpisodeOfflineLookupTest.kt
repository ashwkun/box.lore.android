package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomEpisodeOfflineLookupTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var lookup: RoomEpisodeOfflineLookup

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        lookup = RoomEpisodeOfflineLookup(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fromDownloadMapsLocalFilePathAsAudioUrl() =
        runTest {
            database.downloadedEpisodeDao().insert(
                DownloadedEpisodeEntity(
                    episodeId = "ep-1",
                    podcastId = "pod-1",
                    episodeTitle = "Downloaded Episode",
                    episodeDescription = "desc",
                    episodeImageUrl = "https://example.com/ep.jpg",
                    podcastName = "Podcast",
                    podcastImageUrl = null,
                    durationMs = 1_000L,
                    publishedDate = 0L,
                    localFilePath = "/tmp/ep-1.mp3",
                    downloadId = 1L,
                    downloadedAt = 0L,
                    sizeBytes = 100L,
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                ),
            )

            val snapshot = lookup.fromDownload("ep-1")!!
            assertEquals("pod-1", snapshot.podcastId)
            assertEquals("Downloaded Episode", snapshot.episodeTitle)
            assertEquals("/tmp/ep-1.mp3", snapshot.audioUrl)
            assertEquals(1_000L, snapshot.durationMs)
        }

    @Test
    fun fromDownloadReturnsNullWhenMissing() =
        runTest {
            assertNull(lookup.fromDownload("missing"))
        }

    @Test
    fun fromHistoryMapsHistoryRow() =
        runTest {
            database.listeningHistoryDao().upsert(
                ListeningHistoryEntity(
                    episodeId = "ep-2",
                    podcastId = "pod-2",
                    episodeTitle = "History Episode",
                    episodeImageUrl = null,
                    podcastImageUrl = null,
                    episodeAudioUrl = "https://example.com/ep2.mp3",
                    podcastName = "Podcast",
                    progressMs = 500L,
                    durationMs = 2_000L,
                    isCompleted = false,
                    lastPlayedAt = 0L,
                ),
            )

            val snapshot = lookup.fromHistory("ep-2")!!
            assertEquals("pod-2", snapshot.podcastId)
            assertEquals("History Episode", snapshot.episodeTitle)
            assertEquals("https://example.com/ep2.mp3", snapshot.audioUrl)
        }

    @Test
    fun fromHistoryUsesEmptyAudioUrlWhenNull() =
        runTest {
            database.listeningHistoryDao().upsert(
                ListeningHistoryEntity(
                    episodeId = "ep-3",
                    podcastId = "pod-3",
                    episodeTitle = "No Audio",
                    episodeImageUrl = null,
                    podcastImageUrl = null,
                    episodeAudioUrl = null,
                    podcastName = "Podcast",
                    progressMs = 0L,
                    durationMs = 0L,
                    isCompleted = false,
                    lastPlayedAt = 0L,
                ),
            )

            assertEquals("", lookup.fromHistory("ep-3")!!.audioUrl)
        }

    @Test
    fun fromHistoryReturnsNullWhenMissing() =
        runTest {
            assertNull(lookup.fromHistory("missing"))
        }
}
