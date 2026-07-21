package cx.aswin.boxlore.core.ranking.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AdaptiveModelEntity::class,
        PreferenceFacetEntity::class,
        RankingExposureEntity::class,
        HardShowExclusionEntity::class,
        RankingOutcomeEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AdaptiveRankingDatabase : RoomDatabase() {
    abstract fun adaptiveRankingDao(): AdaptiveRankingDao

    companion object {
        private const val DATABASE_NAME = "adaptive_ranking_database"

        @Volatile
        private var instance: AdaptiveRankingDatabase? = null

        fun getDatabase(context: Context): AdaptiveRankingDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdaptiveRankingDatabase::class.java,
                    DATABASE_NAME,
                )
                    // Personalization state is disposable across this rebuild; wipe on schema bump.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
