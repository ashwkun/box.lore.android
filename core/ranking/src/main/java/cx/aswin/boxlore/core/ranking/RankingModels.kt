package cx.aswin.boxlore.core.ranking

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

enum class RankingObjective(
    val allowsExploration: Boolean,
) {
    YOUR_SHOWS(allowsExploration = false),
    DISCOVERY(allowsExploration = true),
    CONTINUATION(allowsExploration = true),
    OFFLINE(allowsExploration = false),
    SLATE(allowsExploration = true),
}

enum class RankingSurface {
    HOME,
    EXPLORE,
    LIBRARY,
    QUEUE,
    DOWNLOADS,
    ANDROID_AUTO,
}

enum class CandidateSource {
    SUBSCRIPTION,
    LOCAL_HISTORY,
    SERVER_RECOMMENDATION,
    CURATED_INTENT,
    TRENDING,
    LIKED,
    DOWNLOADED,
}

enum class FeatureSlot {
    INTERCEPT,
    SHOW_AFFINITY,
    GENRE_AFFINITY,
    SOURCE_AFFINITY,
    FRESHNESS,
    NOVELTY,
    DURATION_FIT,
    SUBSCRIBED,
    RESUME_PROGRESS,
    UNPLAYED,
    SERIAL_MATCH,
    SERVER_RELEVANCE,
    EXPOSURE_FATIGUE,
    TIME_CONTEXT,
    OFFLINE_SUITABILITY,
    EXPLICIT_PREFERENCE,
    RECENT_SUBSCRIPTION,
    CURRENT_SHOW,
}

object RankingFeatureSchema {
    const val VERSION = 1
    val dimension: Int = FeatureSlot.entries.size
}

data class CandidateSignals(
    val showAffinity: Double = 0.0,
    val genreAffinity: Double = 0.0,
    val sourceAffinity: Double = 0.0,
    val ageHours: Double? = null,
    val isUnseenShow: Boolean = false,
    val durationFit: Double = 0.5,
    val isSubscribed: Boolean = false,
    val progressRatio: Double = 0.0,
    val isUnplayed: Boolean = true,
    val serialMatch: Double = 0.5,
    val serverRelevance: Double = 0.0,
    val recentExposureCount: Int = 0,
    val timeContextMatch: Double = 0.5,
    val offlineSuitability: Double = 0.5,
    val explicitPreference: Double = 0.0,
    val hoursSinceSubscription: Double? = null,
    val isCurrentShow: Boolean = false,
)

data class RankingFeatures(
    val schemaVersion: Int = RankingFeatureSchema.VERSION,
    val values: DoubleArray,
) {
    init {
        require(values.size == RankingFeatureSchema.dimension) {
            "Expected ${RankingFeatureSchema.dimension} ranking features, got ${values.size}"
        }
        require(values.all(Double::isFinite)) { "Ranking features must be finite" }
    }
}

object CandidateFeatureBuilder {
    fun build(signals: CandidateSignals): RankingFeatures {
        val values = DoubleArray(RankingFeatureSchema.dimension)
        values[FeatureSlot.INTERCEPT.ordinal] = 1.0
        values[FeatureSlot.SHOW_AFFINITY.ordinal] = signals.showAffinity.unit()
        values[FeatureSlot.GENRE_AFFINITY.ordinal] = signals.genreAffinity.unit()
        values[FeatureSlot.SOURCE_AFFINITY.ordinal] = signals.sourceAffinity.unit()
        values[FeatureSlot.FRESHNESS.ordinal] = signals.ageHours
            ?.coerceAtLeast(0.0)
            ?.let { exp(-it / (24.0 * 14.0)) }
            ?: 0.0
        values[FeatureSlot.NOVELTY.ordinal] = signals.isUnseenShow.asUnit()
        values[FeatureSlot.DURATION_FIT.ordinal] = signals.durationFit.unit()
        values[FeatureSlot.SUBSCRIBED.ordinal] = signals.isSubscribed.asUnit()
        values[FeatureSlot.RESUME_PROGRESS.ordinal] = signals.progressRatio.unit()
        values[FeatureSlot.UNPLAYED.ordinal] = signals.isUnplayed.asUnit()
        values[FeatureSlot.SERIAL_MATCH.ordinal] = signals.serialMatch.unit()
        values[FeatureSlot.SERVER_RELEVANCE.ordinal] = signals.serverRelevance.unit()
        values[FeatureSlot.EXPOSURE_FATIGUE.ordinal] =
            -(1.0 - exp(-signals.recentExposureCount.coerceAtLeast(0) / 3.0))
        values[FeatureSlot.TIME_CONTEXT.ordinal] = signals.timeContextMatch.unit()
        values[FeatureSlot.OFFLINE_SUITABILITY.ordinal] = signals.offlineSuitability.unit()
        values[FeatureSlot.EXPLICIT_PREFERENCE.ordinal] = signals.explicitPreference.coerceIn(-1.0, 1.0)
        values[FeatureSlot.RECENT_SUBSCRIPTION.ordinal] = signals.hoursSinceSubscription
            ?.coerceAtLeast(0.0)
            ?.let { exp(-it / (24.0 * 14.0)) }
            ?: 0.0
        values[FeatureSlot.CURRENT_SHOW.ordinal] = signals.isCurrentShow.asUnit()
        return RankingFeatures(values = values)
    }
}

