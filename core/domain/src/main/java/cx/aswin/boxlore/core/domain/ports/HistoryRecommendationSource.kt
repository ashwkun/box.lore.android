package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.network.model.HistoryItem

/**
 * History shaping for personalized recommendations / smart downloads.
 *
 * Workers and [cx.aswin.boxlore.core.downloads.SmartDownloadManager] depend on this
 * instead of constructing a full PlaybackRepository (`:core:playback`).
 *
 * Production: [cx.aswin.boxlore.core.data.DefaultSmartQueueSources] (or any
 * implementation that mirrors PlaybackRepository filtering rules).
 */
fun interface HistoryRecommendationSource {
    suspend fun getHistoryForRecommendations(limit: Int): List<HistoryItem>
}
