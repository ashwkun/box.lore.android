package cx.aswin.boxlore.core.ranking.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AdaptiveModelEntity::class,
        PreferenceFacetEntity::class,
        RankingExposureEntity::class,
        RankingOutcomeEntity::class,
        HardShowExclusionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AdaptiveRankingDatabase : RoomDatabase() {
    abstract fun adaptiveRankingDao(): AdaptiveRankingDao

    companion object {
        private const val DATABASE_NAME = "adaptive_ranking_database"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE ranking_exposures ADD COLUMN requestId TEXT")
                    db.execSQL("ALTER TABLE ranking_exposures ADD COLUMN placement TEXT")
                    db.execSQL("ALTER TABLE ranking_exposures ADD COLUMN rankPosition INTEGER")
                    db.execSQL("ALTER TABLE ranking_exposures ADD COLUMN retrievalReason TEXT")
                    db.execSQL("ALTER TABLE ranking_exposures ADD COLUMN revision TEXT")
                    db.execSQL("ALTER TABLE ranking_exposures ADD COLUMN accumulatedReward REAL")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ranking_outcomes (
                            outcomeId TEXT NOT NULL PRIMARY KEY,
                            exposureId TEXT,
                            episodeId TEXT NOT NULL,
                            podcastId TEXT NOT NULL,
                            action TEXT NOT NULL,
                            reward REAL NOT NULL,
                            listenSeconds INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            finalizedAt INTEGER
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS hard_show_exclusions (
                            podcastId TEXT NOT NULL PRIMARY KEY,
                            reason TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            sourceExposureId TEXT
                        )
                        """.trimIndent(),
                    )
                }
            }

        @Volatile
        private var instance: AdaptiveRankingDatabase? = null

        fun getDatabase(context: Context): AdaptiveRankingDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AdaptiveRankingDatabase::class.java,
                        DATABASE_NAME,
                    ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        /** Test helper: clear the singleton so in-memory builders stay isolated. */
        fun clearInstanceForTests() {
            instance = null
        }
    }
}
