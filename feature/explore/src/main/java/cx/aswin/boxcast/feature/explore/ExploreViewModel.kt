package cx.aswin.boxcast.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.SearchResult
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.model.Episode

enum class SearchTab { SHOWS, EPISODES }

sealed interface ExploreUiState {
    data object Loading : ExploreUiState
    data class Success(
        val trending: List<Podcast> = emptyList(),
        val searchResults: List<Podcast> = emptyList(),
        val recommendations: List<Episode> = emptyList(),
        val subscribedIds: Set<String> = emptySet(), // For badging
        val currentCategory: String = "All",
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val isLoading: Boolean = false, // For showing skeleton in grid area only
        val currentVibe: String? = null,
        val suggestedVibes: List<Pair<String, String>> = emptyList(),
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val selectedTab: Int = 1, // 0 for Trending, 1 for For You (default)
        val isRecommendationsLoading: Boolean = false,
        val searchTab: SearchTab = SearchTab.SHOWS,
        val semanticSearchResults: List<Episode> = emptyList(),
        val isSemanticLoading: Boolean = false,
        val hasPerformedSemanticSearch: Boolean = false
    ) : ExploreUiState
    data class Error(val message: String) : ExploreUiState
}

class ExploreViewModel(
    application: android.app.Application,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPrefs: cx.aswin.boxcast.core.data.UserPreferencesRepository,
    private val playbackRepository: PlaybackRepository,
    initialCategory: String? = null,
    initialTab: String? = null
) : androidx.lifecycle.AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ExploreUiState>(
        ExploreUiState.Success(
            trending = emptyList(),
            searchResults = emptyList(),
            recommendations = emptyList(),
            subscribedIds = emptySet(),
            currentCategory = initialCategory ?: "All",
            searchQuery = "",
            isSearching = false,
            isLoading = true,
            currentVibe = null,
            suggestedVibes = emptyList(),
            isLoadingMore = false,
            selectedTab = if (initialCategory != null || initialTab == "trending") 0 else 1,
            isRecommendationsLoading = false,
            searchTab = SearchTab.SHOWS,
            semanticSearchResults = emptyList(),
            isSemanticLoading = false,
            hasPerformedSemanticSearch = false
        )
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()
    val playerState = playbackRepository.playerState

    // Internal state to combine
    private val _searchQuery = MutableStateFlow("")
    private val _currentCategory = MutableStateFlow(initialCategory ?: "All") // Use it here
    private val _trendingPodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    private val _searchResults = MutableStateFlow<List<Podcast>>(emptyList())
    private val _selectedTab = MutableStateFlow(if (initialCategory != null || initialTab == "trending") 0 else 1)
    private val _recommendations = MutableStateFlow<List<Episode>>(emptyList())
    private val _isRecommendationsLoading = MutableStateFlow(false)
    private val _searchTab = MutableStateFlow(SearchTab.SHOWS)
    private val _semanticSearchResults = MutableStateFlow<List<Episode>>(emptyList())
    private val _isSemanticLoading = MutableStateFlow(false)
    private val _hasPerformedSemanticSearch = MutableStateFlow(false)

    // Seen/cached podcasts for eager zero-latency substring client-side matching
    private val _seenPodcasts = java.util.concurrent.ConcurrentHashMap<String, Podcast>()
    private val _localSubstringResults = MutableStateFlow<List<Podcast>>(emptyList())
    
    // Combine local substring matches and remote search results seamlessly
    private val _combinedSearchResults = combine(_localSubstringResults, _searchResults) { local, remote ->
        val seenIds = mutableSetOf<String>()
        val combined = mutableListOf<Podcast>()
        
        remote.forEach {
            if (seenIds.add(it.id)) {
                combined.add(it)
            }
        }
        local.forEach {
            if (seenIds.add(it.id)) {
                combined.add(it)
            }
        }
        combined
    }

    private val _isLoading = MutableStateFlow(true) // Explicit loading state
    
    // Vibe Prompt State
    private val _currentVibe = MutableStateFlow<String?>(null)
    private val _suggestedVibes = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    
    // Search Job to cancel previous searches
    private var searchJob: Job? = null

    // Pagination State
    private var currentOffset = 0
    private val PAGE_SIZE = 20
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasMorePages = MutableStateFlow(true)

    // Telemetry State
    private var sessionStartTime = System.currentTimeMillis()
    private var categoriesClickedCount = 0
    private var vibesClickedCount = 0
    private var searchesPerformedCount = 0
    private var podcastsClickedCount = 0
    private var maxScrollDepth = 0
    private var hasTrackedExit = false

    // Active charts region (shown as a chip on the Trending header)
    private val _activeRegionCode = MutableStateFlow("us")
    val activeRegionCode: StateFlow<String> = _activeRegionCode.asStateFlow()


    init {
        // Preload subscriptions into _seenPodcasts cache to enable instant zero-latency filtering
        viewModelScope.launch {
            subscriptionRepository.subscribedPodcasts.collect { subscribed ->
                subscribed.forEach { podcast ->
                    _seenPodcasts[podcast.id] = podcast
                }
            }
        }

        // Observe Subscriptions for Badging
        viewModelScope.launch {
            combine(
                subscriptionRepository.subscribedPodcastIds,
                _currentCategory,
                _trendingPodcasts,
                _combinedSearchResults,
                _searchQuery,
                _isLoading,
                _isLoadingMore,
                _hasMorePages
            ) { args: Array<Any?> ->
                val subIds = args[0] as Set<String>
                val category = args[1] as String
                val trending = args[2] as List<Podcast>
                val searchRes = args[3] as List<Podcast>
                val query = args[4] as String
                val pIsLoading = args[5] as Boolean
                val pIsLoadingMore = args[6] as Boolean
                val pHasMore = args[7] as Boolean
                // Custom combine to pull all flows
                Triple(subIds, category, trending) to arrayOf(searchRes, query, pIsLoading, pIsLoadingMore, pHasMore)
            }.combine(
                combine(
                    _currentVibe,
                    _suggestedVibes,
                    _selectedTab,
                    _recommendations,
                    _isRecommendationsLoading,
                    _searchTab,
                    _semanticSearchResults,
                    _isSemanticLoading,
                    _hasPerformedSemanticSearch
                ) { args ->
                    args
                }
            ) { (trip1, trip2), extra ->
                val (subIds, category, trending) = trip1
                val searchRes = trip2[0] as List<Podcast>
                val query = trip2[1] as String
                val pIsLoading = trip2[2] as Boolean
                val pIsLoadingMore = trip2[3] as Boolean
                val pHasMore = trip2[4] as Boolean
                
                val currentVibe = extra[0] as String?
                val vibes = extra[1] as List<Pair<String, String>>
                val selectedTab = extra[2] as Int
                val recommendations = extra[3] as List<Episode>
                val isRecommendationsLoading = extra[4] as Boolean
                val searchTab = extra[5] as SearchTab
                val semanticSearchResults = extra[6] as List<Episode>
                val isSemanticLoading = extra[7] as Boolean
                val hasPerformedSemanticSearch = extra[8] as Boolean

                val isSearching = query.isNotEmpty() || currentVibe != null

                ExploreUiState.Success(
                    trending = trending,
                    searchResults = searchRes,
                    recommendations = recommendations,
                    subscribedIds = subIds,
                    currentCategory = category,
                    searchQuery = query,
                    isSearching = isSearching,
                    isLoading = pIsLoading,
                    currentVibe = currentVibe,
                    suggestedVibes = vibes,
                    isLoadingMore = pIsLoadingMore,
                    hasMore = pHasMore,
                    selectedTab = selectedTab,
                    isRecommendationsLoading = isRecommendationsLoading,
                    searchTab = searchTab,
                    semanticSearchResults = semanticSearchResults,
                    isSemanticLoading = isSemanticLoading,
                    hasPerformedSemanticSearch = hasPerformedSemanticSearch
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Search Debounce Implementation
        startSearchObserver()
        
        // Initial Load
        loadAllVibes()
        
        viewModelScope.launch {
            combine(_currentCategory, userPrefs.regionStream) { category, region ->
                category to region
            }.collectLatest { (category, region) ->
                loadTrending(category, region)
            }
        }

        viewModelScope.launch {
            combine(
                userPrefs.regionStream,
                subscriptionRepository.subscribedPodcastIds
            ) { _, _ -> }.collect {
                fetchPersonalizedRecommendations()
            }
        }
        
        // Keep active region label in sync for the charts header chip.
        viewModelScope.launch {
            userPrefs.regionStream.collect { region ->
                _activeRegionCode.value = region
            }
        }
    }

    private fun getSortedVibesForTimeOfDay(): List<Pair<String, String>> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val morning = listOf(
            "morning_news" to "Top News",
            "morning_motivation" to "Daily Motivation",
            "business_insider" to "Business & Tech"
        )
        val afternoon = listOf(
            "science_explainer" to "Science & Discovery",
            "tech_culture" to "Tech & Gadgets",
            "creative_focus" to "Creative Focus"
        )
        val evening = listOf(
            "comedy_gold" to "Comedy Gold",
            "tv_film_buff" to "TV & Film",
            "sports_fan" to "Sports Highlights"
        )
        val lateNight = listOf(
            "true_crime_sleep" to "True Crime & Chill",
            "history_buff" to "History",
            "mystery_thriller" to "Mystery & Thrillers"
        )

        return when (hour) {
            in 5..11 -> morning + afternoon + evening + lateNight
            in 12..16 -> afternoon + evening + lateNight + morning
            in 17..22 -> evening + lateNight + morning + afternoon
            else -> lateNight + morning + afternoon + evening
        }
    }

    private fun loadAllVibes() {
        _suggestedVibes.value = getSortedVibesForTimeOfDay()
    }

    @OptIn(FlowPreview::class)
    private fun startSearchObserver() {
        // 1. Shows tab observer (300ms debounce)
        _searchQuery
            .debounce(300L)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isNotBlank() && _searchTab.value == SearchTab.SHOWS) {
                    performSearch(query)
                }
            }
            .launchIn(viewModelScope)

        // 2. Episodes tab observer (1000ms debounce)
        _searchQuery
            .debounce(1000L)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isNotBlank() && _searchTab.value == SearchTab.EPISODES) {
                    performSemanticSearch(query)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchTriggered(query: String) {
        if (query.isNotBlank()) {
            if (_searchTab.value == SearchTab.EPISODES) {
                performSemanticSearch(query)
            } else {
                performSearch(query)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        if (query.isNotEmpty() && _currentVibe.value != null) {
            _currentVibe.value = null // Stop showing vibe results if they start typing
            _searchResults.value = emptyList()
        }
        _searchQuery.value = query

        // Eager, zero-latency local filtering as the user types (0ms delay)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _localSubstringResults.value = emptyList()
            _semanticSearchResults.value = emptyList()
            _hasPerformedSemanticSearch.value = false
            _isSemanticLoading.value = false
        } else {
            val matches = _seenPodcasts.values.filter { podcast ->
                podcast.title.contains(trimmed, ignoreCase = true) ||
                (podcast.artist ?: "").contains(trimmed, ignoreCase = true)
            }.sortedBy { it.title }
            _localSubstringResults.value = matches

            // If we are on EPISODES tab, set loading to true immediately because they just started typing!
            if (_searchTab.value == SearchTab.EPISODES) {
                _isSemanticLoading.value = true
                _hasPerformedSemanticSearch.value = false
            }
        }
    }

    fun onCategorySelected(category: String) {
        if (_currentCategory.value == category) return
        categoriesClickedCount++
        _currentCategory.value = category
        clearVibe()
        // Clear Search when switching category to browse
        _searchQuery.value = "" 
        _localSubstringResults.value = emptyList()
        _trendingPodcasts.value = emptyList() // Clear to force Skeleton
    }
    
    fun onVibeSelected(vibeId: String, vibeName: String) {
        vibesClickedCount++
        _searchQuery.value = ""
        _localSubstringResults.value = emptyList()
        _currentVibe.value = vibeName
        _isLoading.value = true
        _searchResults.value = emptyList()

        searchJob?.cancel()
        
        var myJob: Job? = null
        myJob = viewModelScope.launch {
            try {
                // Uses same curated endpoint as HomeScreen!
                val results = podcastRepository.getCuratedPodcasts(vibeId)
                if (searchJob == myJob) {
                    _searchResults.value = results
                    results.forEach { _seenPodcasts[it.id] = it }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (searchJob == myJob) {
                    _searchResults.value = emptyList()
                }
            } finally {
                if (searchJob == myJob) {
                    _isLoading.value = false
                }
            }
        }
        searchJob = myJob
    }
    
    fun clearVibe() {
        _currentVibe.value = null
        if (_searchQuery.value.isEmpty()) {
            _searchResults.value = emptyList()
            _localSubstringResults.value = emptyList()
        }
    }

    private suspend fun loadTrending(category: String, region: String) {
        currentOffset = 0
        _hasMorePages.value = true
        _isLoading.value = true
        try {
            // Map "All" to null for API, and lowercase others for consistency
            val apiCategory = if (category == "All") null else category.lowercase()
            
            // This hits the Turso DB (via Proxy)
            val podcasts = podcastRepository.getTrendingPodcasts(
                country = region, 
                limit = PAGE_SIZE,
                category = apiCategory,
                offset = 0
            )
            _trendingPodcasts.value = podcasts
            podcasts.forEach { _seenPodcasts[it.id] = it }
            _hasMorePages.value = podcasts.size >= PAGE_SIZE
            currentOffset = podcasts.size
        } catch (e: Exception) {
            // Handle error
            _trendingPodcasts.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    fun loadMoreTrending() {
        if (_isLoadingMore.value || !_hasMorePages.value || _searchQuery.value.isNotEmpty() || _currentVibe.value != null) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val apiCategory = if (_currentCategory.value == "All") null else _currentCategory.value.lowercase()
                val region = userPrefs.regionStream.first()
                val morePodcasts = podcastRepository.getTrendingPodcasts(
                    country = region,
                    limit = PAGE_SIZE,
                    category = apiCategory,
                    offset = currentOffset
                )
                if (morePodcasts.size < PAGE_SIZE) {
                    _hasMorePages.value = false
                }
                currentOffset += morePodcasts.size
                val existingIds = _trendingPodcasts.value.map { it.id }.toSet()
                val newPodcasts = morePodcasts.filter { it.id !in existingIds }
                _trendingPodcasts.value = _trendingPodcasts.value + newPodcasts
                newPodcasts.forEach { _seenPodcasts[it.id] = it }
            } catch (e: Exception) {
                android.util.Log.e("ExploreViewModel", "Load more error", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun performSearch(query: String) {
        if (_currentVibe.value != null) return // Safety check

        searchesPerformedCount++
        searchJob?.cancel()

        if (_searchTab.value == SearchTab.EPISODES) {
            performSemanticSearch(query)
        } else {
            _semanticSearchResults.value = emptyList()
        }
        
        var myJob: Job? = null
        myJob = viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = emptyList() // Clear previous results to force Skeleton
            try {
                val searchResult = podcastRepository.searchPodcastsWithCorrection(query)
                if (searchJob == myJob) {
                    _searchResults.value = searchResult.podcasts
                    searchResult.podcasts.forEach { _seenPodcasts[it.id] = it }
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackExploreSearchPerformed(query, searchResult.podcasts.size)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                if (searchJob == myJob) {
                    _searchResults.value = emptyList()
                }
            } finally {
                if (searchJob == myJob) {
                    _isLoading.value = false
                }
            }
        }
        searchJob = myJob
    }

    private var semanticSearchJob: Job? = null

    private fun performSemanticSearch(query: String) {
        semanticSearchJob?.cancel()
        var myJob: Job? = null
        myJob = viewModelScope.launch {
            _isSemanticLoading.value = true
            _semanticSearchResults.value = emptyList()
            _hasPerformedSemanticSearch.value = false
            try {
                val region = userPrefs.regionStream.first()
                val results = podcastRepository.searchEpisodesSemantic(query, region)
                if (semanticSearchJob == myJob) {
                    _semanticSearchResults.value = results
                    _hasPerformedSemanticSearch.value = true
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                if (semanticSearchJob == myJob) {
                    _semanticSearchResults.value = emptyList()
                    _hasPerformedSemanticSearch.value = true
                }
            } finally {
                if (semanticSearchJob == myJob) {
                    _isSemanticLoading.value = false
                }
            }
        }
        semanticSearchJob = myJob
    }

    fun setSearchTab(tab: SearchTab) {
        if (_searchTab.value == tab) return
        _searchTab.value = tab
        val query = _searchQuery.value.trim()
        if (tab == SearchTab.EPISODES && _semanticSearchResults.value.isEmpty() && query.isNotEmpty()) {
            performSemanticSearch(query)
        }
    }

    fun trackPodcastClicked(index: Int) {
        podcastsClickedCount++
        if (index > maxScrollDepth) {
            maxScrollDepth = index
        }
    }

    fun onScreenResume() {
        if (hasTrackedExit) {
            // User came back from background or backstack. Restart the session timer.
            // Note: We don't reset the click counts, so it truly acts as one contiguous session.
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackScreenExit() {
        if (hasTrackedExit) return
        hasTrackedExit = true
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackExploreScreenSession(
            timeSpentSeconds = timeSpent,
            categoriesClickedCount = categoriesClickedCount,
            vibesClickedCount = vibesClickedCount,
            searchesPerformedCount = searchesPerformedCount,
            podcastsClickedCount = podcastsClickedCount,
            maxScrollDepth = maxScrollDepth,
            finalCategoryState = _currentCategory.value,
            finalVibeState = _currentVibe.value,
            finalSearchQuery = _searchQuery.value.takeIf { it.isNotBlank() }
        )
    }

    fun onTabSelected(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun fetchPersonalizedRecommendations() {
        viewModelScope.launch {
            _isRecommendationsLoading.value = true
            try {
                val prefs = getApplication<android.app.Application>().getSharedPreferences("boxcast_prefs", android.content.Context.MODE_PRIVATE)
                val interests = prefs.getStringSet("user_genres", emptySet())?.toList() ?: emptyList()
                val history = playbackRepository.getHistoryForRecommendations(15)
                
                val subscribedIds = subscriptionRepository.subscribedPodcastIds.first().toList()
                val subscribedGenres = subscriptionRepository.subscribedPodcasts.first()
                    .mapNotNull { it.genre }
                    .distinct()
                
                val region = userPrefs.regionStream.first()
                
                android.util.Log.d("ExploreViewModel", "Fetching recommendations with history size: ${history.size}, interests: $interests, region: $region, subscribedCount: ${subscribedIds.size}")
                val recs = podcastRepository.getPersonalizedRecommendations(
                    history = history,
                    interests = interests,
                    country = region,
                    subscribedPodcastIds = subscribedIds,
                    subscribedGenres = subscribedGenres
                )
                android.util.Log.d("ExploreViewModel", "Fetched recommendations size: ${recs.size}")
                val distinctRecs = recs
                    .distinctBy { it.id }
                    .distinctBy { it.title.lowercase().trim() }
                _recommendations.value = distinctRecs
            } catch (e: Exception) {
                android.util.Log.e("ExploreViewModel", "Failed to fetch personalized recommendations", e)
            } finally {
                _isRecommendationsLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        trackScreenExit()
    }
}
