package cx.aswin.boxcast.feature.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LearnHistoryUiState {
    data object Loading : LearnHistoryUiState
    data class Success(val entries: List<LearnHistoryEntry>) : LearnHistoryUiState
}

class LearnHistoryViewModel(
    application: Application,
    private val historyStore: LearnCuriosityHistoryStore = LearnCuriosityHistoryStore(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LearnHistoryUiState>(LearnHistoryUiState.Loading)
    val uiState: StateFlow<LearnHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = LearnHistoryUiState.Success(historyStore.getEntries())
    }

    fun restoreEntry(episodeId: String) {
        historyStore.removeEntry(episodeId, queueRestore = true)
        refresh()
    }

    fun removeEntry(episodeId: String) {
        historyStore.removeEntry(episodeId, queueRestore = false)
        refresh()
    }

    fun clearAll() {
        historyStore.clearAll()
        refresh()
    }
}
