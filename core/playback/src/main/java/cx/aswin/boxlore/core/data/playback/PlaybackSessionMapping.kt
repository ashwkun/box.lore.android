package cx.aswin.boxlore.core.data.playback

import cx.aswin.boxlore.core.data.PlaybackSession
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity

/**
 * Pure mapping from listening-history rows to [PlaybackSession].
 * Extracted from [cx.aswin.boxlore.core.data.PlaybackRepository] history flows.
 */
object PlaybackSessionMapping {
    fun fromHistoryEntity(entity: ListeningHistoryEntity): PlaybackSession =
        PlaybackSession(
            podcastId = entity.podcastId,
            episodeId = entity.episodeId,
            positionMs = entity.progressMs,
            durationMs = entity.durationMs,
            timestamp = entity.lastPlayedAt,
            episodeTitle = entity.episodeTitle,
            podcastTitle = entity.podcastName,
            imageUrl = entity.episodeImageUrl,
            podcastImageUrl = entity.podcastImageUrl,
            audioUrl = entity.episodeAudioUrl,
            enclosureType = entity.enclosureType,
        )
}
