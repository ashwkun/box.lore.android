package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.data.database.PodcastEntity

data class ScorablePodcast(
    val id: String,
    val subscribedAt: Long,
    val latestEpisode: Episode?,
    val notificationsEnabled: Boolean,
    val autoDownloadEnabled: Boolean
)

object PodcastScoring {
    fun calculateScores(
        podcasts: List<ScorablePodcast>,
        allHistory: List<ListeningHistoryEntity>,
        includeAutoDownloadBoost: Boolean = true
    ): Map<String, Double> {
        val historyByPodcast = allHistory.groupBy { it.podcastId }
        val historyByEpisode = allHistory.associateBy { it.episodeId }

        return podcasts.associate { pod ->
            val podHistory = historyByPodcast[pod.id] ?: emptyList()
            val playCount = podHistory.size
            val likeCount = podHistory.count { it.isLiked }
            val playScore = 12.0 * playCount
            val likeScore = 25.0 * likeCount

            val lastPlayTime = podHistory.maxOfOrNull { it.lastPlayedAt }
            val playRecencyScore = if (lastPlayTime != null) {
                val hoursSinceLastPlay = (System.currentTimeMillis() - lastPlayTime).toDouble() / (1000.0 * 3600.0)
                250.0 / (1.0 + hoursSinceLastPlay.coerceAtLeast(0.0) / 24.0)
            } else {
                0.0
            }

            val latestEp = pod.latestEpisode
            val freshnessScore = if (latestEp != null) {
                val latestEpHistory = historyByEpisode[latestEp.id]
                val isUnplayed = latestEpHistory == null || (latestEpHistory.progressMs == 0L && !latestEpHistory.isCompleted)
                val releasedAfterSub = latestEp.publishedDate > (pod.subscribedAt / 1000L)
                if (isUnplayed && releasedAfterSub) {
                    val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - latestEp.publishedDate) / 3600.0
                    (150.0 / (1.0 + hoursSinceRelease.coerceAtLeast(0.0) / 24.0)) + 80.0
                } else {
                    0.0
                }
            } else {
                0.0
            }

            val subRecencyScore = if (pod.subscribedAt > 0L) {
                val hoursSinceSubscribed = (System.currentTimeMillis() - pod.subscribedAt).toDouble() / (1000.0 * 3600.0)
                350.0 / (1.0 + hoursSinceSubscribed.coerceAtLeast(0.0) / 24.0)
            } else {
                0.0
            }

            val notificationsBoost = if (pod.notificationsEnabled) 30.0 else 0.0
            val autoDownloadBoost = if (includeAutoDownloadBoost && pod.autoDownloadEnabled) 60.0 else 0.0

            pod.id to (playScore + likeScore + playRecencyScore + freshnessScore + subRecencyScore + notificationsBoost + autoDownloadBoost)
        }
    }
}

fun Podcast.toScorable() = ScorablePodcast(
    id = id,
    subscribedAt = subscribedAt,
    latestEpisode = latestEpisode,
    notificationsEnabled = notificationsEnabled,
    autoDownloadEnabled = autoDownloadEnabled
)

fun PodcastEntity.toScorable() = ScorablePodcast(
    id = podcastId,
    subscribedAt = subscribedAt,
    latestEpisode = latestEpisode,
    notificationsEnabled = notificationsEnabled,
    autoDownloadEnabled = autoDownloadEnabled
)
