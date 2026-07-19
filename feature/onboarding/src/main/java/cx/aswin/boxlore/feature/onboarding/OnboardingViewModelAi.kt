package cx.aswin.boxlore.feature.onboarding

import android.util.Log
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRequest
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingNextTurnRequest
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.network.model.toPodcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun OnboardingViewModel.updateAiCustomInput(text: String) {
    _uiState.update { state ->
        state.copy(
            aiCustomInputText = text,
            aiSelectedOptions = if (text.isNotEmpty()) emptySet() else state.aiSelectedOptions,
        )
    }
}

internal fun OnboardingViewModel.toggleAiOption(option: String) {
    _uiState.update { state ->
        if (state.aiCustomInputText.isNotEmpty()) {
            return@update state
        }
        val currentSelected = state.aiSelectedOptions
        val newSelected =
            if (option in currentSelected) {
                currentSelected - option
            } else {
                currentSelected + option
            }
        state.copy(
            aiSelectedOptions = newSelected,
        )
    }
}

internal fun OnboardingViewModel.switchToLegacyOnboarding() {
    didSwitchFromAi = true
    _uiState.update { it.copy(currentStep = OnboardingStep.GENRES, onboardingError = null) }
    currentStepStartMs = System.currentTimeMillis()
}

internal fun OnboardingViewModel.retryLastAction() {
    val action = lastFailedAction
    if (action != null) {
        _uiState.update { it.copy(onboardingError = null) }
        action()
    }
}

internal fun OnboardingViewModel.sendAiTurnInput() {
    val currentState = _uiState.value
    if (currentState.isAiLoading) return
    val turnInput =
        (
            currentState.aiSelectedOptions.toList() +
                if (currentState.aiCustomInputText.isNotBlank()) listOf(currentState.aiCustomInputText) else emptyList()
        ).joinToString(". ")

    if (turnInput.isBlank()) return

    val turnTime = (System.currentTimeMillis() - turnStartMs) / 1000f
    AnalyticsHelper.trackOnboardingAiTurnSubmitted(
        turnNumber = currentState.aiCurrentTurn,
        selectedOptions = currentState.aiSelectedOptions,
        customInputText = currentState.aiCustomInputText,
        timeSpentSeconds = turnTime,
    )

    // Push current turn state to back stack before updating
    turnHistoryState.add(
        OnboardingViewModel.TurnState(
            assistantMessage = currentState.aiAssistantMessage,
            options = currentState.aiOptions,
            history = currentState.aiHistory,
        ),
    )

    val userEntry =
        OnboardingHistoryEntry(
            role = "user",
            parts = listOf(OnboardingPart(text = turnInput)),
        )
    val newHistory = currentState.aiHistory + userEntry

    _uiState.update {
        it.copy(
            aiHistory = newHistory,
            aiSelectedOptions = emptySet(),
            aiCustomInputText = "",
            aiSearchSuggestion = null,
            isAiLoading = true,
            aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE,
            onboardingError = null,
        )
    }

    val finalAction: () -> Unit = {
        _uiState.update {
            it.copy(
                isAiLoading = true,
                aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE,
                onboardingError = null,
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

                val response =
                    kotlinx.coroutines.withTimeout(45000) {
                        withContext(Dispatchers.IO) {
                            podcastRepository.api
                                .getOnboardingNextTurn(
                                    publicKey = podcastRepository.publicKey,
                                    request = OnboardingNextTurnRequest(history = newHistory),
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

                    val durationSec = (System.currentTimeMillis() - startTime) / 1000f
                    val detectedIntent =
                        when {
                            body.searchSuggestion != null -> "named_show"
                            body.assistantMessage.startsWith("I can only help you discover podcasts") -> "guardrail_refusal"
                            body.assistantMessage.startsWith("Got it — you've been clear") -> "repetition_finish"
                            else -> null
                        }
                    AnalyticsHelper.trackOnboardingAiResponseReceived(
                        turnNumber = currentState.aiCurrentTurn,
                        assistantMessage = body.assistantMessage,
                        optionsCount = body.options.size,
                        optionsList = body.options,
                        durationSeconds = durationSec,
                        detectedIntent = detectedIntent,
                    )

                    val assistantEntry =
                        OnboardingHistoryEntry(
                            role = "model",
                            parts = listOf(OnboardingPart(text = body.assistantMessage)),
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
                                onboardingError = null,
                            )
                        }
                        synthesizeAndBuildCurriculum(force = true)
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                aiHistory = state.aiHistory + assistantEntry,
                                aiAssistantMessage = body.assistantMessage,
                                aiOptions = body.options,
                                aiSearchSuggestion = body.searchSuggestion,
                                aiCurrentTurn = state.aiCurrentTurn + 1,
                                isAiLoading = false,
                                aiLoadingStage = AiLoadingStage.IDLE,
                                onboardingError = null,
                            )
                        }
                        turnStartMs = System.currentTimeMillis()
                    }
                } else {
                    throw Exception("Server returned error ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error in sendAiTurnInput", e)
                val userFriendlyMsg =
                    when (e) {
                        is kotlinx.coroutines.TimeoutCancellationException -> "Our AI is taking a moment to catch its breath. Let's try that again."
                        else -> "We encountered a temporary hiccup. Let's try that again."
                    }
                _uiState.update { state ->
                    state.copy(
                        isAiLoading = false,
                        aiLoadingStage = AiLoadingStage.IDLE,
                        onboardingError = userFriendlyMsg,
                    )
                }
            }
        }
    }
    lastFailedAction = finalAction
    finalAction()
}

