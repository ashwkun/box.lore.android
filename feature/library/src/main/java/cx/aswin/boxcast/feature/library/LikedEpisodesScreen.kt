package cx.aswin.boxcast.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast

/**
 * Screen for displaying all liked episodes.
 * Moved from LibraryScreen.kt to support Predictive Back Navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedEpisodesScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleStyle = lerp(
        start = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        stop = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        fraction = scrollBehavior.state.collapsedFraction,
    )

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.trackLikedExit()
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
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryLikedViewed("library_hub_card")
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Liked Episodes",
                        style = titleStyle,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (uiState) {
                is LibraryUiState.Success -> {
                    val liked = (uiState as LibraryUiState.Success).likedEpisodes
                    if (liked.isEmpty()) {
                        ExpressiveSolarSystemEmptyState(
                            title = "No Liked Episodes",
                            description = "Heart episodes you love to save them here.",
                            actionText = "Find Podcasts",
                            onExploreClick = onExploreClick
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 180.dp) // Nav bar padding
                        ) {
                            items(liked) { historyItem ->
                                val episode = Episode(
                                    id = historyItem.episodeId,
                                    title = historyItem.episodeTitle ?: "Unknown Episode",
                                    description = "",
                                    imageUrl = historyItem.episodeImageUrl ?: "",
                                    audioUrl = historyItem.episodeAudioUrl ?: "",
                                    duration = ((historyItem.durationMs) / 1000).toInt(),
                                    publishedDate = 0L
                                )
                                val podcast = Podcast(
                                    id = historyItem.podcastId,
                                    title = historyItem.podcastName,
                                    artist = "",
                                    imageUrl = historyItem.podcastImageUrl ?: "",
                                    description = ""
                                )

                                ListItem(
                                    headlineContent = { Text(episode.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(podcast.title, style = MaterialTheme.typography.bodySmall) },
                                    leadingContent = {
                                        AsyncImage(
                                            model = episode.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.genericEpisodesClickedCount++
                                            onEpisodeClick(episode, podcast)
                                        }
                                )
                            }
                        }
                    }
                }
                else -> { /* Handle Loading/Error */ }
            }
        }
    }
}
