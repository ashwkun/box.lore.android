package cx.aswin.boxcast.core.data

import android.util.Log

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.core.data.service.BoxCastPlaybackService
import cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

data class PlaybackSession(
    val podcastId: String,
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long,
    // Cached Metadata
    val episodeTitle: String,
    val podcastTitle: String,
    val imageUrl: String?, // Primary (Episode) Art
    val podcastImageUrl: String?, // Fallback (Podcast) Art
    val audioUrl: String?,
    val enclosureType: String? = null
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val position: Long = 0L,
    val bufferedPosition: Long = 0L,
    val currentEpisode: Episode? = null,
    val currentPodcast: Podcast? = null,
    val isLoading: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerEnd: Long? = null,
    val sleepAtEndOfEpisode: Boolean = false, // Dynamic mode: sleep when episode ends
    val queue: List<Episode> = emptyList(),
    val isLiked: Boolean = false,
    val showLateNightNudge: Boolean = false,
    val currentChapters: List<cx.aswin.boxcast.core.model.Chapter> = emptyList(),
    val isChaptersLoading: Boolean = false,
    val currentTranscript: List<TranscriptSegment> = emptyList(),
    val autoTranscriptState: AutoTranscriptState = AutoTranscriptState.NONE,
    val autoChaptersState: AutoTranscriptState = AutoTranscriptState.NONE,
    val autoTranscriptLimitLeft: Int? = null
)

