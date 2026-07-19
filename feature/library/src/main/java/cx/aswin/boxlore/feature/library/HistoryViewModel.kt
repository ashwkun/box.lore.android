package cx.aswin.boxlore.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.domain.ports.ListeningHistoryPort
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryFilter {
    ALL,
    IN_PROGRESS,
    COMPLETED,
}

sealed interface HistoryUiEvent {
    data class ShowUndoDelete(
        val removal: ListeningHistoryRemoval,
    ) : HistoryUiEvent

    data object HistoryCleared : HistoryUiEvent
}

data class HistorySuccessState(
    val insights: ListeningInsightSummary,
    val groupedHistory: Map<LocalDate, List<ListeningHistoryItem>>,
    val expandedDates: Set<LocalDate>,
    val selectedFilterDate: LocalDate? = null,
    val selectedHistoryFilter: HistoryFilter = HistoryFilter.ALL,
    val selectedPeriod: ListeningPeriod = ListeningPeriod.DAYS_30,
    val activeDays: Set<LocalDate> = emptySet(),
    val timelineEmpty: Boolean = false,
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data object Empty : HistoryUiState

    data class Success(
        val state: HistorySuccessState,
    ) : HistoryUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val listeningHistoryPort: ListeningHistoryPort,
    private val userPreferencesRepository: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
) : ViewModel() {
    private val _expandedDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    private val _selectedFilterDate = MutableStateFlow<LocalDate?>(null)
    private val _selectedHistoryFilter = MutableStateFlow(HistoryFilter.ALL)
    private val _selectedPeriod = MutableStateFlow(ListeningPeriod.DAYS_30)
    private val _showTrackingNotice = MutableStateFlow(false)
    private var hasInitializedExpansions = false

    val showTrackingNotice: StateFlow<Boolean> = _showTrackingNotice

    private val eventsChannel = Channel<HistoryUiEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val seen = userPreferencesRepository.hasSeenListeningHistoryTrackingNotice.first()
            if (seen) return@launch
            val hasEpisodeHistory = listeningHistoryPort.observeHistoryTimeline().first().isNotEmpty()
            if (hasEpisodeHistory) {
                _showTrackingNotice.value = true
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHistoryTrackingNotice("shown")
            } else {
                // New installs never had the old estimate-based insights.
                userPreferencesRepository.markListeningHistoryTrackingNoticeSeen()
            }
        }
    }

    private val insightsFlow =
        _selectedPeriod.flatMapLatest { period ->
            listeningHistoryPort.observeInsights(period)
        }

    private data class SelectionState(
        val expandedDates: Set<LocalDate>,
        val selectedFilterDate: LocalDate?,
        val selectedHistoryFilter: HistoryFilter,
        val selectedPeriod: ListeningPeriod,
    )

    private val selectionFlow =
        combine(
            _expandedDates,
            _selectedFilterDate,
            _selectedHistoryFilter,
            _selectedPeriod,
        ) { expanded, date, filter, period ->
            SelectionState(expanded, date, filter, period)
        }

    val uiState: StateFlow<HistoryUiState> =
        combine(
            listeningHistoryPort.observeHistoryTimeline(),
            insightsFlow,
            selectionFlow,
        ) { timeline, insights, selection ->
            if (timeline.isEmpty()) {
                HistoryUiState.Empty
            } else {
                val filtered =
                    when (selection.selectedHistoryFilter) {
                        HistoryFilter.ALL -> timeline
                        HistoryFilter.IN_PROGRESS -> timeline.filter { !it.isCompleted && it.progressMs > 0 }
                        HistoryFilter.COMPLETED -> timeline.filter { it.isCompleted }
                    }
                val groupedByDate =
                    filtered
                        .groupBy { item ->
                            Instant.ofEpochMilli(item.lastPlayedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        }.toSortedMap(reverseOrder())
                val activeDays =
                    timeline
                        .map {
                            Instant.ofEpochMilli(it.lastPlayedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        }.toSet()
                val currentGrouped =
                    if (selection.selectedFilterDate != null) {
                        groupedByDate.filterKeys { it == selection.selectedFilterDate }
                    } else {
                        groupedByDate
                    }
                val timelineEmpty = currentGrouped.isEmpty()

                if (!hasInitializedExpansions) {
                    hasInitializedExpansions = true
                    val initialExpand = groupedByDate.keys.take(2).toSet()
                    _expandedDates.update { initialExpand }
                    HistoryUiState.Success(
                        HistorySuccessState(
                            insights = insights,
                            groupedHistory = currentGrouped,
                            expandedDates = initialExpand,
                            selectedFilterDate = selection.selectedFilterDate,
                            selectedHistoryFilter = selection.selectedHistoryFilter,
                            selectedPeriod = selection.selectedPeriod,
                            activeDays = activeDays,
                            timelineEmpty = timelineEmpty,
                        ),
                    )
                } else {
                    val finalExpanded =
                        if (selection.selectedFilterDate != null) {
                            selection.expandedDates + selection.selectedFilterDate
                        } else {
                            selection.expandedDates
                        }
                    HistoryUiState.Success(
                        HistorySuccessState(
                            insights = insights,
                            groupedHistory = currentGrouped,
                            expandedDates = finalExpanded,
                            selectedFilterDate = selection.selectedFilterDate,
                            selectedHistoryFilter = selection.selectedHistoryFilter,
                            selectedPeriod = selection.selectedPeriod,
                            activeDays = activeDays,
                            timelineEmpty = timelineEmpty,
                        ),
                    )
                }
            }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HistoryUiState.Loading,
            )

    init {
        viewModelScope.launch {
            listeningHistoryPort.maintainListeningAnalytics()
        }
    }

    fun setPeriod(period: ListeningPeriod) {
        _selectedPeriod.value = period
    }

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
            val removal = listeningHistoryPort.removeHistoryItem(episodeId) ?: return@launch
            itemsDeletedCount++
            eventsChannel.send(HistoryUiEvent.ShowUndoDelete(removal))
        }
    }

    fun undoRemoval(removal: ListeningHistoryRemoval) {
        viewModelScope.launch {
            listeningHistoryPort.restoreHistoryRemoval(removal)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            listeningHistoryPort.clearHistory()
            itemsDeletedCount++
            eventsChannel.send(HistoryUiEvent.HistoryCleared)
        }
    }

    fun dismissTrackingNotice() {
        if (!_showTrackingNotice.value) return
        _showTrackingNotice.value = false
        viewModelScope.launch {
            userPreferencesRepository.markListeningHistoryTrackingNoticeSeen()
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHistoryTrackingNotice("dismissed")
        }
    }

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
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHistorySession(
            timeSpentSeconds = timeSpent,
            episodesClickedCount = episodesClickedCount,
            itemsDeletedCount = itemsDeletedCount,
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }
}
