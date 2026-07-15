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
import kotlinx.coroutines.flow.distinctUntilChanged
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
        val similarEpisodes: List<Episode> = emptyList(),
        val similarEpisodesLoading: Boolean = true,
        val isPlaying: Boolean = false, // Sync with global player
        val isPlaybackLoading: Boolean = false,
        val location: String? = null,
        val license: String? = null,
        val crossPromotion: cx.aswin.boxcast.core.model.ResolvedCrossPromotion? = null,
        val crossPromoLoading: Boolean = false
    ) : EpisodeInfoUiState
    data object Error : EpisodeInfoUiState
}

class EpisodeInfoViewModel(
    application: Application,
    private val apiBaseUrl: String,
    private val publicKey: String,
    private val playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository,
    private val downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    private val queueManager: cx.aswin.boxcast.core.data.QueueManager,
    private val userPrefs: cx.aswin.boxcast.core.data.UserPreferencesRepository
) : AndroidViewModel(application) {

    private val database = cx.aswin.boxcast.core.data.database.BoxLoreDatabase.getDatabase(application)

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
                    val isPlaybackLoading = isSameEpisode && playerState.isLoading
                    
                    // If playing this episode, we can also sync the progress in real-time
                    val resumePos = if (isSameEpisode) playerState.position else currentState.resumePositionMs
                    
                    if (
                        currentState.isPlaying != isPlaying ||
                        currentState.isPlaybackLoading != isPlaybackLoading ||
                        (isSameEpisode && currentState.resumePositionMs != resumePos)
                    ) {
                        _uiState.value = currentState.copy(
                            isPlaying = isPlaying,
                            isPlaybackLoading = isPlaybackLoading,
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
        episodeTitle: String = "",
        episodeDescription: String = "",
        episodeImageUrl: String = "",
        episodeAudioUrl: String = "",
        episodeDuration: Int = 0,
        podcastId: String = "",
        podcastTitle: String = "",
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
                val repository = cx.aswin.boxcast.core.data.PodcastRepository(apiBaseUrl, publicKey, getApplication())
                
                var finalPodcastId = podcastId
                var finalPodcastTitle = podcastTitle
                var finalEpisodeTitle = episodeTitle
                var finalEpisodeImageUrl = episodeImageUrl
                var finalEpisodeDescription = episodeDescription
                var finalEpisodeAudioUrl = episodeAudioUrl
                var finalEpisodeDuration = episodeDuration
                
                var currentEpisode: Episode? = null

                // If essential parameters are missing, resolve them from local DB first, then network
                val localDownload = database.downloadedEpisodeDao().getDownload(episodeId)
                if (localDownload != null) {
                    if (finalPodcastId.isEmpty()) finalPodcastId = localDownload.podcastId
                    if (finalPodcastTitle.isEmpty() || finalPodcastTitle == "Podcast") finalPodcastTitle = localDownload.podcastName
                    if (finalEpisodeTitle.isEmpty()) finalEpisodeTitle = localDownload.episodeTitle
                    if (finalEpisodeImageUrl.isEmpty()) finalEpisodeImageUrl = localDownload.episodeImageUrl ?: ""
                    if (finalEpisodeDescription.isEmpty()) finalEpisodeDescription = localDownload.episodeDescription ?: ""
                    if (finalEpisodeAudioUrl.isEmpty()) finalEpisodeAudioUrl = localDownload.localFilePath
                    if (finalEpisodeDuration == 0) finalEpisodeDuration = (localDownload.durationMs / 1000L).toInt()
                }

                val localHistory = database.listeningHistoryDao().getHistoryItem(episodeId)
                if (localHistory != null) {
                    if (finalPodcastId.isEmpty()) finalPodcastId = localHistory.podcastId
                    if (finalPodcastTitle.isEmpty() || finalPodcastTitle == "Podcast") finalPodcastTitle = localHistory.podcastName
                    if (finalEpisodeTitle.isEmpty()) finalEpisodeTitle = localHistory.episodeTitle
                    if (finalEpisodeImageUrl.isEmpty()) finalEpisodeImageUrl = localHistory.episodeImageUrl ?: ""
                    if (finalEpisodeDescription.isEmpty()) finalEpisodeDescription = localHistory.episodeDescription ?: ""
                    if (finalEpisodeAudioUrl.isEmpty()) finalEpisodeAudioUrl = localHistory.episodeAudioUrl ?: ""
                    if (finalEpisodeDuration == 0) finalEpisodeDuration = (localHistory.durationMs / 1000L).toInt()
                }

                if (finalPodcastId.isEmpty() || finalEpisodeTitle.isEmpty() || finalEpisodeAudioUrl.isEmpty()) {
                    val fullEpisode = repository.getEpisode(episodeId)
                    if (fullEpisode != null) {
                        currentEpisode = fullEpisode
                        if (finalPodcastId.isEmpty()) finalPodcastId = fullEpisode.podcastId ?: "unknown"
                        if (finalPodcastTitle.isEmpty() || finalPodcastTitle == "Podcast") finalPodcastTitle = fullEpisode.podcastTitle ?: "Podcast"
                        if (finalEpisodeTitle.isEmpty()) finalEpisodeTitle = fullEpisode.title
                        if (finalEpisodeImageUrl.isEmpty()) finalEpisodeImageUrl = fullEpisode.imageUrl ?: ""
                        if (finalEpisodeDescription.isEmpty()) finalEpisodeDescription = fullEpisode.description
                        if (finalEpisodeAudioUrl.isEmpty()) finalEpisodeAudioUrl = fullEpisode.audioUrl
                        if (finalEpisodeDuration == 0) finalEpisodeDuration = fullEpisode.duration
                    }
                }

                if (currentEpisode == null) {
                    currentEpisode = Episode(
                        id = episodeId,
                        title = finalEpisodeTitle,
                        description = finalEpisodeDescription,
                        imageUrl = finalEpisodeImageUrl,
                        audioUrl = finalEpisodeAudioUrl,
                        duration = finalEpisodeDuration,
                        publishedDate = 0L
                    )
                }

                // Check for resume position immediately
                val resumeSession = playbackRepository.getSession(episodeId)
                val resumeMs = resumeSession?.positionMs ?: 0L
                val durationMs = resumeSession?.durationMs ?: (finalEpisodeDuration * 1000L)
                
                // Fetch local podcast if exists for immediate metadata
                val localPodcast = if (finalPodcastId.isNotEmpty()) database.podcastDao().getPodcast(finalPodcastId) else null
                val initialLocation = localPodcast?.location
                val initialLicense = localPodcast?.license

                if (localPodcast != null && (finalPodcastTitle.isEmpty() || finalPodcastTitle == "Podcast")) {
                    finalPodcastTitle = localPodcast.title
                }

                // If finalPodcastTitle is empty/generic, try to fetch podcast details from network
                if (finalPodcastId.isNotEmpty() && (finalPodcastTitle.isEmpty() || finalPodcastTitle == "Podcast")) {
                    val podcast = repository.getPodcastDetails(finalPodcastId)
                    if (podcast != null) {
                        finalPodcastTitle = podcast.title
                    }
                }

                _uiState.value = EpisodeInfoUiState.Success(
                    episode = currentEpisode,
                    podcastId = finalPodcastId,
                    podcastTitle = finalPodcastTitle,
                    resumePositionMs = resumeMs,
                    durationMs = durationMs,
                    location = initialLocation,
                    license = initialLicense
                )

                // Detect immediately on partial title (triggers search request concurrently)
                detectCrossPromotion(currentEpisode, finalPodcastTitle)
                
                // Track Screen View
                val props = mutableMapOf<String, Any>().apply {
                    put("podcast_id", finalPodcastId)
                    put("podcast_name", finalPodcastTitle)
                    put("episode_id", episodeId)
                    put("episode_name", finalEpisodeTitle)
                    put("is_partially_played", resumeMs > 0)
                    if (entryPointContext != null) {
                        entryPointContext.getString("entry_point")?.let {
                            sourceEntryPoint = it
                            put("source_entry_point", it)
                        }
                    }
                }
                com.posthog.PostHog.capture("episode_info_screen_viewed", properties = props)
                
                // 2. Fetch full details if we haven't already
                if (finalEpisodeDescription.isEmpty()) {
                    val fullEpisode = repository.getEpisode(episodeId)
                    if (fullEpisode != null) {
                        val netImage = fullEpisode.imageUrl
                        currentEpisode = fullEpisode.copy(
                            imageUrl = if (!netImage.isNullOrEmpty()) netImage else finalEpisodeImageUrl
                        )
                        
                        val existingState = _uiState.value as? EpisodeInfoUiState.Success
                        _uiState.value = existingState?.copy(
                            episode = currentEpisode,
                            podcastId = finalPodcastId,
                            podcastTitle = finalPodcastTitle,
                        ) ?: EpisodeInfoUiState.Success(
                            episode = currentEpisode,
                            podcastId = finalPodcastId,
                            podcastTitle = finalPodcastTitle,
                            resumePositionMs = resumeMs,
                            durationMs = durationMs,
                            location = initialLocation,
                            license = initialLicense,
                        )

                        detectCrossPromotion(currentEpisode, finalPodcastTitle)
                    }
                } else {
                    // Fetch network episode anyway to ensure we have any extra metadata
                    val fullEpisode = repository.getEpisode(episodeId)
                    if (fullEpisode != null) {
                        val netImage = fullEpisode.imageUrl
                        currentEpisode = fullEpisode.copy(
                            imageUrl = if (!netImage.isNullOrEmpty()) netImage else finalEpisodeImageUrl
                        )
                        val existingState = _uiState.value as? EpisodeInfoUiState.Success
                        _uiState.value = existingState?.copy(episode = currentEpisode) ?: EpisodeInfoUiState.Success(
                            episode = currentEpisode,
                            podcastId = finalPodcastId,
                            podcastTitle = finalPodcastTitle,
                            resumePositionMs = resumeMs,
                            durationMs = durationMs,
                            location = existingState?.location ?: initialLocation,
                            license = existingState?.license ?: initialLicense
                        )
                    }
                }

                loadRelatedAndSimilar(episodeId, finalPodcastId, finalEpisodeTitle, finalPodcastTitle, finalEpisodeDescription)
                
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep showing partial data if success was already emitted?
                if (_uiState.value is EpisodeInfoUiState.Loading) {
                    _uiState.value = EpisodeInfoUiState.Error
                }
            }
        }
    }

    private fun loadRelatedAndSimilar(
        episodeId: String,
        podcastId: String,
        episodeTitle: String,
        podcastTitle: String,
        episodeDescription: String
    ) {
        if (podcastId.isEmpty() || podcastId == "unknown") return
        
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
                        podcastGenre = genre.ifEmpty { currentSuccess.podcastGenre },
                        location = podcast?.location ?: currentSuccess.location,
                        license = podcast?.license ?: currentSuccess.license
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

        // 4. Fetch similar episodes (contextual "More like this") from Qdrant Cloud via proxy
        viewModelScope.launch {
            try {
                android.util.Log.d("EpisodeInfo", "Fetching similar episodes for episodeId: $episodeId, title: $episodeTitle")
                val repository = cx.aswin.boxcast.core.data.PodcastRepository(apiBaseUrl, publicKey, getApplication())
                
                // Fetch the podcast details to get categories and author
                val podcast = repository.getPodcastDetails(podcastId)
                val categories = podcast?.genre ?: ""
                val author = podcast?.artist ?: ""

                val region = userPrefs.regionStream.first().takeIf { it.isNotBlank() } ?: "us"
                val similarEps = repository.getSimilarEpisodes(
                    episodeId = episodeId,
                    podcastId = podcastId,
                    title = episodeTitle,
                    description = episodeDescription,
                    podcastTitle = podcastTitle,
                    categories = categories,
                    author = author,
                    limit = 10,
                    country = region
                )

                android.util.Log.d("EpisodeInfo", "Fetched ${similarEps.size} similar episodes")

                val currentSuccess = _uiState.value as? EpisodeInfoUiState.Success
                if (currentSuccess != null && currentSuccess.episode.id == episodeId) {
                    _uiState.value = currentSuccess.copy(
                        similarEpisodes = similarEps,
                        similarEpisodesLoading = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("EpisodeInfo", "Error fetching similar episodes", e)
                val currentSuccess = _uiState.value as? EpisodeInfoUiState.Success
                if (currentSuccess != null) {
                    _uiState.value = currentSuccess.copy(similarEpisodesLoading = false)
                }
            }
        }
    }

    fun toggleDownload(episode: Episode) {
        didDownload = true
        val currentState = _uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            viewModelScope.launch {
                // Check if already downloaded or currently downloading
                val isDownloaded = downloadRepository.isDownloaded(episode.id).first()
                val isDownloading = downloadRepository.isDownloading(episode.id).first()
                if (isDownloaded || isDownloading) {
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


    fun seekToPosition(positionMs: Long) {
        val currentState = uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            val globalState = playbackRepository.playerState.value
            if (globalState.currentEpisode?.id == currentState.episode.id) {
                playbackRepository.seekTo(positionMs, play = true)
            } else {
                viewModelScope.launch {
                    playbackRepository.savePlaybackState(
                        podcastId = currentState.podcastId,
                        episodeId = currentState.episode.id,
                        positionMs = positionMs,
                        durationMs = currentState.durationMs,
                        episodeTitle = currentState.episode.title,
                        episodeImageUrl = currentState.episode.imageUrl,
                        podcastImageUrl = currentState.episode.podcastImageUrl,
                        episodeAudioUrl = currentState.episode.audioUrl,
                        podcastName = currentState.podcastTitle,
                        isCompleted = false,
                        isLiked = likedEpisodeIds.value.contains(currentState.episode.id),
                        enclosureType = currentState.episode.enclosureType,
                        episodeDescription = currentState.episode.description
                    )
                    val pod = cx.aswin.boxcast.core.model.Podcast(
                        id = currentState.podcastId,
                        title = currentState.podcastTitle,
                        artist = "",
                        imageUrl = currentState.episode.podcastImageUrl ?: "",
                        description = "",
                        genre = currentState.podcastGenre
                    )
                    queueManager.playEpisode(currentState.episode, pod)
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
        .distinctUntilChanged()
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
            put("podcast_name", currentState.podcastTitle)
            put("episode_id", currentState.episode.id)
            put("episode_name", currentState.episode.title)
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

    private fun detectCrossPromotion(episode: Episode, hostPodcastTitle: String) {
        viewModelScope.launch {
            try {
                val currentSuccess = _uiState.value as? EpisodeInfoUiState.Success
                if (currentSuccess != null) {
                    _uiState.value = currentSuccess.copy(crossPromoLoading = true)
                }

                val detector = cx.aswin.boxcast.core.data.crosspromo.CrossPromotionDetector()
                val result = detector.detect(episode, hostPodcastTitle)
                val extractedName = result.extractedShowName

                if (result.isCrossPromotion && extractedName != null) {
                    android.util.Log.d("EpisodeInfo", "Cross promotion detected: $extractedName")
                    val repository = cx.aswin.boxcast.core.data.PodcastRepository(apiBaseUrl, publicKey, getApplication())
                    val resolver = cx.aswin.boxcast.core.data.crosspromo.CrossPromotionResolver(repository)
                    val targetPodcast = resolver.resolve(extractedName)

                    val finalSuccess = _uiState.value as? EpisodeInfoUiState.Success
                    if (finalSuccess != null && finalSuccess.episode.id == episode.id) {
                        _uiState.value = finalSuccess.copy(
                            crossPromoLoading = false,
                            crossPromotion = cx.aswin.boxcast.core.model.ResolvedCrossPromotion(
                                extractedShowName = extractedName,
                                confidence = result.confidence,
                                targetPodcast = targetPodcast,
                                matchedIndicators = result.matchedIndicators
                            )
                        )
                    }
                } else {
                    val finalSuccess = _uiState.value as? EpisodeInfoUiState.Success
                    if (finalSuccess != null && finalSuccess.episode.id == episode.id) {
                        _uiState.value = finalSuccess.copy(
                            crossPromoLoading = false,
                            crossPromotion = null
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EpisodeInfo", "Error detecting cross promotion", e)
                val finalSuccess = _uiState.value as? EpisodeInfoUiState.Success
                if (finalSuccess != null && finalSuccess.episode.id == episode.id) {
                    _uiState.value = finalSuccess.copy(crossPromoLoading = false)
                }
            }
        }
    }
}

