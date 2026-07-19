package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastDaoExtendedTest {

    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun podcast(
        podcastId: String,
        title: String = "Podcast $podcastId",
        isSubscribed: Boolean = true,
        sourceType: String = PodcastEntity.SOURCE_PODCAST_INDEX,
        linkedPodcastIndexId: String? = null,
        notificationsEnabled: Boolean = false,
    ) = PodcastEntity(
        podcastId = podcastId,
        title = title,
        author = "Author",
        imageUrl = "https://example.com/$podcastId.jpg",
        description = "desc",
        isSubscribed = isSubscribed,
        subscribedAt = 100L,
        sourceType = sourceType,
        linkedPodcastIndexId = linkedPodcastIndexId,
        notificationsEnabled = notificationsEnabled,
    )

    @Test
    fun subscribedPodcastsFilteredAndSortedByTitle() = runTest {
        dao.upsert(podcast("1", title = "Zebra"))
        dao.upsert(podcast("2", title = "Apple"))
        dao.upsert(podcast("3", title = "Unsubbed", isSubscribed = false))

        assertEquals(listOf("Apple", "Zebra"), dao.getSubscribedPodcasts().first().map { it.title })
        assertEquals(listOf("Apple", "Zebra"), dao.getSubscribedPodcastsList().map { it.title })
    }

    @Test
    fun subscribedPodcastsSplitBySourceType() = runTest {
        dao.upsert(podcast("rss-1", sourceType = PodcastEntity.SOURCE_RSS))
        dao.upsert(podcast("idx-1", sourceType = PodcastEntity.SOURCE_PODCAST_INDEX))

        assertEquals(listOf("rss-1"), dao.getSubscribedRssPodcasts().map { it.podcastId })
        assertEquals(listOf("idx-1"), dao.getSubscribedPodcastIndexPodcasts().map { it.podcastId })
    }

    @Test
    fun rssPodcastLinkedToResolvesByLinkedId() = runTest {
        dao.upsert(podcast("-1001", sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = "920"))
        assertEquals("-1001", dao.getRssPodcastLinkedTo("920")?.podcastId)
        assertNull(dao.getRssPodcastLinkedTo("missing"))
    }

    @Test
    fun getPodcastsByIdsReturnsRequestedRows() = runTest {
        dao.upsert(podcast("1"))
        dao.upsert(podcast("2"))
        dao.upsert(podcast("3"))

        assertEquals(setOf("1", "3"), dao.getPodcastsByIds(listOf("1", "3")).map { it.podcastId }.toSet())
    }

    @Test
    fun setSubscribedTogglesFlag() = runTest {
        dao.upsert(podcast("1", isSubscribed = true))
        dao.setSubscribed("1", false)
        assertFalse(dao.getPodcast("1")!!.isSubscribed)
    }

    @Test
    fun retireLinkedPodcastIndexSubscriptionResetsFields() = runTest {
        dao.upsert(
            podcast("1", isSubscribed = true, notificationsEnabled = true).copy(autoDownloadEnabled = true),
        )

        dao.retireLinkedPodcastIndexSubscription("1")

        val retired = dao.getPodcast("1")!!
        assertFalse(retired.isSubscribed)
        assertEquals(0L, retired.subscribedAt)
        assertFalse(retired.notificationsEnabled)
        assertFalse(retired.autoDownloadEnabled)
    }

    @Test
    fun updatePreferredSortAndType() = runTest {
        dao.upsert(podcast("1"))
        dao.updatePreferredSortAndType("1", sort = "oldest", type = "serial")

        val updated = dao.getPodcast("1")!!
        assertEquals("oldest", updated.preferredSort)
        assertEquals("serial", updated.type)
    }

    @Test
    fun notificationToggleAndQuery() = runTest {
        dao.upsert(podcast("1", notificationsEnabled = false))
        dao.upsert(podcast("2", notificationsEnabled = false))
        dao.setNotificationsEnabled("1", true)

        assertTrue(dao.getPodcast("1")!!.notificationsEnabled)
        assertEquals(listOf("1"), dao.getNotificationEnabledPodcasts().map { it.podcastId })
    }

    @Test
    fun autoDownloadToggle() = runTest {
        dao.upsert(podcast("1"))
        dao.setAutoDownloadEnabled("1", true)
        assertTrue(dao.getPodcast("1")!!.autoDownloadEnabled)
    }

    @Test
    fun playbackSkipOverridesPersist() = runTest {
        dao.upsert(podcast("1"))
        dao.setPlaybackSkipOverrides("1", skipBeginningMs = 5_000L, skipEndingMs = 10_000L)

        val updated = dao.getPodcast("1")!!
        assertEquals(5_000L, updated.skipBeginningOverrideMs)
        assertEquals(10_000L, updated.skipEndingOverrideMs)
    }

    @Test
    fun updateRssStateAppliesPartialUpdate() = runTest {
        dao.upsert(podcast("-1001", sourceType = PodcastEntity.SOURCE_RSS))

        dao.updateRssState(
            RssFeedStateUpdate(
                podcastId = "-1001",
                feedEtag = "etag-1",
                feedLastModified = "Mon",
                feedDeclaredUpdatedAt = 42L,
                rssRefreshCapability = PodcastEntity.RSS_REFRESH_HEAD_VALIDATORS,
                lastRssSyncAt = 999L,
                rssCatalogStale = true,
                rssHasNewEpisodes = true,
            ),
        )

        val updated = dao.getPodcast("-1001")!!
        assertEquals("etag-1", updated.feedEtag)
        assertEquals("Mon", updated.feedLastModified)
        assertEquals(42L, updated.feedDeclaredUpdatedAt)
        assertEquals(PodcastEntity.RSS_REFRESH_HEAD_VALIDATORS, updated.rssRefreshCapability)
        assertEquals(999L, updated.lastRssSyncAt)
        assertTrue(updated.rssCatalogStale)
        assertTrue(updated.rssHasNewEpisodes)
        // Untouched identity fields survive the partial update.
        assertEquals("Podcast -1001", updated.title)
    }

    @Test
    fun clearRssNewEpisodesFlag() = runTest {
        dao.upsert(podcast("-1001", sourceType = PodcastEntity.SOURCE_RSS).copy(rssHasNewEpisodes = true))
        dao.clearRssNewEpisodesFlag("-1001")
        assertFalse(dao.getPodcast("-1001")!!.rssHasNewEpisodes)
    }

    @Test
    fun updateLatestEpisodeAcceptsNull() = runTest {
        dao.upsert(podcast("1"))
        dao.updateLatestEpisode("1", null)
        assertNull(dao.getPodcast("1")!!.latestEpisode)
    }
}
