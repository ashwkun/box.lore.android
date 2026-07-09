package cx.aswin.boxcast.feature.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.network.model.CuratedCuriosityResponseDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import cx.aswin.boxcast.core.network.model.DailyCuriosityDto

sealed interface LearnUiState {
    data object Loading : LearnUiState
    
    data class Success(
        val data: CuratedCuriosityResponseDto,
        val questionsStack: List<DailyCuriosityDto>,
        val isRefreshing: Boolean = false
    ) : LearnUiState
    
    data class Error(val message: String) : LearnUiState
}

class LearnViewModel(
    private val podcastRepository: PodcastRepository,
    application: Application,
    private val historyStore: LearnCuriosityHistoryStore = LearnCuriosityHistoryStore(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    // Telemetry tracking fields
    private var sessionStartTime = System.currentTimeMillis()
    private var hasTrackedExit = false
    private var cardsDismissedCount = 0
    private var cardsQueuedCount = 0
    private var playsCount = 0
    private var podcastsClickedCount = 0
    private var infosClickedCount = 0

    fun onScreenResume() {
        applyPendingRestores()
        if (hasTrackedExit) {
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
            cardsDismissedCount = 0
            cardsQueuedCount = 0
            playsCount = 0
            podcastsClickedCount = 0
            infosClickedCount = 0
        }
    }

    private fun applyPendingRestores() {
        val restored = historyStore.consumePendingRestores()
        if (restored.isEmpty()) return

        val currentState = _uiState.value
        if (currentState !is LearnUiState.Success) return

        val restoredCards = restored.map { it.toDailyCuriosityDto() }
        val restoredIds = restoredCards.map { it.episode.id }.toSet()
        val merged = restoredCards + currentState.questionsStack.filterNot { it.episode.id in restoredIds }
        _uiState.value = currentState.copy(questionsStack = merged)
    }

    fun trackScreenExit() {
        if (hasTrackedExit) return
        hasTrackedExit = true
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLearnScreenSession(
            timeSpentSeconds = timeSpent,
            cardsDismissedCount = cardsDismissedCount,
            cardsQueuedCount = cardsQueuedCount,
            playsCount = playsCount,
            podcastsClickedCount = podcastsClickedCount,
            infosClickedCount = infosClickedCount
        )
    }

    fun trackCardDismissed() {
        cardsDismissedCount++
    }

    fun trackCardQueued() {
        cardsQueuedCount++
    }

    fun trackPlayClicked() {
        playsCount++
    }

    fun trackPodcastClicked() {
        podcastsClickedCount++
    }

    fun trackInfoClicked() {
        infosClickedCount++
    }

    private var currentPage = 1
    private var isLoadingMore = false
    private var isEndOfContent = false
    private var fetchJob: Job? = null

    init {
        loadData()
    }

    private fun getDismissedIds(): Set<String> = historyStore.getDismissedIds()

    fun dismissCuriosity(daily: DailyCuriosityDto, action: LearnHistoryAction) {
        val episodeId = daily.episode.id.toString()
        historyStore.recordDismissal(daily, action)

        val currentState = _uiState.value
        if (currentState is LearnUiState.Success) {
            val updatedStack = currentState.questionsStack.filterNot { it.episode.id.toString() == episodeId }
            _uiState.value = currentState.copy(questionsStack = updatedStack)

            if (updatedStack.size < 3) {
                fetchNextPage()
            }
        }
    }

    private fun filterAndShuffleNewItems(
        rawItems: List<DailyCuriosityDto>,
        currentStack: List<DailyCuriosityDto>
    ): List<DailyCuriosityDto> {
        val dismissed = getDismissedIds()
        val newItems = rawItems.filterNot { it.episode.id.toString() in dismissed }
        if (newItems.isEmpty()) return emptyList()

        val shuffledNew = weightedShuffle(newItems)
        val existingIds = currentStack.map { it.episode.id }.toSet()
        return shuffledNew.filterNot { it.episode.id in existingIds }
    }

    private suspend fun fetchPageAndFilter(
        page: Int,
        currentStack: List<DailyCuriosityDto>
    ): List<DailyCuriosityDto> {
        val res = podcastRepository.getCuratedCuriosity(page = page, bypassCache = false) ?: return emptyList()
        if (res.questionsStack.isEmpty()) {
            isEndOfContent = true
            return emptyList()
        }
        currentPage = page
        return filterAndShuffleNewItems(res.questionsStack, currentStack)
    }

    private fun appendCuriositiesToSuccessState(newItems: List<DailyCuriosityDto>) {
        _uiState.update { state ->
            if (state is LearnUiState.Success) {
                state.copy(questionsStack = state.questionsStack + newItems)
            } else {
                state
            }
        }
    }

    private fun fetchNextPage() {
        if (isLoadingMore || isEndOfContent || fetchJob?.isActive == true) return
        if (_uiState.value !is LearnUiState.Success) return

        isLoadingMore = true
        fetchJob = viewModelScope.launch {
            try {
                var pageToFetch = currentPage + 1
                var accumulatedNew = emptyList<DailyCuriosityDto>()
                var pageAttempts = 0
                val maxAttempts = 5

                while (accumulatedNew.isEmpty() && !isEndOfContent && pageAttempts < maxAttempts) {
                    pageAttempts++
                    val currentStack = (_uiState.value as? LearnUiState.Success)?.questionsStack ?: emptyList()
                    accumulatedNew = fetchPageAndFilter(pageToFetch, currentStack)
                    if (!isEndOfContent) {
                        pageToFetch++
                    }
                }

                if (accumulatedNew.isNotEmpty()) {
                    appendCuriositiesToSuccessState(accumulatedNew)
                }
            } catch (e: Exception) {
                android.util.Log.e("LearnViewModel", "Failed to load page ${currentPage + 1}", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    private fun weightedShuffle(list: List<DailyCuriosityDto>): List<DailyCuriosityDto> {
        if (list.size <= 1) return list
        val random = java.security.SecureRandom()
        return list.map { item ->
            val u = random.nextDouble()
            val w = (item.curiosityScore ?: 0).toDouble() + 1.0
            val key = Math.pow(u, 1.0 / w)
            Pair(item, key)
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun loadData() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = LearnUiState.Loading
            currentPage = 1
            isEndOfContent = false
            isLoadingMore = false
            try {
                val res = podcastRepository.getCuratedCuriosity(page = 1, bypassCache = false)
                if (res != null) {
                    val dismissed = getDismissedIds()
                    val remaining = res.questionsStack.filterNot { it.episode.id.toString() in dismissed }
                    val shuffled = weightedShuffle(remaining)
                    _uiState.value = LearnUiState.Success(data = res, questionsStack = shuffled)
                } else {
                    _uiState.value = LearnUiState.Error("Failed to load curiosity curation")
                }
            } catch (e: Exception) {
                _uiState.value = LearnUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun refresh() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val current = _uiState.value
            if (current is LearnUiState.Success) {
                _uiState.value = current.copy(isRefreshing = true)
            }
            try {
                currentPage = 1
                isEndOfContent = false
                isLoadingMore = false
                val res = podcastRepository.getCuratedCuriosity(page = 1, bypassCache = true)
                if (res != null) {
                    val dismissed = getDismissedIds()
                    val remaining = res.questionsStack.filterNot { it.episode.id.toString() in dismissed }
                    val shuffled = weightedShuffle(remaining)
                    _uiState.value = LearnUiState.Success(data = res, questionsStack = shuffled, isRefreshing = false)
                } else {
                    if (current is LearnUiState.Success) {
                        _uiState.value = current.copy(isRefreshing = false)
                    } else {
                        _uiState.value = LearnUiState.Error("Failed to refresh curiosity curation")
                    }
                }
            } catch (e: Exception) {
                if (current is LearnUiState.Success) {
                    _uiState.value = current.copy(isRefreshing = false)
                } else {
                    _uiState.value = LearnUiState.Error(e.message ?: "Unknown error occurred")
                }
            }
        }
    }
}
