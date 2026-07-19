package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.logic.HomeBecauseYouLikeLogic
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

    if (subscriptions.isEmpty() && historyList.isEmpty()) return null

    val lastPlayedMap = mutableMapOf<String, Long>()
    val podcastNameMap = mutableMapOf<String, String>()
    val podcastImageMap = mutableMapOf<String, String>()

    val scores =
        PodcastAffinityLogic.calculatePodcastAffinityScores(
            subscriptions = subscriptions,
            historyList = historySignals,
            lastPlayedMap = lastPlayedMap,
            podcastNameMap = podcastNameMap,
            podcastImageMap = podcastImageMap,
        )

    val topPodId =
        PodcastAffinityLogic.topAffinityPodcastId(scores, lastPlayedMap)
            ?: return null

    val sub = subscriptions.find { it.id == topPodId }
    if (sub != null) return sub

    localCatalog.getLocalPodcast(topPodId)?.let { return it }

    return Podcast(
        id = topPodId,
        title = podcastNameMap[topPodId] ?: "Podcast",
        artist = "",
        imageUrl = podcastImageMap[topPodId] ?: "",
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
            val title = podcast.title
            val desc = podcast.description ?: ""
            val id = podcast.id

            android.util.Log.d("HomeViewModel", "Fetching because-you-like recommendations for: $title (ID: $id), region: $region")
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

            android.util.Log.d(
                "HomeViewModel",
                "Fetched because-you-like: podcasts count = ${distinctPodcasts.size}, episodes count = ${distinctEpisodes.size}",
            )

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

