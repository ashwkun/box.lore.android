package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.database.ListeningHistoryEntity

/**
 * UI-facing listening progress for Podcast Info. Room [ListeningHistoryEntity] stays at the mapper.
 */
data class InfoListeningProgressItem(
    val episodeId: String,
    val progressMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
)

internal fun ListeningHistoryEntity.toInfoListeningProgressItem(): InfoListeningProgressItem =
    InfoListeningProgressItem(
        episodeId = episodeId,
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
    )
