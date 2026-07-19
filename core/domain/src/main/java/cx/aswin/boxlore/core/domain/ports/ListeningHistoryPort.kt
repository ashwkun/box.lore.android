package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import kotlinx.coroutines.flow.Flow

/**
 * Narrow history + insights seam for Library History UI.
 * Production: [cx.aswin.boxlore.core.playback.PlaybackRepository].
 */
interface ListeningHistoryPort {
    fun observeHistoryTimeline(): Flow<List<ListeningHistoryItem>>

    fun observeInsights(period: ListeningPeriod): Flow<ListeningInsightSummary>

    suspend fun removeHistoryItem(episodeId: String): ListeningHistoryRemoval?

    suspend fun restoreHistoryRemoval(removal: ListeningHistoryRemoval)

    suspend fun clearHistory()

    suspend fun maintainListeningAnalytics()
}
