package cx.aswin.boxcast.feature.onboarding

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.data.analytics.AnalyticsHelper
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cx.aswin.boxcast.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxcast.core.network.model.OnboardingPart
import cx.aswin.boxcast.core.network.model.OnboardingNextTurnRequest
import cx.aswin.boxcast.core.network.model.OnboardingCurriculumRequest
import cx.aswin.boxcast.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxcast.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxcast.core.network.model.OnboardingCurriculumEpisodeDto
import cx.aswin.boxcast.core.network.model.OnboardingQuery
import cx.aswin.boxcast.core.network.model.toPodcast
import cx.aswin.boxcast.core.model.Episode

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

enum class AiLoadingStage {
    IDLE,
    GENERATING_RESPONSE,
    SYNTHESIZING_PREFERENCES,
    FETCHING_CATALOGS,
    ASSEMBLING_FEED
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val selectedGenres: Set<String> = emptySet(),
    val selectedSubGenres: Set<String> = emptySet(),
    val listeningActivities: Set<String> = emptySet(),
    val preferredLengths: Set<String> = emptySet(),
    val activityGenreMap: Map<String, Set<String>> = emptyMap(),
    val lengthGenreMap: Map<String, Set<String>> = emptyMap(),
    val genreChartsPodcasts: List<Podcast> = emptyList(),
    val subscribedPodcastIds: Set<String> = emptySet(),
    val isLoadingPodcasts: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Podcast> = emptyList(),
    val isSearching: Boolean = false,
    val isCompleting: Boolean = false,
    val currentRegion: String = "us",
    val initialRegion: String = "us",
    val selectedPodcasts: Map<String, Podcast> = emptyMap(),
    // AI Onboarding fields
    val aiHistory: List<OnboardingHistoryEntry> = emptyList(),
    val aiAssistantMessage: String = "Hi! I'm boxcast. To start, what kind of a listener are you?",
    val aiOptions: List<String> = listOf(
        "Storyseeker | Serialized narratives, investigative series, and immersive audio dramas",
        "Deep Diver | Detailed analysis, long-form research, and intellectual essays",
        "Conversationalist | Candid guest interviews, unscripted discussions, and casual banter",
        "Chill Listener | Soothing voices, mindfulness sessions, and relaxing background talk"
    ),
    val aiCurrentTurn: Int = 1,
    val aiCustomInputText: String = "",
    val aiSelectedOptions: Set<String> = emptySet(),
    val isAiLoading: Boolean = false,
    val isSynthesizing: Boolean = false,
    val aiCurriculumRows: List<OnboardingCurriculumRowDto> = emptyList(),
    val aiLoadingStage: AiLoadingStage = AiLoadingStage.IDLE,
    val onboardingError: String? = null,
    val reachedSuggestionsViaAiFlow: Boolean = false
)

enum class OnboardingStep {
    WELCOME, GENRES, SUB_GENRES, ACTIVITY_PICKER, LENGTH_PICKER, SEARCH, AI_ONBOARDING, AI_SUGGESTIONS
}

