package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.database.ListeningHistoryEntity

data class DebugHistoryItem(
    val episodeId: String,
    val episodeTitle: String,
    val podcastName: String,
    val progressMs: Long,
    val durationMs: Long,
    val isDirty: Boolean,
    val isCompleted: Boolean,
)

internal fun ListeningHistoryEntity.toDebugHistoryItem(): DebugHistoryItem =
    DebugHistoryItem(
        episodeId = episodeId,
        episodeTitle = episodeTitle,
        podcastName = podcastName,
        progressMs = progressMs,
        durationMs = durationMs,
        isDirty = isDirty,
        isCompleted = isCompleted,
    )
