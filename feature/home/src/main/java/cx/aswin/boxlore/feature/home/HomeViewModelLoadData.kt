package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.DiversityPolicy
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.PodcastRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.database.toScorable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.logic.HomeMixtapeCache
import cx.aswin.boxlore.feature.home.logic.HomeUiAssemblyLogic
import cx.aswin.boxlore.feature.home.logic.discoverPodcastsExcluding
import cx.aswin.boxlore.feature.home.logic.toRecommendationPodcast
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun HomeViewModel.fetchPersonalizedRecommendations(region: String) {
    viewModelScope.launch {
        _isRecommendationsLoaded.value = false
        try {
            val interests = boxcastPrefs.getUserGenres().toList()
            val history = playbackRepository.getHistoryForRecommendations(15)

            val subscribedIds = subscriptionRepository.subscribedPodcastIds.first().toList()
            val subscribedGenres =
                subscriptionRepository.subscribedPodcasts
                    .first()
                    .mapNotNull { it.genre }
                    .distinct()

            android.util.Log.d(
                "HomeViewModel",
                "Fetching recommendations with history size: ${history.size}, interests: $interests, region: $region, subscribedCount: ${subscribedIds.size}",
            )
            val recs =
                podcastRepository.getPersonalizedRecommendations(
                    history = history,
                    interests = interests,
                    country = region,
                    subscribedPodcastIds = subscribedIds,
                    subscribedGenres = subscribedGenres,
                )
            android.util.Log.d("HomeViewModel", "Fetched recommendations size: ${recs.size}")
            val distinctRecs =
                recs
                    .distinctBy { it.id }
                    .distinctBy { it.title.lowercase().trim() }
            _recommendations.value = distinctRecs
            try {
                val json = Json { ignoreUnknownKeys = true }
                val serialized = json.encodeToString(distinctRecs)
                boxcastPrefs.setCachedRecommendationsJson(serialized)
            } catch (ce: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to cache recommendations", ce)
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Failed to fetch personalized recommendations", e)
        } finally {
            _isRecommendationsLoaded.value = true
        }
    }
}

// from private fun loadData
@OptIn(FlowPreview::class)
internal fun HomeViewModel.loadData() {
    viewModelScope.launch {
        // --- BASE DATA FLOW (Restarts when Region or dismissal changes) ---
        combine(
            userPrefs.regionStream,
            clockContextFlow.map { clock -> clock.daypart }.distinctUntilChanged(),
        ) { region, daypart ->
            region to daypart
        }.distinctUntilChanged()
            .collectLatest { (region, daypart) ->
                if (cachedRegion != region) {
                    cachedRegion = region
                    cachedForYouTrending = emptyList()
                    cachedHeroItems = emptyList()
                    cachedLatestEpisodes = emptyList()

                    _uiState.update {
                        it.copy(
                            discoverPodcasts = emptyList(),
                            isFilterLoading = true,
                        )
                    }
                }
                activeRegion = region

                val trendingState = MutableStateFlow<List<Podcast>>(emptyList())

                // 1. Fast Bootstrap Call (Briefing & Trending)
                val fastJob =
                    launch {
                        _isTrendingLoaded.value = false
                        try {
                            val bootstrapData =
                                podcastRepository.getHomeBootstrapDataFast(
                                    country = region,
                                )

                            _briefingState.value = bootstrapData.briefing
                            _briefingChaptersState.value = bootstrapData.briefingChapters
                            trendingState.value = bootstrapData.trending
                        } catch (e: Exception) {
                            android.util.Log.e("BoxCastTiming", "VM: Fast Bootstrap API load failed", e)
                        } finally {
                            _isTrendingLoaded.value = true
                        }
                    }

                // 2. Background Personalized Recommendations Call
                launch {
                    fastJob.join()
                    _isRecommendationsLoaded.value = false
                    try {
                        android.util.Log.d("BoxCastTiming", "VM: Background personalized Home screen load for region=$region")

                        val interests = boxcastPrefs.getUserGenres().toList()

                        val historyDeferred = async { playbackRepository.getHistoryForRecommendations(15) }
                        val subscribedIdsDeferred = async { subscriptionRepository.subscribedPodcastIds.first().toList() }
                        val subscribedPodcastsDeferred = async { subscriptionRepository.subscribedPodcasts.first() }

                        val history = historyDeferred.await()
                        val subscribedIds = subscribedIdsDeferred.await()
                        val subscribedPodcasts = subscribedPodcastsDeferred.await()
                        val subscribedGenres = subscribedPodcasts.mapNotNull { it.genre }.distinct()

                        val bootstrapData =
                            podcastRepository.getHomeBootstrapData(
                                country = region,
                                vibeIds = emptyList(),
                                history = history,
                                interests = interests,
                                subscribedPodcastIds = subscribedIds,
                                subscribedGenres = subscribedGenres,
                            )

                        val distinctRecs =
                            bootstrapData.recommendations
                                .distinctBy { it.id }
                                .distinctBy { it.title.lowercase().trim() }
                        _recommendations.value = distinctRecs
                        _isRecommendationsFallback.value = bootstrapData.isRecommendationsFallback
                        try {
                            val json = Json { ignoreUnknownKeys = true }
                            val serialized = json.encodeToString(distinctRecs)
                            boxcastPrefs.saveRecommendationsCache(
                                serialized,
                                bootstrapData.isRecommendationsFallback,
                            )
                        } catch (ce: Exception) {
                            android.util.Log.e("HomeViewModel", "Failed to cache recommendations", ce)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BoxCastTiming", "VM: Recommendations load failed", e)
                    } finally {
                        _isRecommendationsLoaded.value = true
                    }
                }

                val coreSlice =
                    combine(
                        trendingState, // Hot StateFlow — never completes
                        playbackRepository.resumeSessions,
                        subscriptionRepository.subscribedPodcasts,
                        allHomeHistory,
                        _resolvedSerialEpisodes,
                    ) { trending, resume, subs, history, resolvedSerial ->
                        HomeCoreSlice(trending, resume, subs, history, resolvedSerial)
                    }
                val recsSlice =
                    combine(
                        _recommendations,
                        playbackRepository.completedEpisodeIds,
                        _isTrendingLoaded,
                        _isRecommendationsLoaded,
                        userPrefs.hasDismissedHomeImportBannerStream,
                    ) { recommendations, completedEpisodeIds, isTrendingLoaded, isRecommendationsLoaded, hasDismissedImportBanner ->
                        HomeRecsSlice(
                            recommendations,
                            completedEpisodeIds,
                            isTrendingLoaded,
                            isRecommendationsLoaded,
                            hasDismissedImportBanner,
                        )
                    }
                val briefingSlice =
                    combine(
                        _briefingState,
                        _briefingDismissedDate,
                        _briefingChaptersState,
                        _briefingDismissedForever,
                    ) { briefing, dismissedDate, briefingChapters, dismissedForever ->
                        HomeBriefingSlice(briefing, dismissedDate, briefingChapters, dismissedForever)
                    }
                val becauseYouLikeSlice =
                    combine(
                        _seemsToLikePodcast,
                        _becauseYouLikeRecommendations,
                        _becauseYouLikePodcasts,
                        _isBecauseYouLikeLoading,
                        _isRecommendationsFallback,
                    ) {
                        seemsToLikePodcast,
                        becauseYouLikeRecommendations,
                        becauseYouLikePodcasts,
                        isBecauseYouLikeLoading,
                        isRecommendationsFallback,
                        ->
                        HomeBecauseYouLikeSlice(
                            seemsToLikePodcast,
                            becauseYouLikeRecommendations,
                            becauseYouLikePodcasts,
                            isBecauseYouLikeLoading,
                            isRecommendationsFallback,
                        )
                    }
                combine(
                    combine(coreSlice, recsSlice, briefingSlice, becauseYouLikeSlice) { core, recs, briefing, becauseYouLike ->
                        HomeDataWrapper(
                            trending = core.trending,
                            resume = core.resume,
                            subs = core.subs,
                            history = core.history,
                            resolvedSerial = core.resolvedSerial,
                            recommendations = recs.recommendations,
                            completedEpisodeIds = recs.completedEpisodeIds,
                            isTrendingLoaded = recs.isTrendingLoaded,
                            isRecommendationsLoaded = recs.isRecommendationsLoaded,
                            hasDismissedImportBanner = recs.hasDismissedImportBanner,
                            briefing = briefing.briefing,
                            briefingChapters = briefing.briefingChapters,
                            briefingDismissedDate = briefing.briefingDismissedDate,
                            briefingDismissedForever = briefing.briefingDismissedForever,
                            seemsToLikePodcast = becauseYouLike.seemsToLikePodcast,
                            becauseYouLikeRecommendations = becauseYouLike.becauseYouLikeRecommendations,
                            becauseYouLikePodcasts = becauseYouLike.becauseYouLikePodcasts,
                            isBecauseYouLikeLoading = becauseYouLike.isBecauseYouLikeLoading,
                            isRecommendationsFallback = becauseYouLike.isRecommendationsFallback,
                        )
                    },
                    _adaptiveSections,
                ) { wrapper, adaptiveSections ->
                    wrapper.copy(adaptiveSections = adaptiveSections)
                }.debounce(100L).collect { wrapper ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val allHistory = wrapper.history
                        val scoringHistory = playbackRepository.getAllHistory().first()
                        val subscribedIds = wrapper.subs.map(Podcast::id).toSet()
                        val trendingList =
                            adaptiveScorer.rankPodcasts(
                                inputs =
                                    wrapper.trending.mapIndexed { index, podcast ->
                                        PodcastRankingInput(
                                            podcast = podcast,
                                            priorScore = (wrapper.trending.size - index).toDouble(),
                                            source = CandidateSource.TRENDING,
                                            isNovel = podcast.id !in subscribedIds,
                                        )
                                    },
                                history = scoringHistory,
                                objective = RankingObjective.DISCOVERY,
                                surface = RankingSurface.HOME,
                                diversityPolicy =
                                    DiversityPolicy(
                                        limit = wrapper.trending.size,
                                        maxPerShow = 1,
                                        reserveNovelSlot = true,
                                    ),
                            )
                        val rankedRecommendations =
                            adaptiveScorer.rankEpisodes(
                                inputs =
                                    wrapper.recommendations.mapIndexed { index, episode ->
                                        val podcast = episode.toRecommendationPodcast()
                                        EpisodeRankingInput(
                                            episode = episode,
                                            podcast = podcast,
                                            priorScore = (wrapper.recommendations.size - index).toDouble(),
                                            source = CandidateSource.SERVER_RECOMMENDATION,
                                            isNovel = podcast.id !in subscribedIds,
                                        )
                                    },
                                history = scoringHistory,
                                objective = RankingObjective.DISCOVERY,
                                surface = RankingSurface.HOME,
                                diversityPolicy =
                                    DiversityPolicy(
                                        limit = wrapper.recommendations.size,
                                        maxPerShow = 2,
                                        reserveNovelSlot = true,
                                    ),
                            )
                        val resumeList = wrapper.resume
                        val subs = wrapper.subs
                        val resolvedSerial = wrapper.resolvedSerial
                        val completedEpisodeIds = wrapper.completedEpisodeIds

                        // Compute completed count for review prompt logic
                        val completedCount = allHistory.count { it.isCompleted }

                        // Check if review prompt should be shown (promoter handoff or milestone-based)
                        if (!_showReviewPrompt.value && !_showFeedback.value && !_showPostReview.value) {
                            val isPlayingNow = playerState.value.isPlaying
                            if (engagementCoordinator.shouldShowPromoterReview(isPlayingNow)) {
                                engagementCoordinator.recordProactivePromptShown()
                                engagementCoordinator.clearPromoterReviewPending()
                                val npsScore = userPrefs.npsLastScore()
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPromoterReviewHandoff(
                                    npsScore,
                                )
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackEngagementPromptShown(
                                    promptType = "promoter_review",
                                    source = "nps_handoff",
                                )
                                _reviewPromptVariant.value =
                                    cx.aswin.boxlore.feature.home.components.ReviewPromptVariant.PromoterHandoff
                                _showReviewPrompt.value = true
                            } else if (engagementCoordinator.canShowProactivePrompt(isPlayingNow)) {
                                userPrefs.syncReviewMilestonePending(completedCount)
                                val shouldPrompt = userPrefs.shouldShowReviewPrompt(isPlayingNow)
                                if (shouldPrompt) {
                                    engagementCoordinator.recordProactivePromptShown()
                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackEngagementPromptShown(
                                        promptType = "milestone_review",
                                        source = "episode_milestone",
                                        completedEpisodes = completedCount,
                                    )
                                    _reviewPromptVariant.value =
                                        cx.aswin.boxlore.feature.home.components.ReviewPromptVariant.Milestone
                                    _showReviewPrompt.value = true
                                }
                            }
                        }

                        // NPS survey: mark eligible once the user reaches 3+ completed episodes.
                        // The trigger event fires on the next app open (see MainActivity)
                        // so it never interrupts background playback.
                        if (completedCount >= 3 && !userPrefs.hasNpsSurveyFired() && !userPrefs.isNpsSurveyPending()) {
                            userPrefs.markNpsSurveyPending(completedCount)
                        }

                        val podScoresMap =
                            if (stablePodcastOrder == null || stableMixtapePodcasts == null) {
                                adaptiveScorer.scorePodcasts(
                                    podcasts = wrapper.subs.map { it.toScorable() },
                                    history = scoringHistory,
                                    objective = RankingObjective.YOUR_SHOWS,
                                    surface = RankingSurface.HOME,
                                )
                            } else {
                                emptyMap()
                            }

                        val previousMixtape =
                            if (stableMixtapePodcasts != null &&
                                stableMixtapeCount != null &&
                                stableCurrentUnplayedEpisodes != null &&
                                stableMixtapeSubSignature != null
                            ) {
                                HomeMixtapeCache(
                                    podcasts = stableMixtapePodcasts!!,
                                    unplayedCount = stableMixtapeCount!!,
                                    episodes = stableCurrentUnplayedEpisodes!!,
                                    subSignature = stableMixtapeSubSignature!!,
                                )
                            } else {
                                null
                            }

                        val assembled =
                            HomeUiAssemblyLogic.assemble(
                                trendingList = trendingList,
                                rankedRecommendations = rankedRecommendations,
                                resumeList = resumeList,
                                subs = subs,
                                allHistory = allHistory,
                                resolvedSerial = resolvedSerial,
                                completedEpisodeIds = completedEpisodeIds,
                                region = region,
                                adaptiveSections = wrapper.adaptiveSections,
                                previousStableOrder = stablePodcastOrder,
                                podcastScores = podScoresMap,
                                previousMixtape = previousMixtape,
                                buildMixtape = { scores, recommendations ->
                                    MixtapeEngine.build(
                                        subscriptions = subs,
                                        history = scoringHistory,
                                        resolvedSerialEpisodes = _resolvedSerialEpisodes.value,
                                        recommendations = recommendations,
                                        podcastScores = scores,
                                        adaptiveRanking =
                                            MixtapeEngine.AdaptiveRanking(
                                                scorer = adaptiveScorer,
                                                surface = RankingSurface.HOME,
                                            ),
                                    )
                                },
                                isTrendingLoaded = wrapper.isTrendingLoaded,
                                hasDismissedImportBanner = wrapper.hasDismissedImportBanner,
                                rawBriefing = wrapper.briefing,
                                rawBriefingChapters = wrapper.briefingChapters,
                                briefingDismissedDate = wrapper.briefingDismissedDate,
                                briefingDismissedForever = wrapper.briefingDismissedForever,
                            )

                        stablePodcastOrder = assembled.stablePodcastOrder
                        assembled.mixtapeCache?.let { cache ->
                            stableMixtapePodcasts = cache.podcasts
                            stableMixtapeCount = cache.unplayedCount
                            stableCurrentUnplayedEpisodes = cache.episodes
                            stableMixtapeSubSignature = cache.subSignature
                            currentUnplayedEpisodes = cache.episodes
                        }

                        if (assembled.shouldUpdateForYouCache) {
                            cachedRegion = region
                            cachedForYouTrending = trendingList
                            cachedHeroItems = assembled.heroItems
                            cachedLatestEpisodes = assembled.latestEpisodes
                        }

                        _uiState.value =
                            HomeUiState(
                                heroItems = assembled.heroItems,
                                latestEpisodes = assembled.latestEpisodes,
                                unplayedEpisodeCount = assembled.unplayedEpisodeCount,
                                completedEpisodeCount = assembled.completedEpisodeCount,
                                subscribedPodcasts = assembled.subscribedPodcasts,
                                selectedCategory = _selectedCategory.value,
                                discoveryGreeting =
                                    discoveryGreetingFor(
                                        daypart = daypart,
                                        date = clockContextFlow.value.date,
                                    ),
                                discoverPodcasts = assembled.discoverPodcasts,
                                recommendations = assembled.recommendations,
                                isLoading = assembled.isLoading,
                                isFilterLoading = assembled.isFilterLoading,
                                isError = false,
                                selectedPodcastId = _selectedPodcastId.value,
                                selectedPodcastEpisodes = _selectedPodcastEpisodes.value,
                                isSelectedPodcastLoading = _isSelectedPodcastLoading.value,
                                isSelectedRssRefreshing = _isSelectedRssRefreshing.value,
                                episodePlaybackState = assembled.episodePlaybackState,
                                showImportBanner = assembled.showImportBanner,
                                briefing = assembled.briefing,
                                briefingChapters = assembled.briefingChapters,
                                isRecommendationsLoading = !wrapper.isRecommendationsLoaded,
                                seemsToLikePodcast = wrapper.seemsToLikePodcast,
                                becauseYouLikeRecommendations = wrapper.becauseYouLikeRecommendations,
                                becauseYouLikePodcasts = wrapper.becauseYouLikePodcasts,
                                isBecauseYouLikeLoading = wrapper.isBecauseYouLikeLoading,
                                isRecommendationsFallback = wrapper.isRecommendationsFallback,
                                adaptiveSections = wrapper.adaptiveSections,
                                isAdaptiveSectionsLoading = _isAdaptiveSectionsLoading.value,
                            )
                    }
                }
            }
    }

    // --- CATEGORY OBSERVER (Considers Region) ---
    viewModelScope.launch {
        combine(_selectedCategory, userPrefs.regionStream) { category, region ->
            category to region
        }.collectLatest { (category, region) ->
            if (category == null) {
                // "For You" - use cached data instantly if it matches current region
                if (cachedRegion == region && cachedHeroItems.isNotEmpty()) {
                    val discover =
                        discoverPodcastsExcluding(
                            trending = cachedForYouTrending,
                            heroItems = cachedHeroItems,
                            adaptiveSections = _adaptiveSections.value,
                        ).orEmpty()

                    _uiState.update {
                        it.copy(
                            selectedCategory = null,
                            discoverPodcasts = discover,
                            isFilterLoading = false,
                        )
                    }
                } else {
                    // Region has changed or cache is empty / stale, so clear discover list and wait for load
                    _uiState.update {
                        it.copy(
                            selectedCategory = null,
                            discoverPodcasts = emptyList(),
                            isFilterLoading = true,
                        )
                    }
                }
            } else {
                // Category selected
                _uiState.update {
                    it.copy(
                        isFilterLoading = true,
                        selectedCategory = category,
                        discoverPodcasts = emptyList(),
                    )
                }

                try {
                    android.util.Log.d("HomeViewModel", "Category: Fetching '$category' for region '$region'...")
                    var finalList: List<Podcast> = emptyList()
                    podcastRepository
                        .getTrendingPodcastsStream(region, 50, category.lowercase())
                        .collect { items ->
                            finalList = items
                            _uiState.update {
                                it.copy(
                                    discoverPodcasts = items,
                                    isFilterLoading = items.size < 10,
                                )
                            }
                        }
                    _uiState.update {
                        it.copy(
                            discoverPodcasts = finalList,
                            isFilterLoading = false,
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Category stream error", e)
                    _uiState.update { it.copy(isFilterLoading = false) }
                }
            }
        }
    }
}

