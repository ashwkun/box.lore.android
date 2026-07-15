package cx.aswin.boxcast.core.data.ranking

import android.content.Context
import cx.aswin.boxcast.core.data.PodcastScoring
import cx.aswin.boxcast.core.data.ScorablePodcast
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlin.math.ln

data class EpisodeRankingInput(
    val episode: Episode,
    val podcast: Podcast,
    val priorScore: Double,
    val source: CandidateSource,
    val isNovel: Boolean = false,
    val online: Boolean = true,
)

class AdaptiveCandidateScorer private constructor(
    private val rankingRepository: AdaptiveRankingRepository,
) {
    suspend fun scorePodcasts(
        podcasts: List<ScorablePodcast>,
        history: List<ListeningHistoryEntity>,
        objective: RankingObjective,
        includeAutoDownloadBoost: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
    ): Map<String, Double> {
        if (podcasts.isEmpty()) return emptyMap()
        val legacy = PodcastScoring.calculateScores(
            podcasts = podcasts,
            allHistory = history,
            includeAutoDownloadBoost = includeAutoDownloadBoost,
        )
        val normalizedPriors = normalizePriors(legacy)
        val historyByPodcast = history.groupBy(ListeningHistoryEntity::podcastId)
        return podcasts.associate { podcast ->
            val podcastHistory = historyByPodcast[podcast.id].orEmpty()
            val latestHistory = podcast.latestEpisode?.let { latest ->
                podcastHistory.firstOrNull { it.episodeId == latest.id }
            }
            val showAffinity = rankingRepository.facetAffinity(
                PreferenceFacetType.SHOW,
                podcast.id,
                nowMs,
            )
            val features = CandidateFeatureBuilder.build(
                CandidateSignals(
                    showAffinity = showAffinity.toUnitAffinity(),
                    ageHours = podcast.latestEpisode?.let { episode ->
                        (nowMs / 1_000.0 - episode.publishedDate).coerceAtLeast(0.0) / 3_600.0
                    },
                    isSubscribed = true,
                    progressRatio = latestHistory.progressRatio(),
                    isUnplayed = latestHistory == null ||
                        (latestHistory.progressMs == 0L && !latestHistory.isCompleted),
                    serialMatch = if (podcast.latestEpisode != null) 1.0 else 0.0,
                    explicitPreference = when {
                        podcast.autoDownloadEnabled && includeAutoDownloadBoost -> 1.0
                        podcast.notificationsEnabled -> 0.7
                        else -> 0.0
                    },
                    hoursSinceSubscription = podcast.subscribedAt
                        .takeIf { it > 0L }
                        ?.let { (nowMs - it).coerceAtLeast(0L) / 3_600_000.0 },
                ),
            )
            val score = rankingRepository.score(
                objective = objective,
                features = features,
                priorScore = normalizedPriors[podcast.id] ?: 0.0,
            )
            podcast.id to score.finalScore
        }
    }

    suspend fun scoreEpisodes(
        inputs: List<EpisodeRankingInput>,
        history: List<ListeningHistoryEntity>,
        objective: RankingObjective,
        nowMs: Long = System.currentTimeMillis(),
    ): Map<String, Double> {
        if (inputs.isEmpty()) return emptyMap()
        val normalizedPriors = normalizePriors(inputs.associate { it.episode.id to it.priorScore })
        val historyByEpisode = history.associateBy(ListeningHistoryEntity::episodeId)
        return inputs.associate { input ->
            val episodeHistory = historyByEpisode[input.episode.id]
            val showAffinity = rankingRepository.facetAffinity(
                PreferenceFacetType.SHOW,
                input.podcast.id,
                nowMs,
            )
            val genreAffinity = input.podcast.genre
                .split(",")
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { rankingRepository.facetAffinity(PreferenceFacetType.GENRE, it, nowMs) }
                .averageOrNeutral()
            val sourceAffinity = rankingRepository.facetAffinity(
                PreferenceFacetType.SOURCE,
                input.source.name,
                nowMs,
            )
            val features = CandidateFeatureBuilder.build(
                CandidateSignals(
                    showAffinity = showAffinity.toUnitAffinity(),
                    genreAffinity = genreAffinity.toUnitAffinity(),
                    sourceAffinity = sourceAffinity.toUnitAffinity(),
                    ageHours = (nowMs / 1_000.0 - input.episode.publishedDate)
                        .coerceAtLeast(0.0) / 3_600.0,
                    isUnseenShow = input.isNovel,
                    durationFit = durationFit(input.episode.duration),
                    isSubscribed = input.podcast.subscribedAt > 0L,
                    progressRatio = episodeHistory.progressRatio(),
                    isUnplayed = episodeHistory == null ||
                        (episodeHistory.progressMs == 0L && !episodeHistory.isCompleted),
                    serialMatch = if (input.podcast.preferredSort == "oldest") 1.0 else 0.5,
                    serverRelevance = normalizedPriors[input.episode.id] ?: 0.0,
                    offlineSuitability = if (input.online) {
                        durationFit(input.episode.duration)
                    } else {
                        1.0
                    },
                    explicitPreference = when {
                        input.podcast.autoDownloadEnabled -> 1.0
                        input.podcast.notificationsEnabled -> 0.7
                        else -> 0.0
                    },
                    hoursSinceSubscription = input.podcast.subscribedAt
                        .takeIf { it > 0L }
                        ?.let { (nowMs - it).coerceAtLeast(0L) / 3_600_000.0 },
                ),
            )
            val score = rankingRepository.score(
                objective = objective,
                features = features,
                priorScore = normalizedPriors[input.episode.id] ?: 0.0,
            )
            input.episode.id to score.finalScore
        }
    }

    private fun normalizePriors(scores: Map<String, Double>): Map<String, Double> {
        val finite = scores.filterValues { it.isFinite() && it >= 0.0 }
        val max = finite.values.maxOrNull() ?: return scores.mapValues { 0.0 }
        if (max <= 0.0) return scores.mapValues { 0.0 }
        val denominator = ln(1.0 + max)
        return scores.mapValues { (_, value) ->
            if (!value.isFinite() || value <= 0.0) 0.0
            else (ln(1.0 + value) / denominator).coerceIn(0.0, 1.0)
        }
    }

    companion object {
        @Volatile
        private var instance: AdaptiveCandidateScorer? = null

        fun getInstance(context: Context): AdaptiveCandidateScorer {
            return instance ?: synchronized(this) {
                instance ?: AdaptiveCandidateScorer(
                    AdaptiveRankingRepository.getInstance(context.applicationContext),
                ).also { instance = it }
            }
        }
    }
}

private fun Double.toUnitAffinity(): Double = ((coerceIn(-1.0, 1.0) + 1.0) / 2.0)

private fun ListeningHistoryEntity?.progressRatio(): Double {
    if (this == null || durationMs <= 0L) return 0.0
    return (progressMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
}

private fun List<Double>.averageOrNeutral(): Double = if (isEmpty()) 0.0 else average()

private fun durationFit(durationSeconds: Int): Double {
    if (durationSeconds <= 0) return 0.5
    val idealSeconds = 45.0 * 60.0
    return (1.0 - kotlin.math.abs(durationSeconds - idealSeconds) / idealSeconds)
        .coerceIn(0.0, 1.0)
}