internal fun OnboardingViewModel.synthesizeAndBuildCurriculum(force: Boolean = false) {
    val currentState = _uiState.value
    if (currentState.isAiLoading && !force) return
    val turnInput =
        (
            currentState.aiSelectedOptions.toList() +
                if (currentState.aiCustomInputText.isNotBlank()) listOf(currentState.aiCustomInputText) else emptyList()
        ).joinToString(". ")

    if (!force) {
        val turnTime = (System.currentTimeMillis() - turnStartMs) / 1000f
        AnalyticsHelper.trackOnboardingAiTurnSubmitted(
            turnNumber = currentState.aiCurrentTurn,
            selectedOptions = currentState.aiSelectedOptions,
            customInputText = currentState.aiCustomInputText,
            timeSpentSeconds = turnTime,
        )
    }

    val newHistoryList = currentState.aiHistory.toMutableList()
    if (turnInput.isNotBlank()) {
        newHistoryList.add(
            OnboardingHistoryEntry(
                role = "user",
                parts = listOf(OnboardingPart(text = turnInput)),
            ),
        )
    }
    if (newHistoryList.isEmpty()) {
        newHistoryList.add(
            OnboardingHistoryEntry(
                role = "user",
                parts = listOf(OnboardingPart(text = "Recommend some top topics like True Crime or Tech")),
            ),
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
            onboardingError = null,
        )
    }

    val finalAction: () -> Unit = {
        synthesisStartMs = System.currentTimeMillis()
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
                val startTime = System.currentTimeMillis()
                val hasSlow =
                    newHistoryList.any { h ->
                        h.role == "user" &&
                            h.parts.any { p ->
                                p.text
                                    .trim()
                                    .lowercase()
                                    .let { it == "test_slow" || it == "test_delay" }
                            }
                    }
                val hasFail =
                    newHistoryList.any { h ->
                        h.role == "user" &&
                            h.parts.any { p ->
                                p.text
                                    .trim()
                                    .lowercase()
                                    .let { it == "test_fail" || it == "test_error" }
                            }
                    }
                val hasTimeout =
                    newHistoryList.any { h ->
                        h.role == "user" &&
                            h.parts.any { p ->
                                p.text
                                    .trim()
                                    .lowercase()
                                    .let { it == "test_timeout" }
                            }
                    }

                if (hasFail) {
                    throw java.io.IOException("Simulated network failure")
                } else if (hasTimeout) {
                    delay(50000)
                }

                // Call Synthesize to get queries
                val queries =
                    kotlinx.coroutines.withTimeout(45000) {
                        val synthResponse =
                            withContext(Dispatchers.IO) {
                                podcastRepository.api
                                    .onboardingSynthesize(
                                        publicKey = podcastRepository.publicKey,
                                        request = OnboardingNextTurnRequest(history = newHistoryList),
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
                val rows =
                    kotlinx.coroutines.withTimeout(45000) {
                        val curriculumResponse =
                            withContext(Dispatchers.IO) {
                                podcastRepository.api
                                    .getOnboardingCurriculum(
                                        publicKey = podcastRepository.publicKey,
                                        request =
                                            OnboardingCurriculumRequest(
                                                queries = queries,
                                                country = currentState.currentRegion,
                                            ),
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
                val defaultSelectedIds = OnboardingCurriculumLogic.defaultSelectedPodcastIds(rows)
                val defaultSelectedPodcasts = newPodcasts.filter { it.id in defaultSelectedIds }.associateBy { it.id }

                val duration = (System.currentTimeMillis() - synthesisStartMs) / 1000f
                val uniquePodcastsCount = rows.flatMap { it.podcasts }.distinctBy { it.id }.size
                AnalyticsHelper.trackOnboardingAiSynthesisCompleted(
                    rowsCount = rows.size,
                    podcastsCount = uniquePodcastsCount,
                    durationSeconds = duration,
                )

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
                        // Merge, don't replace — keeps shows picked during a
                        // search side-trip from the AI chat.
                        subscribedPodcastIds = state.subscribedPodcastIds + defaultSelectedIds,
                        onboardingError = null,
                    )
                }
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error in synthesizeAndBuildCurriculum", e)
                AnalyticsHelper.trackOnboardingAiSynthesisFailed(e.message ?: "Unknown error")
                try {
                    _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.FETCHING_CATALOGS) }
                    val trending =
                        withContext(Dispatchers.IO) {
                            podcastRepository.getTrendingPodcasts(
                                country = currentState.currentRegion,
                                limit = 10,
                            )
                        }
                    val dummyPodcastDtos =
                        trending.map { pod ->
                            OnboardingCurriculumPodcastDto(
                                id = pod.id.toLongOrNull() ?: 0L,
                                title = pod.title,
                                author = pod.artist,
                                image = pod.imageUrl,
                                artwork = pod.imageUrl,
                                categories = mapOf("1" to pod.genre),
                                description = pod.description,
                            )
                        }

                    val fallbackRow =
                        OnboardingCurriculumRowDto(
                            rowTitle = "Trending Hits",
                            podcasts = dummyPodcastDtos,
                            episodes = emptyList(),
                        )
                    val newPodcasts = listOf(fallbackRow).flatMap { it.podcasts }.map { it.toPodcast() }
                    val defaultSelectedIds = OnboardingCurriculumLogic.defaultSelectedPodcastIds(listOf(fallbackRow))
                    val defaultSelectedPodcasts = newPodcasts.filter { it.id in defaultSelectedIds }.associateBy { it.id }
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
                            subscribedPodcastIds = state.subscribedPodcastIds + defaultSelectedIds,
                            onboardingError = null,
                        )
                    }
                } catch (ex: Exception) {
                    Log.e("OnboardingViewModel", "Error in fallback synthesis", ex)
                    val userFriendlyMsg =
                        when (ex) {
                            is kotlinx.coroutines.TimeoutCancellationException -> "Our AI is taking a moment to build your feed. Let's try that again."
                            else -> "We encountered a temporary hiccup. Let's try that again."
                        }
                    _uiState.update { state ->
                        state.copy(
                            isAiLoading = false,
                            isSynthesizing = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            onboardingError = userFriendlyMsg,
                        )
                    }
                }
            }
        }
    }
    lastFailedAction = finalAction
    finalAction()
}

