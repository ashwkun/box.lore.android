package cx.aswin.boxcast.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast

@Composable
fun DownloadedEpisodesScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.trackDownloadsExit()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.onScreenResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryDownloadsViewed("library_hub_card")
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (uiState) {
                is LibraryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error loading downloads")
                    }
                }
                is LibraryUiState.Success -> {
                    val downloads = (uiState as LibraryUiState.Success).downloadedEpisodes
                    if (downloads.isEmpty()) {
                        ExpressiveSolarSystemEmptyState(
                            title = "No Downloads Found",
                            description = "Save episodes for offline listening.",
                            actionText = "Find Podcasts",
                            onExploreClick = onExploreClick
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 180.dp)
                        ) {
                            items(items = downloads, key = { it.episodeId }) { download ->
                                val episode = Episode(
                                    id = download.episodeId,
                                    title = download.episodeTitle,
                                    description = download.episodeDescription ?: "",
                                    audioUrl = download.localFilePath, // Use local path
                                    imageUrl = download.episodeImageUrl,
                                    podcastImageUrl = download.podcastImageUrl,
                                    duration = (download.durationMs / 1000).toInt(),
                                    publishedDate = download.publishedDate
                                )
                                val podcast = Podcast(
                                    id = download.podcastId,
                                    title = download.podcastName,
                                    artist = "",
                                    imageUrl = download.podcastImageUrl ?: "",
                                    description = "",
                                    genre = ""
                                )

                                ListItem(
                                    headlineContent = {
                                        Text(
                                            download.episodeTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            download.podcastName,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingContent = {
                                        AsyncImage(
                                            model = download.episodeImageUrl ?: download.podcastImageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .width(56.dp)
                                                .height(56.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    trailingContent = {
                                        when (download.status) {
                                            cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity.STATUS_DOWNLOADING, 
                                            cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity.STATUS_QUEUED -> {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.width(24.dp).height(24.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity.STATUS_FAILED -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Failed",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "Downloaded",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        viewModel.genericEpisodesClickedCount++
                                        onEpisodeClick(episode, podcast)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
