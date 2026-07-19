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
)
