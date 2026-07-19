package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.data.PersonalizedContentSectionInputs
import cx.aswin.boxlore.core.data.content.ContentCatalogSnapshot
import cx.aswin.boxlore.core.data.content.ContentContext
import cx.aswin.boxlore.core.data.content.ContentContextInput
import cx.aswin.boxlore.core.data.content.ContentSection
import cx.aswin.boxlore.core.ranking.CandidateFeatureBuilder
import cx.aswin.boxlore.core.ranking.CandidateSignals
import cx.aswin.boxlore.core.ranking.RankingExposure
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.logic.adaptiveHistoryMaturityBucket
import cx.aswin.boxlore.feature.home.logic.discoverPodcastsExcluding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal suspend fun HomeViewModel.loadPersonalizedDiscoverySections(
    context: cx.aswin.boxlore.core.data.content.ContentContext,
): cx.aswin.boxlore.core.data.content.GroupedContentSections? {
    val catalog = podcastRepository.getContentCatalog() ?: return null
    val interests = boxcastPrefs.getUserGenres().toList()
    val history = playbackRepository.getHistoryForRecommendations(30)
    val subscriptions = subscriptionRepository.subscribedPodcasts.first()
    val learnedGenreAffinities = adaptiveRankingRepository.genreAffinities()
    return podcastRepository.getPersonalizedContentSections(
        contentContext = context,
        catalog = catalog,
        inputs =
            PersonalizedContentSectionInputs(
                history = history,
                interests = interests,
                subscribedPodcastIds = subscriptions.map(Podcast::id),
                subscribedGenres = subscriptions.mapNotNull(Podcast::genre).distinct(),
                learnedGenreAffinities = learnedGenreAffinities,
                recentSectionIds = recentSectionIntentStore.recentIds(),
                languages =
                    listOf(
                        java.util.Locale
                            .getDefault()
                            .language,
                        "en",
                    ).distinct(),
            ),
        preferCache = preferCachedAdaptiveSections,
    )
}

// from private fun loadAdaptiveContent
internal fun HomeViewModel.loadAdaptiveContent() {
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        combine(
            userPrefs.regionStream,
            clockContextFlow,
            subscriptionRepository.subscribedPodcasts,
            allHomeHistory,
        ) { region, clockContext, subscriptions, history ->
            AdaptiveContentTrigger(
                region = region,
                daypart = clockContext.daypart,
                sectionDaypart = clockContext.sectionDaypart,
                date = clockContext.date,
                timezoneOffsetMinutes = clockContext.timezoneOffsetMinutes,
                subscriptionIds = subscriptions.map(Podcast::id).toSet(),
                historyMaturityBucket = adaptiveHistoryMaturityBucket(history.size),
            )
        }.distinctUntilChanged().collectLatest { trigger ->
            try {
                refreshAdaptiveSections(trigger)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                markAdaptiveSectionsIdle()
                android.util.Log.w(
                    "HomeViewModel",
                    "Adaptive content composition failed",
                    error,
                )
            }
        }
    }
}

// from private suspend fun refreshAdaptiveSections
internal suspend fun HomeViewModel.refreshAdaptiveSections(trigger: AdaptiveContentTrigger) {
    val history = allHomeHistory.first()
    val latestHistory = history.maxByOrNull(HomeListeningHistoryItem::lastPlayedAt)
    val context =
        contentContextEngine
            .create(
                ContentContextInput(
                    surface = RankingSurface.HOME,
                    region = trigger.region,
                    isDriving = false,
                    isOnline = connectivityStatus.isOnline(),
                    availableMinutes = null,
                    currentEpisodeId = latestHistory?.episodeId,
                    currentPodcastId = latestHistory?.podcastId,
                    historyMaturity = history.size,
                    subscriptionCount = trigger.subscriptionIds.size,
                    sessionId = adaptiveContentSessionId,
                ),
            ).copy(daypart = trigger.daypart)
    val catalog = podcastRepository.getContentCatalog()
    if (_adaptiveSections.value.isEmpty()) {
        _isAdaptiveSectionsLoading.value = true
        _uiState.update { it.copy(isAdaptiveSectionsLoading = true) }
    }
    paintCachedAdaptiveSections(context, catalog)
    paintFreshAdaptiveSections(context, catalog)
}

