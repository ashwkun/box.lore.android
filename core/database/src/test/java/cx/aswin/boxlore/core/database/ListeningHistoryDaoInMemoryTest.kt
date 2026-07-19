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
class ListeningHistoryDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: ListeningHistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.listeningHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun history(
        episodeId: String,
        podcastId: String = "pod-1",
        progressMs: Long = 0L,
        durationMs: Long = 3_600_000L,
        isCompleted: Boolean = false,
        isLiked: Boolean = false,
        lastPlayedAt: Long = 0L,
        isDirty: Boolean = true,
    ) = ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode $episodeId",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$episodeId.mp3",
        podcastName = "Podcast",
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        isLiked = isLiked,
        lastPlayedAt = lastPlayedAt,
        isDirty = isDirty,
    )

    @Test
    fun upsertAndGetHistoryItem() =
        runTest {
            dao.upsert(history("ep-1"))
            assertEquals("Episode ep-1", dao.getHistoryItem("ep-1")?.episodeTitle)
            assertNull(dao.getHistoryItem("missing"))
        }

    @Test
    fun upsertReplacesExistingRow() =
        runTest {
            dao.upsert(history("ep-1", progressMs = 100))
            dao.upsert(history("ep-1", progressMs = 500))
            assertEquals(500L, dao.getHistoryItem("ep-1")?.progressMs)
        }

    @Test
    fun getResumeItemsReturnsIncompleteWithProgressNewestFirst() =
        runTest {
            dao.upsertAll(
                listOf(
                    history("ep-old", progressMs = 10, lastPlayedAt = 1L),
                    history("ep-new", progressMs = 10, lastPlayedAt = 5L),
                    history("ep-done", progressMs = 10, isCompleted = true, lastPlayedAt = 9L),
                    history("ep-zero", progressMs = 0, lastPlayedAt = 8L),
                ),
            )

            val resume = dao.getResumeItems().first().map { it.episodeId }

            assertEquals(listOf("ep-new", "ep-old"), resume)
        }

    @Test
    fun getResumeItemsListCapsAtTwenty() =
        runTest {
            dao.upsertAll((1..25).map { history("ep-$it", progressMs = 10, lastPlayedAt = it.toLong()) })
            assertEquals(20, dao.getResumeItemsList().size)
        }

    @Test
    fun getAllHistoryOrdersByLastPlayed() =
        runTest {
            dao.upsertAll(
                listOf(
                    history("a", lastPlayedAt = 1L),
                    history("b", lastPlayedAt = 3L),
                    history("c", lastPlayedAt = 2L),
                ),
            )
            assertEquals(listOf("b", "c", "a"), dao.getAllHistory().first().map { it.episodeId })
        }

    @Test
    fun dirtyItemsAndMarkAsSyncedClearFlag() =
        runTest {
            dao.upsertAll(
                listOf(
                    history("dirty-1", isDirty = true),
                    history("clean-1", isDirty = false),
                ),
            )
            assertEquals(listOf("dirty-1"), dao.getDirtyItems().map { it.episodeId })

            dao.markAsSynced(listOf("dirty-1"), timestamp = 999L)

            assertTrue(dao.getDirtyItems().isEmpty())
            assertEquals(999L, dao.getHistoryItem("dirty-1")?.syncedAt)
        }

    @Test
    fun deleteAndDeleteAll() =
        runTest {
            dao.upsertAll(listOf(history("a"), history("b")))
            dao.delete("a")
            assertNull(dao.getHistoryItem("a"))
            dao.deleteAll()
            assertTrue(dao.getAllHistory().first().isEmpty())
        }

    @Test
    fun getHistoryForPodcastFiltersByPodcastId() =
        runTest {
            dao.upsertAll(
                listOf(
                    history("a", podcastId = "p1"),
                    history("b", podcastId = "p2"),
                    history("c", podcastId = "p1"),
                ),
            )
            assertEquals(setOf("a", "c"), dao.getHistoryForPodcast("p1").map { it.episodeId }.toSet())
        }

    @Test
    fun lastPlayedSessionPrefersIncompleteWhereasAnyIncludesCompleted() =
        runTest {
            dao.upsertAll(
                listOf(
                    history("incomplete", isCompleted = false, lastPlayedAt = 1L),
                    history("completed", isCompleted = true, lastPlayedAt = 10L),
                ),
            )
            assertEquals("incomplete", dao.getLastPlayedSession()?.episodeId)
            assertEquals("completed", dao.getLastPlayedSessionAny()?.episodeId)
        }

    @Test
    fun likeStatusRoundTrips() =
        runTest {
            dao.upsert(history("ep-1"))
            dao.setLikeStatus("ep-1", true)

            assertTrue(dao.getHistoryItem("ep-1")!!.isLiked)
            assertEquals(listOf("ep-1"), dao.getLikedEpisodesList().map { it.episodeId })
            assertEquals(listOf("ep-1"), dao.getLikedEpisodes().first().map { it.episodeId })

            dao.setLikeStatus("ep-1", false)
            assertTrue(dao.getLikedEpisodesList().isEmpty())
        }

    @Test
    fun updateProgressMarksDirty() =
        runTest {
            dao.upsert(history("ep-1", isDirty = false))
            dao.updateProgress("ep-1", progressMs = 200, durationMs = 1000, lastPlayedAt = 50)

            val item = dao.getHistoryItem("ep-1")!!
            assertEquals(200L, item.progressMs)
            assertEquals(50L, item.lastPlayedAt)
            assertTrue(item.isDirty)
        }

    @Test
    fun completionStatusAndCompletedIds() =
        runTest {
            dao.upsertAll(listOf(history("a"), history("b")))
            dao.setCompletionStatus("a", true)

            assertTrue(dao.getHistoryItem("a")!!.isCompleted)
            assertEquals(listOf("a"), dao.getCompletedEpisodeIds())
            assertEquals(listOf("a"), dao.getCompletedEpisodeIdsFlow().first())
        }

    @Test
    fun recentlyPlayedPodcastsAreDistinctAndTimeFiltered() =
        runTest {
            dao.upsertAll(
                listOf(
                    history("a", podcastId = "p1", lastPlayedAt = 100L),
                    history("b", podcastId = "p1", lastPlayedAt = 150L),
                    history("c", podcastId = "p2", lastPlayedAt = 50L),
                ),
            )
            assertEquals(setOf("p1"), dao.getRecentlyPlayedPodcasts(sinceTimestamp = 60L).toSet())
        }

    @Test
    fun recentHistoryListRespectsLimit() =
        runTest {
            dao.upsertAll((1..5).map { history("ep-$it", lastPlayedAt = it.toLong()) })
            assertEquals(3, dao.getRecentHistoryList(3).size)
            assertEquals("ep-5", dao.getRecentHistoryList(3).first().episodeId)
        }
}
