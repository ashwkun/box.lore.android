package cx.aswin.boxcast.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RichHistoryStats(
    val totalListeningMs: Long = 0L,
    val completedEpisodesCount: Int = 0,
    val topPodcastName: String? = null,
    val topPodcastImageUrl: String? = null
)

sealed interface HistoryUiState {
    object Loading : HistoryUiState
    object Empty : HistoryUiState
    data class Success(
        val stats: RichHistoryStats,
        val groupedHistory: Map<LocalDate, List<ListeningHistoryEntity>>,
        val expandedDates: Set<LocalDate>
    ) : HistoryUiState
}

class HistoryViewModel(
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val _expandedDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    private var hasInitializedExpansions = false

    val uiState: StateFlow<HistoryUiState> = combine(
        playbackRepository.getAllHistory(),
        _expandedDates
    ) { historyList, expandedDates ->
        if (historyList.isEmpty()) {
            HistoryUiState.Empty
        } else {
            // Calculate Rich Stats (separate from grouping to avoid side-effects)
            var totalMs = 0L
            var completedCount = 0
            val podcastFrequency = mutableMapOf<String, Int>()
            val podcastImages = mutableMapOf<String, String?>()

            historyList.forEach { entity ->
                // Use durationMs for completed episodes (prevents replay-reduces-time bug)
                // Use progressMs for in-progress episodes
                val isComplete = entity.isCompleted || (entity.durationMs > 0 && entity.progressMs > entity.durationMs * 0.9f)
                totalMs += if (isComplete && entity.durationMs > 0) entity.durationMs else entity.progressMs
                if (isComplete) completedCount++

                val count = podcastFrequency.getOrDefault(entity.podcastName, 0) + 1
                podcastFrequency[entity.podcastName] = count
                if (entity.podcastImageUrl != null) {
                    podcastImages[entity.podcastName] = entity.podcastImageUrl
                }
            }

            val groupedByDate = historyList.groupBy { entity ->
                Instant.ofEpochMilli(entity.lastPlayedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSortedMap(reverseOrder())

            // Auto-expand the first 2 dates only once
            if (!hasInitializedExpansions) {
                hasInitializedExpansions = true
                val initialExpand = groupedByDate.keys.take(2).toSet()
                _expandedDates.update { initialExpand }
                
                // Return immediately with the auto-expanded dates to prevent a stutter
                return@combine HistoryUiState.Success(
                    stats = calculateStats(totalMs, completedCount, podcastFrequency, podcastImages),
                    groupedHistory = groupedByDate,
                    expandedDates = initialExpand
                )
            }

            HistoryUiState.Success(
                stats = calculateStats(totalMs, completedCount, podcastFrequency, podcastImages),
                groupedHistory = groupedByDate,
                expandedDates = expandedDates
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState.Loading
    )

    private fun calculateStats(
        totalMs: Long,
        completedCount: Int,
        podcastFrequency: Map<String, Int>,
        podcastImages: Map<String, String?>
    ): RichHistoryStats {
        val topPodcast = podcastFrequency.maxByOrNull { it.value }?.key
        return RichHistoryStats(
            totalListeningMs = totalMs,
            completedEpisodesCount = completedCount,
            topPodcastName = topPodcast,
            topPodcastImageUrl = topPodcast?.let { podcastImages[it] }
        )
    }

    fun toggleDateExpansion(date: LocalDate) {
        _expandedDates.update { current ->
            if (current.contains(date)) current - date else current + date
        }
    }

    fun removeHistoryItem(episodeId: String) {
        viewModelScope.launch {
            playbackRepository.removeHistoryItem(episodeId)
        }
        itemsDeletedCount++
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            playbackRepository.clearHistory()
        }
        itemsDeletedCount++ // count as 1 major action
    }

    // ── Telemetry State & Lifecycle ──

    var sessionStartTime: Long = 0L
    private var hasTrackedExit = false

    var episodesClickedCount = 0
    var itemsDeletedCount = 0

    fun onScreenResume() {
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackScreenExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryHistorySession(
            timeSpentSeconds = timeSpent,
            episodesClickedCount = episodesClickedCount,
            itemsDeletedCount = itemsDeletedCount
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }
}
