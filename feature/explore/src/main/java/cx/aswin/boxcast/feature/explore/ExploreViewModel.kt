package cx.aswin.boxcast.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import cx.aswin.boxcast.core.data.PodcastRepository
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

sealed interface ExploreUiState {
    data object Loading : ExploreUiState
    data class Success(
        val trending: List<Podcast> = emptyList(),
        val searchResults: List<Podcast> = emptyList(),
        val subscribedIds: Set<String> = emptySet(), // For badging
        val currentCategory: String = "All",
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val isLoading: Boolean = false, // For showing skeleton in grid area only
        val currentVibe: String? = null,
        val suggestedVibes: List<Pair<String, String>> = emptyList(),
        val correctedQuery: String? = null,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true
    ) : ExploreUiState
    data class Error(val message: String) : ExploreUiState
}

class ExploreViewModel(
    application: android.app.Application,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPrefs: cx.aswin.boxcast.core.data.UserPreferencesRepository,
    initialCategory: String? = null // New param
) : androidx.lifecycle.AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Internal state to combine
    private val _searchQuery = MutableStateFlow("")
    private val _currentCategory = MutableStateFlow(initialCategory ?: "All") // Use it here
    private val _trendingPodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    private val _searchResults = MutableStateFlow<List<Podcast>>(emptyList())
    private val _isLoading = MutableStateFlow(true) // Explicit loading state
    
    // Vibe Prompt State
    private val _currentVibe = MutableStateFlow<String?>(null)
    private val _suggestedVibes = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val _correctedQuery = MutableStateFlow<String?>(null)
    
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

    // Explore Region Nudge State flows (must be before init)
    private val _activeRegionCode = MutableStateFlow("us")
    val activeRegionCode: StateFlow<String> = _activeRegionCode.asStateFlow()

    private val _showRegionNudge = MutableStateFlow(false)
    val showRegionNudge: StateFlow<Boolean> = _showRegionNudge.asStateFlow()


    init {
        // Observe Subscriptions for Badging
        viewModelScope.launch {
            combine(
                subscriptionRepository.subscribedPodcastIds,
                _currentCategory,
                _trendingPodcasts,
                _searchResults,
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
                combine(_currentVibe, _suggestedVibes, _correctedQuery) { vibe, vibes, corrected -> Triple(vibe, vibes, corrected) }
            ) { (trip1, trip2), vibeTriple ->
                val (subIds, category, trending) = trip1
                val searchRes = trip2[0] as List<Podcast>
                val query = trip2[1] as String
                val pIsLoading = trip2[2] as Boolean
                val pIsLoadingMore = trip2[3] as Boolean
                val pHasMore = trip2[4] as Boolean
                val (currentVibe, vibes, corrected) = vibeTriple

                val isSearching = query.isNotEmpty() || currentVibe != null

                ExploreUiState.Success(
                    trending = trending,
                    searchResults = searchRes,
                    subscribedIds = subIds,
                    currentCategory = category,
                    searchQuery = query,
                    isSearching = isSearching,
                    isLoading = pIsLoading,
                    currentVibe = currentVibe,
                    suggestedVibes = vibes,
                    correctedQuery = corrected,
                    isLoadingMore = pIsLoadingMore,
                    hasMore = pHasMore
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
        
        // Observe explore region nudge preference and active region stream
        // Only show for non-mismatch users (mismatch users already get the Home nudge)
        viewModelScope.launch {
            val systemCountry = java.util.Locale.getDefault().country.lowercase().let {
                if (it == "us" || it == "in" || it == "gb") it else "us"
            }
            
            // Persist the initial match check to survive VM recreation
            val wasMatch = userPrefs.wasInitialRegionMatchStream.first() ?: run {
                val currentReg = userPrefs.regionStream.first()
                val isMatch = (systemCountry == currentReg)
                userPrefs.setWasInitialRegionMatch(isMatch)
                isMatch
            }

            combine(
                userPrefs.regionStream,
                userPrefs.hasDismissedExploreRegionNudgeStream
            ) { region, hasDismissed ->
                region to hasDismissed
            }.collect { (region, hasDismissed) ->
                _activeRegionCode.value = region
                // Show only when no initial mismatch and not already dismissed
                _showRegionNudge.value = !hasDismissed && wasMatch
            }
        }
    }

    private fun loadAllVibes() {
        val vibes = listOf(
            "morning_news" to "Top News",
            "morning_motivation" to "Daily Motivation",
            "business_insider" to "Business & Tech",
            "science_explainer" to "Science & Discovery",
            "tech_culture" to "Tech & Gadgets",
            "creative_focus" to "Creative Focus",
            "comedy_gold" to "Comedy Gold",
            "tv_film_buff" to "TV & Film",
            "sports_fan" to "Sports Highlights",
            "true_crime_sleep" to "True Crime & Chill",
            "history_buff" to "History",
            "mystery_thriller" to "Mystery & Thrillers"
        )
        // Shuffle for variety, or keep static. Static provides a consistent layout.
        _suggestedVibes.value = vibes
    }

    @OptIn(FlowPreview::class)
    private fun startSearchObserver() {
        _searchQuery
            .debounce(500L) // Wait 500ms after last char
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isNotBlank()) {
                    performSearch(query)
                } else if (_currentVibe.value == null) {
                    _searchResults.value = emptyList()
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        if (query.isNotEmpty() && _currentVibe.value != null) {
            _currentVibe.value = null // Stop showing vibe results if they start typing
            _searchResults.value = emptyList()
        }
        _correctedQuery.value = null // Clear correction when typing
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        if (_currentCategory.value == category) return
        categoriesClickedCount++
        _currentCategory.value = category
        clearVibe()
        // Clear Search when switching category to browse
        _searchQuery.value = "" 
        _trendingPodcasts.value = emptyList() // Clear to force Skeleton
    }
    
    fun onVibeSelected(vibeId: String, vibeName: String) {
        vibesClickedCount++
        _searchQuery.value = ""
        _currentVibe.value = vibeName
        _isLoading.value = true
        _searchResults.value = emptyList()


        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                // Uses same curated endpoint as HomeScreen!
                val results = podcastRepository.getCuratedPodcasts(vibeId)
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearVibe() {
        _currentVibe.value = null
        if (_searchQuery.value.isEmpty()) {
            _searchResults.value = emptyList()
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
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = emptyList() // Clear previous results to force Skeleton
            try {
                android.util.Log.d("ExploreViewModel", "Starting search for: $query")
                val corrected = query // Spelling is now handled by the Cloudflare Worker
                if (corrected != query) {
                    _correctedQuery.value = corrected
                } else {
                    _correctedQuery.value = null
                }
                
                val results = podcastRepository.searchPodcasts(corrected)
                android.util.Log.d("ExploreViewModel", "Search success: ${results.size} results returned")
                _searchResults.value = results
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackExploreSearchPerformed(query, results.size)
            } catch (e: Exception) {
                android.util.Log.e("ExploreViewModel", "Search error: ${e.message}", e)
                // Handle error silently for search
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
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

    // Explore Region Nudge controllers

    fun dismissExploreRegionNudge() {
        viewModelScope.launch {
            userPrefs.dismissExploreRegionNudge()
            userPrefs.dismissRegionNudge()
        }
    }

    fun switchRegion(region: String) {
        viewModelScope.launch {
            userPrefs.setRegion(region)
        }
    }

    override fun onCleared() {
        super.onCleared()
        trackScreenExit()
    }
}
