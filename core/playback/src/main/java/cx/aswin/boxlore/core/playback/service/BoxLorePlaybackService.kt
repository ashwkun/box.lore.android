package cx.aswin.boxlore.core.playback.service

import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import cx.aswin.boxlore.core.playback.PlaybackIntroOutroController
import cx.aswin.boxlore.core.playback.PlaybackProgressCoordinator
import cx.aswin.boxlore.core.playback.PlaybackSkipPolicy
import cx.aswin.boxlore.core.playback.PlaybackTelemetrySession
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseLibraryCallback
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseLibraryHost
import cx.aswin.boxlore.core.playback.service.auto.stripEpisodePrefix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val LEARN_PREFIX = AutoBrowseContract.LEARN_PREFIX
private const val GENRE_TV_FILM = AutoBrowseContract.GENRE_TV_FILM

open class BoxLorePlaybackService :
    MediaLibraryService(),
    AutoBrowseLibraryHost {
    override fun asContext(): android.content.Context = this

    override var mediaSession: MediaLibrarySession? = null
        protected set
    private var exoPlayer: ExoPlayer? = null
    override lateinit var seekBackAction: androidx.media3.session.CommandButton
        protected set
    override lateinit var seekForwardAction: androidx.media3.session.CommandButton
        protected set
    private lateinit var likeAction: androidx.media3.session.CommandButton
    private lateinit var addToQueueAction: androidx.media3.session.CommandButton
    override lateinit var markCompleteAction: androidx.media3.session.CommandButton
        protected set

    @Volatile
    override var autoCollageUris: Map<String, android.net.Uri> = emptyMap()
        protected set

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
        cx.aswin.boxlore.core.playback.QueueSkipMemory
            .fromContext(this)
    }
    private val rankingFeedbackRepository by lazy { sharedDeps.rankingFeedbackRepository }
    override val adaptiveCandidateScorer by lazy { sharedDeps.adaptiveCandidateScorer }
    override val smartQueueSources by lazy {
        cx.aswin.boxlore.core.playback.DefaultSmartQueueSources(
            context = this,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }
    private val smartQueueEngine by lazy {
        cx.aswin.boxlore.core.playback.DefaultSmartQueueEngine(
            sources = smartQueueSources,
            skipMemory = queueSkipMemory,
            adaptiveScorer = adaptiveCandidateScorer,
        )
    }
    override val queueRepository by lazy {
        cx.aswin.boxlore.core.playback.QueueRepository(database, podcastRepository)
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
            mediaIdPrefixStripper = cx.aswin.boxlore.core.playback.SmartQueueRefillPolicy::stripQueuePrefixes,
        )
    }

    @Volatile private var cachedSkipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS

    @Volatile private var cachedSkipEndingMs = PlaybackSkipPolicy.DEFAULT_SKIP_ENDING_MS

    @Volatile private var cachedSeekBackwardMs = PlaybackSkipPolicy.DEFAULT_SEEK_BACKWARD_MS

    @Volatile private var cachedSeekForwardMs = PlaybackSkipPolicy.DEFAULT_SEEK_FORWARD_MS

    // Breaks circular lazy init between telemetry ↔ intro/outro controllers.
    private var introOutroControllerRef: PlaybackIntroOutroController? = null

    private val telemetrySession by lazy {
        PlaybackTelemetrySession(
            scope = serviceScope,
            mainDispatcher = mainDispatcher,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
            queueSkipMemory = queueSkipMemory,
            userPreferencesRepository = userPreferencesRepository,
            findPodcastIdForEpisode = ::findPodcastIdForEpisode,
            effectiveSkipEndingMs = { durationMs ->
                introOutroControllerRef!!.effectiveEndingTrimForCompletion(durationMs)
            },
            markCompletionTelemetryDispatched = {
                introOutroControllerRef!!.markCompletionTelemetryDispatched()
            },
            playerProvider = { mediaSession?.player },
            removeCompletedDownload = { episodeId ->
                downloadDeps.downloadRepository.removeDownload(episodeId)
            },
        )
    }

    private val introOutroController by lazy {
        PlaybackIntroOutroController(
            scope = serviceScope,
            database = database,
            globalSkipBeginningMs = { cachedSkipBeginningMs },
            globalSkipEndingMs = { cachedSkipEndingMs },
            lifecycleEpisodeId = ::lifecycleEpisodeId,
            findPodcastIdForEpisode = ::findPodcastIdForEpisode,
            onActiveDurationResolved = { episodeId, durationMs ->
                if (episodeId == telemetrySession.episodeId) {
                    telemetrySession.totalDurationMs = durationMs
                }
            },
            onNaturalCompletion = ::persistNaturalCompletionFromLifecycle,
            onClearEndOfEpisodeSleep = ::clearEndOfEpisodeSleep,
        ).also { introOutroControllerRef = it }
    }

    private val progressCoordinator by lazy {
        PlaybackProgressCoordinator(
            mainDispatcher = mainDispatcher,
            database = database,
            mediaSessionProvider = { mediaSession },
            isEffectiveEndLatched = { introOutroController.isEffectiveEndLatched },
            effectiveSkipEndingMs = { durationMs ->
                introOutroController.effectiveEndingTrimForCompletion(durationMs)
            },
            updateConsumedAudio = { player -> telemetrySession.updateConsumedAudio(player) },
            dispatchHeartbeatTelemetry = { player -> telemetrySession.dispatchHeartbeatTelemetry(player) },
        )
    }

    private val playerFactory by lazy { PlaybackServicePlayerFactory(this, serviceScope) }

    private val autoCollagePrewarmer by lazy {
        AutoCollagePrewarmer(
            context = this,
            database = database,
            queueRepository = queueRepository,
            smartQueueSources = smartQueueSources,
            adaptiveCandidateScorer = adaptiveCandidateScorer,
            toAutoPodcast = ::toAutoPodcast,
            mediaSessionProvider = { mediaSession },
            onCollagesReady = { autoCollageUris = it },
        )
    }

    private var sleepRestoreInProgress = false

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val player = playerFactory.createExoPlayer()
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
                    telemetrySession.trackPlayerError(error)
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
                        telemetrySession.onBufferingStarted()
                    } else if (state == Player.STATE_READY) {
                        telemetrySession.onBufferingEnded()
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
                    telemetrySession.noteSeekPosition(newPosition.positionMs)
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        telemetrySession.updateHeartbeatsForPosition(
                            newPosition.positionMs,
                            telemetrySession.totalDurationMs,
                        )
                        val source =
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper
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
                        val epId = telemetrySession.episodeId
                        if (!seekResult.isLifecycleSeek && epId != null) {
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPlaybackSeeked(
                                podcastId = telemetrySession.podcastId,
                                podcastName = telemetrySession.podcastName,
                                episodeId = epId,
                                episodeTitle = telemetrySession.episodeTitle,
                                fromPositionSeconds = oldPosition.positionMs / 1000f,
                                toPositionSeconds = newPosition.positionMs / 1000f,
                                totalDurationSeconds = telemetrySession.totalDurationMs / 1000f,
                                seekSource = source,
                                entryPoint = telemetrySession.entryPoint,
                            )
                        }
                    }
                }
            },
        )

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
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper
                            .setPauseReason(pauseReason)
                        android.util.Log.d(
                            "BoxCastPlayer",
                            "onPlayWhenReadyChanged: playWhenReady=false, reason=$reason, pauseReason=$pauseReason",
                        )
                    }
                }

                override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                    if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper
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

        var progressSaverJob: kotlinx.coroutines.Job? = null
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val currentItem = player.currentMediaItem
                    val episodeId = currentItem?.mediaId?.stripEpisodePrefix()

                    if (isPlaying) {
                        if (episodeId != null) telemetrySession.start(episodeId, currentItem)
                        progressCoordinator.activePlaybackStartTimeMs = System.currentTimeMillis()

                        introOutroController.onReadyOrPlaying(player)
                        introOutroController.startOutroMonitor(player)
                        progressSaverJob?.cancel()
                        progressSaverJob =
                            serviceScope.launch {
                                progressCoordinator.startPlaybackTicker(player)
                            }
                    } else {
                        introOutroController.stopOutroMonitor()
                        val shouldEndSession =
                            !player.playWhenReady ||
                                player.playbackState == Player.STATE_ENDED ||
                                player.playbackState == Player.STATE_IDLE ||
                                player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE

                        if (shouldEndSession) {
                            telemetrySession.lastPausedEpisodeId = episodeId
                            telemetrySession.end(forceCompleted = false)
                        }

                        progressSaverJob?.cancel()
                        progressSaverJob = null
                        serviceScope.launch {
                            progressCoordinator.saveProgressOnce(player)
                            progressCoordinator.activePlaybackStartTimeMs = 0L
                        }
                    }
                }
            },
        )

        val built =
            playerFactory.assembleSession(
                service = this,
                player = player,
                seekForwardMs = { cachedSeekForwardMs },
                seekBackMs = { cachedSeekBackwardMs },
                onSeekByConfiguredIncrement = ::seekByConfiguredIncrement,
                onSkipNext = ::handleSkipNext,
                callback = AutoBrowseLibraryCallback(this),
                seekBackwardMs = cachedSeekBackwardMs,
                seekForwardMsValue = cachedSeekForwardMs,
            )
        seekBackAction = built.seekButtons.seekBack
        seekForwardAction = built.seekButtons.seekForward
        likeAction = built.customActions.like
        addToQueueAction = built.customActions.addToQueue
        markCompleteAction = built.customActions.markComplete
        mediaSession = built.mediaSession
        serviceScope.launch { autoCollagePrewarmer.prewarm() }
    }

    private fun rebuildSeekCommandButtons() {
        val buttons = playerFactory.buildSeekButtons(cachedSeekBackwardMs, cachedSeekForwardMs)
        seekBackAction = buttons.seekBack
        seekForwardAction = buttons.seekForward
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
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .setSeekSource(source)
        player.seekTo(target)
        android.util.Log.d("BoxCastPlayer", "$source to ${target}ms")
    }

    private fun lifecycleEpisodeId(item: MediaItem?): String? = item?.mediaId?.stripEpisodePrefix()

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
                cx.aswin.boxlore.core.playback.PlaybackLifecycleSignals
                    .serviceOwnedNaturalAdvanceEpisodeId

        completePreviousItemTransition(
            previousEpisodeId = previousEpisodeId,
            previousDurationMs = previousDurationMs,
            wasAutoCompleted = wasAutoCompleted,
        )
        if (restoreLifecycleAfterSleepTransition(player, mediaItem)) return
        if (
            wasAutoCompleted &&
            cx.aswin.boxlore.core.playback.SleepTimerHolder.sleepAtEndOfEpisode
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
            telemetrySession.end(forceCompleted = false, isTransition = true)
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
            progressCoordinator.activePlaybackStartTimeMs = 0L
            return
        }
        val episodeId = lifecycleEpisodeId(mediaItem)
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
        if (episodeId != null) telemetrySession.start(episodeId, mediaItem, transitionSource)
        progressCoordinator.activePlaybackStartTimeMs = System.currentTimeMillis()
    }

    private fun maybeRefillQueueAfterTransition(
        player: ExoPlayer,
        reason: Int,
    ) {
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        android.util.Log.d("AutoQueue", "onMediaItemTransition: remaining=$remaining, reason=$reason")
        val currentItem = player.currentMediaItem
        val isLearn = currentItem?.mediaId?.startsWith(LEARN_PREFIX) == true
        val sleepingAtEndOfEpisode =
            cx.aswin.boxlore.core.playback.SleepTimerHolder.sleepAtEndOfEpisode

        if (
            !cx.aswin.boxlore.core.playback.SmartQueueRefillPolicy.shouldRefill(
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
        cx.aswin.boxlore.core.playback.SleepTimerHolder.activeSleepTimerEndMs = null
        cx.aswin.boxlore.core.playback.SleepTimerHolder.sleepAtEndOfEpisode = false
    }

    private fun persistNaturalCompletionFromLifecycle(
        episodeId: String,
        durationMs: Long,
    ): kotlinx.coroutines.Job {
        val fallbackPodcastId = telemetrySession.podcastId
        val fallbackPodcastName = telemetrySession.podcastName
        val fallbackEpisodeTitle = telemetrySession.episodeTitle
        val fallbackMediaItem =
            exoPlayer
                ?.currentMediaItem
                ?.takeIf { lifecycleEpisodeId(it) == episodeId }
        val resolvedDurationMs =
            durationMs.takeIf { it > 0L }
                ?: telemetrySession.totalDurationMs
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
        telemetrySession.end(forceCompleted = true, isTransition = false)
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
                cx.aswin.boxlore.core.database.ListeningHistoryEntity(
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

    override fun toAutoPodcast(entity: cx.aswin.boxlore.core.database.PodcastEntity) =
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        telemetrySession.end(forceCompleted = false)
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
                telemetrySession.end(forceCompleted = false)
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

    private suspend fun findPodcastIdForEpisode(episodeId: String): String? {
        val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
        historyItem?.podcastId?.takeIf { it.isNotBlank() }?.let { return it }

        val queueItem = database.queueDao().getQueueItemByEpisodeId(episodeId)
        queueItem?.podcastId?.takeIf { it.isNotBlank() }?.let { return it }

        val episode = podcastRepository.getEpisode(episodeId)
        return episode?.podcastId
    }

    private fun markCurrentEpisodeCompleted() {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem
        val durationMs = player.duration
        val episodeId = currentItem?.mediaId?.stripEpisodePrefix()
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

                        telemetrySession.trackManualCompletion(
                            episodeId = episodeId,
                            totalDurationSeconds =
                                (if (durationMs > 0) durationMs else existing.durationMs) / 1000f,
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BoxLorePlaybackService", "Failed to mark current episode completed", e)
                }
            }
        }
    }

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
}
