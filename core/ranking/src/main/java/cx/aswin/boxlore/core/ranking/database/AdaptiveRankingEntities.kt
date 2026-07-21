package cx.aswin.boxlore.core.ranking.database

import androidx.room.Entity

@Entity(
    tableName = "adaptive_models",
    primaryKeys = ["objective"],
)
data class AdaptiveModelEntity(
    val objective: String,
    val featureSchemaVersion: Int,
    val dimension: Int,
    val covariance: ByteArray,
    val inverseCovariance: ByteArray,
    val rewardVector: ByteArray,
    val updateCount: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "preference_facets",
    primaryKeys = ["facetType", "facetKey"],
)
data class PreferenceFacetEntity(
    val facetType: String,
    val facetKey: String,
    val positiveEvidence: Double,
    val negativeEvidence: Double,
    val updatedAt: Long,
)

@Entity(
    tableName = "ranking_exposures",
    primaryKeys = ["exposureId"],
)
data class RankingExposureEntity(
    val exposureId: String,
    val episodeId: String,
    val podcastId: String,
    val objective: String,
    val surface: String,
    val source: String,
    val featureSchemaVersion: Int,
    val featureVector: ByteArray,
    val shownAt: Long,
    val resolvedAt: Long?,
    val reward: Double?,
    val listenSeconds: Long,
    val entryPoint: String?,
    val online: Boolean,
    val sessionId: String? = null,
    val rankPosition: Int? = null,
    val intentId: String? = null,
    val retrievalReason: String? = null,
    val revision: String? = null,
    val accumulatedReward: Double? = null,
)

@Entity(
    tableName = "hard_show_exclusions",
    primaryKeys = ["podcastId"],
)
data class HardShowExclusionEntity(
    val podcastId: String,
    val reason: String,
    val createdAt: Long,
    val sourceExposureId: String? = null,
)

@Entity(
    tableName = "ranking_outcomes",
    primaryKeys = ["outcomeId"],
)
data class RankingOutcomeEntity(
    val outcomeId: String,
    val exposureId: String?,
    val episodeId: String,
    val podcastId: String,
    val action: String,
    val partialReward: Double,
    val listenSeconds: Long,
    val durationSeconds: Long,
    val recordedAt: Long,
    val appliedToModel: Boolean,
)
