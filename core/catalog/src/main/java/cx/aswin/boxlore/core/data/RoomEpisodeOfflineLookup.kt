package cx.aswin.boxlore.core.data

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.domain.ports.EpisodeOfflineLookupPort
import cx.aswin.boxlore.core.domain.ports.OfflineEpisodeSnapshot

/** Room-backed [EpisodeOfflineLookupPort] for Episode Info deep-link hydration. */
class RoomEpisodeOfflineLookup(
    private val database: BoxLoreDatabase,
) : EpisodeOfflineLookupPort {
    override suspend fun fromDownload(episodeId: String): OfflineEpisodeSnapshot? {
        val row = database.downloadedEpisodeDao().getDownload(episodeId) ?: return null
        return OfflineEpisodeSnapshot(
            podcastId = row.podcastId,
            podcastName = row.podcastName,
            episodeTitle = row.episodeTitle,
            episodeImageUrl = row.episodeImageUrl,
            episodeDescription = row.episodeDescription,
            audioUrl = row.localFilePath,
            durationMs = row.durationMs,
        )
    }

    override suspend fun fromHistory(episodeId: String): OfflineEpisodeSnapshot? {
        val row = database.listeningHistoryDao().getHistoryItem(episodeId) ?: return null
        return OfflineEpisodeSnapshot(
            podcastId = row.podcastId,
            podcastName = row.podcastName,
            episodeTitle = row.episodeTitle,
            episodeImageUrl = row.episodeImageUrl,
            episodeDescription = row.episodeDescription,
            audioUrl = row.episodeAudioUrl.orEmpty(),
            durationMs = row.durationMs,
        )
    }
}
