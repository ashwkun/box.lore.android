package cx.aswin.boxcast.core.network.model

import kotlinx.serialization.Serializable

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
    val genre: String? = null,
    val durationMs: Long? = null,
    val progressMs: Long? = null,
    val isCompleted: Boolean? = null,
    val isLiked: Boolean? = null
)
