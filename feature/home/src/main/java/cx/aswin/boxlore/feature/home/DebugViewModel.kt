package cx.aswin.boxlore.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.data.PlaybackRepository
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.data.UserPreferencesRepository
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.data.database.PodcastEntity
import cx.aswin.boxlore.core.data.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.data.ranking.LearnerInspectorSnapshot
import cx.aswin.boxlore.core.data.ranking.LearningEvent
import cx.aswin.boxlore.core.data.ranking.LearningEventLog
import cx.aswin.boxlore.core.data.ranking.RankingShadowDiagnostics
import cx.aswin.boxlore.core.data.ranking.RankingShadowSnapshot
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
    private val playbackRepository: PlaybackRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPrefs: UserPreferencesRepository,
    private val adaptiveRankingRepository: AdaptiveRankingRepository,
) : AndroidViewModel(application) {

    val history: Flow<List<ListeningHistoryEntity>> = playbackRepository.getAllHistory()
    val podcasts: Flow<List<PodcastEntity>> = subscriptionRepository.getAllSubscribedPodcasts()

    private val _skipSleepWindow = MutableStateFlow(playbackRepository.isDebugSkipSleepWindowEnabled())
    val skipSleepWindow: StateFlow<Boolean> = _skipSleepWindow.asStateFlow()

    private val _learnerSnapshot = MutableStateFlow<LearnerInspectorSnapshot?>(null)
    val learnerSnapshot: StateFlow<LearnerInspectorSnapshot?> = _learnerSnapshot.asStateFlow()

    private val _learnerLoading = MutableStateFlow(false)
    val learnerLoading: StateFlow<Boolean> = _learnerLoading.asStateFlow()

    /** Live, session-only feed of signals that mutated the ranking model. */
    val learningEvents: StateFlow<List<LearningEvent>> = LearningEventLog.events

    private val debugPrefs = application.getSharedPreferences(
        LearningEventLog.PREFS_NAME,
        android.content.Context.MODE_PRIVATE,
    )

    private val _logEnabled = MutableStateFlow(LearningEventLog.enabled)
    val logEnabled: StateFlow<Boolean> = _logEnabled.asStateFlow()

    private val _shadowDiagnostics = MutableStateFlow<List<RankingShadowSnapshot>>(emptyList())
    val shadowDiagnostics: StateFlow<List<RankingShadowSnapshot>> = _shadowDiagnostics.asStateFlow()

    init {
        refreshLearnerSnapshot()
    }

    fun setLogEnabled(enabled: Boolean) {
        debugPrefs.edit().putBoolean(LearningEventLog.ENABLED_PREF_KEY, enabled).apply()
        LearningEventLog.configure(enabled)
        _logEnabled.value = enabled
    }

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
        val prefs = getApplication<Application>().getSharedPreferences(
            "boxcast_prefs",
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit()
            .remove("dismissed_curiosities")
            .remove("learn_curiosity_history")
            .apply()
    }

    fun refreshLearnerSnapshot() {
        viewModelScope.launch {
            _learnerLoading.value = true
            _learnerSnapshot.value = runCatching {
                adaptiveRankingRepository.learnerInspectorSnapshot()
            }.getOrNull()
            _shadowDiagnostics.value = runCatching {
                RankingShadowDiagnostics.snapshots()
            }.getOrDefault(emptyList())
            _learnerLoading.value = false
        }
    }
}
