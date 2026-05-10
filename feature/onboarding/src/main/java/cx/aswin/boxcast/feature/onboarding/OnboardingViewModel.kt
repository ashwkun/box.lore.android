package cx.aswin.boxcast.feature.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.data.analytics.AnalyticsHelper
import cx.aswin.boxcast.core.model.Podcast

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.GENRES,
    val selectedGenres: Set<String> = emptySet(),
    val recommendedPodcasts: List<Podcast> = emptyList(),
    val subscribedPodcastIds: Set<String> = emptySet(),
    val isLoadingPodcasts: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Podcast> = emptyList(),
    val isSearching: Boolean = false,
    val isCompleting: Boolean = false
)

enum class OnboardingStep {
    GENRES, PODCASTS, SEARCH
}

class OnboardingViewModel(
    application: Application,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null

    // ── Analytics Timing & Counters ────────────────────────────────
    private var onboardingStartMs: Long = 0L
    private var genreScreenStartMs: Long = 0L
    private var podcastScreenStartMs: Long = 0L
    private var searchScreenStartMs: Long = 0L
    private var searchesPerformedCount: Int = 0
    private var podcastsSubscribedInSearchCount: Int = 0
    private var searchEntryPoint: String = "genre_screen"
    private var onboardingStartedFired: Boolean = false
    private var didScrollSuggestions: Boolean = false
    
    /** Total time since the onboarding flow began */
    fun getTotalOnboardingTime(): Float {
        if (onboardingStartMs == 0L) return 0f
        return (System.currentTimeMillis() - onboardingStartMs) / 1000f
    }
    
    /** Time spent on the genre screen in seconds */
    fun getGenreScreenTimeSpent(): Float {
        if (genreScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - genreScreenStartMs) / 1000f
    }

    /** Time spent on the podcast screen in seconds */
    private fun getPodcastScreenTimeSpent(): Float {
        if (podcastScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - podcastScreenStartMs) / 1000f
    }

    /** Time spent on the search screen in seconds */
    private fun getSearchScreenTimeSpent(): Float {
        if (searchScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - searchScreenStartMs) / 1000f
    }

    /** Called when the genre screen composable first loads */
    fun onGenreScreenViewed() {
        if (!onboardingStartedFired) {
            onboardingStartMs = System.currentTimeMillis()
            genreScreenStartMs = System.currentTimeMillis()
            AnalyticsHelper.trackOnboardingStarted()
            onboardingStartedFired = true
        }
    }

    /** Called when the podcast screen composable first loads */
    fun onPodcastScreenViewed() {
        if (podcastScreenStartMs == 0L) {
            podcastScreenStartMs = System.currentTimeMillis()
        }
    }

    fun onPodcastScreenScrolled() {
        didScrollSuggestions = true
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }
    
    
    
    fun toggleGenre(genre: String) {
        _uiState.update { state ->
            val newGenres = if (genre in state.selectedGenres) {
                state.selectedGenres - genre
            } else {
                state.selectedGenres + genre
            }
            state.copy(selectedGenres = newGenres)
        }
    }
    
    fun continueToRecommendations() {
        // Analytics: Track genres submitted with time spent
        val selectedGenres = _uiState.value.selectedGenres
        AnalyticsHelper.trackGenresSubmitted(selectedGenres, getGenreScreenTimeSpent())

        val selectedCount = _uiState.value.selectedGenres.size
        _uiState.update { it.copy(currentStep = OnboardingStep.PODCASTS, isLoadingPodcasts = true) }
        viewModelScope.launch {
            val genresList = _uiState.value.selectedGenres.toList()
            val allPodcasts = mutableListOf<Podcast>()
            
            // Fetch trending podcasts for each selected genre
            val perGenreLimit = when {
                genresList.size <= 2 -> 5
                genresList.size <= 4 -> 3
                else -> 2
            }
            
            for (genre in genresList) {
                val trending = podcastRepository.getTrendingPodcasts(
                    category = genre,
                    limit = perGenreLimit
                )
                allPodcasts.addAll(trending)
            }
            
            // Deduplicate and limit to 10
            val uniquePodcasts = allPodcasts
                .distinctBy { it.id }
                .shuffled()
                .take(10)
            
            _uiState.update {
                it.copy(
                    recommendedPodcasts = uniquePodcasts,
                    subscribedPodcastIds = uniquePodcasts.map { p -> p.id }.toSet(), // Pre-select all
                    isLoadingPodcasts = false
                )
            }
        }
    }
    
    fun togglePodcastSubscription(podcastId: String) {
        _uiState.update { state ->
            val newSubs = if (podcastId in state.subscribedPodcastIds) {
                state.subscribedPodcastIds - podcastId
            } else {
                state.subscribedPodcastIds + podcastId
            }
            state.copy(subscribedPodcastIds = newSubs)
        }
    }
    
    fun navigateToSearch() {
        // Determine entry point based on current step
        searchEntryPoint = when (_uiState.value.currentStep) {
            OnboardingStep.GENRES -> "genre_screen"
            OnboardingStep.PODCASTS -> "podcast_screen"
            else -> "genre_screen"
        }

        // Analytics: Track search opened
        val genreTime = if (searchEntryPoint == "genre_screen") getGenreScreenTimeSpent() else null
        AnalyticsHelper.trackSearchOpened(searchEntryPoint, genreTime)

        // Reset search counters for this session
        searchesPerformedCount = 0
        podcastsSubscribedInSearchCount = 0
        searchScreenStartMs = System.currentTimeMillis()

        _uiState.update { it.copy(currentStep = OnboardingStep.SEARCH) }
    }
    
    fun navigateBackFromPodcasts() {
        _uiState.update { it.copy(currentStep = OnboardingStep.GENRES) }
    }
    
    fun navigateBackFromSearch() {
        val state = _uiState.value
        val exitDestination = if (state.selectedGenres.isNotEmpty()) "podcast_screen" else "genre_screen"

        // Analytics: Track search exit
        AnalyticsHelper.trackSearchExited(
            exitDestination = exitDestination,
            searchesPerformed = searchesPerformedCount,
            podcastsSubscribedInSearch = podcastsSubscribedInSearchCount,
            timeSpentOnSearchSeconds = getSearchScreenTimeSpent()
        )

        val backStep = if (state.selectedGenres.isNotEmpty()) OnboardingStep.PODCASTS else OnboardingStep.GENRES
        _uiState.update { it.copy(currentStep = backStep, searchQuery = "", searchResults = emptyList()) }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        // Debounce search
        searchJob?.cancel()
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(400) // Debounce
            val results = podcastRepository.searchPodcasts(cleaned)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }

            // Analytics: Track search performed
            searchesPerformedCount++
            AnalyticsHelper.trackSearchPerformed(cleaned, results.size)
        }
    }
    
    fun subscribeFromSearch(podcast: Podcast) {
        _uiState.update { state ->
            // Add to subscribed IDs
            val newSubs = state.subscribedPodcastIds + podcast.id
            
            // Allow this podcast to appear in the main recommendation list too
            val newRecommendations = if (state.recommendedPodcasts.any { it.id == podcast.id }) {
                state.recommendedPodcasts
            } else {
                state.recommendedPodcasts + podcast
            }
            
            state.copy(
                subscribedPodcastIds = newSubs,
                recommendedPodcasts = newRecommendations
            )
        }

        // Analytics: Track podcast subscribed in search
        podcastsSubscribedInSearchCount++
        AnalyticsHelper.trackSearchPodcastSubscribed(
            podcastName = podcast.title,
            podcastId = podcast.id,
            totalSubscribedCount = _uiState.value.subscribedPodcastIds.size
        )

        // Metadata is already captured in the podcast object added to recommendations.
        // Actual DB write will happen in completeOnboarding to avoid double-toggling issues.
    }
    
    fun completeOnboarding(onDone: () -> Unit) {
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            val state = _uiState.value
            // Subscribe to all selected podcasts from recommendations (which now includes search selections)
            val podcastsToSubscribe = state.recommendedPodcasts.filter { it.id in state.subscribedPodcastIds }
            for (podcast in podcastsToSubscribe) {
                // Use idempotent subscribe to avoid toggling off existing subs
                subscriptionRepository.subscribe(podcast)
            }
            
            // Mark onboarding as completed
            prefs.edit().putBoolean("onboarding_completed", true).apply()

            // Analytics: If completing from search screen, fire search_done variant
            if (state.currentStep == OnboardingStep.SEARCH) {
                AnalyticsHelper.trackOnboardingSearchDone(
                    entryPoint = searchEntryPoint,
                    totalSubscribedCount = state.subscribedPodcastIds.size,
                    searchesPerformed = searchesPerformedCount,
                    timeSpentOnSearchSeconds = getSearchScreenTimeSpent(),
                    timeSpentOnGenreScreenSeconds = getGenreScreenTimeSpent(),
                    totalOnboardingTimeSeconds = getTotalOnboardingTime(),
                    selectedGenres = state.selectedGenres
                )
            } else if (state.currentStep == OnboardingStep.PODCASTS) {
                val removedCount = state.recommendedPodcasts.count { it.id !in state.subscribedPodcastIds }
                AnalyticsHelper.trackOnboardingSuggestionsDone(
                    totalSubscribedCount = state.subscribedPodcastIds.size,
                    removedSuggestionsCount = removedCount,
                    addedFromSearchCount = podcastsSubscribedInSearchCount,
                    didScrollSuggestions = didScrollSuggestions,
                    timeSpentOnPodcastScreenSeconds = getPodcastScreenTimeSpent(),
                    timeSpentOnGenreScreenSeconds = getGenreScreenTimeSpent(),
                    totalOnboardingTimeSeconds = getTotalOnboardingTime()
                )
            }

            // Save selected genres for future personalization
            prefs.edit().putStringSet("user_genres", state.selectedGenres).apply()

            onDone()
        }
    }
    
    fun skipOnboarding(onDone: () -> Unit) {
        // Analytics: Track skip
        val currentScreen = when (_uiState.value.currentStep) {
            OnboardingStep.GENRES -> "genre_screen"
            OnboardingStep.PODCASTS -> "podcast_screen"
            OnboardingStep.SEARCH -> "search_screen"
        }
        val podcastTime = if (_uiState.value.currentStep == OnboardingStep.PODCASTS) getPodcastScreenTimeSpent() else null
        AnalyticsHelper.trackOnboardingSkipped(currentScreen, getGenreScreenTimeSpent(), getTotalOnboardingTime(), podcastTime)

        prefs.edit().putBoolean("onboarding_completed", true).apply()

        onDone()
    }
}
