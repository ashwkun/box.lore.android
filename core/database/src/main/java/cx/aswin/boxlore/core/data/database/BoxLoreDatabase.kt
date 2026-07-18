package cx.aswin.boxlore.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cx.aswin.boxlore.core.data.database.entities.QueueItem
import cx.aswin.boxlore.core.data.database.dao.QueueDao

@Database(
    entities = [
        ListeningHistoryEntity::class,
        PodcastEntity::class,
        DownloadedEpisodeEntity::class,
        QueueItem::class,
        RssEpisodeEntity::class,
    ],
    version = 29,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BoxLoreDatabase : RoomDatabase() {
    abstract fun listeningHistoryDao(): ListeningHistoryDao
    abstract fun podcastDao(): PodcastDao
    abstract fun rssEpisodeDao(): RssEpisodeDao
    abstract fun downloadedEpisodeDao(): DownloadedEpisodeDao
    abstract fun queueDao(): QueueDao

    companion object {
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN podcastGuid TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN fundingUrl TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN fundingMessage TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN medium TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN hasValue INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_items ADD COLUMN episodeType TEXT")
                db.execSQL("ALTER TABLE queue_items ADD COLUMN seasonNumber INTEGER")
                db.execSQL("ALTER TABLE queue_items ADD COLUMN episodeNumber INTEGER")
            }
        }
        
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN type TEXT NOT NULL DEFAULT 'episodic'")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN updateFrequency TEXT")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN location TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN license TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN subscribedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN preferredSort TEXT")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_items ADD COLUMN enclosureType TEXT")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE listening_history ADD COLUMN enclosureType TEXT")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE listening_history ADD COLUMN isManualCompletion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE listening_history ADD COLUMN isBulkCompletion INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE listening_history ADD COLUMN episodeDescription TEXT")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_episodes ADD COLUMN isSmartDownloaded INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN autoDownloadEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'podcast_index'")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN feedUrl TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN feedEtag TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN feedLastModified TEXT")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN feedDeclaredUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN rssRefreshCapability TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN lastRssSyncAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN rssCatalogStale INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN rssHasNewEpisodes INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rss_episodes (
                        episodeId TEXT NOT NULL PRIMARY KEY CHECK(CAST(episodeId AS INTEGER) < 0),
                        podcastId TEXT NOT NULL,
                        guid TEXT,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        audioUrl TEXT NOT NULL,
                        imageUrl TEXT,
                        duration INTEGER NOT NULL,
                        publishedDate INTEGER NOT NULL,
                        chaptersUrl TEXT,
                        transcriptUrl TEXT,
                        transcripts TEXT,
                        persons TEXT,
                        seasonNumber INTEGER,
                        episodeNumber INTEGER,
                        episodeType TEXT,
                        enclosureType TEXT,
                        FOREIGN KEY(podcastId) REFERENCES podcasts(podcastId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_rss_episodes_podcastId ON rss_episodes(podcastId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_rss_episodes_podcastId_publishedDate ON rss_episodes(podcastId, publishedDate)",
                )
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN linkedPodcastIndexId TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_podcasts_linkedPodcastIndexId " +
                        "ON podcasts(linkedPodcastIndexId)",
                )
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN skipBeginningOverrideMs INTEGER")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN skipEndingOverrideMs INTEGER")
            }
        }

        @Volatile
        private var INSTANCE: BoxLoreDatabase? = null


        private fun renameDatabaseSuffixFiles(oldDbFile: java.io.File, newDbFile: java.io.File) {
            val suffixes = listOf("-journal", "-shm", "-wal")
            for (suffix in suffixes) {
                val oldSuffixFile = java.io.File(oldDbFile.path + suffix)
                val newSuffixFile = java.io.File(newDbFile.path + suffix)
                if (oldSuffixFile.exists()) {
                    val renamed = oldSuffixFile.renameTo(newSuffixFile)
                    if (!renamed) {
                        android.util.Log.w("BoxLoreDatabase", "Failed to rename database suffix file: $suffix")
                    }
                }
            }
        }

        private fun renameDatabaseFileIfExists(context: android.content.Context) {
            val oldDbFile = context.getDatabasePath("boxcast_database")
            val newDbFile = context.getDatabasePath("boxlore_database")
            
            if (oldDbFile.exists()) {
                val success = oldDbFile.renameTo(newDbFile)
                if (success) {
                    renameDatabaseSuffixFiles(oldDbFile, newDbFile)
                    android.util.Log.d("BoxLoreDatabase", "Successfully migrated database file to boxlore_database")
                } else {
                    android.util.Log.e("BoxLoreDatabase", "Failed to migrate database file from boxcast_database to boxlore_database")
                }
            }
        }

        fun getDatabase(context: android.content.Context): BoxLoreDatabase {
            return INSTANCE ?: synchronized(this) {
                renameDatabaseFileIfExists(context)
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    BoxLoreDatabase::class.java,
                    "boxlore_database"
                )
                .addMigrations(
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                    MIGRATION_24_25,
                    MIGRATION_25_26,
                    MIGRATION_26_27,
                    MIGRATION_27_28,
                    MIGRATION_28_29,
                )
                .fallbackToDestructiveMigration(dropAllTables = true) // For development simplicity on older versions
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
