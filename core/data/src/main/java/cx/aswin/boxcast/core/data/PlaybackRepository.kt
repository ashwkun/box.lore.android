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
import cx.aswin.boxcast.core.data.service.BoxCastPlaybackService
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
    val audioUrl: String?
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
    val isLiked: Boolean = false
)

class PlaybackRepository(
    private val context: Context,
    private val listeningHistoryDao: cx.aswin.boxcast.core.data.database.ListeningHistoryDao,
    private val queueRepository: cx.aswin.boxcast.core.data.QueueRepository
) {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val _playerState = kotlinx.coroutines.flow.MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()
    
    // Preferences for session state
    private val prefs = context.getSharedPreferences("boxcast_player", Context.MODE_PRIVATE)
    private val KEY_PLAYER_DISMISSED = "player_dismissed"
    
    // Scope for progress updates
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var likeStateObserverJob: Job? = null
    
    // Queue auto-refill callback - set by QueueManager
    private val QUEUE_REFILL_THRESHOLD = 3
    var queueRefillCallback: ((currentEpisode: Episode, podcast: Podcast) -> Unit)? = null

    init {
        initializeMediaController()
        monitorLikeState()
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
                    _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                    if (isPlaying) {
                        pendingSaveJob?.cancel() // Cancel pending save if we resume
                        startProgressTicker()
                    } else {
                        stopProgressTicker()
                        // Save state when paused - delay to prevent list reorder during pod switching
                        pendingSaveJob?.cancel()
                        pendingSaveJob = repositoryScope.launch { 
                            kotlinx.coroutines.delay(10000) // 10 second delay
                            saveCurrentState() 
                        }
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
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false, 
                        isLoading = false,
                        currentEpisode = null,
                        queue = emptyList()
                    )
                    repositoryScope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Playback error: Stream may be unavailable or broken.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
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
                    
                    if (slotIndex == -1) {
                        android.util.Log.w("PlaybackRepo", "onMediaItemTransition: Episode $episodeId NOT found in local queue. Ignoring.")
                        return
                    }
                    
                    val newEpisode = queue[slotIndex]
                    android.util.Log.d("PlaybackRepo", "Media transition to: ${newEpisode.title}")

                    // Launch a single coroutine to handle the transition logic sequentially
                    // This ensures DB updates finish BEFORE we trigger SmartQueue refill
                    repositoryScope.launch {
                        // 1. Mark PREVIOUS episode as completed (if distinct)
                        val previousEpisode = oldState.currentEpisode
                        val previousPodcast = oldState.currentPodcast 
                        
                        if (previousEpisode != null && previousEpisode.id != newEpisode.id) {
                            android.util.Log.d("PlaybackRepo", "Transition: Marking previous episode as COMPLETED: ${previousEpisode.title} (ID: ${previousEpisode.id})")
                            markEpisodeAsCompleted(previousEpisode, previousPodcast)
                        }
                        
                        // 2. Derive podcast context from episode metadata
                        val newPodcast = if (newEpisode.podcastId != null) {
                            cx.aswin.boxcast.core.model.Podcast(
                                id = newEpisode.podcastId!!,
                                title = newEpisode.podcastTitle ?: "Unknown Podcast",
                                artist = newEpisode.podcastArtist ?: "",
                                imageUrl = newEpisode.podcastImageUrl ?: "",
                                description = null,
                                genre = newEpisode.podcastGenre ?: "Podcast"
                            )
                        } else {
                            oldState.currentPodcast
                        }

                        // 3. QUEUE CONSUMPTION: Drop items before current
                        if (slotIndex > 0) {
                             val newQueue = queue.drop(slotIndex)
                             android.util.Log.d("PlaybackRepo", "Consuming queue: Dropped $slotIndex items. New size: ${newQueue.size}")
                             _playerState.value = _playerState.value.copy(
                                 currentEpisode = newEpisode,
                                 currentPodcast = newPodcast,
                                 queue = newQueue
                             )
                        } else {
                             _playerState.value = _playerState.value.copy(
                                 currentEpisode = newEpisode,
                                 currentPodcast = newPodcast
                             )
                        }

                        // 4. Sync queue to DB for restart recovery
                        syncQueueToDb()

                        // 5. Auto-refill when queue is running low
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
                        publishedDate = 0L
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
    
    private fun startProgressTicker() {
        stopProgressTicker()
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
                        
                        // Save progress periodically (every ~10 seconds)
                        if (System.currentTimeMillis() % 10000 < 500) {
                             saveCurrentState()
                        }
                    }
                }
                kotlinx.coroutines.delay(500) // Update every 500ms
            }
        }
    }
    
    // Helper to save current state
    private suspend fun saveCurrentState() {
        val state = _playerState.value
        val episode = state.currentEpisode ?: return
        val podcast = state.currentPodcast ?: return
        
        // Check existing completion status to avoid overwriting
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val wasCompleted = existing?.isCompleted ?: false
        
        // If it was completed, keep it completed UNLESS we are explicitly restarting (pos < 5s)
        // But even then, maybe we just want to keep it "played"? 
        // Let's say if we are > 95% or < 5% and it WAS completed, keep it completed.
        // Actually simplest: If it was completed, STAY completed.
        // Only un-complete if user explicitly clears history (which is a different action).
        
        savePlaybackState(
            podcastId = podcast.id,
            episodeId = episode.id,
            positionMs = state.position,
            durationMs = state.duration,
            episodeTitle = episode.title,
            episodeImageUrl = episode.imageUrl,
            podcastImageUrl = podcast.imageUrl,
            episodeAudioUrl = episode.audioUrl,
            podcastName = podcast.title,
            isCompleted = wasCompleted, // Persist completion!
            isLiked = state.isLiked
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
            
            // Check for saved progress for the STARTING episode
            var startPosMs = 0L
            val startEpisodeId = episodes.getOrNull(startIndex)?.id
            if (startEpisodeId != null) {
                 val saved = listeningHistoryDao.getHistoryItem(startEpisodeId)
                 if (saved != null && !saved.isCompleted) {
                     startPosMs = saved.progressMs
                 }
            }
            
            // Check liked status for start episode
            var initialLikeState = false
            if (startEpisodeId != null) {
                val saved = listeningHistoryDao.getHistoryItem(startEpisodeId)
                if (saved != null) {
                    initialLikeState = saved.isLiked
                }
            }
            
            // Update local state BEFORE setMediaItems (onMediaItemTransition reads this)
            val currentEp = episodes.getOrNull(startIndex)
            if (currentEp != null) {
                 _playerState.value = _playerState.value.copy(
                    currentEpisode = currentEp,
                    currentPodcast = podcast,
                    isPlaying = true,
                    position = startPosMs,
                    duration = currentEp.duration.toLong() * 1000,
                    queue = episodes, // Update queue BEFORE Media3 triggers callbacks
                    isLiked = initialLikeState
                )
            }
            
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
            } else {
            }
            
            controller.play()
            
            // Sync queue to DB for restart recovery
            syncQueueToDb()
            
            // Immediate save so Home resume card appears right away
            // (without this, first DB write doesn't happen until ~10s periodic ticker save)
            saveCurrentState()
        }
    }

    suspend fun addToQueue(episode: Episode, podcast: Podcast) {
        Log.d("PlaybackRepo", "addToQueue called: episodeId=${episode.id}, title=${episode.title}")
        
        // Prevent Duplicates in active queue (by ID)
        if (_playerState.value.queue.any { it.id == episode.id }) {
            Log.w("PlaybackRepo", "addToQueue: Episode ${episode.title} already in active queue (ID match). Skipping.")
            return
        }
        
        // Prevent duplicate of currently playing episode (by title — catches republished episodes)
        val currentEp = _playerState.value.currentEpisode
        if (currentEp != null && currentEp.title == episode.title && currentEp.id != episode.id) {
            Log.w("PlaybackRepo", "addToQueue: Episode '${episode.title}' matches currently playing title. Skipping.")
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
        
        mediaController?.let { controller ->
            for (i in 0 until controller.mediaItemCount) {
                val item = controller.getMediaItemAt(i)
                if (item.mediaId == episodeId) {
                    controller.removeMediaItem(i)
                    
                    // Update local state
                    val currentQueue = _playerState.value.queue
                    val newQueue = currentQueue.filter { it.id != episodeId }
                    _playerState.value = _playerState.value.copy(queue = newQueue)
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
            publishedDate = 0L
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
        
        // Save state on seek
        repositoryScope.launch { saveCurrentState() }
    }
    
    fun skipForward() {
        seekTo((_playerState.value.position + 30000).coerceAtMost(_playerState.value.duration))
    }
    
    fun skipBackward() {
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
                    } else {
                    }
                    
                    controller.seekToDefaultPosition(i)
                    controller.play()
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

    fun setSleepTimer(durationMinutes: Int) {
        Log.d("PlaybackRepo", "setSleepTimer called: $durationMinutes minutes")
        sleepTimerJob?.cancel()
        
        if (durationMinutes <= 0) {
            Log.d("PlaybackRepo", "Sleep timer: OFF")
            _playerState.value = _playerState.value.copy(sleepTimerEnd = null, sleepAtEndOfEpisode = false)
            return
        }

        // Special marker for "End of Episode" mode
        if (durationMinutes == 999) {
            Log.d("PlaybackRepo", "Sleep timer: End of Episode mode ENABLED")
            // Just set the flag — the actual pause is handled by onMediaItemTransition
            // and onPlaybackStateChanged(STATE_ENDED) in the Media3 listener
            _playerState.value = _playerState.value.copy(sleepAtEndOfEpisode = true, sleepTimerEnd = null)
            
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
            _playerState.value = _playerState.value.copy(sleepTimerEnd = endTime, sleepAtEndOfEpisode = false)

            sleepTimerJob = repositoryScope.launch {
                val waitMs = endTime - System.currentTimeMillis()
                if (waitMs > 0) {
                    delay(waitMs)
                }
                Log.d("PlaybackRepo", "Sleep timer: FIRING! Pausing playback.")
                mediaController?.pause()
                stopProgressTicker()
                _playerState.value = _playerState.value.copy(
                    sleepTimerEnd = null, isPlaying = false
                )
            }
        }
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
                    audioUrl = latest.episodeAudioUrl
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
                    audioUrl = entity.episodeAudioUrl
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
                isDirty = true
            )
            listeningHistoryDao.upsert(entity)
        }
    }

    suspend fun toggleCompletion(episode: Episode, podcastId: String, podcastTitle: String, podcastImageUrl: String?) {
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val newStatus = !(existing?.isCompleted ?: false)
        
        if (existing != null) {
            listeningHistoryDao.setCompletionStatus(episode.id, newStatus)
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
                isDirty = true
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
                isDirty = true
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
        isLiked: Boolean
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
            lastPlayedAt = System.currentTimeMillis(),
            isDirty = true
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
            audioUrl = entity.episodeAudioUrl
        )
    }
}
