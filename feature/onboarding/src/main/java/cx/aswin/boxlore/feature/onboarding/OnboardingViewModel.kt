package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.toPodcast
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class OnboardingViewModel(
    application: Application,
    internal val podcastRepository: PodcastRepository,
    internal val subscriptionRepository: SubscriptionRepository,
    internal val userPrefs: UserPreferencesRepository,
) : AndroidViewModel(application) {
    internal val boxcastPrefs = BoxcastPrefs(application)

    internal val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    internal var searchJob: Job? = null
    internal var recommendationJob: Job? = null

    internal val turnHistoryState = mutableListOf<TurnState>()
    internal var lastFailedAction: (() -> Unit)? = null

    internal data class TurnState(
        val assistantMessage: String,
        val options: List<String>,
        val history: List<OnboardingHistoryEntry>,
    )

    init {
        // Retrieve and initialize region once on startup
        viewModelScope.launch {
            userPrefs.regionStream.take(1).collect { region ->
                _uiState.update {
                    it.copy(
                        currentRegion = region,
                        initialRegion = region,
                    )
                }
            }
        }
    }

    // ── Analytics Timing & Counters ────────────────────────────────
    internal var onboardingStartMs: Long = 0L
    internal var welcomeScreenStartMs: Long = 0L
    internal var genreScreenStartMs: Long = 0L
    internal var podcastScreenStartMs: Long = 0L
    internal var searchScreenStartMs: Long = 0L
    internal var searchesPerformedCount: Int = 0
    internal var podcastsSubscribedInSearchCount: Int = 0
    internal var searchEntryPoint: String = "genre_screen"
    internal var onboardingEntryPoint: String = "welcome_screen"
    internal val seenPodcasts = ConcurrentHashMap<String, Podcast>()
    internal var onboardingStartedFired: Boolean = false
    internal var didScrollSuggestions: Boolean = false

    internal var turnStartMs: Long = 0L
    internal var synthesisStartMs: Long = 0L
    internal var currentStepStartMs: Long = 0L
    internal var didSwitchFromAi: Boolean = false

    /** Total time since the onboarding flow began */
    fun getTotalOnboardingTime(): Float {
        if (onboardingStartMs == 0L) return 0f
        return (System.currentTimeMillis() - onboardingStartMs) / 1000f
    }

    /** Time spent on the welcome screen in seconds */
    fun getWelcomeScreenTimeSpent(): Float {
        if (welcomeScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - welcomeScreenStartMs) / 1000f
    }

    /** Time spent on the genre screen in seconds */
    fun getGenreScreenTimeSpent(): Float {
        if (genreScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - genreScreenStartMs) / 1000f
    }

    /** Time spent on the podcast screen in seconds */
    internal fun getPodcastScreenTimeSpent(): Float {
        if (podcastScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - podcastScreenStartMs) / 1000f
    }

    /** Time spent on the search screen in seconds */
    internal fun getSearchScreenTimeSpent(): Float {
        if (searchScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - searchScreenStartMs) / 1000f
    }

    /** Called when the welcome screen composable first loads */
    fun onWelcomeScreenViewed() {
        if (!onboardingStartedFired) {
            onboardingStartMs = System.currentTimeMillis()
            welcomeScreenStartMs = System.currentTimeMillis()
            currentStepStartMs = System.currentTimeMillis()
            AnalyticsHelper.trackOnboardingStarted()
            onboardingStartedFired = true
        }
    }

    /** Called when the genre screen composable first loads */
    fun onGenreScreenViewed() {
        if (genreScreenStartMs == 0L) {
            genreScreenStartMs = System.currentTimeMillis()
        }
        currentStepStartMs = System.currentTimeMillis()
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

    fun isOnboardingCompleted(): Boolean = boxcastPrefs.isOnboardingCompleted()

    fun startOnboarding(entryPoint: String = "welcome_screen") {
        onboardingEntryPoint = entryPoint
        if (entryPoint == "home_import_banner") {
            AnalyticsHelper.trackOnboardingStarted("home_import_banner")
        }
        turnHistoryState.clear()
        _uiState.value = OnboardingUiState().copy(currentStep = OnboardingStep.AI_ONBOARDING)
        turnStartMs = System.currentTimeMillis()
        onboardingStartMs = System.currentTimeMillis()
        didSwitchFromAi = false
        AnalyticsHelper.trackOnboardingFlowSelected("ai_chat", entryPoint)
    }

    fun toggleGenre(genre: String) {
        _uiState.update { state ->
            val newGenres =
                if (genre in state.selectedGenres) {
                    state.selectedGenres - genre
                } else {
                    state.selectedGenres + genre
                }
            state.copy(selectedGenres = newGenres)
        }
    }

    fun continueToRecommendations() {
        val selectedGenres = _uiState.value.selectedGenres
        val timeSpent = (System.currentTimeMillis() - currentStepStartMs) / 1000f
        AnalyticsHelper.trackOnboardingManualStepCompleted("genres", selectedGenres.size, selectedGenres.toList(), timeSpent)

        _uiState.update { it.copy(currentStep = OnboardingStep.SUB_GENRES) }
        currentStepStartMs = System.currentTimeMillis()
    }

    fun toggleSubGenre(subGenre: String) {
        _uiState.update { state ->
            val newSubGenres =
                if (subGenre in state.selectedSubGenres) {
                    state.selectedSubGenres - subGenre
                } else {
                    state.selectedSubGenres + subGenre
                }
            state.copy(selectedSubGenres = newSubGenres)
        }
    }

    fun toggleListeningActivity(activity: String) {
        _uiState.update { state ->
            val activities = state.listeningActivities
            val newActivities =
                if (activity in activities) {
                    activities - activity
                } else {
                    activities + activity
                }
            val newMap =
                if (activity in activities) {
                    state.activityGenreMap - activity
                } else {
                    state.activityGenreMap
                }
            state.copy(
                listeningActivities = newActivities,
                activityGenreMap = newMap,
            )
        }
    }

    fun setGenresForActivity(
        activity: String,
        genres: Set<String>,
    ) {
        _uiState.update { state ->
            val newMap = state.activityGenreMap.toMutableMap()
            if (genres.isEmpty()) {
                newMap.remove(activity)
            } else {
                newMap[activity] = genres
            }
            state.copy(activityGenreMap = newMap)
        }
    }

    fun togglePreferredLength(length: String) {
        _uiState.update { state ->
            val lengths = state.preferredLengths
            val newLengths =
                if (length in lengths) {
                    lengths - length
                } else {
                    lengths + length
                }
            val newMap =
                if (length in lengths) {
                    state.lengthGenreMap - length
                } else {
                    state.lengthGenreMap
                }
            state.copy(
                preferredLengths = newLengths,
                lengthGenreMap = newMap,
            )
        }
    }

    fun setGenresForLength(
        length: String,
        genres: Set<String>,
    ) {
        _uiState.update { state ->
            val newMap = state.lengthGenreMap.toMutableMap()
            if (genres.isEmpty()) {
                newMap.remove(length)
            } else {
                newMap[length] = genres
            }
            state.copy(lengthGenreMap = newMap)
        }
    }

    fun continueToActivityPicker() {
        val selectedSubGenres = _uiState.value.selectedSubGenres
        val timeSpent = (System.currentTimeMillis() - currentStepStartMs) / 1000f
        AnalyticsHelper.trackOnboardingManualStepCompleted("sub_genres", selectedSubGenres.size, selectedSubGenres.toList(), timeSpent)

        _uiState.update { it.copy(currentStep = OnboardingStep.ACTIVITY_PICKER) }
        currentStepStartMs = System.currentTimeMillis()
    }

    fun continueToLengthPicker() {
        val selectedActivities = _uiState.value.listeningActivities
        val timeSpent = (System.currentTimeMillis() - currentStepStartMs) / 1000f
        AnalyticsHelper.trackOnboardingManualStepCompleted("activities", selectedActivities.size, selectedActivities.toList(), timeSpent)

        _uiState.update { it.copy(currentStep = OnboardingStep.LENGTH_PICKER) }
        currentStepStartMs = System.currentTimeMillis()
    }

    fun navigateBackFromSubGenres() {
        _uiState.update { it.copy(currentStep = OnboardingStep.GENRES) }
        currentStepStartMs = System.currentTimeMillis()
    }

    fun navigateBackFromActivityPicker() {
        _uiState.update { it.copy(currentStep = OnboardingStep.SUB_GENRES) }
        currentStepStartMs = System.currentTimeMillis()
    }

    fun navigateBackFromLengthPicker() {
        _uiState.update { it.copy(currentStep = OnboardingStep.ACTIVITY_PICKER) }
        currentStepStartMs = System.currentTimeMillis()
    }

    fun setRegion(region: String) {
        _uiState.update { it.copy(currentRegion = region, isLoadingPodcasts = true) }
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val genresList = state.selectedGenres.toList()
                val allPodcasts = mutableListOf<Podcast>()
                val perGenreLimit = OnboardingGenreLimits.perGenreTrendingLimit(genresList.size)
                val charts =
                    withContext(Dispatchers.IO) {
                        for (genre in genresList) {
                            try {
                                val trending =
                                    podcastRepository.getTrendingPodcasts(
                                        country = region,
                                        category = genre,
                                        limit = perGenreLimit,
                                    )
                                allPodcasts.addAll(trending)
                            } catch (e: Exception) {
                                Log.e("OnboardingViewModel", "Failed to fetch trending for genre $genre in region $region", e)
                            }
                        }
                        allPodcasts.distinctBy { it.id }.shuffled().take(10)
                    }
                _uiState.update { state ->
                    state.copy(
                        genreChartsPodcasts = charts,
                        isLoadingPodcasts = false,
                    )
                }
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error in setRegion reload", e)
                _uiState.update { it.copy(isLoadingPodcasts = false) }
            }
        }
    }

    fun togglePodcastSubscription(podcastId: String) {
        _uiState.update { state ->
            val newSelected =
                if (podcastId in state.subscribedPodcastIds) {
                    state.selectedPodcasts - podcastId
                } else {
                    val podcast =
                        state.searchResults.find { it.id == podcastId }
                            ?: state.aiCurriculumRows
                                .flatMap { it.podcasts }
                                .map { it.toPodcast() }
                                .find { it.id == podcastId }
                            ?: state.genreChartsPodcasts.find { it.id == podcastId }
                            ?: state.popularPodcasts.find { it.id == podcastId }
                    if (podcast != null) {
                        state.selectedPodcasts + (podcastId to podcast)
                    } else {
                        state.selectedPodcasts
                    }
                }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys,
            )
        }
    }

    fun toggleAllCurriculumPodcasts() {
        _uiState.update { state ->
            val allPodcasts = state.aiCurriculumRows.flatMap { it.podcasts }.map { it.toPodcast() }
            val allIds = allPodcasts.map { it.id }.toSet()
            val allSelected = allIds.isNotEmpty() && allIds.all { it in state.subscribedPodcastIds }

            val newSelected =
                if (allSelected) {
                    state.selectedPodcasts.filterKeys { it !in allIds }
                } else {
                    state.selectedPodcasts + allPodcasts.associateBy { it.id }
                }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys,
            )
        }
    }

    fun toggleAllPodcastsInRow(rowTitle: String) {
        _uiState.update { state ->
            val row = state.aiCurriculumRows.find { it.rowTitle == rowTitle } ?: return@update state
            val rowPodcasts = row.podcasts.map { it.toPodcast() }
            val rowIds = rowPodcasts.map { it.id }.toSet()
            val allSelected = rowIds.isNotEmpty() && rowIds.all { it in state.subscribedPodcastIds }

            val newSelected =
                if (allSelected) {
                    state.selectedPodcasts.filterKeys { it !in rowIds }
                } else {
                    state.selectedPodcasts + rowPodcasts.associateBy { it.id }
                }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys,
            )
        }
    }

    override fun onCleared() {
        if (onboardingStartedFired && !boxcastPrefs.isOnboardingCompleted()) {
            val state = _uiState.value
            val lastStep =
                when (state.currentStep) {
                    OnboardingStep.WELCOME -> "welcome"
                    OnboardingStep.GENRES -> "genres"
                    OnboardingStep.SUB_GENRES -> "sub_genres"
                    OnboardingStep.ACTIVITY_PICKER -> "activities"
                    OnboardingStep.LENGTH_PICKER -> "lengths"
                    OnboardingStep.SEARCH -> "search"
                    OnboardingStep.AI_ONBOARDING -> "ai_chat"
                    OnboardingStep.AI_SUGGESTIONS -> "ai_suggestions"
                }
            val flowType =
                when {
                    state.reachedSuggestionsViaAiFlow || state.currentStep == OnboardingStep.AI_ONBOARDING -> "ai_chat"
                    state.reachedSuggestionsViaSearchFlow || state.currentStep == OnboardingStep.SEARCH -> "search"
                    state.reachedSuggestionsViaOpmlFlow -> "import"
                    state.currentStep == OnboardingStep.WELCOME -> "welcome"
                    else -> "manual_genre"
                }
            AnalyticsHelper.trackOnboardingAbandoned(
                lastStep = lastStep,
                flowType = flowType,
                timeSpentSeconds = getTotalOnboardingTime().toInt(),
                subscribedCount = state.subscribedPodcastIds.size,
            )
        }
        super.onCleared()
    }
}
