package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
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
import cx.aswin.boxcast.feature.home.StablePodcastList
import cx.aswin.boxcast.feature.home.StableEpisodeList

@Composable
fun BecauseYouLikeSection(
    podcast: Podcast,
    recommendations: StableEpisodeList,
    suggestedPodcasts: StablePodcastList,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    onPlayEpisode: (Episode, Podcast) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
    onChangePodcastClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // --- Seed Podcast Card (Matching OutlinedCard Bento style) ---
        OutlinedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .expressiveClickable(onClick = { onPodcastClick(podcast) })
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
                            painter = painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.mood_heart_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
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
        if (suggestedPodcasts.list.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Column {
                BecauseYouLikeSectionHeader(title = "Similar Shows")
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(suggestedPodcasts.list, key = { it.id }) { suggestedPodcast ->
                        PodcastCard(
                            podcast = suggestedPodcast,
                            onClick = { onPodcastClick(suggestedPodcast) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }
        }

        // --- Subsection 2: Recommended Episodes ---
        if (recommendations.list.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Column {
                BecauseYouLikeSectionHeader(title = "Recommended Episodes")

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recommendations.list, key = { it.id }) { episode ->
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
                            onClick = { onEpisodeClick(episode, parentPodcast) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BecauseYouLikeSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(bottom = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
