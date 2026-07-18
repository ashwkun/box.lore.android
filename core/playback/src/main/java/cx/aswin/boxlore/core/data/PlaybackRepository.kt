package cx.aswin.boxlore.core.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import cx.aswin.boxlore.core.data.playback.PlaybackChaptersTranscriptController
import cx.aswin.boxlore.core.data.playback.PlaybackHistoryStore
import cx.aswin.boxlore.core.data.playback.PlaybackMediaControllerBridge
import cx.aswin.boxlore.core.data.playback.PlaybackMediaControllerHandle
import cx.aswin.boxlore.core.data.playback.PlaybackQueueCoordinator
import cx.aswin.boxlore.core.data.playback.PlaybackSkipPolicy
import cx.aswin.boxlore.core.data.playback.PlaybackTransportHelper
import cx.aswin.boxlore.core.data.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.data.service.BoxLorePlaybackService
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val enclosureType: String? = null,
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
    val seekBackwardMs: Long = PlaybackSkipPolicy.DEFAULT_SEEK_BACKWARD_MS,
    val seekForwardMs: Long = PlaybackSkipPolicy.DEFAULT_SEEK_FORWARD_MS,
    val sleepTimerEnd: Long? = null,
    val sleepAtEndOfEpisode: Boolean = false, // Dynamic mode: sleep when episode ends
    val queue: List<Episode> = emptyList(),
    val isLiked: Boolean = false,
    val isCompleted: Boolean = false,
    val showLateNightNudge: Boolean = false,
    val currentChapters: List<cx.aswin.boxlore.core.model.Chapter> = emptyList(),
    val isChaptersLoading: Boolean = false,
    val isChaptersNative: Boolean = false,
    val currentTranscript: List<TranscriptSegment> = emptyList(),
    val autoTranscriptState: AutoTranscriptState = AutoTranscriptState.NONE,
    val autoChaptersState: AutoTranscriptState = AutoTranscriptState.NONE,
    val autoTranscriptLimitLeft: Int? = null,
)

object SleepTimerHolder {
    @Volatile var activeSleepTimerEndMs: Long? = null

    @Volatile var sleepAtEndOfEpisode: Boolean = false
}

object PlaybackLifecycleSignals {
    @Volatile var serviceOwnedNaturalAdvanceEpisodeId: String? = null

    @Volatile var effectiveSkipEndingMs: Long? = null
}

