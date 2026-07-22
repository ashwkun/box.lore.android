package cx.aswin.boxlore.core.ranking.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AdaptiveRankingDao {
    @Query("SELECT * FROM adaptive_models WHERE objective = :objective LIMIT 1")
    suspend fun getModel(objective: String): AdaptiveModelEntity?

    @Query("SELECT * FROM adaptive_models")
    suspend fun getAllModels(): List<AdaptiveModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: AdaptiveModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModels(models: List<AdaptiveModelEntity>)

    @Query(
        """
        SELECT * FROM preference_facets
        WHERE facetType = :facetType AND facetKey = :facetKey
        LIMIT 1
        """,
    )
    suspend fun getFacet(
        facetType: String,
        facetKey: String,
    ): PreferenceFacetEntity?

    @Query("SELECT * FROM preference_facets")
    suspend fun getAllFacets(): List<PreferenceFacetEntity>

    @Query("SELECT * FROM preference_facets WHERE facetType = :facetType")
    suspend fun getFacetsByType(facetType: String): List<PreferenceFacetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFacet(facet: PreferenceFacetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFacets(facets: List<PreferenceFacetEntity>)

    @Query(
        """
        DELETE FROM preference_facets
        WHERE facetType = :facetType AND facetKey = :facetKey
        """,
    )
    suspend fun deleteFacet(
        facetType: String,
        facetKey: String,
    )

    @Query("SELECT * FROM ranking_exposures WHERE exposureId = :exposureId LIMIT 1")
    suspend fun getExposure(exposureId: String): RankingExposureEntity?

    @Query("SELECT * FROM ranking_exposures ORDER BY shownAt DESC")
    suspend fun getAllExposures(): List<RankingExposureEntity>

    @Query(
        """
        SELECT * FROM ranking_exposures
        WHERE episodeId = :episodeId AND resolvedAt IS NULL
        ORDER BY shownAt DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestUnresolvedExposure(episodeId: String): RankingExposureEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExposure(exposure: RankingExposureEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExposures(exposures: List<RankingExposureEntity>)

    @Query(
        """
        UPDATE ranking_exposures
        SET resolvedAt = :resolvedAt, reward = :reward, listenSeconds = :listenSeconds,
            accumulatedReward = :reward
        WHERE exposureId = :exposureId AND resolvedAt IS NULL
        """,
    )
    suspend fun resolveExposure(
        exposureId: String,
        resolvedAt: Long,
        reward: Double,
        listenSeconds: Long,
    ): Int

    @Query(
        """
        UPDATE ranking_exposures
        SET accumulatedReward = :accumulatedReward, listenSeconds = :listenSeconds
        WHERE exposureId = :exposureId AND resolvedAt IS NULL
        """,
    )
    suspend fun updateAccumulatedReward(
        exposureId: String,
        accumulatedReward: Double,
        listenSeconds: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM ranking_exposures WHERE surface = :surface")
    suspend fun countExposuresBySurface(surface: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM ranking_exposures
        WHERE surface = :surface AND resolvedAt IS NOT NULL
        """,
    )
    suspend fun countResolvedExposuresBySurface(surface: String): Int

    @Query(
        """
        SELECT MIN(shownAt) FROM ranking_exposures
        WHERE surface = :surface AND resolvedAt IS NULL
        """,
    )
    suspend fun oldestPendingExposureShownAt(surface: String): Long?

    @Query("DELETE FROM ranking_exposures WHERE shownAt < :cutoff")
    suspend fun pruneExposuresBefore(cutoff: Long): Int

    @Query(
        """
        DELETE FROM ranking_exposures
        WHERE exposureId IN (
            SELECT exposureId FROM ranking_exposures
            ORDER BY shownAt DESC
            LIMIT -1 OFFSET :keepCount
        )
        """,
    )
    suspend fun pruneExposuresToCount(keepCount: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutcome(outcome: RankingOutcomeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutcomes(outcomes: List<RankingOutcomeEntity>)

    @Query("SELECT * FROM ranking_outcomes WHERE exposureId = :exposureId ORDER BY createdAt ASC")
    suspend fun getOutcomesForExposure(exposureId: String): List<RankingOutcomeEntity>

    @Query("SELECT * FROM ranking_outcomes ORDER BY createdAt DESC")
    suspend fun getAllOutcomes(): List<RankingOutcomeEntity>

    @Query(
        """
        UPDATE ranking_outcomes
        SET finalizedAt = :finalizedAt
        WHERE exposureId = :exposureId AND finalizedAt IS NULL
        """,
    )
    suspend fun finalizeOutcomesForExposure(
        exposureId: String,
        finalizedAt: Long,
    ): Int

    @Query("DELETE FROM ranking_outcomes WHERE createdAt < :cutoff")
    suspend fun pruneOutcomesBefore(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM ranking_outcomes")
    suspend fun countAllOutcomes(): Int

    @Query("SELECT COUNT(*) FROM ranking_outcomes WHERE exposureId IS NULL")
    suspend fun countUnmatchedOutcomes(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHardExclusion(exclusion: HardShowExclusionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHardExclusions(exclusions: List<HardShowExclusionEntity>)

    @Query("SELECT * FROM hard_show_exclusions WHERE podcastId = :podcastId LIMIT 1")
    suspend fun getHardExclusion(podcastId: String): HardShowExclusionEntity?

    @Query("SELECT * FROM hard_show_exclusions")
    suspend fun getAllHardExclusions(): List<HardShowExclusionEntity>

    @Query("SELECT podcastId FROM hard_show_exclusions")
    suspend fun getHardExcludedPodcastIds(): List<String>

    @Query("DELETE FROM hard_show_exclusions WHERE podcastId = :podcastId")
    suspend fun deleteHardExclusion(podcastId: String)

    @Query("DELETE FROM adaptive_models")
    suspend fun clearModels()

    @Query("DELETE FROM preference_facets")
    suspend fun clearFacets()

    @Query("DELETE FROM ranking_exposures")
    suspend fun clearExposures()

    @Query("DELETE FROM ranking_outcomes")
    suspend fun clearOutcomes()

    @Query("DELETE FROM hard_show_exclusions")
    suspend fun clearHardExclusions()

    @Transaction
    suspend fun clearAll() {
        clearModels()
        clearFacets()
        clearExposures()
        clearOutcomes()
        clearHardExclusions()
    }

    @Transaction
    suspend fun replaceAll(
        models: List<AdaptiveModelEntity>,
        facets: List<PreferenceFacetEntity>,
        exposures: List<RankingExposureEntity>,
        outcomes: List<RankingOutcomeEntity> = emptyList(),
        hardExclusions: List<HardShowExclusionEntity> = emptyList(),
    ) {
        clearAll()
        upsertModels(models)
        upsertFacets(facets)
        upsertExposures(exposures)
        upsertOutcomes(outcomes)
        upsertHardExclusions(hardExclusions)
    }
}
