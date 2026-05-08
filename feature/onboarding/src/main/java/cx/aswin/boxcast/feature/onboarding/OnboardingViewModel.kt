package cx.aswin.boxcast.feature.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.model.Podcast
import com.posthog.PostHog

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
    val isCompleting: Boolean = false,
    val searchSource: String = "none",
    val initialSuggestionCount: Int = 0,
    val genreTimeSpent: Long = 0,
    val suggestionTimeSpent: Long = 0,
    val genreMaxScroll: Float = 0f
)

enum class OnboardingStep {
    GENRES,
    PODCASTS,
    SEARCH
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val prefs: android.content.SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var stepStartTime: Long = System.currentTimeMillis()

    private fun getAndResetDuration(): Long {
        val now = System.currentTimeMillis()
        val duration = (now - stepStartTime) / 1000 // In seconds
        stepStartTime = now
        return duration
    }
    
    fun completeOnboarding(method: String, maxScroll: Float, onDone: () -> Unit) {
        _uiState.update { it.copy(isCompleting = true) }
        val suggestionDuration = getAndResetDuration()
        
        viewModelScope.launch {
            val state = _uiState.value
            val podcastsToSubscribe = state.recommendedPodcasts.filter { it.id in state.subscribedPodcastIds }
            for (podcast in podcastsToSubscribe) {
                subscriptionRepository.subscribe(podcast)
            }
            
            prefs.edit().putBoolean("onboarding_completed", true).apply()

            // Calculate Behavioral Persona
            val totalTime = state.genreTimeSpent + suggestionDuration
            val behavior = when {
                state.searchSource == "bypass" -> "The Power User"
                totalTime > 60 && (state.genreMaxScroll > 0.7f || maxScroll > 0.7f) -> "The Researcher"
                totalTime < 20 -> "The Decisive"
                else -> "The Casual"
            }

            val initialCount = state.initialSuggestionCount
            val finalCountFromSuggestions = podcastsToSubscribe.count { pod -> 
                state.recommendedPodcasts.any { it.id == pod.id } 
            }
            
            val suggestionScore = when {
                initialCount == 0 -> "n/a"
                finalCountFromSuggestions == initialCount -> "perfect_match"
                finalCountFromSuggestions > 0 -> "selective"
                else -> "rejected"
            }

            // Set Profile Traits
            PostHog.setPersonProperties(mapOf(
                "onboarding_behavior" to behavior,
                "onboarding_completion_method" to method,
                "suggestion_engagement" to suggestionScore
            ))

            PostHog.capture(
                event = "onboarding_completed",
                properties = mapOf(
                    "method" to method,
                    "behavior" to behavior,
                    "total_time_sec" to totalTime,
                    "suggestion_time_sec" to suggestionDuration,
                    "suggestion_max_scroll" to (maxScroll * 100).toInt(),
                    "genre_count" to state.selectedGenres.size,
                    "podcast_count" to podcastsToSubscribe.size,
                    "suggestion_score" to suggestionScore,
                    "assigned_persona" to (PostHog.getPersonProperties()["primary_interests"] ?: "none")
                )
            )

            prefs.edit().putStringSet("user_genres", state.selectedGenres).apply()
            onDone()
        }
    }
    
