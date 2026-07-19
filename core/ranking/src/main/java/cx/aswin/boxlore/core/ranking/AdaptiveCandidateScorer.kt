package cx.aswin.boxlore.core.ranking

import android.content.Context
import cx.aswin.boxlore.core.database.PodcastScoring
import cx.aswin.boxlore.core.database.ScorablePodcast
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodcastGenres
import kotlin.math.ln

data class EpisodeRankingInput(
    val episode: Episode,
    val podcast: Podcast,
    val priorScore: Double,
    val source: CandidateSource,
    val isNovel: Boolean = false,
    val online: Boolean = true,
)

data class PodcastRankingInput(
    val podcast: Podcast,
    val priorScore: Double,
    val source: CandidateSource,
    val isNovel: Boolean = false,
    val timeContextMatch: Double = 0.5,
)

class AdaptiveCandidateScorer private constructor(
    private val rankingRepository: AdaptiveRankingRepository,
    private val runtimeControls: RankingRuntimeControls,
) {
    suspend fun scorePodcasts(
        podcasts: List<ScorablePodcast>,
        history: List<ListeningHistoryEntity>,
        objective: RankingObjective,
        surface: RankingSurface,
        includeAutoDownloadBoost: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
    ): Map<String, Double> {
        if (podcasts.isEmpty()) return emptyMap()
        val legacy = PodcastScoring.calculateScores(
            podcasts = podcasts,
            allHistory = history,
            includeAutoDownloadBoost = includeAutoDownloadBoost,
        )
        if (!runtimeControls.isAdaptiveEnabled(objective, surface)) return legacy
        val normalizedPriors = normalizePriors(legacy)
        val historyByPodcast = history.groupBy(ListeningHistoryEntity::podcastId)
        val affinities = rankingRepository.facetAffinities(
            podcasts.mapTo(mutableSetOf()) { podcast ->
                PreferenceFacetKey(PreferenceFacetType.SHOW, podcast.id)
            },
            nowMs,
        )
        val featuresByPodcast = podcasts.associate { podcast ->
            val podcastHistory = historyByPodcast[podcast.id].orEmpty()
            val latestHistory = podcast.latestEpisode?.let { latest ->
                podcastHistory.firstOrNull { it.episodeId == latest.id }
            }
            val showAffinity =
                affinities[PreferenceFacetKey(PreferenceFacetType.SHOW, podcast.id)] ?: 0.0
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
            podcast.id to features
        }
        val scores = rankingRepository.scoreBatch(
            objective = objective,
            inputs = podcasts.map { podcast ->
                RankingScoreInput(
                    features = featuresByPodcast.getValue(podcast.id),
                    priorScore = normalizedPriors[podcast.id] ?: 0.0,
                )
            },
        )
        val adaptive = podcasts.mapIndexed { index, podcast ->
            podcast.id to scores[index].finalScore
        }.toMap()
        recordShadowComparison(objective, legacy, adaptive)
        return adaptive
    }

    suspend fun scoreEpisodes(
        inputs: List<EpisodeRankingInput>,
        history: List<ListeningHistoryEntity>,
        objective: RankingObjective,
        surface: RankingSurface,
        nowMs: Long = System.currentTimeMillis(),
    ): Map<String, Double> {
        if (inputs.isEmpty()) return emptyMap()
        val normalizedPriors = normalizePriors(inputs.associate { it.episode.id to it.priorScore })
        if (!runtimeControls.isAdaptiveEnabled(objective, surface)) return normalizedPriors
        val historyByEpisode = history.associateBy(ListeningHistoryEntity::episodeId)
        val facetKeys = buildSet {
            inputs.forEach { input ->
                add(PreferenceFacetKey(PreferenceFacetType.SHOW, input.podcast.id))
                add(PreferenceFacetKey(PreferenceFacetType.SOURCE, input.source.name))
                input.podcast.rankingGenres().forEach { genre ->
                    add(PreferenceFacetKey(PreferenceFacetType.GENRE, genre))
                }
            }
        }
        val affinities = rankingRepository.facetAffinities(facetKeys, nowMs)
        val featuresByEpisode = inputs.associate { input ->
            val episodeHistory = historyByEpisode[input.episode.id]
            val showAffinity =
                affinities[PreferenceFacetKey(PreferenceFacetType.SHOW, input.podcast.id)] ?: 0.0
            val genreAffinity = input.podcast.rankingGenres()
                .map { genre ->
                    affinities[PreferenceFacetKey(PreferenceFacetType.GENRE, genre)] ?: 0.0
                }
                .averageOrNeutral()
            val sourceAffinity =
                affinities[PreferenceFacetKey(PreferenceFacetType.SOURCE, input.source.name)] ?: 0.0
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
            input.episode.id to features
        }
        val scores = rankingRepository.scoreBatch(
            objective = objective,
            inputs = inputs.map { input ->
                RankingScoreInput(
                    features = featuresByEpisode.getValue(input.episode.id),
                    priorScore = normalizedPriors[input.episode.id] ?: 0.0,
                )
            },
        )
        val adaptive = inputs.mapIndexed { index, input ->
            input.episode.id to scores[index].finalScore
        }.toMap()
        recordShadowComparison(objective, normalizedPriors, adaptive)
        return adaptive
    }

    suspend fun rankEpisodes(
        inputs: List<EpisodeRankingInput>,
        history: List<ListeningHistoryEntity>,
        objective: RankingObjective,
        surface: RankingSurface,
        diversityPolicy: DiversityPolicy,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Episode> {
        val scores = scoreEpisodes(inputs, history, objective, surface, nowMs)
        val candidates = inputs.map { input ->
            RankedCandidate(
                value = input.episode,
                episodeId = input.episode.id,
                podcastId = input.podcast.id,
                genre = input.episode.podcastGenre ?: input.podcast.genre,
                score = scores[input.episode.id] ?: 0.0,
                isNovel = input.isNovel,
            )
        }
        val ranked = DiversityReranker.rerank(candidates, diversityPolicy)
            .map(RankedCandidate<Episode>::value)
        if (runtimeControls.isShadowDiagnosticsEnabled() &&
            runtimeControls.isAdaptiveEnabled(objective, surface)
        ) {
            RankingShadowDiagnostics.record(
                objective = objective,
                priorOrder = inputs.sortedByDescending(EpisodeRankingInput::priorScore)
                    .map { it.episode.id },
                adaptiveOrder = ranked.map(Episode::id),
            )
        }
        return ranked
    }

    suspend fun rankPodcasts(
        inputs: List<PodcastRankingInput>,
        history: List<ListeningHistoryEntity>,
        objective: RankingObjective,
        surface: RankingSurface,
        diversityPolicy: DiversityPolicy? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Podcast> {
        if (inputs.isEmpty()) return emptyList()
        val normalizedPriors = normalizePriors(inputs.associate { it.podcast.id to it.priorScore })
        val historyByPodcast = history.groupBy(ListeningHistoryEntity::podcastId)
        val adaptiveEnabled = runtimeControls.isAdaptiveEnabled(objective, surface)
        val facetKeys = if (adaptiveEnabled) {
            buildSet {
                inputs.forEach { input ->
                    add(PreferenceFacetKey(PreferenceFacetType.SHOW, input.podcast.id))
                    add(PreferenceFacetKey(PreferenceFacetType.SOURCE, input.source.name))
                    input.podcast.rankingGenres().forEach { genre ->
                        add(PreferenceFacetKey(PreferenceFacetType.GENRE, genre))
                    }
                }
            }
        } else {
            emptySet()
        }
        val affinities = rankingRepository.facetAffinities(facetKeys, nowMs)
        val features = inputs.map { input ->
            val podcast = input.podcast
            val latestHistory = podcast.latestEpisode?.let { episode ->
                historyByPodcast[podcast.id].orEmpty().firstOrNull { it.episodeId == episode.id }
            }
            val showAffinity =
                affinities[PreferenceFacetKey(PreferenceFacetType.SHOW, podcast.id)] ?: 0.0
            val genreAffinity = podcast.rankingGenres()
                .map { genre ->
                    affinities[PreferenceFacetKey(PreferenceFacetType.GENRE, genre)] ?: 0.0
                }
                .averageOrNeutral()
            CandidateFeatureBuilder.build(
                CandidateSignals(
                    showAffinity = showAffinity.toUnitAffinity(),
                    genreAffinity = genreAffinity.toUnitAffinity(),
                    sourceAffinity = (
                        affinities[PreferenceFacetKey(PreferenceFacetType.SOURCE, input.source.name)]
                            ?: 0.0
                        ).toUnitAffinity(),
                    ageHours = podcast.latestEpisode?.let { episode ->
                        (nowMs / 1_000.0 - episode.publishedDate).coerceAtLeast(0.0) / 3_600.0
                    },
                    isUnseenShow = input.isNovel,
                    isSubscribed = podcast.subscribedAt > 0L,
                    progressRatio = latestHistory.progressRatio(),
                    isUnplayed = latestHistory == null ||
                        (latestHistory.progressMs == 0L && !latestHistory.isCompleted),
                    serverRelevance = normalizedPriors[podcast.id] ?: 0.0,
                    timeContextMatch = input.timeContextMatch,
                    explicitPreference = when {
                        podcast.autoDownloadEnabled -> 1.0
                        podcast.notificationsEnabled -> 0.7
                        else -> 0.0
                    },
                    hoursSinceSubscription = podcast.subscribedAt
                        .takeIf { it > 0L }
                        ?.let { (nowMs - it).coerceAtLeast(0L) / 3_600_000.0 },
                ),
            )
        }
        val adaptiveScores = if (adaptiveEnabled) {
            rankingRepository.scoreBatch(
                objective = objective,
                inputs = inputs.mapIndexed { index, input ->
                    RankingScoreInput(
                        features = features[index],
                        priorScore = normalizedPriors[input.podcast.id] ?: 0.0,
                    )
                },
            )
        } else {
            emptyList()
        }
        val scored = inputs.mapIndexed { index, input ->
            val podcast = input.podcast
            val priorScore = normalizedPriors[podcast.id] ?: 0.0
            val finalScore = adaptiveScores.getOrNull(index)?.finalScore ?: priorScore
            RankedCandidate(
                value = podcast,
                episodeId = podcast.id,
                podcastId = podcast.id,
                genre = podcast.genre.substringBefore(",").trim(),
                score = finalScore,
                isNovel = input.isNovel,
            )
        }
        val ordered = if (diversityPolicy == null) {
            scored.distinctBy(RankedCandidate<Podcast>::episodeId)
                .sortedByDescending(RankedCandidate<Podcast>::score)
        } else {
            DiversityReranker.rerank(scored, diversityPolicy)
        }
        val ranked = ordered.map(RankedCandidate<Podcast>::value)
        if (runtimeControls.isShadowDiagnosticsEnabled() &&
            runtimeControls.isAdaptiveEnabled(objective, surface)
        ) {
            RankingShadowDiagnostics.record(
                objective = objective,
                priorOrder = inputs.sortedByDescending(PodcastRankingInput::priorScore)
                    .map { it.podcast.id },
                adaptiveOrder = ranked.map(Podcast::id),
            )
        }
        return ranked
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

    private fun recordShadowComparison(
        objective: RankingObjective,
        priors: Map<String, Double>,
        adaptive: Map<String, Double>,
    ) {
        if (!runtimeControls.isShadowDiagnosticsEnabled()) return
        RankingShadowDiagnostics.record(
            objective = objective,
            priorOrder = priors.entries.sortedByDescending(Map.Entry<String, Double>::value)
                .map(Map.Entry<String, Double>::key),
            adaptiveOrder = adaptive.entries.sortedByDescending(Map.Entry<String, Double>::value)
                .map(Map.Entry<String, Double>::key),
        )
    }

    companion object {
        @Volatile
        private var instance: AdaptiveCandidateScorer? = null

        fun create(
            rankingRepository: AdaptiveRankingRepository,
            runtimeControls: RankingRuntimeControls,
        ): AdaptiveCandidateScorer = AdaptiveCandidateScorer(rankingRepository, runtimeControls)

        fun install(value: AdaptiveCandidateScorer) {
            instance = value
        }

        /** Prefer AppContainer / SharedAppDependenciesHolder in production. */
        fun getInstance(context: Context): AdaptiveCandidateScorer {
            return instance ?: synchronized(this) {
                instance ?: create(
                    AdaptiveRankingRepository.getInstance(context.applicationContext),
                    RankingRuntimeControls.getInstance(context.applicationContext),
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

private fun Podcast.rankingGenres(): List<String> = genre
    .split(",")
    .mapNotNull(PodcastGenres::canonicalize)
    .distinct()

private fun durationFit(durationSeconds: Int): Double {
    if (durationSeconds <= 0) return 0.5
    val idealSeconds = 45.0 * 60.0
    return (1.0 - kotlin.math.abs(durationSeconds - idealSeconds) / idealSeconds)
        .coerceIn(0.0, 1.0)
}
