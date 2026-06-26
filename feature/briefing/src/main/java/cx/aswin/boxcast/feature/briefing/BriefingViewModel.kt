package cx.aswin.boxcast.feature.briefing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.ChapterRepository
import cx.aswin.boxcast.core.model.Briefing
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BriefingViewModel(
    application: Application,
    private val podcastRepository: PodcastRepository,
    private val playbackRepository: PlaybackRepository,
    private val queueManager: cx.aswin.boxcast.core.data.QueueManager,
    initialRegion: String? = null
) : AndroidViewModel(application) {

    private val _selectedRegion = MutableStateFlow(initialRegion ?: "in")
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    private val _briefingState = MutableStateFlow<Briefing?>(null)
    private val _briefingChapters = MutableStateFlow<List<Chapter>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _savedSession = MutableStateFlow<cx.aswin.boxcast.core.data.PlaybackSession?>(null)

    private val _uiState = MutableStateFlow<BriefingUiState>(BriefingUiState.Loading)
    val uiState: StateFlow<BriefingUiState> = _uiState.asStateFlow()

    init {
        // Observe region changes and load data
        viewModelScope.launch {
            _selectedRegion.collect { region ->
                loadBriefing(region)
            }
        }

        // Keep _savedSession updated in real-time when the current briefing is playing
        viewModelScope.launch {
            playbackRepository.playerState.collect { playerState ->
                val briefing = _briefingState.value
                if (briefing != null) {
                    val briefingEpisodeId = getBriefingEpisodeId(briefing)
                    if (playerState.currentEpisode?.id == briefingEpisodeId) {
                        _savedSession.value = cx.aswin.boxcast.core.data.PlaybackSession(
                            podcastId = "briefing_${briefing.region}",
                            episodeId = briefingEpisodeId,
                            positionMs = playerState.position,
                            durationMs = playerState.duration,
                            timestamp = System.currentTimeMillis(),
                            episodeTitle = briefing.title,
                            podcastTitle = "The Boxlore Brief",
                            imageUrl = getBriefingCoverUrl(briefing.region),
                            podcastImageUrl = getBriefingCoverUrl(briefing.region),
                            audioUrl = briefing.audioUrl,
                            enclosureType = "audio/mpeg"
                        )
                    }
                }
            }
        }

        // Combine network briefing state with local media playback state
        val briefingSubState = combine(
            _briefingState,
            _isLoading,
            _error,
            _selectedRegion,
            _briefingChapters
        ) { briefing, isLoading, error, region, chapters ->
            BriefingSubState(briefing, isLoading, error, region, chapters)
        }

        viewModelScope.launch {
            combine(
                briefingSubState,
                playbackRepository.playerState,
                _savedSession
            ) { subState, playerState, savedSession ->
                val briefing = subState.briefing
                val isLoading = subState.isLoading
                val error = subState.error
                val region = subState.region
                val chapters = subState.chapters

                when {
                    isLoading -> BriefingUiState.Loading
                    error != null -> BriefingUiState.Error(error, region)
                    briefing != null -> {
                        val briefingEpisodeId = getBriefingEpisodeId(briefing)
                        val isCurrentBriefing = playerState.currentEpisode?.id == briefingEpisodeId
                        
                        val currentPos = if (isCurrentBriefing) playerState.position else (savedSession?.positionMs ?: 0L)
                        val dur = if (isCurrentBriefing) playerState.duration else (savedSession?.durationMs ?: 0L)

                        BriefingUiState.Success(
                            briefing = briefing,
                            selectedRegion = region,
                            isPlaying = isCurrentBriefing && playerState.isPlaying,
                            currentPosition = currentPos,
                            duration = dur,
                            isBuffering = isCurrentBriefing && playerState.isLoading,
                            chapters = chapters
                        )
                    }
                    else -> BriefingUiState.Error("Unknown error", region)
                }
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun selectRegion(region: String) {
        _selectedRegion.value = region
    }

    fun loadBriefing(region: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _briefingChapters.value = emptyList()
            _savedSession.value = null
            val result = podcastRepository.getBriefingMetadata(region)
            if (result != null) {
                _briefingState.value = result
                
                // Fetch saved session progress
                val briefingEpisodeId = getBriefingEpisodeId(result)
                _savedSession.value = playbackRepository.getSession(briefingEpisodeId)

                // Fetch chapters asynchronously
                val audioUri = android.net.Uri.parse(result.audioUrl)
                val version = audioUri.getQueryParameter("v")
                val versionParam = if (version != null) "&v=$version" else ""
                val chaptersUrl = "https://api.aswin.cx/briefings/chapters/${result.region}?d=${result.date}$versionParam"
                try {
                    val chaptersList = ChapterRepository.getChapters(chaptersUrl)
                    _briefingChapters.value = chaptersList
                } catch (e: Exception) {
                    _briefingChapters.value = emptyList()
                }
            } else {
                _error.value = "Failed to load briefing for ${region.uppercase()}. Please check your connection."
            }
            _isLoading.value = false
        }
    }

    fun playBriefing(briefing: Briefing, initialPositionMs: Long? = null) {
        viewModelScope.launch {
            val dummyPodcast = Podcast(
                id = "briefing_${briefing.region}",
                title = "The Boxlore Brief",
                artist = "BoxCast AI",
                imageUrl = getBriefingCoverUrl(briefing.region)
            )

            val publishedDate = try {
                java.time.LocalDate.parse(briefing.date)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toEpochSecond()
            } catch (e: Exception) {
                System.currentTimeMillis() / 1000
            }

            val audioUri = android.net.Uri.parse(briefing.audioUrl)
            val version = audioUri.getQueryParameter("v")
            val versionParam = if (version != null) "&v=$version" else ""

            val descriptionHtml = buildString {
                append("<p>Your daily AI-generated news briefing for ${briefing.region.uppercase()}.</p>")
                if (briefing.script.isNotEmpty()) {
                    append("<br/>")
                    briefing.script.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                        append("<p>").append(paragraph.replace("\n", "<br/>")).append("</p>")
                    }
                }
                if (briefing.sources.isNotEmpty()) {
                    append("<br/><br/><h3>References & Sources</h3><ul>")
                    briefing.sources.forEach { source ->
                        append("<li><a href=\"").append(source.url).append("\">")
                            .append(source.title).append("</a></li>")
                    }
                    append("</ul>")
                }
            }

            val dummyEpisode = Episode(
                id = getBriefingEpisodeId(briefing),
                title = briefing.title,
                description = descriptionHtml,
                audioUrl = briefing.audioUrl,
                imageUrl = getBriefingCoverUrl(briefing.region),
                podcastId = dummyPodcast.id,
                podcastTitle = dummyPodcast.title,
                podcastImageUrl = dummyPodcast.imageUrl,
                podcastArtist = dummyPodcast.artist,
                duration = 180,
                publishedDate = publishedDate,
                transcriptUrl = "https://api.aswin.cx/briefings/transcript/${briefing.region}?d=${briefing.date}$versionParam",
                chaptersUrl = "https://api.aswin.cx/briefings/chapters/${briefing.region}?d=${briefing.date}$versionParam"
            )

            queueManager.playEpisode(dummyEpisode, dummyPodcast, initialPositionMs = initialPositionMs)
        }
    }

    fun togglePlayPause(briefing: Briefing, initialPositionMs: Long? = null) {
        val state = _uiState.value
        if (state is BriefingUiState.Success) {
            val briefingEpisodeId = getBriefingEpisodeId(briefing)
            val isCurrentBriefing = playbackRepository.playerState.value.currentEpisode?.id == briefingEpisodeId
            
            if (isCurrentBriefing) {
                if (playbackRepository.playerState.value.isPlaying) {
                    playbackRepository.pause()
                } else {
                    if (initialPositionMs != null) {
                        playbackRepository.seekTo(initialPositionMs)
                    }
                    playbackRepository.resume()
                }
            } else {
                playBriefing(briefing, initialPositionMs)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playbackRepository.seekTo(positionMs)
    }

    private fun getBriefingEpisodeId(briefing: Briefing): String {
        return "briefing_${briefing.region}_${briefing.date}"
    }

    private fun getBriefingCoverUrl(region: String): String {
        val packageName = getApplication<Application>().packageName
        val resId = when (region.lowercase()) {
            "in", "ind" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_india
            "uk", "gb" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_uk
            "us", "usa" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_usa
            else -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_global
        }
        return "android.resource://$packageName/$resId"
    }
}

private data class BriefingSubState(
    val briefing: Briefing?,
    val isLoading: Boolean,
    val error: String?,
    val region: String,
    val chapters: List<Chapter>
)
