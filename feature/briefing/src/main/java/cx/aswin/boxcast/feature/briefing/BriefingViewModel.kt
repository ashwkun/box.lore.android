package cx.aswin.boxcast.feature.briefing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.model.Briefing
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
    initialRegion: String? = null
) : AndroidViewModel(application) {

    private val _selectedRegion = MutableStateFlow(initialRegion ?: "in")
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    private val _briefingState = MutableStateFlow<Briefing?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow<BriefingUiState>(BriefingUiState.Loading)
    val uiState: StateFlow<BriefingUiState> = _uiState.asStateFlow()

    init {
        // Observe region changes and load data
        viewModelScope.launch {
            _selectedRegion.collect { region ->
                loadBriefing(region)
            }
        }

        // Combine network briefing state with local media playback state
        viewModelScope.launch {
            combine(
                _briefingState,
                _isLoading,
                _error,
                _selectedRegion,
                playbackRepository.playerState
            ) { briefing, isLoading, error, region, playerState ->
                when {
                    isLoading -> BriefingUiState.Loading
                    error != null -> BriefingUiState.Error(error, region)
                    briefing != null -> {
                        val briefingEpisodeId = getBriefingEpisodeId(briefing)
                        val isCurrentBriefing = playerState.currentEpisode?.id == briefingEpisodeId
                        
                        BriefingUiState.Success(
                            briefing = briefing,
                            selectedRegion = region,
                            isPlaying = isCurrentBriefing && playerState.isPlaying,
                            currentPosition = if (isCurrentBriefing) playerState.position else 0L,
                            duration = if (isCurrentBriefing) playerState.duration else 0L,
                            isBuffering = isCurrentBriefing && playerState.isLoading
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
            val result = podcastRepository.getBriefingMetadata(region)
            if (result != null) {
                _briefingState.value = result
            } else {
                _error.value = "Failed to load briefing for ${region.uppercase()}. Please check your connection."
            }
            _isLoading.value = false
        }
    }

    fun playBriefing(briefing: Briefing) {
        viewModelScope.launch {
            val dummyPodcast = Podcast(
                id = "briefing_${briefing.region}",
                title = "The Boxcast Brief",
                artist = "BoxCast AI",
                imageUrl = briefing.coverUrl
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
                imageUrl = briefing.coverUrl,
                podcastId = dummyPodcast.id,
                podcastTitle = dummyPodcast.title,
                podcastImageUrl = dummyPodcast.imageUrl,
                podcastArtist = dummyPodcast.artist,
                duration = 180,
                publishedDate = publishedDate,
                transcriptUrl = "https://api.aswin.cx/briefings/transcript/${briefing.region}?d=${briefing.date}$versionParam",
                chaptersUrl = "https://api.aswin.cx/briefings/chapters/${briefing.region}?d=${briefing.date}$versionParam"
            )

            playbackRepository.playEpisode(dummyEpisode, dummyPodcast)
        }
    }

    fun togglePlayPause(briefing: Briefing) {
        val state = _uiState.value
        if (state is BriefingUiState.Success) {
            val briefingEpisodeId = getBriefingEpisodeId(briefing)
            val isCurrentBriefing = playbackRepository.playerState.value.currentEpisode?.id == briefingEpisodeId
            
            if (isCurrentBriefing) {
                if (playbackRepository.playerState.value.isPlaying) {
                    playbackRepository.pause()
                } else {
                    playbackRepository.resume()
                }
            } else {
                playBriefing(briefing)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playbackRepository.seekTo(positionMs)
    }

    private fun getBriefingEpisodeId(briefing: Briefing): String {
        return "briefing_${briefing.region}_${briefing.date}"
    }
}
