package cx.aswin.boxlore.core.domain.ports

/**
 * Offline episode metadata for deep links / Episode Info when network args are incomplete.
 *
 * Production: [cx.aswin.boxlore.core.data.RoomEpisodeOfflineLookup].
 */
data class OfflineEpisodeSnapshot(
    val podcastId: String,
    val podcastName: String,
    val episodeTitle: String,
    val episodeImageUrl: String?,
    val episodeDescription: String?,
    /** Local file path (download) or remote/history audio URL. */
    val audioUrl: String,
    val durationMs: Long,
)

interface EpisodeOfflineLookupPort {
    suspend fun fromDownload(episodeId: String): OfflineEpisodeSnapshot?

    suspend fun fromHistory(episodeId: String): OfflineEpisodeSnapshot?
}
