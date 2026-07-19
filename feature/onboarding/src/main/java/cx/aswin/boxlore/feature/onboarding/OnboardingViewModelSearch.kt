package cx.aswin.boxlore.feature.onboarding

import android.util.Log
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingSelectedShowDto
import cx.aswin.boxlore.core.network.model.OnboardingSimilarShowsRequest
import cx.aswin.boxlore.core.network.model.toPodcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun OnboardingViewModel.navigateToSearch() {
    // Determine entry point based on current step
    searchEntryPoint =
        when (_uiState.value.currentStep) {
            OnboardingStep.GENRES -> "genre_screen"
            OnboardingStep.WELCOME -> "welcome_screen"
            else -> "genre_screen"
        }

    if (searchEntryPoint == "welcome_screen") {
        AnalyticsHelper.trackOnboardingFlowSelected("search_direct")
    }

    // Reset search counters for this session
    searchesPerformedCount = 0
    podcastsSubscribedInSearchCount = 0
    searchScreenStartMs = System.currentTimeMillis()

    _uiState.update {
        it.copy(
            currentStep = OnboardingStep.SEARCH,
            searchQuery = "",
            searchResults = emptyList(),
            selectedSearchGenre = null,
        )
    }
    selectSearchGenre(null)
}

/**
 * Jump from the AI chat to the search flow, optionally pre-filling the
 * query the AI detected (e.g. the user typed a specific show name).
 */
internal fun OnboardingViewModel.switchToSearchFromAi(prefillQuery: String? = null) {
    AnalyticsHelper.trackOnboardingAiSearchRedirect(
        turnNumber = _uiState.value.aiCurrentTurn,
        suggestedQuery = prefillQuery,
    )
    searchEntryPoint = "ai_onboarding"
    searchesPerformedCount = 0
    podcastsSubscribedInSearchCount = 0
    searchScreenStartMs = System.currentTimeMillis()

    _uiState.update {
        it.copy(
            currentStep = OnboardingStep.SEARCH,
            searchQuery = "",
            searchResults = emptyList(),
            selectedSearchGenre = null,
        )
    }
    selectSearchGenre(null)
    if (!prefillQuery.isNullOrBlank()) {
        updateSearchQuery(prefillQuery)
    }
}

internal fun OnboardingViewModel.selectSearchGenre(genreValue: String?) {
    _uiState.update { it.copy(selectedSearchGenre = genreValue, isPopularLoading = true) }
    viewModelScope.launch {
        try {
            val region = _uiState.value.currentRegion
            val trending =
                withContext(Dispatchers.IO) {
                    podcastRepository.getTrendingPodcasts(
                        country = region,
                        category = genreValue,
                        limit = 20,
                    )
                }
            trending.forEach { seenPodcasts[it.id] = it }
            _uiState.update {
                it.copy(
                    popularPodcasts = trending,
                    isPopularLoading = false,
                )
            }
        } catch (e: Exception) {
            Log.e("OnboardingViewModel", "Error loading popular podcasts for search", e)
            _uiState.update {
                it.copy(
                    popularPodcasts = emptyList(),
                    isPopularLoading = false,
                )
            }
        }
    }
}

internal fun OnboardingViewModel.toggleSubscriptionFromSearch(podcast: Podcast) {
    _uiState.update { state ->
        val isSubbed = podcast.id in state.subscribedPodcastIds
        val newSelected =
            if (isSubbed) {
                state.selectedPodcasts - podcast.id
            } else {
                state.selectedPodcasts + (podcast.id to podcast)
            }
        state.copy(
            selectedPodcasts = newSelected,
            subscribedPodcastIds = newSelected.keys,
        )
    }

    val isNowSubbed = podcast.id in _uiState.value.subscribedPodcastIds
    if (isNowSubbed) {
        // Analytics: Track podcast subscribed in search
        podcastsSubscribedInSearchCount++
        AnalyticsHelper.trackSearchPodcastSubscribed(
            podcastName = podcast.title,
            podcastId = podcast.id,
            totalSubscribedCount = _uiState.value.subscribedPodcastIds.size,
        )
    }
}

internal fun OnboardingViewModel.navigateBackToWelcome() {
    _uiState.update { it.copy(currentStep = OnboardingStep.WELCOME) }
}

internal fun OnboardingViewModel.navigateBackFromSearch() {
    val state = _uiState.value
    val backStep =
        OnboardingSearchBackStep.resolve(
            searchEntryPoint = searchEntryPoint,
            selectedGenres = state.selectedGenres,
        )
    _uiState.update { it.copy(currentStep = backStep, searchQuery = "", searchResults = emptyList()) }
}

internal fun OnboardingViewModel.isSearchFromAiChat(): Boolean = searchEntryPoint == "ai_onboarding"

/**
 * Ends a search side-trip started from the AI chat: keeps any subscriptions
 * picked in search and returns to the chat so the taste profile can continue.
 */
internal fun OnboardingViewModel.returnToAiChatFromSearch() {
    _uiState.update {
        it.copy(
            currentStep = OnboardingStep.AI_ONBOARDING,
            searchQuery = "",
            searchResults = emptyList(),
        )
    }
}

