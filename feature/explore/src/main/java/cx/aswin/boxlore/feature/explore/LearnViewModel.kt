package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.ranking.CandidateFeatureBuilder
import cx.aswin.boxlore.core.ranking.CandidateSignals
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingExposure
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.explore.logic.filterAndShuffleNewItems
import cx.aswin.boxlore.feature.explore.logic.weightedShuffle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LearnUiState {
    data object Loading : LearnUiState
    
    data class Success(
        val questionsStack: List<LearnCuriosityCard>,
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
    private val visibleSince = mutableMapOf<String, Long>()

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

        val restoredCards = restored.map { it.toLearnCuriosityCard() }
        _uiState.update { currentState ->
            when (currentState) {
                is LearnUiState.Success -> {
                    val restoredIds = restoredCards.map { it.episodeId }.toSet()
                    val merged = restoredCards + currentState.questionsStack.filterNot {
                        it.episodeId in restoredIds
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
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLearnScreenSession(
            timeSpentSeconds = timeSpent,
            cardsDismissedCount = cardsDismissedCount,
            cardsQueuedCount = cardsQueuedCount,
            playsCount = playsCount,
            podcastsClickedCount = podcastsClickedCount,
            infosClickedCount = infosClickedCount
        )
    }

    fun trackCardVisible(card: LearnCuriosityCard) {
        if (visibleSince.putIfAbsent(card.episodeId, System.currentTimeMillis()) != null) return
        viewModelScope.launch {
            rankingFeedback.recordExposure(
                RankingExposure(
                    episodeId = card.episodeId,
                    podcastId = card.podcastId.orEmpty(),
                    objective = RankingObjective.DISCOVERY,
                    surface = RankingSurface.EXPLORE,
                    source = CandidateSource.CURATED_INTENT,
                    features = CandidateFeatureBuilder.build(
                        CandidateSignals(
                            isUnseenShow = true,
                            serverRelevance = card.curiosityScore / 10.0,
                            isUnplayed = true,
                        ),
                    ),
                    entryPoint = "lore",
                    online = true,
                ),
            )
        }
    }

    fun trackCardDismissed(card: LearnCuriosityCard) {
        cardsDismissedCount++
        val dwellMillis = System.currentTimeMillis() - (visibleSince.remove(card.episodeId) ?: return)
        if (dwellMillis < MEANINGFUL_LORE_DWELL_MILLIS) return
        recordLoreAction(card, RankingAction.DISMISS)
    }

    fun trackCardQueued(card: LearnCuriosityCard) {
        cardsQueuedCount++
        visibleSince.remove(card.episodeId)
        recordLoreAction(card, RankingAction.EXPLICIT_QUEUE)
    }

    fun trackPlayClicked(card: LearnCuriosityCard) {
        playsCount++
        recordLoreAction(card, RankingAction.OPEN_DETAILS)
    }

    fun trackPodcastClicked(card: LearnCuriosityCard) {
        podcastsClickedCount++
        recordLoreAction(card, RankingAction.OPEN_DETAILS)
    }

    fun trackInfoClicked(card: LearnCuriosityCard) {
        infosClickedCount++
        recordLoreAction(card, RankingAction.OPEN_DETAILS)
    }

    private fun recordLoreAction(
        card: LearnCuriosityCard,
        action: RankingAction,
    ) {
        viewModelScope.launch {
            rankingFeedback.recordAction(
                target = FeedbackTarget(
                    episodeId = card.episodeId,
                    podcastId = card.podcastId.orEmpty(),
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
            )?.questionsStack?.map { it.toLearnCuriosityCard() }
        }
    )

    private fun showDeck(result: InitialCuriosityDeckResult.Found) {
        currentPage = result.page
        val shuffled = weightedShuffle(
            list = result.unseenItems,
            curiosityScore = LearnCuriosityCard::curiosityScore,
        )
        _uiState.value = LearnUiState.Success(
            questionsStack = shuffled
        )
    }

    fun dismissCuriosity(card: LearnCuriosityCard, action: LearnHistoryAction) {
        val episodeId = card.episodeId
        historyStore.recordDismissal(card, action)

        val currentState = _uiState.value
        if (currentState is LearnUiState.Success) {
            val updatedStack = currentState.questionsStack.filterNot { it.episodeId == episodeId }
            if (updatedStack.isEmpty() && isEndOfContent) {
                _uiState.value = LearnUiState.CaughtUp()
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLearnCaughtUp(0)
                return
            }

            _uiState.value = currentState.copy(questionsStack = updatedStack)
            if (updatedStack.size < 3) {
                fetchNextPage()
            }
        }
    }

    private suspend fun fetchPageAndFilter(
        page: Int,
        currentStack: List<LearnCuriosityCard>
    ): List<LearnCuriosityCard> {
        val res = podcastRepository.getCuratedCuriosity(page = page, bypassCache = false) ?: return emptyList()
        if (res.questionsStack.isEmpty()) {
            isEndOfContent = true
            return emptyList()
        }
        currentPage = page
        return filterAndShuffleNewItems(
            rawItems = res.questionsStack.map { it.toLearnCuriosityCard() },
            currentStack = currentStack,
            dismissedIds = getDismissedIds(),
            episodeId = LearnCuriosityCard::episodeId,
            curiosityScore = LearnCuriosityCard::curiosityScore,
        )
    }

    private fun appendCuriositiesToSuccessState(newItems: List<LearnCuriosityCard>) {
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
                var accumulatedNew = emptyList<LearnCuriosityCard>()
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
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLearnCaughtUp(0)
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
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLearnCaughtUp(0)
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
