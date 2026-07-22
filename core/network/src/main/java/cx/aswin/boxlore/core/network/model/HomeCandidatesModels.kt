package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.Serializable

/**
 * Request / response DTOs for `POST /home/candidates/v1`.
 * Proxy is stateless; Android owns final scoring and module allocation.
 */
@Serializable
data class HomeCandidatesV1Request(
    val contractVersion: Int = 1,
    val requestedModules: List<String> =
        listOf(
            "taste",
            "because_you_like",
            "mission",
            "regional",
        ),
    val seeds: List<HomeCandidateSeedDto> = emptyList(),
    val anchorPodcastId: String? = null,
    val excludedPodcastIds: List<String> = emptyList(),
    val excludedEpisodeIds: List<String> = emptyList(),
    val country: String = "us",
    val languages: List<String> = emptyList(),
    val noveltyPreference: Double? = null,
    val tasteBudget: Int = 24,
    val becauseYouLikeBudget: Int = 18,
    val missionBudget: Int = 12,
    val regionalBudget: Int = 30,
    val daypart: String? = null,
    val localMinuteOfDay: Int? = null,
    val missionId: String? = null,
    val revision: String? = null,
)

@Serializable
data class HomeCandidateSeedDto(
    val episodeId: String? = null,
    val podcastId: String? = null,
    val weight: Double = 0.5,
    val fallback: HomeCandidateSeedFallbackDto? = null,
)

@Serializable
data class HomeCandidateSeedFallbackDto(
    val episodeTitle: String? = null,
    val podcastTitle: String? = null,
    val description: String? = null,
)

@Serializable
data class HomeCandidatesV1Response(
    val status: String = "false",
    val contractVersion: Int = 1,
    val algorithmVersion: String? = null,
    val inventoryVersion: String? = null,
    val catalogVersion: Int? = null,
    val requestId: String? = null,
    val resolvedModules: List<String> = emptyList(),
    val modules: HomeCandidateModulesDto = HomeCandidateModulesDto(),
    val calibratedPriors: HomeCandidatesCalibratedPriorsDto? = null,
)

@Serializable
data class HomeCandidatesCalibratedPriorsDto(
    val noveltyPreference: Double? = null,
    val seedCount: Int = 0,
    val seedVectorCount: Int = 0,
)

@Serializable
data class HomeCandidateModulesDto(
    val taste: List<HomeCandidateItemDto> = emptyList(),
    val becauseYouLike: List<HomeCandidateItemDto> = emptyList(),
    val mission: List<HomeCandidateItemDto> = emptyList(),
    val regional: List<HomeCandidateItemDto> = emptyList(),
)

@Serializable
data class HomeCandidateItemDto(
    val episodeId: String? = null,
    val podcastId: String? = null,
    val title: String? = null,
    val podcastTitle: String? = null,
    val imageUrl: String? = null,
    val podcastImageUrl: String? = null,
    val audioUrl: String? = null,
    val durationSeconds: Int? = null,
    val publishedDate: Long? = null,
    val genre: String? = null,
    val priorScore: Double = 0.0,
    val retrievalScore: Double = 0.0,
    val source: String? = null,
    val reason: String? = null,
    val isNovel: Boolean = false,
)
