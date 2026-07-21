package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.playback.getHistoryForRecommendations
import cx.aswin.boxlore.core.playback.getRecentHistoryList
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.logic.HomeAnchorSelectionLogic
import cx.aswin.boxlore.feature.home.logic.HomeBecauseYouLikeLogic
import cx.aswin.boxlore.feature.home.logic.HomePersonalizationModeLogic
import cx.aswin.boxlore.feature.home.logic.PodcastAffinityLogic
import cx.aswin.boxlore.feature.home.logic.toRecommendationPodcast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// from private suspend fun resolveFavoritePodcast
internal suspend fun HomeViewModel.resolveFavoritePodcast(
    overriddenId: String?,
    subscriptions: List<Podcast>,
    historyList: List<HomeListeningHistoryItem>,
): Podcast? {
    if (!HomePersonalizationModeLogic.showBecauseYouLike(_personalizationMode.value) &&
        overriddenId == null
    ) {
        // Cold-start / regional: BYL stays hidden unless the user already forced an anchor.
        return null
    }

    val historySignals = historyList.map { HomeBecauseYouLikeLogic.toAffinitySignal(it) }
    if (overriddenId != null) {
        val sub = subscriptions.find { it.id == overriddenId }
        if (sub != null) return sub

        localCatalog.getLocalPodcast(overriddenId)?.let { return it }

        val hist = historySignals.find { it.podcastId == overriddenId }
        if (hist != null) {
            return PodcastAffinityLogic.podcastFromHistorySignal(hist)
        }

        return null
    }

    val hidden = adaptiveRankingRepository.hardExcludedPodcastIds()
    val showBeliefs = adaptiveRankingRepository.showFacetBeliefs()
    val lastPlayedMap =
        historySignals
            .groupBy { it.podcastId }
            .mapValues { (_, items) -> items.maxOfOrNull { it.lastPlayedAt } ?: 0L }
    val selection =
        HomeAnchorSelectionLogic.selectAutomatic(
            candidates =
                showBeliefs.map { (podcastId, belief) ->
                    HomeAnchorSelectionLogic.ShowCandidate(
                        podcastId = podcastId,
                        affinity = belief.affinity,
                        confidence = belief.confidence,
                        evidence = belief.evidence,
                        lastPlayedAt = lastPlayedMap[podcastId] ?: 0L,
                        isHidden = podcastId in hidden,
                    )
                },
            currentAnchorId = _seemsToLikePodcast.value?.id,
            manualOverrideId = null,
        ) ?: return null

    val topPodId = selection.podcastId
    subscriptions.find { it.id == topPodId }?.let { return it }
    localCatalog.getLocalPodcast(topPodId)?.let { return it }

    val hist = historySignals.find { it.podcastId == topPodId }
    if (hist != null) {
        return PodcastAffinityLogic.podcastFromHistorySignal(hist)
    }

    return Podcast(
        id = topPodId,
        title = "Podcast",
        artist = "",
        imageUrl = "",
        fallbackImageUrl = "",
        description = "",
    )
}