internal fun OnboardingViewModel.navigateToSuggestions() {
    _uiState.update {
        it.copy(
            currentStep = OnboardingStep.AI_SUGGESTIONS,
            reachedSuggestionsViaAiFlow = true,
        )
    }
}

internal fun OnboardingViewModel.navigateBackFromSuggestions() {
    val state = _uiState.value
    val nextStep =
        when {
            state.reachedSuggestionsViaAiFlow -> OnboardingStep.AI_ONBOARDING
            state.reachedSuggestionsViaSearchFlow -> OnboardingStep.SEARCH
            state.reachedSuggestionsViaOpmlFlow -> OnboardingStep.WELCOME
            else -> OnboardingStep.LENGTH_PICKER
        }
    _uiState.update { it.copy(currentStep = nextStep) }
    if (nextStep == OnboardingStep.AI_ONBOARDING) {
        turnStartMs = System.currentTimeMillis()
    } else if (nextStep == OnboardingStep.LENGTH_PICKER) {
        currentStepStartMs = System.currentTimeMillis()
    }
}

internal fun OnboardingViewModel.navigateBackInAiOnboarding() {
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
                    aiSearchSuggestion = null,
                    isAiLoading = false,
                    isSynthesizing = false,
                    aiCurriculumRows = emptyList(),
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
                    aiCurriculumRows = emptyList(),
                )
            }
        }
        turnStartMs = System.currentTimeMillis()
    }
}

