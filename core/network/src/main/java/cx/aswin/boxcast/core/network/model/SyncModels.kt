package cx.aswin.boxcast.core.network.model

import kotlinx.serialization.Serializable
import cx.aswin.boxcast.core.model.Briefing
import cx.aswin.boxcast.core.model.Chapter

@Serializable
data class SyncRequest(
    val ids: List<String>
)

@Serializable
data class SyncResponse(
    val items: List<SyncItem> = emptyList(),
    val error: String? = null
)

@Serializable
data class SyncItem(
    val id: String,
    val latestEpisode: EpisodeItem? = null
)

@Serializable
data class FeedbackRequest(
    val category: String,
    val message: String,
    val appVersion: String,
    val email: String? = null
)

@Serializable
data class FeedbackResponse(
    val success: Boolean
)

@Serializable
data class RecommendationsRequest(
    val history: List<HistoryItem> = emptyList(),
    val interests: List<String> = emptyList(),
    val country: String? = null,
    val subscribedPodcastIds: List<String> = emptyList(),
    val subscribedGenres: List<String> = emptyList()
)

@Serializable
data class RecommendationSemanticFallback(
    val episodeTitle: String? = null,
    val podcastTitle: String? = null,
    val genre: String? = null,
    val description: String? = null,
)

@Serializable
data class RecommendationSeedV2(
    val kind: String,
    val id: Long,
    val weight: Double,
    val fallback: RecommendationSemanticFallback? = null,
)

@Serializable
data class RecommendationsV2Request(
    val contractVersion: Int = 2,
    val country: String,
    val languages: List<String> = emptyList(),
    val mode: String = "home",
    val limit: Int = 60,
    val seeds: List<RecommendationSeedV2> = emptyList(),
    val interests: List<String> = emptyList(),
    val subscribedPodcastIds: List<Long> = emptyList(),
    val excludedEpisodeIds: List<Long> = emptyList(),
    val excludedPodcastIds: List<Long> = emptyList(),
)

@Serializable
data class RecommendationsV2Response(
    val status: String = "false",
    val contractVersion: Int? = null,
    val algorithmVersion: String? = null,
    val items: List<EpisodeItem> = emptyList(),
    val isFallback: Boolean = false,
    val candidateCount: Int = 0,
)

@Serializable
data class HistoryItem(
    val podcastTitle: String,
    val episodeTitle: String,
    val podcastId: String? = null,
    val episodeId: String? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val progressMs: Long? = null,
    val isCompleted: Boolean? = null,
    val isLiked: Boolean? = null,
    val episodeDescription: String? = null
)

@Serializable
data class BootstrapRequest(
    val country: String,
    val vibeIds: List<String>,
    val deviceUuid: String? = null,
    val recommendationsRequest: RecommendationsRequest? = null,
    val contractVersion: Int? = null,
    val intentIds: List<String> = emptyList(),
)

@Serializable
data class BootstrapResponse(
    val briefing: Briefing? = null,
    val briefingChapters: List<Chapter> = emptyList(),
    val trending: List<TrendingFeed> = emptyList(),
    val curatedVibes: Map<String, List<TrendingFeed>> = emptyMap(),
    val recommendations: List<EpisodeItem> = emptyList(),
    val isRecommendationsFallback: Boolean? = null,
    val contractVersion: Int? = null,
    val catalogVersion: Int? = null,
    val intentCandidates: Map<String, List<TrendingFeed>> = emptyMap(),
    val recommendationAlgorithmVersion: String? = null,
)

@Serializable
data class ContentCatalogResponse(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val validForSeconds: Long,
    val dayparts: List<ContentDaypartDto> = emptyList(),
    val safeLayouts: List<String> = emptyList(),
    val intents: List<ContentIntentDto> = emptyList(),
    val fallbackIntent: ContentFallbackIntentDto? = null,
)

@Serializable
data class ContentDaypartDto(
    val id: String,
    val startMinute: Int,
    val endMinute: Int,
)

