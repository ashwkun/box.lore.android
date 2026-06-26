package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage

@Composable
fun BecauseYouLikeSection(
    podcast: Podcast,
    recommendations: List<Episode>,
    suggestedPodcasts: List<Podcast>,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    onPlayEpisode: (Episode, Podcast) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
    onChangePodcastClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Seed Podcast Card (Matching OutlinedCard Bento style) ---
        OutlinedCard(
            onClick = { onPodcastClick(podcast) },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 120,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "BECAUSE YOU LIKE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onChangePodcastClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Change seed podcast",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // --- Subsection 1: Suggested Shows (OutlinedCard Grid Matching CuratedEpisodeCard) ---
        if (suggestedPodcasts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SIMILAR SHOWS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    letterSpacing = 0.5.sp
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(suggestedPodcasts, key = { it.id }) { suggestedPodcast ->
                        SuggestedPodcastCard(
                            podcast = suggestedPodcast,
                            onClick = { onPodcastClick(suggestedPodcast) }
                        )
                    }
                }
            }
        }

        // --- Subsection 2: Recommended Episodes ---
        if (recommendations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RECOMMENDED EPISODES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    letterSpacing = 0.5.sp
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recommendations, key = { it.id }) { episode ->
                        val parentPodcast = Podcast(
                            id = episode.podcastId ?: "",
                            title = episode.podcastTitle ?: "Podcast",
                            artist = "",
                            imageUrl = episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: episode.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                            description = "",
                            genre = episode.podcastGenre ?: "Podcast"
                        )
                        CuratedEpisodeCard(
                            podcast = parentPodcast,
                            episode = episode,
                            onClick = { onEpisodeClick(episode, parentPodcast) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedPodcastCard(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(140.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                )
            }
            
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = podcast.title.replace("+", " "),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = podcast.artist.replace("+", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