// from private fun fetchBecauseYouLikeRecommendations
internal fun HomeViewModel.fetchBecauseYouLikeRecommendations(
    podcast: Podcast,
    region: String,
) {
    viewModelScope.launch {
        _isBecauseYouLikeLoading.value = true
        try {
            if (!HomePersonalizationModeLogic.showBecauseYouLike(_personalizationMode.value)) {
                _becauseYouLikePodcasts.value = emptyList()
                _becauseYouLikeRecommendations.value = emptyList()
                return@launch
            }
            val title = podcast.title
            val desc = podcast.description ?: ""
            val id = podcast.id
            val history = playbackRepository.getHistoryForRecommendations(15)
            val retainedTasteIds = _recommendations.value.mapNotNull { it.podcastId }
            val slate =
                homePersonalizationCoordinator.loadSlate(
                    cx.aswin.boxlore.core.catalog.home.HomePersonalizationCoordinator.SlateRequest(
                        modules = listOf("because_you_like"),
                        country = region,
                        languages =
                            cx.aswin.boxlore.core.catalog.recommendationLanguagesForCountry(region),
                        history = history,
                        anchorPodcastId = id,
                        missionId = _uiState.value.activeDiscoveryMissionId,
                        excludedPodcastIds = retainedTasteIds + listOf(id),
                        excludedEpisodeIds = _recommendations.value.map { it.id },
                        noveltyPreference = null,
                        daypart = clockContextFlow.value.daypart.name.lowercase(),
                        revision = "home-v1-byl|$id",
                        becauseYouLikeOnly = true,
                    ),
                )
            if (slate.becauseYouLikeEpisodes.isNotEmpty() || slate.becauseYouLikePodcasts.isNotEmpty()) {
                val ranked =
                    rankBecauseYouLike(
                        slate.becauseYouLikePodcasts,
                        slate.becauseYouLikeEpisodes,
                    )
                _becauseYouLikePodcasts.value = ranked.first
                _becauseYouLikeRecommendations.value = ranked.second
                try {
                    val json = Json { ignoreUnknownKeys = true }
                    boxcastPrefs.saveBylCache(
                        episodesJson = json.encodeToString(ranked.second),
                        podcastsJson = json.encodeToString(ranked.first),
                        podcastId = id,
                    )
                } catch (ce: Exception) {
                    android.util.Log.e("HomeViewModel", "Failed to cache because-you-like", ce)
                }
                return@launch
            }

            android.util.Log.d(
                "HomeViewModel",
                "Fetching because-you-like recommendations for: $title (ID: $id), region: $region",
            )
            val data =
                podcastRepository.getBecauseYouLikeRecommendations(
                    podcastTitle = title,
                    podcastDescription = desc,
                    excludePodcastId = id,
                    country = region,
                )

            val distinctPodcasts =
                HomeBecauseYouLikeLogic.distinctByIdAndTitle(
                    data.podcasts,
                    id = { it.id },
                    title = { it.title },
                )
            val distinctEpisodes =
                HomeBecauseYouLikeLogic.distinctByIdAndTitle(
                    data.episodes,
                    id = { it.id },
                    title = { it.title },
                )
            val ranked = rankBecauseYouLike(distinctPodcasts, distinctEpisodes)

            _becauseYouLikePodcasts.value = ranked.first
            _becauseYouLikeRecommendations.value = ranked.second

            try {
                val json = Json { ignoreUnknownKeys = true }
                val serializedEpisodes = json.encodeToString(ranked.second)
                val serializedPodcasts = json.encodeToString(ranked.first)
                boxcastPrefs.saveBylCache(
                    episodesJson = serializedEpisodes,
                    podcastsJson = serializedPodcasts,
                    podcastId = id,
                )
            } catch (ce: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to cache because-you-like recommendations", ce)
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Failed to fetch because-you-like recommendations", e)
        } finally {
            _isBecauseYouLikeLoading.value = false
        }
    }
}

// from private suspend fun rankBecauseYouLike
internal suspend fun HomeViewModel.rankBecauseYouLike(
    podcasts: List<Podcast>,
    episodes: List<Episode>,
): Pair<List<Podcast>, List<Episode>> {
    val history = playbackRepository.getRecentHistoryList(300)
    val subscribedIds = subscriptionRepository.subscribedPodcastIds.first()
    val podcastById = podcasts.associateBy(Podcast::id)
    val podcastInputs =
        podcasts.mapIndexedNotNull { index, candidate ->
            candidate.latestEpisode?.let { episode ->
                EpisodeRankingInput(
                    episode = episode,
                    podcast = candidate,
                    priorScore = (podcasts.size - index).toDouble(),
                    source = CandidateSource.SERVER_RECOMMENDATION,
                    isNovel = candidate.id !in subscribedIds,
                )
            }
        }
    val episodeInputs =
        episodes.mapIndexed { index, episode ->
            EpisodeRankingInput(
                episode = episode,
                podcast = podcastById[episode.podcastId] ?: episode.toRecommendationPodcast(),
                priorScore = (episodes.size - index).toDouble(),
                source = CandidateSource.SERVER_RECOMMENDATION,
                isNovel = episode.podcastId !in subscribedIds,
            )
        }
    val podcastScores =
        adaptiveScorer.scoreEpisodes(
            podcastInputs,
            history,
            RankingObjective.DISCOVERY,
            RankingSurface.HOME,
        )
    val episodeScores =
        adaptiveScorer.scoreEpisodes(
            episodeInputs,
            history,
            RankingObjective.DISCOVERY,
            RankingSurface.HOME,
        )
    return HomeBecauseYouLikeLogic.sortPodcastsByEpisodeScores(podcasts, podcastScores) to
        HomeBecauseYouLikeLogic.sortEpisodesByScores(episodes, episodeScores)
}

