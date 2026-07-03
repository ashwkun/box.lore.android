package cx.aswin.boxcast.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.model.EpisodeStatus
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.data.PodcastScoring
import cx.aswin.boxcast.core.data.toScorable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

enum class SubscriptionSort { SmartRank, RecentlyUpdated, Alphabetical, MostListened }
enum class DownloadsSortOrder { RECENT, NAME, SIZE, COUNT }
enum class ShowSortOrder { NEWEST, OLDEST, LARGEST }

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val subscribedPodcasts: List<Podcast> = emptyList(),
        val likedEpisodes: List<ListeningHistoryEntity> = emptyList(),
        val downloadedEpisodes: List<cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity> = emptyList(),
        val recentHistory: List<ListeningHistoryEntity> = emptyList(),
        val currentSort: SubscriptionSort = SubscriptionSort.SmartRank,
        val allHistory: List<ListeningHistoryEntity> = emptyList()
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

class LibraryViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    private val userPreferencesRepository: cx.aswin.boxcast.core.data.UserPreferencesRepository
) : ViewModel() {

    val lastSeenEpisodes: StateFlow<Map<String, String>> = userPreferencesRepository.lastSeenEpisodesStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    private val _downloadsSortOrder = MutableStateFlow(DownloadsSortOrder.RECENT)
    val downloadsSortOrder = _downloadsSortOrder.asStateFlow()

    private val _showSortOrder = MutableStateFlow(ShowSortOrder.NEWEST)
    val showSortOrder = _showSortOrder.asStateFlow()

    fun setDownloadsSortOrder(sortOrder: DownloadsSortOrder) {
        _downloadsSortOrder.value = sortOrder
    }

    fun setShowSortOrder(sortOrder: ShowSortOrder) {
        _showSortOrder.value = sortOrder
    }

    private val subscriptionSort = userPreferencesRepository.subscriptionSortStream
        .map { sortName ->
            try {
                SubscriptionSort.valueOf(sortName)
            } catch (e: Exception) {
                SubscriptionSort.SmartRank
            }
        }

    fun setSubscriptionSort(sort: SubscriptionSort) {
        viewModelScope.launch {
            userPreferencesRepository.setSubscriptionSort(sort.name)
        }
    }

    val useSmartRank: StateFlow<Boolean> = userPreferencesRepository.latestEpisodesSortUseSmartStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    fun setUseSmartRank(useSmart: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setLatestEpisodesSortUseSmart(useSmart)
        }
    }

    val hideCompletedInSubs: StateFlow<Boolean> = userPreferencesRepository.hideCompletedInSubsStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    // Combine subscriptions, liked episodes, downloads, AND listening history
    // so we can enrich each podcast's latestEpisode with play status
    val uiState: StateFlow<LibraryUiState> = combine(
        subscriptionRepository.subscribedPodcasts,
        playbackRepository.likedEpisodes,
        downloadRepository.downloads,
        playbackRepository.getAllHistory(),
        subscriptionSort
    ) { podcasts: List<Podcast>, liked: List<ListeningHistoryEntity>, downloads: List<cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity>, allHistory: List<ListeningHistoryEntity>, sort: SubscriptionSort ->
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

        // Apply sorting
        val sortedPodcasts = when (sort) {
            SubscriptionSort.SmartRank -> {
                val podScoresMap = PodcastScoring.calculateScores(
                    podcasts = enrichedPodcasts.map { it.toScorable() },
                    allHistory = allHistory
                )

                enrichedPodcasts.map { pod ->
                    pod to (podScoresMap[pod.id] ?: 0.0)
                }.sortedWith(
                    compareByDescending<Pair<Podcast, Double>> { it.second }
                        .thenBy { it.first.title }
                ).map { it.first }
            }
            SubscriptionSort.RecentlyUpdated -> {
                enrichedPodcasts.sortedByDescending { it.latestEpisode?.publishedDate ?: 0L }
            }
            SubscriptionSort.Alphabetical -> {
                enrichedPodcasts.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
            SubscriptionSort.MostListened -> {
                val historyCounts = allHistory.groupBy { it.podcastId }.mapValues { it.value.size }
                enrichedPodcasts.sortedByDescending { historyCounts[it.id] ?: 0 }
            }
        }

        LibraryUiState.Success(
            subscribedPodcasts = sortedPodcasts,
            likedEpisodes = liked,
            downloadedEpisodes = downloads,
            recentHistory = allHistory.filter { !it.isManualCompletion && !it.isBulkCompletion }.take(3),
            currentSort = sort,
            allHistory = allHistory
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
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

    fun removeDownload(episodeId: String) {
        viewModelScope.launch {
            genericItemsRemovedCount++
            downloadRepository.removeDownload(episodeId)
        }
    }

    fun removeMultipleDownloads(episodeIds: List<String>) {
        viewModelScope.launch {
            genericItemsRemovedCount += episodeIds.size
            episodeIds.forEach { id ->
                downloadRepository.removeDownload(id)
            }
        }
    }

    fun playEpisode(episode: Episode, podcast: Podcast) {
        viewModelScope.launch {
            playbackRepository.playEpisode(episode, podcast)
        }
    }

    fun addToQueue(episode: Episode, podcast: Podcast) {
        viewModelScope.launch {
            playbackRepository.addToQueue(episode, podcast)
        }
    }

    fun addToQueueNext(episode: Episode, podcast: Podcast) {
        viewModelScope.launch {
            playbackRepository.addToQueueNext(episode, podcast)
        }
    }

    fun playQueue(episodes: List<Episode>, podcast: Podcast) {
        viewModelScope.launch {
            playbackRepository.playQueue(episodes, podcast)
        }
    }
}
