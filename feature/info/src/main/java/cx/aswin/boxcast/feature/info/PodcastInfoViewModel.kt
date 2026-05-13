package cx.aswin.boxcast.feature.info

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

enum class EpisodeSort { NEWEST, OLDEST }

@Immutable
sealed interface PodcastInfoUiState {
    data object Loading : PodcastInfoUiState
    data class Success(
        val podcast: Podcast,
        val episodes: List<Episode>,
        val isSubscribed: Boolean,
        val isLoadingMore: Boolean = false,
        val hasMoreEpisodes: Boolean = true,
        val currentSort: EpisodeSort = EpisodeSort.NEWEST,
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val searchResults: List<Episode>? = null // null = not searching, empty = no results
    ) : PodcastInfoUiState
    data object Error : PodcastInfoUiState
}

class PodcastInfoViewModel(
    application: Application,
    private val apiBaseUrl: String,
    private val publicKey: String,
    private val playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository,
    private val downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    private val queueManager: cx.aswin.boxcast.core.data.QueueManager,
    private val entryPoint: String?,
    private val genreFilter: String?,
    private val scrollDepth: Int?,
    private val searchQuery: String?
) : AndroidViewModel(application) {

    private val repository = PodcastRepository(
        baseUrl = apiBaseUrl,
        publicKey = publicKey,
        context = application
    )
    private val database = cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(application)
    private val subscriptionRepository = cx.aswin.boxcast.core.data.SubscriptionRepository(database.podcastDao())

    private val _uiState = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Loading)
    val uiState: StateFlow<PodcastInfoUiState> = _uiState.asStateFlow()

    private var currentPodcastId: String = ""
    private var currentOffset: Int = 0
    private var searchJob: Job? = null

    // --- Tracking State ---
    private var sessionStartTime = System.currentTimeMillis()
    private var wasSubscribedAtStart: Boolean? = null
    private var didSubscribe = false
    private var didUnsubscribe = false
    private var didSearch = false
    private var didSortEpisodes = false
    private val playedEpisodes = mutableSetOf<String>()
    private val clickedEpisodes = mutableSetOf<String>()
    private var hasTrackedExit = false

    // Observe liked episodes
    private val likedEpisodeIds = playbackRepository.likedEpisodes
        .map { historyList -> historyList.map { it.episodeId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    fun onToggleLike(episode: Episode) {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                playbackRepository.toggleLike(
                    episode = episode,
                    podcastId = currentState.podcast.id,
                    podcastTitle = currentState.podcast.title,
                    podcastImageUrl = currentState.podcast.imageUrl
                )
            }
        }
    }
    
    // Check if an episode is liked (helper for UI)
    fun isEpisodeLiked(episodeId: String): Boolean {
        return likedEpisodeIds.value.contains(episodeId)
    }

    // Expose flow for UI to collect
    val likedEpisodesState = likedEpisodeIds

    // Observe completed episodes
    private val completedEpisodeIds = playbackRepository.completedEpisodeIds
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )
    val completedEpisodesState: StateFlow<Set<String>> = completedEpisodeIds

    fun isEpisodeCompleted(episodeId: String): Boolean {
        return completedEpisodeIds.value.contains(episodeId)
    }

    fun onToggleCompletion(episode: Episode) {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                playbackRepository.toggleCompletion(
                    episode = episode,
                    podcastId = currentState.podcast.id,
                    podcastTitle = currentState.podcast.title,
                    podcastImageUrl = currentState.podcast.imageUrl
                )
            }
        }
    }

    fun toggleDownload(episode: Episode) {
        val currentState = _uiState.value
        android.util.Log.d("PodcastInfoVM", "toggleDownload: title=${episode.title}, state=$currentState")
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val isDownloaded = downloadRepository.isDownloaded(episode.id).first()
                val isDownloading = downloadRepository.isDownloading(episode.id).first()
                android.util.Log.d("PodcastInfoVM", "toggleDownload check: downloaded=$isDownloaded, downloading=$isDownloading")
                if (isDownloaded || isDownloading) {
                    android.util.Log.d("PodcastInfoVM", "Removing download")
                    downloadRepository.removeDownload(episode.id)
                } else {
                    android.util.Log.d("PodcastInfoVM", "Adding download")
                    downloadRepository.addDownload(episode, currentState.podcast)
                }
            }
        } else {
             android.util.Log.w("PodcastInfoVM", "toggleDownload ignored, state is not Success")
        }
    }

    fun isDownloaded(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return downloadRepository.isDownloaded(episodeId)
    }

    fun isDownloading(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return downloadRepository.isDownloading(episodeId)
            .map { isDownloading ->
                android.util.Log.d("PodcastInfoVM", "isDownloading($episodeId): $isDownloading")
                isDownloading
            }
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val SEARCH_DEBOUNCE_MS = 500L
    }

    fun loadPodcast(podcastId: String) {
        if (currentPodcastId == podcastId && (_uiState.value is PodcastInfoUiState.Success || _uiState.value is PodcastInfoUiState.Error)) {
            return
        }
        
        // Track screen viewed
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPodcastInfoScreenViewed(
            podcastId = podcastId,
            entryPoint = entryPoint,
            genreFilter = genreFilter,
            scrollDepth = scrollDepth,
            searchQuery = searchQuery
        )

        currentPodcastId = podcastId
        currentOffset = 0
        viewModelScope.launch {
            _uiState.value = PodcastInfoUiState.Loading
            try {
                val podcast = repository.getPodcastDetails(podcastId)
                if (podcast != null) {
                    val page = repository.getEpisodesPaginated(podcastId, PAGE_SIZE, 0, "newest")
                    val isSubscribed = subscriptionRepository.isSubscribed(podcastId)
                    
                    if (wasSubscribedAtStart == null) {
                        wasSubscribedAtStart = isSubscribed
                    }

                    currentOffset = page.episodes.size
                    _uiState.value = PodcastInfoUiState.Success(
                        podcast = podcast,
                        episodes = page.episodes,
                        isSubscribed = isSubscribed,
                        hasMoreEpisodes = page.hasMore
                    )
                } else {
                    _uiState.value = PodcastInfoUiState.Error
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = PodcastInfoUiState.Error
            }
        }
    }

    fun loadMoreEpisodes() {
        val currentState = _uiState.value
        if (currentState !is PodcastInfoUiState.Success) return
        if (currentState.isLoadingMore || !currentState.hasMoreEpisodes) return
        if (currentState.searchResults != null) return // Don't load more when searching

        _uiState.value = currentState.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val sortParam = if (currentState.currentSort == EpisodeSort.OLDEST) "oldest" else "newest"
                val page = repository.getEpisodesPaginated(currentPodcastId, PAGE_SIZE, currentOffset, sortParam)
                currentOffset += page.episodes.size
                _uiState.value = currentState.copy(
                    episodes = currentState.episodes + page.episodes,
                    isLoadingMore = false,
                    hasMoreEpisodes = page.hasMore
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }

    fun toggleSort() {
        didSortEpisodes = true
        val currentState = _uiState.value
        if (currentState !is PodcastInfoUiState.Success) return

        val newSort = if (currentState.currentSort == EpisodeSort.NEWEST) EpisodeSort.OLDEST else EpisodeSort.NEWEST
        currentOffset = 0
        
        _uiState.value = currentState.copy(
            currentSort = newSort,
            episodes = emptyList(),
            isLoadingMore = true,
            hasMoreEpisodes = true,
            searchQuery = "",
            searchResults = null
        )

        viewModelScope.launch {
            try {
                val sortParam = if (newSort == EpisodeSort.OLDEST) "oldest" else "newest"
                val page = repository.getEpisodesPaginated(currentPodcastId, PAGE_SIZE, 0, sortParam)
                currentOffset = page.episodes.size
                val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value = latestState.copy(
                    episodes = page.episodes,
                    isLoadingMore = false,
                    hasMoreEpisodes = page.hasMore
                )
            } catch (e: Exception) {
                e.printStackTrace()
                val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value = latestState.copy(isLoadingMore = false)
            }
        }
    }

    fun searchEpisodes(query: String) {
        if (query.isNotBlank()) didSearch = true
        val currentState = _uiState.value
        if (currentState !is PodcastInfoUiState.Success) return

        _uiState.value = currentState.copy(searchQuery = query)

        // Clear search
        if (query.isBlank()) {
            _uiState.value = currentState.copy(
                searchQuery = "",
                searchResults = null,
                isSearching = false
            )
            return
        }

        // Debounce search
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = (_uiState.value as? PodcastInfoUiState.Success)?.copy(isSearching = true) ?: return@launch
            delay(SEARCH_DEBOUNCE_MS)
            
            try {
                // Correctly search for episodes within this feed using the repository
                val podcastContext = (uiState.value as? PodcastInfoUiState.Success)
                val feedId = podcastContext?.podcast?.id ?: return@launch
                
                val results = repository.searchEpisodes(feedId, query)
                
                // Ensure we are still in a valid state to update
                val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value = latestState.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value = latestState.copy(isSearching = false, searchResults = emptyList())
            }
        }
    }

    fun toggleSubscription() {
        val currentState = _uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val wasSubscribed = currentState.isSubscribed
                if (wasSubscribed) {
                    didUnsubscribe = true
                } else {
                    didSubscribe = true
                }
                subscriptionRepository.toggleSubscription(currentState.podcast)
                // Refresh state
                val isSubscribed = subscriptionRepository.isSubscribed(currentState.podcast.id)
                _uiState.value = currentState.copy(isSubscribed = isSubscribed)
                
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPodcastSubscriptionToggled(
                    podcastId = currentState.podcast.id,
                    podcastName = currentState.podcast.title,
                    isSubscribed = isSubscribed,
                    entryPoint = entryPoint ?: "unknown"
                )

                if (isSubscribed && !wasSubscribed) {

                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val synced = repository.syncSubscriptions(listOf(currentState.podcast.id))
                            synced[currentState.podcast.id]?.let { episode ->
                                subscriptionRepository.updateLatestEpisode(currentState.podcast.id, episode)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (!isSubscribed && wasSubscribed) {

                }
            }
        }
    }

    fun toggleQueue(episode: Episode) {
        val currentState = _uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val isQueued = queuedEpisodeIds.value.contains(episode.id)
                if (isQueued) {
                    playbackRepository.removeFromQueue(episode.id)
                } else {
                    // User requested "Add to Queue" -> Insert as NEXT item
                    playbackRepository.addToQueueNext(episode, currentState.podcast)
                }
            }
        }
    }
    
    // Playback State Logic
    data class EpisodePlaybackState(
        val isPlaying: Boolean = false,
        val isResume: Boolean = false,
        val progress: Float = 0f,
        val timeLeft: String? = null
    )
    
    // Combine player state and history to provide per-episode state
    val episodePlaybackState: StateFlow<Map<String, EpisodePlaybackState>> = kotlinx.coroutines.flow.combine(
        playbackRepository.playerState,
        playbackRepository.getAllHistory()
    ) { player, historyList ->
        val map = mutableMapOf<String, EpisodePlaybackState>()
        
        // 1. Map History (Resume State)
        historyList.forEach { history ->
            if (!history.isCompleted && history.progressMs > 0L && history.durationMs > 0L) {
                val progress = (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
                val remainingSeconds = (history.durationMs - history.progressMs) / 1000
                val timeLeft = if (remainingSeconds > 0) {
                    val h = remainingSeconds / 3600
                    val m = (remainingSeconds % 3600) / 60
                    if (h > 0) "${h}h ${m}m left" else "${m}m left"
                } else null
                
                map[history.episodeId] = EpisodePlaybackState(
                    isPlaying = false,
                    isResume = true,
                    progress = progress,
                    timeLeft = timeLeft
                )
            }
        }
        
        // 2. Override with Active Player State
        val currentEp = player.currentEpisode
        if (currentEp != null) {
            val progress = if (player.duration > 0) (player.position.toFloat() / player.duration).coerceIn(0f, 1f) else 0f
            val remainingSeconds = if (player.duration > 0) (player.duration - player.position) / 1000 else 0
             val timeLeft = if (remainingSeconds > 0) {
                val h = remainingSeconds / 3600
                val m = (remainingSeconds % 3600) / 60
                if (h > 0) "${h}h ${m}m left" else "${m}m left"
            } else null
            
            map[currentEp.id] = EpisodePlaybackState(
                isPlaying = player.isPlaying,
                isResume = true, // Currently playing is technically "resumed" or "active"
                progress = progress,
                timeLeft = timeLeft
            )
        }
        
        map
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )
    
    fun onPlayClick(episode: Episode) {
        android.util.Log.d("PodcastInfoViewModel", "onPlayClick triggered for: ${episode.title} (ID: ${episode.id})")
        playedEpisodes.add(episode.id)
        val currentState = _uiState.value as? PodcastInfoUiState.Success ?: return

        viewModelScope.launch {
            if (playbackRepository.playerState.value.currentEpisode?.id == episode.id) {
                android.util.Log.d("PodcastInfoViewModel", "Episode already active, toggling play/pause")
                playbackRepository.togglePlayPause()
            } else {
                android.util.Log.d("PodcastInfoViewModel", "Starting new playback via queueManager")
                val sortOrder = if (currentState.currentSort == EpisodeSort.OLDEST) "oldest" else "newest"
                queueManager.playEpisode(episode, currentState.podcast, sortOrder)
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

    fun recordEpisodeClick(episodeId: String) {
        clickedEpisodes.add(episodeId)
    }

    fun onScreenResume() {
        if (hasTrackedExit) {
            // Restart the session timer when coming back from background
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackScreenExit() {
        if (hasTrackedExit || currentPodcastId.isEmpty()) return
        hasTrackedExit = true
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        val podcastName = (_uiState.value as? PodcastInfoUiState.Success)?.podcast?.title ?: "Unknown"
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPodcastInfoScreenSession(
            podcastId = currentPodcastId,
            podcastName = podcastName,
            timeSpentSeconds = timeSpent,
            wasSubscribed = wasSubscribedAtStart ?: false,
            didSubscribe = didSubscribe,
            didUnsubscribe = didUnsubscribe,
            didSearch = didSearch,
            didSortEpisodes = didSortEpisodes,
            episodesPlayedCount = playedEpisodes.size,
            episodesClickedCount = clickedEpisodes.size
        )
    }

    override fun onCleared() {
        super.onCleared()
        trackScreenExit()
    }
}
