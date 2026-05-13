package cx.aswin.boxcast.core.data.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
class BoxCastPlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Lazy-init database & repos (avoid creating them if Auto is never used)
    private val database by lazy {
        cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(this)
    }
    private val podcastRepository by lazy {
        val prefs = getSharedPreferences("boxcast_api_config", MODE_PRIVATE)
        val baseUrl = prefs.getString("base_url", null) ?: "https://api.aswin.cx"
        val publicKey = prefs.getString("public_key", null) ?: "boxcast-app-c47580b27dd94ec1"
        cx.aswin.boxcast.core.data.PodcastRepository(baseUrl, publicKey, this)
    }
    private val subscriptionRepository by lazy {
        cx.aswin.boxcast.core.data.SubscriptionRepository(database.podcastDao())
    }
    private val smartQueueEngine by lazy {
        cx.aswin.boxcast.core.data.DefaultSmartQueueEngine(
            podcastRepository = podcastRepository,
            listeningHistoryDao = database.listeningHistoryDao(),
            subscriptionRepository = subscriptionRepository
        )
    }
    private val queueRepository by lazy {
        cx.aswin.boxcast.core.data.QueueRepository(database, podcastRepository)
    }
    private var isRefilling = false
    private val QUEUE_MAX_SIZE = 50
    
    // Pending seek position for resume playback (set in onAddMediaItems, consumed in onIsPlayingChanged)
    @Volatile
    private var pendingSeekMs: Long = 0L

    // Playback Telemetry State
    private var playbackSessionStartTimeMs: Long = 0L
    private var playbackSessionEpisodeId: String? = null
    private var playbackSessionEpisodeTitle: String? = null
    private var playbackSessionPodcastId: String? = null
    private var playbackSessionPodcastName: String? = null
    private var playbackSessionPodcastGenre: String? = null
    private var playbackSessionTotalDurationMs: Long = 0L
    private var playbackSessionIsRepeating: Boolean = false
    private var playbackSessionEntryPoint: String? = null
    private var playbackSessionEntryPointContext: Map<String, Any>? = null
    
    private var playbackSessionBufferingStartTimeMs: Long = 0L
    private var playbackSessionTotalBufferedTimeMs: Long = 0L

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // Configure AudioAttributes for Focus and Background Playback
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        // Dual-cache architecture:
        // - downloadCache: permanent, user-downloaded episodes (NoOpCacheEvictor)
        // - streamCache: temporary streaming buffer for seeking (250MB LRU cap)
        val downloadCache = cx.aswin.boxcast.core.data.DownloadRepository.getDownloadCache(this)
        val streamCache = cx.aswin.boxcast.core.data.DownloadRepository.getStreamCache(this)
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(androidx.media3.common.util.Util.getUserAgent(this, "BoxCast"))
            .setAllowCrossProtocolRedirects(true)

        // Stream cache: writes streamed data here (auto-evicts at 250MB)
        val streamCacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Download cache: read-only layer that serves user-downloaded episodes without hitting network
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(streamCacheDataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Never write streaming data into the permanent download cache
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)
            
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // Handle Audio Focus
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK) // Prevent CPU sleep during streaming
            .setHandleAudioBecomingNoisy(true) // Pause on headphone disconnect
            .setSeekForwardIncrementMs(30000)
            .setSeekBackIncrementMs(10000)
            .build()
            
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onPlayerError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("BoxCastPlayer", "onPlayerError: ${error.errorCodeName}", error)
                val podcastId = playbackSessionPodcastId
                val episodeId = playbackSessionEpisodeId
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackError(
                    errorCode = error.errorCodeName,
                    errorMessage = error.message ?: "Unknown",
                    podcastId = podcastId,
                    episodeId = episodeId
                )
            }
            
            override fun onAudioSinkError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, error: Exception) {
                android.util.Log.e("BoxCastPlayer", "onAudioSinkError", error)
            }
            
            override fun onAudioUnderrun(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                android.util.Log.e("BoxCastPlayer", "onAudioUnderrun: buffer=$bufferSize, elapsed=$elapsedSinceLastFeedMs")
            }
            
            override fun onIsPlayingChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, isPlaying: Boolean) {
                android.util.Log.d("BoxCastPlayer", "onIsPlayingChanged: $isPlaying")
            }

            override fun onPlaybackStateChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, state: Int) {
                if (state == Player.STATE_BUFFERING) {
                    playbackSessionBufferingStartTimeMs = System.currentTimeMillis()
                } else if (state == Player.STATE_READY) {
                    if (playbackSessionBufferingStartTimeMs > 0) {
                        playbackSessionTotalBufferedTimeMs += (System.currentTimeMillis() - playbackSessionBufferingStartTimeMs)
                        playbackSessionBufferingStartTimeMs = 0L
                    }
                }
            }
            
            override fun onPositionDiscontinuity(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                oldPosition: Player.PositionInfo, 
                newPosition: Player.PositionInfo, 
                reason: Int
            ) {
                android.util.Log.d("BoxCastPlayer", "onPositionDiscontinuity: reason=$reason, from ${oldPosition.positionMs} to ${newPosition.positionMs}")
            }
        })

        // SmartQueue auto-refill: when queue runs low, fetch more episodes
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // Telemetry: Transition implies the previous item stopped
                val wasAutoCompleted = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                endPlaybackSession(forceCompleted = wasAutoCompleted)
                
                if (player.isPlaying) {
                    val episodeId = mediaItem?.mediaId?.removePrefix("episode:")?.removePrefix("queue:")
                    if (episodeId != null) startPlaybackSession(episodeId, mediaItem)
                }

                val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
                android.util.Log.d("AutoQueue", "onMediaItemTransition: remaining=$remaining, reason=$reason")

                if (remaining <= 2 && !isRefilling && player.mediaItemCount > 0) {
                    isRefilling = true
                    serviceScope.launch {
                        try {
                            refillQueue(player)
                        } catch (e: Exception) {
                            android.util.Log.e("AutoQueue", "Refill failed", e)
                        } finally {
                            isRefilling = false
                        }
                    }
                }
            }
        })
        
        // Progress saver + resume-seek + Telemetry
        var progressSaverJob: kotlinx.coroutines.Job? = null
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val currentItem = player.currentMediaItem
                val episodeId = currentItem?.mediaId?.removePrefix("episode:")?.removePrefix("queue:")
                
                if (isPlaying) {
                    // Telemetry: Started playing
                    if (episodeId != null) startPlaybackSession(episodeId, currentItem)

                    // Apply any pending resume-seek BEFORE starting progress saver
                    val seekTo = pendingSeekMs
                    pendingSeekMs = 0L
                    
                    if (seekTo > 2000) {
                        android.util.Log.d("AutoBrowse", "Resume-seek: seeking to ${seekTo}ms (${seekTo/1000}s)")
                        player.seekTo(seekTo)
                        // Delay progress saver to avoid overwriting the seek position
                        progressSaverJob?.cancel()
                        progressSaverJob = serviceScope.launch(Dispatchers.IO) {
                            kotlinx.coroutines.delay(5000) // Wait 5s after seek
                            saveProgressLoop(player)
                        }
                    } else {
                        progressSaverJob?.cancel()
                        progressSaverJob = serviceScope.launch(Dispatchers.IO) {
                            saveProgressLoop(player)
                        }
                    }
                } else {
                    // Telemetry: Paused playing 
                    // Only end session if explicitly paused or stopped. Ignore buffering/seeking.
                    val shouldEndSession = !player.playWhenReady || 
                                           player.playbackState == Player.STATE_ENDED ||
                                           player.playbackState == Player.STATE_IDLE
                    
                    if (shouldEndSession) {
                        endPlaybackSession(forceCompleted = false)
                    }

                    // Save one final time on pause
                    progressSaverJob?.cancel()
                    progressSaverJob = null
                    serviceScope.launch(Dispatchers.IO) {
                        saveProgressOnce(player)
                    }
                }
            }
        })

        val intent = Intent()
        intent.component = android.content.ComponentName("cx.aswin.boxcast", "cx.aswin.boxcast.MainActivity")
        intent.putExtra("EXTRA_OPEN_PLAYER", true) // Notification Click -> Open Player
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun seekToNext() {
                seekForward()
            }
            override fun seekToPrevious() {
                seekBack()
            }
            override fun seekToNextMediaItem() {
                seekForward()
            }
            override fun seekToPreviousMediaItem() {
                seekBack()
            }
            
            override fun isCommandAvailable(command: Int): Boolean {
                // Report seek forward/back as available for proper icon rendering
                if (command == Player.COMMAND_SEEK_FORWARD || command == Player.COMMAND_SEEK_BACK) return true
                return super.isCommandAvailable(command)
            }
            
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .add(Player.COMMAND_SEEK_BACK)
                    .build()
            }
        }

        // Custom actions: Seek Back 10s and Seek Forward 30s  
        val seekBackAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Replay 10s")
            .setIconResId(cx.aswin.boxcast.core.designsystem.R.drawable.rounded_replay_10_24)
            .setSessionCommand(androidx.media3.session.SessionCommand("SEEK_BACK", Bundle.EMPTY))
            .build()
        
        val seekForwardAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Forward 30s")
            .setIconResId(cx.aswin.boxcast.core.designsystem.R.drawable.rounded_forward_30_24)
            .setSessionCommand(androidx.media3.session.SessionCommand("SEEK_FORWARD", Bundle.EMPTY))
            .build()

        mediaSession = MediaLibrarySession.Builder(this, forwardingPlayer, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .setCustomLayout(listOf(seekBackAction, seekForwardAction))
            .build()
    }
    
    private fun getTimeBasedGenres(hour: Int): List<Pair<String, String>> {
        return when (hour) {
            in 5..11 -> listOf(
                "morning_news" to "Top News",
                "morning_motivation" to "Daily Motivation",
                "business_insider" to "Business & Tech"
            )
            in 12..16 -> listOf(
                "science_explainer" to "Science & Discovery",
                "tech_culture" to "Tech & Gadgets",
                "creative_focus" to "Creative Focus"
            )
            in 17..22 -> listOf(
                "comedy_gold" to "Comedy Gold",
                "tv_film_buff" to "TV & Film",
                "sports_fan" to "Sports Highlights"
            )
            else -> listOf(
                "true_crime_sleep" to "True Crime & Chill",
                "history_buff" to "History",
                "mystery_thriller" to "Mystery & Thrillers"
            )
        }
    }

    private fun startPlaybackSession(episodeId: String, currentItem: MediaItem?) {
        if (playbackSessionStartTimeMs > 0 && playbackSessionEpisodeId == episodeId) return
        
        endPlaybackSession(forceCompleted = false) // Flush any outgoing session
        
        playbackSessionStartTimeMs = System.currentTimeMillis()
        playbackSessionBufferingStartTimeMs = 0L
        playbackSessionTotalBufferedTimeMs = 0L
        playbackSessionEpisodeId = episodeId
        
        val title = currentItem?.mediaMetadata?.title?.toString()
        val artist = currentItem?.mediaMetadata?.artist?.toString() ?: currentItem?.mediaMetadata?.subtitle?.toString()
        val genre = currentItem?.mediaMetadata?.genre?.toString()
        playbackSessionEpisodeTitle = title
        playbackSessionPodcastName = artist
        playbackSessionPodcastGenre = genre
        
        val extras = currentItem?.mediaMetadata?.extras
        val bundleMap = mutableMapOf<String, Any>()
        
        // Primary: Check static holder (bypasses IPC serialization issues)
        val pendingEntryPoint = cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.consume()
        if (pendingEntryPoint != null) {
            playbackSessionEntryPoint = pendingEntryPoint["entry_point"] as? String
            val contextMap = pendingEntryPoint.filterKeys { it != "entry_point" }
            playbackSessionEntryPointContext = contextMap.ifEmpty { null }
        } else {
            // Fallback: Read from MediaMetadata extras (may not survive IPC)
            extras?.keySet()?.forEach { key ->
                @Suppress("DEPRECATION")
                val value = extras.get(key)
                if (value != null && key != "entry_point") {
                    bundleMap[key] = value
                }
            }
            playbackSessionEntryPoint = extras?.getString("entry_point")
            playbackSessionEntryPointContext = if (bundleMap.isNotEmpty()) bundleMap else null
        }
        
        serviceScope.launch {
            val podcastId = findPodcastIdForEpisode(episodeId)
            playbackSessionPodcastId = podcastId
            
            // Resolve genre race condition
            var actualGenre = genre
            if ((actualGenre == null || actualGenre == "Podcast") && podcastId != null) {
                try {
                    val podcast = database.podcastDao().getPodcast(podcastId)
                    if (podcast != null && !podcast.genre.isNullOrBlank() && podcast.genre != "Podcast") {
                        actualGenre = podcast.genre
                        playbackSessionPodcastGenre = actualGenre
                    }
                } catch (e: Exception) { /* ignore */ }
            }
            
            // Check if repeating
            val history = database.listeningHistoryDao().getHistoryItem(episodeId)
            playbackSessionIsRepeating = history?.isCompleted == true
            
            var durationMs = currentItem?.mediaMetadata?.extras?.getLong("durationMs", 0L) ?: 0L
            val exoDuration = kotlinx.coroutines.withContext(Dispatchers.Main) { 
                mediaSession?.player?.duration ?: 0L 
            }
            if (exoDuration > 0) durationMs = exoDuration
            playbackSessionTotalDurationMs = durationMs
            
            val startPositionMs = kotlinx.coroutines.withContext(Dispatchers.Main) { 
                mediaSession?.player?.currentPosition ?: 0L 
            }
            val isSubscribed = podcastId?.let { subscriptionRepository.isSubscribed(it) } ?: false
            
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackStarted(
                podcastId = podcastId,
                podcastName = artist,
                podcastGenre = actualGenre,
                episodeId = episodeId,
                episodeTitle = title,
                startPositionSeconds = startPositionMs / 1000f,
                totalDurationSeconds = durationMs / 1000f,
                isRepeating = playbackSessionIsRepeating,
                isSubscribed = isSubscribed,
                entryPoint = playbackSessionEntryPoint,
                entryPointContext = playbackSessionEntryPointContext
            )
        }
    }

    private fun endPlaybackSession(forceCompleted: Boolean = false) {
        if (playbackSessionStartTimeMs > 0 && playbackSessionEpisodeId != null) {
            val durationPlayedMs = System.currentTimeMillis() - playbackSessionStartTimeMs
            val durationPlayedSeconds = durationPlayedMs / 1000f
            
            val currentEpisodeId = playbackSessionEpisodeId!!
            val currentPodcastId = playbackSessionPodcastId
            val currentPodcastName = playbackSessionPodcastName
            val currentPodcastGenre = playbackSessionPodcastGenre
            val currentEpisodeTitle = playbackSessionEpisodeTitle
            val totalDurationMs = playbackSessionTotalDurationMs
            val entryPoint = playbackSessionEntryPoint
            val entryPointContext = playbackSessionEntryPointContext
            
            var isCompleted = forceCompleted
            if (!isCompleted) {
                try {
                    val pos = mediaSession?.player?.currentPosition ?: 0L
                    if (totalDurationMs > 0 && pos >= totalDurationMs - 5000) {
                        isCompleted = true // within 5 seconds of end
                    }
                } catch (e: Exception) {}
            }
            
            // Capture queue size for analytics
            val currentQueueSize = try { mediaSession?.player?.mediaItemCount ?: 0 } catch (_: Exception) { 0 }
            
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackPaused(
                podcastId = currentPodcastId,
                podcastName = currentPodcastName,
                podcastGenre = currentPodcastGenre,
                episodeId = currentEpisodeId,
                episodeTitle = currentEpisodeTitle,
                durationPlayedSeconds = durationPlayedSeconds,
                totalBufferedTimeSeconds = playbackSessionTotalBufferedTimeMs / 1000f,
                totalDurationSeconds = totalDurationMs / 1000f,
                isCompleted = isCompleted,
                entryPoint = entryPoint,
                entryPointContext = entryPointContext,
                queueSize = currentQueueSize
            )
            
            // Reset
            playbackSessionStartTimeMs = 0L
            playbackSessionBufferingStartTimeMs = 0L
            playbackSessionTotalBufferedTimeMs = 0L
            playbackSessionEpisodeId = null
            playbackSessionEpisodeTitle = null
            playbackSessionPodcastId = null
            playbackSessionPodcastName = null
            playbackSessionTotalDurationMs = 0L
            playbackSessionIsRepeating = false
            playbackSessionEntryPoint = null
            playbackSessionEntryPointContext = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        endPlaybackSession(forceCompleted = false)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * SmartQueue refill: called when the player queue is running low.
     * Uses SmartQueueEngine to find next episodes (same podcast → subscriptions → trending).
     * Works independently of the app UI being open.
     */
    private suspend fun refillQueue(player: ExoPlayer) {
        val currentItem = player.currentMediaItem ?: return
        
        // Extract episode info — strip any prefix for consistent ID format
        val episodeId = currentItem.mediaId.removePrefix("episode:").removePrefix("queue:")
        val metadata = currentItem.mediaMetadata
        
        android.util.Log.d("AutoQueue", "Refilling from: ${metadata.title}, episodeId=$episodeId")
        
        // Enforce queue cap
        if (player.mediaItemCount >= QUEUE_MAX_SIZE) {
            android.util.Log.d("AutoQueue", "Queue at max capacity ($QUEUE_MAX_SIZE). Skipping refill.")
            return
        }
        
        // Get podcast context (check DB first, then fallback to API)
        val podcastId = findPodcastIdForEpisode(episodeId) ?: return
        
        val podcastEntity = database.podcastDao().getPodcast(podcastId)
        val podcast = if (podcastEntity != null) {
            cx.aswin.boxcast.core.model.Podcast(
                id = podcastEntity.podcastId,
                title = podcastEntity.title,
                artist = podcastEntity.author,
                imageUrl = podcastEntity.imageUrl,
                description = podcastEntity.description,
                genre = podcastEntity.genre ?: "Podcast"
            )
        } else {
            // Fallback to API if not in local DB (e.g. unsubscribed podcast from history)
            podcastRepository.getPodcastDetails(podcastId) ?: return
        }
        
        // Build the EpisodeItem for SmartQueueEngine
        val currentEpisodeItem = cx.aswin.boxcast.core.network.model.EpisodeItem(
            id = episodeId.toLongOrNull() ?: 0L,
            title = metadata.title?.toString() ?: "",
            description = "",
            enclosureUrl = currentItem.localConfiguration?.uri?.toString(),
            image = metadata.artworkUri?.toString(),
            feedImage = podcast.imageUrl
        )
        
        val nextEntries = smartQueueEngine.getNextEpisodes(currentEpisodeItem, podcast)
        android.util.Log.d("AutoQueue", "SmartQueue returned ${nextEntries.size} episodes")
        
        if (nextEntries.isNotEmpty()) {
            // Collect existing mediaIds to avoid duplicates
            val existingIds = kotlinx.coroutines.withContext(Dispatchers.Main) {
                (0 until player.mediaItemCount).map { 
                    player.getMediaItemAt(it).mediaId.removePrefix("episode:").removePrefix("queue:")
                }.toSet()
            }
            
            // Add to player queue on main thread
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                nextEntries.forEach { entry ->
                    val ep = entry.episode
                    val pod = entry.podcast
                    val epIdStr = ep.id.toString()
                    
                    // Skip duplicates
                    if (epIdStr in existingIds) {
                        android.util.Log.d("AutoQueue", "Skipping duplicate: ${ep.title} ($epIdStr)")
                        return@forEach
                    }
                    
                    val artworkUri = (ep.image ?: ep.feedImage ?: pod.imageUrl)
                        .let { android.net.Uri.parse(it) }
                    
                    // Use raw ID — same format as PlaybackRepository (L1 fix)
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(epIdStr)
                        .setUri(ep.enclosureUrl ?: "")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(ep.title)
                                .setSubtitle(pod.title)
                                .setArtist(pod.artist)
                                .setArtworkUri(artworkUri)
                                .setDisplayTitle(ep.title)
                                .setGenre(pod.genre)
                                .setIsPlayable(true)
                                .setIsBrowsable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                .build()
                        )
                        .build()
                    
                    player.addMediaItem(mediaItem)
                }
                android.util.Log.d("AutoQueue", "Added ${nextEntries.size} items. Queue now: ${player.mediaItemCount}")
            }
            
            // Persist to QueueRepository so PlaybackRepository and UI stay in sync (C2 fix)
            nextEntries.forEach { entry ->
                try {
                    queueRepository.addToQueue(entry.episode, entry.podcast)
                } catch (e: Exception) {
                    android.util.Log.e("AutoQueue", "Failed to persist queue item: ${entry.episode.title}", e)
                }
            }
        }
    }

    /**
     * Periodically saves playback position to DB (runs on Dispatchers.IO).
     */
    private suspend fun saveProgressLoop(player: ExoPlayer) {
        while (true) {
            kotlinx.coroutines.delay(10_000)
            saveProgressOnce(player)
        }
    }
    
    /**
     * Saves the current playback position to DB once.
     */
    private suspend fun saveProgressOnce(player: ExoPlayer) {
        try {
            val currentItem = kotlinx.coroutines.withContext(Dispatchers.Main) {
                player.currentMediaItem
            }
            val positionMs = kotlinx.coroutines.withContext(Dispatchers.Main) {
                player.currentPosition
            }
            val durationMs = kotlinx.coroutines.withContext(Dispatchers.Main) {
                player.duration
            }
            val episodeId = currentItem?.mediaId
                ?.removePrefix("episode:")?.removePrefix("queue:") ?: return
            val existing = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (existing != null && positionMs > 0) {
                database.listeningHistoryDao().updateProgress(
                    episodeId = episodeId,
                    progressMs = positionMs,
                    durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                    lastPlayedAt = System.currentTimeMillis()
                )
                android.util.Log.d("AutoProgress", "Saved: $episodeId @ ${positionMs/1000}s / ${durationMs/1000}s")
                
                // Notify Auto to refresh browse tree so lists reorder by recency
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    try {
                        mediaSession?.notifyChildrenChanged("home_continue_listening", 0, null)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AutoProgress", "Save progress failed", e)
        }
    }

    /**
     * Find which podcast an episode belongs to (service-level helper).
     */
    private suspend fun findPodcastIdForEpisode(episodeId: String): String? {
        val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
        if (historyItem != null) return historyItem.podcastId
        
        val episode = podcastRepository.getEpisode(episodeId)
        return episode?.podcastId
    }
    
    /**
     * Android Auto Browse Tree Implementation.
     * 
     * Serves a media tree for browsing:
     *   Root
     *   ├── Continue Listening  (in-progress episodes from history)
     *   ├── Subscriptions       (user's subscribed podcasts → episodes)
     *   └── Queue               (current playback queue)
     */
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        // Browse tree node IDs
        private val ROOT_ID = "root"
        private val HOME_ID = "home"
        private val CONTINUE_LISTENING_ID = "continue_listening"
        private val SUBSCRIPTIONS_ID = "subscriptions"
        
        // Home Sub-folders
        private val HOME_CONTINUE_LISTENING_ID = "home_continue_listening"
        private val HOME_SUBSCRIPTIONS_ID = "home_subscriptions"
        private val HOME_NEW_EPISODES_ID = "home_new_episodes"
        
        // Explore tab
        private val EXPLORE_ID = "explore"
        
        // Virtual Playback ID
        private val PLAY_ALL_NEW_EPISODES_ID = "play_all_new_episodes"
        
        // Prefix for dynamic subscription podcast nodes
        private val SUBSCRIPTION_PREFIX = "subscription:"

        // Content style constants for Android Auto grid/list display
        private val CONTENT_STYLE_BROWSABLE_KEY = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private val CONTENT_STYLE_PLAYABLE_KEY = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private val CONTENT_STYLE_LIST = 1
        private val CONTENT_STYLE_GRID = 2
        
        // Progress bar constants for Android Auto
        private val DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE = "android.media.extra.COMPLETION_PERCENTAGE"
        private val DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS = "android.media.extra.COMPLETION_STATUS"
        private val COMPLETION_STATUS_NOT_PLAYED = 0
        private val COMPLETION_STATUS_PARTIALLY_PLAYED = 1
        private val COMPLETION_STATUS_FULLY_PLAYED = 2
        
        private val SEEK_BACK_CMD = androidx.media3.session.SessionCommand("SEEK_BACK", Bundle.EMPTY)
        private val SEEK_FORWARD_CMD = androidx.media3.session.SessionCommand("SEEK_FORWARD", Bundle.EMPTY)

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val defaultResult = super.onConnect(session, controller)
            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(SEEK_BACK_CMD)
                .add(SEEK_FORWARD_CMD)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(defaultResult.availablePlayerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle
        ): ListenableFuture<androidx.media3.session.SessionResult> {
            when (customCommand.customAction) {
                "SEEK_BACK" -> {
                    session.player.seekBack()
                    android.util.Log.d("AutoBrowse", "Seek back 10s")
                }
                "SEEK_FORWARD" -> {
                    session.player.seekForward()
                    android.util.Log.d("AutoBrowse", "Seek forward 30s")
                }
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            android.util.Log.d("AutoBrowse", "onGetLibraryRoot called")

            // Tell Auto we support content style hints
            val rootExtras = Bundle().apply {
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_LIST)   // Default: folders as list
                putInt(CONTENT_STYLE_PLAYABLE_KEY, CONTENT_STYLE_LIST)    // Episodes show as list rows
            }

            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("BoxCast")
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(rootExtras)
                        .build()
                )
                .build()

            val resultParams = LibraryParams.Builder().setExtras(rootExtras).build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, resultParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            android.util.Log.d("AutoBrowse", "onGetChildren: parentId=$parentId, page=$page")
            
            return serviceScope.future {
                try {
                    val items = when {
                        parentId == ROOT_ID -> getRootChildren()
                        parentId == HOME_ID -> getHomeChildren()
                        parentId == HOME_CONTINUE_LISTENING_ID -> getContinueListeningChildren()
                        parentId == HOME_NEW_EPISODES_ID -> getNewEpisodesChildren()
                        parentId == HOME_SUBSCRIPTIONS_ID || parentId == SUBSCRIPTIONS_ID -> getSubscriptionsChildren()
                        parentId == EXPLORE_ID -> getExploreChildren()
                        parentId == "explore_picks" -> getExplorePicksChildren()
                        parentId == "explore_genres" -> getGenresChildren()
                        parentId.startsWith("home_curated_") || parentId.startsWith("explore_curated_") -> {
                            val vibeId = parentId.removePrefix("home_curated_").removePrefix("explore_curated_")
                            getCuratedChildren(vibeId)
                        }
                        parentId.startsWith(SUBSCRIPTION_PREFIX) -> {
                            val podcastId = parentId.removePrefix(SUBSCRIPTION_PREFIX)
                            getPodcastEpisodes(podcastId)
                        }
                        else -> emptyList()
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    android.util.Log.e("AutoBrowse", "onGetChildren error for $parentId", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            android.util.Log.d("AutoBrowse", "onSearch: query='$query'")
            // Trigger async search, results delivered via onGetSearchResult
            session.notifySearchResultChanged(browser, query, 0, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            android.util.Log.d("AutoBrowse", "onGetSearchResult: query='$query'")
            
            return serviceScope.future {
                try {
                    val results = mutableListOf<MediaItem>()
                    val lowerQuery = query.lowercase()
                    
                    // 1. Search local listening history (instant, no network)
                    val historyItems = database.listeningHistoryDao().getResumeItemsList()
                    historyItems.filter {
                        it.episodeTitle.lowercase().contains(lowerQuery) ||
                        it.podcastName.lowercase().contains(lowerQuery)
                    }.take(5).forEach { entity ->
                        val artworkUri = (entity.episodeImageUrl ?: entity.podcastImageUrl)
                            ?.let { android.net.Uri.parse(it) }
                        results.add(
                            MediaItem.Builder()
                                .setMediaId("episode:${entity.episodeId}")
                                .setUri(entity.episodeAudioUrl)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(entity.episodeTitle)
                                        .setArtist(entity.podcastName)
                                        .setArtworkUri(artworkUri)
                                        .setIsPlayable(true)
                                        .setIsBrowsable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                        .build()
                                )
                                .build()
                        )
                    }
                    
                    // 2. Search subscribed podcasts (local DB)
                    val subs = database.podcastDao().getSubscribedPodcastsList()
                    subs.filter {
                        it.title.lowercase().contains(lowerQuery) ||
                        (it.author.lowercase().contains(lowerQuery))
                    }.take(5).forEach { pod ->
                        val artworkUri = android.net.Uri.parse(pod.imageUrl)
                        results.add(
                            MediaItem.Builder()
                                .setMediaId("$SUBSCRIPTION_PREFIX${pod.podcastId}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(pod.title)
                                        .setArtist(pod.author)
                                        .setArtworkUri(artworkUri)
                                        .setIsPlayable(false)
                                        .setIsBrowsable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                                        .build()
                                )
                                .build()
                        )
                    }
                    
                    // 3. Search Podcast Index API (network)
                    if (results.size < 10) {
                        try {
                            val apiResults = podcastRepository.searchPodcasts(query)
                            apiResults.take(10).forEach { podcast ->
                                val artworkUri = android.net.Uri.parse(podcast.imageUrl)
                                results.add(
                                    MediaItem.Builder()
                                        .setMediaId("$SUBSCRIPTION_PREFIX${podcast.id}")
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(podcast.title)
                                                .setArtist(podcast.artist)
                                                .setArtworkUri(artworkUri)
                                                .setIsPlayable(false)
                                                .setIsBrowsable(true)
                                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                                                .build()
                                        )
                                        .build()
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AutoBrowse", "API search failed", e)
                        }
                    }
                    
                    android.util.Log.d("AutoBrowse", "Search results for '$query': ${results.size} items")
                    LibraryResult.ofItemList(ImmutableList.copyOf(results), params)
                } catch (e: Exception) {
                    android.util.Log.e("AutoBrowse", "Search failed for '$query'", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            android.util.Log.d("AutoBrowse", "onGetItem: mediaId=$mediaId")
            // Return a simple placeholder — Auto mainly uses onGetChildren
            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(mediaId)
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        /**
         * Called when Android Auto wants to play a MediaItem from the browse tree.
         * Resolves items into playable MediaItems AND builds a queue from the
         * same podcast, mirroring the phone's QueueManager/PlayerViewModel behavior.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            android.util.Log.d("AutoBrowse", "onAddMediaItems: ${mediaItems.size} items")
            
            return serviceScope.future {
                // If multiple items (e.g. queue restore), resolve each individually
                if (mediaItems.size > 1) {
                    return@future mediaItems.map { resolveMediaItem(it) }.toMutableList()
                }
                
                val selectedItem = mediaItems.first()
                val searchQuery = selectedItem.requestMetadata.searchQuery
                
                // Voice command: "play X on BoxCast" → searchQuery is set
                if (!searchQuery.isNullOrBlank()) {
                    android.util.Log.d("AutoBrowse", "Voice play request: '$searchQuery'")
                    
                    val lowerQuery = searchQuery.lowercase()
                    val isVague = lowerQuery in listOf("podcast", "something", "music", "play", "anything", "")
                    
                    if (isVague || lowerQuery.contains("subscription") || lowerQuery.contains("resume")) {
                        // Vague request → resume last played session
                        val lastSession = database.listeningHistoryDao().getLastPlayedSession()
                        if (lastSession != null) {
                            android.util.Log.d("AutoBrowse", "Vague query → resuming last: ${lastSession.episodeTitle}")
                            pendingSeekMs = lastSession.progressMs
                            val artworkUri = (lastSession.episodeImageUrl ?: lastSession.podcastImageUrl)
                                ?.let { android.net.Uri.parse(it) }
                            return@future mutableListOf(
                                MediaItem.Builder()
                                    .setMediaId("episode:${lastSession.episodeId}")
                                    .setUri(lastSession.episodeAudioUrl)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(lastSession.episodeTitle)
                                            .setArtist(lastSession.podcastName)
                                            .setArtworkUri(artworkUri)
                                            .setIsPlayable(true)
                                            .setIsBrowsable(false)
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    }
                    
                    // Specific query → search subscriptions first, then API
                    val subs = database.podcastDao().getSubscribedPodcastsList()
                    val matchedPod = subs.firstOrNull { 
                        it.title.lowercase().contains(lowerQuery) 
                    }
                    
                    if (matchedPod != null) {
                        // Found a subscribed podcast → play its latest episode
                        android.util.Log.d("AutoBrowse", "Voice matched subscription: ${matchedPod.title}")
                        val episodes = podcastRepository.getEpisodes(matchedPod.podcastId)
                        val ep = episodes.firstOrNull()
                        if (ep != null) {
                            val artworkUri = android.net.Uri.parse(ep.imageUrl ?: matchedPod.imageUrl)
                            return@future mutableListOf(
                                MediaItem.Builder()
                                    .setMediaId("episode:${ep.id}")
                                    .setUri(ep.audioUrl)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(ep.title)
                                            .setArtist(matchedPod.title)
                                            .setArtworkUri(artworkUri)
                                            .setIsPlayable(true)
                                            .setIsBrowsable(false)
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    }
                    
                    // Not in subscriptions → search Podcast Index API
                    try {
                        val apiResults = podcastRepository.searchPodcasts(searchQuery)
                        val firstPod = apiResults.firstOrNull()
                        if (firstPod != null) {
                            android.util.Log.d("AutoBrowse", "Voice matched API: ${firstPod.title}")
                            val episodes = podcastRepository.getEpisodes(firstPod.id)
                            val ep = episodes.firstOrNull()
                            if (ep != null) {
                                val artworkUri = android.net.Uri.parse(ep.imageUrl ?: firstPod.imageUrl)
                                return@future mutableListOf(
                                    MediaItem.Builder()
                                        .setMediaId("episode:${ep.id}")
                                        .setUri(ep.audioUrl)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(ep.title)
                                                .setArtist(firstPod.title)
                                                .setArtworkUri(artworkUri)
                                                .setIsPlayable(true)
                                                .setIsBrowsable(false)
                                                .build()
                                        )
                                        .build()
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AutoBrowse", "Voice search API failed", e)
                    }
                    
                    // Fallback: resume last session
                    val fallback = database.listeningHistoryDao().getLastPlayedSession()
                    if (fallback != null) {
                        pendingSeekMs = fallback.progressMs
                        return@future mutableListOf(
                            MediaItem.Builder()
                                .setMediaId("episode:${fallback.episodeId}")
                                .setUri(fallback.episodeAudioUrl)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(fallback.episodeTitle)
                                        .setArtist(fallback.podcastName)
                                        .setIsPlayable(true)
                                        .setIsBrowsable(false)
                                        .build()
                                )
                                .build()
                        )
                    }
                }
                
                // Intercept the Virtual "Play All" Action from the New Episodes tab
                if (selectedItem.mediaId == PLAY_ALL_NEW_EPISODES_ID) {
                    android.util.Log.d("AutoBrowse", "Play All New Episodes triggered")
                    val subscriptions = subscriptionRepository.getAllSubscribedPodcasts().first()
                    
                    val newEpisodes = subscriptions
                        .mapNotNull { entity -> entity.latestEpisode?.let { ep -> ep to entity } }
                        .sortedByDescending { (ep, _) -> ep.publishedDate }
                        .take(20)
                    
                    val playableItems = newEpisodes.map { (ep, pod) ->
                        val artworkUri = android.net.Uri.parse(ep.imageUrl ?: pod.imageUrl)
                        
                        MediaItem.Builder()
                            .setMediaId("episode:${ep.id}")
                            .setUri(ep.audioUrl)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(ep.title)
                                    .setSubtitle(pod.title)
                                    .setArtist(pod.author)
                                    .setArtworkUri(artworkUri)
                                    .setIsPlayable(true)
                                    .setIsBrowsable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                    .build()
                            )
                            .build()
                    }.toMutableList()
                    
                    // Instantly returning all 20 to the player immediately builds the queue
                    return@future playableItems
                }
                
                // Single item selection: Resolve it IMMEDIATELY so playback starts instantly
                val resolvedItem = resolveMediaItem(selectedItem)
                
                val episodeId = selectedItem.mediaId.removePrefix("episode:").removePrefix("queue:")
                android.util.Log.d("AutoBrowse", "Returning episode instantly: $episodeId")
                
                // Read saved position NOW, before playback starts (no race!)
                val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
                if (historyItem != null && historyItem.progressMs > 2000 && !historyItem.isCompleted) {
                    pendingSeekMs = historyItem.progressMs
                    android.util.Log.d("AutoBrowse", "Queued resume-seek: ${historyItem.progressMs}ms (${historyItem.progressMs/1000}s)")
                } else {
                    pendingSeekMs = 0L
                }
                
                // Launch background job to fetch all other episodes and silently append to queue
                // This prevents Auto from showing the ugly "Getting your selection" loading screen
                serviceScope.launch {
                    try {
                        val podcastId = findPodcastIdForEpisode(episodeId)
                        if (podcastId != null) {
                            android.util.Log.d("AutoBrowse", "Async queue build: fetching episodes for $podcastId")
                            val allEpisodes = podcastRepository.getEpisodes(podcastId)
                            val podcastEntity = database.podcastDao().getPodcast(podcastId)
                            
                            // Need API fallback if not in Local DB
                            val podcastApi = if (podcastEntity == null) podcastRepository.getPodcastDetails(podcastId) else null
                            val podcastImageUrl = podcastEntity?.imageUrl ?: podcastApi?.imageUrl
                            val podcastTitle = podcastEntity?.title ?: podcastApi?.title
                            val podcastAuthor = podcastEntity?.author ?: podcastApi?.artist
                            
                            if (allEpisodes.isNotEmpty()) {
                                // Sort chronologically (oldest → newest)
                                val sorted = allEpisodes.sortedBy { it.publishedDate }
                                val selectedIndex = sorted.indexOfFirst { it.id == episodeId }
                                
                                // Take everything AFTER the selected episode
                                if (selectedIndex >= 0 && selectedIndex < sorted.size - 1) {
                                    val remainingQueue = sorted.subList(selectedIndex + 1, sorted.size)
                                    
                                    val mediaItemsToAdd = remainingQueue.map { episode ->
                                        val artworkUri = (episode.imageUrl ?: podcastImageUrl)?.let { android.net.Uri.parse(it) }
                                        
                                        MediaItem.Builder()
                                            .setMediaId("episode:${episode.id}")
                                            .setUri(episode.audioUrl)
                                            .setMediaMetadata(
                                                MediaMetadata.Builder()
                                                    .setTitle(episode.title)
                                                    .setSubtitle(podcastTitle ?: episode.podcastTitle ?: "")
                                                    .setArtist(podcastAuthor ?: episode.podcastArtist ?: "")
                                                    .setArtworkUri(artworkUri)
                                                    .setIsPlayable(true)
                                                    .setIsBrowsable(false)
                                                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                                    .build()
                                            )
                                            .build()
                                    }
                                    
                                    // Safely add to player on the main thread
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        mediaSession.player.addMediaItems(mediaItemsToAdd)
                                        android.util.Log.d("AutoBrowse", "Async queue built: appended ${mediaItemsToAdd.size} items")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AutoBrowse", "Async queue build failed", e)
                    }
                }
                
                // Instantly return the resolved item!
                mutableListOf(resolvedItem)
            }
        }
        
        // findPodcastIdForEpisode is defined at the service level and accessible from this inner class

        
        /**
         * Resolve a single MediaItem into a playable one with a proper URI.
         */
        private suspend fun resolveMediaItem(item: MediaItem): MediaItem {
            // Try to get the URI from various sources
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
            
            if (uri != null) {
                return item.buildUpon().setUri(uri).build()
            }
            
            val episodeId = item.mediaId.removePrefix("episode:").removePrefix("queue:")
            
            // Try history DB first
            val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (historyItem?.episodeAudioUrl != null) {
                return MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(historyItem.episodeAudioUrl)
                    .setMediaMetadata(
                        item.mediaMetadata.buildUpon()
                            .setTitle(historyItem.episodeTitle)
                            .setArtist(historyItem.podcastName)
                            .setArtworkUri(
                                (historyItem.episodeImageUrl ?: historyItem.podcastImageUrl)
                                    ?.let { android.net.Uri.parse(it) }
                            )
                            .build()
                    )
                    .build()
            }
            
            // Try API
            val episode = podcastRepository.getEpisode(episodeId)
            if (episode != null) {
                return MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(episode.audioUrl)
                    .setMediaMetadata(
                        item.mediaMetadata.buildUpon()
                            .setTitle(episode.title)
                            .setArtist(episode.podcastArtist ?: "")
                            .setArtworkUri(episode.imageUrl?.let { android.net.Uri.parse(it) })
                            .build()
                    )
                    .build()
            }
            
            android.util.Log.e("AutoBrowse", "Could not resolve episode: $episodeId")
            return item
        }

        // ============= Browse Tree Builders =============

        private fun getRootChildren(): List<MediaItem> {
            // Hint: List style for folders
            val listExtras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_KEY, CONTENT_STYLE_LIST)
            }

            return listOf(
                buildBrowsableItem(
                    id = HOME_ID,
                    title = "Home",
                    subtitle = "Your library",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = listExtras
                ),
                buildBrowsableItem(
                    id = EXPLORE_ID,
                    title = "Explore",
                    subtitle = "Discover new podcasts",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = listExtras
                )
            )
        }

        private suspend fun getContinueListeningChildren(): List<MediaItem> {
            val resumeItems = database.listeningHistoryDao().getResumeItemsList()
            android.util.Log.d("AutoBrowse", "Continue Listening: ${resumeItems.size} items")
            
            return resumeItems.map { entity ->
                val artworkUri = (entity.episodeImageUrl ?: entity.podcastImageUrl)
                    ?.let { android.net.Uri.parse(it) }
                
                // Rich subtitle with remaining time
                val subtitle = buildProgressSubtitle(entity.podcastName, entity.progressMs, entity.durationMs)
                
                // Progress bar extras for Android Auto
                val completionPercentage = if (entity.durationMs > 0) {
                    (entity.progressMs.toDouble() / entity.durationMs.toDouble()).coerceIn(0.0, 1.0)
                } else 0.0
                
                val completionStatus = when {
                    entity.isCompleted -> COMPLETION_STATUS_FULLY_PLAYED
                    entity.progressMs > 0 -> COMPLETION_STATUS_PARTIALLY_PLAYED
                    else -> COMPLETION_STATUS_NOT_PLAYED
                }
                
                val progressExtras = Bundle().apply {
                    putDouble(DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE, completionPercentage)
                    putInt(DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS, completionStatus)
                }
                
                android.util.Log.d("AutoBrowse", "Episode ${entity.episodeId}: progress=${(completionPercentage * 100).toInt()}%, status=$completionStatus")
                
                MediaItem.Builder()
                    .setMediaId("episode:${entity.episodeId}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(entity.episodeTitle)
                            .setSubtitle(subtitle)
                            .setArtist(subtitle)
                            .setArtworkUri(artworkUri)
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                            .setExtras(progressExtras)
                            .build()
                    )
                    .setUri(entity.episodeAudioUrl)
                    .build()
            }
        }

        private suspend fun getSubscriptionsChildren(): List<MediaItem> {
            val subscriptions = database.podcastDao().getSubscribedPodcastsList()
            android.util.Log.d("AutoBrowse", "Subscriptions: ${subscriptions.size} podcasts")
            
            return subscriptions.map { entity ->
                val artworkUri = android.net.Uri.parse(entity.imageUrl)
                
                MediaItem.Builder()
                    .setMediaId("$SUBSCRIPTION_PREFIX${entity.podcastId}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(entity.title)
                            .setSubtitle(entity.author)
                            .setArtist(entity.author)
                            .setArtworkUri(artworkUri)
                            .setIsPlayable(false)
                            .setIsBrowsable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                            .build()
                    )
                    .build()
            }
        }

        private suspend fun getHomeChildren(): List<MediaItem> {
            val listExtras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_KEY, CONTENT_STYLE_LIST)
            }
            val gridExtras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_GRID)
                putInt(CONTENT_STYLE_PLAYABLE_KEY, CONTENT_STYLE_LIST)
            }

            val newEpCount = try {
                database.podcastDao().getSubscribedPodcastsList().count { it.latestEpisode != null }
            } catch (e: Exception) { 0 }
            val newEpSubtitle = when {
                newEpCount == 0 -> "Subscribe to podcasts to see new drops"
                newEpCount == 1 -> "1 new from your subscriptions"
                else -> "$newEpCount new from your subscriptions"
            }

            return listOf(
                buildBrowsableItem(
                    id = HOME_CONTINUE_LISTENING_ID,
                    title = "Continue Listening",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = listExtras
                ),
                buildBrowsableItem(
                    id = HOME_SUBSCRIPTIONS_ID,
                    title = "Subscriptions",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = gridExtras
                ),
                buildBrowsableItem(
                    id = HOME_NEW_EPISODES_ID,
                    title = "What's New",
                    subtitle = newEpSubtitle,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = listExtras
                )
            )
        }
        
        /**
         * Explore tab: time-of-day picks + full genre directory.
         */
        private fun getExploreChildren(): List<MediaItem> {
            val listExtras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_KEY, CONTENT_STYLE_LIST)
            }
            
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val timeLabel = when (hour) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..22 -> "Evening"
                else -> "Late Night"
            }
            
            return listOf(
                buildBrowsableItem(
                    id = "explore_picks",
                    title = "$timeLabel Picks",
                    subtitle = "Curated for right now",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = listExtras
                ),
                buildBrowsableItem(
                    id = "explore_genres",
                    title = "Browse by Genre",
                    subtitle = "16 categories",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = listExtras
                )
            )
        }
        
        private fun getExplorePicksChildren(): List<MediaItem> {
            val listExtras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_KEY, CONTENT_STYLE_LIST)
            }
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            return getTimeBasedGenres(hour).map { (vibeId, title) ->
                buildBrowsableItem(
                    id = "explore_curated_$vibeId",
                    title = title,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = listExtras
                )
            }
        }
        
        private fun getGenresChildren(): List<MediaItem> {
            val listExtras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_KEY, CONTENT_STYLE_LIST)
            }
            return listOf(
                "true_crime" to "True Crime",
                "comedy" to "Comedy",
                "news" to "News & Politics",
                "technology" to "Technology",
                "science" to "Science",
                "health" to "Health & Wellness",
                "business" to "Business",
                "sports" to "Sports",
                "history" to "History",
                "society" to "Society & Culture",
                "education" to "Education",
                "arts" to "Arts & Entertainment",
                "music" to "Music",
                "fiction" to "Fiction & Drama",
                "kids" to "Kids & Family",
                "self_improvement" to "Self-Improvement"
            ).map { (genreId, title) ->
                buildBrowsableItem(
                    id = "explore_curated_$genreId",
                    title = title,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    extras = listExtras
                )
            }
        }
        
        private suspend fun getNewEpisodesChildren(): List<MediaItem> {
            // Use direct DAO query instead of Flow to avoid hanging
            val subscriptions = try {
                database.podcastDao().getSubscribedPodcastsList()
            } catch (e: Exception) {
                return emptyList()
            }
            
            // Get completed episode IDs to exclude (matches phone app behavior)
            val completedIds = try {
                database.listeningHistoryDao().getCompletedEpisodeIds().toSet()
            } catch (e: Exception) { emptySet() }
            
            // Extract the newest episode from each subscription, excluding completed ones
            val newEpisodes = subscriptions
                .mapNotNull { entity ->
                    entity.latestEpisode?.let { ep ->
                        if (ep.id !in completedIds) ep to entity else null
                    }
                }
                .sortedByDescending { (ep, _) -> ep.publishedDate }
                .take(20)
                
            if (newEpisodes.isEmpty()) return emptyList()
            
            val items = mutableListOf<MediaItem>()
            
            // Inject Virtual Play All Item
            items.add(
                MediaItem.Builder()
                    .setMediaId(PLAY_ALL_NEW_EPISODES_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Play All (${newEpisodes.size} episodes)")
                            .setArtist("Plays latest from each subscription")
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                            .build()
                    )
                    .build()
            )
            
            items.addAll(
                newEpisodes.map { (ep, pod) ->
                    val artworkUri = android.net.Uri.parse(ep.imageUrl ?: pod.imageUrl)
                    
                    MediaItem.Builder()
                        .setMediaId("episode:${ep.id}")
                        .setUri(ep.audioUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(ep.title)
                                .setSubtitle(pod.title)
                                .setArtist(pod.author)
                                .setArtworkUri(artworkUri)
                                .setIsPlayable(true)
                                .setIsBrowsable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                .build()
                        )
                        .build()
                }
            )
            
            return items
        }
        
        private suspend fun getCuratedChildren(vibeId: String): List<MediaItem> {
            val curatedPodcasts = podcastRepository.getCuratedPodcasts(vibeId)
            android.util.Log.d("AutoBrowse", "Curated $vibeId: ${curatedPodcasts.size} podcasts")
            
            return curatedPodcasts.map { podcast ->
                val artworkUri = android.net.Uri.parse(podcast.imageUrl)
                
                MediaItem.Builder()
                    .setMediaId("$SUBSCRIPTION_PREFIX${podcast.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(podcast.title)
                            .setSubtitle(podcast.artist)
                            .setArtist(podcast.artist)
                            .setArtworkUri(artworkUri)
                            .setIsPlayable(false)
                            .setIsBrowsable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                            .build()
                    )
                    .build()
            }
        }

        private suspend fun getPodcastEpisodes(podcastId: String): List<MediaItem> {
            android.util.Log.d("AutoBrowse", "Fetching episodes for podcast: $podcastId")
            
            // Get podcast details for artwork fallback
            val podcastEntity = database.podcastDao().getPodcast(podcastId)
            val podcastArtwork = podcastEntity?.imageUrl
            
            // Fetch latest episodes (limit to 50 for Auto performance)
            val episodes = podcastRepository.getEpisodesPaginated(podcastId, limit = 50, sort = "newest")
            android.util.Log.d("AutoBrowse", "Got ${episodes.episodes.size} episodes for $podcastId")
            
            return episodes.episodes.map { episode ->
                val artworkUri = (episode.imageUrl ?: podcastArtwork)
                    ?.let { android.net.Uri.parse(it) }
                
                // Show duration in subtitle
                val durationText = formatDuration(episode.duration.toLong() * 1000)
                val subtitle = "${podcastEntity?.title ?: "Podcast"} · $durationText"
                
                MediaItem.Builder()
                    .setMediaId("episode:${episode.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(episode.title)
                            .setSubtitle(subtitle)
                            .setArtist(podcastEntity?.author ?: "")
                            .setArtworkUri(artworkUri)
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                            .build()
                    )
                    .setUri(episode.audioUrl)
                    .build()
            }
        }

        // ============= Helpers =============

        private fun buildBrowsableItem(
            id: String,
            title: String,
            subtitle: String? = null,
            mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            extras: Bundle? = null,
            artworkUri: android.net.Uri? = null
        ): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setArtist(subtitle)
                        .setArtworkUri(artworkUri)
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(mediaType)
                        .apply { if (extras != null) setExtras(extras) }
                        .build()
                )
                .build()
        }

        /**
         * Build a subtitle showing remaining time, e.g. "Podcast Name · 35 min left"
         */
        private fun buildProgressSubtitle(podcastName: String, progressMs: Long, durationMs: Long): String {
            if (durationMs <= 0) return podcastName
            val remainingMs = (durationMs - progressMs).coerceAtLeast(0)
            val remainingMin = remainingMs / 60000
            return when {
                remainingMin > 60 -> {
                    val hours = remainingMin / 60
                    val mins = remainingMin % 60
                    "$podcastName · ${hours}h ${mins}m left"
                }
                remainingMin > 0 -> "$podcastName · ${remainingMin} min left"
                else -> "$podcastName · Almost done"
            }
        }

        /**
         * Format duration in ms to human-readable string, e.g. "45 min" or "1h 20m"
         */
        private fun formatDuration(durationMs: Long): String {
            if (durationMs <= 0) return ""
            val totalMin = durationMs / 60000
            return when {
                totalMin > 60 -> {
                    val hours = totalMin / 60
                    val mins = totalMin % 60
                    "${hours}h ${mins}m"
                }
                totalMin > 0 -> "${totalMin} min"
                else -> "< 1 min"
            }
        }
    }
}
