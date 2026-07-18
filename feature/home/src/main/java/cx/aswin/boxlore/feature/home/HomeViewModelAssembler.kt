package cx.aswin.boxlore.feature.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cx.aswin.boxlore.core.data.DownloadRepository
import cx.aswin.boxlore.core.data.EngagementPromptCoordinator
import cx.aswin.boxlore.core.data.PlaybackRepository
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.RssPodcastRepository
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.data.UserPreferencesRepository
import cx.aswin.boxlore.core.data.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.data.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.domain.ports.AlwaysOnlineConnectivity
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort

/** Shared dependencies for [HomeViewModel] construction (keeps assembler APIs ≤7 params). */
data class HomeViewModelDeps(
    val podcastRepository: PodcastRepository,
    val playbackRepository: PlaybackRepository,
    val engagementCoordinator: EngagementPromptCoordinator,
    val subscriptionRepository: SubscriptionRepository,
    val downloadRepository: DownloadRepository,
    val rssRepository: RssPodcastRepository,
    val adaptiveRankingRepository: AdaptiveRankingRepository,
    val adaptiveScorer: AdaptiveCandidateScorer,
    val rankingFeedback: RankingFeedbackRepository,
    val localCatalog: LocalCatalogPort,
    val userPreferencesRepository: UserPreferencesRepository,
    val connectivityStatus: ConnectivityStatusPort = AlwaysOnlineConnectivity,
)

/** Builds [HomeViewModel] from shared container deps (production or test doubles). */
object HomeViewModelAssembler {
    fun create(
        application: Application,
        deps: HomeViewModelDeps,
    ): HomeViewModel =
        HomeViewModel(
            application = application,
            podcastRepository = deps.podcastRepository,
            playbackRepository = deps.playbackRepository,
            engagementCoordinator = deps.engagementCoordinator,
            subscriptionRepository = deps.subscriptionRepository,
            downloadRepository = deps.downloadRepository,
            rssRepository = deps.rssRepository,
            adaptiveRankingRepository = deps.adaptiveRankingRepository,
            adaptiveScorer = deps.adaptiveScorer,
            rankingFeedback = deps.rankingFeedback,
            localCatalog = deps.localCatalog,
            userPreferencesRepository = deps.userPreferencesRepository,
            connectivityStatus = deps.connectivityStatus,
        )

    fun factory(
        application: Application,
        deps: HomeViewModelDeps,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    "Unknown ViewModel class: ${modelClass.name}"
                }
                return create(application = application, deps = deps) as T
            }
        }
}
