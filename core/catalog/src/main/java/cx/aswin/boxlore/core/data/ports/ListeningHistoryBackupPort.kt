package cx.aswin.boxlore.core.data.ports

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.flow.Flow

/**
 * Narrow history seam for [cx.aswin.boxlore.core.data.backup.LibraryBackupManager].
 *
 * Stays in `:core:data` (not `:core:domain`) because it exposes Room
 * [ListeningHistoryEntity]. Production: [cx.aswin.boxlore.core.playback.PlaybackRepository]
 * in `:core:playback`. Keeps `:core:data` free of a dependency on `:core:playback`.
 */
interface ListeningHistoryBackupPort {
    fun getAllHistory(): Flow<List<ListeningHistoryEntity>>

    suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity)

    suspend fun markAllEpisodesCompleted(
        episodes: List<Episode>,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    )
}
