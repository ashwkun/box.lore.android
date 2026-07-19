package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
class ListeningInsightsDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: ListeningInsightsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.listeningInsightsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun session(
        id: String,
        episodeId: String = "ep-1",
        podcastId: String = "pod-1",
        startedAt: Long = 1_000L,
        endedAt: Long = 2_000L,
        consumedMs: Long = 1_000L,
        completed: Boolean = false,
        localDay: Long = 10L,
        timeBucket: Int = 0,
    ) = ListeningSessionEntity(
        sessionId = id,
        episodeId = episodeId,
        podcastId = podcastId,
        startedAt = startedAt,
        endedAt = endedAt,
        consumedMs = consumedMs,
        completed = completed,
        localDay = localDay,
        timeBucket = timeBucket,
    )

    @Test
    fun upsertAndQuerySessions() =
        runTest {
            dao.upsertSession(session("s1", consumedMs = 500))
            assertEquals(500L, dao.getAllSessions().single().consumedMs)
        }

    @Test
    fun rollupSkipsTodayAndMergesOlderSessions() =
        runTest {
            val today = 100L
            dao.upsertSessions(
                listOf(
                    session("old-a", localDay = 10, endedAt = 10_000, consumedMs = 100, timeBucket = 0),
                    session("old-b", localDay = 10, endedAt = 11_000, consumedMs = 200, completed = true, timeBucket = 1),
                    session("today", localDay = today, endedAt = 99_000, consumedMs = 999, timeBucket = 2),
                ),
            )

            val rolled =
                dao.rollUpEligibleSessions(
                    cutoffEndedAtExclusive = 50_000L,
                    todayLocalDay = today,
                )

            assertEquals(2, rolled)
            assertEquals(1, dao.getAllSessions().size)
            assertEquals("today", dao.getAllSessions().single().sessionId)

            val rollup = dao.getRollup(10L, "ep-1")!!
            assertEquals(300L, rollup.consumedMs)
            assertEquals(2, rollup.sessionCount)
            assertEquals(1, rollup.completionCount)
            assertEquals(100L, rollup.morningMs)
            assertEquals(200L, rollup.afternoonMs)
        }

    @Test
    fun rollupIsIdempotentAndMergesIntoExisting() =
        runTest {
            val today = 200L
            dao.upsertSession(session("old-1", localDay = 5, endedAt = 5_000, consumedMs = 50))
            dao.rollUpEligibleSessions(cutoffEndedAtExclusive = 10_000L, todayLocalDay = today)
            assertEquals(50L, dao.getRollup(5L, "ep-1")!!.consumedMs)

            dao.upsertSession(session("old-2", localDay = 5, endedAt = 6_000, consumedMs = 25))
            dao.rollUpEligibleSessions(cutoffEndedAtExclusive = 10_000L, todayLocalDay = today)

            val rollup = dao.getRollup(5L, "ep-1")!!
            assertEquals(75L, rollup.consumedMs)
            assertEquals(2, rollup.sessionCount)
            assertTrue(dao.getAllSessions().isEmpty())
        }

    @Test
    fun samePodcastCanExistInRollupAndTodayRaw() =
        runTest {
            val today = 50L
            dao.upsertRollup(
                ListeningRollupEntity(
                    localDay = 1L,
                    episodeId = "ep-old",
                    podcastId = "pod-shared",
                    consumedMs = 1_000L,
                    sessionCount = 2,
                    completionCount = 1,
                    lastListenedAt = 1_000L,
                ),
            )
            dao.upsertSession(
                session(
                    "today-s",
                    episodeId = "ep-new",
                    podcastId = "pod-shared",
                    localDay = today,
                    endedAt = 90_000,
                    consumedMs = 400,
                ),
            )

            dao.rollUpEligibleSessions(cutoffEndedAtExclusive = 10_000L, todayLocalDay = today)

            assertEquals(1, dao.getAllRollups().size)
            assertEquals(1, dao.getAllSessions().size)
            assertEquals("pod-shared", dao.getAllSessions().single().podcastId)
            assertEquals("pod-shared", dao.getAllRollups().single().podcastId)
        }

    @Test
    fun deleteEpisodeAndClearAll() =
        runTest {
            dao.upsertSession(session("s1", episodeId = "ep-a"))
            dao.upsertSession(session("s2", episodeId = "ep-b"))
            dao.upsertRollup(
                ListeningRollupEntity(
                    localDay = 1,
                    episodeId = "ep-a",
                    podcastId = "pod-1",
                    consumedMs = 10,
                    sessionCount = 1,
                    completionCount = 0,
                    lastListenedAt = 1,
                ),
            )

            dao.deleteEpisodeAnalytics("ep-a")
            assertNull(dao.getRollup(1, "ep-a"))
            assertEquals(listOf("ep-b"), dao.getAllSessions().map { it.episodeId })

            dao.clearAllAnalytics()
            assertTrue(dao.getAllSessions().isEmpty())
            assertTrue(dao.getAllRollups().isEmpty())
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListeningInsightsMigration29To30Test {
    @Test
    fun migrationSqlCreatesSessionAndRollupTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(null)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(29) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE listening_history (" +
                                    "episodeId TEXT NOT NULL PRIMARY KEY, " +
                                    "podcastId TEXT NOT NULL, " +
                                    "episodeTitle TEXT NOT NULL, " +
                                    "episodeImageUrl TEXT, " +
                                    "podcastImageUrl TEXT, " +
                                    "episodeAudioUrl TEXT, " +
                                    "podcastName TEXT NOT NULL, " +
                                    "progressMs INTEGER NOT NULL, " +
                                    "durationMs INTEGER NOT NULL, " +
                                    "isCompleted INTEGER NOT NULL, " +
                                    "isLiked INTEGER NOT NULL, " +
                                    "lastPlayedAt INTEGER NOT NULL, " +
                                    "isDirty INTEGER NOT NULL, " +
                                    "syncedAt INTEGER NOT NULL, " +
                                    "enclosureType TEXT, " +
                                    "isManualCompletion INTEGER NOT NULL, " +
                                    "isBulkCompletion INTEGER NOT NULL, " +
                                    "episodeDescription TEXT)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = openHelper.writableDatabase
        BoxLoreDatabaseMigrations.migrate29To30(db)

        val sessions =
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='listening_sessions'")
        assertTrue(sessions.moveToFirst())
        sessions.close()

        val rollups =
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='listening_rollups'")
        assertTrue(rollups.moveToFirst())
        rollups.close()
        db.close()
    }
}
