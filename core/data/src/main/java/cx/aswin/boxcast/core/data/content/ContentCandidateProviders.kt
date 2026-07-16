package cx.aswin.boxcast.core.data.content

import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.data.ranking.AdaptiveCandidateScorer
import cx.aswin.boxcast.core.data.ranking.CandidateSource
import cx.aswin.boxcast.core.data.ranking.EpisodeRankingInput
import cx.aswin.boxcast.core.data.ranking.PodcastRankingInput
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.CancellationException

class PodcastCandidateProvider(
    override val source: CandidateSource,
    private val loader: suspend (ContentIntent, ContentContext) -> List<Podcast>,
) : CandidateProvider {
    override suspend fun candidates(
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        return loader(intent, context).mapIndexed { index, podcast ->
            ContentCandidate(
                id = podcast.latestEpisode?.id ?: "podcast:${podcast.id}",
                episode = podcast.latestEpisode,
                podcast = podcast,
                source = source,
                intentId = intent.id,
                retrievalScore = reciprocalRank(index),
                isNovel = podcast.subscribedAt <= 0L,
            )
        }
    }
}

class EpisodeCandidateProvider(
    override val source: CandidateSource,
    private val loader: suspend (ContentIntent, ContentContext) -> List<Pair<Episode, Podcast>>,
) : CandidateProvider {
    override suspend fun candidates(
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        return loader(intent, context).mapIndexed { index, (episode, podcast) ->
            ContentCandidate(
                id = episode.id,
                episode = episode,
                podcast = podcast,
                source = source,
                intentId = intent.id,
                retrievalScore = episode.retrievalScore ?: reciprocalRank(index),
                isNovel = podcast.subscribedAt <= 0L,
                explanationTokens = setOfNotNull(episode.recommendationReason),
            )
        }
    }
}

class ServerIntentCandidateProvider(
    private val podcastRepository: PodcastRepository,
) : CandidateProvider {
    override val source: CandidateSource = CandidateSource.CURATED_INTENT

    override suspend fun candidates(
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        val query = intent.providerQueryRef ?: return emptyList()
        return podcastRepository.getCuratedPodcasts(query, context.region).mapIndexed { index, podcast ->
            ContentCandidate(
                id = podcast.latestEpisode?.id ?: "podcast:${podcast.id}",
                episode = podcast.latestEpisode,
                podcast = podcast,
                source = source,
                intentId = intent.id,
                retrievalScore = reciprocalRank(index),
                isNovel = podcast.subscribedAt <= 0L,
                explanationTokens = setOf(intent.id),
            )
        }
    }
}

class AdaptiveContentCandidateRanker(
    private val scorer: AdaptiveCandidateScorer,
    private val historyProvider: suspend () -> List<ListeningHistoryEntity>,
) : ContentCandidateRanker {
    override suspend fun rank(
        candidates: List<ContentCandidate>,
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        return try {
            rankAdaptively(candidates, intent, context)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            candidates.sortedWith(
                compareByDescending(ContentCandidate::retrievalScore)
                    .thenBy { it.serverRank ?: Int.MAX_VALUE }
                    .thenBy(ContentCandidate::id),
            )
        }
    }

    private suspend fun rankAdaptively(
        candidates: List<ContentCandidate>,
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val history = historyProvider()
        val episodeCandidates = candidates.filter { it.episode != null }
        val episodeScores = scorer.scoreEpisodes(
            inputs = episodeCandidates.map { candidate ->
                EpisodeRankingInput(
                    episode = requireNotNull(candidate.episode),
                    podcast = candidate.podcast,
                    priorScore = candidate.retrievalScore,
                    source = candidate.source,
                    isNovel = candidate.isNovel,
                    online = context.isOnline,
                )
            },
            history = history,
            objective = intent.objective,
            surface = context.surface,
        )
        val podcastCandidates = candidates.filter { it.episode == null }
        val rankedPodcasts = scorer.rankPodcasts(
            inputs = podcastCandidates.map { candidate ->
                PodcastRankingInput(
                    podcast = candidate.podcast,
                    priorScore = candidate.retrievalScore,
                    source = candidate.source,
                    isNovel = candidate.isNovel,
                    timeContextMatch = if (intent.eligibleDayparts.contains(context.daypart)) 1.0 else 0.0,
                )
            },
            history = history,
            objective = intent.objective,
            surface = context.surface,
        )
        val podcastScores = rankedPodcasts.mapIndexed { index, podcast ->
            podcast.id to reciprocalRank(index)
        }.toMap()
        return candidates.map { candidate ->
            candidate.copy(
                rankingScore = candidate.episode?.let { episodeScores[it.id] }
                    ?: podcastScores[candidate.podcast.id]
                    ?: candidate.retrievalScore,
            )
        }.sortedWith(
            compareByDescending<ContentCandidate>(ContentCandidate::rankingScore)
                .thenBy { it.serverRank ?: Int.MAX_VALUE }
                .thenByDescending(ContentCandidate::retrievalScore)
                .thenBy(ContentCandidate::id),
        )
    }
}

private fun reciprocalRank(index: Int): Double = 1.0 / (index + 1.0)
