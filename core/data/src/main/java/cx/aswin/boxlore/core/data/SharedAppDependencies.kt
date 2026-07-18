package cx.aswin.boxlore.core.data

import cx.aswin.boxlore.core.data.database.BoxLoreDatabase
import cx.aswin.boxlore.core.data.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.data.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource

/**
 * Process-wide façade of Application-scoped instances that workers and Media3 services
 * may consume without rebuilding a parallel repository graph.
 *
 * Installed once from [AppContainer] via [SharedAppDependenciesHolder] in Application.onCreate.
 * Do not construct ranking/RSS repositories ad hoc — use these instances.
 */
interface SharedAppDependencies {
    val database: BoxLoreDatabase
    val podcastRepository: PodcastRepository
    val downloadRepository: DownloadRepository
    val subscriptionRepository: SubscriptionRepository
    val userPreferencesRepository: UserPreferencesRepository
    val rssPodcastRepository: RssPodcastRepository
    val adaptiveCandidateScorer: AdaptiveCandidateScorer
    val rankingFeedbackRepository: RankingFeedbackRepository
    val adaptiveRankingRepository: AdaptiveRankingRepository
    val historyRecommendationSource: HistoryRecommendationSource
    val smartDownloadManager: SmartDownloadManager
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
