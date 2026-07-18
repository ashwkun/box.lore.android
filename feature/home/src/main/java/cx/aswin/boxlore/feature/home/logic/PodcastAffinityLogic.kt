package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast

/**
 * Pure affinity scoring for Home "favorite" / Because You Like podcast resolution.
 * Extracted from [cx.aswin.boxlore.feature.home.HomeViewModel] for JVM unit tests.
 */
object PodcastAffinityLogic {
    data class HistorySignal(
        val podcastId: String,
        val podcastName: String,
        val podcastImageUrl: String?,
        val progressMs: Long,
        val lastPlayedAt: Long,
        val isCompleted: Boolean,
        val isLiked: Boolean,
    )

    fun historyScoreIncrement(history: HistorySignal): Int {
        var score = 0
        if (history.isCompleted) {
            score += 20
        } else {
            if (history.progressMs >= 300_000L) {
                score += 15
            } else if (history.progressMs >= 60_000L) {
                score += 5
            }
        }
        if (history.isLiked) {
            score += 40
        }
        return score
    }

    fun calculatePodcastAffinityScores(
        subscriptions: List<Podcast>,
        historyList: List<HistorySignal>,
        lastPlayedMap: MutableMap<String, Long>,
        podcastNameMap: MutableMap<String, String>,
        podcastImageMap: MutableMap<String, String>,
    ): Map<String, Int> {
        val scores = mutableMapOf<String, Int>()

        historyList.forEach { history ->
            val podId = history.podcastId
            if (podId.isNotEmpty()) {
                podcastNameMap[podId] = history.podcastName
                podcastImageMap[podId] = history.podcastImageUrl ?: ""

                val currentLastPlayed = lastPlayedMap.getOrDefault(podId, 0L)
                if (history.lastPlayedAt > currentLastPlayed) {
                    lastPlayedMap[podId] = history.lastPlayedAt
                }

                scores[podId] = scores.getOrDefault(podId, 0) + historyScoreIncrement(history)
            }
        }

        subscriptions.forEach { sub ->
            scores[sub.id] = scores.getOrDefault(sub.id, 0) + 100
            podcastNameMap[sub.id] = sub.title
            podcastImageMap[sub.id] = sub.imageUrl
        }

        return scores
    }

    /**
     * Picks the top affinity podcast id when no explicit override is set.
     * Returns null when scores are empty or the top score is below [minScore].
     */
    fun topAffinityPodcastId(
        scores: Map<String, Int>,
        lastPlayedMap: Map<String, Long>,
        minScore: Int = 15,
    ): String? {
        if (scores.isEmpty()) return null
        val topEntry =
            scores.maxByOrNull { entry ->
                entry.value.toLong() * 1_000_000_000_000L + lastPlayedMap.getOrDefault(entry.key, 0L)
            } ?: return null
        if (topEntry.value < minScore) return null
        return topEntry.key
    }

    fun podcastFromHistorySignal(signal: HistorySignal): Podcast =
        Podcast(
            id = signal.podcastId,
            title = signal.podcastName,
            artist = "",
            imageUrl = signal.podcastImageUrl ?: "",
            fallbackImageUrl = "",
            description = "",
        )
}
