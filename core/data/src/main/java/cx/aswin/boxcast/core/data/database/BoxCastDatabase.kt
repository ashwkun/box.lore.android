package cx.aswin.boxcast.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cx.aswin.boxcast.core.data.database.entities.QueueItem
import cx.aswin.boxcast.core.data.database.dao.QueueDao

@Database(
    entities = [ListeningHistoryEntity::class, PodcastEntity::class, DownloadedEpisodeEntity::class, QueueItem::class],
    version = 13, // Increased DB version for Podcasting 2.0 fields
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BoxCastDatabase : RoomDatabase() {
    abstract fun listeningHistoryDao(): ListeningHistoryDao
    abstract fun podcastDao(): PodcastDao
    abstract fun downloadedEpisodeDao(): DownloadedEpisodeDao
    abstract fun queueDao(): QueueDao

    companion object {
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE podcasts ADD COLUMN podcastGuid TEXT")
                database.execSQL("ALTER TABLE podcasts ADD COLUMN fundingUrl TEXT")
                database.execSQL("ALTER TABLE podcasts ADD COLUMN fundingMessage TEXT")
                database.execSQL("ALTER TABLE podcasts ADD COLUMN medium TEXT")
                database.execSQL("ALTER TABLE podcasts ADD COLUMN hasValue INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: BoxCastDatabase? = null

        fun getDatabase(context: android.content.Context): BoxCastDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    BoxCastDatabase::class.java,
                    "boxcast_database"
                )
                .addMigrations(MIGRATION_12_13)
                .fallbackToDestructiveMigration() // For development simplicity on older versions
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