class PlaybackRepository(
    private val context: Context,
    private val listeningHistoryDao: cx.aswin.boxcast.core.data.database.ListeningHistoryDao,
    private val queueRepository: cx.aswin.boxcast.core.data.QueueRepository,
    private val podcastRepository: PodcastRepository
) {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    val controller: MediaController? get() = mediaController
    
    private val _playerState = kotlinx.coroutines.flow.MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()
    
    // Preferences for session state
    private val prefs = context.getSharedPreferences("boxcast_player", Context.MODE_PRIVATE)
    private val KEY_PLAYER_DISMISSED = "player_dismissed"

    fun getOrCreateDeviceUuid(): String {
        val key = "device_uuid"
        var uuid = prefs.getString(key, null)
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(key, uuid).apply()
        }
        android.util.Log.d("BoxCastDeviceUuid", "Your physical Device UUID is: $uuid")
        return uuid
    }
    
    // Scope for progress updates
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var likeStateObserverJob: Job? = null
    
    // Queue auto-refill callback - set by QueueManager
    private val QUEUE_REFILL_THRESHOLD = 3
    private val QUEUE_MAX_SIZE = 50
    var queueRefillCallback: ((currentEpisode: Episode, podcast: Podcast) -> Unit)? = null

    init {
        getOrCreateDeviceUuid()
        initializeMediaController()
        monitorLikeState()
        monitorChaptersAndTranscripts()
    }

    private var chaptersAndTranscriptFetchJob: Job? = null
    private var autoTranscriptGenerationJob: Job? = null
    private var lastLoadedEpisodeId: String? = null

    private fun monitorChaptersAndTranscripts() {
        repositoryScope.launch {
            playerState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collect { episodeId ->
                    if (episodeId == null) {
                        lastLoadedEpisodeId = null
                        chaptersAndTranscriptFetchJob?.cancel()
                        _playerState.value = _playerState.value.copy(
                            currentChapters = emptyList(),
                            isChaptersLoading = false,
                            currentTranscript = emptyList()
                        )
                    } else {
                        if (episodeId == lastLoadedEpisodeId) {
                            return@collect
                        }
                        lastLoadedEpisodeId = episodeId
                        chaptersAndTranscriptFetchJob?.cancel()
                        chaptersAndTranscriptFetchJob = launch {
                            var episode = _playerState.value.currentEpisode
                            
                            // If missing metadata, try to enrich from PodcastRepository
                            if (episode != null && (episode.chaptersUrl == null || episode.transcriptUrl == null)) {
                                try {
                                    val enriched = podcastRepository.getEpisode(episodeId)
                                    if (enriched != null && _playerState.value.currentEpisode?.id == episodeId) {
                                        val updatedEpisode = episode.copy(
                                            description = if (episode.description.isBlank()) enriched.description else episode.description,
                                            chaptersUrl = enriched.chaptersUrl,
                                            transcriptUrl = enriched.transcriptUrl,
                                            transcripts = enriched.transcripts,
                                            persons = enriched.persons,
                                            seasonNumber = enriched.seasonNumber,
                                            episodeNumber = enriched.episodeNumber,
                                            episodeType = enriched.episodeType,
                                            podcastImageUrl = episode.podcastImageUrl ?: enriched.podcastImageUrl,
                                            podcastTitle = episode.podcastTitle ?: enriched.podcastTitle,
                                            podcastArtist = episode.podcastArtist ?: enriched.podcastArtist
                                        )
                                        val updatedQueue = _playerState.value.queue.map {
                                            if (it.id == episodeId) updatedEpisode else it
                                        }
                                        _playerState.value = _playerState.value.copy(
                                            currentEpisode = updatedEpisode,
                                            queue = updatedQueue
                                        )
                                        episode = updatedEpisode
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PlaybackRepo", "Failed to enrich episode $episodeId", e)
                                }
                            }

                                val chaptersUrl = episode?.chaptersUrl
                            val transcriptUrl = episode?.transcriptUrl
                            
                            // Fetch chapters
                            if (chaptersUrl != null) {
                                _playerState.value = _playerState.value.copy(
                                    isChaptersLoading = true,
                                    currentChapters = emptyList(),
                                    autoChaptersState = AutoTranscriptState.NONE
                                )
                                launch {
                                    val chapters = ChapterRepository.getChapters(chaptersUrl)
                                    if (_playerState.value.currentEpisode?.id == episode?.id) {
                                        _playerState.value = _playerState.value.copy(
                                            currentChapters = chapters,
                                            isChaptersLoading = false
                                        )
                                    }
                                }
                            } else {
                                val autoChapters = ChapterRepository.getCachedChapters("auto_${episode?.id}") ?: emptyList()
                                _playerState.value = _playerState.value.copy(
                                    currentChapters = autoChapters,
                                    isChaptersLoading = false,
                                    autoChaptersState = if (autoChapters.isNotEmpty()) AutoTranscriptState.COMPLETED else _playerState.value.autoChaptersState
                                )
                            }
                            
                            // Fetch transcript
                            if (transcriptUrl != null) {
                                // RSS transcript available — fetch normally, no AI state
                                _playerState.value = _playerState.value.copy(autoTranscriptState = AutoTranscriptState.NONE)
                                launch {
                                    val transcript = TranscriptRepository.getTranscript(transcriptUrl)
                                    if (_playerState.value.currentEpisode?.id == episodeId) {
                                        _playerState.value = _playerState.value.copy(currentTranscript = transcript)
                                    }
                                }

                                // But if chaptersUrl is empty, check if we have auto chapters in Turso
                                if (chaptersUrl.isNullOrEmpty() && episode != null && episode.audioUrl.isNotEmpty()) {
                                    launch {
                                        val deviceUuid = getOrCreateDeviceUuid()
                                        val response = TranscriptRepository.checkAutoTranscriptStatus(
                                            api = podcastRepository.api,
                                            publicKey = podcastRepository.publicKey,
                                            deviceUuid = deviceUuid,
                                            episodeId = episodeId,
                                            audioUrl = episode.audioUrl,
                                            transcriptUrl = episode.transcriptUrl
                                        )
                                        if (_playerState.value.currentEpisode?.id != episodeId) return@launch
                                        
                                        val status = response?.status
                                        val limitLeft = response?.limitLeft
                                        val chapters = response?.chapters
                                        
                                        _playerState.value = _playerState.value.copy(
                                            autoTranscriptLimitLeft = limitLeft
                                        )
                                        
                                        if (chapters != null) {
                                            _playerState.value = _playerState.value.copy(
                                                currentChapters = chapters,
                                                autoChaptersState = if (chapters.isNotEmpty()) AutoTranscriptState.COMPLETED else _playerState.value.autoChaptersState
                                            )
                                        }
                                        
                                        when (status) {
                                            "completed" -> {
                                                _playerState.value = _playerState.value.copy(
                                                    autoChaptersState = AutoTranscriptState.COMPLETED
                                                )
                                            }
                                            "pending", "uploaded" -> {
                                                _playerState.value = _playerState.value.copy(
                                                    autoChaptersState = AutoTranscriptState.GENERATING
                                                )
                                                startAutoTranscriptGeneration(episodeId, episode.audioUrl, episode.transcriptUrl, isTranscriptRequested = false)
                                            }
                                            "failed" -> {
                                                _playerState.value = _playerState.value.copy(
                                                    autoChaptersState = AutoTranscriptState.FAILED
                                                )
                                            }
                                            else -> {
                                                _playerState.value = _playerState.value.copy(
                                                    autoChaptersState = if (_playerState.value.currentChapters.isEmpty()) AutoTranscriptState.NOT_GENERATED else _playerState.value.autoChaptersState
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (episode != null && episode.audioUrl.isNotEmpty()) {
                                // No RSS transcript — check auto-transcript status
                                _playerState.value = _playerState.value.copy(
                                    autoTranscriptState = AutoTranscriptState.CHECKING,
                                    currentTranscript = emptyList()
                                )
                                launch {
                                     val deviceUuid = getOrCreateDeviceUuid()
                                     val response = TranscriptRepository.checkAutoTranscriptStatus(
                                         api = podcastRepository.api,
                                         publicKey = podcastRepository.publicKey,
                                         deviceUuid = deviceUuid,
                                         episodeId = episodeId,
                                         audioUrl = episode.audioUrl,
                                         transcriptUrl = episode.transcriptUrl
                                     )
                                     if (_playerState.value.currentEpisode?.id != episodeId) return@launch
 
                                     val status = response?.status
                                     val limitLeft = response?.limitLeft
                                     val chapters = response?.chapters
                                     
                                     _playerState.value = _playerState.value.copy(
                                         autoTranscriptLimitLeft = limitLeft
                                     )
                                     
                                     if (chapters != null && _playerState.value.currentEpisode?.id == episodeId) {
                                         _playerState.value = _playerState.value.copy(
                                             currentChapters = chapters,
                                             autoChaptersState = if (chapters.isNotEmpty()) AutoTranscriptState.COMPLETED else _playerState.value.autoChaptersState
                                         )
                                     }
 
                                     when (status) {
                                         "completed" -> {
                                             // Transcript exists — fetch the full SRT
                                             _playerState.value = _playerState.value.copy(
                                                 autoTranscriptState = AutoTranscriptState.COMPLETED
                                             )
                                             val transcript = TranscriptRepository.getAutoTranscript(
                                                 api = podcastRepository.api,
                                                 publicKey = podcastRepository.publicKey,
                                                 deviceUuid = deviceUuid,
                                                 episodeId = episodeId,
                                                 audioUrl = episode.audioUrl,
                                                 transcriptUrl = episode.transcriptUrl
                                             )
                                             if (_playerState.value.currentEpisode?.id == episodeId) {
                                                 val autoChapters = ChapterRepository.getCachedChapters("auto_$episodeId") ?: emptyList()
                                                 _playerState.value = _playerState.value.copy(
                                                     currentTranscript = transcript,
                                                     currentChapters = if (autoChapters.isNotEmpty()) autoChapters else _playerState.value.currentChapters,
                                                     autoChaptersState = if (autoChapters.isNotEmpty()) AutoTranscriptState.COMPLETED else _playerState.value.autoChaptersState
                                                 )
                                             }
                                         }
                                         "pending", "uploaded" -> {
                                             // Already in progress — start polling
                                             val wasTranscriptGenerating = _playerState.value.autoTranscriptState == AutoTranscriptState.GENERATING
                                             val wasChaptersGenerating = _playerState.value.autoChaptersState == AutoTranscriptState.GENERATING
                                             
                                             val nextTranscriptState = if (wasTranscriptGenerating) {
                                                 AutoTranscriptState.GENERATING
                                             } else {
                                                 _playerState.value.autoTranscriptState
                                             }
                                             val nextChaptersState = if (wasChaptersGenerating) {
                                                 AutoTranscriptState.GENERATING
                                             } else {
                                                 _playerState.value.autoChaptersState
                                             }

                                             _playerState.value = _playerState.value.copy(
                                                 autoTranscriptState = nextTranscriptState,
                                                 autoChaptersState = nextChaptersState
                                             )
                                             startAutoTranscriptGeneration(episodeId, episode.audioUrl, episode.transcriptUrl, isTranscriptRequested = wasTranscriptGenerating)
                                         }
                                         "failed" -> {
                                             _playerState.value = _playerState.value.copy(
                                                 autoTranscriptState = AutoTranscriptState.FAILED
                                             )
                                         }
                                         else -> {
                                             // "not_started" or null — eligible for generation
                                             _playerState.value = _playerState.value.copy(
                                                 autoTranscriptState = AutoTranscriptState.NOT_GENERATED,
                                                 autoChaptersState = if (_playerState.value.currentChapters.isEmpty()) AutoTranscriptState.NOT_GENERATED else _playerState.value.autoChaptersState
                                             )
                                         }
                                     }
                                 }
                            } else {
                                _playerState.value = _playerState.value.copy(
                                    currentTranscript = emptyList(),
                                    autoTranscriptState = AutoTranscriptState.NONE
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * Called from the UI when the user confirms transcript generation.
     * Transitions to GENERATING and kicks off the actual API call.
     */
    fun generateAutoTranscript() {
        val episode = _playerState.value.currentEpisode ?: return
        if (episode.audioUrl.isEmpty()) return
        val episodeId = episode.id

        val isChaptersEmpty = _playerState.value.currentChapters.isEmpty()
        _playerState.value = _playerState.value.copy(
            autoTranscriptState = AutoTranscriptState.GENERATING,
            autoChaptersState = if (isChaptersEmpty) AutoTranscriptState.GENERATING else _playerState.value.autoChaptersState
        )
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoTranscriptRequested(episodeId, episode.podcastId, episode.audioUrl)
        if (isChaptersEmpty) {
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoChaptersRequested(episodeId, episode.podcastId, episode.audioUrl)
        }
        startAutoTranscriptGeneration(episodeId, episode.audioUrl, episode.transcriptUrl, isTranscriptRequested = true)
    }

    /**
     * Called from the UI when the user clicks "Generate AI Chapters".
     * Sets only autoChaptersState to GENERATING (not transcript state).
     */
    fun generateAutoChapters() {
        val episode = _playerState.value.currentEpisode ?: return
        if (episode.audioUrl.isEmpty()) return
        val episodeId = episode.id

        _playerState.value = _playerState.value.copy(
            autoChaptersState = AutoTranscriptState.GENERATING
        )
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoChaptersRequested(episodeId, episode.podcastId, episode.audioUrl)
        startAutoTranscriptGeneration(episodeId, episode.audioUrl, episode.transcriptUrl, isTranscriptRequested = false)
    }

    /**
     * Starts the background polling/streaming call to generate and retrieve
     * the auto-transcript. Cancels any previous generation job.
     */
    private fun startAutoTranscriptGeneration(episodeId: String, audioUrl: String, transcriptUrl: String?, isTranscriptRequested: Boolean) {
        val deviceUuid = getOrCreateDeviceUuid()
        autoTranscriptGenerationJob?.cancel()
        autoTranscriptGenerationJob = repositoryScope.launch {
            try {
                val transcript = TranscriptRepository.getAutoTranscript(
                    api = podcastRepository.api,
                    publicKey = podcastRepository.publicKey,
                    deviceUuid = deviceUuid,
                    episodeId = episodeId,
                    audioUrl = audioUrl,
                    transcriptUrl = transcriptUrl
                )
                val currentEp = _playerState.value.currentEpisode
                if (currentEp?.id == episodeId) {
                    if (transcript.isNotEmpty()) {
                        val autoChapters = ChapterRepository.getCachedChapters("auto_$episodeId") ?: emptyList()
                        _playerState.value = _playerState.value.copy(
                            currentTranscript = transcript,
                            currentChapters = if (autoChapters.isNotEmpty()) autoChapters else _playerState.value.currentChapters,
                            autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.COMPLETED else _playerState.value.autoTranscriptState,
                            autoChaptersState = if (autoChapters.isNotEmpty()) AutoTranscriptState.COMPLETED else AutoTranscriptState.FAILED
                        )
                        if (isTranscriptRequested) {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoTranscriptCompleted(
                                episodeId, currentEp.podcastId, currentEp.duration.toFloat(), transcript.size
                            )
                        }
                        if (autoChapters.isNotEmpty()) {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoChaptersCompleted(
                                episodeId, currentEp.podcastId, currentEp.duration.toFloat(), autoChapters.size
                            )
                        } else {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                                episodeId, currentEp.podcastId, "Chapters empty or generation failed"
                            )
                        }
                    } else {
                        _playerState.value = _playerState.value.copy(
                            autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.FAILED else _playerState.value.autoTranscriptState,
                            autoChaptersState = AutoTranscriptState.FAILED
                        )
                        if (isTranscriptRequested) {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoTranscriptFailed(
                                episodeId, currentEp.podcastId, "Transcript empty or generation failed"
                            )
                        }
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                            episodeId, currentEp.podcastId, "Transcript empty or generation failed (required for chapters)"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackRepo", "Auto-transcript generation failed for $episodeId", e)
                val currentEp = _playerState.value.currentEpisode
                if (currentEp?.id == episodeId) {
                    _playerState.value = _playerState.value.copy(
                        autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.FAILED else _playerState.value.autoTranscriptState,
                        autoChaptersState = AutoTranscriptState.FAILED
                    )
                    if (isTranscriptRequested) {
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoTranscriptFailed(
                            episodeId, currentEp.podcastId, e.message ?: "Unknown error"
                        )
                    }
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                        episodeId, currentEp.podcastId, e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    private fun monitorLikeState() {
        repositoryScope.launch {
            playerState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collect { episodeId ->
                    likeStateObserverJob?.cancel()
                    if (episodeId != null) {
                        likeStateObserverJob = launch {
                            listeningHistoryDao.getHistoryItemFlow(episodeId).collect { history ->
                                if (history != null) {
                                    if (_playerState.value.isLiked != history.isLiked) {
                                        _playerState.value = _playerState.value.copy(isLiked = history.isLiked)
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, BoxCastPlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            mediaController?.addListener(object : androidx.media3.common.Player.Listener {
                private var pendingSaveJob: Job? = null

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d("PlaybackRepo", "onIsPlayingChanged: isPlaying=$isPlaying, currentPos=${mediaController?.currentPosition}")
                    val oldIsPlaying = _playerState.value.isPlaying
                    _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                    if (isPlaying) {
                        pendingSaveJob?.cancel() // Cancel pending save if we resume
                        if (!oldIsPlaying) {
                            activePlaybackStartTimeMs = System.currentTimeMillis()
                        }
                        startProgressTicker()
                    } else {
                        stopProgressTicker()
                        val hasBeenPlayingFor10s = activePlaybackStartTimeMs > 0 && 
                                (System.currentTimeMillis() - activePlaybackStartTimeMs >= 10_000)
                        pendingSaveJob?.cancel()
                        pendingSaveJob = repositoryScope.launch { 
                            saveCurrentState(updateLastPlayedAt = hasBeenPlayingFor10s) 
                        }
                        activePlaybackStartTimeMs = 0L
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val isLoading = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                    _playerState.value = _playerState.value.copy(isLoading = isLoading)
                    
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        Log.d("PlaybackRepo", "Playback ENDED. Cancelling pending saves and marking completed.")
                        pendingSaveJob?.cancel() // CRITICAL: Prevent "Pause" save from overwriting completion
                        
                        // Sleep Timer: End of Episode — stop everything if EOE is active
                        if (_playerState.value.sleepAtEndOfEpisode) {
                            Log.d("PlaybackRepo", "Sleep Timer (EOE): Episode ended, stopping playback.")
                            mediaController?.stop()
                            mediaController?.clearMediaItems()
                            sleepTimerJob?.cancel()
                            stopProgressTicker()
                            _playerState.value = _playerState.value.copy(
                                isPlaying = false, position = 0,
                                sleepTimerEnd = null, sleepAtEndOfEpisode = false
                            )
                            repositoryScope.launch { markCurrentEpisodeAsCompleted() }
                            return
                        }
                        
                        _playerState.value = _playerState.value.copy(isPlaying = false, position = 0)
                        stopProgressTicker()
                        // Mark as completed
                        repositoryScope.launch {
                            markCurrentEpisodeAsCompleted()
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("PlaybackRepo", "Player Error: ${error.message}", error)
                    val controller = mediaController ?: return
                    val queue = _playerState.value.queue
                    val failedEpisode = _playerState.value.currentEpisode
                    
                    // Try to skip the bad item instead of nuking the entire queue
                    val currentIndex = controller.currentMediaItemIndex
                    val hasNext = currentIndex < controller.mediaItemCount - 1
                    
                    if (hasNext) {
                        // Remove the failed item and advance to the next one
                        Log.d("PlaybackRepo", "onPlayerError: Skipping failed item '${failedEpisode?.title}', advancing to next")
                        controller.removeMediaItem(currentIndex)
                        val newQueue = queue.filterNot { it.id == failedEpisode?.id }
                        _playerState.value = _playerState.value.copy(
                            queue = newQueue,
                            isLoading = true
                        )
                        controller.prepare()
                        controller.play()
                    } else {
                        // No more items — clear everything
                        Log.d("PlaybackRepo", "onPlayerError: No more items in queue, clearing.")
                        controller.stop()
                        controller.clearMediaItems()
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false, 
                            isLoading = false,
                            currentEpisode = null,
                            queue = emptyList()
                        )
                    }
                    
                    repositoryScope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Stream unavailable, skipping...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    if (mediaController?.isPlaying == true) {
                        activePlaybackStartTimeMs = System.currentTimeMillis()
                    } else {
                        activePlaybackStartTimeMs = 0L
                    }
                    // Use mediaId to find episode — more reliable than index
                    val episodeId = mediaItem?.mediaId ?: return
                    val queue = _playerState.value.queue
                    val oldState = _playerState.value
                    
                    val slotIndex = queue.indexOfFirst { it.id == episodeId }
                    android.util.Log.d("PlaybackRepo", "onMediaItemTransition: mediaId=$episodeId, slotIndex=$slotIndex, queueSize=${queue.size}, reason=$reason")
                    
                    // Sleep Timer: End of Episode — intercept auto-advance
                    if (oldState.sleepAtEndOfEpisode && reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        Log.d("PlaybackRepo", "Sleep Timer (EOE): Auto-advance intercepted. Pausing playback.")
                        mediaController?.pause()
                        sleepTimerJob?.cancel()
                        stopProgressTicker()
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            sleepTimerEnd = null, sleepAtEndOfEpisode = false
                        )
                        // Mark previous episode as completed
                        val prevEpisode = oldState.currentEpisode
                        val prevPodcast = oldState.currentPodcast
                        if (prevEpisode != null) {
                            repositoryScope.launch { markEpisodeAsCompleted(prevEpisode, prevPodcast) }
                        }
                        return
                    }
                    
                    // Launch a single coroutine to handle the transition logic sequentially
                    // This ensures DB updates finish BEFORE we trigger SmartQueue refill
                    repositoryScope.launch {
                        val finalQueue: List<Episode>
                        val finalSlotIndex: Int
                        
                        if (slotIndex == -1) {
                            android.util.Log.w("PlaybackRepo", "onMediaItemTransition: Episode $episodeId NOT found in local queue. Attempting recovery from DB...")
                            val dbQueue = queueRepository.getQueueSnapshot()
                            val dbSlotIndex = dbQueue.indexOfFirst { it.id == episodeId }
                            android.util.Log.d("PlaybackRepo", "onMediaItemTransition (Recovery): dbSlotIndex=$dbSlotIndex, dbQueueSize=${dbQueue.size}")
                            
                            val resolvedEpisode = if (dbSlotIndex != -1) {
                                dbQueue[dbSlotIndex]
                            } else {
                                // Fallback: construct Episode directly from mediaItem's MediaMetadata
                                val metadata = mediaItem.mediaMetadata
                                val resolvedPodcastId = findPodcastIdForEpisode(episodeId) ?: ""
                                Episode(
                                    id = episodeId,
                                    title = metadata.title?.toString() ?: "Unknown Episode",
                                    description = "",
                                    audioUrl = mediaItem.localConfiguration?.uri?.toString() ?: "",
                                    imageUrl = metadata.artworkUri?.toString(),
                                    podcastImageUrl = metadata.artworkUri?.toString(),
                                    podcastTitle = metadata.subtitle?.toString() ?: metadata.artist?.toString() ?: "Unknown Podcast",
                                    podcastId = resolvedPodcastId,
                                    podcastGenre = metadata.genre?.toString() ?: "Podcast",
                                    podcastArtist = metadata.artist?.toString() ?: "",
                                    duration = 0,
                                    publishedDate = 0L
                                )
                            }
                            
                            if (dbSlotIndex != -1) {
                                finalQueue = dbQueue
                                finalSlotIndex = dbSlotIndex
                            } else {
                                val mutable = queue.toMutableList()
                                if (mutable.none { it.id == episodeId }) {
                                    mutable.add(resolvedEpisode)
                                }
                                finalQueue = mutable.toList()
                                finalSlotIndex = finalQueue.indexOfFirst { it.id == episodeId }.coerceAtLeast(0)
                            }
                        } else {
                            finalQueue = queue
                            finalSlotIndex = slotIndex
                        }
                        
                        val newEpisode = finalQueue[finalSlotIndex]
                        android.util.Log.d("PlaybackRepo", "Media transition to: ${newEpisode.title}")
                        
                        // 1. Mark PREVIOUS episode as completed (if distinct)
                        val previousEpisode = oldState.currentEpisode
                        val previousPodcast = oldState.currentPodcast 
                        
                        if (previousEpisode != null && previousEpisode.id != newEpisode.id) {
                            android.util.Log.d("PlaybackRepo", "Transition: Marking previous episode as COMPLETED: ${previousEpisode.title} (ID: ${previousEpisode.id})")
                            markEpisodeAsCompleted(previousEpisode, previousPodcast)
                        }
                        
                        // 2. Derive podcast context from episode metadata & local DB
                        val newPodcast = if (newEpisode.podcastId != null) {
                            val existingPod = oldState.currentPodcast
                            val database = cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(context)
                            val dbPodEntity = database.podcastDao().getPodcast(newEpisode.podcastId!!)
                            val dbPodcast = dbPodEntity?.let { entity ->
                                cx.aswin.boxcast.core.model.Podcast(
                                    id = entity.podcastId,
                                    title = entity.title,
                                    artist = entity.author ?: "",
                                    imageUrl = entity.imageUrl ?: "",
                                    fallbackImageUrl = entity.latestEpisode?.imageUrl ?: "",
                                    description = entity.description,
                                    genre = entity.genre ?: "Podcast",
                                    type = entity.type,
                                    latestEpisode = entity.latestEpisode,
                                    subscribedAt = entity.subscribedAt,
                                    podcastGuid = entity.podcastGuid,
                                    fundingUrl = entity.fundingUrl,
                                    fundingMessage = entity.fundingMessage,
                                    medium = entity.medium,
                                    hasValue = entity.hasValue,
                                    updateFrequency = entity.updateFrequency,
                                    location = entity.location,
                                    license = entity.license,
                                    isLocked = entity.isLocked,
                                    preferredSort = entity.preferredSort
                                )
                            }

                            if (dbPodcast != null && dbPodcast.title != "Unknown Podcast") {
                                dbPodcast
                            } else if (existingPod != null && existingPod.id == newEpisode.podcastId && existingPod.title != "Unknown Podcast") {
                                // Preserve the fully-populated existing podcast object
                                existingPod
                            } else {
                                cx.aswin.boxcast.core.model.Podcast(
                                    id = newEpisode.podcastId!!,
                                    title = newEpisode.podcastTitle?.takeIf { !it.isNullOrBlank() && it != "Unknown Podcast" }
                                        ?: "Unknown Podcast",
                                    artist = newEpisode.podcastArtist?.takeIf { it.isNotEmpty() } ?: existingPod?.artist ?: "",
                                    imageUrl = newEpisode.podcastImageUrl?.takeIf { it.isNotEmpty() } ?: existingPod?.imageUrl ?: "",
                                    description = null,
                                    genre = newEpisode.podcastGenre ?: existingPod?.genre ?: "Podcast"
                                )
                            }
                        } else {
                            oldState.currentPodcast
                        }

                        // 3. QUEUE CONSUMPTION: Drop items before current
                        val newQueue = finalQueue.drop(finalSlotIndex)
                        android.util.Log.d("PlaybackRepo", "Consuming queue: Dropped $finalSlotIndex items. New size: ${newQueue.size}")
                        _playerState.value = _playerState.value.copy(
                            currentEpisode = newEpisode,
                            currentPodcast = newPodcast,
                            queue = newQueue
                        )

                        // 4. Sync queue to DB for restart recovery
                        syncQueueToDb()

                        // 5. Restore saved position for previously-played episodes in queue
                        // (e.g. episodes added via SmartQueue or manually that the user already partially played)
                        if (reason != androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                            val historyItem = listeningHistoryDao.getHistoryItem(newEpisode.id)
                            if (historyItem != null && !historyItem.isCompleted && historyItem.progressMs > 2000) {
                                android.util.Log.d("PlaybackRepo", "onMediaItemTransition: Restoring saved position ${historyItem.progressMs}ms for ${newEpisode.title}")
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    mediaController?.seekTo(historyItem.progressMs)
                                }
                            }
                        }

                        // 6. Auto-refill when queue is running low
                        val currentQueueSize = _playerState.value.queue.size
                        if (currentQueueSize < QUEUE_REFILL_THRESHOLD && newPodcast != null) {
                            android.util.Log.d("PlaybackRepo", "Queue running low ($currentQueueSize items). Triggering auto-refill.")
                            queueRefillCallback?.invoke(newEpisode, newPodcast)
                        }
                    }
                }
            })
            
            // Sync state from MediaController (handles app coming back from background)
            syncStateFromMediaController()
        }, MoreExecutors.directExecutor())
    }
    
    /**
     * Sync playback state from the MediaController.
     * Called when MediaController connects (including when app comes back from background).
     */
    private fun syncStateFromMediaController() {
        val controller = mediaController ?: return
        
        val isPlaying = controller.isPlaying
        val isLoading = controller.playbackState == androidx.media3.common.Player.STATE_BUFFERING
        val currentPosition = controller.currentPosition.coerceAtLeast(0)
        val bufferedPosition = controller.bufferedPosition.coerceAtLeast(0)
        val duration = controller.duration.coerceAtLeast(0)
        val hasMedia = controller.mediaItemCount > 0
        
        if (hasMedia && _playerState.value.currentEpisode == null) {
            // MediaController has media but we don't have metadata - restore from DB
            repositoryScope.launch {
                val lastSession = listeningHistoryDao.getLastPlayedSessionAny()
                if (lastSession != null) {
                    var episode = Episode(
                        id = lastSession.episodeId,
                        title = lastSession.episodeTitle,
                        description = "",
                        audioUrl = lastSession.episodeAudioUrl ?: "",
                        imageUrl = lastSession.episodeImageUrl,
                        duration = (lastSession.durationMs / 1000).toInt(),
                        publishedDate = 0L,
                        enclosureType = lastSession.enclosureType
                    )
                    val podcast = Podcast(
                        id = lastSession.podcastId,
                        title = lastSession.podcastName,
                        artist = "",
                        imageUrl = lastSession.podcastImageUrl ?: "",
                        description = null,
                        genre = "Podcast"
                    )
                    // Enrich with P2.0 data from queue if available
                    val currentQueue = _playerState.value.queue
                    val queueEp = currentQueue.find { it.id == episode.id }
                    if (queueEp != null) {
                        episode = episode.copy(
                            chaptersUrl = queueEp.chaptersUrl,
                            transcriptUrl = queueEp.transcriptUrl,
                            persons = queueEp.persons,
                            transcripts = queueEp.transcripts
                        )
                    }
                    _playerState.value = PlayerState(
                        currentEpisode = episode,
                        currentPodcast = podcast,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        position = currentPosition,
                        bufferedPosition = bufferedPosition,
                        duration = if (duration > 0) duration else lastSession.durationMs,
                        playbackSpeed = controller.playbackParameters.speed,
                        queue = _playerState.value.queue, // Preserve queue
                        isLiked = lastSession.isLiked
                    )
                    if (isPlaying) startProgressTicker()
                }
            }
        } else {
            // Just sync playback state
            _playerState.value = _playerState.value.copy(
                isPlaying = isPlaying,
                isLoading = isLoading,
                position = if (currentPosition > 0) currentPosition else _playerState.value.position,
                bufferedPosition = bufferedPosition,
                duration = if (duration > 0) duration else _playerState.value.duration,
                playbackSpeed = controller.playbackParameters.speed
            )
            if (isPlaying) startProgressTicker()
        }
    }
    
    private var lastProgressSaveMs = 0L
    private var activePlaybackStartTimeMs = 0L
    
    private fun startProgressTicker() {
        stopProgressTicker()
        lastProgressSaveMs = System.currentTimeMillis() // Reset so first ticker save is 10s from now
        progressJob = repositoryScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    if (controller.isPlaying || controller.isLoading) {
                        val currentPos = controller.currentPosition
                        val bufferedPos = controller.bufferedPosition
                        val currentDur = controller.duration.coerceAtLeast(0)
                        
                        _playerState.value = _playerState.value.copy(
                            position = currentPos,
                            bufferedPosition = bufferedPos,
                            duration = currentDur
                        )
                        
                        // Save progress every 10 seconds (deterministic)
                        val now = System.currentTimeMillis()
                        if (now - lastProgressSaveMs > 10_000) {
                            val hasBeenPlayingFor10s = activePlaybackStartTimeMs > 0 &&
                                    (now - activePlaybackStartTimeMs >= 10_000)
                            saveCurrentState(updateLastPlayedAt = hasBeenPlayingFor10s)
                            lastProgressSaveMs = now
                        }
                    }
                }
                kotlinx.coroutines.delay(500) // Update every 500ms
            }
        }
    }
    
    // Helper to save current state
    private suspend fun saveCurrentState(updateLastPlayedAt: Boolean = true) {
        val state = _playerState.value
        val episode = state.currentEpisode ?: return
        val podcast = state.currentPodcast ?: return
        
        // Check existing completion status to avoid overwriting
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val wasCompleted = existing?.isCompleted ?: false
        val lastPlayed = if (updateLastPlayedAt) System.currentTimeMillis() else (existing?.lastPlayedAt ?: System.currentTimeMillis())
        
        val isCompletedNow = state.duration > 0 && (
            state.position >= state.duration - 5000 ||
            state.position >= state.duration * 0.95 ||
            (state.duration >= 900_000L && state.duration - state.position <= 300_000L)
        )
        val finalCompleted = wasCompleted || isCompletedNow
        val finalPosition = if (isCompletedNow && !wasCompleted) 0L else state.position

        savePlaybackState(
            podcastId = podcast.id,
            episodeId = episode.id,
            positionMs = finalPosition,
            durationMs = state.duration,
            episodeTitle = episode.title,
            episodeImageUrl = episode.imageUrl,
            podcastImageUrl = podcast.imageUrl,
            episodeAudioUrl = episode.audioUrl,
            podcastName = podcast.title,
            isCompleted = finalCompleted,
            isLiked = state.isLiked,
            lastPlayedAt = lastPlayed,
            enclosureType = episode.enclosureType
        )
    }
    
    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }
    
    /**
     * Sync the in-memory queue to the DB for restart recovery.
     */
    private suspend fun syncQueueToDb() {
        try {
            val currentQueue = _playerState.value.queue
            queueRepository.replaceQueue(currentQueue)
            android.util.Log.d("PlaybackRepo", "syncQueueToDb: Synced ${currentQueue.size} items to DB")
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "syncQueueToDb: Failed", e)
        }
    }

    suspend fun playQueue(episodes: List<Episode>, podcast: Podcast, startIndex: Int = 0, entryPointContext: android.os.Bundle? = null) {
        Log.d("PlaybackRepo", "playQueue() called: count=${episodes.size}, start=$startIndex, podcastGenre='${podcast.genre}'")
        
        prefs.edit().putBoolean(KEY_PLAYER_DISMISSED, false).apply()
        
        if (mediaController == null) {
            mediaController = mediaControllerFuture?.await()
        }
        
        mediaController?.let { controller ->
            // Optimization: If playing the same context, just seek?
            // For now, full reload ensures queue is correct.
            
            val mediaItems = episodes.map { episode ->
                 val metadata = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(episode.podcastTitle ?: podcast.title)
                    .setArtworkUri(android.net.Uri.parse(
                         episode.imageUrl?.takeIf { it.isNotBlank() } 
                         ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() } 
                         ?: podcast.imageUrl
                    ))
                    .setDisplayTitle(episode.title) // Required for notification
                    .setSubtitle(episode.podcastTitle ?: podcast.title)
                    .setGenre(episode.podcastGenre ?: podcast.genre)
                    .setExtras(entryPointContext)
                    .build()
           
                 MediaItem.Builder()
                    .setUri(episode.audioUrl)
                    .setMediaMetadata(metadata)
                    .setMediaId(episode.id) // Important for identification
                    .setCustomCacheKey(episode.id) // Match DownloadRequest custom key
                    .build()
            }
            
            // Check for saved progress and liked status in a single DB query
            var startPosMs = 0L
            var initialLikeState = false
            val startEpisodeId = episodes.getOrNull(startIndex)?.id
            if (startEpisodeId != null) {
                val saved = listeningHistoryDao.getHistoryItem(startEpisodeId)
                if (saved != null) {
                    if (!saved.isCompleted) startPosMs = saved.progressMs
                    initialLikeState = saved.isLiked
                }
            }
            
            // Update local state BEFORE setMediaItems (onMediaItemTransition reads this)
            val currentEp = episodes.getOrNull(startIndex)
            if (currentEp != null) {
                var showNudge = false
                if (isLateNight()) {
                    val lastShown = prefs.getLong("last_late_night_nudge_timestamp", 0L)
                    val now = System.currentTimeMillis()
                    // 12 hours = 43,200,000 milliseconds
                    if (now - lastShown > 43_200_000L) {
                        showNudge = true
                        prefs.edit().putLong("last_late_night_nudge_timestamp", now).apply()
                    }
                }
                
                _playerState.value = _playerState.value.copy(
                    currentEpisode = currentEp,
                    currentPodcast = podcast,
                    isPlaying = true,
                    position = startPosMs,
                    duration = currentEp.duration.toLong() * 1000,
                    queue = episodes, // Update queue BEFORE Media3 triggers callbacks
                    isLiked = initialLikeState,
                    showLateNightNudge = showNudge
                )
            }
            
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("resume")
            controller.setMediaItems(mediaItems, startIndex, startPosMs)
            controller.prepare()
            
            // Set entry point context via static holder (IPC-safe)
            if (entryPointContext != null) {
                val map = mutableMapOf<String, Any>()
                entryPointContext.keySet().forEach { key ->
                    @Suppress("DEPRECATION")
                    val value = entryPointContext.get(key)
                    if (value != null) {
                        map[key] = value
                    }
                }
                if (map.isNotEmpty()) {
                    cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(map)
                }
            }
            
            controller.play()
            
            // Sync queue to DB for restart recovery
            syncQueueToDb()
            
            // Save state immediately but do NOT update lastPlayedAt timestamp to prevent
            // instant card reordering on the home screen. The timestamp will be updated
            // after 10 seconds of active playback via the progress ticker.
            saveCurrentState(updateLastPlayedAt = false)
        }
    }

    suspend fun addToQueue(episode: Episode, podcast: Podcast) {
        Log.d("PlaybackRepo", "addToQueue called: episodeId=${episode.id}, title=${episode.title}")
        
        // Prevent Duplicates in active queue (by ID)
        if (_playerState.value.queue.any { it.id == episode.id }) {
            Log.w("PlaybackRepo", "addToQueue: Episode ${episode.title} already in active queue (ID match). Skipping.")
            return
        }
        
        // ID-based dedup is sufficient — title matching is too fragile
        // (podcasts reuse titles like "Bonus Episode", "Q&A", etc.)
        
        // Enforce queue size cap
        if (_playerState.value.queue.size >= QUEUE_MAX_SIZE) {
            Log.w("PlaybackRepo", "addToQueue: Queue at max capacity ($QUEUE_MAX_SIZE). Skipping.")
            return
        }

        if (mediaController == null) {
            Log.d("PlaybackRepo", "addToQueue: mediaController null, awaiting...")
            mediaController = mediaControllerFuture?.await()
        }
        
        mediaController?.let { controller ->
             Log.d("PlaybackRepo", "addToQueue: mediaController ready, mediaItemCount=${controller.mediaItemCount}")
             val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(podcast.title)
                .setArtworkUri(android.net.Uri.parse(episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcast.imageUrl))
                .setDisplayTitle(episode.title)
                .setSubtitle(podcast.title)
                .setGenre(episode.podcastGenre ?: podcast.genre)
                .build()
       
             val mediaItem = MediaItem.Builder()
                .setUri(episode.audioUrl)
                .setMediaMetadata(metadata)
                .setMediaId(episode.id)
                .setCustomCacheKey(episode.id) // Match DownloadRequest custom key
                .build()
                
             controller.addMediaItem(mediaItem)
             Log.d("PlaybackRepo", "addToQueue: Added to Media3, new mediaItemCount=${controller.mediaItemCount}")
             
             // Update local state
             val currentQueue = _playerState.value.queue
             _playerState.value = _playerState.value.copy(queue = currentQueue + episode)
             Log.d("PlaybackRepo", "addToQueue: Updated local state, queue size=${_playerState.value.queue.size}")
        } ?: Log.e("PlaybackRepo", "addToQueue: mediaController still NULL after await!")
     }

    suspend fun addToQueueNext(episode: Episode, podcast: Podcast) {
        if (mediaController == null) {
            mediaController = mediaControllerFuture?.await()
        }
        
        mediaController?.let { controller ->
             val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(podcast.title)
                .setArtworkUri(android.net.Uri.parse(episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcast.imageUrl))
                .setDisplayTitle(episode.title)
                .setSubtitle(podcast.title)
                .setGenre(episode.podcastGenre ?: podcast.genre)
                .build()
       
             val mediaItem = MediaItem.Builder()
                .setUri(episode.audioUrl)
                .setMediaMetadata(metadata)
                .setMediaId(episode.id)
                .setCustomCacheKey(episode.id) // Match DownloadRequest custom key
                .build()
                
             // Insert at index 1 (after current playing item)
             // If queue is empty or has 1 item, this adds to end (index 1)
             val insertIndex = if (controller.mediaItemCount > 0) controller.currentMediaItemIndex + 1 else 0
             controller.addMediaItem(insertIndex, mediaItem)
             
             // Update local state
             val currentQueue = _playerState.value.queue
             
             val newQueue = if (currentQueue.isNotEmpty()) {
                 // Insert at index 1
                 val mutable = currentQueue.toMutableList()
                 // Find current episode index in local queue to be safe
                 val currentId = _playerState.value.currentEpisode?.id
                 val currentIndex = if (currentId != null) mutable.indexOfFirst { it.id == currentId } else 0
                 
                 val safeIndex = if (currentIndex != -1) currentIndex + 1 else 1
                 
                 if (mutable.size >= safeIndex) {
                    mutable.add(safeIndex, episode)
                 } else {
                    mutable.add(episode)
                 }
                 mutable.toList()
             } else {
                 listOf(episode)
             }
             
             _playerState.value = _playerState.value.copy(queue = newQueue)
        }
    }



    suspend fun removeFromQueue(episodeId: String) {
        if (mediaController == null) {
            mediaController = mediaControllerFuture?.await()
        }
        
        try {
            val queueItem = queueRepository.getQueueItemByEpisodeId(episodeId)
            if (queueItem != null && queueItem.contextType == "AUTO_FILL") {
                var positionInQueue = -1
                mediaController?.let { controller ->
                    for (i in 0 until controller.mediaItemCount) {
                        if (controller.getMediaItemAt(i).mediaId == episodeId) {
                            positionInQueue = i
                            break
                        }
                    }
                }
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSmartQueueEpisodeSkipped(
                    episodeId = episodeId,
                    recommendationSource = queueItem.contextSourceId ?: "unknown",
                    positionInQueue = positionInQueue
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "Failed to track skip for $episodeId", e)
        }

        mediaController?.let { controller ->
            for (i in 0 until controller.mediaItemCount) {
                val item = controller.getMediaItemAt(i)
                if (item.mediaId == episodeId) {
                    controller.removeMediaItem(i)
                    
                    // Update local state
                    val currentQueue = _playerState.value.queue
                    val newQueue = currentQueue.filter { it.id != episodeId }
                    _playerState.value = _playerState.value.copy(queue = newQueue)
                    
                    // Sync the updated queue to the database
                    syncQueueToDb()
                    break
                }
            }
        }
    }

    suspend fun playEpisode(episode: Episode, podcast: Podcast, entryPointContext: android.os.Bundle? = null) {
        playQueue(listOf(episode), podcast, 0, entryPointContext)
    }
    
    /**
     * Play an episode from the provided queue list, reloading into Media3 from that point.
     * Pass the queue list directly to avoid stale state issues.
     */
    suspend fun playFromQueueIndex(episodeId: String, queueList: List<Episode>, podcast: Podcast) {
        val index = queueList.indexOfFirst { it.id == episodeId }
        
        if (index == -1) {
            android.util.Log.e("PlaybackRepo", "playFromQueueIndex: episode $episodeId not found in provided queue!")
            return
        }
        
        // Slice queue from this episode onwards
        val slicedQueue = queueList.drop(index)
        android.util.Log.d("PlaybackRepo", "playFromQueueIndex: slicing from index $index, newQueueSize=${slicedQueue.size}")
        
        // Reload into Media3 with the sliced queue
        playQueue(slicedQueue, podcast, 0)
    }
    
    /**
     * Restore the last played session on app startup (does NOT auto-play)
     */
    suspend fun restoreLastSession(): Boolean {
        // Don't restore if player was explicitly dismissed
        if (prefs.getBoolean(KEY_PLAYER_DISMISSED, false)) {
            return false
        }
        
        val lastSession = listeningHistoryDao.getLastPlayedSessionAny() ?: return false
        
        // Construct Episode WITH podcast metadata (critical for onMediaItemTransition)
        var episode = Episode(
            id = lastSession.episodeId,
            title = lastSession.episodeTitle,
            description = "",
            audioUrl = lastSession.episodeAudioUrl ?: return false,
            imageUrl = lastSession.episodeImageUrl,
            podcastImageUrl = lastSession.podcastImageUrl,
            podcastTitle = lastSession.podcastName,
            podcastId = lastSession.podcastId,
            podcastGenre = "Podcast",
            podcastArtist = "",
            duration = (lastSession.durationMs / 1000).toInt(),
            publishedDate = 0L,
            enclosureType = lastSession.enclosureType
        )
        
        val podcast = Podcast(
            id = lastSession.podcastId,
            title = lastSession.podcastName,
            artist = "",
            imageUrl = lastSession.podcastImageUrl ?: "",
            description = null,
            genre = "Podcast"
        )
        
        // Restore Queue from DB (queue items now include P2.0 fields)
        val savedQueue = queueRepository.getQueueSnapshot()
        
        // Enrich restored episode with P2.0 data from queue if available
        val queueEpisode = savedQueue.find { it.id == episode.id }
        if (queueEpisode != null) {
            episode = episode.copy(
                chaptersUrl = queueEpisode.chaptersUrl,
                transcriptUrl = queueEpisode.transcriptUrl,
                persons = queueEpisode.persons,
                transcripts = queueEpisode.transcripts,
                seasonNumber = queueEpisode.seasonNumber,
                episodeNumber = queueEpisode.episodeNumber,
                episodeType = queueEpisode.episodeType
            )
        }
        
        // If saved queue is empty but we have an episode, make a single-item queue
        val restoredQueue = if (savedQueue.isEmpty()) listOf(episode) else savedQueue
        
        // Update state but don't play
        _playerState.value = _playerState.value.copy(
            currentEpisode = episode,
            currentPodcast = podcast,
            isPlaying = false,
            position = lastSession.progressMs,
            duration = lastSession.durationMs,
            isLiked = lastSession.isLiked,
            queue = restoredQueue
        )
        
        return true
    }
    
    /**
     * Clear the current session (for swipe-to-dismiss)
     */
    fun clearSession() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        stopProgressTicker()
        _playerState.value = PlayerState()
        // Mark as dismissed so we don't restore on next app launch
        prefs.edit().putBoolean(KEY_PLAYER_DISMISSED, true).apply()
    }
    
    fun togglePlayPause(entryPointContext: android.os.Bundle? = null) {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            // Need to set extras for pausing if we want them? 
            // Actually pause just acts on current item. We can't change extras easily on pause via controller.
            // But we can trigger pause.
            controller.pause()
        } else {
             // Use our robust resume() which handles state restoration
             resume(entryPointContext)
        }
    }

    fun pause() {
        mediaController?.pause()
    }
    
    fun resume(entryPointContext: android.os.Bundle? = null) {
        val controller = mediaController ?: return
        
        Log.d("PlaybackRepo", "resume() called: mediaItemCount=${controller.mediaItemCount}, statePos=${_playerState.value.position}")
        
        // If controller has no media but we have state, reload the FULL queue
        if (controller.mediaItemCount == 0 && _playerState.value.currentEpisode != null) {
            val queue = _playerState.value.queue
            val currentEpisode = _playerState.value.currentEpisode!!
            val podcast = _playerState.value.currentPodcast
            val savedPosition = _playerState.value.position
            
            Log.d("PlaybackRepo", "resume(): Controller empty, reloading full queue (${queue.size} items)")
            
            repositoryScope.launch {
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("resume")
                if (queue.isNotEmpty() && podcast != null) {
                    // Find current episode in queue and reload from that point
                    val startIndex = queue.indexOfFirst { it.id == currentEpisode.id }.coerceAtLeast(0)
                    Log.d("PlaybackRepo", "resume(): Reloading queue from index $startIndex with position=$savedPosition")
                    
                    val mediaItems = queue.map { episode ->
                        val metadata = androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(episode.title)
                            .setArtist(episode.podcastTitle ?: podcast.title)
                            .setArtworkUri(android.net.Uri.parse(
                                episode.imageUrl?.takeIf { it.isNotBlank() }
                                ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() }
                                ?: podcast.imageUrl
                            ))
                            .setDisplayTitle(episode.title)
                            .setSubtitle(episode.podcastTitle ?: podcast.title)
                            .setGenre(episode.podcastGenre ?: podcast.genre)
                            .setExtras(entryPointContext)
                            .build()
                        
                        MediaItem.Builder()
                            .setUri(episode.audioUrl)
                            .setMediaMetadata(metadata)
                            .setMediaId(episode.id)
                            .setCustomCacheKey(episode.id)
                            .build()
                    }
                    
                    controller.setMediaItems(mediaItems, startIndex, savedPosition.coerceAtLeast(0L))
                    controller.prepare()
                    controller.play()
                } else {
                    // Fallback: single episode resume (no queue available)
                    Log.d("PlaybackRepo", "resume(): No queue, loading single episode")
                    val metadata = androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(currentEpisode.title)
                        .setArtist(podcast?.title ?: "")
                        .setArtworkUri(android.net.Uri.parse(currentEpisode.imageUrl?.takeIf { it.isNotBlank() } ?: podcast?.imageUrl ?: ""))
                        .setDisplayTitle(currentEpisode.title)
                        .setSubtitle(podcast?.title ?: "")
                        .setGenre(currentEpisode.podcastGenre ?: podcast?.genre ?: "Podcast")
                        .setExtras(entryPointContext)
                        .build()
                    
                    val mediaItem = MediaItem.Builder()
                        .setUri(currentEpisode.audioUrl)
                        .setMediaMetadata(metadata)
                        .setMediaId(currentEpisode.id)
                        .setCustomCacheKey(currentEpisode.id)
                        .build()
                    
                    controller.setMediaItem(mediaItem, savedPosition.coerceAtLeast(0L))
                    controller.prepare()
                    controller.play()
                }
            }
        } else {
            Log.d("PlaybackRepo", "resume(): Media exists, just calling play()")
            controller.play()
        }
    }
    
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(position = positionMs)
        
        // Save state on seek (do not update lastPlayedAt to prevent reordering on scrub)
        repositoryScope.launch { saveCurrentState(updateLastPlayedAt = false) }
    }
    
    fun skipForward() {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("skip_30s")
        seekTo((_playerState.value.position + 30000).coerceAtMost(_playerState.value.duration))
    }
    
    fun skipBackward() {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("replay_10s")
        seekTo((_playerState.value.position - 10000).coerceAtLeast(0))
    }

    fun skipToEpisode(index: Int, entryPointContext: android.os.Bundle? = null) {
        val controller = mediaController
        android.util.Log.d("PlaybackRepo", "skipToEpisode: index=$index, controller=${controller != null}, mediaItemCount=${controller?.mediaItemCount ?: -1}")
        
        if (controller == null) {
            android.util.Log.e("PlaybackRepo", "skipToEpisode: mediaController is NULL!")
            return
        }
        
        // If controller is empty but we have a local queue, re-initialize playback
        if (controller.mediaItemCount == 0 && _playerState.value.queue.isNotEmpty()) {
             android.util.Log.d("PlaybackRepo", "skipToEpisode: Controller empty but local queue exists. Re-initializing playback.")
             val queue = _playerState.value.queue
             val podcast = _playerState.value.currentPodcast
             
             if (index in queue.indices && podcast != null) {
                 repositoryScope.launch {
                     playQueue(queue, podcast, index, entryPointContext)
                 }
                 return
             }
        }
        
        // Find the matching Media3 index by mediaId (more reliable than assuming indices match)
        val targetEpisode = _playerState.value.queue.getOrNull(index)
        if (targetEpisode != null) {
            for (i in 0 until controller.mediaItemCount) {
                if (controller.getMediaItemAt(i).mediaId == targetEpisode.id) {
                    android.util.Log.d("PlaybackRepo", "skipToEpisode: Found mediaId=${targetEpisode.id} at Media3 index $i")
                    
                    // Set entry point context via static holder (IPC-safe)
                    if (entryPointContext != null) {
                        val map = mutableMapOf<String, Any>()
                        entryPointContext.keySet().forEach { key ->
                            entryPointContext.get(key)?.let { map[key] = it }
                        }
                        if (map.isNotEmpty()) {
                            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(map)
                        }
                    }
                    
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("transition")
                    // Look up saved position from listening history to resume where the user left off
                    val mediaIndex = i
                    repositoryScope.launch {
                        val saved = listeningHistoryDao.getHistoryItem(targetEpisode.id)
                        val savedPosMs = if (saved != null && !saved.isCompleted && saved.progressMs > 2000) {
                            android.util.Log.d("PlaybackRepo", "skipToEpisode: Restoring saved position ${saved.progressMs}ms for ${targetEpisode.id}")
                            saved.progressMs
                        } else {
                            0L
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            controller.seekTo(mediaIndex, savedPosMs)
                            controller.play()
                        }
                    }
                    return
                }
            }
            android.util.Log.e("PlaybackRepo", "skipToEpisode: mediaId=${targetEpisode.id} NOT found in Media3!")
        } else {
            android.util.Log.e("PlaybackRepo", "skipToEpisode: index $index out of bounds for queue size ${_playerState.value.queue.size}!")
        }
    }

    fun skipToNextEpisode() {
        val currentEpisodeId = _playerState.value.currentEpisode?.id ?: return
        val currentIndex = _playerState.value.queue.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex != -1 && currentIndex < _playerState.value.queue.size - 1) {
            skipToEpisode(currentIndex + 1)
        }
    }

    fun skipToPreviousEpisode() {
        val currentEpisodeId = _playerState.value.currentEpisode?.id ?: return
        val currentIndex = _playerState.value.queue.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex > 0) {
            skipToEpisode(currentIndex - 1)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
    }

    fun setSleepTimer(durationMinutes: Int, dismissNudge: Boolean = true) {
        Log.d("PlaybackRepo", "setSleepTimer called: $durationMinutes minutes, dismissNudge=$dismissNudge")
        sleepTimerJob?.cancel()
        
        if (durationMinutes <= 0) {
            Log.d("PlaybackRepo", "Sleep timer: OFF")
            _playerState.value = _playerState.value.copy(
                sleepTimerEnd = null, 
                sleepAtEndOfEpisode = false,
                showLateNightNudge = if (dismissNudge) false else _playerState.value.showLateNightNudge
            )
            return
        }

        // Special marker for "End of Episode" mode
        if (durationMinutes == 999) {
            Log.d("PlaybackRepo", "Sleep timer: End of Episode mode ENABLED")
            // Just set the flag — the actual pause is handled by onMediaItemTransition
            // and onPlaybackStateChanged(STATE_ENDED) in the Media3 listener
            _playerState.value = _playerState.value.copy(
                sleepAtEndOfEpisode = true, 
                sleepTimerEnd = null,
                showLateNightNudge = if (dismissNudge) false else _playerState.value.showLateNightNudge
            )
            
            // Background job only updates the countdown display (no action logic)
            sleepTimerJob = repositoryScope.launch {
                while (true) {
                    val state = _playerState.value
                    if (!state.sleepAtEndOfEpisode) break
                    
                    if (state.duration > 0 && state.position > 0) {
                        val remaining = (state.duration - state.position).coerceAtLeast(0)
                        val dynamicEndTime = System.currentTimeMillis() + remaining
                        _playerState.value = _playerState.value.copy(sleepTimerEnd = dynamicEndTime)
                    }
                    
                    delay(1000)
                }
            }
        } else {
            // Fixed timer mode
            val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
            Log.d("PlaybackRepo", "Sleep timer: Fixed ${durationMinutes}m, endTime=$endTime")
            _playerState.value = _playerState.value.copy(
                sleepTimerEnd = endTime, 
                sleepAtEndOfEpisode = false,
                showLateNightNudge = if (dismissNudge) false else _playerState.value.showLateNightNudge
            )

            sleepTimerJob = repositoryScope.launch {
                val waitMs = endTime - System.currentTimeMillis()
                if (waitMs > 0) {
                    delay(waitMs)
                }
                Log.d("PlaybackRepo", "Sleep timer: FIRING! Pausing playback.")
                mediaController?.pause()
                stopProgressTicker()
                _playerState.value = _playerState.value.copy(
                    sleepTimerEnd = null, 
                    isPlaying = false,
                    showLateNightNudge = false
                )
            }
        }
    }

    private fun isLateNight(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute
        
        val startLateNight = 22 * 60 + 30 // 10:30 PM (1350 minutes)
        val endLateNight = 4 * 60 // 4:00 AM (240 minutes)
        
        return timeInMinutes >= startLateNight || timeInMinutes < endLateNight
    }

    fun dismissLateNightNudge() {
        Log.d("PlaybackRepo", "dismissLateNightNudge() called, current showLateNightNudge=${_playerState.value.showLateNightNudge}")
        _playerState.value = _playerState.value.copy(showLateNightNudge = false)
    }

    fun resetSleepNudgeForTesting() {
        prefs.edit().putLong("last_late_night_nudge_timestamp", 0L).apply()
        setSleepTimer(0)
    }

    val lastPlayedSession: Flow<PlaybackSession?> = listeningHistoryDao.getResumeItems()
        .map { historyList ->
            val latest = historyList.firstOrNull()
            if (latest != null) {
                PlaybackSession(
                    podcastId = latest.podcastId,
                    episodeId = latest.episodeId,
                    positionMs = latest.progressMs,
                    durationMs = latest.durationMs,
                    timestamp = latest.lastPlayedAt,
                    episodeTitle = latest.episodeTitle,
                    podcastTitle = latest.podcastName,
                    imageUrl = latest.episodeImageUrl,
                    podcastImageUrl = latest.podcastImageUrl,
                    audioUrl = latest.episodeAudioUrl,
                    enclosureType = latest.enclosureType
                )
            } else {
                null
            }
        }

    val resumeSessions: Flow<List<PlaybackSession>> = listeningHistoryDao.getResumeItems()
        .map { historyList ->
            historyList.map { entity ->
                PlaybackSession(
                    podcastId = entity.podcastId,
                    episodeId = entity.episodeId,
                    positionMs = entity.progressMs,
                    durationMs = entity.durationMs,
                    timestamp = entity.lastPlayedAt,
                    episodeTitle = entity.episodeTitle,
                    podcastTitle = entity.podcastName,
                    imageUrl = entity.episodeImageUrl,
                    podcastImageUrl = entity.podcastImageUrl,
                    audioUrl = entity.episodeAudioUrl,
                    enclosureType = entity.enclosureType
                )
            }
        }

    fun getAllHistory(): Flow<List<cx.aswin.boxcast.core.data.database.ListeningHistoryEntity>> {
        return listeningHistoryDao.getAllHistory()
    }
    
    val likedEpisodes: Flow<List<cx.aswin.boxcast.core.data.database.ListeningHistoryEntity>> = listeningHistoryDao.getLikedEpisodes()
    
    val completedEpisodeIds: Flow<Set<String>> = listeningHistoryDao.getCompletedEpisodeIdsFlow()
        .map { it.toSet() }

    suspend fun upsertHistoryEntity(entity: cx.aswin.boxcast.core.data.database.ListeningHistoryEntity) {
        listeningHistoryDao.upsert(entity)
    }

    suspend fun removeHistoryItem(episodeId: String) {
        listeningHistoryDao.delete(episodeId)
    }

    suspend fun clearHistory() {
        listeningHistoryDao.deleteAll()
    }

    suspend fun toggleLike(episode: Episode, podcastId: String, podcastTitle: String, podcastImageUrl: String?) {
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val newStatus = !(existing?.isLiked ?: false)
        
        // If current player is playing this episode, update state immediately
        if (_playerState.value.currentEpisode?.id == episode.id) {
             _playerState.value = _playerState.value.copy(isLiked = newStatus)
        }
        
        if (existing != null) {
            listeningHistoryDao.setLikeStatus(episode.id, newStatus)
        } else {
            // Create new entry if liking something not in history
            val entity = cx.aswin.boxcast.core.data.database.ListeningHistoryEntity(
                episodeId = episode.id,
                podcastId = podcastId,
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = podcastImageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName = podcastTitle,
                progressMs = 0L,
                durationMs = episode.duration * 1000L,
                isCompleted = false,
                isLiked = newStatus,
                lastPlayedAt = System.currentTimeMillis(),
                isDirty = true,
                enclosureType = episode.enclosureType
            )
            listeningHistoryDao.upsert(entity)
        }
    }

    suspend fun toggleCompletion(episode: Episode, podcastId: String, podcastTitle: String, podcastImageUrl: String?) {
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val newStatus = !(existing?.isCompleted ?: false)
        
        if (existing != null) {
            val updated = existing.copy(
                isCompleted = newStatus,
                isManualCompletion = newStatus,
                progressMs = 0L,
                lastPlayedAt = System.currentTimeMillis(),
                isDirty = true
            )
            listeningHistoryDao.upsert(updated)
        } else {
             // Create new entry
            val entity = cx.aswin.boxcast.core.data.database.ListeningHistoryEntity(
                episodeId = episode.id,
                podcastId = podcastId,
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = podcastImageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName = podcastTitle,
                progressMs = 0L,
                durationMs = episode.duration * 1000L,
                isCompleted = newStatus,
                isLiked = false,
                lastPlayedAt = System.currentTimeMillis(),
                isDirty = true,
                enclosureType = episode.enclosureType,
                isManualCompletion = newStatus,
                isBulkCompletion = false
            )
            listeningHistoryDao.upsert(entity)
        }
    }
    
    private suspend fun markCurrentEpisodeAsCompleted() {
        val state = _playerState.value
        val episode = state.currentEpisode ?: return
        val podcast = state.currentPodcast ?: return
        markEpisodeAsCompleted(episode, podcast)
    }

    private suspend fun markEpisodeAsCompleted(episode: Episode, podcast: Podcast?) {
        android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: START for ${episode.title}")

        // Update DB
        listeningHistoryDao.setCompletionStatus(episode.id, true)
        
        // Update History Entity to ensure consistency
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        
        if (existing == null) {
                if (podcast == null && episode.podcastId == null) {
                 android.util.Log.e("PlaybackRepo", "markEpisodeAsCompleted: Cannot create history item, podcast is null")
                 return
             }
             android.util.Log.d("PlaybackRepo", "Creating new history item for completed episode")
             val entity = cx.aswin.boxcast.core.data.database.ListeningHistoryEntity(
                episodeId = episode.id,
                podcastId = episode.podcastId ?: podcast!!.id,
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = episode.podcastImageUrl ?: podcast?.imageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName = episode.podcastTitle ?: podcast?.title ?: "Unknown Podcast",
                progressMs = 0L, // Reset progress on completion
                durationMs = episode.duration * 1000L,
                isCompleted = true,
                isLiked = false, // We don't know
                lastPlayedAt = System.currentTimeMillis(),
                isDirty = true,
                enclosureType = episode.enclosureType
            )
            listeningHistoryDao.upsert(entity)
        } else {
             // UPDATE timestamp too so it appears in recently played (loop prevention)
             android.util.Log.d("PlaybackRepo", "Updating existing history item as completed")
             val updated = existing.copy(
                 isCompleted = true, 
                 progressMs = 0L, // Reset progress!
                 lastPlayedAt = System.currentTimeMillis(),
                 isDirty = true
             )
             listeningHistoryDao.upsert(updated)
        }
        android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: DONE for ${episode.title}")
    }
    
    // ... toggleLike ...

    suspend fun savePlaybackState(
        podcastId: String, 
        episodeId: String, 
        positionMs: Long, 
        durationMs: Long,
        // New params for cache
        episodeTitle: String,
        episodeImageUrl: String?,
        podcastImageUrl: String?,
        episodeAudioUrl: String,
        podcastName: String,
        isCompleted: Boolean,
        isLiked: Boolean,
        lastPlayedAt: Long = System.currentTimeMillis(),
        enclosureType: String? = null
    ) {
        android.util.Log.v("PlaybackRepo", "Saving playback state: $episodeTitle, pos=$positionMs, completed=$isCompleted")
        val entity = cx.aswin.boxcast.core.data.database.ListeningHistoryEntity(
            episodeId = episodeId,
            podcastId = podcastId,
            episodeTitle = episodeTitle,
            episodeImageUrl = episodeImageUrl,
            podcastImageUrl = podcastImageUrl,
            episodeAudioUrl = episodeAudioUrl,
            podcastName = podcastName,
            progressMs = positionMs,
            durationMs = durationMs,
            isCompleted = isCompleted,
            isLiked = isLiked,
            lastPlayedAt = lastPlayedAt,
            isDirty = true,
            enclosureType = enclosureType,
            isManualCompletion = false,
            isBulkCompletion = false
        )
        listeningHistoryDao.upsert(entity)
    }

    // Legacy parameterless generic toggle (for player controls)
    suspend fun toggleLike() {
        val state = _playerState.value
        val episode = state.currentEpisode ?: return
        val podcast = state.currentPodcast ?: return
        toggleLike(episode, podcast.id, podcast.title, podcast.imageUrl)
    }


    suspend fun deleteSession(episodeId: String) {
        listeningHistoryDao.delete(episodeId)
    }

    suspend fun getSession(episodeId: String): PlaybackSession? {
        val entity = listeningHistoryDao.getHistoryItem(episodeId) ?: return null
        return PlaybackSession(
            podcastId = entity.podcastId,
            episodeId = entity.episodeId,
            positionMs = entity.progressMs,
            durationMs = entity.durationMs,
            timestamp = entity.lastPlayedAt,
            episodeTitle = entity.episodeTitle,
            podcastTitle = entity.podcastName,
            imageUrl = entity.episodeImageUrl,
            podcastImageUrl = entity.podcastImageUrl,
            audioUrl = entity.episodeAudioUrl,
            enclosureType = entity.enclosureType
        )
    }

    suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) {
        val currentTime = System.currentTimeMillis()
        val entitiesToUpsert = episodes.map { episode ->
            val existing = listeningHistoryDao.getHistoryItem(episode.id)
            if (existing != null) {
                existing.copy(
                    isCompleted = true,
                    progressMs = 0L,
                    isBulkCompletion = true,
                    lastPlayedAt = currentTime,
                    isDirty = true
                )
            } else {
                cx.aswin.boxcast.core.data.database.ListeningHistoryEntity(
                    episodeId = episode.id,
                    podcastId = podcastId,
                    episodeTitle = episode.title,
                    episodeImageUrl = episode.imageUrl,
                    podcastImageUrl = podcastImageUrl,
                    episodeAudioUrl = episode.audioUrl,
                    podcastName = podcastTitle,
                    progressMs = 0L,
                    durationMs = episode.duration * 1000L,
                    isCompleted = true,
                    isLiked = false,
                    lastPlayedAt = currentTime,
                    isDirty = true,
                    enclosureType = episode.enclosureType,
                    isManualCompletion = false,
                    isBulkCompletion = true
                )
            }
        }
        listeningHistoryDao.upsertAll(entitiesToUpsert)
    }

    suspend fun markAllEpisodesUncompleted(episodes: List<Episode>) {
        val currentTime = System.currentTimeMillis()
        val entitiesToUpsert = episodes.mapNotNull { episode ->
            val existing = listeningHistoryDao.getHistoryItem(episode.id)
            if (existing != null && existing.isCompleted) {
                existing.copy(
                    isCompleted = false,
                    progressMs = 0L,
                    isManualCompletion = false,
                    isBulkCompletion = false,
                    lastPlayedAt = currentTime,
                    isDirty = true
                )
            } else {
                null
            }
        }
        if (entitiesToUpsert.isNotEmpty()) {
            listeningHistoryDao.upsertAll(entitiesToUpsert)
        }
    }

    private suspend fun findPodcastIdForEpisode(episodeId: String): String? {
        val historyItem = listeningHistoryDao.getHistoryItem(episodeId)
        if (historyItem != null) return historyItem.podcastId
        
        val episode = podcastRepository.getEpisode(episodeId)
        return episode?.podcastId
    }

    suspend fun getHistoryForRecommendations(limit: Int = 15): List<cx.aswin.boxcast.core.network.model.HistoryItem> {
        val database = cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(context)
        val podcastDao = database.podcastDao()
        
        // Fetch up to limit * 3 recent items to have room for filtering out accidental skips/taps
        val rawHistory = listeningHistoryDao.getRecentHistoryList(limit * 3)
        return rawHistory
            .filter { entity ->
                // Exclude manually marked completed (double-check since DAO already filters it)
                !entity.isManualCompletion && !entity.isBulkCompletion &&
                // Filter out accidental plays/skips (progress < 60 seconds) unless they completed it normally
                (entity.progressMs >= 60_000L || entity.isCompleted)
            }
            .take(limit)
            .map { entity ->
                val podcast = podcastDao.getPodcast(entity.podcastId)
                cx.aswin.boxcast.core.network.model.HistoryItem(
                    podcastTitle = entity.podcastName,
                    episodeTitle = entity.episodeTitle,
                    podcastId = entity.podcastId,
                    genre = podcast?.genre,
                    durationMs = entity.durationMs,
                    progressMs = entity.progressMs,
                    isCompleted = entity.isCompleted,
                    isLiked = entity.isLiked
                )
            }
    }
}