data class RankingScore(
    val finalScore: Double,
    val priorScore: Double,
    val learnedScore: Double,
    val explorationBonus: Double,
    val learnedBlend: Double,
    val updateCount: Long,
    val contributions: Lazy<Map<FeatureSlot, Double>>,
)

data class RankedCandidate<T>(
    val value: T,
    val episodeId: String,
    val podcastId: String,
    val genre: String?,
    val score: Double,
    val isNovel: Boolean = false,
)

data class DiversityPolicy(
    val limit: Int,
    val maxPerShow: Int = 2,
    val genreRepeatPenalty: Double = 0.08,
    val recentPodcastIds: Set<String> = emptySet(),
    val recentShowPenalty: Double = 0.12,
    val reserveNovelSlot: Boolean = false,
)

object DiversityReranker {
    fun <T> rerank(
        candidates: List<RankedCandidate<T>>,
        policy: DiversityPolicy,
    ): List<RankedCandidate<T>> {
        if (policy.limit <= 0) return emptyList()
        val state = DiversitySelectionState(candidates, policy)
        while (state.canSelectMore()) {
            val next = state.nextCandidate() ?: break
            state.select(next)
        }
        state.reserveNovelCandidate()
        return state.selected
    }
}

private class DiversitySelectionState<T>(
    private val candidates: List<RankedCandidate<T>>,
    private val policy: DiversityPolicy,
) {
    val selected = mutableListOf<RankedCandidate<T>>()
    private val remaining = candidates.distinctBy { it.episodeId }.toMutableList()
    private val showCounts = mutableMapOf<String, Int>()
    private val genreCounts = mutableMapOf<String, Int>()

    fun canSelectMore(): Boolean = selected.size < policy.limit && remaining.isNotEmpty()

    fun nextCandidate(): RankedCandidate<T>? =
        remaining
            .asSequence()
            .filter(::isWithinShowCap)
            .maxByOrNull(::adjustedScore)

    fun select(candidate: RankedCandidate<T>) {
        selected += candidate
        remaining.remove(candidate)
        showCounts[candidate.podcastId] = (showCounts[candidate.podcastId] ?: 0) + 1
        candidate.normalizedGenre().takeIf(String::isNotEmpty)?.let { genre ->
            genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
        }
    }

    fun reserveNovelCandidate() {
        if (!policy.reserveNovelSlot || selected.any { it.isNovel }) return
        val novel =
            candidates
                .asSequence()
                .filter { it.isNovel }
                .filter { novel -> selected.none { it.episodeId == novel.episodeId } }
                .filter(::isWithinShowCapAfterEviction)
                .maxByOrNull { it.score }
                ?: return
        if (selected.size >= policy.limit) removeLastSelected()
        select(novel)
    }

    private fun isWithinShowCap(candidate: RankedCandidate<T>): Boolean = (showCounts[candidate.podcastId] ?: 0) < policy.maxPerShow

    private fun isWithinShowCapAfterEviction(candidate: RankedCandidate<T>): Boolean {
        val evicted = selected.lastOrNull().takeIf { selected.size >= policy.limit }
        val countAfterEviction =
            (showCounts[candidate.podcastId] ?: 0) -
                if (evicted?.podcastId == candidate.podcastId) 1 else 0
        return countAfterEviction < policy.maxPerShow
    }

    private fun removeLastSelected() {
        val evicted = selected.removeAt(selected.lastIndex)
        showCounts.decrement(evicted.podcastId)
        evicted.normalizedGenre().takeIf(String::isNotEmpty)?.let(genreCounts::decrement)
        if (remaining.none { it.episodeId == evicted.episodeId }) remaining += evicted
    }

    private fun adjustedScore(candidate: RankedCandidate<T>): Double {
        val genrePenalty =
            (genreCounts[candidate.normalizedGenre()] ?: 0) * policy.genreRepeatPenalty
        val recentPenalty =
            policy.recentShowPenalty.takeIf {
                candidate.podcastId in policy.recentPodcastIds
            } ?: 0.0
        return candidate.score - genrePenalty - recentPenalty
    }
}

private fun MutableMap<String, Int>.decrement(key: String) {
    val next = (this[key] ?: 1) - 1
    if (next <= 0) remove(key) else this[key] = next
}

private fun <T> RankedCandidate<T>.normalizedGenre(): String = genre?.trim()?.lowercase().orEmpty()

private fun Double.unit(): Double = min(1.0, max(0.0, this))

private fun Boolean.asUnit(): Double = if (this) 1.0 else 0.0