internal fun OnboardingViewModel.finishAiOnboarding(onDone: () -> Unit) {
    if (_uiState.value.isCompleting) return
    _uiState.update { it.copy(isCompleting = true) }
    viewModelScope.launch {
        try {
            val state = _uiState.value
            val selectedIds = state.subscribedPodcastIds
            val rows = state.aiCurriculumRows
            val allCurriculumPodcasts = rows.flatMap { it.podcasts }.map { it.toPodcast() }.distinctBy { it.id }
            // Include shows picked during a search side-trip from the AI chat
            // (they live in selectedPodcasts but not in the curriculum rows).
            val podcastsToSubscribe =
                (allCurriculumPodcasts + state.selectedPodcasts.values)
                    .distinctBy { it.id }
                    .filter { it.id in selectedIds }
            for (podcast in podcastsToSubscribe) {
                subscriptionRepository.subscribe(podcast)
            }

            userPrefs.setRegion(state.currentRegion)
            boxcastPrefs.setOnboardingCompleted(true)

            val userGenres =
                state.aiHistory
                    .filter { it.role == "user" }
                    .flatMap { it.parts }
                    .map { it.text }
                    .toSet()
            boxcastPrefs.setUserGenres(userGenres)

            val subscribedTitles = selectedIds.map { seenPodcasts[it]?.title ?: it }
            // Analytics: Track onboarding completion depending on the flow that reached suggestions
            if (state.reachedSuggestionsViaAiFlow) {
                AnalyticsHelper.trackOnboardingAiDone(
                    totalSubscribedCount = selectedIds.size,
                    subscribedPodcastsList = subscribedTitles,
                    didScrollSuggestions = didScrollSuggestions,
                    totalOnboardingTimeSeconds = getTotalOnboardingTime(),
                    favoriteGenres = userGenres.toList(),
                    entryPoint = onboardingEntryPoint,
                )
            } else if (state.reachedSuggestionsViaSearchFlow) {
                AnalyticsHelper.trackOnboardingSearchDone(
                    entryPoint = searchEntryPoint,
                    totalSubscribedCount = selectedIds.size,
                    subscribedPodcastsList = subscribedTitles,
                    searchesPerformed = searchesPerformedCount,
                    timeSpentOnSearchSeconds = getSearchScreenTimeSpent(),
                    totalOnboardingTimeSeconds = getTotalOnboardingTime(),
                )
            } else if (state.reachedSuggestionsViaOpmlFlow) {
                // For OPML, onboarding_completed with method "import" is already tracked in MainActivity.kt
                // But we refresh/set the user properties to ensure intent and subscriptions are up to date.
                AnalyticsHelper.setOnboardingImportCompletedUserProperties(
                    initialPodcastsSubscribed = selectedIds.size,
                )
            } else {
                // Manual/Genre flow
                AnalyticsHelper.trackOnboardingManualDone(
                    totalSubscribedCount = selectedIds.size,
                    subscribedPodcastsList = subscribedTitles,
                    totalOnboardingTimeSeconds = getTotalOnboardingTime(),
                    didSwitchFromAi = didSwitchFromAi,
                    favoriteGenres = state.selectedGenres,
                )
            }

            onDone()
        } catch (e: Exception) {
            Log.e("OnboardingViewModel", "Error in finishAiOnboarding", e)
            _uiState.update { state ->
                state.copy(
                    isCompleting = false,
                    onboardingError = "We couldn't finish setup. Please try again.",
                )
            }
        }
    }
}

fun OnboardingViewModel.markOnboardingCompletedSilent(onDone: () -> Unit) {
    boxcastPrefs.setOnboardingCompleted(true)
    onDone()
}
