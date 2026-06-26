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
    val appVersion: String
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
    val recommendationsRequest: RecommendationsRequest? = null
)

@Serializable
data class BootstrapResponse(
    val briefing: Briefing? = null,
    val briefingChapters: List<Chapter> = emptyList(),
    val trending: List<TrendingFeed> = emptyList(),
    val curatedVibes: Map<String, List<TrendingFeed>> = emptyMap(),
    val recommendations: List<EpisodeItem> = emptyList()
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

