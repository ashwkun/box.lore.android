package cx.aswin.boxcast.feature.briefing

import cx.aswin.boxcast.core.model.Briefing

sealed interface BriefingUiState {
    data object Loading : BriefingUiState
    
    data class Success(
        val briefing: Briefing,
        val selectedRegion: String,
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val isBuffering: Boolean = false
    ) : BriefingUiState
    
    data class Error(
        val message: String,
        val selectedRegion: String
    ) : BriefingUiState
}
