package cx.aswin.boxlore.feature.onboarding

import android.util.Log
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingGenreSynthRequest
import cx.aswin.boxlore.core.network.model.toPodcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun OnboardingViewModel.synthesizeGenreOnboarding() {
    val currentState = _uiState.value
    val selectedLengths = currentState.preferredLengths
    val timeSpent = (System.currentTimeMillis() - currentStepStartMs) / 1000f
    AnalyticsHelper.trackOnboardingManualStepCompleted("lengths", selectedLengths.size, selectedLengths.toList(), timeSpent)

    _uiState.update {
        it.copy(
            isLoadingPodcasts = true,
            currentStep = OnboardingStep.AI_SUGGESTIONS,
            onboardingError = null,
            reachedSuggestionsViaAiFlow = false,
            reachedSuggestionsViaSearchFlow = false,
            reachedSuggestionsViaOpmlFlow = false,
        )
    }

    val finalAction: () -> Unit = {
        synthesisStartMs = System.currentTimeMillis()
        _uiState.update { it.copy(isLoadingPodcasts = true, onboardingError = null) }
        viewModelScope.launch {
            try {
                // 1. Fetch charts podcasts in parallel
                val chartsDeferred =
                    async(Dispatchers.IO) {
                        val genresList = currentState.selectedGenres.toList()
                        val allPodcasts = mutableListOf<Podcast>()
                        val perGenreLimit = OnboardingGenreLimits.perGenreTrendingLimit(genresList.size)
                        for (genre in genresList) {
                            try {
                                val trending =
                                    podcastRepository.getTrendingPodcasts(
                                        country = currentState.currentRegion,
                                        category = genre,
                                        limit = perGenreLimit,
                                    )
                                allPodcasts.addAll(trending)
                            } catch (e: Exception) {
                                Log.e("OnboardingViewModel", "Failed to fetch trending for genre $genre", e)
                            }
                        }
                        allPodcasts.distinctBy { it.id }.shuffled().take(10)
                    }

                // 2. Fetch AI curriculum rows from backend
                val rowsDeferred =
                    async(Dispatchers.IO) {
                        val formattedActivities =
                            OnboardingCurriculumLogic.formatMappedSelections(
                                selections = currentState.listeningActivities,
                                genreMap = currentState.activityGenreMap,
                                focusingPrefix = "focusing on",
                            )
                        val formattedLengths =
                            OnboardingCurriculumLogic.formatMappedSelections(
                                selections = currentState.preferredLengths,
                                genreMap = currentState.lengthGenreMap,
                                focusingPrefix = "for",
                            )

                        val request =
                            OnboardingGenreSynthRequest(
                                genres = currentState.selectedGenres.toList(),
                                subGenres = currentState.selectedSubGenres.toList(),
                                activity = formattedActivities,
                                length = formattedLengths,
                                country = currentState.currentRegion,
                            )
                        val response =
                            podcastRepository.api
                                .onboardingGenreSynth(
                                    publicKey = podcastRepository.publicKey,
                                    request = request,
                                ).execute()
                        if (response.isSuccessful && response.body() != null) {
                            response.body()!!.map { it.copy(episodes = emptyList()) }
                        } else {
                            throw Exception("Failed to load curriculum from genre synthesis")
                        }
                    }

                val charts = chartsDeferred.await()
                val rows =
                    try {
                        rowsDeferred.await()
                    } catch (e: Exception) {
                        Log.e("OnboardingViewModel", "AI onboarding synthesis failed, falling back to charts", e)
                        emptyList()
                    }

                val finalRows =
                    if (rows.isEmpty()) {
                        val fallbackPodcastDtos =
                            charts.map { pod ->
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
                        if (fallbackPodcastDtos.isNotEmpty()) {
                            listOf(
                                OnboardingCurriculumRowDto(
                                    rowTitle = "Trending in your Genres",
                                    podcasts = fallbackPodcastDtos,
                                    episodes = emptyList(),
                                ),
                            )
                        } else {
                            throw Exception("AI synthesis and trending charts are both empty")
                        }
                    } else {
                        rows
                    }

                // 3. Process default selections
                val newPodcasts = finalRows.flatMap { it.podcasts }.map { it.toPodcast() }
                val defaultSelectedIds = OnboardingCurriculumLogic.defaultSelectedPodcastIds(finalRows)
                val defaultSelectedPodcasts = newPodcasts.filter { it.id in defaultSelectedIds }.associateBy { it.id }

                val duration = (System.currentTimeMillis() - synthesisStartMs) / 1000f
                val uniquePodcastsCount = finalRows.flatMap { it.podcasts }.distinctBy { it.id }.size
                AnalyticsHelper.trackOnboardingAiSynthesisCompleted(
                    rowsCount = finalRows.size,
                    podcastsCount = uniquePodcastsCount,
                    durationSeconds = duration,
                )

                _uiState.update { state ->
                    state.copy(
                        aiCurriculumRows = finalRows,
                        genreChartsPodcasts = charts,
                        selectedPodcasts = state.selectedPodcasts + defaultSelectedPodcasts,
                        subscribedPodcastIds = state.subscribedPodcastIds + defaultSelectedIds,
                        isLoadingPodcasts = false,
                        onboardingError = null,
                    )
                }
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Error in synthesizeGenreOnboarding", e)
                AnalyticsHelper.trackOnboardingAiSynthesisFailed(e.message ?: "Unknown error")
                _uiState.update { state ->
                    state.copy(
                        isLoadingPodcasts = false,
                        onboardingError = "We encountered a temporary issue generating your curriculum. Let's try again.",
                    )
                }
            }
        }
    }
    lastFailedAction = finalAction
    finalAction()
}