    private var searchJob: Job? = null
    
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }
    
    private var hasTrackedStart = false

    fun trackOnboardingStarted(isInternal: Boolean) {
        if (hasTrackedStart) return
        hasTrackedStart = true

        // 1. Set one-time person properties
        val properties = mutableMapOf<String, Any>(
            "first_seen" to java.time.Instant.now().toString(),
            "initial_version" to "1.0.0",
            "is_internal" to isInternal
        )
        PostHog.setPersonProperties(properties)

        // 2. Start the funnel
        PostHog.capture("onboarding_started")
        
        // 3. Log the initial screen
        trackScreenView("Onboarding - Genres")
    }

    fun trackScreenView(screenName: String) {
        PostHog.screen(screenName)
    }

    fun trackImportClicked() {
        PostHog.capture("onboarding_import_clicked")
    }

    fun trackImportTypeSelected(type: String) {
        PostHog.capture(
            event = "onboarding_import_type_selected",
            properties = mapOf("type" to type)
        )
    }

    fun trackImportSuccess(type: String, count: Int) {
        PostHog.capture(
            event = "onboarding_import_success",
            properties = mapOf(
                "type" to type,
                "import_count" to count
            )
        )
    }

    fun trackGenresConfirmed(selectedGenres: Set<String>) {
        val genres = selectedGenres.toList()
        
        // Persona Mapping Logic
        val mapping = mapOf(
            "The Scholar" to listOf("Technology", "Science", "History", "Education"),
            "The Professional" to listOf("Business", "News", "Government"),
            "The Storyteller" to listOf("True Crime", "Fiction", "Arts"),
            "The Socialite" to listOf("Comedy", "TV & Film", "Society & Culture"),
            "The Lifestyle" to listOf("Health", "Religion & Spirituality", "Kids & Family", "Music"),
            "The Leisure" to listOf("Sports", "Leisure")
        )

        val hits = mutableMapOf<String, Int>()
        mapping.forEach { (persona, matchGenres) ->
            hits[persona] = genres.count { it in matchGenres }
        }

        val allPersonas = hits.filter { it.value > 0 }.keys.toList()
        val primaryPersona = if (genres.size >= 5 && allPersonas.size >= 3) {
            "The Polymath"
        } else {
            hits.maxByOrNull { it.value }?.key ?: "The Explorer"
        }

        // 1. Update Person Profile
        PostHog.setPersonProperties(mapOf(
            "preferred_genres" to genres,
            "primary_persona" to primaryPersona,
            "all_personas" to allPersonas
        ))

        // 2. Capture the Event
        PostHog.capture(
            event = "onboarding_genres_confirmed",
            properties = mapOf(
                "genres" to genres,
                "count" to genres.size,
                "persona" to primaryPersona
            )
        )
    }

    fun trackSearchBypass() {
        _uiState.update { it.copy(searchSource = "bypass") }
        PostHog.capture("onboarding_search_bypass_clicked")
    }

    fun trackSearchBack() {
        val source = _uiState.value.searchSource
        PostHog.capture("onboarding_search_back_clicked", properties = mapOf("source" to source))
        
        _uiState.update { state ->
            val backStep = if (state.selectedGenres.isNotEmpty()) OnboardingStep.PODCASTS else OnboardingStep.GENRES
            state.copy(
                currentStep = backStep,
                searchQuery = "",
                searchResults = emptyList(),
                searchSource = "none"
            )
        }
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
    
    fun navigateToRecommendations() {
        val genres = _uiState.value.selectedGenres
        if (genres.isNotEmpty()) {
            loadRecommendations(genres)
        }
        _uiState.update { it.copy(currentStep = OnboardingStep.PODCASTS) }
    }

    private fun loadRecommendations(genres: Set<String>) {
        _uiState.update { it.copy(isLoadingPodcasts = true) }
        viewModelScope.launch {
            try {
                val recommendations = podcastRepository.getTrendingPodcasts(genres.toList().first(), 10)
                _uiState.update { state ->
                    state.copy(
                        recommendedPodcasts = recommendations,
                        subscribedPodcastIds = recommendations.map { it.id }.toSet(),
                        initialSuggestionCount = recommendations.size,
                        isLoadingPodcasts = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingPodcasts = false) }
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
        _uiState.update { state ->
            val newSource = if (state.searchSource == "none") "supplement" else state.searchSource
            state.copy(currentStep = OnboardingStep.SEARCH, searchSource = newSource)
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        searchJob?.cancel()
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(400)
            val results = podcastRepository.searchPodcasts(cleaned)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }
    
    fun subscribeFromSearch(podcast: Podcast) {
        val source = _uiState.value.searchSource
        PostHog.capture(
            event = "onboarding_search_result_selected",
            properties = mapOf(
                "podcast_title" to podcast.title,
                "podcast_id" to podcast.id,
                "source" to source
            )
        )
        _uiState.update { state ->
            val newSubs = state.subscribedPodcastIds + podcast.id
            val newRecommendations = if (state.recommendedPodcasts.any { it.id == podcast.id }) {
                state.recommendedPodcasts
            } else {
                state.recommendedPodcasts + podcast
            }
            state.copy(
    }
    
    fun skipOnboarding(method: String = "skipped", onDone: () -> Unit) {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        
        PostHog.capture(
            event = "onboarding_completed",
            properties = mapOf(
                "method" to method,
                "suggestion_score" to "skipped"
            )
        )

        onDone()
    }
}
