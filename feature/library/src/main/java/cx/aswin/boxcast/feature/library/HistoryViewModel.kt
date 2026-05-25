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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DetailedHistoryStats(
    val totalListeningMs: Long = 0L,
    val completedEpisodesCount: Int = 0,
    val inProgressEpisodesCount: Int = 0,
    val likedEpisodesCount: Int = 0,
    val topPodcastName: String? = null,
    val topPodcastImageUrl: String? = null,
    val topPodcastPlayCount: Int = 0,
    val listeningStreakDays: Int = 0,
    val peakListeningHour: Int = -1,
    val peakListeningVibe: String = "Unknown",
    val hourlyDistribution: FloatArray = FloatArray(24),
    val activeDays: Set<LocalDate> = emptySet()
)

enum class HistoryFilter {
    ALL,
    IN_PROGRESS,
    COMPLETED
}

sealed interface HistoryUiState {
    object Loading : HistoryUiState
    object Empty : HistoryUiState
    data class Success(
        val stats: DetailedHistoryStats,
        val groupedHistory: Map<LocalDate, List<ListeningHistoryEntity>>,
        val expandedDates: Set<LocalDate>,
        val selectedFilterDate: LocalDate? = null,
        val selectedHistoryFilter: HistoryFilter = HistoryFilter.ALL
    ) : HistoryUiState
}

class HistoryViewModel(
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    private val _expandedDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    private val _selectedFilterDate = MutableStateFlow<LocalDate?>(null)
    private val _selectedHistoryFilter = MutableStateFlow(HistoryFilter.ALL)
    private var hasInitializedExpansions = false

    val uiState: StateFlow<HistoryUiState> = combine(
        playbackRepository.getAllHistory(),
        _expandedDates,
        _selectedFilterDate,
        _selectedHistoryFilter
    ) { historyList: List<ListeningHistoryEntity>, expandedDates: Set<LocalDate>, selectedFilterDate: LocalDate?, selectedHistoryFilter: HistoryFilter ->
        if (historyList.isEmpty()) {
            HistoryUiState.Empty
        } else {
            var totalMs = 0L
            var completedCount = 0
            var inProgressCount = 0
            var likedCount = 0
            val podcastFrequency = mutableMapOf<String, Int>()
            val podcastImages = mutableMapOf<String, String?>()
            val hourlyCount = IntArray(24)

            historyList.forEach { entity ->
                val isComplete = entity.isCompleted || (entity.durationMs > 0 && entity.progressMs > entity.durationMs * 0.9f)
                totalMs += if (isComplete && entity.durationMs > 0) entity.durationMs else entity.progressMs
                if (isComplete) {
                    completedCount++
                } else if (entity.progressMs > 0) {
                    inProgressCount++
                }
                if (entity.isLiked) {
                    likedCount++
                }

                val count = podcastFrequency.getOrDefault(entity.podcastName, 0) + 1
                podcastFrequency[entity.podcastName] = count
                if (entity.podcastImageUrl != null) {
                    podcastImages[entity.podcastName] = entity.podcastImageUrl
                } else if (entity.podcastImageUrl == null && entity.episodeImageUrl != null) {
                    podcastImages[entity.podcastName] = entity.episodeImageUrl
                }

                val hour = Instant.ofEpochMilli(entity.lastPlayedAt).atZone(ZoneId.systemDefault()).hour
                hourlyCount[hour]++
            }

            val filteredHistoryList = when (selectedHistoryFilter) {
                HistoryFilter.ALL -> historyList
                HistoryFilter.IN_PROGRESS -> historyList.filter { !it.isCompleted }
                HistoryFilter.COMPLETED -> historyList.filter { it.isCompleted }
            }

            val groupedByDate = filteredHistoryList.groupBy { entity ->
                Instant.ofEpochMilli(entity.lastPlayedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSortedMap(reverseOrder())

            val activeDays = groupedByDate.keys.toSet()

            // Calculate streak
            val sortedActiveDates = activeDays.sortedDescending()
            var streak = 0
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            if (sortedActiveDates.isNotEmpty()) {
                val currentCheck = when {
                    sortedActiveDates.contains(today) -> today
                    sortedActiveDates.contains(yesterday) -> yesterday
                    else -> null
                }
                if (currentCheck != null) {
                    streak = 1
                    var checkDate = currentCheck.minusDays(1)
                    while (sortedActiveDates.contains(checkDate)) {
                        streak++
                        checkDate = checkDate.minusDays(1)
                    }
                }
            }

            val peakHour = hourlyCount.indices.maxByOrNull { hourlyCount[it] } ?: -1
            val totalPlays = historyList.size.toFloat()
            val hourlyDistribution = FloatArray(24) { i ->
                if (totalPlays > 0) hourlyCount[i] / totalPlays else 0f
            }

            val peakVibe = when (peakHour) {
                in 5..10 -> "Morning Ritual"
                in 11..16 -> "Midday Flow"
                in 17..21 -> "Evening Unwind"
                in 22..24, in 0..4 -> "Night Owl"
                else -> "Balanced Listener"
            }

            val topPodcast = podcastFrequency.maxByOrNull { it.value }?.key
            val topPodcastPlayCount = topPodcast?.let { podcastFrequency[it] } ?: 0
            val stats = DetailedHistoryStats(
                totalListeningMs = totalMs,
                completedEpisodesCount = completedCount,
                inProgressEpisodesCount = inProgressCount,
                likedEpisodesCount = likedCount,
                topPodcastName = topPodcast,
                topPodcastImageUrl = topPodcast?.let { podcastImages[it] },
                topPodcastPlayCount = topPodcastPlayCount,
                listeningStreakDays = streak,
                peakListeningHour = peakHour,
                peakListeningVibe = peakVibe,
                hourlyDistribution = hourlyDistribution,
                activeDays = activeDays
            )

            val currentGrouped = if (selectedFilterDate != null) {
                groupedByDate.filterKeys { it == selectedFilterDate }
            } else {
                groupedByDate
            }

            if (!hasInitializedExpansions) {
                hasInitializedExpansions = true
                val initialExpand = groupedByDate.keys.take(2).toSet()
                _expandedDates.update { initialExpand }

                return@combine HistoryUiState.Success(
                    stats = stats,
                    groupedHistory = currentGrouped,
                    expandedDates = initialExpand,
                    selectedFilterDate = selectedFilterDate,
                    selectedHistoryFilter = selectedHistoryFilter
                )
            }

            val finalExpandedDates = if (selectedFilterDate != null) {
                expandedDates + selectedFilterDate
            } else {
                expandedDates
            }

            HistoryUiState.Success(
                stats = stats,
                groupedHistory = currentGrouped,
                expandedDates = finalExpandedDates,
                selectedFilterDate = selectedFilterDate,
                selectedHistoryFilter = selectedHistoryFilter
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState.Loading
    )

    fun setFilterDate(date: LocalDate?) {
        _selectedFilterDate.value = date
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _selectedHistoryFilter.value = filter
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
