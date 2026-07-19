package cx.aswin.boxlore.core.data

import cx.aswin.boxlore.core.data.database.BoxLoreDatabase
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository

/**
 * Process-wide façade of Application-scoped instances that workers and Media3 services
 * may consume without rebuilding a parallel repository graph.
 *
 * Installed once from [AppContainer] via [SharedAppDependenciesHolder] in Application.onCreate.
 * Do not construct ranking/RSS repositories ad hoc — use these instances.
 *
 * Download-owned types (DownloadRepository, SmartDownloadManager) live in
 * [cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder] to avoid a
 * `:core:data` ↔ `:core:downloads` cycle.
 */
interface SharedAppDependencies {
    val database: BoxLoreDatabase
    val podcastRepository: PodcastRepository
    val subscriptionRepository: SubscriptionRepository
    val userPreferencesRepository: UserPreferencesRepository
    val rssPodcastRepository: RssPodcastRepository
    val adaptiveCandidateScorer: AdaptiveCandidateScorer
    val rankingFeedbackRepository: RankingFeedbackRepository
    val adaptiveRankingRepository: AdaptiveRankingRepository
    val rankingRuntimeControls: RankingRuntimeControls
    val historyRecommendationSource: HistoryRecommendationSource
}

/**
 * Holder for the single [SharedAppDependencies] installed by the Application composition root.
 *
 * Workers and [cx.aswin.boxlore.core.data.service.BoxLorePlaybackService] call [require]
 * instead of constructing PodcastRepository / ranking / RSS graphs themselves.
 */
object SharedAppDependenciesHolder {
    @Volatile
    var instance: SharedAppDependencies? = null

    fun require(): SharedAppDependencies =
        instance
            ?: error(
                "SharedAppDependencies not installed. " +
                    "Set SharedAppDependenciesHolder.instance from Application after creating AppContainer.",
            )
}