internal fun OnboardingViewModel.updateSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }

    // Debounce search
    searchJob?.cancel()
    val cleaned = query.trim()
    if (cleaned.isEmpty()) {
        _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
        return
    }

    // Eager client-side matching from seen podcasts (0ms delay)
    val localMatches =
        seenPodcasts.values
            .filter { podcast ->
                podcast.title.contains(cleaned, ignoreCase = true) ||
                    podcast.artist.contains(cleaned, ignoreCase = true)
            }.sortedBy { it.title }

    if (localMatches.isNotEmpty()) {
        _uiState.update { it.copy(searchResults = localMatches) }
    }

    searchJob =
        viewModelScope.launch {
            // Only show loader if we have no local matches to display
            if (localMatches.isEmpty()) {
                _uiState.update { it.copy(isSearching = true) }
            }
            delay(300) // Debounce (aligned with main explore search)

            try {
                val results = podcastRepository.searchPodcasts(cleaned)
                results.forEach { seenPodcasts[it.id] = it }

                // Combine remote results (priority) with local matches to prevent flickering
                val seenIds = mutableSetOf<String>()
                val combined = mutableListOf<Podcast>()
                results.forEach {
                    if (seenIds.add(it.id)) {
                        combined.add(it)
                    }
                }
                localMatches.forEach {
                    if (seenIds.add(it.id)) {
                        combined.add(it)
                    }
                }

                _uiState.update { it.copy(searchResults = combined, isSearching = false) }

                // Analytics: Track search performed
                searchesPerformedCount++
                AnalyticsHelper.trackSearchPerformed(cleaned, results.size)
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Search error", e)
                _uiState.update { it.copy(isSearching = false) }
            }
        }
}

internal fun OnboardingViewModel.subscribeFromSearch(podcast: Podcast) {
    _uiState.update { state ->
        val newSelected = state.selectedPodcasts + (podcast.id to podcast)
        state.copy(
            selectedPodcasts = newSelected,
            subscribedPodcastIds = newSelected.keys,
        )
    }

    // Analytics: Track podcast subscribed in search
    podcastsSubscribedInSearchCount++
    AnalyticsHelper.trackSearchPodcastSubscribed(
        podcastName = podcast.title,
        podcastId = podcast.id,
        totalSubscribedCount = _uiState.value.subscribedPodcastIds.size,
    )
}

internal fun OnboardingViewModel.completeOnboarding(onDone: () -> Unit) {
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
        boxcastPrefs.setOnboardingCompleted(true)

        // Analytics: If completing from search screen, fire search_done variant
        if (state.currentStep == OnboardingStep.SEARCH) {
            val subscribedTitles = state.subscribedPodcastIds.map { seenPodcasts[it]?.title ?: it }
            AnalyticsHelper.trackOnboardingSearchDone(
                entryPoint = searchEntryPoint,
                totalSubscribedCount = state.subscribedPodcastIds.size,
                subscribedPodcastsList = subscribedTitles,
                searchesPerformed = searchesPerformedCount,
                timeSpentOnSearchSeconds = getSearchScreenTimeSpent(),
                totalOnboardingTimeSeconds = getTotalOnboardingTime(),
            )
        }

        // Save selected genres for future personalization
        boxcastPrefs.setUserGenres(state.selectedGenres)

        onDone()
    }
}

