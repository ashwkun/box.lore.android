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

sealed interface LearnUiState {
    data object Loading : LearnUiState
    
    data class Success(
        val data: CuratedCuriosityResponseDto,
        val isRefreshing: Boolean = false
    ) : LearnUiState
    
    data class Error(val message: String) : LearnUiState
}

class LearnViewModel(
    private val podcastRepository: PodcastRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LearnUiState>(LearnUiState.Loading)
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = LearnUiState.Loading
            try {
                val res = podcastRepository.getCuratedCuriosity()
                if (res != null) {
                    _uiState.value = LearnUiState.Success(data = res)
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
                val res = podcastRepository.getCuratedCuriosity()
                if (res != null) {
                    _uiState.value = LearnUiState.Success(data = res, isRefreshing = false)
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
