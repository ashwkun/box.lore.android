package cx.aswin.boxcast.feature.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(
        val podcast: Podcast,
        val episodes: List<Episode>,
        val currentEpisode: Episode? = null,
        val isPlaying: Boolean = false,
        val isLoading: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val playbackSpeed: Float = 1.0f,
        val sleepTimerEnd: Long? = null,
        val isLiked: Boolean = false
    ) : PlayerUiState
    data object Error : PlayerUiState
}

class PlayerViewModel(
    application: Application,
    private val apiBaseUrl: String,
    private val publicKey: String,
    private val downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    private val playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository
) : AndroidViewModel(application) {

    private val repository = PodcastRepository(
        baseUrl = apiBaseUrl,
        publicKey = publicKey,
        context = application
    )
    private val database = cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(application)
    // Removed internal instantiation of PlaybackRepository
    
    // START: Playback State Mapping
    // We combine the Repository State + Local UI State (playlist info)
    
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        // Observe Playback Repository State
        viewModelScope.launch {
            playbackRepository.playerState.collect { playerState ->
                 val currentUi = _uiState.value
                 if (currentUi is PlayerUiState.Success) {
                     // Sync player state to UI
                     // Note: We might want to trust the Repository's current episode if it matches the podcast context
                     val syncedEpisode = playerState.currentEpisode ?: currentUi.currentEpisode
                     
                     
                     _uiState.value = currentUi.copy(
                         currentEpisode = syncedEpisode,
                         isPlaying = playerState.isPlaying,
                         isLoading = playerState.isLoading, // Add this mapping
                         positionMs = playerState.position,
                         durationMs = playerState.duration,
                         playbackSpeed = playerState.playbackSpeed,
                         sleepTimerEnd = playerState.sleepTimerEnd,
                         isLiked = playerState.isLiked
                     )
                 }
            }
        }
    }

    private var initialSource: String = "unknown"

    fun loadPodcast(podcastId: String, source: String = "unknown") {
        initialSource = source
        viewModelScope.launch {
            if (_uiState.value is PlayerUiState.Success) return@launch // Already loaded?
            
            // OPTIMIZATION: Check if we are already playing this podcast globally
            val globalState = playbackRepository.playerState.value
            if (globalState.currentPodcast?.id == podcastId && globalState.currentEpisode != null) {
                // Immediate Success from cache - NO LOADING STATE
                _uiState.value = PlayerUiState.Success(
                    podcast = globalState.currentPodcast!!,
                    episodes = emptyList(), // We might need episodes, but for player playback, current is key. 
                                            // Ideally we'd want the full list too, but let's see if we can fetch that in background 
                                            // while showing the player immediately.
                    currentEpisode = globalState.currentEpisode,
                    isPlaying = globalState.isPlaying,
                    positionMs = globalState.position,
                    durationMs = globalState.duration,
                    playbackSpeed = globalState.playbackSpeed,
                    sleepTimerEnd = globalState.sleepTimerEnd,
                    isLiked = globalState.isLiked
                )
                
                // Fetch full episode list in background to populate playlist if needed
                try {
                    val fullPodcast = repository.getPodcastDetails(podcastId)
                    val episodes = repository.getEpisodes(podcastId)
                    
                     val currentUi = _uiState.value
                     if (currentUi is PlayerUiState.Success) {
                         _uiState.value = currentUi.copy(
                             podcast = fullPodcast ?: currentUi.podcast,
                             episodes = episodes
                         )
                     }
                } catch (e: Exception) {
                    // Silent fail involves just keeping what we have
                    e.printStackTrace()
                }
                return@launch
            }

            _uiState.value = PlayerUiState.Loading
            try {
                // Fetch podcast details and episodes using feed ID
                val podcast = repository.getPodcastDetails(podcastId)
                
                if (podcast != null) {
                     val episodes = repository.getEpisodes(podcastId)
                     
                     // Check if global player is already playing something from this podcast
                     val updatedGlobal = playbackRepository.playerState.value
                     val isSamePodcast = updatedGlobal.currentPodcast?.id == podcastId
                     
                     val initialEpisode = if (isSamePodcast) updatedGlobal.currentEpisode else episodes.firstOrNull()
                     
                     _uiState.value = PlayerUiState.Success(
                         podcast = podcast, 
                         episodes = episodes,
                         currentEpisode = initialEpisode
                     )
                     
                     // Auto-play first episode if available and NOT already playing this podcast
                     if (episodes.isNotEmpty() && !isSamePodcast) {
                         playEpisode(episodes.first(), initialSource)
                     }
                } else {
                    _uiState.value = PlayerUiState.Error
                }
            } catch (e: Exception) {
                _uiState.value = PlayerUiState.Error
                e.printStackTrace()
            }
        }
    }

    fun playEpisode(episode: Episode, source: String? = null) {
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Success) {
            viewModelScope.launch {
                
                // Smart Skip: Check if episode is already in the active queue
                val currentQueue = playbackRepository.playerState.value.queue
                val existingIndex = currentQueue.indexOfFirst { it.id == episode.id }
                
                if (existingIndex != -1) {
                    android.util.Log.d("PlayerVM", "Episode found in queue at $existingIndex. Skipping to it.")
                    playbackRepository.skipToEpisode(existingIndex)
                    return@launch
                }
                
                // Queue Logic: Forward Chronological (Oldest -> Newest) starting from selected
                // We want to play the selected episode, then subsequent episodes in chronological order.
                val queue = currentState.episodes
                    .filter { it.publishedDate >= episode.publishedDate }
                    .sortedBy { it.publishedDate } // Ascending Order (Oldest First)
                
                // Ensure the selected episode is the start index
                val startIndex = queue.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
                
                playbackRepository.playQueue(queue, currentState.podcast, startIndex)
            }
        }
    }
    
    fun togglePlayPause() {
        val currentState = _uiState.value
        if (currentState is PlayerUiState.Success) {
            if (currentState.isPlaying) {
                playbackRepository.pause()
            } else {
                playbackRepository.resume()
            }
        }
    }
    
    fun seekTo(positionMs: Long) {
        playbackRepository.seekTo(positionMs)
    }
    
    fun skipForward() {
        playbackRepository.skipForward()
    }
    
    fun skipBackward() {
        playbackRepository.skipBackward()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackRepository.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int) {
        playbackRepository.setSleepTimer(minutes)
    }



    fun toggleLike() {
        viewModelScope.launch {
            playbackRepository.toggleLike()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT release player here, it's global service
    }
}
