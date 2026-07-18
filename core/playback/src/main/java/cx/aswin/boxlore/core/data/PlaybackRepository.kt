package cx.aswin.boxlore.core.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import cx.aswin.boxlore.core.data.BuildConfig
import cx.aswin.boxlore.core.data.playback.PlaybackSkipPolicy
import cx.aswin.boxlore.core.data.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.data.ranking.CandidateSource
import cx.aswin.boxlore.core.data.ranking.FeedbackTarget
import cx.aswin.boxlore.core.data.ranking.RankingAction
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.data.service.BoxLorePlaybackService
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val LEARN_PREFIX = "learn:"
private const val EPISODE_PREFIX = "episode:"
private const val QUEUE_PREFIX = "queue:"

private fun String.stripPlaybackPrefix(): String = removePrefix(LEARN_PREFIX).removePrefix(EPISODE_PREFIX).removePrefix(QUEUE_PREFIX)

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
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    val controller: MediaController? get() = mediaController

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
    private var likeStateObserverJob: Job? = null

    private val QUEUE_MAX_SIZE = 50

    // Local memory of rejected auto-fill suggestions (feeds the SmartQueueEngine).
    private val queueSkipMemory = QueueSkipMemory.fromContext(context)

    init {
        getOrCreateDeviceUuid()
        initializeMediaController()
        monitorLikeState()
        monitorChaptersAndTranscripts()
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

    private var chaptersAndTranscriptFetchJob: Job? = null
    private var autoTranscriptGenerationJob: Job? = null
    private var lastLoadedEpisodeId: String? = null

    private fun monitorChaptersAndTranscripts() {
        repositoryScope.launch {
            playerState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collect { episodeId ->
                    android.util.Log.d("PlaybackRepo", "monitorChaptersAndTranscripts: collected episodeId=$episodeId")
                    if (episodeId == null) {
                        lastLoadedEpisodeId = null
                        chaptersAndTranscriptFetchJob?.cancel()
                        _playerState.value =
                            _playerState.value.copy(
                                currentChapters = emptyList(),
                                isChaptersLoading = false,
                                currentTranscript = emptyList(),
                            )
                    } else {
                        if (episodeId == lastLoadedEpisodeId) {
                            android.util.Log.d(
                                "PlaybackRepo",
                                "monitorChaptersAndTranscripts: episodeId matches lastLoadedEpisodeId, ignoring",
                            )
                            return@collect
                        }
                        android.util.Log.d("PlaybackRepo", "monitorChaptersAndTranscripts: starting fetch job for episodeId=$episodeId")
                        lastLoadedEpisodeId = episodeId
                        chaptersAndTranscriptFetchJob?.cancel()
                        chaptersAndTranscriptFetchJob =
                            launch {
                                var episode = _playerState.value.currentEpisode
                                android.util.Log.d(
                                    "PlaybackRepo",
                                    "monitorChaptersAndTranscripts: currentEpisode title=${episode?.title}, audioUrl=${episode?.audioUrl}, chaptersUrl=${episode?.chaptersUrl}, transcriptUrl=${episode?.transcriptUrl}",
                                )

                                // If missing metadata, try to enrich from PodcastRepository.
                                // Skip when URLs are already present (they may carry server signatures).
                                if (episodeId.startsWith("briefing_") && (episode?.chaptersUrl == null || episode.transcriptUrl == null)) {
                                    try {
                                        android.util.Log.d(
                                            "PlaybackRepo",
                                            "monitorChaptersAndTranscripts: enriching briefing episode $episodeId",
                                        )
                                        val parts = episodeId.split("_")
                                        android.util.Log.d(
                                            "PlaybackRepo",
                                            "monitorChaptersAndTranscripts: parts=$parts, size=${parts.size}",
                                        )
                                        if (parts.size >= 3) {
                                            val region = parts[1]
                                            val date = parts[2]
                                            val audioUri = android.net.Uri.parse(episode?.audioUrl ?: "")
                                            val version = audioUri.getQueryParameter("v")
                                            val versionParam = if (version != null) "&v=$version" else ""
                                            val mappedRegion = mapRegionForBriefing(region)
                                            android.util.Log.d(
                                                "PlaybackRepo",
                                                "monitorChaptersAndTranscripts: region=$region, mappedRegion=$mappedRegion, date=$date, version=$version",
                                            )

                                            val updatedEpisode =
                                                episode?.copy(
                                                    chaptersUrl = "${BuildConfig.BOXCAST_API_BASE_URL}/briefings/chapters/$mappedRegion?d=$date$versionParam",
                                                    transcriptUrl = "${BuildConfig.BOXCAST_API_BASE_URL}/briefings/transcript/$mappedRegion?d=$date$versionParam",
                                                )
                                            android.util.Log.d(
                                                "PlaybackRepo",
                                                "monitorChaptersAndTranscripts: updatedEpisode chaptersUrl=${updatedEpisode?.chaptersUrl}, transcriptUrl=${updatedEpisode?.transcriptUrl}",
                                            )
                                            if (updatedEpisode != null) {
                                                val updatedQueue =
                                                    _playerState.value.queue.map {
                                                        if (it.id == episodeId) updatedEpisode else it
                                                    }
                                                _playerState.value =
                                                    _playerState.value.copy(
                                                        currentEpisode = updatedEpisode,
                                                        queue = updatedQueue,
                                                    )
                                                episode = updatedEpisode
                                                android.util.Log.d(
                                                    "PlaybackRepo",
                                                    "monitorChaptersAndTranscripts: playerState updated with enriched briefing episode",
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("PlaybackRepo", "Failed to enrich briefing episode $episodeId", e)
                                    }
                                } else if (episode != null && (episode.chaptersUrl == null || episode.transcriptUrl == null)) {
                                    try {
                                        val enriched = podcastRepository.getEpisode(episodeId)
                                        if (enriched != null && _playerState.value.currentEpisode?.id == episodeId) {
                                            val updatedEpisode =
                                                episode.copy(
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
                                                    podcastArtist = episode.podcastArtist ?: enriched.podcastArtist,
                                                )
                                            val updatedQueue =
                                                _playerState.value.queue.map {
                                                    if (it.id == episodeId) updatedEpisode else it
                                                }
                                            _playerState.value =
                                                _playerState.value.copy(
                                                    currentEpisode = updatedEpisode,
                                                    queue = updatedQueue,
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
                                    _playerState.value =
                                        _playerState.value.copy(
                                            isChaptersLoading = true,
                                            currentChapters = emptyList(),
                                            isChaptersNative = true,
                                            autoChaptersState = AutoTranscriptState.NONE,
                                        )
                                    launch {
                                        val chapters = ChapterRepository.getChapters(chaptersUrl)
                                        if (_playerState.value.currentEpisode?.id == episodeId) {
                                            _playerState.value =
                                                _playerState.value.copy(
                                                    currentChapters = chapters,
                                                    isChaptersLoading = false,
                                                )
                                        }
                                    }
                                } else {
                                    val parsedChapters = ChapterRepository.parseChaptersFromDescription(episode?.description)
                                    if (parsedChapters.isNotEmpty()) {
                                        _playerState.value =
                                            _playerState.value.copy(
                                                currentChapters = parsedChapters,
                                                isChaptersLoading = false,
                                                isChaptersNative = true,
                                                autoChaptersState = AutoTranscriptState.NONE,
                                            )
                                    } else {
                                        val autoChapters = ChapterRepository.getCachedChapters("auto_${episode?.id}") ?: emptyList()
                                        _playerState.value =
                                            _playerState.value.copy(
                                                currentChapters = autoChapters,
                                                isChaptersLoading = false,
                                                isChaptersNative = false,
                                                autoChaptersState = if (autoChapters.isNotEmpty()) AutoTranscriptState.COMPLETED else _playerState.value.autoChaptersState,
                                            )
                                    }
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
                                    if (chaptersUrl.isNullOrEmpty() && episode.audioUrl.isNotEmpty()) {
                                        launch {
                                            val deviceUuid = getOrCreateDeviceUuid()
                                            val response =
                                                TranscriptRepository.checkAutoTranscriptStatus(
                                                    api = podcastRepository.api,
                                                    publicKey = podcastRepository.publicKey,
                                                    deviceUuid = deviceUuid,
                                                    episodeId = episodeId,
                                                    audioUrl = episode.audioUrl,
                                                    transcriptUrl = episode.transcriptUrl,
                                                )
                                            if (_playerState.value.currentEpisode?.id != episodeId) return@launch

                                            val status = response?.status
                                            val limitLeft = response?.limitLeft
                                            val chapters = response?.chapters

                                            _playerState.value =
                                                _playerState.value.copy(
                                                    autoTranscriptLimitLeft = limitLeft,
                                                )

                                            if (chapters != null) {
                                                _playerState.value =
                                                    _playerState.value.copy(
                                                        currentChapters = if (_playerState.value.isChaptersNative) _playerState.value.currentChapters else chapters,
                                                        autoChaptersState =
                                                            if (chapters.isNotEmpty() ||
                                                                _playerState.value.isChaptersNative
                                                            ) {
                                                                AutoTranscriptState.COMPLETED
                                                            } else {
                                                                _playerState.value.autoChaptersState
                                                            },
                                                    )
                                            }

                                            when (status) {
                                                "completed" -> {
                                                    _playerState.value =
                                                        _playerState.value.copy(
                                                            autoChaptersState = AutoTranscriptState.COMPLETED,
                                                        )
                                                }
                                                "pending", "uploaded" -> {
                                                    _playerState.value =
                                                        _playerState.value.copy(
                                                            autoChaptersState = AutoTranscriptState.GENERATING,
                                                        )
                                                    startAutoTranscriptGeneration(
                                                        episodeId,
                                                        episode.audioUrl,
                                                        episode.transcriptUrl,
                                                        isTranscriptRequested = false,
                                                    )
                                                }
                                                "failed" -> {
                                                    _playerState.value =
                                                        _playerState.value.copy(
                                                            autoChaptersState = AutoTranscriptState.FAILED,
                                                        )
                                                }
                                                else -> {
                                                    _playerState.value =
                                                        _playerState.value.copy(
                                                            autoChaptersState = if (_playerState.value.currentChapters.isEmpty()) AutoTranscriptState.NOT_GENERATED else _playerState.value.autoChaptersState,
                                                        )
                                                }
                                            }
                                        }
                                    }
                                } else if (episode != null && episode.audioUrl.isNotEmpty()) {
                                    // No RSS transcript — check auto-transcript status
                                    _playerState.value =
                                        _playerState.value.copy(
                                            autoTranscriptState = AutoTranscriptState.CHECKING,
                                            currentTranscript = emptyList(),
                                        )
                                    launch {
                                        val deviceUuid = getOrCreateDeviceUuid()
                                        val response =
                                            TranscriptRepository.checkAutoTranscriptStatus(
                                                api = podcastRepository.api,
                                                publicKey = podcastRepository.publicKey,
                                                deviceUuid = deviceUuid,
                                                episodeId = episodeId,
                                                audioUrl = episode.audioUrl,
                                                transcriptUrl = episode.transcriptUrl,
                                            )
                                        if (_playerState.value.currentEpisode?.id != episodeId) return@launch

                                        val status = response?.status
                                        val limitLeft = response?.limitLeft
                                        val chapters = response?.chapters

                                        _playerState.value =
                                            _playerState.value.copy(
                                                autoTranscriptLimitLeft = limitLeft,
                                            )

                                        if (chapters != null && _playerState.value.currentEpisode?.id == episodeId) {
                                            _playerState.value =
                                                _playerState.value.copy(
                                                    currentChapters = if (_playerState.value.isChaptersNative) _playerState.value.currentChapters else chapters,
                                                    autoChaptersState =
                                                        if (chapters.isNotEmpty() ||
                                                            _playerState.value.isChaptersNative
                                                        ) {
                                                            AutoTranscriptState.COMPLETED
                                                        } else {
                                                            _playerState.value.autoChaptersState
                                                        },
                                                )
                                        }

                                        when (status) {
                                            "completed" -> {
                                                // Transcript exists — fetch the full SRT
                                                _playerState.value =
                                                    _playerState.value.copy(
                                                        autoTranscriptState = AutoTranscriptState.COMPLETED,
                                                    )
                                                val transcript =
                                                    TranscriptRepository.getAutoTranscript(
                                                        api = podcastRepository.api,
                                                        publicKey = podcastRepository.publicKey,
                                                        deviceUuid = deviceUuid,
                                                        episodeId = episodeId,
                                                        audioUrl = episode.audioUrl,
                                                        transcriptUrl = episode.transcriptUrl,
                                                    )
                                                if (_playerState.value.currentEpisode?.id == episodeId) {
                                                    val autoChapters = ChapterRepository.getCachedChapters("auto_$episodeId") ?: emptyList()
                                                    _playerState.value =
                                                        _playerState.value.copy(
                                                            currentTranscript = transcript,
                                                            currentChapters =
                                                                if (_playerState.value.isChaptersNative) {
                                                                    _playerState.value.currentChapters
                                                                } else {
                                                                    (
                                                                        if (autoChapters
                                                                                .isNotEmpty()
                                                                        ) {
                                                                            autoChapters
                                                                        } else {
                                                                            _playerState.value.currentChapters
                                                                        }
                                                                    )
                                                                },
                                                            autoChaptersState =
                                                                if (autoChapters.isNotEmpty() ||
                                                                    _playerState.value.isChaptersNative
                                                                ) {
                                                                    AutoTranscriptState.COMPLETED
                                                                } else {
                                                                    _playerState.value.autoChaptersState
                                                                },
                                                        )
                                                }
                                            }
                                            "pending", "uploaded" -> {
                                                // Already in progress — start polling
                                                val wasTranscriptGenerating =
                                                    _playerState.value.autoTranscriptState == AutoTranscriptState.GENERATING
                                                val wasChaptersGenerating =
                                                    _playerState.value.autoChaptersState == AutoTranscriptState.GENERATING

                                                val nextTranscriptState =
                                                    if (wasTranscriptGenerating) {
                                                        AutoTranscriptState.GENERATING
                                                    } else {
                                                        _playerState.value.autoTranscriptState
                                                    }
                                                val nextChaptersState =
                                                    if (wasChaptersGenerating) {
                                                        AutoTranscriptState.GENERATING
                                                    } else {
                                                        _playerState.value.autoChaptersState
                                                    }

                                                _playerState.value =
                                                    _playerState.value.copy(
                                                        autoTranscriptState = nextTranscriptState,
                                                        autoChaptersState = nextChaptersState,
                                                    )
                                                startAutoTranscriptGeneration(
                                                    episodeId,
                                                    episode.audioUrl,
                                                    episode.transcriptUrl,
                                                    isTranscriptRequested = wasTranscriptGenerating,
                                                )
                                            }
                                            "failed" -> {
                                                _playerState.value =
                                                    _playerState.value.copy(
                                                        autoTranscriptState = AutoTranscriptState.FAILED,
                                                    )
                                            }
                                            else -> {
                                                // "not_started" or null — eligible for generation
                                                _playerState.value =
                                                    _playerState.value.copy(
                                                        autoTranscriptState = AutoTranscriptState.NOT_GENERATED,
                                                        autoChaptersState = if (_playerState.value.currentChapters.isEmpty()) AutoTranscriptState.NOT_GENERATED else _playerState.value.autoChaptersState,
                                                    )
                                            }
                                        }
                                    }
                                } else {
                                    _playerState.value =
                                        _playerState.value.copy(
                                            currentTranscript = emptyList(),
                                            autoTranscriptState = AutoTranscriptState.NONE,
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
        _playerState.value =
            _playerState.value.copy(
                autoTranscriptState = AutoTranscriptState.GENERATING,
                autoChaptersState = if (isChaptersEmpty) AutoTranscriptState.GENERATING else _playerState.value.autoChaptersState,
            )
        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
            .trackAutoTranscriptRequested(episodeId, episode.podcastId, episode.audioUrl)
        if (isChaptersEmpty) {
            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                .trackAutoChaptersRequested(episodeId, episode.podcastId, episode.audioUrl)
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

        _playerState.value =
            _playerState.value.copy(
                autoChaptersState = AutoTranscriptState.GENERATING,
            )
        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
            .trackAutoChaptersRequested(episodeId, episode.podcastId, episode.audioUrl)
        startAutoTranscriptGeneration(episodeId, episode.audioUrl, episode.transcriptUrl, isTranscriptRequested = false)
    }

    /**
     * Starts the background polling/streaming call to generate and retrieve
     * the auto-transcript. Cancels any previous generation job.
     */
    private fun startAutoTranscriptGeneration(
        episodeId: String,
        audioUrl: String,
        transcriptUrl: String?,
        isTranscriptRequested: Boolean,
    ) {
        val deviceUuid = getOrCreateDeviceUuid()
        autoTranscriptGenerationJob?.cancel()
        autoTranscriptGenerationJob =
            repositoryScope.launch {
                try {
                    val transcript =
                        TranscriptRepository.getAutoTranscript(
                            api = podcastRepository.api,
                            publicKey = podcastRepository.publicKey,
                            deviceUuid = deviceUuid,
                            episodeId = episodeId,
                            audioUrl = audioUrl,
                            transcriptUrl = transcriptUrl,
                        )
                    val currentEp = _playerState.value.currentEpisode
                    if (currentEp?.id == episodeId) {
                        if (transcript.isNotEmpty()) {
                            val autoChapters = ChapterRepository.getCachedChapters("auto_$episodeId") ?: emptyList()
                            _playerState.value =
                                _playerState.value.copy(
                                    currentTranscript = transcript,
                                    currentChapters =
                                        if (_playerState.value.isChaptersNative) {
                                            _playerState.value.currentChapters
                                        } else {
                                            (
                                                if (autoChapters
                                                        .isNotEmpty()
                                                ) {
                                                    autoChapters
                                                } else {
                                                    _playerState.value.currentChapters
                                                }
                                            )
                                        },
                                    autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.COMPLETED else _playerState.value.autoTranscriptState,
                                    autoChaptersState =
                                        if (autoChapters.isNotEmpty() ||
                                            _playerState.value.isChaptersNative
                                        ) {
                                            AutoTranscriptState.COMPLETED
                                        } else {
                                            AutoTranscriptState.FAILED
                                        },
                                )
                            if (isTranscriptRequested) {
                                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAutoTranscriptCompleted(
                                    episodeId,
                                    currentEp.podcastId,
                                    currentEp.duration.toFloat(),
                                    transcript.size,
                                )
                            }
                            if (autoChapters.isNotEmpty()) {
                                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAutoChaptersCompleted(
                                    episodeId,
                                    currentEp.podcastId,
                                    currentEp.duration.toFloat(),
                                    autoChapters.size,
                                )
                            } else {
                                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                                    episodeId,
                                    currentEp.podcastId,
                                    "Chapters empty or generation failed",
                                )
                            }
                        } else {
                            _playerState.value =
                                _playerState.value.copy(
                                    autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.FAILED else _playerState.value.autoTranscriptState,
                                    autoChaptersState = AutoTranscriptState.FAILED,
                                )
                            if (isTranscriptRequested) {
                                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAutoTranscriptFailed(
                                    episodeId,
                                    currentEp.podcastId,
                                    "Transcript empty or generation failed",
                                )
                            }
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                                episodeId,
                                currentEp.podcastId,
                                "Transcript empty or generation failed (required for chapters)",
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackRepo", "Auto-transcript generation failed for $episodeId", e)
                    val currentEp = _playerState.value.currentEpisode
                    if (currentEp?.id == episodeId) {
                        _playerState.value =
                            _playerState.value.copy(
                                autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.FAILED else _playerState.value.autoTranscriptState,
                                autoChaptersState = AutoTranscriptState.FAILED,
                            )
                        if (isTranscriptRequested) {
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAutoTranscriptFailed(
                                episodeId,
                                currentEp.podcastId,
                                e.message ?: "Unknown error",
                            )
                        }
                        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                            episodeId,
                            currentEp.podcastId,
                            e.message ?: "Unknown error",
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
                        likeStateObserverJob =
                            launch {
                                listeningHistoryDao.getHistoryItemFlow(episodeId).collect { history ->
                                    if (history == null) {
                                        _playerState.value = _playerState.value.copy(isLiked = false, isCompleted = false)
                                    } else {
                                        if (_playerState.value.isLiked != history.isLiked ||
                                            _playerState.value.isCompleted != history.isCompleted
                                        ) {
                                            _playerState.value =
                                                _playerState.value.copy(
                                                    isLiked = history.isLiked,
                                                    isCompleted = history.isCompleted,
                                                )
                                        }
                                    }
                                }
                            }
                    }
                }
        }
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, BoxLorePlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            // Restore the persisted playback speed so it survives app restarts.
            repositoryScope.launch {
                val savedSpeed = userPreferencesRepository.playbackSpeedStream.first()
                if (savedSpeed != 1.0f && mediaController?.playbackParameters?.speed != savedSpeed) {
                    mediaController?.playbackParameters = PlaybackParameters(savedSpeed)
                    _playerState.value = _playerState.value.copy(playbackSpeed = savedSpeed)
                }
            }
            mediaController?.addListener(
                object : androidx.media3.common.Player.Listener {
                    private var pendingSaveJob: Job? = null

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d("PlaybackRepo", "onIsPlayingChanged: isPlaying=$isPlaying, currentPos=${mediaController?.currentPosition}")
                        val oldIsPlaying = _playerState.value.isPlaying
                        _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) {
                            pendingSaveJob?.cancel() // Cancel pending save if we resume
                            if (!oldIsPlaying) {
                                activePlaybackStartTimeMs = System.currentTimeMillis()
                                onPlaybackStarted()
                            }
                            startProgressTicker()
                        } else {
                            stopProgressTicker()
                            val hasBeenPlayingFor10s =
                                activePlaybackStartTimeMs > 0 &&
                                    (System.currentTimeMillis() - activePlaybackStartTimeMs >= 10_000)
                            pendingSaveJob?.cancel()
                            pendingSaveJob =
                                repositoryScope.launch {
                                    saveCurrentState(updateLastPlayedAt = hasBeenPlayingFor10s)
                                }
                            activePlaybackStartTimeMs = 0L
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val isLoading = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                        _playerState.value = _playerState.value.copy(isLoading = isLoading)

                        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                            Log.d("PlaybackRepo", "Playback ENDED. Cancelling pending saves.")
                            pendingSaveJob?.cancel() // CRITICAL: Prevent "Pause" save from overwriting completion

                            val controller = mediaController
                            val hasMedia = controller != null && controller.mediaItemCount > 0
                            val reachedEnd =
                                controller != null &&
                                    controller.duration > 0 &&
                                    (
                                        controller.currentPosition >= controller.duration - 10000 ||
                                            controller.currentPosition >= controller.duration * 0.95
                                    )

                            if (hasMedia && reachedEnd) {
                                // Sleep Timer: End of Episode — stop everything if EOE is active
                                if (_playerState.value.sleepAtEndOfEpisode) {
                                    Log.d("PlaybackRepo", "Sleep Timer (EOE): Episode ended, stopping playback.")
                                    mediaController?.stop()
                                    mediaController?.clearMediaItems()
                                    sleepTimerJob?.cancel()
                                    stopProgressTicker()
                                    _playerState.value =
                                        _playerState.value.copy(
                                            isPlaying = false,
                                            position = 0,
                                            sleepTimerEnd = null,
                                            sleepAtEndOfEpisode = false,
                                        )
                                    return
                                }

                                _playerState.value = _playerState.value.copy(isPlaying = false, position = 0)
                                stopProgressTicker()
                                // Natural completion persistence is service-owned. The history
                                // observer mirrors the resulting DB state back into PlayerState.
                            } else {
                                Log.d(
                                    "PlaybackRepo",
                                    "Playback ended but not naturally completed (hasMedia=$hasMedia, reachedEnd=$reachedEnd). Skipping completion marking.",
                                )
                                _playerState.value = _playerState.value.copy(isPlaying = false)
                                stopProgressTicker()
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
                            _playerState.value =
                                _playerState.value.copy(
                                    queue = newQueue,
                                    isLoading = true,
                                )
                            controller.prepare()
                            controller.play()
                        } else {
                            // No more items — clear everything
                            Log.d("PlaybackRepo", "onPlayerError: No more items in queue, clearing.")
                            controller.stop()
                            controller.clearMediaItems()
                            _playerState.value =
                                _playerState.value.copy(
                                    isPlaying = false,
                                    isLoading = false,
                                    currentEpisode = null,
                                    queue = emptyList(),
                                )
                        }

                        repositoryScope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast
                                    .makeText(
                                        context,
                                        "Stream unavailable, skipping...",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                    }

                    override fun onMediaItemTransition(
                        mediaItem: androidx.media3.common.MediaItem?,
                        reason: Int,
                    ) {
                        if (mediaController?.isPlaying == true) {
                            activePlaybackStartTimeMs = System.currentTimeMillis()
                        } else {
                            activePlaybackStartTimeMs = 0L
                        }
                        // Use mediaId to find episode — more reliable than index
                        val episodeId = mediaItem?.mediaId?.stripPlaybackPrefix() ?: return
                        val queue = _playerState.value.queue
                        val oldState = _playerState.value

                        val slotIndex = queue.indexOfFirst { it.id == episodeId }
                        android.util.Log.d(
                            "PlaybackRepo",
                            "onMediaItemTransition: mediaId=${mediaItem.mediaId}, stripped=$episodeId, slotIndex=$slotIndex, queueSize=${queue.size}, reason=$reason",
                        )

                        // Sleep Timer: End of Episode — intercept auto-advance
                        if (oldState.sleepAtEndOfEpisode && reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            Log.d("PlaybackRepo", "Sleep Timer (EOE): Auto-advance intercepted. Pausing playback.")
                            mediaController?.pause()
                            sleepTimerJob?.cancel()
                            stopProgressTicker()
                            _playerState.value =
                                _playerState.value.copy(
                                    isPlaying = false,
                                    sleepTimerEnd = null,
                                    sleepAtEndOfEpisode = false,
                                )
                            // Completion persistence and timer-holder clearing are service-owned.
                            // The history observer mirrors completion into this repository.
                            return
                        }

                        // Launch a single coroutine to handle the transition logic sequentially
                        // This ensures DB updates finish BEFORE we trigger SmartQueue refill
                        repositoryScope.launch {
                            val finalQueue: List<Episode>
                            val finalSlotIndex: Int

                            if (slotIndex == -1) {
                                android.util.Log.w(
                                    "PlaybackRepo",
                                    "onMediaItemTransition: Episode $episodeId NOT found in local queue. Attempting recovery from DB...",
                                )
                                val dbQueue = queueRepository.getQueueSnapshot()
                                val dbSlotIndex = dbQueue.indexOfFirst { it.id == episodeId }
                                android.util.Log.d(
                                    "PlaybackRepo",
                                    "onMediaItemTransition (Recovery): dbSlotIndex=$dbSlotIndex, dbQueueSize=${dbQueue.size}",
                                )

                                val resolvedEpisode =
                                    if (dbSlotIndex != -1) {
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
                                            publishedDate = 0L,
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

                            var newEpisode = finalQueue[finalSlotIndex]
                            // Enrich only when URLs are missing; present ones may carry server signatures
                            if (newEpisode.id.startsWith("briefing_") &&
                                (newEpisode.chaptersUrl == null || newEpisode.transcriptUrl == null)
                            ) {
                                try {
                                    val parts = newEpisode.id.split("_")
                                    if (parts.size >= 3) {
                                        val region = parts[1]
                                        val date = parts[2]
                                        val audioUri = android.net.Uri.parse(newEpisode.audioUrl)
                                        val version = audioUri.getQueryParameter("v")
                                        val versionParam = if (version != null) "&v=$version" else ""
                                        val mappedRegion = mapRegionForBriefing(region)
                                        newEpisode =
                                            newEpisode.copy(
                                                chaptersUrl = "${BuildConfig.BOXCAST_API_BASE_URL}/briefings/chapters/$mappedRegion?d=$date$versionParam",
                                                transcriptUrl = "${BuildConfig.BOXCAST_API_BASE_URL}/briefings/transcript/$mappedRegion?d=$date$versionParam",
                                            )
                                        android.util.Log.d(
                                            "PlaybackRepo",
                                            "onMediaItemTransition: Enriched briefing episode ${newEpisode.id} with chaptersUrl=${newEpisode.chaptersUrl}",
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PlaybackRepo", "Failed to enrich transition briefing episode ${newEpisode.id}", e)
                                }
                            }
                            android.util.Log.d("PlaybackRepo", "Media transition to: ${newEpisode.title}")

                            // 1. Mark PREVIOUS episode as completed (if distinct and transition reason permits)
                            val previousEpisode = oldState.currentEpisode
                            val previousPodcast = oldState.currentPodcast
                            val isServiceOwnedNaturalAdvance =
                                previousEpisode?.id ==
                                    PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId
                            if (isServiceOwnedNaturalAdvance) {
                                PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId = null
                            }

                            val shouldMarkCompleted =
                                reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
                                    currentSkipBehavior == "mark_completed_skip" &&
                                    !isServiceOwnedNaturalAdvance

                            if (previousEpisode != null && previousEpisode.id != newEpisode.id && shouldMarkCompleted) {
                                android.util.Log.d(
                                    "PlaybackRepo",
                                    "Transition (ShouldMarkCompleted): Marking previous episode as COMPLETED: ${previousEpisode.title} (ID: ${previousEpisode.id}), reason: $reason, skipBehavior: $currentSkipBehavior",
                                )
                                markEpisodeAsCompleted(previousEpisode, previousPodcast)
                            }

                            // 2. Derive podcast context from episode metadata & local DB
                            val newPodcast: Podcast? =
                                if (newEpisode.podcastId != null) {
                                    val existingPod = oldState.currentPodcast
                                    val database =
                                        cx.aswin.boxlore.core.data.database.BoxLoreDatabase
                                            .getDatabase(context)
                                    val dbPodEntity = database.podcastDao().getPodcast(newEpisode.podcastId!!)
                                    val dbPodcast =
                                        dbPodEntity?.let { entity ->
                                            cx.aswin.boxlore.core.model.Podcast(
                                                id = entity.podcastId,
                                                title = entity.title,
                                                artist = entity.author,
                                                imageUrl = entity.imageUrl,
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
                                                preferredSort = entity.preferredSort,
                                            )
                                        }

                                    if (dbPodcast != null && dbPodcast.title != "Unknown Podcast") {
                                        dbPodcast
                                    } else if (existingPod != null &&
                                        existingPod.id == newEpisode.podcastId &&
                                        existingPod.title != "Unknown Podcast"
                                    ) {
                                        // Preserve the fully-populated existing podcast object
                                        existingPod
                                    } else {
                                        cx.aswin.boxlore.core.model.Podcast(
                                            id = newEpisode.podcastId!!,
                                            title =
                                                newEpisode.podcastTitle?.takeIf { !it.isNullOrBlank() && it != "Unknown Podcast" }
                                                    ?: "Unknown Podcast",
                                            artist = newEpisode.podcastArtist?.takeIf { it.isNotEmpty() } ?: existingPod?.artist ?: "",
                                            imageUrl =
                                                newEpisode.podcastImageUrl?.takeIf {
                                                    it.isNotEmpty()
                                                } ?: existingPod?.imageUrl ?: "",
                                            description = null,
                                            genre = newEpisode.podcastGenre ?: existingPod?.genre ?: "Podcast",
                                        )
                                    }
                                } else {
                                    oldState.currentPodcast
                                }

                            // 3. QUEUE CONSUMPTION: Drop items before current
                            val newQueue = finalQueue.drop(finalSlotIndex)
                            android.util.Log.d("PlaybackRepo", "Consuming queue: Dropped $finalSlotIndex items. New size: ${newQueue.size}")
                            _playerState.value =
                                _playerState.value.copy(
                                    currentEpisode = newEpisode,
                                    currentPodcast = newPodcast,
                                    queue = newQueue,
                                )

                            // 4. Sync queue to DB for restart recovery
                            syncQueueToDb()

                            // Resume/intro seeking is owned exclusively by BoxLorePlaybackService.
                            // A controller transition must never apply a second seek.

                            // NOTE: auto-refill is owned exclusively by BoxLorePlaybackService's
                            // transition listener (single guarded path, works with UI closed).
                            // Items it appends are picked up by onTimelineChanged below.
                        }
                    }

                    override fun onTimelineChanged(
                        timeline: androidx.media3.common.Timeline,
                        reason: Int,
                    ) {
                        // The playback service (auto-refill, Android Auto) can append items
                        // directly to the player. Reconcile the in-memory queue whenever the
                        // playlist changes so the UI stays in sync.
                        if (reason == androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                            reconcileQueueWithController()
                        }
                    }
                },
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
                    mediaController?.let { controller ->
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

    /**
     * Rebuilds PlayerState.queue from the controller's playlist when the two diverge —
     * e.g. after the playback service auto-refilled the queue or Android Auto appended
     * items directly to the player. Episode metadata is resolved from the in-memory
     * queue first, then the persisted queue rows (which carry contextType/source for
     * the queue-sheet labels), then the MediaItem itself as a last resort.
     */
    private fun reconcileQueueWithController() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return

        removeDuplicateUpcomingItems(controller)
        val orderedIds = controller.upcomingEpisodeIds()
        if (orderedIds == _playerState.value.queue.map { it.id }) return

        repositoryScope.launch {
            reconcileQueueSnapshot(orderedIds)
        }
    }

    private fun removeDuplicateUpcomingItems(controller: Player) {
        val seenIds = mutableSetOf<String>()
        val duplicateIndices = mutableListOf<Int>()
        for (index in controller.currentMediaItemIndex.coerceAtLeast(0) until controller.mediaItemCount) {
            val id = QueueMath.stripMediaIdPrefixes(controller.getMediaItemAt(index).mediaId)
            if (!seenIds.add(id)) duplicateIndices += index
        }
        if (duplicateIndices.isEmpty()) return

        android.util.Log.w(
            "PlaybackRepo",
            "reconcileQueueWithController: Removing ${duplicateIndices.size} duplicate media items",
        )
        duplicateIndices.asReversed().forEach(controller::removeMediaItem)
    }

    private fun Player.upcomingEpisodeIds(): List<String> {
        val start = currentMediaItemIndex.coerceAtLeast(0)
        return (start until mediaItemCount).map { index ->
            QueueMath.stripMediaIdPrefixes(getMediaItemAt(index).mediaId)
        }
    }

    private suspend fun reconcileQueueSnapshot(orderedIds: List<String>) {
        var dbItems = loadPersistedQueueById()

        // The service persists refill rows just before appending to the player; give
        // a slow write one retry before falling back to bare MediaItem metadata.
        val knownNow = _playerState.value.queue.associateBy { it.id }
        if (orderedIds.any { it !in knownNow && it !in dbItems }) {
            kotlinx.coroutines.delay(400)
            dbItems = loadPersistedQueueById(dbItems)
        }

        // Re-read the controller: the playlist may have changed again meanwhile.
        val controllerNow = mediaController ?: return
        if (controllerNow.mediaItemCount == 0) return
        val startNow = controllerNow.currentMediaItemIndex.coerceAtLeast(0)
        val idsNow = controllerNow.upcomingEpisodeIds()
        val latestQueue = _playerState.value.queue
        if (idsNow == latestQueue.map { it.id }) return

        val known = latestQueue.associateBy { it.id }
        val newQueue =
            idsNow
                .mapIndexed { offset, id ->
                    val currentEpisode = known[id]
                    val persistedEpisode = dbItems[id]
                    when {
                        currentEpisode != null -> currentEpisode
                        persistedEpisode != null -> persistedEpisode
                        else -> buildEpisodeFromMediaItem(controllerNow.getMediaItemAt(startNow + offset), id)
                    }
                }.distinctBy { it.id }
        android.util.Log.d("PlaybackRepo", "reconcileQueueWithController: ${latestQueue.size} -> ${newQueue.size} items")
        _playerState.value = _playerState.value.copy(queue = newQueue)
        syncQueueToDb()
    }

    private suspend fun loadPersistedQueueById(fallback: Map<String, Episode> = emptyMap()): Map<String, Episode> =
        try {
            queueRepository.getQueueSnapshot().associateBy { it.id }
        } catch (exception: kotlinx.coroutines.CancellationException) {
            throw exception
        } catch (exception: Exception) {
            android.util.Log.w("PlaybackRepo", "Unable to read persisted queue snapshot", exception)
            fallback
        }

    private fun buildEpisodeFromMediaItem(
        item: MediaItem,
        episodeId: String,
    ): Episode {
        val metadata = item.mediaMetadata
        return Episode(
            id = episodeId,
            title = metadata.title?.toString() ?: "Episode",
            description = "",
            audioUrl = item.localConfiguration?.uri?.toString() ?: "",
            imageUrl = metadata.artworkUri?.toString(),
            podcastImageUrl = metadata.artworkUri?.toString(),
            podcastTitle = metadata.subtitle?.toString() ?: metadata.artist?.toString(),
            podcastArtist = metadata.artist?.toString(),
            podcastGenre = metadata.genre?.toString(),
            duration = 0,
            publishedDate = 0L,
            // Items we didn't add locally were appended by the service refill path.
            contextType = "AUTO_FILL",
        )
    }

    private fun buildMediaItems(
        episodes: List<Episode>,
        podcast: Podcast,
        entryPointContext: android.os.Bundle?,
    ): List<MediaItem> {
        val entryPoint = entryPointContext?.getString("entrypoint") ?: entryPointContext?.getString("entry_point")
        val isLearn = entryPoint == "learn"
        return episodes.map { episode ->
            val resolvedUrl =
                episode.imageUrl?.takeIf { it.isNotBlank() }
                    ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() }
                    ?: podcast.imageUrl
            Log.d(
                "PlaybackRepo",
                "playQueue: epId=${episode.id}, title='${episode.title}', resolvedImageUrl='$resolvedUrl', isLearn=$isLearn",
            )
            val metadata =
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(episode.title)
                    .setArtist(episode.podcastTitle ?: podcast.title)
                    .setArtworkUri(android.net.Uri.parse(resolvedUrl))
                    .setDisplayTitle(episode.title)
                    .setSubtitle(episode.podcastTitle ?: podcast.title)
                    .setGenre(episode.podcastGenre ?: podcast.genre)
                    .setExtras(entryPointContext)
                    .build()

            val mediaId = if (isLearn) "$LEARN_PREFIX${episode.id}" else episode.id
            MediaItem
                .Builder()
                .setUri(episode.audioUrl)
                .setMediaMetadata(metadata)
                .setMediaId(mediaId)
                .setCustomCacheKey(episode.id)
                .build()
        }
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
    ) {
        Log.d("PlaybackRepo", "playQueue() called: count=${episodes.size}, start=$startIndex, podcastGenre='${podcast.genre}'")
        val requestedStartId = episodes.getOrNull(startIndex)?.id
        val uniqueEpisodes = episodes.distinctBy { it.id }
        val uniqueStartIndex =
            requestedStartId
                ?.let { id -> uniqueEpisodes.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: 0
        if (uniqueEpisodes.size != episodes.size) {
            Log.w(
                "PlaybackRepo",
                "playQueue: Removed ${episodes.size - uniqueEpisodes.size} duplicate episode IDs",
            )
        }

        prefs.edit().putBoolean(KEY_PLAYER_DISMISSED, false).apply()

        if (mediaController == null) {
            mediaController = mediaControllerFuture?.await()
        }

        mediaController?.let { controller ->
            // A rich source bundle (e.g. "episode_info_screen", "home_hero_*") always wins.
            // Otherwise fall back to a bundle derived from the coarse enum. Note the key must
            // be "entry_point" — the playback service only reads that key.
            val entryPointContext =
                sourceContext?.takeIf { it.getString("entry_point") != null }
                    ?: if (entryPoint != PlaybackEntryPoint.GENERIC) {
                        android.os.Bundle().apply {
                            putString("entry_point", entryPoint.name.lowercase())
                        }
                    } else {
                        null
                    }
            val mediaItems = buildMediaItems(uniqueEpisodes, podcast, entryPointContext)

            val startEpisodeId = uniqueEpisodes.getOrNull(uniqueStartIndex)?.id
            val (startPosMs, initialLikeState) = checkSavedProgress(startEpisodeId, initialPositionMs, entryPoint)

            val currentEp = uniqueEpisodes.getOrNull(uniqueStartIndex)
            if (currentEp != null) {
                // playQueue optimistically flips isPlaying=true here, ahead of the real
                // MediaController callback, so the onIsPlayingChanged edge-trigger below
                // won't see a false->true transition for this path. Trigger explicitly.
                val wasPlaying = _playerState.value.isPlaying
                _playerState.value =
                    _playerState.value.copy(
                        currentEpisode = currentEp,
                        currentPodcast = podcast,
                        isPlaying = true,
                        position = startPosMs,
                        duration = currentEp.duration.toLong() * 1000,
                        queue = uniqueEpisodes,
                        isLiked = initialLikeState,
                    )
                if (!wasPlaying) {
                    onPlaybackStarted()
                }
            }

            if (startPosMs > 0L) {
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                    .setSeekSource("resume")
            }
            controller.setMediaItems(mediaItems, uniqueStartIndex, startPosMs)
            controller.prepare()

            storePendingEntryPoint(entryPointContext)

            controller.play()
            syncQueueToDb()
            saveCurrentState(updateLastPlayedAt = false)
        }
    }

    suspend fun addToQueue(
        episode: Episode,
        podcast: Podcast,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    ) {
        Log.d("PlaybackRepo", "addToQueue called: episodeId=${episode.id}, title=${episode.title}, entryPoint=$entryPoint")

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
            val resolvedUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcast.imageUrl
            Log.d("PlaybackRepo", "addToQueue: epId=${episode.id}, resolvedImageUrl='$resolvedUrl'")
            val metadata =
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast.title)
                    .setArtworkUri(android.net.Uri.parse(resolvedUrl))
                    .setDisplayTitle(episode.title)
                    .setSubtitle(podcast.title)
                    .setGenre(episode.podcastGenre ?: podcast.genre)
                    .build()

            val isLearn = entryPoint == PlaybackEntryPoint.LEARN
            val mediaId = if (isLearn) "$LEARN_PREFIX${episode.id}" else episode.id
            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(episode.audioUrl)
                    .setMediaMetadata(metadata)
                    .setMediaId(mediaId)
                    .setCustomCacheKey(episode.id) // Match DownloadRequest custom key
                    .build()

            controller.addMediaItem(mediaItem)
            Log.d("PlaybackRepo", "addToQueue: Added to Media3, new mediaItemCount=${controller.mediaItemCount}")

            // Update local state
            val currentQueue = _playerState.value.queue
            _playerState.value = _playerState.value.copy(queue = currentQueue + episode)
            Log.d("PlaybackRepo", "addToQueue: Updated local state, queue size=${_playerState.value.queue.size}")
            syncQueueToDb()
            rankingFeedbackRepository.recordAction(
                target =
                    FeedbackTarget(
                        episodeId = episode.id,
                        podcastId = podcast.id,
                        genre = episode.podcastGenre ?: podcast.genre,
                        source =
                            if (entryPoint == PlaybackEntryPoint.LEARN) {
                                CandidateSource.CURATED_INTENT
                            } else {
                                null
                            },
                    ),
                action = RankingAction.EXPLICIT_QUEUE,
            )
        } ?: Log.e("PlaybackRepo", "addToQueue: mediaController still NULL after await!")
    }

    suspend fun addToQueueNext(
        episode: Episode,
        podcast: Podcast,
    ) {
        if (mediaController == null) {
            mediaController = mediaControllerFuture?.await()
        }

        mediaController?.let { controller ->
            val resolvedUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcast.imageUrl
            Log.d("PlaybackRepo", "addToQueueNext: epId=${episode.id}, resolvedImageUrl='$resolvedUrl'")
            val metadata =
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast.title)
                    .setArtworkUri(android.net.Uri.parse(resolvedUrl))
                    .setDisplayTitle(episode.title)
                    .setSubtitle(podcast.title)
                    .setGenre(episode.podcastGenre ?: podcast.genre)
                    .build()

            val mediaItem =
                MediaItem
                    .Builder()
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

            val newQueue =
                if (currentQueue.isNotEmpty()) {
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
            syncQueueToDb()
        }
    }

    /**
     * Snapshot of a removed queue item, returned so the UI can offer Undo and so the
     * skip signal (analytics + skip memory) can be deferred until the undo window lapses.
     */
    data class RemovedQueueItem(
        val episode: Episode,
        val queueIndex: Int,
        val mediaIndex: Int,
        val contextType: String?,
        val contextSourceId: String?,
    )

    /**
     * Removes an episode from the queue (Media3 + in-memory + DB).
     *
     * @param deferSkipSignal when true, the AUTO_FILL rejection signal is NOT recorded
     *   here — the caller must invoke [confirmQueueRemoval] once the undo window lapses
     *   (or [undoQueueRemoval] if the user undoes), so an undone remove doesn't count
     *   as a rejection.
     * @return removal info for undo, or null if the episode wasn't in the queue.
     */
    suspend fun removeFromQueue(
        episodeId: String,
        deferSkipSignal: Boolean = false,
    ): RemovedQueueItem? {
        if (mediaController == null) {
            mediaController = mediaControllerFuture?.await()
        }

        val queueItem =
            try {
                queueRepository.getQueueItemByEpisodeId(episodeId)
            } catch (e: Exception) {
                null
            }

        val currentQueue = _playerState.value.queue
        val queueIndex = currentQueue.indexOfFirst { it.id == episodeId }

        var mediaIndex = -1
        mediaController?.let { controller ->
            val mediaIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
            mediaIndex = QueueMath.mediaIndexOfEpisode(mediaIds, episodeId)
        }

        val removedInfo =
            if (queueIndex != -1) {
                val episode = currentQueue[queueIndex]
                RemovedQueueItem(
                    episode =
                        episode.copy(
                            contextType = queueItem?.contextType ?: episode.contextType,
                            contextSourceId = queueItem?.contextSourceId ?: episode.contextSourceId,
                        ),
                    queueIndex = queueIndex,
                    mediaIndex = mediaIndex,
                    contextType = queueItem?.contextType ?: episode.contextType,
                    contextSourceId = queueItem?.contextSourceId ?: episode.contextSourceId,
                )
            } else {
                null
            }

        if (!deferSkipSignal && removedInfo != null) {
            confirmQueueRemoval(removedInfo)
        }

        var removedFromController = false
        mediaController?.let { controller ->
            if (mediaIndex != -1) {
                controller.removeMediaItem(mediaIndex)
                removedFromController = true
            }
        }

        // Always update local state and sync to DB, even if the item wasn't in Media3 playlist
        val existsInLocalQueue = currentQueue.any { it.id == episodeId }

        if (existsInLocalQueue || !removedFromController) {
            val newQueue = currentQueue.filter { it.id != episodeId }
            _playerState.value = _playerState.value.copy(queue = newQueue)
            syncQueueToDb()
        }
        return removedInfo
    }

    /**
     * Records the rejection signal for a removed AUTO_FILL item: PostHog analytics plus
     * local skip memory (so the SmartQueueEngine stops re-suggesting it and can
     * down-rank the podcast). Called immediately on remove, or after the undo window.
     */
    fun confirmQueueRemoval(removed: RemovedQueueItem) {
        if (removed.contextType != "AUTO_FILL") return
        try {
            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackSmartQueueEpisodeSkipped(
                episodeId = removed.episode.id,
                recommendationSource = removed.contextSourceId ?: "unknown",
                positionInQueue = removed.mediaIndex,
            )
            queueSkipMemory.recordSkip(
                episodeId = removed.episode.id,
                podcastId = removed.episode.podcastId,
                source = removed.contextSourceId,
            )
            repositoryScope.launch {
                rankingFeedbackRepository.recordAction(
                    target =
                        FeedbackTarget(
                            episodeId = removed.episode.id,
                            podcastId = removed.episode.podcastId.orEmpty(),
                            genre = removed.episode.podcastGenre,
                            source = CandidateSource.SERVER_RECOMMENDATION,
                        ),
                    action = RankingAction.REMOVE_AUTOFILLED,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "Failed to record queue removal signal for ${removed.episode.id}", e)
        }
    }

    /** Re-inserts a removed episode at its original position (Media3 + state + DB). */
    suspend fun undoQueueRemoval(removed: RemovedQueueItem) {
        val episode = removed.episode
        if (_playerState.value.queue.any { it.id == episode.id }) return
        if (mediaController == null) {
            mediaController = mediaControllerFuture?.await()
        }
        val controller = mediaController ?: return

        val isLore = removed.contextType == QueueMath.CONTEXT_TYPE_LORE
        val mediaId = if (isLore) "$LEARN_PREFIX${episode.id}" else episode.id
        val resolvedUrl =
            episode.imageUrl?.takeIf { it.isNotBlank() }
                ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: ""
        val metadata =
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(episode.title)
                .setArtist(episode.podcastTitle ?: "")
                .setArtworkUri(android.net.Uri.parse(resolvedUrl))
                .setDisplayTitle(episode.title)
                .setSubtitle(episode.podcastTitle ?: "")
                .setGenre(episode.podcastGenre ?: "Podcast")
                .build()
        val mediaItem =
            MediaItem
                .Builder()
                .setUri(episode.audioUrl)
                .setMediaMetadata(metadata)
                .setMediaId(mediaId)
                .setCustomCacheKey(episode.id)
                .build()

        val insertMediaIndex =
            removed.mediaIndex
                .takeIf { it in 0..controller.mediaItemCount }
                ?: controller.mediaItemCount
        controller.addMediaItem(insertMediaIndex, mediaItem)

        val currentQueue = _playerState.value.queue.toMutableList()
        val insertQueueIndex = removed.queueIndex.coerceIn(0, currentQueue.size)
        currentQueue.add(insertQueueIndex, episode)
        _playerState.value = _playerState.value.copy(queue = currentQueue.toList())
        syncQueueToDb()
    }

    /**
     * Moves a queue item to a new position, updating all three layers in order:
     * Media3 playlist (no playback interruption), in-memory PlayerState.queue, and —
     * via [persistQueueOrder], typically debounced to drag end — the Room queue table.
     *
     * Indices are PlayerState.queue indices; index 0 (the playing item) is pinned.
     */
    fun moveQueueItem(
        fromQueueIndex: Int,
        toQueueIndex: Int,
    ) {
        val queue = _playerState.value.queue
        if (fromQueueIndex == toQueueIndex) return
        if (fromQueueIndex !in queue.indices || toQueueIndex !in queue.indices) return
        if (fromQueueIndex == 0 || toQueueIndex == 0) return

        val controller = mediaController ?: return
        val episode = queue[fromQueueIndex]

        // Resolve Media3 indices by mediaId (with learn-prefix stripped), never by raw
        // queue index: the playlist can retain already-played items before the current one.
        val mediaIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
        val fromMedia = QueueMath.mediaIndexOfEpisode(mediaIds, episode.id)
        if (fromMedia != -1) {
            val base =
                QueueMath
                    .mediaIndexOfEpisode(mediaIds, queue[0].id)
                    .takeIf { it != -1 } ?: controller.currentMediaItemIndex.coerceAtLeast(0)
            val toMedia = (base + toQueueIndex).coerceIn(0, controller.mediaItemCount - 1)
            controller.moveMediaItem(fromMedia, toMedia)
        }

        _playerState.value =
            _playerState.value.copy(
                queue = QueueMath.moveItem(queue, fromQueueIndex, toQueueIndex),
            )
    }

    /**
     * Persists the current queue order to Room (called once on drag end so rapid moves
     * don't thrash the DB) and emits the reorder analytics event.
     */
    suspend fun persistQueueOrder(
        movedEpisodeId: String? = null,
        fromQueueIndex: Int = -1,
        toQueueIndex: Int = -1,
    ) {
        val queue = _playerState.value.queue
        try {
            queueRepository.reorderQueue(queue.map { it.id })
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "persistQueueOrder: Failed", e)
        }
        if (movedEpisodeId != null && fromQueueIndex != toQueueIndex && fromQueueIndex >= 0 && toQueueIndex >= 0) {
            val movedEpisode = queue.firstOrNull { it.id == movedEpisodeId }
            val contextType = movedEpisode?.contextType
            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackQueueReordered(
                episodeId = movedEpisodeId,
                fromPosition = fromQueueIndex,
                toPosition = toQueueIndex,
                contextType = contextType,
            )
            movedEpisode?.let { episode ->
                rankingFeedbackRepository.recordAction(
                    target =
                        FeedbackTarget(
                            episodeId = episode.id,
                            podcastId = episode.podcastId.orEmpty(),
                            genre = episode.podcastGenre,
                        ),
                    action =
                        if (toQueueIndex < fromQueueIndex) {
                            RankingAction.MOVE_UP
                        } else {
                            RankingAction.MOVE_DOWN
                        },
                )
            }
        }
    }

    /**
     * True when the current queue contains any normal (non-Lore) item. Checks the live
     * player first, then the persisted queue rows (which survive process restarts).
     */
    suspend fun hasNonLoreQueue(): Boolean {
        if (mediaController == null) {
            mediaController = mediaControllerFuture?.await()
        }
        val controller = mediaController
        if (controller != null && controller.mediaItemCount > 0) {
            val mediaIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
            return QueueMath.hasNonLoreMediaIds(mediaIds)
        }
        val snapshot =
            try {
                queueRepository.getQueueSnapshot()
            } catch (e: Exception) {
                emptyList()
            }
        return snapshot.isNotEmpty() && QueueMath.hasNonLoreContextTypes(snapshot.map { it.contextType })
    }

    /**
     * Stops playback and clears the queue everywhere (player + state + DB). Used when
     * the user confirms starting a fresh Lore queue over an existing normal queue.
     */
    suspend fun stopAndClearQueue() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        stopProgressTicker()
        _playerState.value = PlayerState()
        // A new queue is about to start; don't block session restore on next launch.
        prefs.edit().putBoolean(KEY_PLAYER_DISMISSED, false).apply()
        try {
            queueRepository.clearQueue()
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "stopAndClearQueue: Failed to clear DB queue", e)
        }
    }

    suspend fun playEpisode(
        episode: Episode,
        podcast: Podcast,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        initialPositionMs: Long? = null,
    ) {
        playQueue(listOf(episode), podcast, 0, entryPoint, initialPositionMs)
    }

    /**
     * Play an episode from the provided queue list, reloading into Media3 from that point.
     * Pass the queue list directly to avoid stale state issues.
     */
    suspend fun playFromQueueIndex(
        episodeId: String,
        queueList: List<Episode>,
        podcast: Podcast,
    ) {
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

        // Update state but don't play
        _playerState.value =
            _playerState.value.copy(
                currentEpisode = episode,
                currentPodcast = podcast,
                isPlaying = false,
                position = lastSession.progressMs,
                duration = lastSession.durationMs,
                isLiked = lastSession.isLiked,
                queue = restoredQueue,
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

        // Attribute the resume so playback_started isn't logged as "not set". An explicit
        // source (e.g. a screen that set PendingEntryPoint just before) always wins via
        // setIfAbsent; otherwise we tag the surface this resume came from.
        val hasExplicitSource = entryPointContext?.getString("entry_point") != null

        fun applyResumeSource(default: String) {
            if (hasExplicitSource) {
                storePendingEntryPoint(entryPointContext)
            } else {
                cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.setIfAbsent(
                    mapOf("entry_point" to default),
                )
            }
        }

        // If controller has no media but we have state, reload the FULL queue
        if (controller.mediaItemCount == 0 && _playerState.value.currentEpisode != null) {
            val queue = _playerState.value.queue
            val currentEpisode = _playerState.value.currentEpisode!!
            val podcast = _playerState.value.currentPodcast
            val savedPosition = _playerState.value.position

            Log.d("PlaybackRepo", "resume(): Controller empty, reloading full queue (${queue.size} items)")

            repositoryScope.launch {
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                    .setSeekSource("resume")
                if (queue.isNotEmpty() && podcast != null) {
                    // Find current episode in queue and reload from that point
                    val startIndex = queue.indexOfFirst { it.id == currentEpisode.id }.coerceAtLeast(0)
                    Log.d("PlaybackRepo", "resume(): Reloading queue from index $startIndex with position=$savedPosition")

                    val mediaItems =
                        queue.map { episode ->
                            val metadata =
                                androidx.media3.common.MediaMetadata
                                    .Builder()
                                    .setTitle(episode.title)
                                    .setArtist(episode.podcastTitle ?: podcast.title)
                                    .setArtworkUri(
                                        android.net.Uri.parse(
                                            episode.imageUrl?.takeIf { it.isNotBlank() }
                                                ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() }
                                                ?: podcast.imageUrl,
                                        ),
                                    ).setDisplayTitle(episode.title)
                                    .setSubtitle(episode.podcastTitle ?: podcast.title)
                                    .setGenre(episode.podcastGenre ?: podcast.genre)
                                    .setExtras(entryPointContext)
                                    .build()

                            MediaItem
                                .Builder()
                                .setUri(episode.audioUrl)
                                .setMediaMetadata(metadata)
                                .setMediaId(episode.id)
                                .setCustomCacheKey(episode.id)
                                .build()
                        }

                    controller.setMediaItems(mediaItems, startIndex, savedPosition.coerceAtLeast(0L))
                    controller.prepare()
                    applyResumeSource("resume_restore")
                    controller.play()
                } else {
                    // Fallback: single episode resume (no queue available)
                    Log.d("PlaybackRepo", "resume(): No queue, loading single episode")
                    val metadata =
                        androidx.media3.common.MediaMetadata
                            .Builder()
                            .setTitle(currentEpisode.title)
                            .setArtist(podcast?.title ?: "")
                            .setArtworkUri(
                                android.net.Uri.parse(currentEpisode.imageUrl?.takeIf { it.isNotBlank() } ?: podcast?.imageUrl ?: ""),
                            ).setDisplayTitle(currentEpisode.title)
                            .setSubtitle(podcast?.title ?: "")
                            .setGenre(currentEpisode.podcastGenre ?: podcast?.genre ?: "Podcast")
                            .setExtras(entryPointContext)
                            .build()

                    val mediaItem =
                        MediaItem
                            .Builder()
                            .setUri(currentEpisode.audioUrl)
                            .setMediaMetadata(metadata)
                            .setMediaId(currentEpisode.id)
                            .setCustomCacheKey(currentEpisode.id)
                            .build()

                    controller.setMediaItem(mediaItem, savedPosition.coerceAtLeast(0L))
                    controller.prepare()
                    applyResumeSource("resume_restore")
                    controller.play()
                }
            }
        } else {
            Log.d("PlaybackRepo", "resume(): Media exists, just calling play()")
            applyResumeSource("resume_player")
            controller.play()
        }
    }

    fun seekTo(
        positionMs: Long,
        play: Boolean = false,
    ) {
        mediaController?.seekTo(positionMs)
        _playerState.value = _playerState.value.copy(position = positionMs)

        if (play) {
            mediaController?.play()
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

    private fun reinitializePlaybackIfEmpty(
        controller: androidx.media3.session.MediaController,
        index: Int,
        entryPoint: PlaybackEntryPoint,
        sourceContext: android.os.Bundle? = null,
    ): Boolean {
        if (controller.mediaItemCount == 0 && _playerState.value.queue.isNotEmpty()) {
            android.util.Log.d("PlaybackRepo", "skipToEpisode: Controller empty but local queue exists. Re-initializing playback.")
            val queue = _playerState.value.queue
            val podcast = _playerState.value.currentPodcast

            if (index in queue.indices && podcast != null) {
                repositoryScope.launch {
                    playQueue(queue, podcast, index, entryPoint, sourceContext = sourceContext)
                }
                return true
            }
        }
        return false
    }

    private fun restorePositionAndSeek(
        controller: androidx.media3.session.MediaController,
        targetEpisodeId: String,
        mediaIndex: Int,
    ) {
        repositoryScope.launch {
            val saved = listeningHistoryDao.getHistoryItem(targetEpisodeId)
            val savedPosMs =
                if (saved != null && !saved.isCompleted && saved.progressMs > 2000) {
                    android.util.Log.d("PlaybackRepo", "skipToEpisode: Restoring saved position ${saved.progressMs}ms for $targetEpisodeId")
                    saved.progressMs
                } else {
                    0L
                }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                controller.seekTo(mediaIndex, savedPosMs)
                controller.play()
            }
        }
    }

    fun skipToEpisode(
        index: Int,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        sourceContext: android.os.Bundle? = null,
    ) {
        val controller = mediaController
        android.util.Log.d(
            "PlaybackRepo",
            "skipToEpisode: index=$index, controller=${controller != null}, mediaItemCount=${controller?.mediaItemCount ?: -1}",
        )

        if (controller == null) {
            android.util.Log.e("PlaybackRepo", "skipToEpisode: mediaController is NULL!")
            return
        }

        val entryPointContext =
            sourceContext?.takeIf { it.getString("entry_point") != null }
                ?: if (entryPoint != PlaybackEntryPoint.GENERIC) {
                    android.os.Bundle().apply {
                        putString("entry_point", entryPoint.name.lowercase())
                    }
                } else {
                    null
                }

        if (reinitializePlaybackIfEmpty(controller, index, entryPoint, entryPointContext)) {
            return
        }

        val targetEpisode = _playerState.value.queue.getOrNull(index)
        if (targetEpisode != null) {
            for (i in 0 until controller.mediaItemCount) {
                if (controller.getMediaItemAt(i).mediaId.stripPlaybackPrefix() == targetEpisode.id) {
                    android.util.Log.d("PlaybackRepo", "skipToEpisode: Found mediaId=${targetEpisode.id} at Media3 index $i")

                    storePendingEntryPoint(entryPointContext)

                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                        .setSeekSource("transition")
                    restorePositionAndSeek(controller, targetEpisode.id, i)
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
                            mediaController?.pause()
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

    val lastPlayedSession: Flow<PlaybackSession?> =
        listeningHistoryDao
            .getResumeItems()
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
                        enclosureType = latest.enclosureType,
                    )
                } else {
                    null
                }
            }

    val resumeSessions: Flow<List<PlaybackSession>> =
        listeningHistoryDao
            .getResumeItems()
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
                        enclosureType = entity.enclosureType,
                    )
                }
            }

    override fun getAllHistory(): Flow<List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity>> =
        listeningHistoryDao.getAllHistory()

    val likedEpisodes: Flow<List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity>> = listeningHistoryDao.getLikedEpisodes()

    val completedEpisodeIds: Flow<Set<String>> =
        listeningHistoryDao
            .getCompletedEpisodeIdsFlow()
            .map { it.toSet() }

    override suspend fun upsertHistoryEntity(entity: cx.aswin.boxlore.core.data.database.ListeningHistoryEntity) {
        listeningHistoryDao.upsert(entity)
    }

    suspend fun removeHistoryItem(episodeId: String) {
        listeningHistoryDao.delete(episodeId)
    }

    suspend fun clearHistory() {
        listeningHistoryDao.deleteAll()
        rankingFeedbackRepository.reset()
    }

    suspend fun toggleLike(
        episode: Episode,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) {
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
            val entity =
                cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
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
                    enclosureType = episode.enclosureType,
                    episodeDescription = episode.description,
                )
            listeningHistoryDao.upsert(entity)
        }
        rankingFeedbackRepository.recordAction(
            target =
                FeedbackTarget(
                    episodeId = episode.id,
                    podcastId = podcastId,
                    genre = episode.podcastGenre,
                ),
            action = if (newStatus) RankingAction.LIKE else RankingAction.UNLIKE,
        )
    }

    suspend fun toggleCompletion(
        episode: Episode,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) {
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val newStatus = !(existing?.isCompleted ?: false)

        if (existing != null) {
            val updated =
                existing.copy(
                    isCompleted = newStatus,
                    isManualCompletion = newStatus,
                    progressMs = 0L,
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    episodeDescription = existing.episodeDescription ?: episode.description,
                )
            listeningHistoryDao.upsert(updated)
        } else {
            // Create new entry
            val entity =
                cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
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
                    isBulkCompletion = false,
                    episodeDescription = episode.description,
                )
            listeningHistoryDao.upsert(entity)
        }
    }

    private suspend fun markEpisodeAsCompleted(
        episode: Episode,
        podcast: Podcast?,
    ) {
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
            val entity =
                cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
                    episodeId = episode.id,
                    podcastId = episode.podcastId ?: podcast!!.id,
                    episodeTitle = episode.title,
                    episodeImageUrl = episode.imageUrl,
                    podcastImageUrl = episode.podcastImageUrl ?: podcast?.imageUrl,
                    episodeAudioUrl = episode.audioUrl,
                    podcastName =
                        episode.podcastTitle.orEmpty().ifBlank {
                            podcast?.title ?: "Unknown Podcast"
                        },
                    progressMs = 0L, // Reset progress on completion
                    durationMs = episode.duration * 1000L,
                    isCompleted = true,
                    isLiked = false, // We don't know
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    enclosureType = episode.enclosureType,
                    episodeDescription = episode.description,
                )
            listeningHistoryDao.upsert(entity)
        } else {
            // UPDATE timestamp too so it appears in recently played (loop prevention)
            android.util.Log.d("PlaybackRepo", "Updating existing history item as completed")
            val updated =
                existing.copy(
                    isCompleted = true,
                    progressMs = 0L, // Reset progress!
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    episodeDescription = existing.episodeDescription ?: episode.description,
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
        enclosureType: String? = null,
        episodeDescription: String? = null,
    ) {
        android.util.Log.v("PlaybackRepo", "Saving playback state: $episodeTitle, pos=$positionMs, completed=$isCompleted")
        val entity =
            cx.aswin.boxlore.core.data.playback.ListeningHistoryUpsertLogic.buildProgressSaveEntity(
                cx.aswin.boxlore.core.data.playback.ListeningHistoryUpsertLogic.ProgressSaveInput(
                    podcastId = podcastId,
                    episodeId = episodeId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    episodeTitle = episodeTitle,
                    episodeImageUrl = episodeImageUrl,
                    podcastImageUrl = podcastImageUrl,
                    episodeAudioUrl = episodeAudioUrl,
                    podcastName = podcastName,
                    isCompleted = isCompleted,
                    isLiked = isLiked,
                    lastPlayedAt = lastPlayedAt,
                    enclosureType = enclosureType,
                    episodeDescription = episodeDescription,
                ),
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
            enclosureType = entity.enclosureType,
        )
    }

    /** Recent history rows for adaptive scoring (Home Because You Like, etc.). */
    suspend fun getRecentHistoryList(limit: Int): List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity> =
        listeningHistoryDao.getRecentHistoryList(limit)

    override suspend fun markAllEpisodesCompleted(
        episodes: List<Episode>,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) {
        val currentTime = System.currentTimeMillis()
        val entitiesToUpsert =
            episodes.map { episode ->
                cx.aswin.boxlore.core.data.playback.ListeningHistoryUpsertLogic.buildBulkCompleteEntity(
                    episode = episode,
                    podcastId = podcastId,
                    podcastTitle = podcastTitle,
                    podcastImageUrl = podcastImageUrl,
                    existing = listeningHistoryDao.getHistoryItem(episode.id),
                    nowMs = currentTime,
                )
            }
        listeningHistoryDao.upsertAll(entitiesToUpsert)
    }

    suspend fun markAllEpisodesUncompleted(episodes: List<Episode>) {
        val currentTime = System.currentTimeMillis()
        val entitiesToUpsert =
            episodes.mapNotNull { episode ->
                val existing = listeningHistoryDao.getHistoryItem(episode.id) ?: return@mapNotNull null
                cx.aswin.boxlore.core.data.playback.ListeningHistoryUpsertLogic.buildBulkUncompleteEntity(
                    existing = existing,
                    nowMs = currentTime,
                )
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

    suspend fun getHistoryForRecommendations(limit: Int = 15): List<cx.aswin.boxlore.core.network.model.HistoryItem> {
        val database =
            cx.aswin.boxlore.core.data.database.BoxLoreDatabase
                .getDatabase(context)
        val podcastDao = database.podcastDao()

        // Fetch up to limit * 3 recent items to have room for filtering out accidental skips/taps
        val rawHistory = listeningHistoryDao.getRecentHistoryList(limit * 3)
        return cx.aswin.boxlore.core.data.playback.HistoryRecommendationLogic
            .selectEligible(
                raw = rawHistory,
                limit = limit,
            ) { entity ->
                cx.aswin.boxlore.core.data.playback.HistoryRecommendationLogic.isEligible(
                    isManualCompletion = entity.isManualCompletion,
                    isBulkCompletion = entity.isBulkCompletion,
                    progressMs = entity.progressMs,
                    isCompleted = entity.isCompleted,
                )
            }.map { entity ->
                val podcast = podcastDao.getPodcast(entity.podcastId)
                android.util.Log.d(
                    "PlaybackRepo",
                    "Passing history: ${entity.episodeTitle} | Has Description: ${!entity.episodeDescription.isNullOrEmpty()} | Length: ${entity.episodeDescription?.length ?: 0}",
                )
                cx.aswin.boxlore.core.network.model.HistoryItem(
                    podcastTitle = entity.podcastName,
                    episodeTitle = entity.episodeTitle,
                    podcastId = entity.podcastId,
                    episodeId = entity.episodeId,
                    genre = podcast?.genre,
                    durationMs = entity.durationMs,
                    progressMs = entity.progressMs,
                    isCompleted = entity.isCompleted,
                    isLiked = entity.isLiked,
                    episodeDescription = entity.episodeDescription,
                )
            }
    }
}
