package cx.aswin.boxlore.feature.info

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Suppress("kotlin:S6310")
class PodcastInfoViewModel(
    application: Application,
    private val repository: PodcastRepository,
    private val playbackRepository: cx.aswin.boxlore.core.playback.PlaybackRepository,
    private val downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository,
    private val queueManager: cx.aswin.boxlore.core.playback.QueueManager,
    private val subscriptionRepository: cx.aswin.boxlore.core.data.SubscriptionRepository,
    private val rssRepository: cx.aswin.boxlore.core.rss.RssPodcastRepository,
    private val localCatalog: LocalCatalogPort,
    private val userPreferencesRepository: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    private val entryPoint: String?,
    private val genreFilter: String?,
    private val scrollDepth: Int?,
    private val searchQuery: String?,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Loading)

    private var currentPodcastId: String = ""
    private val _currentPodcastIdFlow = MutableStateFlow("")
    private var currentOffset: Int = 0
    private var searchJob: Job? = null

    // --- Tracking State ---
    private var sessionStartTime = System.currentTimeMillis()
    private var wasSubscribedAtStart: Boolean? = null
    private var didSubscribe = false
    private var didUnsubscribe = false
    private var didSearch = false
    private var didSortEpisodes = false
    private val playedEpisodes = mutableSetOf<String>()
    private val clickedEpisodes = mutableSetOf<String>()
    private var hasTrackedExit = false

    // Observe liked episodes
    private val likedEpisodeIds =
        playbackRepository.likedEpisodes
            .map { historyList -> historyList.map { it.episodeId }.toSet() }
            .stateIn(
                scope = viewModelScope,
                started =
                    kotlinx.coroutines.flow.SharingStarted
                        .WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    fun onToggleLike(episode: Episode) {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                playbackRepository.toggleLike(
                    episode = episode,
                    podcastId = currentState.podcast.id,
                    podcastTitle = currentState.podcast.title,
                    podcastImageUrl = currentState.podcast.imageUrl,
                )
            }
        }
    }

    // Check if an episode is liked (helper for UI)
    fun isEpisodeLiked(episodeId: String): Boolean = likedEpisodeIds.value.contains(episodeId)

    // Expose flow for UI to collect
    val likedEpisodesState = likedEpisodeIds

    // Observe completed episodes
    private val completedEpisodeIds =
        playbackRepository.completedEpisodeIds
            .stateIn(
                scope = viewModelScope,
                started =
                    kotlinx.coroutines.flow.SharingStarted
                        .WhileSubscribed(5_000),
                initialValue = emptySet(),
            )
    val completedEpisodesState: StateFlow<Set<String>> = completedEpisodeIds

    private val userPrefs = userPreferencesRepository

    val globalSkipBeginningMs: StateFlow<Long> =
        userPrefs.skipBeginningMsStream
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                cx.aswin.boxlore.core.playback.PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS,
            )
    val globalSkipEndingMs: StateFlow<Long> =
        userPrefs.skipEndingMsStream
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                cx.aswin.boxlore.core.playback.PlaybackSkipPolicy.DEFAULT_SKIP_ENDING_MS,
            )

    val hideCompletedInShowDetails: StateFlow<Boolean> =
        userPrefs.hideCompletedInShowDetailsStream
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    fun setUseAppPlaybackDefaults(useDefaults: Boolean) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (!state.isSubscribed) return
        val beginning = if (useDefaults) null else globalSkipBeginningMs.value
        val ending = if (useDefaults) null else globalSkipEndingMs.value
        updatePodcastPlaybackOverrides(beginning, ending)
    }

    fun setSkipBeginningOverride(valueMs: Long) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (!state.isSubscribed) return
        updatePodcastPlaybackOverrides(
            beginningMs =
                cx.aswin.boxlore.core.playback.PlaybackSkipPolicy
                    .sanitizeTrim(valueMs),
            endingMs = state.podcast.skipEndingOverrideMs ?: globalSkipEndingMs.value,
        )
    }

    fun setSkipEndingOverride(valueMs: Long) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (!state.isSubscribed) return
        updatePodcastPlaybackOverrides(
            beginningMs = state.podcast.skipBeginningOverrideMs ?: globalSkipBeginningMs.value,
            endingMs =
                cx.aswin.boxlore.core.playback.PlaybackSkipPolicy
                    .sanitizeTrim(valueMs),
        )
    }

    private fun updatePodcastPlaybackOverrides(
        beginningMs: Long?,
        endingMs: Long?,
    ) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        viewModelScope.launch {
            subscriptionRepository.setPlaybackSkipOverrides(
                state.podcast.id,
                beginningMs,
                endingMs,
            )
            val latest = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
            if (latest.podcast.id == state.podcast.id) {
                _uiState.value =
                    latest.copy(
                        podcast =
                            latest.podcast.copy(
                                skipBeginningOverrideMs = beginningMs,
                                skipEndingOverrideMs = endingMs,
                            ),
                    )
            }
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackSettingsInteraction(
                "podcast_playback_override_changed",
                "${state.podcast.id}:${beginningMs ?: "default"}:${endingMs ?: "default"}",
            )
        }
    }

    fun toggleHideCompleted() {
        viewModelScope.launch {
            userPrefs.setHideCompletedInShowDetails(!hideCompletedInShowDetails.value)
        }
    }

    val uiState: StateFlow<PodcastInfoUiState> = _uiState.asStateFlow()

    // Observe downloaded episode IDs
    val downloadedEpisodeIds: StateFlow<Set<String>> =
        downloadRepository.completedDownloadIds
            .stateIn(
                scope = viewModelScope,
                started =
                    kotlinx.coroutines.flow.SharingStarted
                        .WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    // Observe downloading episode IDs
    val downloadingEpisodeIds: StateFlow<Set<String>> =
        downloadRepository.downloadingEpisodeIds
            .stateIn(
                scope = viewModelScope,
                started =
                    kotlinx.coroutines.flow.SharingStarted
                        .WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    fun isEpisodeCompleted(episodeId: String): Boolean = completedEpisodeIds.value.contains(episodeId)

    fun onToggleCompletion(episode: Episode) {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                playbackRepository.toggleCompletion(
                    episode = episode,
                    podcastId = currentState.podcast.id,
                    podcastTitle = currentState.podcast.title,
                    podcastImageUrl = currentState.podcast.imageUrl,
                )
            }
        }
    }

    fun markAllAsCompleted() {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val allEpisodes =
                    try {
                        repository.getEpisodes(currentState.podcast.id)
                    } catch (e: Exception) {
                        emptyList()
                    }
                val targetEpisodes = if (allEpisodes.isNotEmpty()) allEpisodes else currentState.episodes
                playbackRepository.markAllEpisodesCompleted(
                    episodes = targetEpisodes,
                    podcastId = currentState.podcast.id,
                    podcastTitle = currentState.podcast.title,
                    podcastImageUrl = currentState.podcast.imageUrl,
                )
            }
        }
    }

    fun markAllAsUncompleted() {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val allEpisodes =
                    try {
                        repository.getEpisodes(currentState.podcast.id)
                    } catch (e: Exception) {
                        emptyList()
                    }
                val targetEpisodes = if (allEpisodes.isNotEmpty()) allEpisodes else currentState.episodes
                playbackRepository.markAllEpisodesUncompleted(
                    episodes = targetEpisodes,
                )
            }
        }
    }

    fun toggleDownload(episode: Episode) {
        val currentState = _uiState.value
        android.util.Log.d("PodcastInfoVM", "toggleDownload: title=${episode.title}, state=$currentState")
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val isDownloaded = downloadRepository.isDownloaded(episode.id).first()
                val isDownloading = downloadRepository.isDownloading(episode.id).first()
                android.util.Log.d("PodcastInfoVM", "toggleDownload check: downloaded=$isDownloaded, downloading=$isDownloading")
                if (isDownloaded || isDownloading) {
                    android.util.Log.d("PodcastInfoVM", "Removing download")
                    downloadRepository.removeDownload(episode.id)
                } else {
                    android.util.Log.d("PodcastInfoVM", "Adding download")
                    downloadRepository.addDownload(episode, currentState.podcast)
                }
            }
        } else {
            android.util.Log.w("PodcastInfoVM", "toggleDownload ignored, state is not Success")
        }
    }

    fun isDownloaded(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> = downloadRepository.isDownloaded(episodeId)

    fun isDownloading(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> =
        downloadRepository
            .isDownloading(episodeId)
            .map { isDownloading ->
                android.util.Log.d("PodcastInfoVM", "isDownloading($episodeId): $isDownloading")
                isDownloading
            }

    companion object {
        private const val TAG = "PodcastInfoViewModel"
        private const val PAGE_SIZE = 20
        private const val SEARCH_DEBOUNCE_MS = 500L
    }

    fun loadPodcast(podcastId: String) {
        if (currentPodcastId == podcastId && (_uiState.value is PodcastInfoUiState.Success || _uiState.value is PodcastInfoUiState.Error)) {
            return
        }

        currentPodcastId = podcastId
        _currentPodcastIdFlow.value = podcastId
        currentOffset = 0
        viewModelScope.launch {
            val linkedRss = localCatalog.getSubscribedRssLinkedTo(podcastId)
            val effectivePodcastId = linkedRss?.id ?: podcastId
            if (effectivePodcastId != podcastId) {
                currentPodcastId = effectivePodcastId
                _currentPodcastIdFlow.value = effectivePodcastId
            }
            val localPodcast = linkedRss ?: localCatalog.getLocalPodcast(effectivePodcastId)
            val isSubscribed = subscriptionRepository.isSubscribed(effectivePodcastId)
            var currentPodcast = localPodcast

            if (currentPodcast != null) {
                if (wasSubscribedAtStart == null) {
                    wasSubscribedAtStart = isSubscribed
                }
                _uiState.value =
                    PodcastInfoUiState.Success(
                        podcast = currentPodcast,
                        episodes = emptyList(),
                        isSubscribed = isSubscribed,
                        hasMoreEpisodes = true,
                        isLoadingMore = true,
                    )
                if (isSubscribed) {
                    currentPodcast.let { podcast ->
                        podcast.latestEpisode?.id?.let { episodeId ->
                            launch {
                                userPrefs.setLastSeenEpisodeId(podcast.id, episodeId)
                                subscriptionRepository.clearRssNewEpisodesFlag(podcast.id)
                            }
                        }
                    }
                }
            } else {
                _uiState.value = PodcastInfoUiState.Loading
            }

            try {
                val initialType = currentPodcast?.type ?: "episodic"
                val initialSort =
                    cx.aswin.boxlore.feature.info.logic.PodcastInfoSortLogic.resolveInitialSort(
                        localPodcast?.preferredSort,
                        initialType,
                    )
                val limit = if (initialSort == EpisodeSort.OLDEST) 200 else PAGE_SIZE
                val sortParam = if (initialSort == EpisodeSort.OLDEST) "oldest" else "newest"

                if (currentPodcast?.isRss == true) {
                    val cachedPage =
                        repository.getEpisodesPaginated(effectivePodcastId, limit, 0, sortParam)
                    currentOffset = cachedPage.episodes.size
                    _uiState.value =
                        PodcastInfoUiState.Success(
                            podcast = currentPodcast,
                            episodes = cachedPage.episodes,
                            isSubscribed = isSubscribed,
                            hasMoreEpisodes = cachedPage.hasMore,
                            currentSort = initialSort,
                            isLoadingMore = true,
                        )
                    // Conditional: cheap HEAD check for validator-capable feeds, interval-gated
                    // otherwise, so opening a show doesn't re-download unchanged feeds. Pull-to-
                    // refresh calls the forced refreshCatalog path instead.
                    rssRepository.refreshCatalogIfNeeded(effectivePodcastId)
                }

                val (apiPodcast, page) =
                    fetchPodcastAndEpisodes(effectivePodcastId, limit, sortParam)

                if (apiPodcast != null) {
                    val apiPodcastWithFallback =
                        cx.aswin.boxlore.feature.info.logic.PodcastInfoEnrichLogic.enrichPodcastWithFallback(
                            apiPodcast = apiPodcast,
                            currentPodcast = currentPodcast,
                            localPodcast = localPodcast,
                            pageEpisodes = page.episodes,
                            sortParam = sortParam,
                        )
                    currentPodcast = apiPodcastWithFallback
                    currentPodcastId = apiPodcastWithFallback.id
                    _currentPodcastIdFlow.value = apiPodcastWithFallback.id

                    trackScreenViewed(apiPodcastWithFallback.id, apiPodcastWithFallback.title)

                    if (wasSubscribedAtStart == null) {
                        wasSubscribedAtStart = isSubscribed
                    }

                    currentOffset = page.episodes.size
                    _uiState.value =
                        PodcastInfoUiState.Success(
                            podcast = apiPodcastWithFallback,
                            episodes = page.episodes,
                            isSubscribed = isSubscribed,
                            hasMoreEpisodes = page.hasMore,
                            currentSort = initialSort,
                            isLoadingMore = false,
                        )

                    if (isSubscribed) {
                        apiPodcastWithFallback.latestEpisode?.id?.let { episodeId ->
                            launch {
                                userPrefs.setLastSeenEpisodeId(apiPodcastWithFallback.id, episodeId)
                                subscriptionRepository.clearRssNewEpisodesFlag(apiPodcastWithFallback.id)
                            }
                        }
                    }

                    if (!apiPodcastWithFallback.isRss) {
                        launch {
                            fetchAndApplyPodcastMeta(
                                effectivePodcastId,
                                apiPodcast.id,
                                isSubscribed,
                                localPodcast,
                            )
                        }
                    }
                } else {
                    if (currentPodcast == null) {
                        trackScreenViewed(effectivePodcastId, null)
                        _uiState.value = PodcastInfoUiState.Error
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load podcast $effectivePodcastId", e)
                if (currentPodcast == null) {
                    trackScreenViewed(effectivePodcastId, null)
                    _uiState.value = PodcastInfoUiState.Error
                } else {
                    // We already showed a Success state (with isLoadingMore = true) further up;
                    // make sure a failed refresh doesn't leave it stuck loading forever.
                    val latestState = _uiState.value as? PodcastInfoUiState.Success
                    if (latestState != null) {
                        _uiState.value = latestState.copy(isLoadingMore = false, isRssRefreshing = false)
                    }
                }
            }
        }
    }

    private suspend fun fetchPodcastAndEpisodes(
        podcastId: String,
        limit: Int,
        sortParam: String,
    ): Pair<Podcast?, cx.aswin.boxlore.core.data.PodcastRepository.EpisodePage> =
        kotlinx.coroutines.coroutineScope {
            val apiPodcast: Podcast?
            val page: cx.aswin.boxlore.core.data.PodcastRepository.EpisodePage

            if (podcastId.startsWith("url:") || podcastId.startsWith("guid:")) {
                apiPodcast = repository.getPodcastDetails(podcastId)
                if (apiPodcast != null) {
                    val realId = apiPodcast.id
                    val episodesDeferred = async { repository.getEpisodesPaginated(realId, limit, 0, sortParam) }
                    page = episodesDeferred.await()
                } else {
                    page =
                        cx.aswin.boxlore.core.data.PodcastRepository
                            .EpisodePage(emptyList(), false)
                }
            } else {
                val podcastDeferred = async { repository.getPodcastDetails(podcastId) }
                val episodesDeferred = async { repository.getEpisodesPaginated(podcastId, limit, 0, sortParam) }
                apiPodcast = podcastDeferred.await()
                page = episodesDeferred.await()
            }
            Pair(apiPodcast, page)
        }

    private fun trackScreenViewed(
        podcastId: String,
        podcastName: String?,
    ) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPodcastInfoScreenViewed(
            podcastId = podcastId,
            podcastName = podcastName,
            entryPoint = entryPoint,
            genreFilter = genreFilter,
            scrollDepth = scrollDepth,
            searchQuery = searchQuery,
        )
    }

    private suspend fun fetchAndApplyPodcastMeta(
        podcastId: String,
        apiPodcastId: String,
        isSubscribed: Boolean,
        localPodcast: Podcast?,
    ) {
        try {
            val meta = repository.getPodcastMeta(podcastId)
            if (meta != null) {
                val state = _uiState.value
                if (state is PodcastInfoUiState.Success && (state.podcast.id == podcastId || state.podcast.id == apiPodcastId)) {
                    val enrichedPodcast =
                        state.podcast.copy(
                            location = meta.location,
                            license = meta.license,
                            isLocked = meta.locked == 1,
                            updateFrequency = meta.updateFrequency,
                            podroll =
                                meta.podroll?.map {
                                    cx.aswin.boxlore.core.model.PodrollItem(
                                        title = it.title,
                                        url = it.url,
                                        uuid = it.uuid,
                                    )
                                },
                        )
                    _uiState.value = state.copy(podcast = enrichedPodcast)

                    if (isSubscribed) {
                        val preferredSortVal = localPodcast?.preferredSort ?: "newest"
                        val typeVal = if (preferredSortVal == "oldest") "serial" else "episodic"
                        localCatalog.upsertSubscribedPodcast(
                            enrichedPodcast.copy(
                                imageUrl =
                                    enrichedPodcast.imageUrl.ifEmpty {
                                        localPodcast?.imageUrl.orEmpty()
                                    },
                                type = typeVal,
                                preferredSort = preferredSortVal,
                                notificationsEnabled = localPodcast?.notificationsEnabled ?: false,
                                autoDownloadEnabled = localPodcast?.autoDownloadEnabled ?: false,
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadMoreEpisodes() {
        val currentState = _uiState.value
        if (currentState !is PodcastInfoUiState.Success) return
        if (currentState.isLoadingMore || !currentState.hasMoreEpisodes) return
        if (currentState.searchResults != null) return // Don't load more when searching

        _uiState.value = currentState.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val limit = if (currentState.currentSort == EpisodeSort.OLDEST) 200 else PAGE_SIZE
                val sortParam = if (currentState.currentSort == EpisodeSort.OLDEST) "oldest" else "newest"
                val page = repository.getEpisodesPaginated(currentPodcastId, limit, currentOffset, sortParam)
                currentOffset += page.episodes.size
                _uiState.value =
                    currentState.copy(
                        episodes = currentState.episodes + page.episodes,
                        isLoadingMore = false,
                        hasMoreEpisodes = page.hasMore,
                    )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }

    fun refreshRssFeed() {
        val currentState = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (!currentState.podcast.isRss || currentState.isRssRefreshing) return

        _uiState.value = currentState.copy(isLoadingMore = true, isRssRefreshing = true)
        viewModelScope.launch {
            try {
                rssRepository.refreshCatalog(currentPodcastId)
                val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                val limit = if (latestState.currentSort == EpisodeSort.OLDEST) 200 else PAGE_SIZE
                val sortParam =
                    if (latestState.currentSort == EpisodeSort.OLDEST) "oldest" else "newest"
                val podcast = repository.getPodcastDetails(currentPodcastId) ?: latestState.podcast
                val page =
                    repository.getEpisodesPaginated(currentPodcastId, limit, 0, sortParam)
                currentOffset = page.episodes.size
                _uiState.value =
                    latestState.copy(
                        podcast = podcast,
                        episodes = page.episodes,
                        isLoadingMore = false,
                        isRssRefreshing = false,
                        hasMoreEpisodes = page.hasMore,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value = latestState.copy(isLoadingMore = false, isRssRefreshing = false)
            }
        }
    }

    fun toggleSort() {
        didSortEpisodes = true
        val currentState = _uiState.value
        if (currentState !is PodcastInfoUiState.Success) return

        val newSort = if (currentState.currentSort == EpisodeSort.NEWEST) EpisodeSort.OLDEST else EpisodeSort.NEWEST
        currentOffset = 0

        _uiState.value =
            currentState.copy(
                currentSort = newSort,
                episodes = emptyList(),
                isLoadingMore = true,
                hasMoreEpisodes = true,
                searchQuery = "",
                searchResults = null,
            )

        viewModelScope.launch {
            // Persist sort preference for subscribed podcasts
            if (currentState.isSubscribed) {
                val sortString = if (newSort == EpisodeSort.OLDEST) "oldest" else "newest"
                subscriptionRepository.updatePreferredSort(currentPodcastId, sortString)
            }

            try {
                val limit = if (newSort == EpisodeSort.OLDEST) 200 else PAGE_SIZE
                val sortParam = if (newSort == EpisodeSort.OLDEST) "oldest" else "newest"
                val page = repository.getEpisodesPaginated(currentPodcastId, limit, 0, sortParam)
                currentOffset = page.episodes.size
                val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value =
                    latestState.copy(
                        episodes = page.episodes,
                        isLoadingMore = false,
                        hasMoreEpisodes = page.hasMore,
                    )
            } catch (e: Exception) {
                e.printStackTrace()
                val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value = latestState.copy(isLoadingMore = false)
            }
        }
    }

    fun searchEpisodes(query: String) {
        if (query.isNotBlank()) didSearch = true
        val currentState = _uiState.value
        if (currentState !is PodcastInfoUiState.Success) return

        _uiState.value = currentState.copy(searchQuery = query)

        // Clear search
        if (query.isBlank()) {
            _uiState.value =
                currentState.copy(
                    searchQuery = "",
                    searchResults = null,
                    isSearching = false,
                )
            return
        }

        // Debounce search
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                _uiState.value = (_uiState.value as? PodcastInfoUiState.Success)?.copy(isSearching = true) ?: return@launch
                delay(SEARCH_DEBOUNCE_MS)

                try {
                    // Correctly search for episodes within this feed using the repository
                    val podcastContext = (uiState.value as? PodcastInfoUiState.Success)
                    val feedId = podcastContext?.podcast?.id ?: return@launch

                    val results = repository.searchEpisodes(feedId, query)

                    // Ensure we are still in a valid state to update
                    val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                    _uiState.value =
                        latestState.copy(
                            searchResults = results,
                            isSearching = false,
                        )
                } catch (e: Exception) {
                    e.printStackTrace()
                    val latestState = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                    _uiState.value = latestState.copy(isSearching = false, searchResults = emptyList())
                }
            }
    }

    fun toggleSubscription() {
        val currentState = _uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val wasSubscribed = currentState.isSubscribed
                if (wasSubscribed) {
                    didUnsubscribe = true
                } else {
                    didSubscribe = true
                }
                subscriptionRepository.toggleSubscription(currentState.podcast)
                // Refresh state
                val isSubscribed = subscriptionRepository.isSubscribed(currentState.podcast.id)
                val updatedPodcast =
                    currentState.podcast.copy(
                        subscribedAt = if (isSubscribed) System.currentTimeMillis() else 0L,
                        notificationsEnabled = isSubscribed && currentState.podcast.notificationsEnabled,
                        autoDownloadEnabled = isSubscribed && currentState.podcast.autoDownloadEnabled,
                    )
                _uiState.value =
                    currentState.copy(
                        podcast = updatedPodcast,
                        isSubscribed = isSubscribed,
                    )

                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPodcastSubscriptionToggled(
                    podcastId = currentState.podcast.id,
                    podcastName = currentState.podcast.title,
                    isSubscribed = isSubscribed,
                    entryPoint = entryPoint ?: "unknown",
                )

                if (isSubscribed && !wasSubscribed) {
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val synced = repository.syncSubscriptions(listOf(currentState.podcast.id))
                            synced[currentState.podcast.id]?.let { episode ->
                                subscriptionRepository.updateLatestEpisode(currentState.podcast.id, episode)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (!isSubscribed && wasSubscribed) {
                    userPrefs.removeLastSeenEpisodeId(currentState.podcast.id)
                }
            }
        }
    }

    fun toggleNotifications() {
        val currentState = _uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val currentEnabled = currentState.podcast.notificationsEnabled
                val newEnabled = !currentEnabled

                // If notifications are turned OFF, auto-download must also turn OFF automatically
                val updatedAutoDownload = if (!newEnabled) false else currentState.podcast.autoDownloadEnabled
                if (!newEnabled && currentState.podcast.autoDownloadEnabled) {
                    subscriptionRepository.setAutoDownloadEnabled(currentState.podcast.id, false)
                }

                subscriptionRepository.setNotificationsEnabled(currentState.podcast, newEnabled)

                // Refresh UI State
                val updatedPodcast =
                    currentState.podcast.copy(
                        notificationsEnabled = newEnabled,
                        autoDownloadEnabled = updatedAutoDownload,
                    )
                _uiState.value = currentState.copy(podcast = updatedPodcast)

                android.util.Log.d("PodcastInfoViewModel", "Notifications toggled for ${currentState.podcast.title}: $newEnabled")
            }
        }
    }

    fun toggleAutoDownload() {
        val currentState = _uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val currentEnabled = currentState.podcast.autoDownloadEnabled
                val newEnabled = !currentEnabled

                subscriptionRepository.setAutoDownloadEnabled(currentState.podcast.id, newEnabled)

                // Refresh UI State
                val updatedPodcast = currentState.podcast.copy(autoDownloadEnabled = newEnabled)
                _uiState.value = currentState.copy(podcast = updatedPodcast)

                android.util.Log.d("PodcastInfoViewModel", "Auto-download toggled for ${currentState.podcast.title}: $newEnabled")
            }
        }
    }

    fun enableBothNotificationsAndAutoDownload() {
        val currentState = _uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                subscriptionRepository.setNotificationsEnabled(currentState.podcast, true)
                subscriptionRepository.setAutoDownloadEnabled(currentState.podcast.id, true)

                val updatedPodcast =
                    currentState.podcast.copy(
                        notificationsEnabled = true,
                        autoDownloadEnabled = true,
                    )
                _uiState.value = currentState.copy(podcast = updatedPodcast)

                android.util.Log.d("PodcastInfoViewModel", "Enabled both notifications & auto-download for ${currentState.podcast.title}")
            }
        }
    }

    fun toggleQueue(episode: Episode) {
        val currentState = _uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val isQueued = queuedEpisodeIds.value.contains(episode.id)
                if (isQueued) {
                    playbackRepository.removeFromQueue(episode.id)
                } else {
                    // User requested "Add to Queue" -> Insert as NEXT item
                    playbackRepository.addToQueueNext(episode, currentState.podcast)
                }
            }
        }
    }

    // Playback State Logic
    data class EpisodePlaybackState(
        val isPlaying: Boolean = false,
        val isResume: Boolean = false,
        val progress: Float = 0f,
        val timeLeft: String? = null,
    )

    // Combine player state and history to provide per-episode state
    val episodePlaybackState: StateFlow<Map<String, EpisodePlaybackState>> =
        kotlinx.coroutines.flow
            .combine(
                playbackRepository.playerState,
                playbackRepository.getAllHistory().map { history ->
                    history.map { it.toInfoListeningProgressItem() }
                },
                _currentPodcastIdFlow,
            ) { player: cx.aswin.boxlore.core.playback.PlayerState, historyList: List<InfoListeningProgressItem>, loadedId: String? ->
                val map = mutableMapOf<String, EpisodePlaybackState>()

                // 1. Map History (Resume State)
                historyList.forEach { history ->
                    if (!history.isCompleted && history.progressMs > 0L && history.durationMs > 0L) {
                        val progress = (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
                        val remainingSeconds = (history.durationMs - history.progressMs) / 1000
                        val timeLeft =
                            if (remainingSeconds > 0) {
                                val h = remainingSeconds / 3600
                                val m = (remainingSeconds % 3600) / 60
                                if (h > 0) {
                                    "${h}h ${m}m left"
                                } else {
                                    "${m}m left"
                                }
                            } else {
                                null
                            }

                        map[history.episodeId] =
                            EpisodePlaybackState(
                                isPlaying = false,
                                isResume = true,
                                progress = progress,
                                timeLeft = timeLeft,
                            )
                    }
                }

                // 2. Override with Active Player State ONLY if the playing episode belongs to this podcast
                val currentEp = player.currentEpisode
                if (currentEp != null && (currentEp.podcastId == loadedId || player.currentPodcast?.id == loadedId)) {
                    val progress = if (player.duration > 0) (player.position.toFloat() / player.duration).coerceIn(0f, 1f) else 0f
                    val remainingSeconds = if (player.duration > 0) (player.duration - player.position) / 1000 else 0
                    val timeLeft =
                        if (remainingSeconds > 0) {
                            val h = remainingSeconds / 3600
                            val m = (remainingSeconds % 3600) / 60
                            if (h > 0) {
                                    "${h}h ${m}m left"
                                } else {
                                    "${m}m left"
                                }
                        } else {
                            null
                        }

                    map[currentEp.id] =
                        EpisodePlaybackState(
                            isPlaying = player.isPlaying,
                            isResume = true, // Currently playing is technically "resumed" or "active"
                            progress = progress,
                            timeLeft = timeLeft,
                        )
                }

                map
            }.flowOn(kotlinx.coroutines.Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started =
                    kotlinx.coroutines.flow.SharingStarted
                        .WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    fun onPlayClick(episode: Episode) {
        android.util.Log.d("PodcastInfoViewModel", "onPlayClick triggered for: ${episode.title} (ID: ${episode.id})")
        playedEpisodes.add(episode.id)
        val currentState = _uiState.value as? PodcastInfoUiState.Success ?: return

        viewModelScope.launch {
            if (playbackRepository.playerState.value.currentEpisode
                    ?.id == episode.id
            ) {
                android.util.Log.d("PodcastInfoViewModel", "Episode already active, toggling play/pause")
                playbackRepository.togglePlayPause()
            } else {
                android.util.Log.d("PodcastInfoViewModel", "Starting new playback via queueManager")
                val sortOrder = if (currentState.currentSort == EpisodeSort.OLDEST) "oldest" else "newest"
                queueManager.playEpisode(episode, currentState.podcast, sortOrder)
            }
        }
    }

    // Track queued episodes
    val queuedEpisodeIds: StateFlow<Set<String>> =
        playbackRepository.playerState
            .map { state -> state.queue.map { it.id }.toSet() }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptySet(),
            )

    fun recordEpisodeClick(episodeId: String) {
        clickedEpisodes.add(episodeId)
    }

    fun onScreenResume() {
        if (hasTrackedExit) {
            // Restart the session timer when coming back from background
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackScreenExit() {
        if (hasTrackedExit || currentPodcastId.isEmpty()) return
        hasTrackedExit = true
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        val podcastName = (_uiState.value as? PodcastInfoUiState.Success)?.podcast?.title ?: "Unknown"
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPodcastInfoScreenSession(
            podcastId = currentPodcastId,
            podcastName = podcastName,
            timeSpentSeconds = timeSpent,
            wasSubscribed = wasSubscribedAtStart ?: false,
            didSubscribe = didSubscribe,
            didUnsubscribe = didUnsubscribe,
            didSearch = didSearch,
            didSortEpisodes = didSortEpisodes,
            episodesPlayedCount = playedEpisodes.size,
            episodesClickedCount = clickedEpisodes.size,
        )
    }

    override fun onCleared() {
        super.onCleared()
        trackScreenExit()
    }
}
