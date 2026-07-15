package cx.aswin.boxcast.core.data.service

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
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
import androidx.annotation.VisibleForTesting
import cx.aswin.boxcast.core.data.service.auto.AutoArtworkRepository
import cx.aswin.boxcast.core.data.service.auto.AutoBrowseContract
import cx.aswin.boxcast.core.data.service.auto.AutoMediaItemFactory
import cx.aswin.boxcast.core.data.service.auto.AutoPlayableSpec
import cx.aswin.boxcast.core.data.playback.PlaybackSkipPolicy
import cx.aswin.boxcast.core.data.ranking.RankingObjective
import cx.aswin.boxcast.core.data.toScorable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
private const val LEARN_PREFIX = "learn:"
private const val EPISODE_PREFIX = "episode:"
private const val QUEUE_PREFIX = "queue:"
private const val GENRE_TRUE_CRIME = "True Crime"
private const val GENRE_TV_FILM = "TV & Film"
private const val OUTRO_REARM_HYSTERESIS_MS = 1_000L
private const val EFFECTIVE_END_WATCHDOG_MS = 1_250L
private const val TRUE_END_SEEK_MARGIN_MS = 250L

class BoxLorePlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var exoPlayer: ExoPlayer? = null
    private lateinit var seekBackAction: androidx.media3.session.CommandButton
    private lateinit var seekForwardAction: androidx.media3.session.CommandButton
    private lateinit var likeAction: androidx.media3.session.CommandButton
    private lateinit var addToQueueAction: androidx.media3.session.CommandButton
    private lateinit var markCompleteAction: androidx.media3.session.CommandButton
    @Volatile
    private var autoCollageUris: Map<String, android.net.Uri> = emptyMap()
    
    private val userPreferencesRepository by lazy {
        cx.aswin.boxcast.core.data.UserPreferencesRepository(this)
    }
    @VisibleForTesting internal var mainDispatcher: CoroutineDispatcher = Dispatchers.Main
    @VisibleForTesting internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val serviceScope by lazy { CoroutineScope(mainDispatcher + SupervisorJob()) }

    // Lazy-init database & repos (avoid creating them if Auto is never used)
    private val database by lazy {
        cx.aswin.boxcast.core.data.database.BoxLoreDatabase.getDatabase(this)
    }
    private val podcastRepository by lazy {
        val prefs = getSharedPreferences("boxcast_api_config", MODE_PRIVATE)
        val baseUrl = prefs.getString("base_url", null) ?: cx.aswin.boxcast.core.data.BuildConfig.BOXCAST_API_BASE_URL
        val publicKey = prefs.getString("public_key", null) ?: cx.aswin.boxcast.core.data.BuildConfig.BOXCAST_PUBLIC_KEY
        cx.aswin.boxcast.core.data.PodcastRepository(baseUrl, publicKey, this)
    }
    private val subscriptionRepository by lazy {
        cx.aswin.boxcast.core.data.SubscriptionRepository(database.podcastDao())
    }
    private val queueSkipMemory by lazy {
        cx.aswin.boxcast.core.data.QueueSkipMemory.fromContext(this)
    }
    private val rankingFeedbackRepository by lazy {
        cx.aswin.boxcast.core.data.ranking.RankingFeedbackRepository.getInstance(this)
    }
    private val adaptiveCandidateScorer by lazy {
        cx.aswin.boxcast.core.data.ranking.AdaptiveCandidateScorer.getInstance(this)
    }
    private val smartQueueSources by lazy {
        cx.aswin.boxcast.core.data.DefaultSmartQueueSources(
            context = this,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }
    private val smartQueueEngine by lazy {
        cx.aswin.boxcast.core.data.DefaultSmartQueueEngine(
            sources = smartQueueSources,
            skipMemory = queueSkipMemory,
            adaptiveScorer = adaptiveCandidateScorer,
        )
    }
    private val queueRepository by lazy {
        cx.aswin.boxcast.core.data.QueueRepository(database, podcastRepository)
    }
    private var isRefilling = false
    private val QUEUE_MAX_SIZE = 50
    
    @Volatile private var cachedSkipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS
    @Volatile private var cachedSkipEndingMs = PlaybackSkipPolicy.DEFAULT_SKIP_ENDING_MS
    @Volatile private var cachedSeekBackwardMs = PlaybackSkipPolicy.DEFAULT_SEEK_BACKWARD_MS
    @Volatile private var cachedSeekForwardMs = PlaybackSkipPolicy.DEFAULT_SEEK_FORWARD_MS

    private var playbackActivationGeneration = 0L
    private var activeLifecycleEpisodeId: String? = null
    private var activeLifecycleMediaItem: MediaItem? = null
    private var activeLifecycleDurationMs = 0L
    private var activationInitialPositionMs = 0L
    private var effectiveSkipBeginningMs = 0L
    private var effectiveSkipEndingMs = 0L
    private var introTargetResolved = false
    private var pendingIntroTargetMs: Long? = null
    private var pendingIntroSeekSource: String? = null
    private var introApplied = false
    private var introCancelledByUser = false
    private var automaticSeekSource: String? = null
    private var outroArmed = false
    private var lastOutroPositionMs = 0L
    private var lastOutroBoundaryMs: Long? = null
    private var effectiveEndLatch = false
    private var claimedCompletionGeneration = -1L
    private var completionTelemetryGeneration = -1L
    private var sleepRestoreInProgress = false
    private var outroMonitorJob: kotlinx.coroutines.Job? = null
    private var effectiveEndWatchdogJob: kotlinx.coroutines.Job? = null
    private var completionPersistenceJob: kotlinx.coroutines.Job? = null

    private val firedHeartbeats = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var activePlaybackStartTimeMs: Long = 0L

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
    private var playbackSessionContextType: String? = null
    private var playbackSessionContextSourceId: String? = null
    
    private var playbackSessionBufferingStartTimeMs: Long = 0L
    private var playbackSessionTotalBufferedTimeMs: Long = 0L
    // Remembers the episode that was paused so a subsequent play() with no explicit source
    // (e.g. from the notification / lock screen / Bluetooth) can be attributed as a resume.
    private var lastPausedEpisodeId: String? = null

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
            .setUserAgent(androidx.media3.common.util.Util.getUserAgent(this, "BoxLore"))
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
            .setSeekForwardIncrementMs(PlaybackSkipPolicy.DEFAULT_SEEK_FORWARD_MS)
            .setSeekBackIncrementMs(PlaybackSkipPolicy.DEFAULT_SEEK_BACKWARD_MS)
            .build()
            
        this.exoPlayer = player
        serviceScope.launch {
            userPreferencesRepository.playbackSpeedStream.collectLatest { savedSpeed ->
                player.setPlaybackSpeed(savedSpeed.coerceIn(0.5f, 3.0f))
            }
        }
        serviceScope.launch {
            userPreferencesRepository.skipBeginningMsStream.collectLatest { value ->
                cachedSkipBeginningMs = PlaybackSkipPolicy.sanitizeTrim(value)
                refreshActiveSkipConfiguration(player, preferenceChanged = true)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.skipEndingMsStream.collectLatest { value ->
                cachedSkipEndingMs = PlaybackSkipPolicy.sanitizeTrim(value)
                refreshActiveSkipConfiguration(player, preferenceChanged = true)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.seekBackwardMsStream.collectLatest { value ->
                cachedSeekBackwardMs = PlaybackSkipPolicy.sanitizeSeekBackward(value)
                updateSeekCommandButtons()
            }
        }
        serviceScope.launch {
            userPreferencesRepository.seekForwardMsStream.collectLatest { value ->
                cachedSeekForwardMs = PlaybackSkipPolicy.sanitizeSeekForward(value)
                updateSeekCommandButtons()
            }
        }
            
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onPlayerError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("BoxCastPlayer", "onPlayerError: ${error.errorCodeName}", error)
                val podcastId = playbackSessionPodcastId
                val episodeId = playbackSessionEpisodeId
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackError(
                    errorCode = error.errorCodeName,
                    errorMessage = error.message ?: "Unknown",
                    podcastId = podcastId,
                    episodeId = episodeId,
                    podcastName = playbackSessionPodcastName,
                    episodeTitle = playbackSessionEpisodeTitle
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
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    updateHeartbeatsForPosition(newPosition.positionMs, playbackSessionTotalDurationMs)
                    val source = cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.consumeSeekSource()
                    val isLifecycleSeek = source == automaticSeekSource ||
                        source == "resume" ||
                        source == "transition" ||
                        source == "skip_beginning_auto" ||
                        source == "skip_ending_auto"
                    automaticSeekSource = null
                    if (
                        isLifecycleSeek &&
                        !introTargetResolved &&
                        newPosition.positionMs > 0L
                    ) {
                        activationInitialPositionMs = newPosition.positionMs
                    }
                    if (!isLifecycleSeek) {
                        introCancelledByUser = true
                        pendingIntroTargetMs = null
                        pendingIntroSeekSource = null
                        updateOutroArmingAfterManualSeek(newPosition.positionMs, player.duration)
                    }
                    android.util.Log.d("BoxCastPlayer", "onPositionDiscontinuity (SEEK): source=$source, reason=$reason, from ${oldPosition.positionMs} to ${newPosition.positionMs}")
                    val epId = playbackSessionEpisodeId
                    if (!isLifecycleSeek && epId != null) {
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackSeeked(
                            podcastId = playbackSessionPodcastId,
                            podcastName = playbackSessionPodcastName,
                            episodeId = epId,
                            episodeTitle = playbackSessionEpisodeTitle,
                            fromPositionSeconds = oldPosition.positionMs / 1000f,
                            toPositionSeconds = newPosition.positionMs / 1000f,
                            totalDurationSeconds = playbackSessionTotalDurationMs / 1000f,
                            seekSource = source
                        )
                    }
                }
            }
        })

        // SmartQueue auto-refill: when queue runs low, fetch more episodes
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                handleMediaItemTransition(player, mediaItem, reason)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    val pauseReason = when (reason) {
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "headphone_disconnected"
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "audio_focus_loss_permanent"
                        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "user_voluntary"
                        else -> "user_voluntary"
                    }
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setPauseReason(pauseReason)
                    android.util.Log.d("BoxCastPlayer", "onPlayWhenReadyChanged: playWhenReady=false, reason=$reason, pauseReason=$pauseReason")
                }
            }

            override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setPauseReason("audio_focus_loss_transient")
                    android.util.Log.d("BoxCastPlayer", "onPlaybackSuppressionReasonChanged: transient audio focus loss")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        maybeApplyPendingIntro(player)
                        refreshOutroBoundary(player, preferenceChanged = false)
                    }
                    Player.STATE_ENDED -> handleNaturalStateEnded(player)
                    Player.STATE_IDLE -> if (!player.playWhenReady) {
                        resetLifecycleGuards(null, 0L)
                    }
                }
            }

            override fun onTimelineChanged(
                timeline: androidx.media3.common.Timeline,
                reason: Int,
            ) {
                if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
                val currentItem = player.currentMediaItem
                if (currentItem == null) {
                    resetLifecycleGuards(null, 0L)
                } else if (
                    lifecycleEpisodeId(currentItem) != activeLifecycleEpisodeId ||
                    currentItem !== activeLifecycleMediaItem
                ) {
                    activateMediaItem(player, currentItem)
                }
            }
        })
        
        // Progress saver + resume-seek + Telemetry
        var progressSaverJob: kotlinx.coroutines.Job? = null
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val currentItem = player.currentMediaItem
                val episodeId = currentItem?.mediaId?.removePrefix(LEARN_PREFIX)?.removePrefix(EPISODE_PREFIX)?.removePrefix(QUEUE_PREFIX)
                
                if (isPlaying) {
                    // Telemetry: Started playing
                    if (episodeId != null) startPlaybackSession(episodeId, currentItem)
                    activePlaybackStartTimeMs = System.currentTimeMillis()

                    maybeApplyPendingIntro(player)
                    startOutroMonitor(player)
                    progressSaverJob?.cancel()
                    progressSaverJob = serviceScope.launch {
                        startPlaybackTicker(player)
                    }
                } else {
                    stopOutroMonitor()
                    // Telemetry: Paused playing 
                    // Only end session if explicitly paused, stopped, or transiently suppressed. Ignore buffering/seeking.
                    val shouldEndSession = !player.playWhenReady || 
                                           player.playbackState == Player.STATE_ENDED ||
                                           player.playbackState == Player.STATE_IDLE ||
                                           player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
                    
                    if (shouldEndSession) {
                        // Remember what was paused so a bare remote play() (notification /
                        // lock screen) that restarts this same episode is tagged as a resume.
                        lastPausedEpisodeId = episodeId
                        endPlaybackSession(forceCompleted = false)
                    }

                    // Save one final time on pause
                    progressSaverJob?.cancel()
                    progressSaverJob = null
                    serviceScope.launch {
                        saveProgressOnce(player)
                        activePlaybackStartTimeMs = 0L
                    }
                }
            }
        })

        val intent = Intent()
        intent.component = android.content.ComponentName(packageName, "cx.aswin.boxcast.MainActivity")
        intent.putExtra("EXTRA_OPEN_PLAYER", true) // Notification Click -> Open Player
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun getSeekForwardIncrement(): Long = cachedSeekForwardMs

            override fun getSeekBackIncrement(): Long = cachedSeekBackwardMs

            override fun seekForward() {
                seekByConfiguredIncrement(player, cachedSeekForwardMs, "seek_forward")
            }

            override fun seekBack() {
                seekByConfiguredIncrement(player, -cachedSeekBackwardMs, "seek_backward")
            }

            override fun seekToNext() {
                handleSkipNext()
            }

            override fun seekToNextMediaItem() {
                handleSkipNext()
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

        rebuildSeekCommandButtons()

        likeAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(getString(cx.aswin.boxcast.core.data.R.string.auto_like))
            .setIconResId(cx.aswin.boxcast.core.data.R.drawable.ic_auto_like)
            .setSessionCommand(
                androidx.media3.session.SessionCommand(
                    AutoBrowseContract.COMMAND_TOGGLE_LIKE,
                    Bundle.EMPTY,
                ),
            )
            .build()

        addToQueueAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(getString(cx.aswin.boxcast.core.data.R.string.auto_add_queue))
            .setIconResId(cx.aswin.boxcast.core.data.R.drawable.ic_auto_queue_add)
            .setSessionCommand(
                androidx.media3.session.SessionCommand(
                    AutoBrowseContract.COMMAND_ADD_TO_QUEUE,
                    Bundle.EMPTY,
                ),
            )
            .build()

        markCompleteAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(getString(cx.aswin.boxcast.core.data.R.string.auto_mark_complete))
            .setIconResId(cx.aswin.boxcast.core.data.R.drawable.ic_auto_complete)
            .setSessionCommand(
                androidx.media3.session.SessionCommand(
                    AutoBrowseContract.COMMAND_MARK_COMPLETE,
                    Bundle.EMPTY,
                ),
            )
            .build()

        val coilBitmapLoader = CoilBitmapLoader(this, serviceScope)
        val cacheBitmapLoader = androidx.media3.session.CacheBitmapLoader(coilBitmapLoader)

        mediaSession = MediaLibrarySession.Builder(this, forwardingPlayer, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .setCustomLayout(listOf(seekBackAction, seekForwardAction, markCompleteAction))
            .setCommandButtonsForMediaItems(
                listOf(likeAction, addToQueueAction, markCompleteAction),
            )
            .setBitmapLoader(cacheBitmapLoader)
            .build()
        prewarmAutoCollages()
    }

    private fun rebuildSeekCommandButtons() {
        seekBackAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(
                getString(
                    cx.aswin.boxcast.core.data.R.string.auto_seek_back,
                    cachedSeekBackwardMs / 1_000L,
                ),
            )
            .setIconResId(cx.aswin.boxcast.core.designsystem.R.drawable.rounded_replay_24)
            .setSessionCommand(androidx.media3.session.SessionCommand("SEEK_BACK", Bundle.EMPTY))
            .build()
        seekForwardAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(
                getString(
                    cx.aswin.boxcast.core.data.R.string.auto_seek_forward,
                    cachedSeekForwardMs / 1_000L,
                ),
            )
            .setIconResId(cx.aswin.boxcast.core.designsystem.R.drawable.rounded_forward_24)
            .setSessionCommand(androidx.media3.session.SessionCommand("SEEK_FORWARD", Bundle.EMPTY))
            .build()
    }

    private fun updateSeekCommandButtons() {
        rebuildSeekCommandButtons()
        if (::markCompleteAction.isInitialized) {
            mediaSession?.setCustomLayout(
                listOf(seekBackAction, seekForwardAction, markCompleteAction),
            )
        }
    }

    private fun seekByConfiguredIncrement(player: ExoPlayer, deltaMs: Long, source: String) {
        val upperBound = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, upperBound)
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource(source)
        player.seekTo(target)
        android.util.Log.d("BoxCastPlayer", "$source to ${target}ms")
    }

    private fun lifecycleEpisodeId(item: MediaItem?): String? =
        item?.mediaId
            ?.removePrefix(LEARN_PREFIX)
            ?.removePrefix(EPISODE_PREFIX)
            ?.removePrefix(QUEUE_PREFIX)

    private fun handleMediaItemTransition(
        player: ExoPlayer,
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        android.util.Log.d(
            "BoxCastPlayer",
            "onMediaItemTransition: mediaId=${mediaItem?.mediaId}, title=${mediaItem?.mediaMetadata?.title}, artworkUri=${mediaItem?.mediaMetadata?.artworkUri}, reason=$reason",
        )
        val previousEpisodeId = activeLifecycleEpisodeId
        val previousDurationMs = activeLifecycleDurationMs
        val wasAutoCompleted = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        val wasServiceOwnedNaturalAdvance =
            previousEpisodeId ==
                cx.aswin.boxcast.core.data.PlaybackLifecycleSignals
                    .serviceOwnedNaturalAdvanceEpisodeId

        completePreviousItemTransition(
            previousEpisodeId = previousEpisodeId,
            previousDurationMs = previousDurationMs,
            wasAutoCompleted = wasAutoCompleted,
        )
        if (restoreLifecycleAfterSleepTransition(player, mediaItem)) return
        if (
            wasAutoCompleted &&
            cx.aswin.boxcast.core.data.SleepTimerHolder.sleepAtEndOfEpisode
        ) {
            enforceEndOfEpisodeSleepAfterTransition(player, previousDurationMs)
            return
        }

        activateMediaItem(player, mediaItem)
        updateTransitionPlaybackSession(
            player = player,
            mediaItem = mediaItem,
            reason = reason,
            wasServiceOwnedNaturalAdvance = wasServiceOwnedNaturalAdvance,
        )
        maybeRefillQueueAfterTransition(player, reason)
    }

    private fun completePreviousItemTransition(
        previousEpisodeId: String?,
        previousDurationMs: Long,
        wasAutoCompleted: Boolean,
    ) {
        if (wasAutoCompleted && previousEpisodeId != null) {
            claimNaturalCompletion(previousEpisodeId, previousDurationMs)
        } else {
            endPlaybackSession(forceCompleted = false, isTransition = true)
        }
    }

    private fun restoreLifecycleAfterSleepTransition(
        player: ExoPlayer,
        mediaItem: MediaItem?,
    ): Boolean {
        if (!sleepRestoreInProgress) return false
        sleepRestoreInProgress = false
        resetLifecycleGuards(mediaItem, player.currentPosition)
        return true
    }

    private fun updateTransitionPlaybackSession(
        player: ExoPlayer,
        mediaItem: MediaItem?,
        reason: Int,
        wasServiceOwnedNaturalAdvance: Boolean,
    ) {
        if (!player.isPlaying) {
            activePlaybackStartTimeMs = 0L
            return
        }
        val episodeId = lifecycleEpisodeId(mediaItem)
        // A transition into a playing state with no explicit source is either the
        // queue auto-advancing to the next episode, or a user skip (next/prev).
        val transitionSource = when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "queue_auto_advance"
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ->
                if (wasServiceOwnedNaturalAdvance) {
                    "queue_auto_advance"
                } else {
                    "queue_skip"
                }
            else -> null
        }
        if (episodeId != null) startPlaybackSession(episodeId, mediaItem, transitionSource)
        activePlaybackStartTimeMs = System.currentTimeMillis()
    }

    private fun maybeRefillQueueAfterTransition(player: ExoPlayer, reason: Int) {
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        android.util.Log.d("AutoQueue", "onMediaItemTransition: remaining=$remaining, reason=$reason")
        val currentItem = player.currentMediaItem
        val isLearn = currentItem?.mediaId?.startsWith(LEARN_PREFIX) == true
        // Sleep-timer guard: when playback will stop at the end of this episode,
        // refilling would fetch episodes the player is about to abandon.
        val sleepingAtEndOfEpisode =
            cx.aswin.boxcast.core.data.SleepTimerHolder.sleepAtEndOfEpisode

        if (
            remaining <= 2 &&
            !isRefilling &&
            player.mediaItemCount > 0 &&
            !isLearn &&
            !sleepingAtEndOfEpisode
        ) {
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

    private fun resetLifecycleGuards(mediaItem: MediaItem?, initialPositionMs: Long) {
        playbackActivationGeneration++
        activeLifecycleEpisodeId = lifecycleEpisodeId(mediaItem)
        activeLifecycleMediaItem = mediaItem
        activeLifecycleDurationMs = 0L
        activationInitialPositionMs = initialPositionMs.coerceAtLeast(0L)
        effectiveSkipBeginningMs = 0L
        effectiveSkipEndingMs = 0L
        cx.aswin.boxcast.core.data.PlaybackLifecycleSignals.effectiveSkipEndingMs = null
        introTargetResolved = false
        pendingIntroTargetMs = null
        pendingIntroSeekSource = null
        introApplied = false
        introCancelledByUser = false
        automaticSeekSource = null
        outroArmed = false
        lastOutroPositionMs = initialPositionMs.coerceAtLeast(0L)
        lastOutroBoundaryMs = null
        effectiveEndLatch = false
        stopOutroMonitor()
        effectiveEndWatchdogJob?.cancel()
        effectiveEndWatchdogJob = null
    }

    private fun activateMediaItem(player: ExoPlayer, mediaItem: MediaItem?) {
        resetLifecycleGuards(mediaItem, player.currentPosition)
        refreshActiveSkipConfiguration(player, preferenceChanged = false)
    }

    private fun refreshActiveSkipConfiguration(
        player: ExoPlayer,
        preferenceChanged: Boolean,
    ) {
        val episodeId = activeLifecycleEpisodeId ?: return
        val generation = playbackActivationGeneration
        serviceScope.launch {
            val (history, effectiveTrim) = resolveActiveSkipConfiguration(episodeId)

            if (
                generation != playbackActivationGeneration ||
                episodeId != activeLifecycleEpisodeId
            ) {
                return@launch
            }

            effectiveSkipBeginningMs = effectiveTrim.skipBeginningMs
            effectiveSkipEndingMs = effectiveTrim.skipEndingMs
            // Duration is required to decide whether the configured trims leave a safe
            // playable window. Keep legacy completion behavior until that is known.
            cx.aswin.boxcast.core.data.PlaybackLifecycleSignals.effectiveSkipEndingMs = 0L

            if (!introTargetResolved) {
                resolveActiveIntroTarget(history)
            }

            refreshOutroBoundary(player, preferenceChanged)
            maybeApplyPendingIntro(player)
        }
    }

    private suspend fun resolveActiveSkipConfiguration(
        episodeId: String,
    ): Pair<
        cx.aswin.boxcast.core.data.database.ListeningHistoryEntity?,
        PlaybackSkipPolicy.EffectiveTrim,
    > {
        val history = runCatching {
            database.listeningHistoryDao().getHistoryItem(episodeId)
        }.getOrNull()
        val isBriefing = episodeId.startsWith("briefing_")
        val podcastId = history?.podcastId?.takeIf { it.isNotBlank() }
            ?: if (isBriefing) {
                null
            } else {
                runCatching { findPodcastIdForEpisode(episodeId) }.getOrNull()
            }
        val podcast = podcastId?.let { id ->
            runCatching { database.podcastDao().getPodcast(id) }.getOrNull()
        }
        val effectiveTrim = if (isBriefing) {
            PlaybackSkipPolicy.EffectiveTrim(
                skipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS,
                skipEndingMs = PlaybackSkipPolicy.DEFAULT_SKIP_ENDING_MS,
            )
        } else {
            PlaybackSkipPolicy.resolveEffectiveTrim(
                globalSkipBeginningMs = cachedSkipBeginningMs,
                globalSkipEndingMs = cachedSkipEndingMs,
                podcastSkipBeginningOverrideMs = podcast?.skipBeginningOverrideMs,
                podcastSkipEndingOverrideMs = podcast?.skipEndingOverrideMs,
            )
        }
        return history to effectiveTrim
    }

    private fun resolveActiveIntroTarget(
        history: cx.aswin.boxcast.core.data.database.ListeningHistoryEntity?,
    ) {
        val explicitStartMs = activationInitialPositionMs.takeIf { it > 0L }
        val initialPosition = PlaybackSkipPolicy.resolveInitialPosition(
            explicitPositionMs = explicitStartMs,
            savedProgressMs = history?.progressMs ?: 0L,
            isCompleted = history?.isCompleted == true,
            skipBeginningMs = effectiveSkipBeginningMs,
        )
        pendingIntroTargetMs = when (initialPosition.reason) {
            PlaybackSkipPolicy.InitialPositionReason.RESUME,
            PlaybackSkipPolicy.InitialPositionReason.SKIP_BEGINNING -> initialPosition.positionMs
            PlaybackSkipPolicy.InitialPositionReason.EXPLICIT,
            PlaybackSkipPolicy.InitialPositionReason.START -> null
        }
        pendingIntroSeekSource = when (initialPosition.reason) {
            PlaybackSkipPolicy.InitialPositionReason.RESUME -> "resume"
            PlaybackSkipPolicy.InitialPositionReason.SKIP_BEGINNING -> "skip_beginning_auto"
            PlaybackSkipPolicy.InitialPositionReason.EXPLICIT,
            PlaybackSkipPolicy.InitialPositionReason.START -> null
        }
        introApplied =
            initialPosition.reason == PlaybackSkipPolicy.InitialPositionReason.EXPLICIT
        introTargetResolved = true
    }

    private fun maybeApplyPendingIntro(player: ExoPlayer) {
        if (
            introApplied ||
            introCancelledByUser ||
            !introTargetResolved ||
            player.playbackState != Player.STATE_READY
        ) {
            return
        }
        val durationMs = player.duration
        if (durationMs <= 0L || durationMs == androidx.media3.common.C.TIME_UNSET) return
        val targetMs = pendingIntroTargetMs
        val source = pendingIntroSeekSource
        introApplied = true
        pendingIntroTargetMs = null
        pendingIntroSeekSource = null
        if (targetMs == null || targetMs <= 0L) return
        if (
            source == "skip_beginning_auto" &&
            !PlaybackSkipPolicy.hasSafePlayableWindow(
                durationMs = durationMs,
                skipBeginningMs = effectiveSkipBeginningMs,
                skipEndingMs = effectiveSkipEndingMs,
            )
        ) {
            return
        }
        val clampedTargetMs = targetMs.coerceAtMost((durationMs - 1L).coerceAtLeast(0L))
        if (clampedTargetMs <= 0L || player.currentPosition == clampedTargetMs) return
        performAutomaticSeek(player, clampedTargetMs, source ?: "resume")
    }

    private fun performAutomaticSeek(player: ExoPlayer, targetMs: Long, source: String) {
        automaticSeekSource = source
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource(source)
        player.seekTo(targetMs)
    }

    private fun refreshOutroBoundary(player: ExoPlayer, preferenceChanged: Boolean) {
        val durationMs = player.duration
        activeLifecycleDurationMs = durationMs.takeIf {
            it > 0L && it != androidx.media3.common.C.TIME_UNSET
        } ?: 0L
        if (
            activeLifecycleEpisodeId == playbackSessionEpisodeId &&
            activeLifecycleDurationMs > 0L
        ) {
            playbackSessionTotalDurationMs = activeLifecycleDurationMs
        }
        cx.aswin.boxcast.core.data.PlaybackLifecycleSignals.effectiveSkipEndingMs =
            effectiveEndingTrimForCompletion(activeLifecycleDurationMs)
        val oldBoundaryMs = lastOutroBoundaryMs
        val newBoundaryMs = calculateOutroBoundary(activeLifecycleDurationMs)
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        lastOutroBoundaryMs = newBoundaryMs

        if (newBoundaryMs == null) {
            outroArmed = false
            lastOutroPositionMs = positionMs
            return
        }

        val boundaryMoved = oldBoundaryMs != null && oldBoundaryMs != newBoundaryMs
        if ((preferenceChanged || boundaryMoved) && positionMs >= newBoundaryMs) {
            // A preference/duration update must not manufacture a crossing behind the playhead.
            outroArmed = false
        } else if (oldBoundaryMs == null) {
            outroArmed = positionMs < newBoundaryMs
        }
        lastOutroPositionMs = positionMs
    }

    private fun calculateOutroBoundary(durationMs: Long): Long? {
        if (
            !PlaybackSkipPolicy.hasSafePlayableWindow(
                durationMs = durationMs,
                skipBeginningMs = effectiveSkipBeginningMs,
                skipEndingMs = effectiveSkipEndingMs,
            )
        ) {
            return null
        }
        return PlaybackSkipPolicy.outroBoundaryMs(durationMs, effectiveSkipEndingMs)
    }

    private fun effectiveEndingTrimForCompletion(durationMs: Long): Long =
        effectiveSkipEndingMs.takeIf {
            it > 0L &&
                PlaybackSkipPolicy.hasSafePlayableWindow(
                    durationMs = durationMs,
                    skipBeginningMs = effectiveSkipBeginningMs,
                    skipEndingMs = it,
                )
        } ?: 0L

    private fun updateOutroArmingAfterManualSeek(positionMs: Long, durationMs: Long) {
        activeLifecycleDurationMs = durationMs.takeIf {
            it > 0L && it != androidx.media3.common.C.TIME_UNSET
        } ?: 0L
        val boundaryMs = calculateOutroBoundary(activeLifecycleDurationMs)
        lastOutroBoundaryMs = boundaryMs
        outroArmed = boundaryMs != null &&
            positionMs < boundaryMs - OUTRO_REARM_HYSTERESIS_MS
        lastOutroPositionMs = positionMs
    }

    private fun startOutroMonitor(player: ExoPlayer) {
        if (outroMonitorJob?.isActive == true) return
        outroMonitorJob = serviceScope.launch {
            while (player.isPlaying) {
                checkOutroCrossing(player)
                kotlinx.coroutines.delay(200L)
            }
        }
    }

    private fun stopOutroMonitor() {
        outroMonitorJob?.cancel()
        outroMonitorJob = null
    }

    private fun checkOutroCrossing(player: ExoPlayer) {
        if (effectiveEndLatch) return
        val durationMs = player.duration
        if (durationMs != activeLifecycleDurationMs) {
            refreshOutroBoundary(player, preferenceChanged = false)
            return
        }
        val boundaryMs = calculateOutroBoundary(durationMs)
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        if (PlaybackSkipPolicy.isNaturalOutroCrossing(
                previousPositionMs = lastOutroPositionMs,
                currentPositionMs = positionMs,
                durationMs = durationMs,
                skipBeginningMs = effectiveSkipBeginningMs,
                skipEndingMs = effectiveSkipEndingMs,
                armed = outroArmed && boundaryMs != null,
                isPlaying = player.isPlaying,
            )
        ) {
            finishAtEffectiveEnd(player)
            return
        }
        lastOutroPositionMs = positionMs
    }

    private fun finishAtEffectiveEnd(player: ExoPlayer) {
        if (effectiveEndLatch) return
        effectiveEndLatch = true
        stopOutroMonitor()
        val episodeId = activeLifecycleEpisodeId ?: return
        claimNaturalCompletion(episodeId, player.duration)

        if (cx.aswin.boxcast.core.data.SleepTimerHolder.sleepAtEndOfEpisode) {
            clearEndOfEpisodeSleep()
            player.pause()
            return
        }

        player.playWhenReady = true
        val trueEndTargetMs = (player.duration - TRUE_END_SEEK_MARGIN_MS).coerceAtLeast(0L)
        performAutomaticSeek(player, trueEndTargetMs, "skip_ending_auto")
        val generation = playbackActivationGeneration
        effectiveEndWatchdogJob?.cancel()
        effectiveEndWatchdogJob = serviceScope.launch {
            kotlinx.coroutines.delay(EFFECTIVE_END_WATCHDOG_MS)
            if (
                generation != playbackActivationGeneration ||
                episodeId != lifecycleEpisodeId(player.currentMediaItem)
            ) {
                return@launch
            }
            completionPersistenceJob?.join()
            if (player.hasNextMediaItem()) {
                cx.aswin.boxcast.core.data.PlaybackLifecycleSignals
                    .serviceOwnedNaturalAdvanceEpisodeId = episodeId
                player.seekToNextMediaItem()
                serviceScope.launch {
                    kotlinx.coroutines.delay(2_000L)
                    if (
                        cx.aswin.boxcast.core.data.PlaybackLifecycleSignals
                            .serviceOwnedNaturalAdvanceEpisodeId == episodeId
                    ) {
                        cx.aswin.boxcast.core.data.PlaybackLifecycleSignals
                            .serviceOwnedNaturalAdvanceEpisodeId = null
                    }
                }
            } else {
                player.stop()
                resetLifecycleGuards(null, 0L)
            }
        }
    }

    private fun handleNaturalStateEnded(player: ExoPlayer) {
        val episodeId = activeLifecycleEpisodeId ?: return
        claimNaturalCompletion(episodeId, player.duration)
        if (cx.aswin.boxcast.core.data.SleepTimerHolder.sleepAtEndOfEpisode) {
            clearEndOfEpisodeSleep()
            player.pause()
        } else if (!player.hasNextMediaItem()) {
            player.stop()
            resetLifecycleGuards(null, 0L)
        }
    }

    private fun enforceEndOfEpisodeSleepAfterTransition(
        player: ExoPlayer,
        completedDurationMs: Long,
    ) {
        clearEndOfEpisodeSleep()
        player.pause()
        val previousIndex = player.currentMediaItemIndex - 1
        if (previousIndex >= 0) {
            sleepRestoreInProgress = true
            automaticSeekSource = "transition"
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("transition")
            player.seekTo(
                previousIndex,
                (completedDurationMs - TRUE_END_SEEK_MARGIN_MS).coerceAtLeast(0L),
            )
        }
    }

    private fun clearEndOfEpisodeSleep() {
        cx.aswin.boxcast.core.data.SleepTimerHolder.activeSleepTimerEndMs = null
        cx.aswin.boxcast.core.data.SleepTimerHolder.sleepAtEndOfEpisode = false
    }

    private fun claimNaturalCompletion(episodeId: String, durationMs: Long) {
        if (claimedCompletionGeneration == playbackActivationGeneration) return
        claimedCompletionGeneration = playbackActivationGeneration
        effectiveEndLatch = true
        stopOutroMonitor()
        val fallbackPodcastId = playbackSessionPodcastId
        val fallbackPodcastName = playbackSessionPodcastName
        val fallbackEpisodeTitle = playbackSessionEpisodeTitle
        val fallbackMediaItem = exoPlayer?.currentMediaItem
            ?.takeIf { lifecycleEpisodeId(it) == episodeId }
        val resolvedDurationMs = durationMs.takeIf { it > 0L }
            ?: playbackSessionTotalDurationMs
        completionPersistenceJob = serviceScope.launch {
            persistNaturalCompletionOnce(
                episodeId = episodeId,
                durationMs = resolvedDurationMs,
                fallbackPodcastId = fallbackPodcastId,
                fallbackPodcastName = fallbackPodcastName,
                fallbackEpisodeTitle = fallbackEpisodeTitle,
                fallbackMediaItem = fallbackMediaItem,
            )
        }
        endPlaybackSession(forceCompleted = true, isTransition = false)
    }

    private suspend fun persistNaturalCompletionOnce(
        episodeId: String,
        durationMs: Long,
        fallbackPodcastId: String?,
        fallbackPodcastName: String?,
        fallbackEpisodeTitle: String?,
        fallbackMediaItem: MediaItem?,
    ) {
        val existing = database.listeningHistoryDao().getHistoryItem(episodeId)
        if (existing?.isCompleted == true && existing.progressMs == 0L) return
        val resolvedDurationMs = durationMs.takeIf { it > 0L }
            ?: existing?.durationMs
            ?: 0L
        val completed = if (existing != null) {
            existing.copy(
                progressMs = 0L,
                durationMs = resolvedDurationMs,
                isCompleted = true,
                isManualCompletion = false,
                isDirty = true,
                lastPlayedAt = System.currentTimeMillis(),
            )
        } else {
            val queueItem = runCatching {
                queueRepository.getQueueItemByEpisodeId(episodeId)
            }.getOrNull()
            val podcastId = queueItem?.podcastId
                ?: fallbackPodcastId
                ?: ""
            val podcast = podcastId.takeIf { it.isNotBlank() }?.let {
                runCatching { database.podcastDao().getPodcast(it) }.getOrNull()
            }
            cx.aswin.boxcast.core.data.database.ListeningHistoryEntity(
                episodeId = episodeId,
                podcastId = podcastId,
                episodeTitle = queueItem?.title
                    ?: fallbackEpisodeTitle
                    ?: fallbackMediaItem?.mediaMetadata?.title?.toString()
                    ?: "Unknown Episode",
                episodeImageUrl = queueItem?.imageUrl
                    ?: fallbackMediaItem?.mediaMetadata?.artworkUri?.toString(),
                podcastImageUrl = queueItem?.podcastImageUrl ?: podcast?.imageUrl,
                episodeAudioUrl = queueItem?.audioUrl
                    ?: fallbackMediaItem?.localConfiguration?.uri?.toString(),
                podcastName = queueItem?.podcastTitle
                    ?: podcast?.title
                    ?: fallbackPodcastName
                    ?: fallbackMediaItem?.mediaMetadata?.artist?.toString()
                    ?: "",
                progressMs = 0L,
                durationMs = resolvedDurationMs,
                isCompleted = true,
                lastPlayedAt = System.currentTimeMillis(),
                enclosureType = queueItem?.enclosureType,
                isManualCompletion = false,
                episodeDescription = queueItem?.description,
            )
        }
        database.listeningHistoryDao().upsert(completed)
        mediaSession?.notifyChildrenChanged(AutoBrowseContract.HOME_CONTINUE_ID, 20, null)
        mediaSession?.notifyChildrenChanged(AutoBrowseContract.LIBRARY_HISTORY_ID, 50, null)
    }

    private fun observeManualCompletion(episodeId: String) {
        if (episodeId != activeLifecycleEpisodeId) return
        claimedCompletionGeneration = playbackActivationGeneration
        completionTelemetryGeneration = playbackActivationGeneration
        effectiveEndLatch = true
        introCancelledByUser = true
        pendingIntroTargetMs = null
        pendingIntroSeekSource = null
        stopOutroMonitor()
        effectiveEndWatchdogJob?.cancel()
        effectiveEndWatchdogJob = null
    }

    private fun prewarmAutoCollages() {
        serviceScope.launch {
            try {
                val history = database.listeningHistoryDao().getRecentHistoryList(300)
                val resumeItems = database.listeningHistoryDao().getResumeItemsList()
                val subscriptions = database.podcastDao().getSubscribedPodcastsList()
                val downloads = database.downloadedEpisodeDao().getCompletedDownloads(8)
                val queue = queueRepository.getQueueSnapshot()
                val historyImages = history.mapNotNull {
                    it.episodeImageUrl ?: it.podcastImageUrl
                }
                val resumeImages = resumeItems.mapNotNull {
                    it.episodeImageUrl ?: it.podcastImageUrl
                }
                val subscriptionImages = subscriptions.mapNotNull { it.imageUrl }
                val downloadImages = downloads.mapNotNull {
                    it.episodeImageUrl ?: it.podcastImageUrl
                }
                val queueImages = queue.mapNotNull { it.imageUrl ?: it.podcastImageUrl }
                val newEpisodeImages = subscriptions.mapNotNull {
                    it.latestEpisode?.imageUrl ?: it.imageUrl
                }
                var mixtape = cx.aswin.boxcast.core.data.MixtapeEngine.build(
                    subscriptions = subscriptions.map { it.toAutoPodcast() },
                    history = history,
                    adaptiveRanking = cx.aswin.boxcast.core.data.MixtapeEngine.AdaptiveRanking(
                        adaptiveCandidateScorer,
                    ),
                )
                if (mixtape.episodes.size < 3) {
                    val recommendations = runCatching {
                        kotlinx.coroutines.withTimeout(6_000L) {
                            smartQueueSources.getPersonalizedRecommendations(
                                history = smartQueueSources.getHistoryForRecommendations(25),
                                interests = smartQueueSources.getInterests(),
                                country = smartQueueSources.getRegion(),
                                subscribedPodcastIds = subscriptions.map { it.podcastId },
                                subscribedGenres = subscriptions.mapNotNull { it.genre }.distinct(),
                            )
                        }
                    }.getOrDefault(emptyList())
                    mixtape = cx.aswin.boxcast.core.data.MixtapeEngine.build(
                        subscriptions = subscriptions.map { it.toAutoPodcast() },
                        history = history,
                        recommendations = recommendations,
                        adaptiveRanking = cx.aswin.boxcast.core.data.MixtapeEngine.AdaptiveRanking(
                            adaptiveCandidateScorer,
                        ),
                    )
                }
                val mixtapeImages = mixtape.podcasts.mapNotNull { podcast ->
                    podcast.latestEpisode?.let { episode ->
                        episode.imageUrl ?: episode.podcastImageUrl ?: podcast.imageUrl
                    }
                }
                autoCollageUris = AutoCollageGenerator.generateAllCollages(
                    context = this@BoxLorePlaybackService,
                    folderImages = mapOf(
                        AutoBrowseContract.HOME_ID to (historyImages + newEpisodeImages).take(4),
                        AutoBrowseContract.LIBRARY_ID to subscriptionImages.take(4),
                        AutoBrowseContract.DOWNLOADS_ID to downloadImages.take(4),
                        AutoBrowseContract.DISCOVER_ID to subscriptionImages.asReversed().take(4),
                        AutoBrowseContract.HOME_CONTINUE_ID to resumeImages.take(4),
                        AutoBrowseContract.HOME_QUEUE_ID to queueImages.take(4),
                        AutoBrowseContract.HOME_NEW_EPISODES_ID to newEpisodeImages.take(4),
                        AutoBrowseContract.HOME_DRIVE_MIX_ID to mixtapeImages.take(4),
                        AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID to
                            (queueImages + subscriptionImages).take(4),
                        AutoBrowseContract.DISCOVER_TIME_PICKS_ID to emptyList(),
                        AutoBrowseContract.DISCOVER_GENRES_ID to emptyList(),
                    ),
                    folderContentKeys = mapOf(
                        AutoBrowseContract.HOME_CONTINUE_ID to
                            resumeItems.map { it.episodeId },
                        AutoBrowseContract.HOME_DRIVE_MIX_ID to
                            mixtape.episodes.map { it.id },
                    ),
                )
                mediaSession?.notifyChildrenChanged(AutoBrowseContract.ROOT_ID, 4, null)
                mediaSession?.notifyChildrenChanged(AutoBrowseContract.HOME_ID, 3, null)
                mediaSession?.notifyChildrenChanged(
                    AutoBrowseContract.LIBRARY_ID,
                    subscriptions.size + 2,
                    null,
                )
                mediaSession?.notifyChildrenChanged(AutoBrowseContract.DISCOVER_ID, 3, null)
            } catch (error: Exception) {
                android.util.Log.w("AutoBrowse", "Unable to prewarm Android Auto artwork", error)
            }
        }
    }

    private fun cx.aswin.boxcast.core.data.database.PodcastEntity.toAutoPodcast() =
        cx.aswin.boxcast.core.model.Podcast(
            id = podcastId,
            title = title,
            artist = author,
            imageUrl = imageUrl,
            type = type,
            description = description,
            genre = genre ?: "Podcast",
            fallbackImageUrl = imageUrl,
            latestEpisode = latestEpisode,
            subscribedAt = subscribedAt,
            preferredSort = preferredSort,
            notificationsEnabled = notificationsEnabled,
            autoDownloadEnabled = autoDownloadEnabled,
            sourceType = sourceType,
            feedUrl = feedUrl,
            rssRefreshCapability = rssRefreshCapability,
            rssCatalogStale = rssCatalogStale,
            rssHasNewEpisodes = rssHasNewEpisodes,
        )
    
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
                "tv_film_buff" to GENRE_TV_FILM,
                "sports_fan" to "Sports Highlights"
            )
            else -> listOf(
                "true_crime_sleep" to "True Crime & Chill",
                "history_buff" to "History",
                "mystery_thriller" to "Mystery & Thrillers"
            )
        }
    }

    private fun startPlaybackSession(episodeId: String, currentItem: MediaItem?, fallbackEntryPoint: String? = null) {
        if (playbackSessionStartTimeMs > 0 && playbackSessionEpisodeId == episodeId) return
        
        endPlaybackSession(forceCompleted = false) // Flush any outgoing session
        
        if (playbackSessionEpisodeId != episodeId) {
            firedHeartbeats.clear()
        }
        
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

        // No explicit source was carried into this session. Attribute it so playback_started
        // isn't logged as "not set":
        //   1. A resume of the just-paused episode with no in-app source is a remote resume
        //      (notification / lock screen / Bluetooth / headset).
        //   2. Otherwise use the fallback provided by the caller (auto-advance / queue skip).
        if (playbackSessionEntryPoint == null) {
            playbackSessionEntryPoint = if (episodeId == lastPausedEpisodeId) {
                "resume_notification"
            } else {
                fallbackEntryPoint
            }
        }
        lastPausedEpisodeId = null
        
        serviceScope.launch {
            enrichPlaybackSession(episodeId, currentItem, genre)
        }
    }

    private suspend fun resolvePodcastFromDb(podcastId: String): Pair<String?, String?> {
        return try {
            val podcast = database.podcastDao().getPodcast(podcastId)
            if (podcast != null) {
                val genre = if (!podcast.genre.isNullOrBlank() && podcast.genre != "Podcast") {
                    podcast.genre
                } else {
                    null
                }
                Pair(podcast.title, genre)
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private suspend fun resolvePodcastFromHistory(episodeId: String): String? {
        return try {
            val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (historyItem != null && !historyItem.podcastName.isNullOrBlank()) {
                historyItem.podcastName
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolvePodcastFromNetwork(podcastId: String): String? {
        return try {
            val podcast = podcastRepository.getPodcastDetails(podcastId)
            podcast?.title
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolvePodcastMetadata(
        podcastId: String,
        episodeId: String,
        currentItem: MediaItem?,
        genre: String?
    ): Pair<String?, String?> {
        val (dbName, dbGenre) = resolvePodcastFromDb(podcastId)
        var resolvedPodcastName = dbName
        var actualGenre = dbGenre ?: genre

        if (resolvedPodcastName.isNullOrBlank()) {
            resolvedPodcastName = resolvePodcastFromHistory(episodeId)
        }

        if (resolvedPodcastName.isNullOrBlank()) {
            resolvedPodcastName = resolvePodcastFromNetwork(podcastId)
        }

        val finalPodcastName = resolvedPodcastName
            ?: currentItem?.mediaMetadata?.subtitle?.toString()
            ?: currentItem?.mediaMetadata?.artist?.toString()

        return Pair(finalPodcastName, actualGenre)
    }

    private suspend fun enrichPlaybackSession(episodeId: String, currentItem: MediaItem?, genre: String?) {
        try {
            val queueItem = database.queueDao().getQueueItemByEpisodeId(episodeId)
            if (queueItem != null) {
                playbackSessionContextType = queueItem.contextType
                playbackSessionContextSourceId = queueItem.contextSourceId
            } else {
                playbackSessionContextType = null
                playbackSessionContextSourceId = null
            }
        } catch (e: Exception) {
            playbackSessionContextType = null
            playbackSessionContextSourceId = null
        }

        val podcastId = findPodcastIdForEpisode(episodeId)
        playbackSessionPodcastId = podcastId

        val (resolvedName, resolvedGenre) = if (podcastId != null) {
            resolvePodcastMetadata(podcastId, episodeId, currentItem, genre)
        } else {
            val finalName = currentItem?.mediaMetadata?.subtitle?.toString()
                ?: currentItem?.mediaMetadata?.artist?.toString()
            Pair(finalName, genre)
        }
        playbackSessionPodcastName = resolvedName
        playbackSessionPodcastGenre = resolvedGenre

        // Check if repeating
        val history = database.listeningHistoryDao().getHistoryItem(episodeId)
        playbackSessionIsRepeating = history?.isCompleted == true

        var durationMs = currentItem?.mediaMetadata?.extras?.getLong("durationMs", 0L) ?: 0L
        val exoDuration = kotlinx.coroutines.withContext(mainDispatcher) {
            mediaSession?.player?.duration ?: 0L
        }
        if (exoDuration > 0) durationMs = exoDuration
        playbackSessionTotalDurationMs = durationMs

        val startPositionMs = kotlinx.coroutines.withContext(mainDispatcher) {
            mediaSession?.player?.currentPosition ?: 0L
        }
        kotlinx.coroutines.withContext(mainDispatcher) {
            updateHeartbeatsForPosition(startPositionMs, durationMs)
        }

        val isSubscribed = podcastId?.let { subscriptionRepository.isSubscribed(it) } ?: false

        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackStarted(
            podcastId = podcastId,
            podcastName = resolvedName,
            podcastGenre = resolvedGenre,
            episodeId = episodeId,
            episodeTitle = currentItem?.mediaMetadata?.title?.toString(),
            startPositionSeconds = startPositionMs / 1000f,
            totalDurationSeconds = durationMs / 1000f,
            isRepeating = playbackSessionIsRepeating,
            isSubscribed = isSubscribed,
            entryPoint = playbackSessionEntryPoint,
            entryPointContext = playbackSessionEntryPointContext
        )
    }

    private fun endPlaybackSession(forceCompleted: Boolean = false, isTransition: Boolean = false) {
        val currentEpisodeId = playbackSessionEpisodeId
        if (playbackSessionStartTimeMs > 0 && currentEpisodeId != null) {
            val durationPlayedMs = System.currentTimeMillis() - playbackSessionStartTimeMs
            val durationPlayedSeconds = durationPlayedMs / 1000f
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
                    isCompleted = PlaybackSkipPolicy.shouldCompleteFromProgress(
                        positionMs = pos,
                        durationMs = totalDurationMs,
                        effectiveSkipEndingMs =
                            effectiveEndingTrimForCompletion(totalDurationMs),
                    )
                } catch (e: Exception) {
                    android.util.Log.w(
                        "BoxLorePlaybackService",
                        "Failed to evaluate playback completion; using fallback",
                        e,
                    )
                }
            }
            
            // Capture queue size for analytics
            val currentQueueSize = try { mediaSession?.player?.mediaItemCount ?: 0 } catch (_: Exception) { 0 }
            
            if (
                isCompleted &&
                completionTelemetryGeneration != playbackActivationGeneration
            ) {
                completionTelemetryGeneration = playbackActivationGeneration
                // Instantly dispatch 100% heartbeat if not already fired
                if (!firedHeartbeats.contains("percent_100")) {
                    firedHeartbeats.add("percent_100")
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackHeartbeat(
                        podcastId = currentPodcastId,
                        podcastName = currentPodcastName,
                        episodeId = currentEpisodeId,
                        episodeTitle = currentEpisodeTitle,
                        currentPositionSeconds = totalDurationMs / 1000f,
                        totalDurationSeconds = totalDurationMs / 1000f,
                        heartbeatPercentage = 100,
                        heartbeatType = "percent"
                    )
                }

                // Dedicated playback_completed event
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackCompleted(
                    podcastId = currentPodcastId,
                    podcastName = currentPodcastName,
                    podcastGenre = currentPodcastGenre,
                    episodeId = currentEpisodeId,
                    episodeTitle = currentEpisodeTitle,
                    totalDurationSeconds = totalDurationMs / 1000f,
                    entryPoint = entryPoint,
                    entryPointContext = entryPointContext
                )

                // Auto-delete completed download if enabled in preferences
                val completedEpId = currentEpisodeId
                if (completedEpId.isNotEmpty()) {
                    serviceScope.launch {
                        try {
                            val shouldDelete = userPreferencesRepository.autoDownloadDeleteCompletedStream.first()
                            if (shouldDelete) {
                                val downloadRepo = cx.aswin.boxcast.core.data.DownloadRepository(this@BoxLorePlaybackService, database)
                                downloadRepo.removeDownload(completedEpId)
                                android.util.Log.d("BoxLorePlaybackService", "Auto-deleted completed downloaded episode: $completedEpId")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BoxLorePlaybackService", "Failed to auto-delete completed download", e)
                        }
                    }
                }
            } else if (!isCompleted) {
                val pauseReason = cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.consumePauseReason()
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackPaused(
                    podcastId = currentPodcastId,
                    podcastName = currentPodcastName,
                    podcastGenre = currentPodcastGenre,
                    episodeId = currentEpisodeId,
                    episodeTitle = currentEpisodeTitle,
                    durationPlayedSeconds = durationPlayedSeconds,
                    totalBufferedTimeSeconds = playbackSessionTotalBufferedTimeMs / 1000f,
                    totalDurationSeconds = totalDurationMs / 1000f,
                    isCompleted = false,
                    entryPoint = entryPoint,
                    entryPointContext = entryPointContext,
                    queueSize = currentQueueSize,
                    pauseReason = pauseReason
                )

                // Track skip if it's a transition skip within 30 seconds for an AUTO_FILL episode.
                // Also feed local skip memory so the SmartQueueEngine never re-suggests it
                // and can down-rank the podcast after repeated rejections.
                if (isTransition && durationPlayedSeconds <= 30f && playbackSessionContextType == "AUTO_FILL") {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSmartQueueEpisodeSkipped(
                        episodeId = currentEpisodeId,
                        recommendationSource = playbackSessionContextSourceId ?: "unknown",
                        positionInQueue = 0
                    )
                    try {
                        queueSkipMemory.recordSkip(
                            episodeId = currentEpisodeId,
                            podcastId = currentPodcastId,
                            source = playbackSessionContextSourceId
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("AutoQueue", "Failed to record skip memory", e)
                    }
                }
            }

            val adaptiveSource = when (playbackSessionContextType) {
                "AUTO_FILL" -> cx.aswin.boxcast.core.data.ranking.CandidateSource.SERVER_RECOMMENDATION
                cx.aswin.boxcast.core.data.QueueMath.CONTEXT_TYPE_LORE ->
                    cx.aswin.boxcast.core.data.ranking.CandidateSource.CURATED_INTENT
                else -> null
            }
            val isAdaptiveEarlySkip = isTransition &&
                durationPlayedSeconds <= 30f &&
                adaptiveSource != null
            serviceScope.launch {
                rankingFeedbackRepository.recordPlayback(
                    target = cx.aswin.boxcast.core.data.ranking.FeedbackTarget(
                        episodeId = currentEpisodeId,
                        podcastId = currentPodcastId.orEmpty(),
                        genre = currentPodcastGenre,
                        source = adaptiveSource,
                    ),
                    listenSeconds = durationPlayedSeconds.toLong().coerceAtLeast(0L),
                    durationSeconds = (totalDurationMs / 1_000L).coerceAtLeast(0L),
                    completed = isCompleted,
                    earlySkip = isAdaptiveEarlySkip,
                )
            }
            
            // Flush events immediately to prevent losses during backgrounding/shutdown
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.flush()
            
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
        resetLifecycleGuards(null, 0L)
        clearEndOfEpisodeSleep()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
                android.util.Log.d("BoxLorePlaybackService", "onTaskRemoved: player not playing or queue empty, stopping service gracefully")
                endPlaybackSession(forceCompleted = false)
                resetLifecycleGuards(null, 0L)
                stopSelf()
                super.onTaskRemoved(rootIntent)
            } else {
                android.util.Log.d("BoxLorePlaybackService", "onTaskRemoved: player is playing, keeping service in foreground and bypassing super.onTaskRemoved to prevent notification from disappearing")
            }
        } else {
            stopSelf()
            super.onTaskRemoved(rootIntent)
        }
    }

    /**
     * SmartQueue refill: the single auto-refill path in the app (the UI-side triggers
     * were removed). Uses the tiered SmartQueueEngine to build a batch of episodes
     * (same podcast → resume → scored subscriptions → server recs → region trending).
     * Works independently of the app UI being open.
     */
    private suspend fun refillQueue(player: ExoPlayer) {
        val currentItem = player.currentMediaItem ?: return
             // Extract episode info — strip any prefix for consistent ID format
        val episodeId = currentItem.mediaId.removePrefix(LEARN_PREFIX).removePrefix(EPISODE_PREFIX).removePrefix(QUEUE_PREFIX)
        val metadata = currentItem.mediaMetadata
        
        android.util.Log.d("AutoQueue", "Refilling from: ${metadata.title}, episodeId=$episodeId")
        
        // Enforce queue cap
        if (player.mediaItemCount >= QUEUE_MAX_SIZE) {
            android.util.Log.d("AutoQueue", "Queue at max capacity ($QUEUE_MAX_SIZE). Skipping refill.")
            return
        }
        
        // Get podcast context (check DB first, then fallback to API).
        // Briefings have no feed entry; synthesize a minimal podcast so the engine can
        // run its fallback tiers (it detects the briefing_ prefix itself).
        val isBriefing = episodeId.startsWith("briefing_")
        val podcastId = if (isBriefing) episodeId else findPodcastIdForEpisode(episodeId) ?: return
        
        val podcastEntity = if (isBriefing) null else database.podcastDao().getPodcast(podcastId)
        val podcast = when {
            podcastEntity != null -> cx.aswin.boxcast.core.model.Podcast(
                id = podcastEntity.podcastId,
                title = podcastEntity.title,
                artist = podcastEntity.author,
                imageUrl = podcastEntity.imageUrl,
                description = podcastEntity.description,
                genre = podcastEntity.genre ?: "Podcast",
                type = podcastEntity.type,
                preferredSort = podcastEntity.preferredSort
            )
            isBriefing -> cx.aswin.boxcast.core.model.Podcast(
                id = "briefing_daily",
                title = metadata.subtitle?.toString() ?: "Daily Briefing",
                artist = "",
                imageUrl = metadata.artworkUri?.toString() ?: "",
                genre = "News"
            )
            // Fallback to API if not in local DB (e.g. unsubscribed podcast from history)
            else -> podcastRepository.getPodcastDetails(podcastId) ?: return
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
        
        // Everything already in the player is off-limits for the engine.
        val existingIds = kotlinx.coroutines.withContext(mainDispatcher) {
            (0 until player.mediaItemCount).map {
                player.getMediaItemAt(it).mediaId.removePrefix(LEARN_PREFIX).removePrefix(EPISODE_PREFIX).removePrefix(QUEUE_PREFIX)
            }.toSet()
        }

        android.util.Log.d(
            "AutoQueue",
            "Refill context: podcastId=${podcast.id}, type=${podcast.type}, " +
                "preferredSort=${podcastEntity?.preferredSort ?: podcast.preferredSort}, genre=${podcast.genre}"
        )
        val currentContextSourceId = database.queueDao().getQueueItemByEpisodeId(episodeId)?.contextSourceId
        val nextEntries = kotlinx.coroutines.withContext(ioDispatcher) {
            smartQueueEngine.getNextEpisodes(
                currentEpisode = currentEpisodeItem,
                podcast = podcast,
                preferredSort = podcastEntity?.preferredSort,
                excludeEpisodeIds = existingIds,
                currentContextSourceId = currentContextSourceId,
            )
        }
        android.util.Log.d(
            "AutoQueue",
            "SmartQueue returned ${nextEntries.size} episodes: ${nextEntries.groupingBy { it.source }.eachCount()}"
        )
        if (nextEntries.isEmpty()) {
            android.util.Log.w(
                "AutoQueue",
                "SmartQueue returned no candidates for podcastId=${podcast.id}, episodeId=$episodeId",
            )
            return
        }

        // Respect the queue cap when appending the batch.
        val room = (QUEUE_MAX_SIZE - player.mediaItemCount).coerceAtLeast(0)
        val entriesToAdd = nextEntries
            .filter { it.episode.id.toString() !in existingIds }
            .take(room)
        if (entriesToAdd.isEmpty()) return

        // Persist FIRST so PlaybackRepository's timeline reconciliation finds the rows
        // (with contextType/source for queue-sheet labels) when the player callback fires.
        entriesToAdd.forEach { entry ->
            try {
                queueRepository.addToQueue(
                    episode = entry.episode,
                    podcast = entry.podcast,
                    contextType = "AUTO_FILL",
                    contextSourceId = entry.source
                )
            } catch (e: Exception) {
                android.util.Log.e("AutoQueue", "Failed to persist queue item: ${entry.episode.title}", e)
            }
        }

        val refilledEpisodeIds = mutableListOf<String>()
        val recommendationSources = mutableListOf<String>()

        // Add to player queue on main thread
        kotlinx.coroutines.withContext(mainDispatcher) {
            entriesToAdd.forEach { entry ->
                val ep = entry.episode
                val pod = entry.podcast
                val epIdStr = ep.id.toString()
                
                val finalImageUrl = ep.image ?: ep.feedImage ?: pod.imageUrl
                val artworkUri = finalImageUrl.let { android.net.Uri.parse(it) }
                
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
                refilledEpisodeIds.add(epIdStr)
                recommendationSources.add(entry.source)
            }
            android.util.Log.d("AutoQueue", "Added ${refilledEpisodeIds.size} items. Queue now: ${player.mediaItemCount}")
        }

        if (refilledEpisodeIds.isNotEmpty()) {
            val region = try {
                userPreferencesRepository.regionStream.first()
            } catch (e: Exception) { null }
            val sourceCounts = recommendationSources.groupingBy { it }.eachCount()
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSmartQueueRefilled(
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.SmartQueueRefillEvent(
                    triggeringEpisodeId = episodeId,
                    triggeringPodcastGenre = podcast.genre ?: "Podcast",
                    refilledCount = refilledEpisodeIds.size,
                    recommendationSources = recommendationSources.distinct(),
                    refilledEpisodeIds = refilledEpisodeIds,
                    region = region,
                    sourceCounts = sourceCounts,
                    usedServerRecommendations = cx.aswin.boxcast.core.data.SmartQueueEngine.SOURCE_PERSONALIZED_REC in sourceCounts ||
                        cx.aswin.boxcast.core.data.SmartQueueEngine.SOURCE_SERVER_REC in sourceCounts
                )
            )
        }
    }

    /**
     * Periodically saves playback position and dispatches heartbeat telemetry (runs on Dispatchers.Main).
     */
    /**
     * Periodically saves playback position and dispatches heartbeat telemetry (runs on Dispatchers.Main).
     * Also checks and enforces sleep timer expiration continuously while the foreground service is active.
     */
    private suspend fun startPlaybackTicker(player: ExoPlayer) {
        var tickCount = 0
        while (true) {
            kotlinx.coroutines.delay(1_000)
            
            // Continuous Service-Level Sleep Timer Enforcement (fires even when locked in Doze mode)
            val sleepEnd = cx.aswin.boxcast.core.data.SleepTimerHolder.activeSleepTimerEndMs
            if (sleepEnd != null && System.currentTimeMillis() >= sleepEnd) {
                cx.aswin.boxcast.core.data.SleepTimerHolder.activeSleepTimerEndMs = null
                android.util.Log.d("BoxCastPlayer", "Foreground Service Sleep Timer: Expired! Pausing player.")
                kotlinx.coroutines.withContext(mainDispatcher) {
                    if (player.isPlaying) player.pause()
                }
            }

            tickCount++
            if (tickCount % 10 == 0) {
                saveProgressOnce(player)
                dispatchHeartbeatTelemetry(player)
            }
        }
    }

    private fun dispatchHeartbeatTelemetry(player: ExoPlayer) {
        val episodeId = playbackSessionEpisodeId ?: return
        if (!player.isPlaying) return
        
        val currentPosMs = player.currentPosition
        val durationMs = player.duration
        if (durationMs <= 0) return
        
        val currentPosSec = currentPosMs / 1000f
        val durationSec = durationMs / 1000f
        val percent = (currentPosMs.toFloat() / durationMs.toFloat()) * 100f
        
        checkPercentHeartbeats(episodeId, currentPosSec, durationSec, percent)
        checkIntervalHeartbeats(episodeId, currentPosSec, durationSec)
    }

    private fun checkPercentHeartbeats(episodeId: String, currentPosSec: Float, durationSec: Float, percent: Float) {
        val percentMilestones = listOf(10, 25, 50, 75, 90)
        for (milestone in percentMilestones) {
            if (percent >= milestone && !firedHeartbeats.contains("percent_$milestone")) {
                firedHeartbeats.add("percent_$milestone")
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = playbackSessionPodcastId,
                    podcastName = playbackSessionPodcastName,
                    episodeId = episodeId,
                    episodeTitle = playbackSessionEpisodeTitle,
                    currentPositionSeconds = currentPosSec,
                    totalDurationSeconds = durationSec,
                    heartbeatPercentage = milestone,
                    heartbeatType = "percent"
                )
            }
        }
    }

    private fun checkIntervalHeartbeats(episodeId: String, currentPosSec: Float, durationSec: Float) {
        val fiveMinuteIntervals = (currentPosSec / 300f).toInt()
        if (fiveMinuteIntervals > 0) {
            val milestoneKey = "time_${fiveMinuteIntervals * 5}m"
            if (!firedHeartbeats.contains(milestoneKey)) {
                firedHeartbeats.add(milestoneKey)
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = playbackSessionPodcastId,
                    podcastName = playbackSessionPodcastName,
                    episodeId = episodeId,
                    episodeTitle = playbackSessionEpisodeTitle,
                    currentPositionSeconds = currentPosSec,
                    totalDurationSeconds = durationSec,
                    heartbeatPercentage = 0,
                    heartbeatType = "interval"
                )
            }
        }
    }

    private fun updateHeartbeatsForPosition(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val percent = (positionMs.toFloat() / durationMs.toFloat()) * 100f
        
        // Percent milestones
        val percentMilestones = listOf(10, 25, 50, 75, 90)
        for (milestone in percentMilestones) {
            if (percent >= milestone) {
                firedHeartbeats.add("percent_$milestone")
            }
        }
        
        // Time-based intervals
        val positionSec = positionMs / 1000f
        val fiveMinuteIntervals = (positionSec / 300f).toInt()
        for (i in 1..fiveMinuteIntervals) {
            firedHeartbeats.add("time_${i * 5}m")
        }
    }
    
    /**
     * Saves the current playback position to DB once.
     */
    private suspend fun saveProgressOnce(player: ExoPlayer) {
        if (effectiveEndLatch) return
        try {
            val currentItem = kotlinx.coroutines.withContext(mainDispatcher) { player.currentMediaItem }
            val positionMs = kotlinx.coroutines.withContext(mainDispatcher) { player.currentPosition }
            val durationMs = kotlinx.coroutines.withContext(mainDispatcher) { player.duration }
            val episodeId = currentItem?.mediaId?.removePrefix(LEARN_PREFIX)?.removePrefix(EPISODE_PREFIX)?.removePrefix(QUEUE_PREFIX) ?: return
            
            val existing = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (existing != null && positionMs > 0) {
                val hasBeenPlayingFor10s = activePlaybackStartTimeMs > 0 && 
                        (System.currentTimeMillis() - activePlaybackStartTimeMs >= 10_000)
                val lastPlayed = if (hasBeenPlayingFor10s) System.currentTimeMillis() else existing.lastPlayedAt
                
                val isCompleted = checkIsPlaybackCompleted(positionMs, durationMs)

                if (isCompleted) {
                    val updated = existing.copy(
                        isCompleted = true,
                        progressMs = 0L,
                        durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                        lastPlayedAt = lastPlayed,
                        isDirty = true
                    )
                    database.listeningHistoryDao().upsert(updated)
                    android.util.Log.d("AutoProgress", "Saved completed: $episodeId")
                } else {
                    database.listeningHistoryDao().updateProgress(
                        episodeId = episodeId,
                        progressMs = positionMs,
                        durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                        lastPlayedAt = lastPlayed
                    )
                    android.util.Log.d("AutoProgress", "Saved progress: $episodeId @ ${positionMs/1000}s / ${durationMs/1000}s")
                }
                
                kotlinx.coroutines.withContext(mainDispatcher) {
                    try {
                        mediaSession?.notifyChildrenChanged("home_continue_listening", 0, null)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AutoProgress", "Error saving progress once", e)
        }
    }

    private fun checkIsPlaybackCompleted(positionMs: Long, durationMs: Long): Boolean {
        return PlaybackSkipPolicy.shouldCompleteFromProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            effectiveSkipEndingMs = effectiveEndingTrimForCompletion(durationMs),
        )
    }

    /**
     * Find which podcast an episode belongs to (service-level helper).
     */
    private suspend fun findPodcastIdForEpisode(episodeId: String): String? {
        val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
        historyItem?.podcastId?.takeIf { it.isNotBlank() }?.let { return it }

        val queueItem = database.queueDao().getQueueItemByEpisodeId(episodeId)
        queueItem?.podcastId?.takeIf { it.isNotBlank() }?.let { return it }

        val episode = podcastRepository.getEpisode(episodeId)
        return episode?.podcastId
    }

    /**
     * Marks the current playing episode as completed in the database.
     */
    private fun markCurrentEpisodeCompleted() {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem
        val durationMs = player.duration
        val episodeId = currentItem?.mediaId?.removePrefix(LEARN_PREFIX)?.removePrefix(EPISODE_PREFIX)?.removePrefix(QUEUE_PREFIX)
        if (episodeId != null) {
            observeManualCompletion(episodeId)
            serviceScope.launch {
                try {
                    val existing = database.listeningHistoryDao().getHistoryItem(episodeId)
                    if (existing != null) {
                        val updated = existing.copy(
                            isCompleted = true,
                            progressMs = 0L,
                            durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                            lastPlayedAt = System.currentTimeMillis(),
                            isDirty = true
                        )
                        database.listeningHistoryDao().upsert(updated)
                        android.util.Log.d("BoxLorePlaybackService", "Marked current episode completed: $episodeId")
                        
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlaybackCompleted(
                            podcastId = playbackSessionPodcastId,
                            podcastName = playbackSessionPodcastName,
                            podcastGenre = playbackSessionPodcastGenre,
                            episodeId = episodeId,
                            episodeTitle = playbackSessionEpisodeTitle,
                            totalDurationSeconds = (if (durationMs > 0) durationMs else existing.durationMs) / 1000f,
                            entryPoint = playbackSessionEntryPoint,
                            entryPointContext = playbackSessionEntryPointContext
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BoxLorePlaybackService", "Failed to mark current episode completed", e)
                }
            }
        }
    }

    /**
     * Handles skipping to the next episode based on user settings.
     */
    private fun handleSkipNext() {
        val player = exoPlayer ?: return
        serviceScope.launch {
            val skipBehavior = try {
                userPreferencesRepository.skipBehaviorStream.first()
            } catch (e: Exception) {
                "just_skip"
            }
            
            if (skipBehavior == "mark_completed_skip") {
                markCurrentEpisodeCompleted()
            }
            
            kotlinx.coroutines.withContext(mainDispatcher) {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    player.stop()
                    resetLifecycleGuards(null, 0L)
                }
            }
        }
    }

    /**
     * Marks the current playing episode as completed in the database and skips to the next item (Legacy/Custom Command Callback).
     */
    private fun markCurrentEpisodeCompletedAndSkip(session: MediaSession) {
        markCurrentEpisodeCompleted()
        serviceScope.launch {
            val player = exoPlayer ?: return@launch
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                player.stop()
                resetLifecycleGuards(null, 0L)
            }
        }
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
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        private val rootChildLimits =
            java.util.concurrent.ConcurrentHashMap<MediaSession.ControllerInfo, Int>()
        private val searchCache = object : LinkedHashMap<String, List<MediaItem>>(8, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<MediaItem>>,
            ): Boolean = size > 8
        }
        @Volatile
        private var lastDrivePicks: List<MediaItem> = emptyList()
        @Volatile
        private var lastMixtape: List<MediaItem> = emptyList()
        @Volatile
        private var lastMixtapeUpdatedAt: Long = 0L

        private val ROOT_ID = AutoBrowseContract.ROOT_ID
        private val HOME_ID = AutoBrowseContract.HOME_ID
        private val LIBRARY_ID = AutoBrowseContract.LIBRARY_ID
        private val DOWNLOADS_ID = AutoBrowseContract.DOWNLOADS_ID
        private val DISCOVER_ID = AutoBrowseContract.DISCOVER_ID
        private val EXPLORE_ID = AutoBrowseContract.LEGACY_EXPLORE_ID
        private val SUBSCRIPTIONS_ID = "subscriptions"
        private val HOME_CONTINUE_LISTENING_ID = AutoBrowseContract.HOME_CONTINUE_ID
        private val HOME_SUBSCRIPTIONS_ID = "home_subscriptions"
        private val HOME_QUEUE_ID = AutoBrowseContract.HOME_QUEUE_ID
        private val HOME_NEW_EPISODES_ID = AutoBrowseContract.HOME_NEW_EPISODES_ID
        private val PLAY_ALL_NEW_EPISODES_ID = AutoBrowseContract.PLAY_ALL_NEW_ID
        private val SUBSCRIPTION_PREFIX = AutoBrowseContract.SUBSCRIPTION_PREFIX

        // Content style constants for Android Auto grid/list display
        private val CONTENT_STYLE_BROWSABLE_KEY = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private val CONTENT_STYLE_PLAYABLE_KEY = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private val CONTENT_STYLE_LIST = 1
        private val CONTENT_STYLE_GRID = 2
        
        // Progress bar constants for Android Auto
        private val DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE =
            androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE
        private val DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS =
            androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_STATUS
        private val COMPLETION_STATUS_NOT_PLAYED = 0
        private val COMPLETION_STATUS_PARTIALLY_PLAYED = 1
        private val COMPLETION_STATUS_FULLY_PLAYED = 2
        
        private val SEEK_BACK_CMD = androidx.media3.session.SessionCommand("SEEK_BACK", Bundle.EMPTY)
        private val SEEK_FORWARD_CMD = androidx.media3.session.SessionCommand("SEEK_FORWARD", Bundle.EMPTY)
        private val MARK_COMPLETED_SKIP_CMD = androidx.media3.session.SessionCommand("MARK_COMPLETED_SKIP", Bundle.EMPTY)
        private val TOGGLE_LIKE_CMD = androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_TOGGLE_LIKE,
            Bundle.EMPTY,
        )
        private val ADD_TO_QUEUE_CMD = androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_ADD_TO_QUEUE,
            Bundle.EMPTY,
        )
        private val MARK_COMPLETE_CMD = androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_MARK_COMPLETE,
            Bundle.EMPTY,
        )

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val defaultResult = super.onConnect(session, controller)
            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(SEEK_BACK_CMD)
                .add(SEEK_FORWARD_CMD)
                .add(MARK_COMPLETED_SKIP_CMD)
                .add(TOGGLE_LIKE_CMD)
                .add(ADD_TO_QUEUE_CMD)
                .add(MARK_COMPLETE_CMD)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(defaultResult.availablePlayerCommands)
                .setCustomLayout(listOf(seekBackAction, seekForwardAction, markCompleteAction))
                .build()
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            rootChildLimits.remove(controller)
            super.onDisconnected(session, controller)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle
        ): ListenableFuture<androidx.media3.session.SessionResult> {
            if (
                customCommand.customAction == AutoBrowseContract.COMMAND_TOGGLE_LIKE ||
                customCommand.customAction == AutoBrowseContract.COMMAND_ADD_TO_QUEUE ||
                customCommand.customAction == AutoBrowseContract.COMMAND_MARK_COMPLETE
            ) {
                return serviceScope.future {
                    val episodeId = args.getString(androidx.media3.session.MediaConstants.EXTRA_KEY_MEDIA_ID)
                        ?.stripEpisodePrefix()
                        ?: session.player.currentMediaItem?.mediaId?.stripEpisodePrefix()
                    val handled = if (episodeId != null) {
                        when (customCommand.customAction) {
                            AutoBrowseContract.COMMAND_TOGGLE_LIKE -> toggleEpisodeLike(episodeId)
                            AutoBrowseContract.COMMAND_ADD_TO_QUEUE ->
                                addEpisodeToQueue(episodeId, session.player)
                            AutoBrowseContract.COMMAND_MARK_COMPLETE -> markEpisodeComplete(episodeId)
                            else -> false
                        }
                    } else {
                        false
                    }
                    androidx.media3.session.SessionResult(
                        if (!handled) {
                            androidx.media3.session.SessionResult.RESULT_ERROR_BAD_VALUE
                        } else {
                            androidx.media3.session.SessionResult.RESULT_SUCCESS
                        },
                    )
                }
            }
            when (customCommand.customAction) {
                "SEEK_BACK" -> {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("seek_backward")
                    session.player.seekBack()
                    android.util.Log.d("AutoBrowse", "Seek backward")
                }
                "SEEK_FORWARD" -> {
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("seek_forward")
                    session.player.seekForward()
                    android.util.Log.d("AutoBrowse", "Seek forward")
                }
                "MARK_COMPLETED_SKIP" -> {
                    markCurrentEpisodeCompletedAndSkip(session)
                }
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }

        private suspend fun toggleEpisodeLike(episodeId: String): Boolean {
            val existing = getOrCreateHistoryItem(episodeId) ?: return false
            database.listeningHistoryDao().upsert(
                existing.copy(
                    isLiked = !existing.isLiked,
                    isDirty = true,
                ),
            )
            mediaSession?.notifyChildrenChanged(AutoBrowseContract.LIBRARY_LIKED_ID, 50, null)
            return true
        }

        private suspend fun markEpisodeComplete(episodeId: String): Boolean {
            val existing = getOrCreateHistoryItem(episodeId) ?: return false
            observeManualCompletion(episodeId)
            database.listeningHistoryDao().upsert(
                existing.copy(
                    progressMs = 0L,
                    isCompleted = true,
                    isManualCompletion = true,
                    isDirty = true,
                    lastPlayedAt = System.currentTimeMillis(),
                ),
            )
            mediaSession?.notifyChildrenChanged(AutoBrowseContract.HOME_CONTINUE_ID, 20, null)
            mediaSession?.notifyChildrenChanged(AutoBrowseContract.LIBRARY_HISTORY_ID, 50, null)
            return true
        }

        private suspend fun getOrCreateHistoryItem(
            episodeId: String,
        ): cx.aswin.boxcast.core.data.database.ListeningHistoryEntity? {
            database.listeningHistoryDao().getHistoryItem(episodeId)?.let { return it }
            val episode = resolveDomainEpisode(episodeId) ?: return null
            return cx.aswin.boxcast.core.data.database.ListeningHistoryEntity(
                episodeId = episode.id,
                podcastId = episode.podcastId.orEmpty(),
                episodeTitle = episode.title,
                episodeImageUrl = episode.imageUrl,
                podcastImageUrl = episode.podcastImageUrl,
                episodeAudioUrl = episode.audioUrl,
                podcastName = episode.podcastTitle.orEmpty(),
                progressMs = 0L,
                durationMs = episode.duration.toLong() * 1_000L,
                isCompleted = false,
                lastPlayedAt = System.currentTimeMillis(),
                enclosureType = episode.enclosureType,
                episodeDescription = episode.description,
            )
        }

        private suspend fun addEpisodeToQueue(episodeId: String, player: Player): Boolean {
            val existingQueue = queueRepository.getQueueSnapshot()
            val queuedEpisode = existingQueue.firstOrNull { it.id == episodeId }
            val episode = queuedEpisode ?: resolveDomainEpisode(episodeId) ?: return false
            val playerHasEpisode = (0 until player.mediaItemCount).any { index ->
                player.getMediaItemAt(index).mediaId.stripEpisodePrefix() == episodeId
            }
            if (!playerHasEpisode) {
                player.addMediaItem(
                    AutoMediaItemFactory.fromEpisode(
                        episode = episode,
                        source = AutoBrowseContract.SOURCE_QUEUE,
                        artworkUri = AutoArtworkRepository.remoteUri(
                            this@BoxLorePlaybackService,
                            episode.imageUrl ?: episode.podcastImageUrl,
                        ),
                        mediaIdPrefix = QUEUE_PREFIX,
                    ),
                )
            }
            if (queuedEpisode == null) queueRepository.replaceQueue(existingQueue + episode)
            mediaSession?.notifyChildrenChanged(AutoBrowseContract.HOME_QUEUE_ID, 50, null)
            return true
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = androidx.core.content.IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_KEY_EVENT,
                android.view.KeyEvent::class.java
            )
            if (keyEvent != null && keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("seek_forward")
                        session.player.seekForward()
                        android.util.Log.d("BoxLorePlaybackService", "onMediaButtonEvent: KEYCODE_MEDIA_NEXT intercepted, seeking forward")
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("seek_backward")
                        session.player.seekBack()
                        android.util.Log.d("BoxLorePlaybackService", "onMediaButtonEvent: KEYCODE_MEDIA_PREVIOUS intercepted, seeking backward")
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            android.util.Log.d("AutoBrowse", "onGetLibraryRoot called")

            rootChildLimits[browser] = params?.extras
                ?.getInt(androidx.media3.session.MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 4)
                ?.takeIf { it > 0 }
                ?.coerceAtMost(4)
                ?: 4
            val rootExtras = AutoBrowseContract.listChildrenExtras()

            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(cx.aswin.boxcast.core.data.R.string.auto_app_name))
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
                        parentId == ROOT_ID -> getRootChildren().take(rootChildLimits[browser] ?: 4)
                        parentId == HOME_ID -> getHomeChildren()
                        parentId == HOME_CONTINUE_LISTENING_ID -> getContinueListeningChildren()
                        parentId == HOME_QUEUE_ID -> getQueueChildren()
                        parentId == AutoBrowseContract.HOME_DRIVE_MIX_ID -> getMixtapeChildren()
                        parentId == HOME_NEW_EPISODES_ID -> getNewEpisodesChildren()
                        parentId == HOME_SUBSCRIPTIONS_ID || parentId == SUBSCRIPTIONS_ID -> getSubscriptionsChildren()
                        parentId == LIBRARY_ID -> getLibraryChildren()
                        parentId == AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID ->
                            getSubscriptionsChildren()
                        parentId == AutoBrowseContract.LIBRARY_LIKED_ID -> getLikedChildren()
                        parentId == AutoBrowseContract.LIBRARY_HISTORY_ID -> getHistoryChildren()
                        parentId == DOWNLOADS_ID -> getDownloadsChildren()
                        parentId == DISCOVER_ID || parentId == EXPLORE_ID -> getDiscoverChildren()
                        parentId == AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID ->
                            getDrivePicksChildren()
                        parentId == AutoBrowseContract.DISCOVER_TIME_PICKS_ID ||
                            parentId == "explore_picks" -> getExplorePicksChildren()
                        parentId == AutoBrowseContract.DISCOVER_GENRES_ID ||
                            parentId == "explore_genres" -> getGenresChildren()
                        parentId.startsWith(AutoBrowseContract.GENRE_PREFIX) -> {
                            getGenreChildren(
                                parentId.removePrefix(AutoBrowseContract.GENRE_PREFIX),
                            )
                        }
                        parentId.startsWith("home_curated_") ||
                            parentId.startsWith("explore_curated_") ||
                            parentId.startsWith(AutoBrowseContract.CURATED_PREFIX) -> {
                            val vibeId = parentId
                                .removePrefix("home_curated_")
                                .removePrefix("explore_curated_")
                                .removePrefix(AutoBrowseContract.CURATED_PREFIX)
                            getCuratedChildren(vibeId)
                        }
                        parentId.startsWith(SUBSCRIPTION_PREFIX) -> {
                            val podcastId = parentId.removePrefix(SUBSCRIPTION_PREFIX)
                            getPodcastEpisodes(podcastId)
                        }
                        else -> emptyList()
                    }
                    LibraryResult.ofItemList(
                        ImmutableList.copyOf(slicePage(items, page, pageSize)),
                        params,
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
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
            return serviceScope.future {
                val results = buildSearchResults(query)
                synchronized(searchCache) { searchCache[query] = results }
                session.notifySearchResultChanged(browser, query, results.size, params)
                LibraryResult.ofVoid()
            }
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
                    val results = synchronized(searchCache) { searchCache[query] }
                        ?: buildSearchResults(query).also {
                            synchronized(searchCache) { searchCache[query] = it }
                        }
                    android.util.Log.d("AutoBrowse", "Search results for '$query': ${results.size} items")
                    LibraryResult.ofItemList(
                        ImmutableList.copyOf(slicePage(results, page, pageSize)),
                        params,
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("AutoBrowse", "Search failed for '$query'", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }

        private suspend fun buildSearchResults(query: String): List<MediaItem> {
            val normalized = normalizeVoiceQuery(query)
            if (normalized.isBlank()) return emptyList()
            val results = mutableListOf<Pair<Int, MediaItem>>()

            database.listeningHistoryDao().getRecentHistoryList(100)
                .mapNotNull { history ->
                    val score = searchScore(history.episodeTitle, history.podcastName, normalized)
                    if (score == 0) return@mapNotNull null
                    score to AutoMediaItemFactory.fromHistory(
                        history = history,
                        source = AutoBrowseContract.SOURCE_SEARCH,
                        artworkUri = AutoArtworkRepository.remoteUri(
                            this@BoxLorePlaybackService,
                            history.episodeImageUrl ?: history.podcastImageUrl,
                        ),
                        subtitle = AutoMediaItemFactory.buildDurationSubtitle(
                            history.podcastName,
                            history.durationMs,
                        ),
                        groupTitle = getString(
                            cx.aswin.boxcast.core.data.R.string.auto_group_search,
                        ),
                    )
                }
                .sortedByDescending { it.first }
                .take(8)
                .let(results::addAll)

            database.podcastDao().getSubscribedPodcastsList()
                .mapNotNull { podcast ->
                    val score = searchScore(podcast.title, podcast.author, normalized)
                    if (score == 0) return@mapNotNull null
                    score to AutoMediaItemFactory.browsable(
                        id = "$SUBSCRIPTION_PREFIX${podcast.podcastId}",
                        title = podcast.title,
                        subtitle = podcast.author,
                        artworkUri = AutoArtworkRepository.remoteUri(
                            this@BoxLorePlaybackService,
                            podcast.imageUrl,
                        ),
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    )
                }
                .sortedByDescending { it.first }
                .take(8)
                .let(results::addAll)

            if (results.size < 12) {
                try {
                    kotlinx.coroutines.withTimeout(5_000L) {
                        podcastRepository.searchPodcasts(normalized)
                    }.take(10).forEach { podcast ->
                        results += searchScore(podcast.title, podcast.artist, normalized) to
                            AutoMediaItemFactory.browsable(
                                id = "$SUBSCRIPTION_PREFIX${podcast.id}",
                                title = podcast.title,
                                subtitle = podcast.artist,
                                artworkUri = AutoArtworkRepository.remoteUri(
                                    this@BoxLorePlaybackService,
                                    podcast.imageUrl,
                                ),
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                            )
                    }
                } catch (error: Exception) {
                    android.util.Log.w("AutoBrowse", "Remote Auto search unavailable", error)
                }
            }

            return results
                .sortedByDescending { it.first }
                .map { it.second }
                .distinctBy { it.mediaId }
                .take(30)
        }

        private fun searchScore(primary: String, secondary: String?, query: String): Int {
            val title = primary.lowercase()
            val subtitle = secondary.orEmpty().lowercase()
            return when {
                title == query -> 100
                title.startsWith(query) -> 80
                subtitle == query -> 70
                subtitle.startsWith(query) -> 60
                title.contains(query) -> 50
                subtitle.contains(query) -> 40
                else -> 0
            }
        }

        private fun normalizeVoiceQuery(query: String): String {
            var normalized = query
                .lowercase()
                .replace(Regex("[^a-z0-9&' ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            normalized = normalized.replace(
                Regex(
                    "\\b(on|using|from|in)\\s+(the\\s+)?box\\s*(lore|floor)(\\s+app)?\\b",
                ),
                " ",
            )
            normalized = normalized
                .replace(Regex("^(please\\s+)?(play|start|put on|listen to)\\s+"), "")
                .replace(
                    Regex(
                        "^(the\\s+)?(latest|newest|new)\\s+(podcast\\s+)?episode\\s+(of|from)\\s+",
                    ),
                    "",
                )
                .replace(Regex("^(a|an|the)\\s+episode\\s+(of|from)\\s+"), "")
                .replace(Regex("^(the|a|an)\\s+"), "")
                .replace(Regex("^podcast\\s+"), "")
                .replace(Regex("\\s+podcast$"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            return normalized
        }

        private fun voiceMatchScore(title: String, author: String?, query: String): Int {
            val basicScore = searchScore(title, author, query)
            if (basicScore > 0) return basicScore
            val normalizedTitle = title.lowercase()
            if (query.contains(normalizedTitle)) return 75
            val queryTokens = query.split(" ").filter { it.length > 2 }.toSet()
            val titleTokens = normalizedTitle.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            if (queryTokens.isEmpty() || titleTokens.isEmpty()) return 0
            val overlap = queryTokens.intersect(titleTokens).size
            return if (overlap >= minOf(2, titleTokens.size)) 20 + overlap * 10 else 0
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            android.util.Log.d("AutoBrowse", "onGetItem: mediaId=$mediaId")
            return serviceScope.future {
                val item = if (
                    mediaId.startsWith(EPISODE_PREFIX) ||
                    mediaId.startsWith(QUEUE_PREFIX) ||
                    mediaId.startsWith(LEARN_PREFIX)
                ) {
                    resolveMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
                } else {
                    (
                        getRootChildren() +
                            getHomeChildren() +
                            getLibraryChildren() +
                            getDiscoverChildren()
                        ).firstOrNull { it.mediaId == mediaId }
                        ?: AutoMediaItemFactory.browsable(
                            id = mediaId,
                            title = getString(cx.aswin.boxcast.core.data.R.string.auto_app_name),
                        )
                }
                LibraryResult.ofItem(item, null)
            }
        }

        /**
         * Called when Android Auto wants to play a MediaItem from the browse tree.
         * Resolves items into playable MediaItems AND builds a queue from the
         * same podcast, mirroring the phone's QueueManager/PlayerViewModel behavior.
         */
        private suspend fun handleVoiceSearchQuery(searchQuery: String): MutableList<MediaItem> {
            val rawQuery = searchQuery.lowercase()
            val normalizedQuery = normalizeVoiceQuery(searchQuery)
            android.util.Log.d(
                "AutoBrowse",
                "Normalized voice query '$searchQuery' → '$normalizedQuery'",
            )
            
            handleVoiceQueryQuickFallbacks(rawQuery, normalizedQuery)?.let { return it }
            handleVoiceQueryHistoryResume(rawQuery)?.let { return it }
            handleVoiceQuerySubscriptionMatch(normalizedQuery)?.let { return it }
            handleVoiceQueryRemoteSearch(normalizedQuery)?.let { return it }
            
            val fallback = database.listeningHistoryDao().getLastPlayedSession()
            if (fallback != null) {
                android.util.Log.d("AutoBrowse", "Voice fallback: ${fallback.episodeTitle}")
                return mutableListOf(voiceHistoryItem(fallback))
            }
            
            return handlePlayAllMixtape().ifEmpty {
                getDownloadEpisodeItems().take(1).toMutableList()
            }
        }

        private suspend fun handleVoiceQueryQuickFallbacks(
            rawQuery: String,
            normalizedQuery: String
        ): MutableList<MediaItem>? {
            if (rawQuery.contains("download") || rawQuery.contains("offline")) {
                return getDownloadEpisodeItems().toMutableList()
            }
            if (rawQuery.contains("drive mix") || rawQuery.contains("mixtape")) {
                return handlePlayAllMixtape()
            }
            if (
                normalizedQuery in listOf(
                    "",
                    "something",
                    "anything",
                    "surprise me",
                    "podcast",
                    "podcasts",
                    "my shows",
                    "my mix",
                )
            ) {
                return handlePlayAllMixtape()
            }
            return null
        }

        private suspend fun handleVoiceQueryHistoryResume(rawQuery: String): MutableList<MediaItem>? {
            if (rawQuery.contains("subscription") || rawQuery.contains("resume")) {
                val lastSession = database.listeningHistoryDao().getLastPlayedSession()
                if (lastSession != null) {
                    android.util.Log.d(
                        "AutoBrowse",
                        "Voice resume matched: ${lastSession.episodeTitle}",
                    )
                    return mutableListOf(voiceHistoryItem(lastSession))
                }
            }
            return null
        }

        private suspend fun handleVoiceQuerySubscriptionMatch(normalizedQuery: String): MutableList<MediaItem>? {
            val subs = database.podcastDao().getSubscribedPodcastsList()
            val matchedPod = subs
                .map { podcast ->
                    podcast to voiceMatchScore(
                        podcast.title,
                        podcast.author,
                        normalizedQuery,
                    )
                }
                .filter { (_, score) -> score > 0 }
                .maxByOrNull { (_, score) -> score }
                ?.first
            
            if (matchedPod != null) {
                android.util.Log.d("AutoBrowse", "Voice matched subscription: ${matchedPod.title}")
                val episode = matchedPod.latestEpisode
                    ?: kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                        podcastRepository.getEpisodes(matchedPod.podcastId).firstOrNull()
                    }
                if (episode != null) {
                    return mutableListOf(
                        voiceEpisodeItem(
                            episode = episode,
                            podcastTitle = matchedPod.title,
                            podcastImageUrl = matchedPod.imageUrl,
                        ),
                    )
                }
            }
            return null
        }

        private suspend fun searchPodcastMatch(normalizedQuery: String): MediaItem? {
            return try {
                val podcast = podcastRepository.searchPodcasts(normalizedQuery)
                    .maxByOrNull {
                        voiceMatchScore(it.title, it.artist, normalizedQuery)
                    }
                podcast?.let {
                    val episode = it.latestEpisode
                        ?: podcastRepository.getEpisodes(it.id).firstOrNull()
                    episode?.let { match ->
                        voiceEpisodeItem(
                            episode = match,
                            podcastTitle = it.title,
                            podcastImageUrl = it.imageUrl,
                        )
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(
                    "AutoBrowse",
                    "Voice podcast search unavailable",
                    error,
                )
                null
            }
        }

        private suspend fun searchEpisodeMatch(normalizedQuery: String): MediaItem? {
            return try {
                val region = smartQueueSources.getRegion()
                podcastRepository.searchEpisodesSemantic(normalizedQuery, region)
                    .firstOrNull()
                    ?.let {
                        voiceEpisodeItem(
                            episode = it,
                            podcastTitle = it.podcastTitle,
                            podcastImageUrl = it.podcastImageUrl,
                        )
                    }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(
                    "AutoBrowse",
                    "Voice episode search unavailable",
                    error,
                )
                null
            }
        }

        private suspend fun handleVoiceQueryRemoteSearch(normalizedQuery: String): MutableList<MediaItem>? {
            val remoteItem = kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                kotlinx.coroutines.coroutineScope {
                    val podcastMatch = async { searchPodcastMatch(normalizedQuery) }
                    val episodeMatch = async { searchEpisodeMatch(normalizedQuery) }
                    select<MediaItem?> {
                        podcastMatch.onAwait { result ->
                            if (result != null) {
                                episodeMatch.cancel()
                                result
                            } else {
                                episodeMatch.await()
                            }
                        }
                        episodeMatch.onAwait { result ->
                            if (result != null) {
                                podcastMatch.cancel()
                                result
                            } else {
                                podcastMatch.await()
                            }
                        }
                    }
                }
            }
            if (remoteItem != null) {
                android.util.Log.d("AutoBrowse", "Voice matched remote result")
                return mutableListOf(remoteItem)
            }
            return null
        }


        private fun voiceEpisodeItem(
            episode: cx.aswin.boxcast.core.model.Episode,
            podcastTitle: String?,
            podcastImageUrl: String?,
        ): MediaItem = AutoMediaItemFactory.fromEpisode(
            episode = episode,
            source = AutoBrowseContract.SOURCE_SEARCH,
            artworkUri = AutoArtworkRepository.remoteUri(
                this@BoxLorePlaybackService,
                episode.imageUrl ?: episode.podcastImageUrl ?: podcastImageUrl,
            ),
            podcastTitle = podcastTitle,
            groupTitle = getString(cx.aswin.boxcast.core.data.R.string.auto_group_search),
        )

        private fun voiceHistoryItem(
            history: cx.aswin.boxcast.core.data.database.ListeningHistoryEntity,
        ): MediaItem = AutoMediaItemFactory.fromHistory(
            history = history,
            source = AutoBrowseContract.SOURCE_CONTINUE,
            artworkUri = AutoArtworkRepository.remoteUri(
                this@BoxLorePlaybackService,
                history.episodeImageUrl ?: history.podcastImageUrl,
            ),
            subtitle = buildProgressSubtitle(
                history.podcastName,
                history.progressMs,
                history.durationMs,
            ),
            groupTitle = getString(cx.aswin.boxcast.core.data.R.string.auto_group_continue),
        )

        private suspend fun handlePlayAllNewEpisodes(): MutableList<MediaItem> {
            android.util.Log.d("AutoBrowse", "Play All New Episodes triggered")
            val subscriptions = database.podcastDao().getSubscribedPodcastsList()
            
            val newEpisodes = subscriptions
                .mapNotNull { entity -> entity.latestEpisode?.let { ep -> ep to entity } }
                .sortedByDescending { (ep, _) -> ep.publishedDate }
                .take(20)
            
            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                mapOf("entry_point" to "android_auto_new_episodes"),
            )
            return newEpisodes.map { (episode, podcast) ->
                AutoMediaItemFactory.fromEpisode(
                    episode = episode,
                    source = AutoBrowseContract.SOURCE_NEW,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        episode.imageUrl ?: podcast.imageUrl,
                    ),
                    podcastTitle = podcast.title,
                )
            }.toMutableList()
        }

        private suspend fun handlePlayAllLiked(): MutableList<MediaItem> {
            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                mapOf("entry_point" to "android_auto_liked"),
            )
            return database.listeningHistoryDao()
                .getLikedEpisodesList(50)
                .map { history ->
                    AutoMediaItemFactory.fromHistory(
                        history = history,
                        source = AutoBrowseContract.SOURCE_LIKED,
                        artworkUri = AutoArtworkRepository.remoteUri(
                            this@BoxLorePlaybackService,
                            history.episodeImageUrl ?: history.podcastImageUrl,
                        ),
                        subtitle = AutoMediaItemFactory.buildDurationSubtitle(
                            history.podcastName,
                            history.durationMs,
                        ),
                        groupTitle = getString(cx.aswin.boxcast.core.data.R.string.auto_group_liked),
                    )
                }
                .toMutableList()
        }

        private suspend fun handlePlayAllDownloads(): MutableList<MediaItem> {
            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                mapOf("entry_point" to "android_auto_downloads"),
            )
            return getDownloadEpisodeItems().toMutableList()
        }

        private suspend fun handlePlayAllDrivePicks(): MutableList<MediaItem> {
            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                mapOf("entry_point" to "android_auto_drive_picks"),
            )
            return getDrivePicksChildren()
                .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_DRIVE_ID }
                .toMutableList()
        }

        private suspend fun handlePlayAllMixtape(): MutableList<MediaItem> {
            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                mapOf("entry_point" to "android_auto_mixtape"),
            )
            return getMixtapeChildren()
                .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_MIXTAPE_ID }
                .toMutableList()
        }

        private suspend fun handlePlayFromMixtape(episodeId: String): MutableList<MediaItem> {
            val mixtape = getMixtapeChildren()
                .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_MIXTAPE_ID }
            val selectedIndex = mixtape.indexOfFirst {
                it.mediaId.stripEpisodePrefix() == episodeId
            }
            if (selectedIndex < 0) return mutableListOf()
            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                mapOf("entry_point" to "android_auto_mixtape"),
            )
            return mixtape.drop(selectedIndex).toMutableList()
        }

        private suspend fun handlePlayFromQueue(episodeId: String): MutableList<MediaItem> {
            val queue = queueRepository.getQueueSnapshot()
            val selectedIndex = queue.indexOfFirst { it.id == episodeId }
            if (selectedIndex < 0) {
                android.util.Log.w("AutoBrowse", "Ignoring stale queue selection: $episodeId")
                return mutableListOf()
            }
            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                mapOf("entry_point" to "android_auto_queue"),
            )
            return queue.drop(selectedIndex).map { episode ->
                AutoMediaItemFactory.fromEpisode(
                    episode = episode,
                    source = AutoBrowseContract.SOURCE_QUEUE,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        episode.imageUrl ?: episode.podcastImageUrl,
                    ),
                    mediaIdPrefix = QUEUE_PREFIX,
                    groupTitle = getString(cx.aswin.boxcast.core.data.R.string.auto_group_queue),
                )
            }.toMutableList()
        }

        /**
         * Android Auto browse plays a single episode; this routes the follow-up queue
         * build through the same guarded SmartQueueEngine refill path as the transition
         * listener (shared isRefilling flag), so Auto no longer bypasses dedup/ranking
         * or persists rows without provenance.
         */
        private fun buildAndAppendQueueAsync(episodeId: String, mediaSession: MediaSession) {
            serviceScope.launch {
                try {
                    val player = mediaSession.player as? ExoPlayer ?: return@launch
                    // Wait briefly for the selected episode to become the current item so
                    // the engine refills relative to it (playback start is asynchronous).
                    var attempts = 0
                    while (attempts < 20) {
                        val currentId = player.currentMediaItem?.mediaId
                            ?.removePrefix(LEARN_PREFIX)?.removePrefix(EPISODE_PREFIX)?.removePrefix(QUEUE_PREFIX)
                        if (currentId == episodeId) break
                        kotlinx.coroutines.delay(250)
                        attempts++
                    }
                    if (isRefilling) {
                        android.util.Log.d("AutoBrowse", "Refill already in flight; skipping Auto queue build")
                        return@launch
                    }
                    isRefilling = true
                    try {
                        refillQueue(player)
                    } finally {
                        isRefilling = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AutoBrowse", "Async queue build failed", e)
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            android.util.Log.d("AutoBrowse", "onAddMediaItems: ${mediaItems.size} items")
            
            return serviceScope.future {
                if (mediaItems.size > 1) {
                    cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                        mapOf("entry_point" to "android_auto_play_all"),
                    )
                    return@future mediaItems.map { resolveMediaItem(it) }.toMutableList()
                }
                
                val selectedItem = mediaItems.first()
                val searchQuery = selectedItem.requestMetadata.searchQuery
                
                if (!searchQuery.isNullOrBlank()) {
                    android.util.Log.d("AutoBrowse", "Voice play request: '$searchQuery'")
                    cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                        mapOf("entry_point" to "android_auto_voice"),
                    )
                    return@future handleVoiceSearchQuery(searchQuery)
                }
                
                when (selectedItem.mediaId) {
                    PLAY_ALL_NEW_EPISODES_ID -> return@future handlePlayAllNewEpisodes()
                    AutoBrowseContract.PLAY_ALL_LIKED_ID -> return@future handlePlayAllLiked()
                    AutoBrowseContract.PLAY_ALL_DOWNLOADS_ID -> return@future handlePlayAllDownloads()
                    AutoBrowseContract.PLAY_ALL_DRIVE_ID -> return@future handlePlayAllDrivePicks()
                    AutoBrowseContract.PLAY_ALL_MIXTAPE_ID -> return@future handlePlayAllMixtape()
                }

                val source = selectedItem.mediaMetadata.extras
                    ?.getString(AutoBrowseContract.EXTRA_SOURCE)
                    ?: AutoBrowseContract.SOURCE_DISCOVER
                if (selectedItem.mediaId.startsWith(QUEUE_PREFIX)) {
                    return@future handlePlayFromQueue(selectedItem.mediaId.stripEpisodePrefix())
                }
                if (source == AutoBrowseContract.SOURCE_MIXTAPE) {
                    return@future handlePlayFromMixtape(selectedItem.mediaId.stripEpisodePrefix())
                }
                
                handleSingleMediaItemSelection(mediaSession, selectedItem, source)
            }
        }

        private suspend fun handleSingleMediaItemSelection(
            mediaSession: MediaSession,
            selectedItem: MediaItem,
            source: String
        ): MutableList<MediaItem> {
            android.util.Log.d("BoxCastPlayer", "onAddMediaItems: selectedItem.mediaId=${selectedItem.mediaId}, extrasKeys=${selectedItem.mediaMetadata.extras?.keySet()?.joinToString(", ")}")
            cx.aswin.boxcast.core.data.analytics.PendingEntryPoint.set(
                mapOf("entry_point" to "android_auto_$source"),
            )
            val resolvedItem = resolveMediaItem(selectedItem)
            val episodeId = selectedItem.mediaId.stripEpisodePrefix()
            android.util.Log.d("AutoBrowse", "Returning episode instantly: $episodeId, startsWithLearn=${selectedItem.mediaId.startsWith(LEARN_PREFIX)}")
            
            val skipSmartRefill = selectedItem.mediaId.startsWith(LEARN_PREFIX) ||
                source == AutoBrowseContract.SOURCE_DOWNLOADS ||
                source == AutoBrowseContract.SOURCE_QUEUE
            android.util.Log.d("AutoBrowse", "onAddMediaItems skipSmartRefill=$skipSmartRefill")
            if (!skipSmartRefill) {
                buildAndAppendQueueAsync(episodeId, mediaSession)
            } else {
                android.util.Log.d("AutoBrowse", "Explicit/offline source: skipping async queue append")
            }
            return mutableListOf(resolvedItem)
        }
        
        // findPodcastIdForEpisode is defined at the service level and accessible from this inner class

        
        /**
         * Resolve a single MediaItem into a playable one with a proper URI.
         */
        private suspend fun resolveMediaItem(item: MediaItem): MediaItem {
            android.util.Log.d("BoxCastPlayer", "resolveMediaItem: mediaId=${item.mediaId}, initialArtworkUri=${item.mediaMetadata.artworkUri}")
            val episodeId = item.mediaId.stripEpisodePrefix()
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
            
            if (uri != null) {
                return item.buildUpon()
                    .setUri(uri)
                    .setCustomCacheKey(episodeId)
                    .build()
            }

            val download = database.downloadedEpisodeDao().getDownload(episodeId)
            val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
            val queueItem = queueRepository.getQueueItemByEpisodeId(episodeId)
            val resolvedAudioUrl = download
                ?.takeIf {
                    it.status ==
                        cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity.STATUS_COMPLETED
                }
                ?.let { resolveDownloadRequestUri(episodeId) }
                ?: historyItem?.episodeAudioUrl
                ?.takeIf { it.isNotBlank() }
                ?: queueItem?.audioUrl?.takeIf { it.isNotBlank() }
            if (resolvedAudioUrl != null) {
                val histArtworkUriStr = historyItem?.episodeImageUrl ?: historyItem?.podcastImageUrl
                android.util.Log.d("BoxCastPlayer", "resolveMediaItem: resolved from history: '$histArtworkUriStr'")
                return MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(resolvedAudioUrl)
                    .setCustomCacheKey(episodeId)
                    .setMediaMetadata(
                        item.mediaMetadata.buildUpon()
                            .setTitle(historyItem?.episodeTitle ?: queueItem?.title)
                            .setArtist(historyItem?.podcastName ?: queueItem?.podcastTitle)
                            .setArtworkUri(
                                AutoArtworkRepository.remoteUri(
                                    this@BoxLorePlaybackService,
                                    histArtworkUriStr ?: queueItem?.imageUrl ?: queueItem?.podcastImageUrl,
                                ),
                            )
                            .setExtras(
                                AutoBrowseContract.mergeExtras(
                                    item.mediaMetadata.extras,
                                    AutoBrowseContract.itemExtras(
                                        source = item.mediaMetadata.extras
                                            ?.getString(AutoBrowseContract.EXTRA_SOURCE)
                                            ?: AutoBrowseContract.SOURCE_DISCOVER,
                                        downloadStatus = if (
                                            download?.status ==
                                            cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity.STATUS_COMPLETED
                                        ) {
                                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
                                        } else {
                                            null
                                        },
                                    ),
                                ),
                            )
                            .build()
                    )
                    .build()
            }
            
            // Try API
            val episode = podcastRepository.getEpisode(episodeId)
            if (episode != null) {
                android.util.Log.d("BoxCastPlayer", "resolveMediaItem: resolved from API: '${episode.imageUrl}'")
                return MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(episode.audioUrl)
                    .setCustomCacheKey(episodeId)
                    .setMediaMetadata(
                        item.mediaMetadata.buildUpon()
                            .setTitle(episode.title)
                            .setArtist(episode.podcastArtist ?: "")
                            .setArtworkUri(
                                AutoArtworkRepository.remoteUri(
                                    this@BoxLorePlaybackService,
                                    episode.imageUrl ?: episode.podcastImageUrl,
                                ),
                            )
                            .build()
                    )
                    .build()
            }
            
            android.util.Log.e("AutoBrowse", "Could not resolve episode: $episodeId")
            return item
        }

        private suspend fun resolveDomainEpisode(episodeId: String): cx.aswin.boxcast.core.model.Episode? {
            queueRepository.getQueueSnapshot().firstOrNull { it.id == episodeId }?.let { return it }
            val history = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (history?.episodeAudioUrl != null) {
                return cx.aswin.boxcast.core.model.Episode(
                    id = history.episodeId,
                    title = history.episodeTitle,
                    description = history.episodeDescription.orEmpty(),
                    audioUrl = history.episodeAudioUrl,
                    imageUrl = history.episodeImageUrl,
                    podcastImageUrl = history.podcastImageUrl,
                    podcastTitle = history.podcastName,
                    podcastId = history.podcastId,
                    duration = (history.durationMs / 1_000L).toInt(),
                    enclosureType = history.enclosureType,
                )
            }
            val download = database.downloadedEpisodeDao().getDownload(episodeId)
            if (
                download?.status ==
                cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity.STATUS_COMPLETED
            ) {
                val audioUrl = resolveDownloadRequestUri(episodeId)
                    ?: download.localFilePath.takeIf {
                        it.isNotBlank() && it != "CACHED" && java.io.File(it).isFile
                    }?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                if (audioUrl != null) {
                    return cx.aswin.boxcast.core.model.Episode(
                        id = download.episodeId,
                        title = download.episodeTitle,
                        description = download.episodeDescription.orEmpty(),
                        audioUrl = audioUrl,
                        imageUrl = download.episodeImageUrl,
                        podcastImageUrl = download.podcastImageUrl,
                        podcastTitle = download.podcastName,
                        podcastId = download.podcastId,
                        duration = (download.durationMs / 1_000L).toInt(),
                        publishedDate = download.publishedDate,
                    )
                }
            }
            return podcastRepository.getEpisode(episodeId)
        }

        private fun String.stripEpisodePrefix(): String =
            removePrefix(LEARN_PREFIX).removePrefix(EPISODE_PREFIX).removePrefix(QUEUE_PREFIX)

        // ============= Browse Tree Builders =============

        private fun getRootChildren(): List<MediaItem> {
            return listOf(
                AutoMediaItemFactory.browsable(
                    id = HOME_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_home),
                    subtitle = getString(cx.aswin.boxcast.core.data.R.string.auto_home_subtitle),
                    artworkUri = folderArtwork(HOME_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = LIBRARY_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_library),
                    subtitle = getString(cx.aswin.boxcast.core.data.R.string.auto_library_subtitle),
                    artworkUri = folderArtwork(LIBRARY_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = DISCOVER_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_discover),
                    subtitle = getString(cx.aswin.boxcast.core.data.R.string.auto_discover_subtitle),
                    artworkUri = folderArtwork(DISCOVER_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = DOWNLOADS_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_downloads),
                    subtitle = getString(cx.aswin.boxcast.core.data.R.string.auto_downloads_subtitle),
                    artworkUri = folderArtwork(DOWNLOADS_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
            )
        }

        private suspend fun getContinueListeningChildren(): List<MediaItem> {
            val resumeItems = database.listeningHistoryDao().getResumeItemsList()
            android.util.Log.d("AutoBrowse", "Continue Listening: ${resumeItems.size} items")
            
            return resumeItems.map { entity ->
                val subtitle = buildProgressSubtitle(entity.podcastName, entity.progressMs, entity.durationMs)
                AutoMediaItemFactory.fromHistory(
                    history = entity,
                    source = AutoBrowseContract.SOURCE_CONTINUE,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        entity.episodeImageUrl ?: entity.podcastImageUrl,
                    ),
                    subtitle = subtitle,
                    groupTitle = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_group_continue,
                    ),
                )
            }
        }

        private suspend fun getSubscriptionsChildren(): List<MediaItem> {
            val subscriptions = database.podcastDao().getSubscribedPodcastsList()
            android.util.Log.d("AutoBrowse", "Subscriptions: ${subscriptions.size} podcasts")
            val history = database.listeningHistoryDao().getRecentHistoryList(300)
            val scores = adaptiveCandidateScorer.scorePodcasts(
                podcasts = subscriptions.map { it.toScorable() },
                history = history,
                objective = RankingObjective.YOUR_SHOWS,
            )
            val rankedSubscriptions = subscriptions.sortedByDescending {
                scores[it.podcastId] ?: 0.0
            }

            return rankedSubscriptions.map { entity ->
                AutoMediaItemFactory.browsable(
                    id = "$SUBSCRIPTION_PREFIX${entity.podcastId}",
                    title = entity.title,
                    subtitle = entity.author,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        entity.imageUrl,
                    ),
                    mediaType = MediaMetadata.MEDIA_TYPE_PODCAST,
                    childStyleExtras = AutoBrowseContract.mergeExtras(
                        AutoBrowseContract.listChildrenExtras(),
                        android.os.Bundle().apply {
                            putString(
                                androidx.media3.session.MediaConstants
                                    .EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                getString(
                                    cx.aswin.boxcast.core.data.R.string.auto_group_subscriptions,
                                ),
                            )
                        },
                    ),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                )
            }
        }

        private suspend fun getHomeChildren(): List<MediaItem> {
            val newEpCount = try {
                database.podcastDao().getSubscribedPodcastsList().count { it.latestEpisode != null }
            } catch (e: Exception) { 0 }
            val newEpSubtitle = when {
                newEpCount == 0 -> getString(cx.aswin.boxcast.core.data.R.string.auto_new_none)
                newEpCount == 1 -> getString(cx.aswin.boxcast.core.data.R.string.auto_new_one)
                else -> getString(
                    cx.aswin.boxcast.core.data.R.string.auto_new_many,
                    newEpCount,
                )
            }

            return listOf(
                AutoMediaItemFactory.browsable(
                    id = HOME_CONTINUE_LISTENING_ID,
                    title = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_continue_listening,
                    ),
                    artworkUri = folderArtwork(HOME_CONTINUE_LISTENING_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = AutoBrowseContract.HOME_DRIVE_MIX_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_drive_mix),
                    subtitle = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_drive_mix_subtitle,
                    ),
                    artworkUri = folderArtwork(AutoBrowseContract.HOME_DRIVE_MIX_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = HOME_NEW_EPISODES_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_whats_new),
                    subtitle = newEpSubtitle,
                    artworkUri = folderArtwork(HOME_NEW_EPISODES_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
            )
        }
        
        private fun getDiscoverChildren(): List<MediaItem> {
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val timeLabel = when (hour) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..22 -> "Evening"
                else -> "Late Night"
            }
            
            return listOf(
                AutoMediaItemFactory.browsable(
                    id = AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_drive_picks),
                    subtitle = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_drive_picks_subtitle,
                    ),
                    artworkUri = folderArtwork(AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = AutoBrowseContract.DISCOVER_TIME_PICKS_ID,
                    title = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_time_picks,
                        timeLabel,
                    ),
                    subtitle = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_time_picks_subtitle,
                    ),
                    artworkUri = folderArtwork(AutoBrowseContract.DISCOVER_TIME_PICKS_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = AutoBrowseContract.DISCOVER_GENRES_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_browse_genre),
                    subtitle = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_browse_genre_subtitle,
                    ),
                    artworkUri = folderArtwork(AutoBrowseContract.DISCOVER_GENRES_ID),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                ),
            )
        }
        
        private fun getExplorePicksChildren(): List<MediaItem> {
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            return getTimeBasedGenres(hour).map { (vibeId, title) ->
                AutoMediaItemFactory.browsable(
                    id = "${AutoBrowseContract.CURATED_PREFIX}$vibeId",
                    title = title,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.gridChildrenExtras(),
                )
            }
        }
        
        private fun getGenresChildren(): List<MediaItem> {
            return listOf(
                "News" to "News",
                "Technology" to "Tech",
                "Business" to "Business",
                "Comedy" to "Comedy",
                GENRE_TRUE_CRIME to GENRE_TRUE_CRIME,
                "Sports" to "Sports",
                "Health" to "Health",
                "History" to "History",
                "Arts" to "Arts",
                "Society & Culture" to "Society",
                "Education" to "Education",
                "Science" to "Science",
                GENRE_TV_FILM to GENRE_TV_FILM,
                "Fiction" to "Fiction",
                "Music" to "Music",
                "Religion & Spirituality" to "Religion",
                "Kids & Family" to "Family",
                "Leisure" to "Leisure",
                "Government" to "Government",
            ).map { (category, title) ->
                AutoMediaItemFactory.browsable(
                    id = "${AutoBrowseContract.GENRE_PREFIX}$category",
                    title = title,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.gridChildrenExtras(),
                )
            }
        }

        private suspend fun getLibraryChildren(): List<MediaItem> =
            getSubscriptionsChildren() + listOf(
                AutoMediaItemFactory.browsable(
                    id = AutoBrowseContract.LIBRARY_LIKED_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_liked_episodes),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = AutoBrowseContract.LIBRARY_HISTORY_ID,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_listening_history),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                ),
            )

        private suspend fun getLikedChildren(): List<MediaItem> {
            val history = database.listeningHistoryDao().getLikedEpisodesList(50)
            if (history.isEmpty()) return emptyList()
            val items = history.map {
                AutoMediaItemFactory.fromHistory(
                    history = it,
                    source = AutoBrowseContract.SOURCE_LIKED,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    ),
                    subtitle = AutoMediaItemFactory.buildDurationSubtitle(
                        it.podcastName,
                        it.durationMs,
                    ),
                    groupTitle = getString(cx.aswin.boxcast.core.data.R.string.auto_group_liked),
                )
            }
            return if (items.size > 1) {
                listOf(
                    buildPlayAllItem(
                        AutoBrowseContract.PLAY_ALL_LIKED_ID,
                        items.size,
                        AutoBrowseContract.SOURCE_LIKED,
                    ),
                ) + items
            } else {
                items
            }
        }

        private suspend fun getHistoryChildren(): List<MediaItem> =
            database.listeningHistoryDao().getRecentHistoryList(50).map {
                AutoMediaItemFactory.fromHistory(
                    history = it,
                    source = AutoBrowseContract.SOURCE_HISTORY,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    ),
                    subtitle = AutoMediaItemFactory.buildDurationSubtitle(
                        it.podcastName,
                        it.durationMs,
                    ),
                    groupTitle = getString(cx.aswin.boxcast.core.data.R.string.auto_group_history),
                )
            }

        private suspend fun getMixtapeChildren(): List<MediaItem> {
            val now = System.currentTimeMillis()
            if (lastMixtape.isNotEmpty() && now - lastMixtapeUpdatedAt < 15 * 60_000L) {
                return lastMixtape
            }
            val subscriptionEntities = database.podcastDao().getSubscribedPodcastsList()
            val subscriptions = subscriptionEntities.map { it.toAutoPodcast() }
            val history = database.listeningHistoryDao().getRecentHistoryList(300)
            var result = cx.aswin.boxcast.core.data.MixtapeEngine.build(
                subscriptions = subscriptions,
                history = history,
                adaptiveRanking = cx.aswin.boxcast.core.data.MixtapeEngine.AdaptiveRanking(
                    adaptiveCandidateScorer,
                ),
            )
            if (result.episodes.size < 3) {
                val recommendations = runCatching {
                    kotlinx.coroutines.withTimeout(6_000L) {
                        smartQueueSources.getPersonalizedRecommendations(
                            history = smartQueueSources.getHistoryForRecommendations(25),
                            interests = smartQueueSources.getInterests(),
                            country = smartQueueSources.getRegion(),
                            subscribedPodcastIds = subscriptions.map { it.id },
                            subscribedGenres = subscriptionEntities.mapNotNull { it.genre }.distinct(),
                        )
                    }
                }.onFailure {
                    android.util.Log.w("AutoBrowse", "Mixtape fallback unavailable", it)
                }.getOrDefault(emptyList())
                result = cx.aswin.boxcast.core.data.MixtapeEngine.build(
                    subscriptions = subscriptions,
                    history = history,
                    recommendations = recommendations,
                    adaptiveRanking = cx.aswin.boxcast.core.data.MixtapeEngine.AdaptiveRanking(
                        adaptiveCandidateScorer,
                    ),
                )
            }
            val episodes = result.podcasts.mapNotNull { podcast ->
                podcast.latestEpisode?.let { episode ->
                    AutoMediaItemFactory.fromEpisode(
                        episode = episode,
                        source = AutoBrowseContract.SOURCE_MIXTAPE,
                        artworkUri = AutoArtworkRepository.remoteUri(
                            this@BoxLorePlaybackService,
                            episode.imageUrl ?: episode.podcastImageUrl ?: podcast.imageUrl,
                        ),
                        podcastTitle = podcast.title,
                        groupTitle = getString(
                            cx.aswin.boxcast.core.data.R.string.auto_group_mixtape,
                        ),
                    )
                }
            }
            val items = if (episodes.size > 1) {
                listOf(
                    buildPlayAllItem(
                        AutoBrowseContract.PLAY_ALL_MIXTAPE_ID,
                        episodes.size,
                        AutoBrowseContract.SOURCE_MIXTAPE,
                    ),
                ) + episodes
            } else {
                episodes
            }
            lastMixtape = items
            lastMixtapeUpdatedAt = now
            return items
        }

        private suspend fun getQueueChildren(): List<MediaItem> =
            queueRepository.getQueueSnapshot().take(50).map { episode ->
                AutoMediaItemFactory.fromEpisode(
                    episode = episode,
                    source = AutoBrowseContract.SOURCE_QUEUE,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        episode.imageUrl ?: episode.podcastImageUrl,
                    ),
                    podcastTitle = episode.podcastTitle,
                    mediaIdPrefix = QUEUE_PREFIX,
                    groupTitle = getString(cx.aswin.boxcast.core.data.R.string.auto_group_queue),
                )
            }

        private suspend fun getDownloadsChildren(): List<MediaItem> {
            val items = getDownloadEpisodeItems()
            return if (items.size > 1) {
                listOf(
                    buildPlayAllItem(
                        AutoBrowseContract.PLAY_ALL_DOWNLOADS_ID,
                        items.size,
                        AutoBrowseContract.SOURCE_DOWNLOADS,
                    ),
                ) + items
            } else {
                items
            }
        }

        private suspend fun getDownloadEpisodeItems(): List<MediaItem> =
            database.downloadedEpisodeDao().getCompletedDownloads(50).map { download ->
                val sourceUri = download.localFilePath.takeIf {
                    it.isNotBlank() && it != "CACHED" && java.io.File(it).exists()
                }?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                    ?: resolveDownloadRequestUri(download.episodeId)
                    ?: database.listeningHistoryDao().getHistoryItem(download.episodeId)?.episodeAudioUrl
                    ?: queueRepository.getQueueItemByEpisodeId(download.episodeId)?.audioUrl
                AutoMediaItemFactory.fromDownload(
                    download = download,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        download.episodeImageUrl ?: download.podcastImageUrl,
                    ),
                    uri = sourceUri,
                    groupTitle = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_group_downloads,
                    ),
                )
            }

        private fun resolveDownloadRequestUri(episodeId: String): String? =
            runCatching {
                cx.aswin.boxcast.core.data.DownloadRepository
                    .getDownloadManager(this@BoxLorePlaybackService)
                    .downloadIndex
                    .getDownload(episodeId)
                    ?.request
                    ?.uri
                    ?.toString()
            }.onFailure {
                android.util.Log.w(
                    "AutoBrowse",
                    "Unable to resolve cached download URI for $episodeId",
                    it,
                )
            }.getOrNull()

        private suspend fun getDrivePicksChildren(): List<MediaItem> {
            val calendar = java.util.Calendar.getInstance()
            val driveVibes = AutoBrowseContract.driveVibes(calendar)
            val region = kotlinx.coroutines.withTimeoutOrNull(1_000L) {
                userPreferencesRepository.regionStream.first()
            } ?: "us"
            val driveFeeds = kotlinx.coroutines.withTimeoutOrNull(6_000L) {
                podcastRepository.getCuratedVibes(driveVibes, region)
            }.orEmpty()
            val fallbackFeeds = if (driveFeeds.values.all { it.isEmpty() }) {
                val fallbackIds = getTimeBasedGenres(
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                ).map { it.first }
                kotlinx.coroutines.withTimeoutOrNull(6_000L) {
                    podcastRepository.getCuratedVibes(fallbackIds, region)
                }.orEmpty()
            } else {
                emptyMap()
            }
            val completedIds = database.listeningHistoryDao().getCompletedEpisodeIds().toSet()
            val recentIds = database.listeningHistoryDao()
                .getRecentHistoryList(30)
                .mapTo(mutableSetOf()) { it.episodeId }
            val feedMap = if (driveFeeds.values.any { it.isNotEmpty() }) {
                driveFeeds
            } else {
                fallbackFeeds
            }
            val episodes = (driveVibes + feedMap.keys.sorted())
                .distinct()
                .flatMap { feedMap[it].orEmpty() }
                .distinctBy { it.id }
                .mapNotNull { podcast ->
                    podcast.latestEpisode?.let { episode -> episode to podcast }
                }
                .filter { (episode, _) ->
                    episode.id !in completedIds && episode.id !in recentIds
                }
                .take(20)
            if (episodes.isEmpty()) {
                return lastDrivePicks.ifEmpty {
                    val downloads = getDownloadEpisodeItems()
                    if (downloads.isNotEmpty()) downloads.take(20) else getQueueChildren().take(20)
                }
            }

            val items = episodes.map { (episode, podcast) ->
                AutoMediaItemFactory.fromEpisode(
                    episode = episode.copy(
                        podcastId = podcast.id,
                        podcastTitle = podcast.title,
                        podcastArtist = podcast.artist,
                        podcastImageUrl = podcast.imageUrl,
                    ),
                    source = AutoBrowseContract.SOURCE_DRIVE,
                    artworkUri = AutoArtworkRepository.remoteUri(
                        this@BoxLorePlaybackService,
                        episode.imageUrl ?: podcast.imageUrl,
                    ),
                    podcastTitle = podcast.title,
                    groupTitle = getString(cx.aswin.boxcast.core.data.R.string.auto_group_drive),
                )
            }
            val result = if (items.size > 1) {
                listOf(
                    buildPlayAllItem(
                        AutoBrowseContract.PLAY_ALL_DRIVE_ID,
                        items.size,
                        AutoBrowseContract.SOURCE_DRIVE,
                    ),
                ) + items
            } else {
                items
            }
            lastDrivePicks = result
            return result
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
            
            items.add(
                buildPlayAllItem(
                    PLAY_ALL_NEW_EPISODES_ID,
                    newEpisodes.size,
                    AutoBrowseContract.SOURCE_NEW,
                ),
            )
            
            items.addAll(
                newEpisodes.map { (ep, pod) ->
                    AutoMediaItemFactory.fromEpisode(
                        episode = ep,
                        source = AutoBrowseContract.SOURCE_NEW,
                        artworkUri = AutoArtworkRepository.remoteUri(
                            this@BoxLorePlaybackService,
                            ep.imageUrl ?: pod.imageUrl,
                        ),
                        podcastTitle = pod.title,
                        groupTitle = getString(
                            cx.aswin.boxcast.core.data.R.string.auto_group_new,
                        ),
                    )
                }
            )
            
            return items
        }
        
        private suspend fun getCuratedChildren(vibeId: String): List<MediaItem> {
            legacyAutoGenreCategory(vibeId)?.let { return getGenreChildren(it) }
            val region = kotlinx.coroutines.withTimeoutOrNull(1_000L) {
                userPreferencesRepository.regionStream.first()
            } ?: "us"
            val curatedPodcasts = podcastRepository.getCuratedPodcasts(
                vibeId,
                region,
            )
            android.util.Log.d(
                "AutoBrowse",
                "Curated $vibeId: ${curatedPodcasts.size} podcasts",
            )
            return buildPodcastFolderItems(curatedPodcasts)
        }

        private suspend fun getGenreChildren(category: String): List<MediaItem> {
            val region = kotlinx.coroutines.withTimeoutOrNull(1_000L) {
                userPreferencesRepository.regionStream.first()
            } ?: "us"
            val podcasts = kotlinx.coroutines.withTimeoutOrNull(6_000L) {
                podcastRepository.getTrendingPodcasts(
                    country = region,
                    limit = 50,
                    category = category.lowercase(),
                )
            }.orEmpty()
            android.util.Log.d(
                "AutoBrowse",
                "Genre chart category=$category country=$region: ${podcasts.size} podcasts",
            )
            return buildPodcastFolderItems(podcasts)
        }

        private fun buildPodcastFolderItems(
            podcasts: List<cx.aswin.boxcast.core.model.Podcast>,
        ): List<MediaItem> = podcasts.map { podcast ->
            AutoMediaItemFactory.browsable(
                id = "$SUBSCRIPTION_PREFIX${podcast.id}",
                title = podcast.title,
                subtitle = podcast.artist,
                artworkUri = AutoArtworkRepository.remoteUri(
                    this@BoxLorePlaybackService,
                    podcast.imageUrl,
                ),
                mediaType = MediaMetadata.MEDIA_TYPE_PODCAST,
                childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            )
        }

        private fun legacyAutoGenreCategory(genreId: String): String? = when (genreId) {
            "true_crime" -> GENRE_TRUE_CRIME
            "comedy" -> "Comedy"
            "news" -> "News"
            "technology" -> "Technology"
            "science" -> "Science"
            "health" -> "Health"
            "business" -> "Business"
            "sports" -> "Sports"
            "history" -> "History"
            "society" -> "Society & Culture"
            "education" -> "Education"
            "arts" -> "Arts"
            "music" -> "Music"
            "fiction" -> "Fiction"
            "kids" -> "Kids & Family"
            "self_improvement" -> "Health"
            else -> null
        }

        private suspend fun getPodcastEpisodes(podcastId: String): List<MediaItem> {
            android.util.Log.d("AutoBrowse", "Fetching episodes for podcast: $podcastId")
            
            // Get podcast details for artwork fallback
            val podcastEntity = database.podcastDao().getPodcast(podcastId)
            val podcastArtwork = podcastEntity?.imageUrl
            
            // Fetch latest episodes (limit to 50 for Auto performance)
            val episodes = podcastRepository.getEpisodesPaginated(podcastId, limit = 50, sort = "newest")
            android.util.Log.d("AutoBrowse", "Got ${episodes.episodes.size} episodes for $podcastId")
            val historyById = database.listeningHistoryDao()
                .getRecentHistoryList(300)
                .associateBy { it.episodeId }
            
            return episodes.episodes.map { episode ->
                val history = historyById[episode.id]
                AutoMediaItemFactory.playable(
                    AutoPlayableSpec(
                        mediaId = "episode:${episode.id}",
                        title = episode.title,
                        podcastTitle = podcastEntity?.title ?: episode.podcastTitle,
                        subtitle = AutoMediaItemFactory.buildDurationSubtitle(
                            podcastEntity?.title ?: episode.podcastTitle,
                            episode.duration.toLong() * 1_000L,
                        ),
                        artworkUri = AutoArtworkRepository.remoteUri(
                            this@BoxLorePlaybackService,
                            episode.imageUrl ?: podcastArtwork,
                        ),
                        uri = episode.audioUrl,
                        durationMs = episode.duration.toLong() * 1_000L,
                        source = AutoBrowseContract.SOURCE_DISCOVER,
                        progress = history?.let {
                            if (it.durationMs > 0) {
                                it.progressMs.toDouble() / it.durationMs.toDouble()
                            } else {
                                0.0
                            }
                        },
                        isCompleted = history?.isCompleted == true,
                        customCacheKey = episode.id,
                    ),
                )
            }
        }

        // ============= Helpers =============

        private fun buildPlayAllItem(id: String, count: Int, source: String): MediaItem =
            AutoMediaItemFactory.playable(
                AutoPlayableSpec(
                    mediaId = id,
                    title = getString(cx.aswin.boxcast.core.data.R.string.auto_play_all, count),
                    podcastTitle = getString(
                        cx.aswin.boxcast.core.data.R.string.auto_play_all_subtitle,
                    ),
                    source = source,
                    supportedCommands = emptyList(),
                ),
            )

        private fun folderArtwork(folderId: String): android.net.Uri? =
            autoCollageUris[folderId]
                ?: AutoArtworkRepository.collageUri(this@BoxLorePlaybackService, folderId)

        private fun slicePage(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
            val safePageSize = pageSize.takeIf { it > 0 }?.coerceAtMost(50) ?: 50
            val start = page.coerceAtLeast(0) * safePageSize
            if (start >= items.size) return emptyList()
            return items.subList(start, minOf(start + safePageSize, items.size))
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


    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class CoilBitmapLoader(
    private val context: android.content.Context,
    private val serviceScope: kotlinx.coroutines.CoroutineScope
) : androidx.media3.common.util.BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean {
        return true
    }

    override fun decodeBitmap(data: ByteArray): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) {
                com.google.common.util.concurrent.Futures.immediateFuture(bitmap)
            } else {
                com.google.common.util.concurrent.Futures.immediateFailedFuture(
                    IllegalArgumentException("Could not decode bitmap")
                )
            }
        } catch (e: Exception) {
            com.google.common.util.concurrent.Futures.immediateFailedFuture(e)
        }
    }

    override fun loadBitmap(uri: android.net.Uri): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> {
        return serviceScope.future {
            try {
                android.util.Log.d("BoxCastPlayer", "CoilBitmapLoader: loadBitmap started for $uri")
                val loader = coil.Coil.imageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false) // Required: system notifications cannot use hardware-backed bitmaps
                    .build()
                val result = loader.execute(request)
                val bitmap = (result as? coil.request.SuccessResult)?.drawable?.toBitmap()
                if (bitmap != null) {
                    android.util.Log.d("BoxCastPlayer", "CoilBitmapLoader: loadBitmap succeeded for $uri")
                    bitmap
                } else {
                    val errorMsg = "CoilBitmapLoader: result is not a success or drawable could not be converted to bitmap for $uri"
                    android.util.Log.e("BoxCastPlayer", errorMsg)
                    throw IllegalArgumentException(errorMsg)
                }
            } catch (e: Exception) {
                android.util.Log.e("BoxCastPlayer", "CoilBitmapLoader: loadBitmap failed for $uri", e)
                throw e
            }
        }
    }
}

