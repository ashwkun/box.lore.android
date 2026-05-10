package cx.aswin.boxcast.feature.info

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import cx.aswin.boxcast.core.model.Episode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

@Immutable
sealed interface EpisodeInfoUiState {
    data object Loading : EpisodeInfoUiState
    data class Success(
        val episode: Episode,
        val podcastId: String,
        val podcastTitle: String,
        val podcastGenre: String = "",
        val resumePositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val relatedEpisodes: List<Episode> = emptyList(),
        val relatedEpisodesLoading: Boolean = true,
        val isPlaying: Boolean = false // Sync with global player
    ) : EpisodeInfoUiState
    data object Error : EpisodeInfoUiState
}

class EpisodeInfoViewModel(
    application: Application,
    private val apiBaseUrl: String,
    private val publicKey: String,
    private val playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository,
    private val downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    private val queueManager: cx.aswin.boxcast.core.data.QueueManager
) : AndroidViewModel(application) {

    private val database = cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(application)

    private val _uiState = MutableStateFlow<EpisodeInfoUiState>(EpisodeInfoUiState.Loading)
    val uiState: StateFlow<EpisodeInfoUiState> = _uiState.asStateFlow()

    // Observe liked episodes
    val likedEpisodeIds = playbackRepository.likedEpisodes
        .map { historyList -> historyList.map { it.episodeId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    val completedEpisodeIds = playbackRepository.completedEpisodeIds
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    // --- Tracking State ---
    private var sessionStartTime = System.currentTimeMillis()
    private var didPlay = false
    private var didLike = false
    private var didDownload = false
    private var didAddToQueue = false
    private var didMarkPlayed = false
    private var didViewPodcast = false
    private var didViewRelatedEpisode = false
    private var didScrollRelatedEpisodes = false
    private var relatedEpisodesShownCount = 0
    private var sourceEntryPoint: String? = null
    private var hasTrackedExit = false

    fun onToggleCompletion() {
        didMarkPlayed = true
        val currentState = uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            viewModelScope.launch {
                playbackRepository.toggleCompletion(
                    episode = currentState.episode,
                    podcastId = currentState.podcastId,
                    podcastTitle = currentState.podcastTitle,
                    podcastImageUrl = currentState.episode.podcastImageUrl
                )
            }
        }
    }

    init {
        // Observe global player state to sync button (Play/Pause)
        viewModelScope.launch {
            playbackRepository.playerState.collect { playerState ->
                val currentState = _uiState.value
                if (currentState is EpisodeInfoUiState.Success) {
                    val isSameEpisode = playerState.currentEpisode?.id == currentState.episode.id
                    val isPlaying = isSameEpisode && playerState.isPlaying
                    
                    // If playing this episode, we can also sync the progress in real-time
                    val resumePos = if (isSameEpisode) playerState.position else currentState.resumePositionMs
                    
                    if (currentState.isPlaying != isPlaying || (isSameEpisode && currentState.resumePositionMs != resumePos)) {
                        _uiState.value = currentState.copy(
                            isPlaying = isPlaying,
                            resumePositionMs = resumePos
                        )
                    }
                }
            }
        }
    }

    fun onToggleLike(episode: Episode) {
        didLike = true
        val currentState = uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            val wasLiked = likedEpisodeIds.value.contains(episode.id)
            viewModelScope.launch {
                playbackRepository.toggleLike(
                    episode = episode,
                    podcastId = currentState.podcastId,
                    podcastTitle = currentState.podcastTitle,
                    podcastImageUrl = currentState.episode.podcastImageUrl
                )

            }
        }
    }


    fun loadEpisode(
        episodeId: String,
        episodeTitle: String,
        episodeDescription: String,
        episodeImageUrl: String,
        episodeAudioUrl: String,
        episodeDuration: Int,
        podcastId: String,
        podcastTitle: String,
        entryPointContext: android.os.Bundle? = null
    ) {
        val currentState = _uiState.value
        // If we already have this episode loaded, don't reload
        if (currentState is EpisodeInfoUiState.Success && currentState.episode.id == episodeId) {
            return
        }

        viewModelScope.launch {
            _uiState.value = EpisodeInfoUiState.Loading
            try {
                // 1. Show immediate data (partial)
                var currentEpisode = Episode(
                    id = episodeId,
                    title = episodeTitle,
                    description = episodeDescription,
                    imageUrl = episodeImageUrl,
                    audioUrl = episodeAudioUrl,
                    duration = episodeDuration,
                    publishedDate = 0L
                )
                
                // Check for resume position immediately
                val resumeSession = playbackRepository.getSession(episodeId)
                val resumeMs = resumeSession?.positionMs ?: 0L
                val durationMs = resumeSession?.durationMs ?: (episodeDuration * 1000L)

                _uiState.value = EpisodeInfoUiState.Success(
                    episode = currentEpisode,
                    podcastId = podcastId,
                    podcastTitle = podcastTitle,
                    resumePositionMs = resumeMs,
                    durationMs = durationMs
                )
                
                // Track Screen View
                val props = mutableMapOf<String, Any>().apply {
                    put("podcast_id", podcastId)
                    put("episode_id", episodeId)
                    put("is_partially_played", resumeMs > 0)
                    if (entryPointContext != null) {
                        entryPointContext.getString("entry_point")?.let {
                            sourceEntryPoint = it
                            put("source_entry_point", it)
                        }
                    }
                }
                com.posthog.PostHog.capture("episode_info_screen_viewed", properties = props)
                
                // 2. Fetch full details (description, etc.)
                // Only if description is empty or we suspect it's partial? Always fetch to be safe.
                // 2. Fetch full details from Network (since we don't have local Episode table yet)
                val repository = cx.aswin.boxcast.core.data.PodcastRepository(apiBaseUrl, publicKey, getApplication())
                val fullEpisode = repository.getEpisode(episodeId)

                if (fullEpisode != null) {
                    val netImage = fullEpisode.imageUrl
                    currentEpisode = fullEpisode.copy(
                        // Preserve passed image if network one is missing
                        imageUrl = if (!netImage.isNullOrEmpty()) netImage else episodeImageUrl
                    )
                    
                    // Preserve existing relatedEpisodes state if already loaded
                    val existingState = _uiState.value as? EpisodeInfoUiState.Success
                    _uiState.value = EpisodeInfoUiState.Success(
                        episode = currentEpisode,
                        podcastId = podcastId,
                        podcastTitle = podcastTitle,
                        resumePositionMs = resumeMs,
                        durationMs = durationMs,
                        relatedEpisodes = existingState?.relatedEpisodes ?: emptyList(),
                        relatedEpisodesLoading = existingState?.relatedEpisodesLoading ?: true
                    )
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep showing partial data if success was already emitted?
                if (_uiState.value is EpisodeInfoUiState.Loading) {
                    _uiState.value = EpisodeInfoUiState.Error
                }
            }
        }
        
        // 3. Fetch related episodes AND podcast genre INDEPENDENTLY (non-blocking)
        viewModelScope.launch {
            try {
                android.util.Log.d("EpisodeInfo", "Fetching related episodes for podcastId: $podcastId")
                val repository = cx.aswin.boxcast.core.data.PodcastRepository(apiBaseUrl, publicKey, getApplication())
                
                // Fetch podcast to get genre
                val podcast = repository.getPodcastDetails(podcastId)
                val genre = podcast?.genre ?: ""
                
                // Use getEpisodesPaginated which is the correct method used elsewhere
                val page = repository.getEpisodesPaginated(podcastId, 15, 0, "newest")
                android.util.Log.d("EpisodeInfo", "Fetched ${page.episodes.size} episodes, genre: $genre")
                val relatedEps = page.episodes
                    .filter { it.id != episodeId }
                    .take(10)
                
                    val currentSuccess = _uiState.value as? EpisodeInfoUiState.Success
                    if (currentSuccess != null && currentSuccess.episode.id == episodeId) {
                        relatedEpisodesShownCount = relatedEps.size
                        _uiState.value = currentSuccess.copy(
                        relatedEpisodes = relatedEps,
                        relatedEpisodesLoading = false,
                        podcastGenre = genre.ifEmpty { currentSuccess.podcastGenre }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("EpisodeInfo", "Error fetching related episodes", e)
                // Mark loading as done even on failure
                val currentSuccess = _uiState.value as? EpisodeInfoUiState.Success
                if (currentSuccess != null) {
                    _uiState.value = currentSuccess.copy(relatedEpisodesLoading = false)
                }
                e.printStackTrace()
            }
        }
    }

    fun toggleDownload(episode: Episode) {
        didDownload = true
        val currentState = _uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            viewModelScope.launch {
                // Check if already downloaded
                val isDownloaded = downloadRepository.isDownloaded(episode.id).first()
                if (isDownloaded) {
                    downloadRepository.removeDownload(episode.id)
                } else {
                    val podcast = cx.aswin.boxcast.core.model.Podcast(
                        id = currentState.podcastId,
                        title = currentState.podcastTitle,
                        artist = "",
                        imageUrl = currentState.episode.podcastImageUrl ?: "",
                        description = "",
                        genre = currentState.podcastGenre
                    )
                    downloadRepository.addDownload(episode, podcast)

                }
            }
        }
    }
    
    fun isDownloaded(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return downloadRepository.isDownloaded(episodeId)
    }

    fun isDownloading(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return downloadRepository.isDownloading(episodeId)
    }

    fun onMainActionClick(entryPointContext: android.os.Bundle? = null) {
        didPlay = true
        val currentState = _uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            val globalState = playbackRepository.playerState.value
            
            // Rewrite the bundle so that the immediate entry point is the episode screen
            val finalBundle = android.os.Bundle().apply {
                if (entryPointContext != null) {
                    putAll(entryPointContext)
                    val originalEntryPoint = entryPointContext.getString("entry_point")
                    if (originalEntryPoint != null) {
                        putString("source_entry_point", originalEntryPoint)
                    }
                }
                putString("entry_point", "episode_info_screen")
            }

            if (globalState.currentEpisode?.id == currentState.episode.id) {
                // Same episode: Toggle Play/Pause
                if (!globalState.isPlaying) {
                    val map = mutableMapOf<String, Any>()
                    finalBundle.keySet().forEach { key ->
                        finalBundle.get(key)?.let { map[key] = it }
                    }
                    if (map.isNotEmpty()) {
                        cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(map)
                    }
                }
                playbackRepository.togglePlayPause()
            } else {
                // Different episode: Start Playback

                viewModelScope.launch {
                    val pod = cx.aswin.boxcast.core.model.Podcast(
                        id = currentState.podcastId,
                        title = currentState.podcastTitle,
                        artist = "",
                        imageUrl = currentState.episode.podcastImageUrl ?: "",
                        description = "",
                        genre = currentState.podcastGenre
                    )
                    queueManager.playEpisode(currentState.episode, pod, entryPointContext = finalBundle)
                }
            }
        }
    }


    fun toggleQueue() {
        didAddToQueue = true
        val currentState = _uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            viewModelScope.launch {
                val isQueued = queuedEpisodeIds.value.contains(currentState.episode.id)
                if (isQueued) {
                    playbackRepository.removeFromQueue(currentState.episode.id)
                } else {
                    val pod = cx.aswin.boxcast.core.model.Podcast(
                        id = currentState.podcastId,
                        title = currentState.podcastTitle,
                        artist = "",
                        imageUrl = currentState.episode.podcastImageUrl ?: "",
                        description = "",
                        genre = currentState.podcastGenre
                    )
                    // User requested "Add to Queue" -> Insert as NEXT item
                    playbackRepository.addToQueueNext(currentState.episode, pod)

                }
            }
        }
    }
        
    // Track queued episodes
    val queuedEpisodeIds: StateFlow<Set<String>> = playbackRepository.playerState
        .map { state -> state.queue.map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )
        
    fun onPodcastLinkClicked() {
        didViewPodcast = true
    }
    
    fun onRelatedEpisodeClicked() {
        didViewRelatedEpisode = true
    }
    
    fun onRelatedEpisodesScrolled() {
        didScrollRelatedEpisodes = true
    }

    fun onScreenResume() {
        if (hasTrackedExit) {
            // User came back from background or backstack. Restart the session timer.
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackScreenExit() {
        if (hasTrackedExit) return
        hasTrackedExit = true
        
        val currentState = _uiState.value as? EpisodeInfoUiState.Success ?: return
        val timeSpentSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000f
        
        val props = mutableMapOf<String, Any>().apply {
            put("podcast_id", currentState.podcastId)
            put("episode_id", currentState.episode.id)
            put("time_spent_seconds", timeSpentSeconds)
            put("did_play", didPlay)
            put("did_like", didLike)
            put("did_download", didDownload)
            put("did_add_to_queue", didAddToQueue)
            put("did_mark_played", didMarkPlayed)
            put("did_view_podcast", didViewPodcast)
            put("did_view_related_episode", didViewRelatedEpisode)
            put("related_episodes_shown_count", relatedEpisodesShownCount)
            put("did_scroll_related_episodes", didScrollRelatedEpisodes)
            
            if (sourceEntryPoint != null) {
                put("source_entry_point", sourceEntryPoint!!)
            }
        }
        com.posthog.PostHog.capture("episode_info_screen_session", properties = props)
    }
}