// from private suspend fun paintCachedAdaptiveSections
internal suspend fun HomeViewModel.paintCachedAdaptiveSections(
    context: ContentContext,
    catalog: ContentCatalogSnapshot?,
) {
    preferCachedAdaptiveSections = true
    try {
        val staleSlate =
            contentOrchestrator.compose(
                context = context,
                catalog = catalog,
                forceRefresh = false,
                allowUngroupedFallback = false,
            )
        if (staleSlate.sections.isNotEmpty()) {
            applyAdaptiveSections(staleSlate.sections, loading = false)
        }
    } finally {
        preferCachedAdaptiveSections = false
    }
}

// from private suspend fun paintFreshAdaptiveSections
internal suspend fun HomeViewModel.paintFreshAdaptiveSections(
    context: ContentContext,
    catalog: ContentCatalogSnapshot?,
) {
    // Stale compose already recorded those candidates; without a reset the refresh
    // filters every overlapping item out and we keep the cache forever.
    contentOrchestrator.resetExposureBudget()
    val freshSlate =
        contentOrchestrator.compose(
            context = context,
            catalog = catalog,
            forceRefresh = true,
            allowUngroupedFallback = false,
        )
    if (freshSlate.sections.isNotEmpty()) {
        applyAdaptiveSections(freshSlate.sections, loading = false)
    } else {
        markAdaptiveSectionsIdle()
        if (_adaptiveSections.value.isEmpty()) {
            android.util.Log.w(
                "HomeViewModel",
                "Adaptive content refresh returned no sections",
            )
        }
    }
}

// from private fun applyAdaptiveSections
internal fun HomeViewModel.applyAdaptiveSections(
    sections: List<ContentSection>,
    loading: Boolean,
) {
    _adaptiveSections.value = sections
    _isAdaptiveSectionsLoading.value = loading
    val discover =
        discoverPodcastsExcluding(
            trending = cachedForYouTrending,
            heroItems = cachedHeroItems,
            adaptiveSections = sections,
        )
    _uiState.update {
        it.copy(
            adaptiveSections = sections,
            isAdaptiveSectionsLoading = loading,
            discoverPodcasts = discover ?: it.discoverPodcasts,
        )
    }
}

// from private fun markAdaptiveSectionsIdle
internal fun HomeViewModel.markAdaptiveSectionsIdle() {
    _isAdaptiveSectionsLoading.value = false
    _uiState.update { it.copy(isAdaptiveSectionsLoading = false) }
}

// from fun trackAdaptiveSectionVisible
fun HomeViewModel.trackAdaptiveSectionVisible(
    section: ContentSection,
    visibleCandidateIds: Set<String>,
) {
    val shouldRecordSectionIntent =
        synchronized(exposedAdaptiveSectionIntents) {
            exposedAdaptiveSectionIntents.add(section.intent.id)
        }
    val newlyVisible =
        section.items.filter { candidate ->
            candidate.id in visibleCandidateIds &&
                exposedAdaptiveCandidates.add("${section.stableId}:${candidate.id}")
        }
    if (!shouldRecordSectionIntent && newlyVisible.isEmpty()) return
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        if (shouldRecordSectionIntent) {
            recentSectionIntentStore.recordVisible(section.intent.id)
        }
        newlyVisible.forEach { candidate ->
            rankingFeedback.recordExposure(
                RankingExposure(
                    episodeId = candidate.id,
                    podcastId = candidate.podcast.id,
                    objective = section.intent.objective,
                    surface = RankingSurface.HOME,
                    source = candidate.source,
                    features =
                        CandidateFeatureBuilder.build(
                            CandidateSignals(
                                isUnseenShow = candidate.isNovel,
                                serverRelevance = candidate.retrievalScore.coerceIn(0.0, 1.0),
                                isUnplayed = true,
                                timeContextMatch = 1.0,
                            ),
                        ),
                    entryPoint = "home_adaptive_${section.intent.id}",
                    online = true,
                ),
            )
        }
    }
}