class PlaybackRepository(
    private val context: Context,
    private val listeningHistoryDao: cx.aswin.boxlore.core.data.database.ListeningHistoryDao,
    private val queueRepository: cx.aswin.boxlore.core.data.QueueRepository,
    private val podcastRepository: PodcastRepository,
    private val rankingFeedbackRepository: RankingFeedbackRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ListeningHistoryBackupPort {
    /** Nested alias so existing `PlaybackRepository.RemovedQueueItem` call sites keep compiling. */
    typealias RemovedQueueItem = cx.aswin.boxlore.core.data.playback.RemovedQueueItem

    private val mediaHandle = PlaybackMediaControllerHandle()
    val controller: MediaController? get() = mediaHandle.controller

    private val _playerState = kotlinx.coroutines.flow.MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()

    // Preferences for session state
    private val prefs = context.getSharedPreferences("boxcast_player", Context.MODE_PRIVATE)
    private val KEY_PLAYER_DISMISSED = "player_dismissed"
    private val KEY_LAST_SLEEP_PROMPT_WINDOW_ID = "last_sleep_prompt_window_id"
    private val KEY_DEBUG_SKIP_SLEEP_WINDOW = "debug_skip_sleep_window"

    private var currentSkipBehavior: String = "just_skip"

    @Volatile private var currentSkipEndingMs: Long = 0L

    fun getOrCreateDeviceUuid(): String {
        val key = "device_uuid"
        var uuid = prefs.getString(key, null)
        if (uuid == null) {
            uuid =
                java.util.UUID
                    .randomUUID()
                    .toString()
            prefs.edit().putString(key, uuid).apply()
        }
        android.util.Log.d("BoxCastDeviceUuid", "Your physical Device UUID is: $uuid")
        return uuid
    }

    // Scope for progress updates
    private val repositoryScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob(),
        )
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val QUEUE_MAX_SIZE = 50

    // Local memory of rejected auto-fill suggestions (feeds the SmartQueueEngine).
    private val queueSkipMemory = QueueSkipMemory.fromContext(context)

    private val historyStore =
        PlaybackHistoryStore(
            context = context,
            scope = repositoryScope,
            playerState = playerState,
            playerStateFlow = _playerState,
            listeningHistoryDao = listeningHistoryDao,
            podcastRepository = podcastRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
        )

    private val chaptersController =
        PlaybackChaptersTranscriptController(
            scope = repositoryScope,
            playerState = playerState,
            playerStateFlow = _playerState,
            podcastRepository = podcastRepository,
            deviceUuid = ::getOrCreateDeviceUuid,
        )

    private val queueCoordinator =
        PlaybackQueueCoordinator(
            scope = repositoryScope,
            playerStateFlow = _playerState,
            mediaHandle = mediaHandle,
            queueRepository = queueRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
            queueSkipMemory = queueSkipMemory,
            prefs = prefs,
            playerDismissedKey = KEY_PLAYER_DISMISSED,
            queueMaxSize = QUEUE_MAX_SIZE,
            checkSavedProgress = { startEpisodeId, initialPositionMs, entryPoint ->
                checkSavedProgress(startEpisodeId, initialPositionMs, entryPoint)
            },
            onPlaybackStarted = ::onPlaybackStarted,
            storePendingEntryPoint = ::storePendingEntryPoint,
            saveCurrentState = { updateLastPlayedAt -> saveCurrentState(updateLastPlayedAt) },
            stopProgressTicker = ::stopProgressTicker,
        )

    private val transportHelper =
        PlaybackTransportHelper(
            scope = repositoryScope,
            playerStateFlow = _playerState,
            mediaHandle = mediaHandle,
            listeningHistoryDao = listeningHistoryDao,
            storePendingEntryPoint = ::storePendingEntryPoint,
            playQueue = { episodes, podcast, startIndex, entryPoint, initialPositionMs, sourceContext ->
                queueCoordinator.playQueue(
                    episodes,
                    podcast,
                    startIndex,
                    entryPoint,
                    initialPositionMs,
                    sourceContext,
                )
            },
        )

    init {
        getOrCreateDeviceUuid()
        initializeMediaController()
        historyStore.monitorLikeState()
        chaptersController.monitorChaptersAndTranscripts()
        repositoryScope.launch {
            userPreferencesRepository.skipBehaviorStream.collect {
                currentSkipBehavior = it
            }
        }
        repositoryScope.launch {
            userPreferencesRepository.skipEndingMsStream.collect {
                currentSkipEndingMs = it.coerceAtLeast(0L)
            }
        }
        repositoryScope.launch {
            userPreferencesRepository.seekBackwardMsStream.collect { value ->
                _playerState.value =
                    _playerState.value.copy(
                        seekBackwardMs = value.coerceAtLeast(1_000L),
                    )
            }
        }
        repositoryScope.launch {
            userPreferencesRepository.seekForwardMsStream.collect { value ->
                _playerState.value =
                    _playerState.value.copy(
                        seekForwardMs = value.coerceAtLeast(1_000L),
                    )
            }
        }
    }

    fun generateAutoTranscript() = chaptersController.generateAutoTranscript()

    fun generateAutoChapters() = chaptersController.generateAutoChapters()

    private fun initializeMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, BoxLorePlaybackService::class.java))
        mediaHandle.future = MediaController.Builder(context, sessionToken).buildAsync()
        mediaHandle.future?.addListener({
            mediaHandle.controller = mediaHandle.future?.get()
            // Restore the persisted playback speed so it survives app restarts.
            repositoryScope.launch {
                val savedSpeed = userPreferencesRepository.playbackSpeedStream.first()
                if (savedSpeed != 1.0f && mediaHandle.controller?.playbackParameters?.speed != savedSpeed) {
                    mediaHandle.controller?.playbackParameters = PlaybackParameters(savedSpeed)
                    _playerState.value = _playerState.value.copy(playbackSpeed = savedSpeed)
                }
            }
            mediaHandle.controller?.addListener(
                PlaybackMediaControllerBridge(
                    context = context,
                    scope = repositoryScope,
                    playerStateFlow = _playerState,
                    mediaHandle = mediaHandle,
                    queueRepository = queueRepository,
                    currentSkipBehavior = { currentSkipBehavior },
                    activePlaybackStartTimeMs = { activePlaybackStartTimeMs },
                    setActivePlaybackStartTimeMs = { activePlaybackStartTimeMs = it },
                    onPlaybackStarted = ::onPlaybackStarted,
                    startProgressTicker = ::startProgressTicker,
                    stopProgressTicker = ::stopProgressTicker,
                    saveCurrentState = { updateLastPlayedAt -> saveCurrentState(updateLastPlayedAt) },
                    cancelSleepTimer = { sleepTimerJob?.cancel() },
                    syncQueueToDb = { queueCoordinator.syncQueueToDb() },
                    reconcileQueueWithController = { queueCoordinator.reconcileQueueWithController() },
                    markEpisodeAsCompleted = { episode, podcast ->
                        historyStore.markEpisodeAsCompleted(episode, podcast)
                    },
                    findPodcastIdForEpisode = { historyStore.findPodcastIdForEpisode(it) },
                ),
            )

            // Sync state from MediaController (handles app coming back from background)
            syncStateFromMediaController()
        }, MoreExecutors.directExecutor())
    }

    /**
     * Sync playback state from the MediaController.
     * Called when MediaController connects (including when app comes back from background).
     */
    private fun syncStateFromMediaController() {
        val controller = mediaHandle.controller ?: return

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
                    var episode =
                        Episode(
                            id = lastSession.episodeId,
                            title = lastSession.episodeTitle,
                            description = lastSession.episodeDescription ?: "",
                            audioUrl = lastSession.episodeAudioUrl ?: "",
                            imageUrl = lastSession.episodeImageUrl,
                            duration = (lastSession.durationMs / 1000).toInt(),
                            publishedDate = 0L,
                            enclosureType = lastSession.enclosureType,
                        )
                    val podcast =
                        Podcast(
                            id = lastSession.podcastId,
                            title = lastSession.podcastName,
                            artist = "",
                            imageUrl = lastSession.podcastImageUrl ?: "",
                            description = null,
                            genre = "Podcast",
                        )
                    // Enrich with P2.0 data from queue if available
                    val currentQueue = _playerState.value.queue
                    val queueEp = currentQueue.find { it.id == episode.id }
                    if (queueEp != null) {
                        episode =
                            episode.copy(
                                chaptersUrl = queueEp.chaptersUrl,
                                transcriptUrl = queueEp.transcriptUrl,
                                persons = queueEp.persons,
                                transcripts = queueEp.transcripts,
                            )
                    }
                    _playerState.value =
                        PlayerState(
                            currentEpisode = episode,
                            currentPodcast = podcast,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            position = currentPosition,
                            bufferedPosition = bufferedPosition,
                            duration = if (duration > 0) duration else lastSession.durationMs,
                            playbackSpeed = controller.playbackParameters.speed,
                            queue = _playerState.value.queue, // Preserve queue
                            isLiked = lastSession.isLiked,
                        )
                    if (isPlaying) startProgressTicker()
                }
            }
        } else {
            // Just sync playback state
            _playerState.value =
                _playerState.value.copy(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    position = if (currentPosition > 0) currentPosition else _playerState.value.position,
                    bufferedPosition = bufferedPosition,
                    duration = if (duration > 0) duration else _playerState.value.duration,
                    playbackSpeed = controller.playbackParameters.speed,
                )
            if (isPlaying) startProgressTicker()
        }
    }

    private var lastProgressSaveMs = 0L
    private var activePlaybackStartTimeMs = 0L

    private fun startProgressTicker() {
        stopProgressTicker()
        lastProgressSaveMs = System.currentTimeMillis() // Reset so first ticker save is 10s from now
        progressJob =
            repositoryScope.launch {
                while (true) {
                    mediaHandle.controller?.let { controller ->
                        if (controller.isPlaying || controller.isLoading) {
                            val currentPos = controller.currentPosition
                            val bufferedPos = controller.bufferedPosition
                            val currentDur = controller.duration.coerceAtLeast(0)

                            _playerState.value =
                                _playerState.value.copy(
                                    position = currentPos,
                                    bufferedPosition = bufferedPos,
                                    duration = currentDur,
                                )

                            // Save progress every 10 seconds (deterministic)
                            val now = System.currentTimeMillis()
                            if (now - lastProgressSaveMs > 10_000) {
                                val hasBeenPlayingFor10s =
                                    activePlaybackStartTimeMs > 0 &&
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

        // While an effective ending trim is active the service is the sole completion owner.
        // In particular, do not let the legacy 95%/long-episode heuristics complete an item
        // before the service observes the configured boundary.
        val isCompletedNow =
            PlaybackSkipPolicy.shouldCompleteFromProgress(
                positionMs = state.position,
                durationMs = state.duration,
                effectiveSkipEndingMs =
                    PlaybackLifecycleSignals.effectiveSkipEndingMs ?: currentSkipEndingMs,
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
            enclosureType = episode.enclosureType,
            episodeDescription = episode.description,
        )
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun shouldResetPlaybackForMixtape(
        savedProgressMs: Long,
        durationMs: Long,
        entryPoint: PlaybackEntryPoint,
    ): Boolean =
        cx.aswin.boxlore.core.data.playback.MixtapeResumePolicy.shouldResetPlayback(
            savedProgressMs = savedProgressMs,
            durationMs = durationMs,
            entryPoint = entryPoint,
        )

    private suspend fun checkSavedProgress(
        startEpisodeId: String?,
        initialPositionMs: Long?,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    ): Pair<Long, Boolean> {
        var initialLikeState = false
        var savedProgressMs = 0L
        var isCompleted = false
        var resetRequested = false
        if (startEpisodeId != null) {
            val saved = listeningHistoryDao.getHistoryItem(startEpisodeId)
            if (saved != null) {
                savedProgressMs = saved.progressMs
                isCompleted = saved.isCompleted
                resetRequested =
                    shouldResetPlaybackForMixtape(
                        saved.progressMs,
                        saved.durationMs,
                        entryPoint,
                    )
                initialLikeState = saved.isLiked
            }
        }
        val initialPosition =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = initialPositionMs,
                savedProgressMs = savedProgressMs,
                isCompleted = isCompleted,
                skipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS,
                resetRequested = resetRequested,
            )
        return Pair(initialPosition.positionMs, initialLikeState)
    }

    /**
     * Returns a stable id for the current "night window" (10:30 PM - 4:00 AM), or null if
     * the current time is outside that window. Nights that cross midnight share the id of the
     * calendar day the window started on, so a single window is never split in two.
     */
    private fun currentNightWindowId(): String? =
        cx.aswin.boxlore.core.data.playback.NightWindowLogic
            .currentNightWindowId()

    fun isDebugSkipSleepWindowEnabled(): Boolean = prefs.getBoolean(KEY_DEBUG_SKIP_SLEEP_WINDOW, false)

    fun setDebugSkipSleepWindow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_SKIP_SLEEP_WINDOW, enabled).apply()
    }

    /**
     * Single chokepoint for the late-night sleep prompt. Called whenever playback transitions
     * from paused/stopped to playing, from any entry point (new episode, resume, skip,
     * notification, Bluetooth, auto-advance). Shows the prompt once per night window.
     */
    private fun onPlaybackStarted() {
        if (isDebugSkipSleepWindowEnabled()) {
            _playerState.value = _playerState.value.copy(showLateNightNudge = true)
            return
        }
        val windowId = currentNightWindowId() ?: return
        val stored = prefs.getString(KEY_LAST_SLEEP_PROMPT_WINDOW_ID, null)
        if (windowId != stored) {
            prefs.edit().putString(KEY_LAST_SLEEP_PROMPT_WINDOW_ID, windowId).apply()
            _playerState.value = _playerState.value.copy(showLateNightNudge = true)
        }
    }

    private fun storePendingEntryPoint(entryPointContext: android.os.Bundle?) {
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
                cx.aswin.boxlore.core.data.analytics.PendingEntryPoint
                    .set(map)
            }
        }
    }

    suspend fun playQueue(
        episodes: List<Episode>,
        podcast: Podcast,
        startIndex: Int = 0,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        initialPositionMs: Long? = null,
        sourceContext: android.os.Bundle? = null,
    ) = queueCoordinator.playQueue(episodes, podcast, startIndex, entryPoint, initialPositionMs, sourceContext)

    suspend fun addToQueue(
        episode: Episode,
        podcast: Podcast,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    ) = queueCoordinator.addToQueue(episode, podcast, entryPoint)

    suspend fun addToQueueNext(
        episode: Episode,
        podcast: Podcast,
    ) = queueCoordinator.addToQueueNext(episode, podcast)

    suspend fun removeFromQueue(
        episodeId: String,
        deferSkipSignal: Boolean = false,
    ): RemovedQueueItem? = queueCoordinator.removeFromQueue(episodeId, deferSkipSignal)

    fun confirmQueueRemoval(removed: RemovedQueueItem) = queueCoordinator.confirmQueueRemoval(removed)

    suspend fun undoQueueRemoval(removed: RemovedQueueItem) = queueCoordinator.undoQueueRemoval(removed)

    fun moveQueueItem(
        fromQueueIndex: Int,
        toQueueIndex: Int,
    ) = queueCoordinator.moveQueueItem(fromQueueIndex, toQueueIndex)

    suspend fun persistQueueOrder(
        movedEpisodeId: String? = null,
        fromQueueIndex: Int = -1,
        toQueueIndex: Int = -1,
    ) = queueCoordinator.persistQueueOrder(movedEpisodeId, fromQueueIndex, toQueueIndex)

    suspend fun hasNonLoreQueue(): Boolean = queueCoordinator.hasNonLoreQueue()

    suspend fun stopAndClearQueue() = queueCoordinator.stopAndClearQueue()

    suspend fun playEpisode(
        episode: Episode,
        podcast: Podcast,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        initialPositionMs: Long? = null,
    ) = queueCoordinator.playEpisode(episode, podcast, entryPoint, initialPositionMs)

    suspend fun playFromQueueIndex(
        episodeId: String,
        queueList: List<Episode>,
        podcast: Podcast,
    ) = queueCoordinator.playFromQueueIndex(episodeId, queueList, podcast)

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
        var episode =
            Episode(
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
                enclosureType = lastSession.enclosureType,
            )

        val podcast =
            Podcast(
                id = lastSession.podcastId,
                title = lastSession.podcastName,
                artist = "",
                imageUrl = lastSession.podcastImageUrl ?: "",
                description = null,
                genre = "Podcast",
            )

        // Restore Queue from DB (queue items now include P2.0 fields)
        val savedQueue = queueRepository.getQueueSnapshot()

        // Enrich restored episode with P2.0 data from queue if available
        val queueEpisode = savedQueue.find { it.id == episode.id }
        if (queueEpisode != null) {
            episode =
                episode.copy(
                    chaptersUrl = queueEpisode.chaptersUrl,
                    transcriptUrl = queueEpisode.transcriptUrl,
                    persons = queueEpisode.persons,
                    transcripts = queueEpisode.transcripts,
                    seasonNumber = queueEpisode.seasonNumber,
                    episodeNumber = queueEpisode.episodeNumber,
                    episodeType = queueEpisode.episodeType,
                )
        }

        // If saved queue is empty but we have an episode, make a single-item queue
        val restoredQueue = if (savedQueue.isEmpty()) listOf(episode) else savedQueue

        // Prefer live MediaController truth when the playback service is still running
        // (e.g. user swiped the app from recents while audio continued). Forcing
        // isPlaying=false here races with syncStateFromMediaController() and leaves
        // the UI paused while ExoPlayer keeps playing.
        val controller = mediaHandle.controller
        val controllerPlaying = controller?.isPlaying == true
        val controllerPosition = controller?.currentPosition?.takeIf { it > 0 }
        val controllerDuration = controller?.duration?.takeIf { it > 0 }

        _playerState.value =
            _playerState.value.copy(
                currentEpisode = episode,
                currentPodcast = podcast,
                isPlaying = controllerPlaying,
                position = controllerPosition ?: lastSession.progressMs,
                duration = controllerDuration ?: lastSession.durationMs,
                isLiked = lastSession.isLiked,
                queue = restoredQueue,
            )

        // Re-sync after metadata restore in case the controller connected first and
        // onIsPlayingChanged won't fire again (already playing when the listener attached).
        if (controller != null) {
            syncStateFromMediaController()
        }

        return true
    }

    /**
     * Clear the current session (for swipe-to-dismiss)
     */
    fun clearSession() {
        mediaHandle.controller?.stop()
        mediaHandle.controller?.clearMediaItems()
        stopProgressTicker()
        _playerState.value = PlayerState()
        // Mark as dismissed so we don't restore on next app launch
        prefs.edit().putBoolean(KEY_PLAYER_DISMISSED, true).apply()
    }

    fun togglePlayPause(entryPointContext: android.os.Bundle? = null) {
        val controller = mediaHandle.controller ?: return
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
        mediaHandle.controller?.pause()
    }

    fun resume(entryPointContext: android.os.Bundle? = null) = transportHelper.resume(entryPointContext)

    fun seekTo(
        positionMs: Long,
        play: Boolean = false,
    ) {
        mediaHandle.controller?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(position = positionMs)

        if (play) {
            mediaHandle.controller?.play()
        }

        // Save state on seek (do not update lastPlayedAt to prevent reordering on scrub)
        repositoryScope.launch { saveCurrentState(updateLastPlayedAt = false) }
    }

    fun skipForward() {
        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
            .setSeekSource("seek_forward")
        val state = _playerState.value
        val incrementMs = PlaybackSkipPolicy.sanitizeSeekForward(state.seekForwardMs)
        seekTo((state.position + incrementMs).coerceAtMost(state.duration))
    }

    fun skipBackward() {
        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
            .setSeekSource("seek_backward")
        val state = _playerState.value
        val incrementMs = PlaybackSkipPolicy.sanitizeSeekBackward(state.seekBackwardMs)
        seekTo((state.position - incrementMs).coerceAtLeast(0))
    }

    fun skipToEpisode(
        index: Int,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        sourceContext: android.os.Bundle? = null,
    ) = transportHelper.skipToEpisode(index, entryPoint, sourceContext)

    fun skipToNextEpisode() = transportHelper.skipToNextEpisode()

    fun skipToPreviousEpisode() = transportHelper.skipToPreviousEpisode()

    fun setPlaybackSpeed(speed: Float) {
        mediaHandle.controller?.playbackParameters = PlaybackParameters(speed)
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
        repositoryScope.launch {
            try {
                userPreferencesRepository.setPlaybackSpeed(speed)
            } catch (exception: java.io.IOException) {
                Log.w("PlaybackRepo", "Unable to persist playback speed", exception)
            }
        }
    }

    fun setSleepTimer(
        durationMinutes: Int,
        dismissNudge: Boolean = true,
    ) {
        Log.d("PlaybackRepo", "setSleepTimer called: $durationMinutes minutes, dismissNudge=$dismissNudge")
        sleepTimerJob?.cancel()

        if (durationMinutes <= 0) {
            Log.d("PlaybackRepo", "Sleep timer: OFF")
            SleepTimerHolder.activeSleepTimerEndMs = null
            SleepTimerHolder.sleepAtEndOfEpisode = false
            _playerState.value =
                _playerState.value.copy(
                    sleepTimerEnd = null,
                    sleepAtEndOfEpisode = false,
                    showLateNightNudge = if (dismissNudge) false else _playerState.value.showLateNightNudge,
                )
            return
        }

        // Special marker for "End of Episode" mode
        if (durationMinutes == cx.aswin.boxlore.core.model.SleepTimerConstants.END_OF_EPISODE_MINUTES) {
            Log.d("PlaybackRepo", "Sleep timer: End of Episode mode ENABLED")
            SleepTimerHolder.activeSleepTimerEndMs = null
            SleepTimerHolder.sleepAtEndOfEpisode = true
            _playerState.value =
                _playerState.value.copy(
                    sleepAtEndOfEpisode = true,
                    sleepTimerEnd = null,
                    showLateNightNudge = if (dismissNudge) false else _playerState.value.showLateNightNudge,
                )

            sleepTimerJob =
                repositoryScope.launch {
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
            SleepTimerHolder.activeSleepTimerEndMs = endTime
            SleepTimerHolder.sleepAtEndOfEpisode = false
            _playerState.value =
                _playerState.value.copy(
                    sleepTimerEnd = endTime,
                    sleepAtEndOfEpisode = false,
                    showLateNightNudge = if (dismissNudge) false else _playerState.value.showLateNightNudge,
                )

            sleepTimerJob =
                repositoryScope.launch {
                    while (true) {
                        val currentEnd = SleepTimerHolder.activeSleepTimerEndMs
                        if (currentEnd == null) break
                        if (System.currentTimeMillis() >= currentEnd) {
                            Log.d("PlaybackRepo", "Sleep timer: FIRING! Pausing playback.")
                            SleepTimerHolder.activeSleepTimerEndMs = null
                            mediaHandle.controller?.pause()
                            stopProgressTicker()
                            _playerState.value =
                                _playerState.value.copy(
                                    sleepTimerEnd = null,
                                    isPlaying = false,
                                    showLateNightNudge = false,
                                )
                            break
                        }
                        delay(1000)
                    }
                }
        }
    }

    /** True when the current time falls inside the 10:30 PM - 4:00 AM night window. */
    fun isInNightWindow(): Boolean = currentNightWindowId() != null

    fun dismissLateNightNudge() {
        Log.d("PlaybackRepo", "dismissLateNightNudge() called, current showLateNightNudge=${_playerState.value.showLateNightNudge}")
        _playerState.value = _playerState.value.copy(showLateNightNudge = false)
        // Snooze for the rest of this window even if it wasn't stamped on show
        // (e.g. a debug-forced prompt outside the normal trigger).
        currentNightWindowId()?.let { windowId ->
            prefs.edit().putString(KEY_LAST_SLEEP_PROMPT_WINDOW_ID, windowId).apply()
        }
    }

    /** Clears the once-per-night guard so the prompt can be re-triggered for testing. */
    fun resetSleepNudgeForTesting() {
        prefs.edit().remove(KEY_LAST_SLEEP_PROMPT_WINDOW_ID).apply()
        setSleepTimer(0)
    }

    /** Debug-only: force the prompt to show immediately, bypassing all cadence checks. */
    fun forceShowSleepPromptForTesting() {
        _playerState.value = _playerState.value.copy(showLateNightNudge = true)
    }

    val lastPlayedSession: Flow<PlaybackSession?> = historyStore.lastPlayedSession

    val resumeSessions: Flow<List<PlaybackSession>> = historyStore.resumeSessions

    override fun getAllHistory(): Flow<List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity>> =
        historyStore.getAllHistory()

    val likedEpisodes: Flow<List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity>> =
        historyStore.likedEpisodes

    val completedEpisodeIds: Flow<Set<String>> = historyStore.completedEpisodeIds

    override suspend fun upsertHistoryEntity(entity: cx.aswin.boxlore.core.data.database.ListeningHistoryEntity) {
        historyStore.upsertHistoryEntity(entity)
    }

    suspend fun removeHistoryItem(episodeId: String) = historyStore.removeHistoryItem(episodeId)

    suspend fun clearHistory() = historyStore.clearHistory()

    suspend fun toggleLike(
        episode: Episode,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) = historyStore.toggleLike(episode, podcastId, podcastTitle, podcastImageUrl)

    suspend fun toggleCompletion(
        episode: Episode,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) = historyStore.toggleCompletion(episode, podcastId, podcastTitle, podcastImageUrl)

    suspend fun savePlaybackState(
        podcastId: String,
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        episodeTitle: String,
        episodeImageUrl: String?,
        podcastImageUrl: String?,
        episodeAudioUrl: String,
        podcastName: String,
        isCompleted: Boolean,
        isLiked: Boolean,
        lastPlayedAt: Long = System.currentTimeMillis(),
        enclosureType: String? = null,
        episodeDescription: String? = null,
    ) = historyStore.savePlaybackState(
        podcastId,
        episodeId,
        positionMs,
        durationMs,
        episodeTitle,
        episodeImageUrl,
        podcastImageUrl,
        episodeAudioUrl,
        podcastName,
        isCompleted,
        isLiked,
        lastPlayedAt,
        enclosureType,
        episodeDescription,
    )

    suspend fun toggleLike() = historyStore.toggleLike()

    suspend fun deleteSession(episodeId: String) = historyStore.deleteSession(episodeId)

    suspend fun getSession(episodeId: String): PlaybackSession? = historyStore.getSession(episodeId)

    suspend fun getRecentHistoryList(limit: Int): List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity> =
        historyStore.getRecentHistoryList(limit)

    override suspend fun markAllEpisodesCompleted(
        episodes: List<Episode>,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) = historyStore.markAllEpisodesCompleted(episodes, podcastId, podcastTitle, podcastImageUrl)

    suspend fun markAllEpisodesUncompleted(episodes: List<Episode>) =
        historyStore.markAllEpisodesUncompleted(episodes)

    suspend fun getHistoryForRecommendations(limit: Int = 15): List<cx.aswin.boxlore.core.network.model.HistoryItem> =
        historyStore.getHistoryForRecommendations(limit)
}
