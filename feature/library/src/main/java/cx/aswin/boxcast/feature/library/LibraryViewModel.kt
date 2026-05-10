package cx.aswin.boxcast.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.model.EpisodeStatus
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val subscribedPodcasts: List<Podcast> = emptyList(),
        val likedEpisodes: List<ListeningHistoryEntity> = emptyList(),
        val downloadedEpisodes: List<cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity> = emptyList(),
        val recentHistory: List<ListeningHistoryEntity> = emptyList()
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

class LibraryViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository
) : ViewModel() {

    // Combine subscriptions, liked episodes, downloads, AND listening history
    // so we can enrich each podcast's latestEpisode with play status
    val uiState: StateFlow<LibraryUiState> = combine(
        subscriptionRepository.subscribedPodcasts,
        playbackRepository.likedEpisodes,
        downloadRepository.downloads,
        playbackRepository.getAllHistory()
    ) { podcasts, liked, downloads, allHistory ->
        // Enrich podcasts with episode status from listening history
        val enrichedPodcasts = podcasts.map { podcast ->
            val episode = podcast.latestEpisode ?: return@map podcast
            val history = allHistory.find { it.episodeId == episode.id }

            when {
                // Never touched → UNPLAYED
                history == null || (history.progressMs == 0L && !history.isCompleted) -> {
                    podcast.copy(episodeStatus = EpisodeStatus.UNPLAYED)
                }
                // Started but not finished → IN_PROGRESS
                !history.isCompleted && history.progressMs > 0L -> {
                    val progress = if (history.durationMs > 0)
                        (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
                    else 0f
                    podcast.copy(
                        resumeProgress = progress,
                        episodeStatus = EpisodeStatus.IN_PROGRESS
                    )
                }
                // Completed
                history.isCompleted -> {
                    podcast.copy(
                        resumeProgress = 1f,
                        episodeStatus = EpisodeStatus.COMPLETED
                    )
                }
                else -> podcast
            }
        }

        LibraryUiState.Success(
            subscribedPodcasts = enrichedPodcasts,
            likedEpisodes = liked,
            downloadedEpisodes = downloads,
            recentHistory = allHistory.take(3)
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading
        )

    // ── Telemetry State & Lifecycle ──

    // Shared session timer (since distinct ViewModel instances are created per route)
    var sessionStartTime: Long = 0L
    private var hasTrackedExit = false

    // Hub State
    var hubNavigatedTo: String? = null

    // Subscriptions State
    var subTabSwitchesCount = 0
    var subDidSearch = false
    var subFinalSearchQuery: String? = null
    var subPodcastsClickedCount = 0
    var subEpisodesClickedCount = 0

    // Liked / Downloads State
    var genericEpisodesClickedCount = 0
    var genericItemsRemovedCount = 0

    fun onScreenResume() {
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackHubExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryHubSession(timeSpent, hubNavigatedTo)
        hasTrackedExit = true
        sessionStartTime = 0L
    }

    fun trackSubscriptionsExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsSession(
            timeSpentSeconds = timeSpent,
            tabSwitchesCount = subTabSwitchesCount,
            didSearch = subDidSearch,
            finalSearchQuery = subFinalSearchQuery,
            podcastsClickedCount = subPodcastsClickedCount,
            episodesClickedCount = subEpisodesClickedCount
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }

    fun trackLikedExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryLikedSession(
            timeSpentSeconds = timeSpent,
            episodesClickedCount = genericEpisodesClickedCount,
            episodesUnlikedCount = genericItemsRemovedCount
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }

    fun trackDownloadsExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryDownloadsSession(
            timeSpentSeconds = timeSpent,
            episodesClickedCount = genericEpisodesClickedCount,
            episodesDeletedCount = genericItemsRemovedCount
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }
}
