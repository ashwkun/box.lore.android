package cx.aswin.boxlore.core.data

import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.data.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.data.ranking.CandidateSource
import cx.aswin.boxlore.core.data.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.data.ranking.RankingObjective
import cx.aswin.boxlore.core.data.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CancellationException

object MixtapeEngine {
    private const val MAX_ITEMS = 15
    private const val AFFINITY_WEIGHT = 0.8
    private const val MAX_RESUME_AGE_MS = 30L * 24L * 60L * 60L * 1_000L

    data class Result(
        val podcasts: List<Podcast>,
        val episodes: List<Episode>,
        val unplayedCount: Int,
    )

    data class AdaptiveRanking(
        val scorer: AdaptiveCandidateScorer,
        val objective: RankingObjective = RankingObjective.CONTINUATION,
        val surface: RankingSurface,
    )

    private data class Candidate(
        val podcast: Podcast,
        val episode: Episode,
        val score: Double,
        val isProgress: Boolean,
        val source: CandidateSource,
        val progressMs: Long = 0L,
        val durationMs: Long = 0L,
    )

    suspend fun build(
        subscriptions: List<Podcast>,
        history: List<ListeningHistoryEntity>,
        resolvedSerialEpisodes: Map<String, Episode> = emptyMap(),
        recommendations: List<Episode> = emptyList(),
        podcastScores: Map<String, Double> = PodcastScoring.calculateScores(
            podcasts = subscriptions.map(Podcast::toScorable),
            allHistory = history,
        ),
        adaptiveRanking: AdaptiveRanking? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): Result {
        val historyByEpisode = history.associateBy(ListeningHistoryEntity::episodeId)
        val subscriptionsById = subscriptions.associateBy(Podcast::id)
        val candidates = buildResumeCandidates(
            history = history,
            subscriptionsById = subscriptionsById,
            podcastScores = podcastScores,
            nowMs = nowMs,
        ) + buildUnplayedCandidates(
            subscriptions = subscriptions,
            resolvedSerialEpisodes = resolvedSerialEpisodes,
            historyByEpisode = historyByEpisode,
            podcastScores = podcastScores,
            nowMs = nowMs,
        )
        var ordered = orderCandidates(candidates).toMutableList()
        addRecommendationFallbacks(
            candidates = ordered,
            recommendations = recommendations,
            subscriptionsById = subscriptionsById,
            historyByEpisode = historyByEpisode,
        )
        if (adaptiveRanking != null) {
            ordered = try {
                val adaptiveScores = adaptiveRanking.scorer.scoreEpisodes(
                    inputs = ordered.map { candidate ->
                        EpisodeRankingInput(
                            episode = candidate.episode,
                            podcast = candidate.podcast,
                            priorScore = candidate.score,
                            source = candidate.source,
                            isNovel = candidate.source == CandidateSource.SERVER_RECOMMENDATION,
                        )
                    },
                    history = history,
                    objective = adaptiveRanking.objective,
                    surface = adaptiveRanking.surface,
                    nowMs = nowMs,
                )
                orderCandidates(
                    ordered.map { candidate ->
                        candidate.copy(score = adaptiveScores[candidate.episode.id] ?: candidate.score)
                    },
                ).take(MAX_ITEMS).toMutableList()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                orderCandidates(ordered).take(MAX_ITEMS).toMutableList()
            }
        } else {
            ordered = ordered.take(MAX_ITEMS).toMutableList()
        }
        val podcasts = ordered.map { candidate ->
            val status = when {
                candidate.isProgress -> EpisodeStatus.IN_PROGRESS
                historyByEpisode[candidate.episode.id]?.isCompleted == true ->
                    EpisodeStatus.COMPLETED
                else -> EpisodeStatus.UNPLAYED
            }
            val ratio = if (candidate.durationMs > 0L) {
                (candidate.progressMs.toFloat() / candidate.durationMs.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            candidate.podcast.copy(
                latestEpisode = candidate.episode,
                resumeProgress = when (status) {
                    EpisodeStatus.IN_PROGRESS -> ratio
                    EpisodeStatus.COMPLETED -> 1f
                    EpisodeStatus.UNPLAYED -> null
                },
                episodeStatus = status,
            )
        }
        return Result(
            podcasts = podcasts,
            episodes = ordered.map(Candidate::episode),
            unplayedCount = ordered.count {
                historyByEpisode[it.episode.id]?.isCompleted != true
            },
        )
    }

    private fun buildResumeCandidates(
        history: List<ListeningHistoryEntity>,
        subscriptionsById: Map<String, Podcast>,
        podcastScores: Map<String, Double>,
        nowMs: Long,
    ): List<Candidate> = history
        .filter { it.isUsefulResume(nowMs) }
        .groupBy(ListeningHistoryEntity::podcastId)
        .mapNotNull { (_, episodes) -> episodes.maxByOrNull(ListeningHistoryEntity::lastPlayedAt) }
        .mapNotNull { item ->
            val audioUrl = item.episodeAudioUrl?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val podcast = subscriptionsById[item.podcastId] ?: Podcast(
                id = item.podcastId,
                title = item.podcastName,
                artist = "",
                imageUrl = item.podcastImageUrl.orEmpty(),
                fallbackImageUrl = item.podcastImageUrl,
            )
            val episode = Episode(
                id = item.episodeId,
                title = item.episodeTitle,
                description = item.episodeDescription.orEmpty(),
                audioUrl = audioUrl,
                imageUrl = item.episodeImageUrl,
                podcastImageUrl = item.podcastImageUrl,
                podcastId = item.podcastId,
                podcastTitle = item.podcastName,
                duration = (item.durationMs / 1_000L).toInt(),
                enclosureType = item.enclosureType,
            )
            val progressRatio = item.progressMs.toDouble() / item.durationMs.toDouble()
            Candidate(
                podcast = podcast,
                episode = episode,
                score = 1_000.0 +
                    progressRatio * 500.0 +
                    AFFINITY_WEIGHT * podcastScores.getOrDefault(item.podcastId, 0.0),
                isProgress = true,
                source = CandidateSource.LOCAL_HISTORY,
                progressMs = item.progressMs,
                durationMs = item.durationMs,
            )
        }

    private fun buildUnplayedCandidates(
        subscriptions: List<Podcast>,
        resolvedSerialEpisodes: Map<String, Episode>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        podcastScores: Map<String, Double>,
        nowMs: Long,
    ): List<Candidate> = subscriptions.mapNotNull { podcast ->
        val episode = if (podcast.preferredSort == "oldest") {
            resolvedSerialEpisodes[podcast.id] ?: podcast.latestEpisode
        } else {
            podcast.latestEpisode
        } ?: return@mapNotNull null
        val history = historyByEpisode[episode.id]
        if (history != null && (history.progressMs > 0L || history.isCompleted)) {
            return@mapNotNull null
        }
        val isNewRelease = episode.publishedDate > podcast.subscribedAt / 1_000L
        val hoursSinceRelease = (
            nowMs / 1_000.0 - episode.publishedDate
            ).coerceAtLeast(0.0) / 3_600.0
        val freshnessBoost = if (isNewRelease) {
            500.0 / (1.0 + hoursSinceRelease / 24.0)
        } else {
            0.0
        }
        Candidate(
            podcast = podcast,
            episode = episode.copy(
                podcastId = podcast.id,
                podcastTitle = podcast.title,
                podcastArtist = podcast.artist,
                podcastImageUrl = podcast.imageUrl,
            ),
            score = 500.0 +
                freshnessBoost +
                (if (isNewRelease) 200.0 else 0.0) +
                (if (podcast.preferredSort == "oldest") 150.0 else 0.0) +
                AFFINITY_WEIGHT * podcastScores.getOrDefault(podcast.id, 0.0),
            isProgress = false,
            source = CandidateSource.SUBSCRIPTION,
        )
    }

    private fun orderCandidates(candidates: List<Candidate>): List<Candidate> {
        val deduplicated = candidates.distinctBy { it.episode.id }
        val resumes = deduplicated.filter(Candidate::isProgress)
            .sortedByDescending(Candidate::score)
        val unplayed = deduplicated.filterNot(Candidate::isProgress)
            .sortedByDescending(Candidate::score)
            .toMutableList()
        return buildList {
            resumes.forEach { resume ->
                add(resume)
                unplayed.firstOrNull { it.podcast.id == resume.podcast.id }?.let {
                    add(it)
                    unplayed.remove(it)
                }
            }
            addAll(unplayed)
        }
    }

    private fun addRecommendationFallbacks(
        candidates: MutableList<Candidate>,
        recommendations: List<Episode>,
        subscriptionsById: Map<String, Podcast>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
    ) {
        if (candidates.size >= 3) return
        recommendations.asSequence()
            .filter { episode ->
                val history = historyByEpisode[episode.id]
                (history == null || (history.progressMs == 0L && !history.isCompleted)) &&
                    candidates.none { it.episode.id == episode.id }
            }
            .take(3 - candidates.size)
            .forEach { episode ->
                val podcast = subscriptionsById[episode.podcastId] ?: Podcast(
                    id = episode.podcastId.orEmpty(),
                    title = episode.podcastTitle.orEmpty(),
                    artist = episode.podcastArtist.orEmpty(),
                    imageUrl = episode.podcastImageUrl ?: episode.imageUrl.orEmpty(),
                )
                candidates += Candidate(
                    podcast = podcast,
                    episode = episode,
                    score = 100.0,
                    isProgress = false,
                    source = CandidateSource.SERVER_RECOMMENDATION,
                )
            }
    }

    private fun ListeningHistoryEntity.isUsefulResume(nowMs: Long): Boolean {
        if (isCompleted || progressMs <= 0L || durationMs <= 0L) return false
        if (nowMs - lastPlayedAt > MAX_RESUME_AGE_MS) return false
        val ratio = progressMs.toDouble() / durationMs.toDouble()
        val remainingMs = durationMs - progressMs
        return ratio in 0.10..0.90 && remainingMs >= 120_000L
    }
}
