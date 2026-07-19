package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode

/**
 * Pure ListeningHistoryEntity builders for PlaybackRepository write paths.
 */
object ListeningHistoryUpsertLogic {
    data class ProgressSaveInput(
        val podcastId: String,
        val episodeId: String,
        val positionMs: Long,
        val durationMs: Long,
        val episodeTitle: String,
        val episodeImageUrl: String?,
        val podcastImageUrl: String?,
        val episodeAudioUrl: String,
        val podcastName: String,
        val isCompleted: Boolean,
        val isLiked: Boolean,
        val lastPlayedAt: Long,
        val enclosureType: String? = null,
        val episodeDescription: String? = null,
    )

    fun buildProgressSaveEntity(input: ProgressSaveInput): ListeningHistoryEntity =
        ListeningHistoryEntity(
            episodeId = input.episodeId,
            podcastId = input.podcastId,
            episodeTitle = input.episodeTitle,
            episodeImageUrl = input.episodeImageUrl,
            podcastImageUrl = input.podcastImageUrl,
            episodeAudioUrl = input.episodeAudioUrl,
            podcastName = input.podcastName,
            progressMs = input.positionMs,
            durationMs = input.durationMs,
            isCompleted = input.isCompleted,
            isLiked = input.isLiked,
            lastPlayedAt = input.lastPlayedAt,
            isDirty = true,
            enclosureType = input.enclosureType,
            isManualCompletion = false,
            isBulkCompletion = false,
            episodeDescription = input.episodeDescription,
        )

    fun buildBulkCompleteEntity(
        episode: Episode,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
        existing: ListeningHistoryEntity?,
        nowMs: Long,
    ): ListeningHistoryEntity {
        if (existing != null) {
            return existing.copy(
                isCompleted = true,
                progressMs = 0L,
                isBulkCompletion = true,
                lastPlayedAt = nowMs,
                isDirty = true,
                episodeDescription = existing.episodeDescription ?: episode.description,
            )
        }
        return ListeningHistoryEntity(
            episodeId = episode.id,
            podcastId = podcastId,
            episodeTitle = episode.title,
            episodeImageUrl = episode.imageUrl,
            podcastImageUrl = podcastImageUrl,
            episodeAudioUrl = episode.audioUrl,
            podcastName = podcastTitle,
            progressMs = 0L,
            durationMs = episode.duration * 1000L,
            isCompleted = true,
            isLiked = false,
            lastPlayedAt = nowMs,
            isDirty = true,
            enclosureType = episode.enclosureType,
            isManualCompletion = false,
            isBulkCompletion = true,
            episodeDescription = episode.description,
        )
    }

    fun buildBulkUncompleteEntity(
        existing: ListeningHistoryEntity,
        nowMs: Long,
    ): ListeningHistoryEntity? {
        if (!existing.isCompleted) return null
        return existing.copy(
            isCompleted = false,
            progressMs = 0L,
            isManualCompletion = false,
            isBulkCompletion = false,
            lastPlayedAt = nowMs,
            isDirty = true,
        )
    }
}
