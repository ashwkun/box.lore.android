package cx.aswin.boxcast.feature.home.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.RssPodcastRepository
import cx.aswin.boxcast.core.data.RssSubscriptionResult
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** RSS "add feed" and Podcast Index match-confirmation state owned by [SettingsViewModel]. */
data class SettingsRssUiState(
    val showAddRssDialog: Boolean = false,
    val rssUrl: String = "",
    val rssError: String? = null,
    val isAddingRss: Boolean = false,
    val pendingRssMatch: RssSubscriptionResult? = null,
    val isLinkingRssMatch: Boolean = false,
)

/** One-off UI events (e.g. toasts) that [SettingsScreen] surfaces on behalf of the ViewModel. */
sealed interface SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent
}

/**
 * Owns the RSS add-feed and Podcast Index match-confirmation flows for [SettingsScreen],
 * running the subscription work on [viewModelScope] instead of a composable-scoped
 * [kotlinx.coroutines.CoroutineScope].
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val rssRepository by lazy { RssPodcastRepository.getInstance(getApplication()) }

    private val _uiState = MutableStateFlow(SettingsRssUiState())
    val uiState: StateFlow<SettingsRssUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = _events

    fun openAddRssDialog() {
        _uiState.value = _uiState.value.copy(showAddRssDialog = true)
    }

    fun dismissAddRssDialog() {
        val state = _uiState.value
        if (state.isAddingRss) return
        _uiState.value = state.copy(showAddRssDialog = false, rssError = null)
    }

    fun onRssUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(rssUrl = url, rssError = null)
    }

    fun addSubscription() {
        val url = _uiState.value.rssUrl
        _uiState.value = _uiState.value.copy(isAddingRss = true, rssError = null)
        viewModelScope.launch {
            runCatching {
                rssRepository.addSubscription(url)
            }.onSuccess { subscription ->
                if (subscription.potentialPodcastIndexMatch != null) {
                    _uiState.value = _uiState.value.copy(
                        showAddRssDialog = false,
                        rssUrl = "",
                        pendingRssMatch = subscription,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(showAddRssDialog = false, rssUrl = "")
                    _events.emit(SettingsEvent.ShowToast(subscriptionAddedMessage(subscription)))
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(rssError = error.toRssErrorMessage())
            }
            _uiState.value = _uiState.value.copy(isAddingRss = false)
        }
    }

    fun confirmPodcastIndexLink() {
        val subscription = _uiState.value.pendingRssMatch ?: return
        val podcastIndexMatch = subscription.potentialPodcastIndexMatch ?: return
        _uiState.value = _uiState.value.copy(isLinkingRssMatch = true)
        viewModelScope.launch {
            runCatching {
                rssRepository.confirmPodcastIndexLink(
                    rssPodcastId = subscription.podcast.id,
                    podcastIndexId = podcastIndexMatch.id,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(pendingRssMatch = null)
                _events.emit(
                    SettingsEvent.ShowToast(
                        "Using the RSS source for ${subscription.podcast.title}.",
                    ),
                )
            }.onFailure { error ->
                _events.emit(SettingsEvent.ShowToast(error.toRssErrorMessage()))
            }
            _uiState.value = _uiState.value.copy(isLinkingRssMatch = false)
        }
    }

    fun keepRssMatchSeparate() {
        if (_uiState.value.pendingRssMatch == null) return
        _uiState.value = _uiState.value.copy(pendingRssMatch = null)
        _events.tryEmit(SettingsEvent.ShowToast("Kept both subscriptions separate."))
    }
}

private fun subscriptionAddedMessage(subscription: RssSubscriptionResult): String = when {
    subscription.linkedPodcastIndexId != null ->
        "Switched ${subscription.podcast.title} to its RSS source."
    subscription.automaticUpdateChecksSupported ->
        "Added ${subscription.podcast.title} (${subscription.episodeCount} episodes)."
    else ->
        "Added ${subscription.podcast.title}. To check for new episodes, open the podcast and refresh."
}

private fun Throwable.toRssErrorMessage(): String = when (this) {
    is IllegalArgumentException ->
        "Check that this is a valid HTTPS podcast RSS feed."
    is IOException ->
        "The RSS feed could not be downloaded. Check your connection and try again."
    else ->
        "We couldn't add this RSS feed."
}
