package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.ranking.CandidateFeatureBuilder
import cx.aswin.boxlore.core.data.ranking.CandidateSignals
import cx.aswin.boxlore.core.data.ranking.CandidateSource
import cx.aswin.boxlore.core.data.ranking.FeedbackTarget
import cx.aswin.boxlore.core.data.ranking.RankingAction
import cx.aswin.boxlore.core.data.ranking.RankingExposure
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.data.ranking.RankingObjective
import cx.aswin.boxlore.core.data.ranking.RankingSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import cx.aswin.boxlore.core.network.model.DailyCuriosityDto

sealed interface LearnUiState {
    data object Loading : LearnUiState
    
    data class Success(
        val questionsStack: List<DailyCuriosityDto>,
        val isRefreshing: Boolean = false
    ) : LearnUiState

    data class CaughtUp(val isRefreshing: Boolean = false) : LearnUiState
    
    data class Error(val message: String) : LearnUiState
}

class LearnViewModel(
    private val podcastRepository: PodcastRepository,
    application: Application,
    private val rankingFeedback: RankingFeedbackRepository,
    private val historyStore: LearnCuriosityHistoryStore = LearnCuriosityHistoryStore(application),
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
    private val visibleSince = mutableMapOf<Long, Long>()

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

        val restoredCards = restored.map { it.toDailyCuriosityDto() }
        _uiState.update { currentState ->
            when (currentState) {
                is LearnUiState.Success -> {
                    val restoredIds = restoredCards.map { it.episode.id }.toSet()
                    val merged = restoredCards + currentState.questionsStack.filterNot {
                        it.episode.id in restoredIds
                    }
                    currentState.copy(questionsStack = merged)
                }
                is LearnUiState.CaughtUp -> LearnUiState.Success(restoredCards)
                else -> currentState
            }
        }
    }

    fun trackScreenExit() {
        if (hasTrackedExit) return
        hasTrackedExit = true
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackLearnScreenSession(
            timeSpentSeconds = timeSpent,
            cardsDismissedCount = cardsDismissedCount,
            cardsQueuedCount = cardsQueuedCount,
            playsCount = playsCount,
            podcastsClickedCount = podcastsClickedCount,
            infosClickedCount = infosClickedCount
        )
    }

    fun trackCardVisible(daily: DailyCuriosityDto) {
        if (visibleSince.putIfAbsent(daily.episode.id, System.currentTimeMillis()) != null) return
        viewModelScope.launch {
            rankingFeedback.recordExposure(
                RankingExposure(
                    episodeId = daily.episode.id.toString(),
                    podcastId = daily.episode.feedId?.toString().orEmpty(),
                    objective = RankingObjective.DISCOVERY,
                    surface = RankingSurface.EXPLORE,
                    source = CandidateSource.CURATED_INTENT,
                    features = CandidateFeatureBuilder.build(
                        CandidateSignals(
                            isUnseenShow = true,
                            serverRelevance = (daily.curiosityScore ?: 0) / 10.0,
                            isUnplayed = true,
                        ),
                    ),
                    entryPoint = "lore",
                    online = true,
                ),
            )
        }
    }

    fun trackCardDismissed(daily: DailyCuriosityDto) {
        cardsDismissedCount++
        val dwellMillis = System.currentTimeMillis() - (visibleSince.remove(daily.episode.id) ?: return)
        if (dwellMillis < MEANINGFUL_LORE_DWELL_MILLIS) return
        recordLoreAction(daily, RankingAction.DISMISS)
    }

    fun trackCardQueued(daily: DailyCuriosityDto) {
        cardsQueuedCount++
        visibleSince.remove(daily.episode.id)
        recordLoreAction(daily, RankingAction.EXPLICIT_QUEUE)
    }

    fun trackPlayClicked(daily: DailyCuriosityDto) {
        playsCount++
        recordLoreAction(daily, RankingAction.OPEN_DETAILS)
    }

    fun trackPodcastClicked(daily: DailyCuriosityDto) {
        podcastsClickedCount++
        recordLoreAction(daily, RankingAction.OPEN_DETAILS)
    }

    fun trackInfoClicked(daily: DailyCuriosityDto) {
        infosClickedCount++
        recordLoreAction(daily, RankingAction.OPEN_DETAILS)
    }

    private fun recordLoreAction(
        daily: DailyCuriosityDto,
        action: RankingAction,
    ) {
        viewModelScope.launch {
            rankingFeedback.recordAction(
                target = FeedbackTarget(
                    episodeId = daily.episode.id.toString(),
                    podcastId = daily.episode.feedId?.toString().orEmpty(),
                    source = CandidateSource.CURATED_INTENT,
                ),
                action = action,
            )
        }
    }

    private var currentPage = 1
    private var isLoadingMore = false
    private var isEndOfContent = false
    private var fetchJob: Job? = null

    init {
        loadData()
    }

    private fun getDismissedIds(): Set<String> = historyStore.getDismissedIds()

    private suspend fun loadFirstAvailableDeck(
        bypassCache: Boolean
    ): InitialCuriosityDeckResult = findFirstUnseenCuriosityDeck(
        dismissedIds = getDismissedIds(),
        fetchPage = { page ->
            podcastRepository.getCuratedCuriosity(
                page = page,
                bypassCache = bypassCache
            )
        }
    )

    private fun showDeck(result: InitialCuriosityDeckResult.Found) {
        currentPage = result.page
        val shuffled = weightedShuffle(result.unseenItems)
        _uiState.value = LearnUiState.Success(
            questionsStack = shuffled
        )
    }

    fun dismissCuriosity(daily: DailyCuriosityDto, action: LearnHistoryAction) {
        val episodeId = daily.episode.id.toString()
        historyStore.recordDismissal(daily, action)

        val currentState = _uiState.value
        if (currentState is LearnUiState.Success) {
            val updatedStack = currentState.questionsStack.filterNot { it.episode.id.toString() == episodeId }
            if (updatedStack.isEmpty() && isEndOfContent) {
                _uiState.value = LearnUiState.CaughtUp()
                return
            }

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
                when (val result = loadFirstAvailableDeck(bypassCache = false)) {
                    is InitialCuriosityDeckResult.Found -> showDeck(result)
                    is InitialCuriosityDeckResult.Exhausted -> {
                        currentPage = result.lastPage
                        isEndOfContent = true
                        _uiState.value = LearnUiState.CaughtUp()
                    }
                    is InitialCuriosityDeckResult.Failed -> {
                        _uiState.value = LearnUiState.Error("Failed to load curiosity curation")
                    }
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
            when (current) {
                is LearnUiState.Success -> _uiState.value = current.copy(isRefreshing = true)
                is LearnUiState.CaughtUp -> _uiState.value = current.copy(isRefreshing = true)
                else -> Unit
            }
            try {
                currentPage = 1
                isEndOfContent = false
                isLoadingMore = false
                when (val result = loadFirstAvailableDeck(bypassCache = true)) {
                    is InitialCuriosityDeckResult.Found -> showDeck(result)
                    is InitialCuriosityDeckResult.Exhausted -> {
                        currentPage = result.lastPage
                        isEndOfContent = true
                        _uiState.value = LearnUiState.CaughtUp()
                    }
                    is InitialCuriosityDeckResult.Failed -> {
                        _uiState.value = when (current) {
                            is LearnUiState.Success -> current.copy(isRefreshing = false)
                            is LearnUiState.CaughtUp -> current.copy(isRefreshing = false)
                            else -> LearnUiState.Error("Failed to refresh curiosity curation")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = when (current) {
                    is LearnUiState.Success -> current.copy(isRefreshing = false)
                    is LearnUiState.CaughtUp -> current.copy(isRefreshing = false)
                    else -> LearnUiState.Error(e.message ?: "Unknown error occurred")
                }
            }
        }
    }

    companion object {
        private const val MEANINGFUL_LORE_DWELL_MILLIS = 3_000L
    }
}
