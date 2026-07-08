package cx.aswin.boxcast.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import cx.aswin.boxcast.core.data.database.BoxLoreDatabase
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.data.database.PodcastEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lightweight ViewModel backing the full-screen debug tools. Deliberately independent from
 * [HomeViewModel] so opening it never triggers the home feed's network/recommendation loads.
 */
class DebugViewModel(
    application: Application,
    private val playbackRepository: PlaybackRepository
) : AndroidViewModel(application) {

    private val database = BoxLoreDatabase.getDatabase(application)
    private val subscriptionRepository = SubscriptionRepository(database.podcastDao())
    private val userPrefs = UserPreferencesRepository(application)

    val history: Flow<List<ListeningHistoryEntity>> = playbackRepository.getAllHistory()
    val podcasts: Flow<List<PodcastEntity>> = subscriptionRepository.getAllSubscribedPodcasts()

    private val _skipSleepWindow = MutableStateFlow(playbackRepository.isDebugSkipSleepWindowEnabled())
    val skipSleepWindow: StateFlow<Boolean> = _skipSleepWindow.asStateFlow()

    fun setSkipSleepWindow(enabled: Boolean) {
        playbackRepository.setDebugSkipSleepWindow(enabled)
        _skipSleepWindow.value = enabled
    }

    fun forceSleepPromptNow() {
        playbackRepository.forceShowSleepPromptForTesting()
    }

    fun clearSleepTimer() {
        playbackRepository.setSleepTimer(0)
    }

    fun resetSleepWindowGuard() {
        playbackRepository.resetSleepNudgeForTesting()
    }

    fun deleteHistoryItem(episodeId: String) {
        viewModelScope.launch {
            playbackRepository.deleteSession(episodeId)
        }
    }

    fun resetFeatureFlag() {
        viewModelScope.launch {
            userPrefs.dismissFeatureAnnouncement("")
        }
    }

    fun clearDismissedCuriosities() {
        val prefs = getApplication<Application>().getSharedPreferences("boxcast_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .remove("dismissed_curiosities")
            .remove("learn_curiosity_history")
            .apply()
    }
}
