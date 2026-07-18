package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity

data class HomeListeningHistoryItem(
    val episodeId: String,
    val podcastId: String,
    val podcastName: String,
    val podcastImageUrl: String?,
    val progressMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val isLiked: Boolean,
    val lastPlayedAt: Long,
)

internal fun ListeningHistoryEntity.toHomeListeningHistoryItem(): HomeListeningHistoryItem =
    HomeListeningHistoryItem(
        episodeId = episodeId,
        podcastId = podcastId,
        podcastName = podcastName,
        podcastImageUrl = podcastImageUrl,
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        isLiked = isLiked,
        lastPlayedAt = lastPlayedAt,
    )