@Serializable
data class ContentIntentDto(
    val id: String,
    val titleKey: String,
    val titleFallback: String,
    val subtitleKey: String,
    val subtitleFallback: String,
    val icon: String,
    val surfaces: List<String>,
    val dayparts: List<String>,
    val providerQueryRef: String,
    val layout: String,
    val refreshPolicy: String? = null,
    val minCandidates: Int,
    val maxCandidates: Int,
    val freshnessDays: Int,
    val durationMinutes: ContentDurationRangeDto? = null,
    val diversity: ContentDiversityDto,
    val quality: ContentQualityDto = ContentQualityDto(),
)

@Serializable
data class ContentDurationRangeDto(
    val min: Int,
    val max: Int,
)

@Serializable
data class ContentDiversityDto(
    val maxPerShow: Int,
    val minDistinctShows: Int,
)

@Serializable
data class ContentQualityDto(
    val minSemanticScore: Double = 0.0,
    val unseenShowReserve: Double = 0.0,
)

@Serializable
data class ContentFallbackIntentDto(
    val id: String,
    val titleKey: String,
    val titleFallback: String,
    val subtitleKey: String,
    val subtitleFallback: String,
    val icon: String,
    val layout: String,
)

@Serializable
data class ContentSectionsV1Request(
    val contractVersion: Int = 1,
    val surface: String,
    val localMinuteOfDay: Int,
    val daypart: String? = null,
    val country: String,
    val languages: List<String> = emptyList(),
    val recentSeeds: List<ContentSectionRecentSeedDto> = emptyList(),
    val interests: List<String> = emptyList(),
    val subscribedPodcastIds: List<Long> = emptyList(),
    val excludedPodcastIds: List<Long> = emptyList(),
    val excludedEpisodeIds: List<Long> = emptyList(),
    val candidateBudget: Int = 120,
)

@Serializable
data class ContentSectionRecentSeedDto(
    val kind: String,
    val id: Long,
    val weight: Double,
    val fallback: ContentSectionSeedFallbackDto? = null,
)

@Serializable
data class ContentSectionSeedFallbackDto(
    val episodeTitle: String? = null,
    val podcastTitle: String? = null,
    val podcastId: Long? = null,
    val genre: String? = null,
    val description: String? = null,
)

@Serializable
data class ContentSectionsV1Response(
    val status: String = "false",
    val contractVersion: Int? = null,
    val catalogVersion: Int? = null,
    val resolvedDaypart: String? = null,
    val algorithmVersion: String? = null,
    val isFallback: Boolean = false,
    val generatedAt: String? = null,
    val sections: List<ContentDiscoverySectionDto> = emptyList(),
)

@Serializable
data class ContentDiscoverySectionDto(
    val intent: ContentSectionIntentMetadataDto,
    val layout: String,
    val items: List<ContentSectionEpisodeDto> = emptyList(),
)

@Serializable
data class ContentSectionIntentMetadataDto(
    val id: String,
    val titleKey: String,
    val titleFallback: String,
    val subtitleKey: String,
    val subtitleFallback: String,
    val icon: String,
    val dayparts: List<String> = emptyList(),
    val refreshPolicy: String,
)

@Serializable
data class ContentSectionEpisodeDto(
    val id: Long,
    val title: String,
    val description: String = "",
    val enclosureUrl: String,
    /** Duration in seconds. */
    val duration: Int,
    /** Unix timestamp in seconds. */
    val datePublished: Long,
    val image: String = "",
    val feedImage: String = "",
    val feedId: Long,
    val feedTitle: String,
    val genre: String = "",
    val language: String = "en",
    val retrievalScore: Double,
    val semanticScore: Double,
    val source: String,
    val reason: String,
    val serverRank: Int,
    val algorithmVersion: String,
)

@Serializable
data class BecauseYouLikeRequest(
    val podcastTitle: String,
    val podcastDescription: String,
    val excludePodcastId: String? = null,
    val country: String? = null
)

@Serializable
data class BecauseYouLikeResponse(
    val status: String,
    val podcasts: List<TrendingFeed> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList()
)

