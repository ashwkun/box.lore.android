package cx.aswin.boxlore.feature.home.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cx.aswin.boxlore.core.domain.ports.RankingResetPort
import cx.aswin.boxlore.core.domain.ports.RssSubscriptionPort

/** Builds [SettingsViewModel] from narrow ports (production or fakes). */
object SettingsViewModelAssembler {
    fun create(
        rssSubscriptionPort: RssSubscriptionPort,
        rankingResetPort: RankingResetPort,
    ): SettingsViewModel =
        SettingsViewModel(
            rssRepository = rssSubscriptionPort,
            rankingFeedbackRepository = rankingResetPort,
        )

    fun factory(
        rssSubscriptionPort: RssSubscriptionPort,
        rankingResetPort: RankingResetPort,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    "Unknown ViewModel class: ${modelClass.name}"
                }
                return create(rssSubscriptionPort, rankingResetPort) as T
            }
        }
}
