package cx.aswin.boxcast.feature.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.network.model.CuratedCuriosityResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    application: Application
) : AndroidViewModel(application) {

    private val prefs = getApplication<Application>().getSharedPreferences("boxcast_prefs", android.content.Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    private var currentPage = 1
    private var isLoadingMore = false
    private var isEndOfContent = false

    init {
        loadData()
    }

    private fun getDismissedIds(): Set<String> {
        return prefs.getStringSet("dismissed_curiosities", emptySet()) ?: emptySet()
    }

    fun dismissCuriosity(episodeId: String) {
        val currentSet = getDismissedIds().toMutableSet()
        currentSet.add(episodeId)
        prefs.edit().putStringSet("dismissed_curiosities", currentSet).apply()

        // Update the state with the pruned stack
        val currentState = _uiState.value
        if (currentState is LearnUiState.Success) {
            val updatedStack = currentState.questionsStack.filterNot { it.episode.id.toString() == episodeId }
            _uiState.value = currentState.copy(questionsStack = updatedStack)

            // Trigger pre-fetching if the card pool runs low (less than 3 cards)
            if (updatedStack.size < 3) {
                fetchNextPage()
            }
        }
    }

    private fun fetchNextPage() {
        if (isLoadingMore || isEndOfContent) return
        val currentState = _uiState.value as? LearnUiState.Success ?: return

        isLoadingMore = true
        viewModelScope.launch {
            try {
                val nextPage = currentPage + 1
                val res = podcastRepository.getCuratedCuriosity(page = nextPage, bypassCache = false)
                if (res != null) {
                    if (res.questionsStack.isEmpty()) {
                        isEndOfContent = true
                    } else {
                        currentPage = nextPage
                        val dismissed = getDismissedIds()
                        val newItems = res.questionsStack.filterNot { it.episode.id.toString() in dismissed }
                        
                        if (newItems.isNotEmpty()) {
                            val shuffledNew = weightedShuffle(newItems)
                            val currentStack = (_uiState.value as? LearnUiState.Success)?.questionsStack ?: emptyList()
                            val existingIds = currentStack.map { it.episode.id }.toSet()
                            val uniqueNew = shuffledNew.filterNot { it.episode.id in existingIds }
                            
                            _uiState.value = currentState.copy(
                                questionsStack = currentStack + uniqueNew
                            )
                        }
                    }
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