internal fun OnboardingViewModel.generateRecommendationsFromSearch() {
    val currentState = _uiState.value
    val selectedShows = currentState.selectedPodcasts.values.toList()
    if (selectedShows.isEmpty()) return

    _uiState.update {
        it.copy(
            currentStep = OnboardingStep.AI_SUGGESTIONS,
            isAiLoading = true,
            isSynthesizing = true,
            aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
            onboardingError = null,
            reachedSuggestionsViaSearchFlow = true,
            reachedSuggestionsViaAiFlow = false,
        )
    }

    val finalAction: () -> Unit = {
        _uiState.update {
            it.copy(
                isAiLoading = true,
                isSynthesizing = true,
                aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                onboardingError = null,
            )
        }
        viewModelScope.launch {
            try {
                // Subscribe to manually selected shows first
                for (podcast in selectedShows) {
                    subscriptionRepository.subscribe(podcast)
                }

                val request =
                    OnboardingSimilarShowsRequest(
                        shows =
                            selectedShows.distinctBy { it.title.lowercase().trim() }.take(20).map {
                                OnboardingSelectedShowDto(
                                    title = it.title,
                                    description = it.description ?: "",
                                )
                            },
                        country = currentState.currentRegion,
                    )

                val response =
                    withContext(Dispatchers.IO) {
                        podcastRepository.api
                            .getSimilarShows(
                                publicKey = podcastRepository.publicKey,
                                request = request,
                            ).execute()
                    }

                if (response.isSuccessful && response.body() != null) {
                    val rows =
                        response.body()!!.map {
                            it.copy(episodes = emptyList())
                        }

                    val newPodcasts = rows.flatMap { it.podcasts }.map { it.toPodcast() }
                    // Filter out podcasts that are already in selectedPodcasts to avoid resetting selections
                    val defaultSelectedIds =
                        buildSet {
                            rows.forEach { row ->
                                row.podcasts.firstOrNull()?.let { add(it.id.toString()) }
                            }
                        }
                    // Only auto-select recommendations that are NOT already manually selected
                    val recommendationsToSelect =
                        newPodcasts.filter {
                            it.id in defaultSelectedIds &&
                                it.id !in currentState.selectedPodcasts.keys
                        }

                    _uiState.update { state ->
                        val newSelected = state.selectedPodcasts + recommendationsToSelect.associateBy { it.id }
                        state.copy(
                            aiCurriculumRows = rows,
                            isAiLoading = false,
                            isSynthesizing = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            selectedPodcasts = newSelected,
                            subscribedPodcastIds = newSelected.keys,
                            onboardingError = null,
                        )
                    }
                } else {
                    throw Exception("Failed to load similar shows from backend: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error in generateRecommendationsFromSearch", e)
                _uiState.update { state ->
                    state.copy(
                        isAiLoading = false,
                        isSynthesizing = false,
                        aiLoadingStage = AiLoadingStage.IDLE,
                        onboardingError = "We encountered a temporary issue generating recommendations. Let's try again.",
                    )
                }
            }
        }
    }
    lastFailedAction = finalAction
    finalAction()
}

fun OnboardingViewModel.generateRecommendationsFromOpml(importedPodcasts: List<Podcast>) {
    if (importedPodcasts.isEmpty()) return

    _uiState.update {
        it.copy(
            currentStep = OnboardingStep.AI_SUGGESTIONS,
            isAiLoading = true,
            isSynthesizing = true,
            aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
            onboardingError = null,
            reachedSuggestionsViaSearchFlow = false,
            reachedSuggestionsViaAiFlow = false,
            reachedSuggestionsViaOpmlFlow = true,
            selectedPodcasts = importedPodcasts.associateBy { p -> p.id },
        )
    }

    val finalAction: () -> Unit = {
        _uiState.update {
            it.copy(
                isAiLoading = true,
                isSynthesizing = true,
                aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                onboardingError = null,
            )
        }
        viewModelScope.launch {
            try {
                val request =
                    OnboardingSimilarShowsRequest(
                        shows =
                            importedPodcasts.distinctBy { it.title.lowercase().trim() }.take(20).map {
                                OnboardingSelectedShowDto(
                                    title = it.title,
                                    description = it.description ?: "",
                                )
                            },
                        country = _uiState.value.currentRegion,
                    )

                val response =
                    withContext(Dispatchers.IO) {
                        podcastRepository.api
                            .getSimilarShows(
                                publicKey = podcastRepository.publicKey,
                                request = request,
                            ).execute()
                    }

                if (response.isSuccessful && response.body() != null) {
                    val rows =
                        response.body()!!.map {
                            it.copy(episodes = emptyList())
                        }

                    val newPodcasts = rows.flatMap { it.podcasts }.map { it.toPodcast() }
                    val defaultSelectedIds =
                        buildSet {
                            rows.forEach { row ->
                                row.podcasts.firstOrNull()?.let { add(it.id.toString()) }
                            }
                        }
                    val recommendationsToSelect =
                        newPodcasts.filter {
                            it.id in defaultSelectedIds &&
                                it.id !in importedPodcasts.map { p -> p.id }
                        }

                    _uiState.update { state ->
                        val newSelected = state.selectedPodcasts + recommendationsToSelect.associateBy { it.id }
                        state.copy(
                            aiCurriculumRows = rows,
                            isAiLoading = false,
                            isSynthesizing = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            selectedPodcasts = newSelected,
                            subscribedPodcastIds = newSelected.keys,
                            onboardingError = null,
                        )
                    }
                } else {
                    throw Exception("Failed to load similar shows from backend: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error in generateRecommendationsFromOpml", e)
                _uiState.update { state ->
                    state.copy(
                        isAiLoading = false,
                        isSynthesizing = false,
                        aiLoadingStage = AiLoadingStage.IDLE,
                        onboardingError = "We encountered a temporary issue generating recommendations. Let's try again.",
                    )
                }
            }
        }
    }
    lastFailedAction = finalAction
    finalAction()
}

internal fun OnboardingViewModel.skipOnboarding(onDone: () -> Unit) {
    val currentScreen =
        when (_uiState.value.currentStep) {
            OnboardingStep.WELCOME -> "welcome_screen"
            OnboardingStep.GENRES -> "genre_screen"
            OnboardingStep.SUB_GENRES -> "sub_genres_screen"
            OnboardingStep.ACTIVITY_PICKER -> "activity_picker_screen"
            OnboardingStep.LENGTH_PICKER -> "length_picker_screen"
            OnboardingStep.SEARCH -> "search_screen"
            OnboardingStep.AI_ONBOARDING -> "ai_onboarding_screen"
            OnboardingStep.AI_SUGGESTIONS -> "ai_suggestions_screen"
        }
    AnalyticsHelper.trackOnboardingSkipped(currentScreen, getTotalOnboardingTime())

    boxcastPrefs.setOnboardingCompleted(true)

    onDone()
}
