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
    suspend fun getFacet(facetType: String, facetKey: String): PreferenceFacetEntity?

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
    suspend fun deleteFacet(facetType: String, facetKey: String)

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
        SET resolvedAt = :resolvedAt,
            reward = :reward,
            listenSeconds = :listenSeconds,
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
        SET reward = :reward,
            accumulatedReward = :reward,
            listenSeconds = :listenSeconds
        WHERE exposureId = :exposureId AND resolvedAt IS NOT NULL
        """,
    )
    suspend fun updateResolvedReward(
        exposureId: String,
        reward: Double,
        listenSeconds: Long,
    ): Int

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
    suspend fun upsertExclusion(exclusion: HardShowExclusionEntity)

    @Query("SELECT * FROM hard_show_exclusions")
    suspend fun getAllExclusions(): List<HardShowExclusionEntity>

    @Query("SELECT podcastId FROM hard_show_exclusions")
    suspend fun getExcludedPodcastIds(): List<String>

    @Query("SELECT * FROM hard_show_exclusions WHERE podcastId = :podcastId LIMIT 1")
    suspend fun getExclusion(podcastId: String): HardShowExclusionEntity?

    @Query("DELETE FROM hard_show_exclusions WHERE podcastId = :podcastId")
    suspend fun deleteExclusion(podcastId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: RankingOutcomeEntity)

    @Query("SELECT * FROM ranking_outcomes WHERE exposureId = :exposureId ORDER BY recordedAt ASC")
    suspend fun getOutcomesForExposure(exposureId: String): List<RankingOutcomeEntity>

    @Query("SELECT * FROM ranking_outcomes ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun getRecentOutcomes(limit: Int): List<RankingOutcomeEntity>

    @Query("DELETE FROM adaptive_models")
    suspend fun clearModels()

    @Query("DELETE FROM preference_facets")
    suspend fun clearFacets()

    @Query("DELETE FROM ranking_exposures")
    suspend fun clearExposures()

    @Query("DELETE FROM hard_show_exclusions")
    suspend fun clearExclusions()

    @Query("DELETE FROM ranking_outcomes")
    suspend fun clearOutcomes()

    @Transaction
    suspend fun clearAll() {
        clearModels()
        clearFacets()
        clearExposures()
        clearExclusions()
        clearOutcomes()
    }

    @Transaction
    suspend fun replaceAll(
        models: List<AdaptiveModelEntity>,
        facets: List<PreferenceFacetEntity>,
        exposures: List<RankingExposureEntity>,
    ) {
        clearAll()
        upsertModels(models)
        upsertFacets(facets)
        upsertExposures(exposures)
    }
}
