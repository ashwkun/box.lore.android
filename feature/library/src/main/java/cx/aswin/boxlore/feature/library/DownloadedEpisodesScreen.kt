package cx.aswin.boxlore.feature.library

import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.downloads.DownloadedEpisodesScreen as DownloadedEpisodesScreenImpl
import cx.aswin.boxlore.feature.library.downloads.DownloadedShowEpisodesScreen as DownloadedShowEpisodesScreenImpl
import cx.aswin.boxlore.feature.library.downloads.toEpisode as downloadedEntityToEpisode
import cx.aswin.boxlore.feature.library.downloads.toPodcast as downloadedEntityToPodcast

fun DownloadedEpisodeEntity.toEpisode() = downloadedEntityToEpisode()

fun DownloadedEpisodeEntity.toPodcast() = downloadedEntityToPodcast()

@Composable
fun DownloadedEpisodesScreen(
    viewModel: LibraryViewModel,
    userPrefs: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    isOffline: Boolean = false,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
    onPodcastShowClick: (String, String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    isSyncing: Boolean = false,
    isPlayerActive: Boolean = false
) {
    DownloadedEpisodesScreenImpl(
        viewModel = viewModel,
        userPrefs = userPrefs,
        isOffline = isOffline,
        onBack = onBack,
        onExploreClick = onExploreClick,
        onPodcastShowClick = onPodcastShowClick,
        onSettingsClick = onSettingsClick,
        onSyncNow = onSyncNow,
        isSyncing = isSyncing,
        isPlayerActive = isPlayerActive,
    )
}

@Composable
fun DownloadedShowEpisodesScreen(
    viewModel: LibraryViewModel,
    podcastId: String,
    podcastTitle: String,
    onBack: () -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    isPlayerActive: Boolean = false
) {
    DownloadedShowEpisodesScreenImpl(
        viewModel = viewModel,
        podcastId = podcastId,
        podcastTitle = podcastTitle,
        onBack = onBack,
        onEpisodeClick = onEpisodeClick,
        isPlayerActive = isPlayerActive,
    )
}
