package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Minimal in-memory Room DAO smoke (B4).
 *
 * If this suite fails to configure under AGP (schemas / includeAndroidResources /
 * AAR metadata), treat documentation in `core/database/README.md` as the B4 exit
 * and keep RSS ID fixtures as the identity safety net — see `docs/TESTING.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastDaoInMemoryTest {

    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertAndGetPodcast_roundTrips() =
        runTest {
            val entity =
                PodcastEntity(
                    podcastId = "920666",
                    title = "The Daily",
                    author = "The New York Times",
                    imageUrl = "https://example.com/daily.jpg",
                    description = "News",
                    isSubscribed = true,
                )

            dao.upsert(entity)

            val loaded = dao.getPodcast("920666")
            assertEquals("The Daily", loaded?.title)
            assertEquals("The New York Times", loaded?.author)
            assertNull(dao.getPodcast("missing"))
        }
}
