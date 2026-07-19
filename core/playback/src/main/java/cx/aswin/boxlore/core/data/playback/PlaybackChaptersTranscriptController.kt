package cx.aswin.boxlore.core.data.playback

import cx.aswin.boxlore.core.data.BuildConfig
import cx.aswin.boxlore.core.data.ChapterRepository
import cx.aswin.boxlore.core.data.PlayerState
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.TranscriptRepository
import cx.aswin.boxlore.core.data.mapRegionForBriefing
import cx.aswin.boxlore.core.model.AutoTranscriptState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Chapters / transcript monitoring and auto-generation for [cx.aswin.boxlore.core.data.PlaybackRepository].
 */
internal class PlaybackChaptersTranscriptController(
    private val scope: CoroutineScope,
    private val playerState: StateFlow<PlayerState>,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val podcastRepository: PodcastRepository,
    private val deviceUuid: () -> String,
) {
    private var chaptersAndTranscriptFetchJob: Job? = null
    private var autoTranscriptGenerationJob: Job? = null
    private var lastLoadedEpisodeId: String? = null

    fun monitorChaptersAndTranscripts() {
        scope.launch {
            playerState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collect { episodeId ->
                    android.util.Log.d("PlaybackRepo", "monitorChaptersAndTranscripts: collected episodeId=$episodeId")
                    if (episodeId == null) {
                        lastLoadedEpisodeId = null
                        chaptersAndTranscriptFetchJob?.cancel()
                        playerStateFlow.value =
                            playerStateFlow.value.copy(
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
                                var episode = playerStateFlow.value.currentEpisode
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
                                                    chaptersUrl = "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/$mappedRegion?d=$date$versionParam",
                                                    transcriptUrl = "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/transcript/$mappedRegion?d=$date$versionParam",
                                                )
                                            android.util.Log.d(
                                                "PlaybackRepo",
                                                "monitorChaptersAndTranscripts: updatedEpisode chaptersUrl=${updatedEpisode?.chaptersUrl}, transcriptUrl=${updatedEpisode?.transcriptUrl}",
                                            )
                                            if (updatedEpisode != null) {
                                                val updatedQueue =
                                                    playerStateFlow.value.queue.map {
                                                        if (it.id == episodeId) updatedEpisode else it
                                                    }
                                                playerStateFlow.value =
                                                    playerStateFlow.value.copy(
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
                                        if (enriched != null && playerStateFlow.value.currentEpisode?.id == episodeId) {
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
                                                playerStateFlow.value.queue.map {
                                                    if (it.id == episodeId) updatedEpisode else it
                                                }
                                            playerStateFlow.value =
                                                playerStateFlow.value.copy(
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
                                    playerStateFlow.value =
                                        playerStateFlow.value.copy(
                                            isChaptersLoading = true,
                                            currentChapters = emptyList(),
                                            isChaptersNative = true,
                                            autoChaptersState = AutoTranscriptState.NONE,
                                        )
                                    launch {
                                        val chapters = ChapterRepository.getChapters(chaptersUrl)
                                        if (playerStateFlow.value.currentEpisode?.id == episodeId) {
                                            playerStateFlow.value =
                                                playerStateFlow.value.copy(
                                                    currentChapters = chapters,
                                                    isChaptersLoading = false,
                                                )
                                        }
                                    }
                                } else {
                                    val parsedChapters = ChapterRepository.parseChaptersFromDescription(episode?.description)
                                    if (parsedChapters.isNotEmpty()) {
                                        playerStateFlow.value =
                                            playerStateFlow.value.copy(
                                                currentChapters = parsedChapters,
                                                isChaptersLoading = false,
                                                isChaptersNative = true,
                                                autoChaptersState = AutoTranscriptState.NONE,
                                            )
                                    } else {
                                        val autoChapters = ChapterRepository.getCachedChapters("auto_${episode?.id}") ?: emptyList()
                                        playerStateFlow.value =
                                            playerStateFlow.value.copy(
                                                currentChapters = autoChapters,
                                                isChaptersLoading = false,
                                                isChaptersNative = false,
                                                autoChaptersState = if (autoChapters.isNotEmpty()) AutoTranscriptState.COMPLETED else playerStateFlow.value.autoChaptersState,
                                            )
                                    }
                                }

                                // Fetch transcript
                                if (transcriptUrl != null) {
                                    // RSS transcript available — fetch normally, no AI state
                                    playerStateFlow.value = playerStateFlow.value.copy(autoTranscriptState = AutoTranscriptState.NONE)
                                    launch {
                                        val transcript = TranscriptRepository.getTranscript(transcriptUrl)
                                        if (playerStateFlow.value.currentEpisode?.id == episodeId) {
                                            playerStateFlow.value = playerStateFlow.value.copy(currentTranscript = transcript)
                                        }
                                    }

                                    // But if chaptersUrl is empty, check if we have auto chapters in Turso
                                    if (chaptersUrl.isNullOrEmpty() && episode.audioUrl.isNotEmpty()) {
                                        launch {
                                            val deviceUuid = deviceUuid()
                                            val response =
                                                TranscriptRepository.checkAutoTranscriptStatus(
                                                    api = podcastRepository.api,
                                                    publicKey = podcastRepository.publicKey,
                                                    deviceUuid = deviceUuid,
                                                    episodeId = episodeId,
                                                    audioUrl = episode.audioUrl,
                                                    transcriptUrl = episode.transcriptUrl,
                                                )
                                            if (playerStateFlow.value.currentEpisode?.id != episodeId) return@launch

                                            val status = response?.status
                                            val limitLeft = response?.limitLeft
                                            val chapters = response?.chapters

                                            playerStateFlow.value =
                                                playerStateFlow.value.copy(
                                                    autoTranscriptLimitLeft = limitLeft,
                                                )

                                            if (chapters != null) {
                                                playerStateFlow.value =
                                                    playerStateFlow.value.copy(
                                                        currentChapters = if (playerStateFlow.value.isChaptersNative) playerStateFlow.value.currentChapters else chapters,
                                                        autoChaptersState =
                                                            if (chapters.isNotEmpty() ||
                                                                playerStateFlow.value.isChaptersNative
                                                            ) {
                                                                AutoTranscriptState.COMPLETED
                                                            } else {
                                                                playerStateFlow.value.autoChaptersState
                                                            },
                                                    )
                                            }

                                            when (status) {
                                                "completed" -> {
                                                    playerStateFlow.value =
                                                        playerStateFlow.value.copy(
                                                            autoChaptersState = AutoTranscriptState.COMPLETED,
                                                        )
                                                }
                                                "pending", "uploaded" -> {
                                                    playerStateFlow.value =
                                                        playerStateFlow.value.copy(
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
                                                    playerStateFlow.value =
                                                        playerStateFlow.value.copy(
                                                            autoChaptersState = AutoTranscriptState.FAILED,
                                                        )
                                                }
                                                else -> {
                                                    playerStateFlow.value =
                                                        playerStateFlow.value.copy(
                                                            autoChaptersState = if (playerStateFlow.value.currentChapters.isEmpty()) AutoTranscriptState.NOT_GENERATED else playerStateFlow.value.autoChaptersState,
                                                        )
                                                }
                                            }
                                        }
                                    }
                                } else if (episode != null && episode.audioUrl.isNotEmpty()) {
                                    // No RSS transcript — check auto-transcript status
                                    playerStateFlow.value =
                                        playerStateFlow.value.copy(
                                            autoTranscriptState = AutoTranscriptState.CHECKING,
                                            currentTranscript = emptyList(),
                                        )
                                    launch {
                                        val deviceUuid = deviceUuid()
                                        val response =
                                            TranscriptRepository.checkAutoTranscriptStatus(
                                                api = podcastRepository.api,
                                                publicKey = podcastRepository.publicKey,
                                                deviceUuid = deviceUuid,
                                                episodeId = episodeId,
                                                audioUrl = episode.audioUrl,
                                                transcriptUrl = episode.transcriptUrl,
                                            )
                                        if (playerStateFlow.value.currentEpisode?.id != episodeId) return@launch

                                        val status = response?.status
                                        val limitLeft = response?.limitLeft
                                        val chapters = response?.chapters

                                        playerStateFlow.value =
                                            playerStateFlow.value.copy(
                                                autoTranscriptLimitLeft = limitLeft,
                                            )

                                        if (chapters != null && playerStateFlow.value.currentEpisode?.id == episodeId) {
                                            playerStateFlow.value =
                                                playerStateFlow.value.copy(
                                                    currentChapters = if (playerStateFlow.value.isChaptersNative) playerStateFlow.value.currentChapters else chapters,
                                                    autoChaptersState =
                                                        if (chapters.isNotEmpty() ||
                                                            playerStateFlow.value.isChaptersNative
                                                        ) {
                                                            AutoTranscriptState.COMPLETED
                                                        } else {
                                                            playerStateFlow.value.autoChaptersState
                                                        },
                                                )
                                        }

                                        when (status) {
                                            "completed" -> {
                                                // Transcript exists — fetch the full SRT
                                                playerStateFlow.value =
                                                    playerStateFlow.value.copy(
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
                                                if (playerStateFlow.value.currentEpisode?.id == episodeId) {
                                                    val autoChapters = ChapterRepository.getCachedChapters("auto_$episodeId") ?: emptyList()
                                                    playerStateFlow.value =
                                                        playerStateFlow.value.copy(
                                                            currentTranscript = transcript,
                                                            currentChapters =
                                                                if (playerStateFlow.value.isChaptersNative) {
                                                                    playerStateFlow.value.currentChapters
                                                                } else {
                                                                    (
                                                                        if (autoChapters
                                                                                .isNotEmpty()
                                                                        ) {
                                                                            autoChapters
                                                                        } else {
                                                                            playerStateFlow.value.currentChapters
                                                                        }
                                                                    )
                                                                },
                                                            autoChaptersState =
                                                                if (autoChapters.isNotEmpty() ||
                                                                    playerStateFlow.value.isChaptersNative
                                                                ) {
                                                                    AutoTranscriptState.COMPLETED
                                                                } else {
                                                                    playerStateFlow.value.autoChaptersState
                                                                },
                                                        )
                                                }
                                            }
                                            "pending", "uploaded" -> {
                                                // Already in progress — start polling
                                                val wasTranscriptGenerating =
                                                    playerStateFlow.value.autoTranscriptState == AutoTranscriptState.GENERATING
                                                val wasChaptersGenerating =
                                                    playerStateFlow.value.autoChaptersState == AutoTranscriptState.GENERATING

                                                val nextTranscriptState =
                                                    if (wasTranscriptGenerating) {
                                                        AutoTranscriptState.GENERATING
                                                    } else {
                                                        playerStateFlow.value.autoTranscriptState
                                                    }
                                                val nextChaptersState =
                                                    if (wasChaptersGenerating) {
                                                        AutoTranscriptState.GENERATING
                                                    } else {
                                                        playerStateFlow.value.autoChaptersState
                                                    }

                                                playerStateFlow.value =
                                                    playerStateFlow.value.copy(
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
                                                playerStateFlow.value =
                                                    playerStateFlow.value.copy(
                                                        autoTranscriptState = AutoTranscriptState.FAILED,
                                                    )
                                            }
                                            else -> {
                                                // "not_started" or null — eligible for generation
                                                playerStateFlow.value =
                                                    playerStateFlow.value.copy(
                                                        autoTranscriptState = AutoTranscriptState.NOT_GENERATED,
                                                        autoChaptersState = if (playerStateFlow.value.currentChapters.isEmpty()) AutoTranscriptState.NOT_GENERATED else playerStateFlow.value.autoChaptersState,
                                                    )
                                            }
                                        }
                                    }
                                } else {
                                    playerStateFlow.value =
                                        playerStateFlow.value.copy(
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
        val episode = playerStateFlow.value.currentEpisode ?: return
        if (episode.audioUrl.isEmpty()) return
        val episodeId = episode.id

        val isChaptersEmpty = playerStateFlow.value.currentChapters.isEmpty()
        playerStateFlow.value =
            playerStateFlow.value.copy(
                autoTranscriptState = AutoTranscriptState.GENERATING,
                autoChaptersState = if (isChaptersEmpty) AutoTranscriptState.GENERATING else playerStateFlow.value.autoChaptersState,
            )
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .trackAutoTranscriptRequested(episodeId, episode.podcastId, episode.audioUrl)
        if (isChaptersEmpty) {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackAutoChaptersRequested(episodeId, episode.podcastId, episode.audioUrl)
        }
        startAutoTranscriptGeneration(episodeId, episode.audioUrl, episode.transcriptUrl, isTranscriptRequested = true)
    }

    /**
     * Called from the UI when the user clicks "Generate AI Chapters".
     * Sets only autoChaptersState to GENERATING (not transcript state).
     */
    fun generateAutoChapters() {
        val episode = playerStateFlow.value.currentEpisode ?: return
        if (episode.audioUrl.isEmpty()) return
        val episodeId = episode.id

        playerStateFlow.value =
            playerStateFlow.value.copy(
                autoChaptersState = AutoTranscriptState.GENERATING,
            )
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .trackAutoChaptersRequested(episodeId, episode.podcastId, episode.audioUrl)
        startAutoTranscriptGeneration(episodeId, episode.audioUrl, episode.transcriptUrl, isTranscriptRequested = false)
    }

    /**
     * Starts the background polling/streaming call to generate and retrieve
     * the auto-transcript. Cancels any previous generation job.
     */
    fun startAutoTranscriptGeneration(
        episodeId: String,
        audioUrl: String,
        transcriptUrl: String?,
        isTranscriptRequested: Boolean,
    ) {
        val deviceUuid = deviceUuid()
        autoTranscriptGenerationJob?.cancel()
        autoTranscriptGenerationJob =
            scope.launch {
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
                    val currentEp = playerStateFlow.value.currentEpisode
                    if (currentEp?.id == episodeId) {
                        if (transcript.isNotEmpty()) {
                            val autoChapters = ChapterRepository.getCachedChapters("auto_$episodeId") ?: emptyList()
                            playerStateFlow.value =
                                playerStateFlow.value.copy(
                                    currentTranscript = transcript,
                                    currentChapters =
                                        if (playerStateFlow.value.isChaptersNative) {
                                            playerStateFlow.value.currentChapters
                                        } else {
                                            (
                                                if (autoChapters
                                                        .isNotEmpty()
                                                ) {
                                                    autoChapters
                                                } else {
                                                    playerStateFlow.value.currentChapters
                                                }
                                            )
                                        },
                                    autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.COMPLETED else playerStateFlow.value.autoTranscriptState,
                                    autoChaptersState =
                                        if (autoChapters.isNotEmpty() ||
                                            playerStateFlow.value.isChaptersNative
                                        ) {
                                            AutoTranscriptState.COMPLETED
                                        } else {
                                            AutoTranscriptState.FAILED
                                        },
                                )
                            if (isTranscriptRequested) {
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackAutoTranscriptCompleted(
                                    episodeId,
                                    currentEp.podcastId,
                                    currentEp.duration.toFloat(),
                                    transcript.size,
                                )
                            }
                            if (autoChapters.isNotEmpty()) {
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackAutoChaptersCompleted(
                                    episodeId,
                                    currentEp.podcastId,
                                    currentEp.duration.toFloat(),
                                    autoChapters.size,
                                )
                            } else {
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                                    episodeId,
                                    currentEp.podcastId,
                                    "Chapters empty or generation failed",
                                )
                            }
                        } else {
                            playerStateFlow.value =
                                playerStateFlow.value.copy(
                                    autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.FAILED else playerStateFlow.value.autoTranscriptState,
                                    autoChaptersState = AutoTranscriptState.FAILED,
                                )
                            if (isTranscriptRequested) {
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackAutoTranscriptFailed(
                                    episodeId,
                                    currentEp.podcastId,
                                    "Transcript empty or generation failed",
                                )
                            }
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                                episodeId,
                                currentEp.podcastId,
                                "Transcript empty or generation failed (required for chapters)",
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackRepo", "Auto-transcript generation failed for $episodeId", e)
                    val currentEp = playerStateFlow.value.currentEpisode
                    if (currentEp?.id == episodeId) {
                        playerStateFlow.value =
                            playerStateFlow.value.copy(
                                autoTranscriptState = if (isTranscriptRequested) AutoTranscriptState.FAILED else playerStateFlow.value.autoTranscriptState,
                                autoChaptersState = AutoTranscriptState.FAILED,
                            )
                        if (isTranscriptRequested) {
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackAutoTranscriptFailed(
                                episodeId,
                                currentEp.podcastId,
                                e.message ?: "Unknown error",
                            )
                        }
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackAutoChaptersFailed(
                            episodeId,
                            currentEp.podcastId,
                            e.message ?: "Unknown error",
                        )
                    }
                }
            }
    }
}