class OnboardingViewModel(
    application: Application,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPrefs: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null
    private var recommendationJob: Job? = null

    private val turnHistoryState = mutableListOf<TurnState>()
    private var lastFailedAction: (() -> Unit)? = null
    
    data class TurnState(
        val assistantMessage: String,
        val options: List<String>,
        val history: List<OnboardingHistoryEntry>
    )

    init {
        // Retrieve and initialize region once on startup
        viewModelScope.launch {
            userPrefs.regionStream.take(1).collect { region ->
                _uiState.update { 
                    it.copy(
                         currentRegion = region,
                         initialRegion = region
                    ) 
                }
            }
        }
    }

    // ── Analytics Timing & Counters ────────────────────────────────
    private var onboardingStartMs: Long = 0L
    private var welcomeScreenStartMs: Long = 0L
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
    private fun getPodcastScreenTimeSpent(): Float {
        if (podcastScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - podcastScreenStartMs) / 1000f
    }

    /** Time spent on the search screen in seconds */
    private fun getSearchScreenTimeSpent(): Float {
        if (searchScreenStartMs == 0L) return 0f
        return (System.currentTimeMillis() - searchScreenStartMs) / 1000f
    }

    /** Called when the welcome screen composable first loads */
    fun onWelcomeScreenViewed() {
        if (!onboardingStartedFired) {
            onboardingStartMs = System.currentTimeMillis()
            welcomeScreenStartMs = System.currentTimeMillis()
            AnalyticsHelper.trackOnboardingStarted()
            onboardingStartedFired = true
        }
    }

    /** Called when the genre screen composable first loads */
    fun onGenreScreenViewed() {
        if (genreScreenStartMs == 0L) {
            genreScreenStartMs = System.currentTimeMillis()
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
    
    fun startOnboarding() {
        _uiState.update { it.copy(currentStep = OnboardingStep.AI_ONBOARDING) }
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

        _uiState.update { it.copy(currentStep = OnboardingStep.SUB_GENRES) }
    }

    fun toggleSubGenre(subGenre: String) {
        _uiState.update { state ->
            val newSubGenres = if (subGenre in state.selectedSubGenres) {
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
            val newActivities = if (activity in activities) {
                activities - activity
            } else {
                activities + activity
            }
            val newMap = if (activity in activities) {
                state.activityGenreMap - activity
            } else {
                state.activityGenreMap
            }
            state.copy(
                listeningActivities = newActivities,
                activityGenreMap = newMap
            )
        }
    }

    fun setGenresForActivity(activity: String, genres: Set<String>) {
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
            val newLengths = if (length in lengths) {
                lengths - length
            } else {
                lengths + length
            }
            val newMap = if (length in lengths) {
                state.lengthGenreMap - length
            } else {
                state.lengthGenreMap
            }
            state.copy(
                preferredLengths = newLengths,
                lengthGenreMap = newMap
            )
        }
    }

    fun setGenresForLength(length: String, genres: Set<String>) {
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
        _uiState.update { it.copy(currentStep = OnboardingStep.ACTIVITY_PICKER) }
    }

    fun continueToLengthPicker() {
        _uiState.update { it.copy(currentStep = OnboardingStep.LENGTH_PICKER) }
    }

    fun navigateBackFromSubGenres() {
        _uiState.update { it.copy(currentStep = OnboardingStep.GENRES) }
    }

    fun navigateBackFromActivityPicker() {
        _uiState.update { it.copy(currentStep = OnboardingStep.SUB_GENRES) }
    }

    fun navigateBackFromLengthPicker() {
        _uiState.update { it.copy(currentStep = OnboardingStep.ACTIVITY_PICKER) }
    }

    fun synthesizeGenreOnboarding() {
        val currentState = _uiState.value
        _uiState.update { it.copy(
            isLoadingPodcasts = true,
            currentStep = OnboardingStep.AI_SUGGESTIONS,
            onboardingError = null,
            reachedSuggestionsViaAiFlow = false
        ) }
        
        val finalAction: () -> Unit = {
            _uiState.update { it.copy(isLoadingPodcasts = true, onboardingError = null) }
            viewModelScope.launch {
                try {
                    // 1. Fetch charts podcasts in parallel
                    val chartsDeferred = async(Dispatchers.IO) {
                        val genresList = currentState.selectedGenres.toList()
                        val allPodcasts = mutableListOf<Podcast>()
                        val perGenreLimit = when {
                            genresList.size <= 2 -> 5
                            genresList.size <= 4 -> 3
                            else -> 2
                        }
                        for (genre in genresList) {
                            try {
                                val trending = podcastRepository.getTrendingPodcasts(
                                    country = currentState.currentRegion,
                                    category = genre,
                                    limit = perGenreLimit
                                )
                                allPodcasts.addAll(trending)
                            } catch (e: Exception) {
                                Log.e("OnboardingViewModel", "Failed to fetch trending for genre $genre", e)
                            }
                        }
                        allPodcasts.distinctBy { it.id }.shuffled().take(10)
                    }

                    // 2. Fetch AI curriculum rows from backend
                    val rowsDeferred = async(Dispatchers.IO) {
                        // Format activity string dynamically based on selections and mapped genres
                        val formattedActivities = currentState.listeningActivities.joinToString(", ") { act ->
                            val mappedGenres = currentState.activityGenreMap[act]
                            if (!mappedGenres.isNullOrEmpty()) {
                                "$act (focusing on ${mappedGenres.joinToString(", ")})"
                            } else {
                                act
                            }
                        }

                        // Format length string dynamically based on selections and mapped genres
                        val formattedLengths = currentState.preferredLengths.joinToString(", ") { len ->
                            val mappedGenres = currentState.lengthGenreMap[len]
                            if (!mappedGenres.isNullOrEmpty()) {
                                "$len (for ${mappedGenres.joinToString(", ")})"
                            } else {
                                len
                            }
                        }

                        val request = cx.aswin.boxcast.core.network.model.OnboardingGenreSynthRequest(
                            genres = currentState.selectedGenres.toList(),
                            subGenres = currentState.selectedSubGenres.toList(),
                            activity = formattedActivities,
                            length = formattedLengths,
                            country = currentState.currentRegion
                        )
                        val response = podcastRepository.api.onboardingGenreSynth(
                            publicKey = podcastRepository.publicKey,
                            request = request
                        ).execute()
                        if (response.isSuccessful && response.body() != null) {
                            response.body()!!.map { it.copy(episodes = emptyList()) }
                        } else {
                            throw Exception("Failed to load curriculum from genre synthesis")
                        }
                    }

                    val charts = chartsDeferred.await()
                    val rows = try {
                        rowsDeferred.await()
                    } catch (e: Exception) {
                        Log.e("OnboardingViewModel", "AI onboarding synthesis failed, falling back to charts", e)
                        emptyList()
                    }

                    val finalRows = if (rows.isEmpty()) {
                        val fallbackPodcastDtos = charts.map { pod ->
                            OnboardingCurriculumPodcastDto(
                                id = pod.id.toLongOrNull() ?: 0L,
                                title = pod.title,
                                author = pod.artist,
                                image = pod.imageUrl,
                                artwork = pod.imageUrl,
                                categories = mapOf("1" to pod.genre),
                                description = pod.description
                            )
                        }
                        if (fallbackPodcastDtos.isNotEmpty()) {
                            listOf(
                                OnboardingCurriculumRowDto(
                                    rowTitle = "Trending in your Genres",
                                    podcasts = fallbackPodcastDtos,
                                    episodes = emptyList()
                                )
                            )
                        } else {
                            throw Exception("AI synthesis and trending charts are both empty")
                        }
                    } else {
                        rows
                    }

                    // 3. Process default selections
                    val newPodcasts = finalRows.flatMap { it.podcasts }.map { it.toPodcast() }
                    val defaultSelectedIds = buildSet {
                        if (finalRows.size == 1) {
                            finalRows.firstOrNull()?.podcasts?.take(2)?.forEach { add(it.id.toString()) }
                        } else {
                            finalRows.forEach { row ->
                                row.podcasts.firstOrNull()?.let { add(it.id.toString()) }
                            }
                        }
                    }
                    val defaultSelectedPodcasts = newPodcasts.filter { it.id.toString() in defaultSelectedIds }.associateBy { it.id }

                    _uiState.update { state ->
                        state.copy(
                            aiCurriculumRows = finalRows,
                            genreChartsPodcasts = charts,
                            selectedPodcasts = state.selectedPodcasts + defaultSelectedPodcasts,
                            subscribedPodcastIds = state.subscribedPodcastIds + defaultSelectedIds,
                            isLoadingPodcasts = false,
                            onboardingError = null
                        )
                    }
                } catch (e: Exception) {
                    Log.e("OnboardingViewModel", "Error in synthesizeGenreOnboarding", e)
                    _uiState.update { state ->
                        state.copy(
                            isLoadingPodcasts = false,
                            onboardingError = "We encountered a temporary issue generating your curriculum. Let's try again."
                        )
                    }
                }
            }
        }
        lastFailedAction = finalAction
        finalAction()
    }

    fun setRegion(region: String) {
        _uiState.update { it.copy(currentRegion = region, isLoadingPodcasts = true) }
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val genresList = state.selectedGenres.toList()
                val allPodcasts = mutableListOf<Podcast>()
                val perGenreLimit = when {
                    genresList.size <= 2 -> 5
                    genresList.size <= 4 -> 3
                    else -> 2
                }
                val charts = withContext(Dispatchers.IO) {
                    for (genre in genresList) {
                        try {
                            val trending = podcastRepository.getTrendingPodcasts(
                                country = region,
                                category = genre,
                                limit = perGenreLimit
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
                        isLoadingPodcasts = false
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
            val newSelected = if (podcastId in state.subscribedPodcastIds) {
                state.selectedPodcasts - podcastId
            } else {
                val podcast = state.searchResults.find { it.id == podcastId }
                    ?: state.aiCurriculumRows.flatMap { it.podcasts }.map { it.toPodcast() }.find { it.id == podcastId }
                    ?: state.genreChartsPodcasts.find { it.id == podcastId }
                if (podcast != null) {
                    state.selectedPodcasts + (podcastId to podcast)
                } else {
                    state.selectedPodcasts
                }
            }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys
            )
        }
    }

    fun toggleAllCurriculumPodcasts() {
        _uiState.update { state ->
            val allPodcasts = state.aiCurriculumRows.flatMap { it.podcasts }.map { it.toPodcast() }
            val allIds = allPodcasts.map { it.id }.toSet()
            val allSelected = allIds.isNotEmpty() && allIds.all { it in state.subscribedPodcastIds }
            
            val newSelected = if (allSelected) {
                state.selectedPodcasts.filterKeys { it !in allIds }
            } else {
                state.selectedPodcasts + allPodcasts.associateBy { it.id }
            }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys
            )
        }
    }

    fun toggleAllPodcastsInRow(rowTitle: String) {
        _uiState.update { state ->
            val row = state.aiCurriculumRows.find { it.rowTitle == rowTitle } ?: return@update state
            val rowPodcasts = row.podcasts.map { it.toPodcast() }
            val rowIds = rowPodcasts.map { it.id }.toSet()
            val allSelected = rowIds.isNotEmpty() && rowIds.all { it in state.subscribedPodcastIds }
            
            val newSelected = if (allSelected) {
                state.selectedPodcasts.filterKeys { it !in rowIds }
            } else {
                state.selectedPodcasts + rowPodcasts.associateBy { it.id }
            }
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys
            )
        }
    }
    
    fun navigateToSearch() {
        // Determine entry point based on current step
        searchEntryPoint = when (_uiState.value.currentStep) {
            OnboardingStep.GENRES -> "genre_screen"
            OnboardingStep.WELCOME -> "welcome_screen"
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
    

    
    fun navigateBackToWelcome() {
        _uiState.update { it.copy(currentStep = OnboardingStep.WELCOME) }
    }
    
    fun navigateBackFromSearch() {
        val state = _uiState.value
        val exitDestination = if (state.selectedGenres.isNotEmpty()) "sub_genres_screen" else "genre_screen"

        // Analytics: Track search exit
        AnalyticsHelper.trackSearchExited(
            exitDestination = exitDestination,
            searchesPerformed = searchesPerformedCount,
            podcastsSubscribedInSearch = podcastsSubscribedInSearchCount,
            timeSpentOnSearchSeconds = getSearchScreenTimeSpent()
        )

        val backStep = if (state.selectedGenres.isNotEmpty()) OnboardingStep.SUB_GENRES else OnboardingStep.GENRES
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
            val newSelected = state.selectedPodcasts + (podcast.id to podcast)
            state.copy(
                selectedPodcasts = newSelected,
                subscribedPodcastIds = newSelected.keys
            )
        }

        // Analytics: Track podcast subscribed in search
        podcastsSubscribedInSearchCount++
        AnalyticsHelper.trackSearchPodcastSubscribed(
            podcastName = podcast.title,
            podcastId = podcast.id,
            totalSubscribedCount = _uiState.value.subscribedPodcastIds.size
        )
    }
    
    fun completeOnboarding(onDone: () -> Unit) {
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            val state = _uiState.value
            // Subscribe to all selected podcasts accumulated across regions and search
            val podcastsToSubscribe = state.selectedPodcasts.values
            for (podcast in podcastsToSubscribe) {
                subscriptionRepository.subscribe(podcast)
            }
            
            // Persist the home region preference selection upon completion
            userPrefs.setRegion(state.currentRegion)
            
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
            }

            // Save selected genres for future personalization
            prefs.edit().putStringSet("user_genres", state.selectedGenres).apply()

            onDone()
        }
    }
    
    fun skipOnboarding(onDone: () -> Unit) {
        // Analytics: Track skip
        val currentScreen = when (_uiState.value.currentStep) {
            OnboardingStep.WELCOME -> "welcome_screen"
            OnboardingStep.GENRES -> "genre_screen"
            OnboardingStep.SUB_GENRES -> "sub_genres_screen"
            OnboardingStep.ACTIVITY_PICKER -> "activity_picker_screen"
            OnboardingStep.LENGTH_PICKER -> "length_picker_screen"
            OnboardingStep.SEARCH -> "search_screen"
            OnboardingStep.AI_ONBOARDING -> "ai_onboarding_screen"
            OnboardingStep.AI_SUGGESTIONS -> "ai_suggestions_screen"
        }
        AnalyticsHelper.trackOnboardingSkipped(currentScreen, getGenreScreenTimeSpent(), getTotalOnboardingTime(), null)

        prefs.edit().putBoolean("onboarding_completed", true).apply()

        onDone()
    }

    fun updateAiCustomInput(text: String) {
        _uiState.update { state ->
            state.copy(
                aiCustomInputText = text,
                aiSelectedOptions = if (text.isNotEmpty()) emptySet() else state.aiSelectedOptions
            )
        }
    }

    fun toggleAiOption(option: String) {
        _uiState.update { state ->
            if (state.aiCustomInputText.isNotEmpty()) {
                return@update state
            }
            val currentSelected = state.aiSelectedOptions
            val newSelected = if (option in currentSelected) {
                currentSelected - option
            } else {
                currentSelected + option
            }
            state.copy(
                aiSelectedOptions = newSelected
            )
        }
    }

    fun switchToLegacyOnboarding() {
        _uiState.update { it.copy(currentStep = OnboardingStep.GENRES, onboardingError = null) }
    }

    fun retryLastAction() {
        val action = lastFailedAction
        if (action != null) {
            _uiState.update { it.copy(onboardingError = null) }
            action()
        }
    }

    fun sendAiTurnInput() {
        val currentState = _uiState.value
        if (currentState.isAiLoading) return
        val turnInput = (currentState.aiSelectedOptions.toList() + 
            if (currentState.aiCustomInputText.isNotBlank()) listOf(currentState.aiCustomInputText) else emptyList()
        ).joinToString(". ")

        if (turnInput.isBlank()) return

        // Push current turn state to back stack before updating
        turnHistoryState.add(
            TurnState(
                assistantMessage = currentState.aiAssistantMessage,
                options = currentState.aiOptions,
                history = currentState.aiHistory
            )
        )

        val userEntry = OnboardingHistoryEntry(
            role = "user",
            parts = listOf(OnboardingPart(text = turnInput))
        )
        val newHistory = currentState.aiHistory + userEntry

        _uiState.update {
            it.copy(
                aiHistory = newHistory,
                aiSelectedOptions = emptySet(),
                aiCustomInputText = "",
                isAiLoading = true,
                aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE,
                onboardingError = null
            )
        }

        val finalAction: () -> Unit = {
            _uiState.update {
                it.copy(
                    isAiLoading = true,
                    aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE,
                    onboardingError = null
                )
            }
            viewModelScope.launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val testInput = turnInput.trim().lowercase()
                    if (testInput == "test_fail" || testInput == "test_error") {
                        throw java.io.IOException("Simulated network failure")
                    } else if (testInput == "test_timeout") {
                        delay(50000)
                    }

                    val response = kotlinx.coroutines.withTimeout(45000) {
                        withContext(Dispatchers.IO) {
                            podcastRepository.api.getOnboardingNextTurn(
                                publicKey = podcastRepository.publicKey,
                                request = OnboardingNextTurnRequest(history = newHistory)
                            ).execute()
                        }
                    }

                    if (testInput == "test_delay" || testInput == "test_slow") {
                        val elapsed = System.currentTimeMillis() - startTime
                        val remainingDelay = 20000 - elapsed
                        if (remainingDelay > 0) {
                            delay(remainingDelay)
                        }
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val assistantEntry = OnboardingHistoryEntry(
                            role = "model",
                            parts = listOf(OnboardingPart(text = body.assistantMessage))
                        )
                        if (body.options.isEmpty()) {
                            _uiState.update { state ->
                                state.copy(
                                    aiHistory = state.aiHistory + assistantEntry,
                                    aiAssistantMessage = body.assistantMessage,
                                    aiOptions = emptyList(),
                                    aiCurrentTurn = state.aiCurrentTurn + 1,
                                    isAiLoading = false,
                                    aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                                    onboardingError = null
                                )
                            }
                            this@OnboardingViewModel.synthesizeAndBuildCurriculum(force = true)
                        } else {
                            _uiState.update { state ->
                                state.copy(
                                    aiHistory = state.aiHistory + assistantEntry,
                                    aiAssistantMessage = body.assistantMessage,
                                    aiOptions = body.options,
                                    aiCurrentTurn = state.aiCurrentTurn + 1,
                                    isAiLoading = false,
                                    aiLoadingStage = AiLoadingStage.IDLE,
                                    onboardingError = null
                                )
                            }
                        }
                    } else {
                        throw Exception("Server returned error ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("OnboardingViewModel", "Error in sendAiTurnInput", e)
                    val userFriendlyMsg = when (e) {
                        is kotlinx.coroutines.TimeoutCancellationException -> "Our AI is taking a moment to catch its breath. Let's try that again."
                        else -> "We encountered a temporary hiccup. Let's try that again."
                    }
                    _uiState.update { state ->
                        state.copy(
                            isAiLoading = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            onboardingError = userFriendlyMsg
                        )
                    }
                }
            }
        }
        lastFailedAction = finalAction
        finalAction()
    }

    fun synthesizeAndBuildCurriculum(force: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.isAiLoading && !force) return
        val turnInput = (currentState.aiSelectedOptions.toList() + 
            if (currentState.aiCustomInputText.isNotBlank()) listOf(currentState.aiCustomInputText) else emptyList()
        ).joinToString(". ")

        val newHistoryList = currentState.aiHistory.toMutableList()
        if (turnInput.isNotBlank()) {
            newHistoryList.add(
                OnboardingHistoryEntry(
                    role = "user",
                    parts = listOf(OnboardingPart(text = turnInput))
                )
            )
        }
        if (newHistoryList.isEmpty()) {
            newHistoryList.add(
                OnboardingHistoryEntry(
                    role = "user",
                    parts = listOf(OnboardingPart(text = "Recommend some top topics like True Crime or Tech"))
                )
            )
        }

        _uiState.update {
            it.copy(
                aiHistory = newHistoryList,
                aiSelectedOptions = emptySet(),
                aiCustomInputText = "",
                isAiLoading = true,
                isSynthesizing = true,
                aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                onboardingError = null
            )
        }

        val finalAction: () -> Unit = {
            _uiState.update {
                it.copy(
                    isAiLoading = true,
                    isSynthesizing = true,
                    aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                    onboardingError = null
                )
            }
            viewModelScope.launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val hasSlow = newHistoryList.any { h -> h.role == "user" && h.parts.any { p -> p.text.trim().lowercase().let { it == "test_slow" || it == "test_delay" } } }
                    val hasFail = newHistoryList.any { h -> h.role == "user" && h.parts.any { p -> p.text.trim().lowercase().let { it == "test_fail" || it == "test_error" } } }
                    val hasTimeout = newHistoryList.any { h -> h.role == "user" && h.parts.any { p -> p.text.trim().lowercase().let { it == "test_timeout" } } }

                    if (hasFail) {
                        throw java.io.IOException("Simulated network failure")
                    } else if (hasTimeout) {
                        delay(50000)
                    }

                    // Call Synthesize to get queries
                    val queries = kotlinx.coroutines.withTimeout(45000) {
                        val synthResponse = withContext(Dispatchers.IO) {
                            podcastRepository.api.onboardingSynthesize(
                                publicKey = podcastRepository.publicKey,
                                request = OnboardingNextTurnRequest(history = newHistoryList)
                            ).execute()
                        }
                        if (synthResponse.isSuccessful && synthResponse.body() != null) {
                            synthResponse.body()!!
                        } else {
                            throw Exception("Failed to synthesize preferences")
                        }
                    }

                    _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.FETCHING_CATALOGS) }

                    // Call Curriculum with synthesized queries
                    val rows = kotlinx.coroutines.withTimeout(45000) {
                        val curriculumResponse = withContext(Dispatchers.IO) {
                            podcastRepository.api.getOnboardingCurriculum(
                                publicKey = podcastRepository.publicKey,
                                request = OnboardingCurriculumRequest(
                                    queries = queries,
                                    country = currentState.currentRegion
                                )
                            ).execute()
                        }
                        if (curriculumResponse.isSuccessful && curriculumResponse.body() != null) {
                            curriculumResponse.body()!!.map {
                                it.copy(episodes = emptyList())
                            }
                        } else {
                            throw Exception("Failed to load curriculum from synthesis")
                        }
                    }

                    _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.ASSEMBLING_FEED) }

                    if (hasSlow) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val remainingDelay = 20000 - elapsed
                        if (remainingDelay > 0) {
                            delay(remainingDelay)
                        }
                    }

                    val newPodcasts = rows.flatMap { it.podcasts }.map { it.toPodcast() }
                    val defaultSelectedIds = buildSet {
                        if (rows.size == 1) {
                            rows.firstOrNull()?.podcasts?.take(2)?.forEach { add(it.id.toString()) }
                        } else {
                            rows.forEach { row ->
                                row.podcasts.firstOrNull()?.let { add(it.id.toString()) }
                            }
                        }
                    }
                    val defaultSelectedPodcasts = newPodcasts.filter { it.id.toString() in defaultSelectedIds }.associateBy { it.id }
                    
                    _uiState.update { state ->
                        val newSelected = state.selectedPodcasts + defaultSelectedPodcasts
                        state.copy(
                            aiHistory = newHistoryList,
                            aiCurriculumRows = rows,
                            aiCurrentTurn = 4,
                            isAiLoading = false,
                            isSynthesizing = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            selectedPodcasts = newSelected,
                            subscribedPodcastIds = defaultSelectedIds,
                            onboardingError = null
                        )
                    }
                } catch (e: Exception) {
                    Log.e("OnboardingViewModel", "Error in synthesizeAndBuildCurriculum", e)
                    try {
                        _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.FETCHING_CATALOGS) }
                        val trending = withContext(Dispatchers.IO) {
                            podcastRepository.getTrendingPodcasts(
                                country = currentState.currentRegion,
                                limit = 10
                            )
                        }
                        val dummyPodcastDtos = trending.map { pod ->
                            OnboardingCurriculumPodcastDto(
                                id = pod.id.toLongOrNull() ?: 0L,
                                title = pod.title,
                                author = pod.artist,
                                image = pod.imageUrl,
                                artwork = pod.imageUrl,
                                categories = mapOf("1" to pod.genre),
                                description = pod.description
                            )
                        }

                        val fallbackRow = OnboardingCurriculumRowDto(
                            rowTitle = "Trending Hits",
                            podcasts = dummyPodcastDtos,
                            episodes = emptyList()
                        )
                        val newPodcasts = listOf(fallbackRow).flatMap { it.podcasts }.map { it.toPodcast() }
                        val defaultSelectedIds = buildSet {
                            fallbackRow.podcasts.take(2).forEach { add(it.id.toString()) }
                        }
                        val defaultSelectedPodcasts = newPodcasts.filter { it.id.toString() in defaultSelectedIds }.associateBy { it.id }
                        _uiState.update { state ->
                            val newSelected = state.selectedPodcasts + defaultSelectedPodcasts
                            state.copy(
                                aiHistory = newHistoryList,
                                aiCurriculumRows = listOf(fallbackRow),
                                aiCurrentTurn = 4,
                                isAiLoading = false,
                                isSynthesizing = false,
                                aiLoadingStage = AiLoadingStage.IDLE,
                                selectedPodcasts = newSelected,
                                subscribedPodcastIds = defaultSelectedIds,
                                onboardingError = null
                            )
                        }
                    } catch (ex: Exception) {
                        Log.e("OnboardingViewModel", "Error in fallback synthesis", ex)
                        val userFriendlyMsg = when (e) {
                            is kotlinx.coroutines.TimeoutCancellationException -> "Our AI is taking a moment to build your feed. Let's try that again."
                            else -> "We encountered a temporary hiccup. Let's try that again."
                        }
                        _uiState.update { state ->
                            state.copy(
                                isAiLoading = false,
                                isSynthesizing = false,
                                aiLoadingStage = AiLoadingStage.IDLE,
                                onboardingError = userFriendlyMsg
                            )
                        }
                    }
                }
            }
        }
        lastFailedAction = finalAction
        finalAction()
    }

    fun navigateToSuggestions() {
        _uiState.update { it.copy(
            currentStep = OnboardingStep.AI_SUGGESTIONS,
            reachedSuggestionsViaAiFlow = true
        ) }
    }

    fun navigateBackFromSuggestions() {
        val nextStep = if (_uiState.value.reachedSuggestionsViaAiFlow) {
            OnboardingStep.AI_ONBOARDING
        } else {
            OnboardingStep.LENGTH_PICKER
        }
        _uiState.update { it.copy(currentStep = nextStep) }
    }

    fun navigateBackInAiOnboarding() {
        val currentState = _uiState.value
        if (currentState.aiCurrentTurn <= 1) {
            _uiState.update { it.copy(currentStep = OnboardingStep.WELCOME) }
        } else {
            if (turnHistoryState.isNotEmpty()) {
                val previousState = turnHistoryState.removeAt(turnHistoryState.size - 1)
                _uiState.update {
                    it.copy(
                        aiAssistantMessage = previousState.assistantMessage,
                        aiOptions = previousState.options,
                        aiHistory = previousState.history,
                        aiCurrentTurn = currentState.aiCurrentTurn - 1,
                        aiSelectedOptions = emptySet(),
                        aiCustomInputText = "",
                        isAiLoading = false,
                        isSynthesizing = false,
                        aiCurriculumRows = emptyList()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        aiCurrentTurn = 1,
                        aiHistory = emptyList(),
                        aiSelectedOptions = emptySet(),
                        aiCustomInputText = "",
                        isAiLoading = false,
                        isSynthesizing = false,
                        aiCurriculumRows = emptyList()
                    )
                }
            }
        }
    }

    fun finishAiOnboarding(onDone: () -> Unit) {
        if (_uiState.value.isCompleting) return
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val selectedIds = state.subscribedPodcastIds
                val rows = state.aiCurriculumRows
                val allCurriculumPodcasts = rows.flatMap { it.podcasts }.map { it.toPodcast() }.distinctBy { it.id }
                val podcastsToSubscribe = allCurriculumPodcasts.filter { it.id in selectedIds }
                for (podcast in podcastsToSubscribe) {
                    subscriptionRepository.subscribe(podcast)
                }

                userPrefs.setRegion(state.currentRegion)
                prefs.edit().putBoolean("onboarding_completed", true).apply()

                val userGenres = _uiState.value.aiHistory
                    .filter { it.role == "user" }
                    .flatMap { it.parts }
                    .map { it.text }
                    .toSet()
                prefs.edit().putStringSet("user_genres", userGenres).apply()

                onDone()
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error in finishAiOnboarding", e)
                prefs.edit().putBoolean("onboarding_completed", true).apply()
                onDone()
            }
        }
    }

    fun markOnboardingCompletedSilent(onDone: () -> Unit) {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        onDone()
    }
}
