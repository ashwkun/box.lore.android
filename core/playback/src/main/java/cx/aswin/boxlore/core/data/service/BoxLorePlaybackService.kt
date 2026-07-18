package cx.aswin.boxlore.core.data.service

import android.content.Intent
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import cx.aswin.boxlore.core.data.playback.PlaybackIntroOutroController
import cx.aswin.boxlore.core.data.playback.PlaybackSkipPolicy
import cx.aswin.boxlore.core.data.service.auto.AutoBrowseContract
import cx.aswin.boxlore.core.data.service.auto.AutoBrowseLibraryCallback
import cx.aswin.boxlore.core.data.service.auto.AutoBrowseLibraryHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val LEARN_PREFIX = AutoBrowseContract.LEARN_PREFIX
private const val EPISODE_PREFIX = AutoBrowseContract.EPISODE_PREFIX
private const val QUEUE_PREFIX = AutoBrowseContract.QUEUE_PREFIX
private const val GENRE_TRUE_CRIME = AutoBrowseContract.GENRE_TRUE_CRIME
private const val GENRE_TV_FILM = AutoBrowseContract.GENRE_TV_FILM

class BoxLorePlaybackService :
    MediaLibraryService(),
    AutoBrowseLibraryHost {
    override fun asContext(): android.content.Context = this

    override var mediaSession: MediaLibrarySession? = null
        private set
    private var exoPlayer: ExoPlayer? = null
    override lateinit var seekBackAction: androidx.media3.session.CommandButton
        private set
    override lateinit var seekForwardAction: androidx.media3.session.CommandButton
        private set
    private lateinit var likeAction: androidx.media3.session.CommandButton
    private lateinit var addToQueueAction: androidx.media3.session.CommandButton
    override lateinit var markCompleteAction: androidx.media3.session.CommandButton
        private set

    @Volatile
    override var autoCollageUris: Map<String, android.net.Uri> = emptyMap()
        private set

    @VisibleForTesting internal var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @VisibleForTesting internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    override val serviceScope by lazy { CoroutineScope(mainDispatcher + SupervisorJob()) }

    /**
     * Shared Application graph — do not rebuild PodcastRepository / ranking / RSS here.
     * Installed in [cx.aswin.boxlore.BoxLoreApplication] via SharedAppDependenciesHolder.
     */
    private val sharedDeps by lazy {
        cx.aswin.boxlore.core.data.SharedAppDependenciesHolder
            .require()
    }
    private val downloadDeps by lazy {
        cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
            .require()
    }
    override val userPreferencesRepository by lazy { sharedDeps.userPreferencesRepository }
    override val database by lazy { sharedDeps.database }
    override val podcastRepository by lazy { sharedDeps.podcastRepository }
    private val subscriptionRepository by lazy { sharedDeps.subscriptionRepository }
    private val queueSkipMemory by lazy {
        cx.aswin.boxlore.core.data.QueueSkipMemory
            .fromContext(this)
    }
    private val rankingFeedbackRepository by lazy { sharedDeps.rankingFeedbackRepository }
    override val adaptiveCandidateScorer by lazy { sharedDeps.adaptiveCandidateScorer }
    override val smartQueueSources by lazy {
        cx.aswin.boxlore.core.data.DefaultSmartQueueSources(
            context = this,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }
    private val smartQueueEngine by lazy {
        cx.aswin.boxlore.core.data.DefaultSmartQueueEngine(
            sources = smartQueueSources,
            skipMemory = queueSkipMemory,
            adaptiveScorer = adaptiveCandidateScorer,
        )
    }
    override val queueRepository by lazy {
        // Queue lives in :core:playback; reuse shared DB + PodcastRepository (no parallel ranking/RSS).
        cx.aswin.boxlore.core.data
            .QueueRepository(database, podcastRepository)
    }
    override var isRefilling = false
    private val QUEUE_MAX_SIZE = 50
    private val smartQueueRefillCoordinator by lazy {
        SmartQueueRefillCoordinator(
            database = database,
            podcastRepository = podcastRepository,
            queueRepository = queueRepository,
            smartQueueEngine = smartQueueEngine,
            userPreferencesRepository = userPreferencesRepository,
            mainDispatcher = mainDispatcher,
            ioDispatcher = ioDispatcher,
            findPodcastIdForEpisode = ::findPodcastIdForEpisode,
            queueMaxSize = QUEUE_MAX_SIZE,
            mediaIdPrefixStripper = cx.aswin.boxlore.core.data.playback.SmartQueueRefillPolicy::stripQueuePrefixes,
        )
    }

    @Volatile private var cachedSkipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS

    @Volatile private var cachedSkipEndingMs = PlaybackSkipPolicy.DEFAULT_SKIP_ENDING_MS

    @Volatile private var cachedSeekBackwardMs = PlaybackSkipPolicy.DEFAULT_SEEK_BACKWARD_MS

    @Volatile private var cachedSeekForwardMs = PlaybackSkipPolicy.DEFAULT_SEEK_FORWARD_MS

    private val introOutroController by lazy {
        PlaybackIntroOutroController(
            scope = serviceScope,
            database = database,
            globalSkipBeginningMs = { cachedSkipBeginningMs },
            globalSkipEndingMs = { cachedSkipEndingMs },
            lifecycleEpisodeId = ::lifecycleEpisodeId,
            findPodcastIdForEpisode = ::findPodcastIdForEpisode,
            onActiveDurationResolved = { episodeId, durationMs ->
                if (episodeId == playbackSessionEpisodeId) {
                    playbackSessionTotalDurationMs = durationMs
                }
            },
            onNaturalCompletion = ::persistNaturalCompletionFromLifecycle,
            onClearEndOfEpisodeSleep = ::clearEndOfEpisodeSleep,
        )
    }
    private var sleepRestoreInProgress = false

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
    private var playbackSessionConsumedAudioMs: Long = 0L
    private var playbackSessionLastPositionMs: Long? = null
    private var playbackSessionLastPositionSampleMs: Long = 0L

    // Remembers the episode that was paused so a subsequent play() with no explicit source
    // (e.g. from the notification / lock screen / Bluetooth) can be attributed as a resume.
    private var lastPausedEpisodeId: String? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Configure AudioAttributes for Focus and Background Playback
        val audioAttributes =
            androidx.media3.common.AudioAttributes
                .Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build()

        // Dual-cache architecture:
        // - downloadCache: permanent, user-downloaded episodes (NoOpCacheEvictor)
        // - streamCache: temporary streaming buffer for seeking (250MB LRU cap)
        val downloadCache =
            cx.aswin.boxlore.core.data.DownloadRepository
                .getDownloadCache(this)
        val streamCache =
            cx.aswin.boxlore.core.data.DownloadRepository
                .getStreamCache(this)
        val httpDataSourceFactory =
            androidx.media3.datasource.DefaultHttpDataSource
                .Factory()
                .setUserAgent(
                    androidx.media3.common.util.Util
                        .getUserAgent(this, "BoxLore"),
                ).setAllowCrossProtocolRedirects(true)

        // Stream cache: writes streamed data here (auto-evicts at 250MB)
        val streamCacheDataSourceFactory =
            androidx.media3.datasource.cache.CacheDataSource
                .Factory()
                .setCache(streamCache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Download cache: read-only layer that serves user-downloaded episodes without hitting network
        val cacheDataSourceFactory =
            androidx.media3.datasource.cache.CacheDataSource
                .Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(streamCacheDataSourceFactory)
                .setCacheWriteDataSinkFactory(null) // Never write streaming data into the permanent download cache
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory =
            androidx.media3.exoplayer.source
                .DefaultMediaSourceFactory(this)
                .setDataSourceFactory(cacheDataSourceFactory)

        val player =
            ExoPlayer
                .Builder(this)
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
                introOutroController.onSkipPreferencesChanged(player)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.skipEndingMsStream.collectLatest { value ->
                cachedSkipEndingMs = PlaybackSkipPolicy.sanitizeTrim(value)
                introOutroController.onSkipPreferencesChanged(player)
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

        player.addAnalyticsListener(
            object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onPlayerError(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    error: androidx.media3.common.PlaybackException,
                ) {
                    android.util.Log.e("BoxCastPlayer", "onPlayerError: ${error.errorCodeName}", error)
                    val podcastId = playbackSessionPodcastId
                    val episodeId = playbackSessionEpisodeId
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackError(
                        errorCode = error.errorCodeName,
                        errorMessage = error.message ?: "Unknown",
                        podcastId = podcastId,
                        episodeId = episodeId,
                        podcastName = playbackSessionPodcastName,
                        episodeTitle = playbackSessionEpisodeTitle,
                    )
                }

                override fun onAudioSinkError(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    error: Exception,
                ) {
                    android.util.Log.e("BoxCastPlayer", "onAudioSinkError", error)
                }

                override fun onAudioUnderrun(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    bufferSize: Int,
                    bufferSizeMs: Long,
                    elapsedSinceLastFeedMs: Long,
                ) {
                    android.util.Log.e("BoxCastPlayer", "onAudioUnderrun: buffer=$bufferSize, elapsed=$elapsedSinceLastFeedMs")
                }

                override fun onIsPlayingChanged(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    isPlaying: Boolean,
                ) {
                    android.util.Log.d("BoxCastPlayer", "onIsPlayingChanged: $isPlaying")
                }

                override fun onPlaybackStateChanged(
                    eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    state: Int,
                ) {
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
                    reason: Int,
                ) {
                    android.util.Log.d(
                        "BoxCastPlayer",
                        "onPositionDiscontinuity: reason=$reason, from ${oldPosition.positionMs} to ${newPosition.positionMs}",
                    )
                    playbackSessionLastPositionMs = newPosition.positionMs
                    playbackSessionLastPositionSampleMs = android.os.SystemClock.elapsedRealtime()
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        updateHeartbeatsForPosition(newPosition.positionMs, playbackSessionTotalDurationMs)
                        val source =
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                                .consumeSeekSource()
                        val seekResult =
                            introOutroController.onSeekDiscontinuity(
                                newPositionMs = newPosition.positionMs,
                                durationMs = player.duration,
                                source = source,
                            )
                        android.util.Log.d(
                            "BoxCastPlayer",
                            "onPositionDiscontinuity (SEEK): source=$source, reason=$reason, from ${oldPosition.positionMs} to ${newPosition.positionMs}",
                        )
                        val epId = playbackSessionEpisodeId
                        if (!seekResult.isLifecycleSeek && epId != null) {
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackSeeked(
                                podcastId = playbackSessionPodcastId,
                                podcastName = playbackSessionPodcastName,
                                episodeId = epId,
                                episodeTitle = playbackSessionEpisodeTitle,
                                fromPositionSeconds = oldPosition.positionMs / 1000f,
                                toPositionSeconds = newPosition.positionMs / 1000f,
                                totalDurationSeconds = playbackSessionTotalDurationMs / 1000f,
                                seekSource = source,
                            )
                        }
                    }
                }
            },
        )

        // SmartQueue auto-refill: when queue runs low, fetch more episodes
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    handleMediaItemTransition(player, mediaItem, reason)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    if (!playWhenReady) {
                        val pauseReason =
                            when (reason) {
                                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "headphone_disconnected"
                                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "audio_focus_loss_permanent"
                                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "user_voluntary"
                                else -> "user_voluntary"
                            }
                        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                            .setPauseReason(pauseReason)
                        android.util.Log.d(
                            "BoxCastPlayer",
                            "onPlayWhenReadyChanged: playWhenReady=false, reason=$reason, pauseReason=$pauseReason",
                        )
                    }
                }

                override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                    if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                            .setPauseReason("audio_focus_loss_transient")
                        android.util.Log.d("BoxCastPlayer", "onPlaybackSuppressionReasonChanged: transient audio focus loss")
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            introOutroController.onReadyOrPlaying(player)
                        }
                        Player.STATE_ENDED -> introOutroController.onNaturalStateEnded(player)
                        Player.STATE_IDLE ->
                            if (!player.playWhenReady) {
                                introOutroController.reset(null, 0L)
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
                        introOutroController.reset(null, 0L)
                    } else if (!introOutroController.isActiveMediaItem(currentItem)) {
                        introOutroController.onMediaActivated(player, currentItem)
                    }
                }
            },
        )

        // Progress saver + resume-seek + Telemetry
        var progressSaverJob: kotlinx.coroutines.Job? = null
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val currentItem = player.currentMediaItem
                    val episodeId =
                        currentItem
                            ?.mediaId
                            ?.removePrefix(LEARN_PREFIX)
                            ?.removePrefix(EPISODE_PREFIX)
                            ?.removePrefix(QUEUE_PREFIX)

                    if (isPlaying) {
                        // Telemetry: Started playing
                        if (episodeId != null) startPlaybackSession(episodeId, currentItem)
                        activePlaybackStartTimeMs = System.currentTimeMillis()

                        introOutroController.onReadyOrPlaying(player)
                        introOutroController.startOutroMonitor(player)
                        progressSaverJob?.cancel()
                        progressSaverJob =
                            serviceScope.launch {
                                startPlaybackTicker(player)
                            }
                    } else {
                        introOutroController.stopOutroMonitor()
                        // Telemetry: Paused playing
                        // Only end session if explicitly paused, stopped, or transiently suppressed. Ignore buffering/seeking.
                        val shouldEndSession =
                            !player.playWhenReady ||
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
            },
        )

        val intent = Intent()
        intent.component = android.content.ComponentName(packageName, "cx.aswin.boxlore.MainActivity")
        intent.putExtra("EXTRA_OPEN_PLAYER", true) // Notification Click -> Open Player

        val pendingIntent =
            android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val forwardingPlayer =
            object : androidx.media3.common.ForwardingPlayer(player) {
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

                override fun getAvailableCommands(): Player.Commands =
                    super
                        .getAvailableCommands()
                        .buildUpon()
                        .add(Player.COMMAND_SEEK_FORWARD)
                        .add(Player.COMMAND_SEEK_BACK)
                        .build()
            }

        rebuildSeekCommandButtons()

        likeAction =
            androidx.media3.session.CommandButton
                .Builder()
                .setDisplayName(getString(cx.aswin.boxlore.core.data.R.string.auto_like))
                .setIconResId(cx.aswin.boxlore.core.data.R.drawable.ic_auto_like)
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(
                        AutoBrowseContract.COMMAND_TOGGLE_LIKE,
                        Bundle.EMPTY,
                    ),
                ).build()

        addToQueueAction =
            androidx.media3.session.CommandButton
                .Builder()
                .setDisplayName(getString(cx.aswin.boxlore.core.data.R.string.auto_add_queue))
                .setIconResId(cx.aswin.boxlore.core.data.R.drawable.ic_auto_queue_add)
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(
                        AutoBrowseContract.COMMAND_ADD_TO_QUEUE,
                        Bundle.EMPTY,
                    ),
                ).build()

        markCompleteAction =
            androidx.media3.session.CommandButton
                .Builder()
                .setDisplayName(getString(cx.aswin.boxlore.core.data.R.string.auto_mark_complete))
                .setIconResId(cx.aswin.boxlore.core.data.R.drawable.ic_auto_complete)
                .setSessionCommand(
                    androidx.media3.session.SessionCommand(
                        AutoBrowseContract.COMMAND_MARK_COMPLETE,
                        Bundle.EMPTY,
                    ),
                ).build()

        val coilBitmapLoader = CoilBitmapLoader(this, serviceScope)
        val cacheBitmapLoader = androidx.media3.session.CacheBitmapLoader(coilBitmapLoader)

        mediaSession =
            MediaLibrarySession
                .Builder(this, forwardingPlayer, AutoBrowseLibraryCallback(this))
                .setSessionActivity(pendingIntent)
                .setCustomLayout(listOf(seekBackAction, seekForwardAction, markCompleteAction))
                .setCommandButtonsForMediaItems(
                    listOf(likeAction, addToQueueAction, markCompleteAction),
                ).setBitmapLoader(cacheBitmapLoader)
                .build()
        prewarmAutoCollages()
    }

    private fun rebuildSeekCommandButtons() {
        seekBackAction =
            androidx.media3.session.CommandButton
                .Builder()
                .setDisplayName(
                    getString(
                        cx.aswin.boxlore.core.data.R.string.auto_seek_back,
                        cachedSeekBackwardMs / 1_000L,
                    ),
                ).setIconResId(cx.aswin.boxlore.core.data.R.drawable.rounded_replay_24)
                .setSessionCommand(androidx.media3.session.SessionCommand("SEEK_BACK", Bundle.EMPTY))
                .build()
        seekForwardAction =
            androidx.media3.session.CommandButton
                .Builder()
                .setDisplayName(
                    getString(
                        cx.aswin.boxlore.core.data.R.string.auto_seek_forward,
                        cachedSeekForwardMs / 1_000L,
                    ),
                ).setIconResId(cx.aswin.boxlore.core.data.R.drawable.rounded_forward_24)
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

    private fun seekByConfiguredIncrement(
        player: ExoPlayer,
        deltaMs: Long,
        source: String,
    ) {
        val upperBound = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, upperBound)
        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
            .setSeekSource(source)
        player.seekTo(target)
        android.util.Log.d("BoxCastPlayer", "$source to ${target}ms")
    }

    private fun lifecycleEpisodeId(item: MediaItem?): String? =
        item
            ?.mediaId
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
        val previousEpisodeId = introOutroController.activeEpisodeId
        val previousDurationMs = introOutroController.activeDurationMs
        val wasAutoCompleted = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        val wasServiceOwnedNaturalAdvance =
            previousEpisodeId ==
                cx.aswin.boxlore.core.data.PlaybackLifecycleSignals
                    .serviceOwnedNaturalAdvanceEpisodeId

        completePreviousItemTransition(
            previousEpisodeId = previousEpisodeId,
            previousDurationMs = previousDurationMs,
            wasAutoCompleted = wasAutoCompleted,
        )
        if (restoreLifecycleAfterSleepTransition(player, mediaItem)) return
        if (
            wasAutoCompleted &&
            cx.aswin.boxlore.core.data.SleepTimerHolder.sleepAtEndOfEpisode
        ) {
            enforceEndOfEpisodeSleepAfterTransition(player, previousDurationMs)
            return
        }

        introOutroController.onMediaActivated(player, mediaItem)
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
            introOutroController.claimNaturalCompletion(previousEpisodeId, previousDurationMs)
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
        introOutroController.reset(mediaItem, player.currentPosition)
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
        val transitionSource =
            when (reason) {
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

    private fun maybeRefillQueueAfterTransition(
        player: ExoPlayer,
        reason: Int,
    ) {
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        android.util.Log.d("AutoQueue", "onMediaItemTransition: remaining=$remaining, reason=$reason")
        val currentItem = player.currentMediaItem
        val isLearn = currentItem?.mediaId?.startsWith(LEARN_PREFIX) == true
        // Sleep-timer guard: when playback will stop at the end of this episode,
        // refilling would fetch episodes the player is about to abandon.
        val sleepingAtEndOfEpisode =
            cx.aswin.boxlore.core.data.SleepTimerHolder.sleepAtEndOfEpisode

        if (
            !cx.aswin.boxlore.core.data.playback.SmartQueueRefillPolicy.shouldRefill(
                remainingUpcoming = remaining,
                isRefilling = isRefilling,
                mediaItemCount = player.mediaItemCount,
                isLearnEpisode = isLearn,
                sleepingAtEndOfEpisode = sleepingAtEndOfEpisode,
            )
        ) {
            return
        }
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

    private fun enforceEndOfEpisodeSleepAfterTransition(
        player: ExoPlayer,
        completedDurationMs: Long,
    ) {
        clearEndOfEpisodeSleep()
        player.pause()
        val previousIndex = player.currentMediaItemIndex - 1
        if (previousIndex >= 0) {
            sleepRestoreInProgress = true
            introOutroController.markAutomaticSeekSource("transition")
            player.seekTo(
                previousIndex,
                introOutroController.trueEndSeekTarget(completedDurationMs),
            )
        }
    }

    private fun clearEndOfEpisodeSleep() {
        cx.aswin.boxlore.core.data.SleepTimerHolder.activeSleepTimerEndMs = null
        cx.aswin.boxlore.core.data.SleepTimerHolder.sleepAtEndOfEpisode = false
    }

    private fun persistNaturalCompletionFromLifecycle(
        episodeId: String,
        durationMs: Long,
    ): kotlinx.coroutines.Job {
        val fallbackPodcastId = playbackSessionPodcastId
        val fallbackPodcastName = playbackSessionPodcastName
        val fallbackEpisodeTitle = playbackSessionEpisodeTitle
        val fallbackMediaItem =
            exoPlayer
                ?.currentMediaItem
                ?.takeIf { lifecycleEpisodeId(it) == episodeId }
        val resolvedDurationMs =
            durationMs.takeIf { it > 0L }
                ?: playbackSessionTotalDurationMs
        val persistenceJob =
            serviceScope.launch {
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
        return persistenceJob
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
        val resolvedDurationMs =
            durationMs.takeIf { it > 0L }
                ?: existing?.durationMs
                ?: 0L
        val completed =
            if (existing != null) {
                existing.copy(
                    progressMs = 0L,
                    durationMs = resolvedDurationMs,
                    isCompleted = true,
                    isManualCompletion = false,
                    isDirty = true,
                    lastPlayedAt = System.currentTimeMillis(),
                )
            } else {
                val queueItem =
                    runCatching {
                        queueRepository.getQueueItemByEpisodeId(episodeId)
                    }.getOrNull()
                val podcastId =
                    queueItem?.podcastId
                        ?: fallbackPodcastId
                        ?: ""
                val podcast =
                    podcastId.takeIf { it.isNotBlank() }?.let {
                        runCatching { database.podcastDao().getPodcast(it) }.getOrNull()
                    }
                cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
                    episodeId = episodeId,
                    podcastId = podcastId,
                    episodeTitle =
                        queueItem?.title
                            ?: fallbackEpisodeTitle
                            ?: fallbackMediaItem?.mediaMetadata?.title?.toString()
                            ?: "Unknown Episode",
                    episodeImageUrl =
                        queueItem?.imageUrl
                            ?: fallbackMediaItem?.mediaMetadata?.artworkUri?.toString(),
                    podcastImageUrl = queueItem?.podcastImageUrl ?: podcast?.imageUrl,
                    episodeAudioUrl =
                        queueItem?.audioUrl
                            ?: fallbackMediaItem?.localConfiguration?.uri?.toString(),
                    podcastName =
                        queueItem?.podcastTitle
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

    override fun observeManualCompletion(episodeId: String) {
        introOutroController.observeManualCompletion(episodeId)
    }

    private fun prewarmAutoCollages() {
        serviceScope.launch {
            try {
                val history = database.listeningHistoryDao().getRecentHistoryList(300)
                val resumeItems = database.listeningHistoryDao().getResumeItemsList()
                val subscriptions = database.podcastDao().getSubscribedPodcastsList()
                val downloads = database.downloadedEpisodeDao().getCompletedDownloads(8)
                val queue = queueRepository.getQueueSnapshot()
                val historyImages =
                    history.mapNotNull {
                        it.episodeImageUrl ?: it.podcastImageUrl
                    }
                val resumeImages =
                    resumeItems.mapNotNull {
                        it.episodeImageUrl ?: it.podcastImageUrl
                    }
                val subscriptionImages = subscriptions.mapNotNull { it.imageUrl }
                val downloadImages =
                    downloads.mapNotNull {
                        it.episodeImageUrl ?: it.podcastImageUrl
                    }
                val queueImages = queue.mapNotNull { it.imageUrl ?: it.podcastImageUrl }
                val newEpisodeImages =
                    subscriptions.mapNotNull {
                        it.latestEpisode?.imageUrl ?: it.imageUrl
                    }
                var mixtape =
                    cx.aswin.boxlore.core.data.MixtapeEngine.build(
                        subscriptions = subscriptions.map(::toAutoPodcast),
                        history = history,
                        adaptiveRanking =
                            cx.aswin.boxlore.core.data.MixtapeEngine.AdaptiveRanking(
                                scorer = adaptiveCandidateScorer,
                                surface = cx.aswin.boxlore.core.data.ranking.RankingSurface.ANDROID_AUTO,
                            ),
                    )
                if (mixtape.episodes.size < 3) {
                    val recommendations =
                        runCatching {
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
                    mixtape =
                        cx.aswin.boxlore.core.data.MixtapeEngine.build(
                            subscriptions = subscriptions.map(::toAutoPodcast),
                            history = history,
                            recommendations = recommendations,
                            adaptiveRanking =
                                cx.aswin.boxlore.core.data.MixtapeEngine.AdaptiveRanking(
                                    scorer = adaptiveCandidateScorer,
                                    surface = cx.aswin.boxlore.core.data.ranking.RankingSurface.ANDROID_AUTO,
                                ),
                        )
                }
                val mixtapeImages =
                    mixtape.podcasts.mapNotNull { podcast ->
                        podcast.latestEpisode?.let { episode ->
                            episode.imageUrl ?: episode.podcastImageUrl ?: podcast.imageUrl
                        }
                    }
                autoCollageUris =
                    AutoCollageGenerator.generateAllCollages(
                        context = this@BoxLorePlaybackService,
                        folderImages =
                            mapOf(
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
                        folderContentKeys =
                            mapOf(
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

    override fun toAutoPodcast(entity: cx.aswin.boxlore.core.data.database.PodcastEntity) =
        with(entity) {
            cx.aswin.boxlore.core.model.Podcast(
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
        }

    override fun getTimeBasedGenres(hour: Int): List<Pair<String, String>> =
        when (hour) {
            in 5..11 ->
                listOf(
                    "morning_news" to "Top News",
                    "morning_motivation" to "Daily Motivation",
                    "business_insider" to "Business & Tech",
                )
            in 12..16 ->
                listOf(
                    "science_explainer" to "Science & Discovery",
                    "tech_culture" to "Tech & Gadgets",
                    "creative_focus" to "Creative Focus",
                )
            in 17..22 ->
                listOf(
                    "comedy_gold" to "Comedy Gold",
                    "tv_film_buff" to GENRE_TV_FILM,
                    "sports_fan" to "Sports Highlights",
                )
            else ->
                listOf(
                    "true_crime_sleep" to "True Crime & Chill",
                    "history_buff" to "History",
                    "mystery_thriller" to "Mystery & Thrillers",
                )
        }

    private fun startPlaybackSession(
        episodeId: String,
        currentItem: MediaItem?,
        fallbackEntryPoint: String? = null,
    ) {
        if (playbackSessionStartTimeMs > 0 && playbackSessionEpisodeId == episodeId) return

        endPlaybackSession(forceCompleted = false) // Flush any outgoing session

        if (playbackSessionEpisodeId != episodeId) {
            firedHeartbeats.clear()
        }

        playbackSessionStartTimeMs = System.currentTimeMillis()
        playbackSessionBufferingStartTimeMs = 0L
        playbackSessionTotalBufferedTimeMs = 0L
        playbackSessionConsumedAudioMs = 0L
        playbackSessionLastPositionMs = mediaSession?.player?.currentPosition
        playbackSessionLastPositionSampleMs = android.os.SystemClock.elapsedRealtime()
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
        val pendingEntryPoint =
            cx.aswin.boxlore.core.data.analytics.PendingEntryPoint
                .consume()
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
            playbackSessionEntryPoint =
                if (episodeId == lastPausedEpisodeId) {
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

    private suspend fun resolvePodcastFromDb(podcastId: String): Pair<String?, String?> =
        try {
            val podcast = database.podcastDao().getPodcast(podcastId)
            if (podcast != null) {
                val genre =
                    if (!podcast.genre.isNullOrBlank() && podcast.genre != "Podcast") {
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

    private suspend fun resolvePodcastFromHistory(episodeId: String): String? =
        try {
            val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (historyItem != null && !historyItem.podcastName.isNullOrBlank()) {
                historyItem.podcastName
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    private suspend fun resolvePodcastFromNetwork(podcastId: String): String? =
        try {
            val podcast = podcastRepository.getPodcastDetails(podcastId)
            podcast?.title
        } catch (e: Exception) {
            null
        }

    private suspend fun resolvePodcastMetadata(
        podcastId: String,
        episodeId: String,
        currentItem: MediaItem?,
        genre: String?,
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

        val finalPodcastName =
            resolvedPodcastName
                ?: currentItem?.mediaMetadata?.subtitle?.toString()
                ?: currentItem?.mediaMetadata?.artist?.toString()

        return Pair(finalPodcastName, actualGenre)
    }

    private suspend fun enrichPlaybackSession(
        episodeId: String,
        currentItem: MediaItem?,
        genre: String?,
    ) {
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

        val (resolvedName, resolvedGenre) =
            if (podcastId != null) {
                resolvePodcastMetadata(podcastId, episodeId, currentItem, genre)
            } else {
                val finalName =
                    currentItem?.mediaMetadata?.subtitle?.toString()
                        ?: currentItem?.mediaMetadata?.artist?.toString()
                Pair(finalName, genre)
            }
        playbackSessionPodcastName = resolvedName
        playbackSessionPodcastGenre = resolvedGenre

        // Check if repeating
        val history = database.listeningHistoryDao().getHistoryItem(episodeId)
        playbackSessionIsRepeating = history?.isCompleted == true

        var durationMs = currentItem?.mediaMetadata?.extras?.getLong("durationMs", 0L) ?: 0L
        val exoDuration =
            kotlinx.coroutines.withContext(mainDispatcher) {
                mediaSession?.player?.duration ?: 0L
            }
        if (exoDuration > 0) durationMs = exoDuration
        playbackSessionTotalDurationMs = durationMs

        val startPositionMs =
            kotlinx.coroutines.withContext(mainDispatcher) {
                mediaSession?.player?.currentPosition ?: 0L
            }
        kotlinx.coroutines.withContext(mainDispatcher) {
            updateHeartbeatsForPosition(startPositionMs, durationMs)
        }

        val isSubscribed = podcastId?.let { subscriptionRepository.isSubscribed(it) } ?: false

        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackStarted(
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
            entryPointContext = playbackSessionEntryPointContext,
        )
    }

    private fun endPlaybackSession(
        forceCompleted: Boolean = false,
        isTransition: Boolean = false,
    ) {
        val currentEpisodeId = playbackSessionEpisodeId
        if (playbackSessionStartTimeMs > 0 && currentEpisodeId != null) {
            mediaSession?.player?.let(::updateConsumedAudio)
            val durationPlayedMs = System.currentTimeMillis() - playbackSessionStartTimeMs
            val durationPlayedSeconds = durationPlayedMs / 1000f
            val consumedAudioSeconds = playbackSessionConsumedAudioMs / 1000f
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
                    isCompleted =
                        PlaybackSkipPolicy.shouldCompleteFromProgress(
                            positionMs = pos,
                            durationMs = totalDurationMs,
                            effectiveSkipEndingMs =
                                introOutroController.effectiveEndingTrimForCompletion(totalDurationMs),
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
            val currentQueueSize =
                try {
                    mediaSession?.player?.mediaItemCount ?: 0
                } catch (_: Exception) {
                    0
                }

            if (
                isCompleted &&
                introOutroController.markCompletionTelemetryDispatched()
            ) {
                // Instantly dispatch 100% heartbeat if not already fired
                if (!firedHeartbeats.contains("percent_100")) {
                    firedHeartbeats.add("percent_100")
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackHeartbeat(
                        podcastId = currentPodcastId,
                        podcastName = currentPodcastName,
                        episodeId = currentEpisodeId,
                        episodeTitle = currentEpisodeTitle,
                        currentPositionSeconds = totalDurationMs / 1000f,
                        totalDurationSeconds = totalDurationMs / 1000f,
                        heartbeatPercentage = 100,
                        heartbeatType = "percent",
                    )
                }

                // Dedicated playback_completed event
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackCompleted(
                    podcastId = currentPodcastId,
                    podcastName = currentPodcastName,
                    podcastGenre = currentPodcastGenre,
                    episodeId = currentEpisodeId,
                    episodeTitle = currentEpisodeTitle,
                    totalDurationSeconds = totalDurationMs / 1000f,
                    entryPoint = entryPoint,
                    entryPointContext = entryPointContext,
                )

                // Auto-delete completed download if enabled in preferences
                val completedEpId = currentEpisodeId
                if (completedEpId.isNotEmpty()) {
                    serviceScope.launch {
                        try {
                            val shouldDelete = userPreferencesRepository.autoDownloadDeleteCompletedStream.first()
                            if (shouldDelete) {
                                downloadDeps.downloadRepository.removeDownload(completedEpId)
                                android.util.Log.d("BoxLorePlaybackService", "Auto-deleted completed downloaded episode: $completedEpId")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BoxLorePlaybackService", "Failed to auto-delete completed download", e)
                        }
                    }
                }
            } else if (!isCompleted) {
                val pauseReason =
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                        .consumePauseReason()
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackPaused(
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
                    pauseReason = pauseReason,
                )

                // Track skip if it's a transition skip within 30 seconds for an AUTO_FILL episode.
                // Also feed local skip memory so the SmartQueueEngine never re-suggests it
                // and can down-rank the podcast after repeated rejections.
                if (isTransition && consumedAudioSeconds <= 30f && playbackSessionContextType == "AUTO_FILL") {
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackSmartQueueEpisodeSkipped(
                        episodeId = currentEpisodeId,
                        recommendationSource = playbackSessionContextSourceId ?: "unknown",
                        positionInQueue = 0,
                    )
                    try {
                        queueSkipMemory.recordSkip(
                            episodeId = currentEpisodeId,
                            podcastId = currentPodcastId,
                            source = playbackSessionContextSourceId,
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("AutoQueue", "Failed to record skip memory", e)
                    }
                }
            }

            val adaptiveSource =
                when (playbackSessionContextType) {
                    "AUTO_FILL" -> cx.aswin.boxlore.core.data.ranking.CandidateSource.SERVER_RECOMMENDATION
                    cx.aswin.boxlore.core.data.QueueMath.CONTEXT_TYPE_LORE ->
                        cx.aswin.boxlore.core.data.ranking.CandidateSource.CURATED_INTENT
                    else -> null
                }
            val isAdaptiveEarlySkip =
                isTransition &&
                    consumedAudioSeconds <= 30f &&
                    adaptiveSource != null
            serviceScope.launch {
                rankingFeedbackRepository.recordPlayback(
                    target =
                        cx.aswin.boxlore.core.data.ranking.FeedbackTarget(
                            episodeId = currentEpisodeId,
                            podcastId = currentPodcastId.orEmpty(),
                            genre = currentPodcastGenre,
                            source = adaptiveSource,
                        ),
                    listenSeconds = consumedAudioSeconds.toLong().coerceAtLeast(0L),
                    durationSeconds = (totalDurationMs / 1_000L).coerceAtLeast(0L),
                    completed = isCompleted,
                    earlySkip = isAdaptiveEarlySkip,
                )
            }

            // Flush events immediately to prevent losses during backgrounding/shutdown
            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                .flush()

            // Reset
            playbackSessionStartTimeMs = 0L
            playbackSessionBufferingStartTimeMs = 0L
            playbackSessionTotalBufferedTimeMs = 0L
            playbackSessionConsumedAudioMs = 0L
            playbackSessionLastPositionMs = null
            playbackSessionLastPositionSampleMs = 0L
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        endPlaybackSession(forceCompleted = false)
        introOutroController.reset(null, 0L)
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
            if (!player.playWhenReady ||
                player.mediaItemCount == 0 ||
                player.playbackState == Player.STATE_ENDED ||
                player.playbackState == Player.STATE_IDLE
            ) {
                android.util.Log.d(
                    "BoxLorePlaybackService",
                    "onTaskRemoved: player not playing or queue empty, stopping service gracefully",
                )
                endPlaybackSession(forceCompleted = false)
                introOutroController.reset(null, 0L)
                stopSelf()
                super.onTaskRemoved(rootIntent)
            } else {
                android.util.Log.d(
                    "BoxLorePlaybackService",
                    "onTaskRemoved: player is playing, keeping service in foreground and bypassing super.onTaskRemoved to prevent notification from disappearing",
                )
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
    override suspend fun refillQueue(player: ExoPlayer) {
        smartQueueRefillCoordinator.refillQueue(player)
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
            updateConsumedAudio(player)

            // Continuous Service-Level Sleep Timer Enforcement (fires even when locked in Doze mode)
            val sleepEnd = cx.aswin.boxlore.core.data.SleepTimerHolder.activeSleepTimerEndMs
            if (sleepEnd != null && System.currentTimeMillis() >= sleepEnd) {
                cx.aswin.boxlore.core.data.SleepTimerHolder.activeSleepTimerEndMs = null
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

    private fun updateConsumedAudio(player: Player) {
        val now = android.os.SystemClock.elapsedRealtime()
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val previousPosition = playbackSessionLastPositionMs
        val previousSample = playbackSessionLastPositionSampleMs
        if (player.isPlaying && previousPosition != null && previousSample > 0L) {
            val positionAdvance = currentPosition - previousPosition
            val elapsed = (now - previousSample).coerceAtLeast(0L)
            val maximumNaturalAdvance =
                (elapsed * player.playbackParameters.speed * 1.5f).toLong() + 1_000L
            if (positionAdvance in 0..maximumNaturalAdvance) {
                playbackSessionConsumedAudioMs += positionAdvance
            }
        }
        playbackSessionLastPositionMs = currentPosition
        playbackSessionLastPositionSampleMs = now
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

    private fun checkPercentHeartbeats(
        episodeId: String,
        currentPosSec: Float,
        durationSec: Float,
        percent: Float,
    ) {
        val percentMilestones = listOf(10, 25, 50, 75, 90)
        for (milestone in percentMilestones) {
            if (percent >= milestone && !firedHeartbeats.contains("percent_$milestone")) {
                firedHeartbeats.add("percent_$milestone")
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = playbackSessionPodcastId,
                    podcastName = playbackSessionPodcastName,
                    episodeId = episodeId,
                    episodeTitle = playbackSessionEpisodeTitle,
                    currentPositionSeconds = currentPosSec,
                    totalDurationSeconds = durationSec,
                    heartbeatPercentage = milestone,
                    heartbeatType = "percent",
                )
            }
        }
    }

    private fun checkIntervalHeartbeats(
        episodeId: String,
        currentPosSec: Float,
        durationSec: Float,
    ) {
        val fiveMinuteIntervals = (currentPosSec / 300f).toInt()
        if (fiveMinuteIntervals > 0) {
            val milestoneKey = "time_${fiveMinuteIntervals * 5}m"
            if (!firedHeartbeats.contains(milestoneKey)) {
                firedHeartbeats.add(milestoneKey)
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = playbackSessionPodcastId,
                    podcastName = playbackSessionPodcastName,
                    episodeId = episodeId,
                    episodeTitle = playbackSessionEpisodeTitle,
                    currentPositionSeconds = currentPosSec,
                    totalDurationSeconds = durationSec,
                    heartbeatPercentage = 0,
                    heartbeatType = "interval",
                )
            }
        }
    }

    private fun updateHeartbeatsForPosition(
        positionMs: Long,
        durationMs: Long,
    ) {
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
        if (introOutroController.isEffectiveEndLatched) return
        try {
            val currentItem = kotlinx.coroutines.withContext(mainDispatcher) { player.currentMediaItem }
            val positionMs = kotlinx.coroutines.withContext(mainDispatcher) { player.currentPosition }
            val durationMs = kotlinx.coroutines.withContext(mainDispatcher) { player.duration }
            val episodeId =
                currentItem
                    ?.mediaId
                    ?.removePrefix(LEARN_PREFIX)
                    ?.removePrefix(EPISODE_PREFIX)
                    ?.removePrefix(QUEUE_PREFIX) ?: return

            val existing = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (existing != null && positionMs > 0) {
                val hasBeenPlayingFor10s =
                    activePlaybackStartTimeMs > 0 &&
                        (System.currentTimeMillis() - activePlaybackStartTimeMs >= 10_000)
                val lastPlayed = if (hasBeenPlayingFor10s) System.currentTimeMillis() else existing.lastPlayedAt

                val isCompleted = checkIsPlaybackCompleted(positionMs, durationMs)

                if (isCompleted) {
                    val updated =
                        existing.copy(
                            isCompleted = true,
                            progressMs = 0L,
                            durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                            lastPlayedAt = lastPlayed,
                            isDirty = true,
                        )
                    database.listeningHistoryDao().upsert(updated)
                    android.util.Log.d("AutoProgress", "Saved completed: $episodeId")
                } else {
                    database.listeningHistoryDao().updateProgress(
                        episodeId = episodeId,
                        progressMs = positionMs,
                        durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                        lastPlayedAt = lastPlayed,
                    )
                    android.util.Log.d("AutoProgress", "Saved progress: $episodeId @ ${positionMs / 1000}s / ${durationMs / 1000}s")
                }

                kotlinx.coroutines.withContext(mainDispatcher) {
                    try {
                        mediaSession?.notifyChildrenChanged("home_continue_listening", 0, null)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AutoProgress", "Error saving progress once", e)
        }
    }

    private fun checkIsPlaybackCompleted(
        positionMs: Long,
        durationMs: Long,
    ): Boolean =
        PlaybackSkipPolicy.shouldCompleteFromProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            effectiveSkipEndingMs = introOutroController.effectiveEndingTrimForCompletion(durationMs),
        )

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
        val episodeId =
            currentItem
                ?.mediaId
                ?.removePrefix(LEARN_PREFIX)
                ?.removePrefix(EPISODE_PREFIX)
                ?.removePrefix(QUEUE_PREFIX)
        if (episodeId != null) {
            observeManualCompletion(episodeId)
            serviceScope.launch {
                try {
                    val existing = database.listeningHistoryDao().getHistoryItem(episodeId)
                    if (existing != null) {
                        val updated =
                            existing.copy(
                                isCompleted = true,
                                progressMs = 0L,
                                durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                                lastPlayedAt = System.currentTimeMillis(),
                                isDirty = true,
                            )
                        database.listeningHistoryDao().upsert(updated)
                        android.util.Log.d("BoxLorePlaybackService", "Marked current episode completed: $episodeId")

                        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackPlaybackCompleted(
                            podcastId = playbackSessionPodcastId,
                            podcastName = playbackSessionPodcastName,
                            podcastGenre = playbackSessionPodcastGenre,
                            episodeId = episodeId,
                            episodeTitle = playbackSessionEpisodeTitle,
                            totalDurationSeconds = (if (durationMs > 0) durationMs else existing.durationMs) / 1000f,
                            entryPoint = playbackSessionEntryPoint,
                            entryPointContext = playbackSessionEntryPointContext,
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
            val skipBehavior =
                try {
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
                    introOutroController.reset(null, 0L)
                }
            }
        }
    }

    /**
     * Marks the current playing episode as completed in the database and skips to the next item (Legacy/Custom Command Callback).
     */
    override fun markCurrentEpisodeCompletedAndSkip(session: MediaSession) {
        markCurrentEpisodeCompleted()
        serviceScope.launch {
            val player = exoPlayer ?: return@launch
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                player.stop()
                introOutroController.reset(null, 0L)
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
}
