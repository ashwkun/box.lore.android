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
        val correctedQuery: String? = null,
        val isSearching: Boolean = false,
        val isLoading: Boolean = false, // For showing skeleton in grid area only
        val currentVibe: String? = null,
        val suggestedVibes: List<Pair<String, String>> = emptyList()
    ) : ExploreUiState
    data class Error(val message: String) : ExploreUiState
}

class ExploreViewModel(
    application: android.app.Application,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    initialCategory: String? = null // New param
) : androidx.lifecycle.AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Internal state to combine
    data class SearchRequest(val query: String, val exact: Boolean = false)
    private val _searchRequest = MutableStateFlow(SearchRequest("", false))
    private val _correctedQuery = MutableStateFlow<String?>(null)
    private val _currentCategory = MutableStateFlow(initialCategory ?: "All") // Use it here
    private val _trendingPodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    private val _searchResults = MutableStateFlow<List<Podcast>>(emptyList())
    private val _isLoading = MutableStateFlow(true) // Explicit loading state
    
    // Vibe Prompt State
    private val _currentVibe = MutableStateFlow<String?>(null)
    private val _suggestedVibes = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    
    // Search Job to cancel previous searches
    private var searchJob: Job? = null

    // Telemetry State
    private var sessionStartTime = System.currentTimeMillis()
    private var categoriesClickedCount = 0
    private var vibesClickedCount = 0
    private var searchesPerformedCount = 0
    private var podcastsClickedCount = 0
    private var maxScrollDepth = 0
    private var hasTrackedExit = false

    init {
        // Observe Subscriptions for Badging
        viewModelScope.launch {
            combine(
                subscriptionRepository.subscribedPodcastIds,
                _currentCategory,
                _trendingPodcasts,
                _searchResults,
                _searchRequest,
                _correctedQuery
            ) { args: Array<Any?> ->
                val subIds = args[0] as Set<String>
                val category = args[1] as String
                val trending = args[2] as List<Podcast>
                val searchRes = args[3] as List<Podcast>
                val request = args[4] as SearchRequest
                val corrected = args[5] as String?
                // Custom combine to pull all flows
                Triple(subIds, category, trending) to Triple(searchRes, request.query, corrected)
            }.combine(
                combine(_isLoading, _currentVibe, _suggestedVibes) { loading, vibe, vibes -> Triple(loading, vibe, vibes) }
            ) { (trip1, trip2), trip3 ->
                val (subIds, category, trending) = trip1
                val (searchRes, query, corrected) = trip2
                val (pIsLoading, currentVibe, vibes) = trip3

                val isSearching = query.isNotEmpty() || currentVibe != null

                ExploreUiState.Success(
                    trending = trending,
                    searchResults = searchRes,
                    subscribedIds = subIds,
                    currentCategory = category,
                    searchQuery = query,
                    correctedQuery = corrected,
                    isSearching = isSearching,
                    isLoading = pIsLoading,
                    currentVibe = currentVibe,
                    suggestedVibes = vibes
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Search Debounce Implementation
        startSearchObserver()
        
        // Initial Load
        loadAllVibes()
        loadTrending(_currentCategory.value)
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
        _searchRequest
            .debounce(500L) // Wait 500ms after last char
            .distinctUntilChanged()
            .onEach { request ->
                if (request.query.isNotBlank()) {
                    performSearch(request.query, request.exact)
                } else if (_currentVibe.value == null) {
                    _searchResults.value = emptyList()
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String, exact: Boolean = false) {
        if (query.isNotEmpty() && _currentVibe.value != null) {
            _currentVibe.value = null // Stop showing vibe results if they start typing
            _searchResults.value = emptyList()
        }
        _searchRequest.value = SearchRequest(query, exact)
    }

    fun onCategorySelected(category: String) {
        if (_currentCategory.value == category) return
        categoriesClickedCount++
        _currentCategory.value = category
        clearVibe()
        // Clear Search when switching category to browse
        _searchRequest.value = SearchRequest("", false) 
        _trendingPodcasts.value = emptyList() // Clear to force Skeleton
        loadTrending(category)
    }
    
    fun onVibeSelected(vibeId: String, vibeName: String) {
        vibesClickedCount++
        _searchRequest.value = SearchRequest("", false)
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
        if (_searchRequest.value.query.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    private fun loadTrending(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Map "All" to null for API, and lowercase others for consistency
                val apiCategory = if (category == "All") null else category.lowercase()
                
                // This hits the Turso DB (via Proxy)
                val podcasts = podcastRepository.getTrendingPodcasts(
                    country = "us", 
                    limit = 50,
                    category = apiCategory
                )
                _trendingPodcasts.value = podcasts
            } catch (e: Exception) {
                // Handle error
                _trendingPodcasts.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun performSearch(query: String, exact: Boolean = false) {
        if (_currentVibe.value != null) return // Safety check

        searchesPerformedCount++
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = emptyList() // Clear previous results to force Skeleton
            _correctedQuery.value = null // Clear previous correction
            try {
                val result = podcastRepository.searchPodcasts(query, exact)
                _searchResults.value = result.podcasts
                _correctedQuery.value = result.correctedQuery
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackExploreSearchPerformed(query, result.podcasts.size)
            } catch (e: Exception) {
                // Handle error silently for search
                _searchResults.value = emptyList()
                _correctedQuery.value = null
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
            finalSearchQuery = _searchRequest.value.query.takeIf { it.isNotBlank() }
        )
    }

    override fun onCleared() {
        super.onCleared()
        trackScreenExit()
    }
}
