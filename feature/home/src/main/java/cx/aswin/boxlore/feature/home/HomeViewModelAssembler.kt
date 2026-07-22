package cx.aswin.boxlore.feature.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.catalog.EngagementPromptCoordinator
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort

/** Shared dependencies for [HomeViewModel] construction (keeps assembler APIs ≤7 params). */
data class HomeViewModelDeps(
    val podcastRepository: PodcastRepository,
    val playbackRepository: PlaybackRepository,
    val engagementCoordinator: EngagementPromptCoordinator,
    val subscriptionRepository: SubscriptionRepository,
    val downloadRepository: DownloadRepository,
    val rssRepository: RssPodcastRepository,
    val adaptiveScorer: AdaptiveCandidateScorer,
    val localCatalog: LocalCatalogPort,
    val userPreferencesRepository: UserPreferencesRepository,
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
            adaptiveScorer = deps.adaptiveScorer,
            localCatalog = deps.localCatalog,
            userPreferencesRepository = deps.userPreferencesRepository,
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
