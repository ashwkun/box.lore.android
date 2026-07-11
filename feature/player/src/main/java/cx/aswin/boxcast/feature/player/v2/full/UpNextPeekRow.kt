package cx.aswin.boxcast.feature.player.v2.full

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry
import cx.aswin.boxcast.feature.player.v2.chrome.playerSheetShape
import cx.aswin.boxcast.feature.player.v2.logic.EpisodeCarouselLogic
import cx.aswin.boxcast.feature.player.v2.logic.UpNextLogic

@Composable
fun UpNextPeekRow(
    queue: List<Episode>,
    currentEpisodeId: String?,
    podcasts: Map<String, Podcast> = emptyMap(),
    colorScheme: ColorScheme,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = 2,
) {
    val upNext = UpNextLogic.upNextEpisodes(queue, currentEpisodeId, maxItems)

    if (upNext.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenQueue)
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open queue",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.padding(top = 6.dp))

        upNext.forEachIndexed { index, episode ->
            UpNextPeekItem(
                episode = episode,
                podcasts = podcasts,
                colorScheme = colorScheme,
                modifier = Modifier.fillMaxWidth(),
            )
            if (index < upNext.lastIndex) {
                Spacer(modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun UpNextPeekItem(
    episode: Episode,
    podcasts: Map<String, Podcast>,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    val rowShape = playerSheetShape(
        PlayerChromeGeometry.QueueRowCorner,
        PlayerChromeGeometry.QueueRowCorner,
        PlayerChromeGeometry.QueueRowCorner,
        PlayerChromeGeometry.QueueRowCorner,
    )
    val imageUrl = EpisodeCarouselLogic.resolveEpisodeImageUrl(episode, podcasts)

    Surface(
        modifier = modifier.clip(rowShape),
        shape = rowShape,
        color = colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OptimizedImage(
                url = imageUrl,
                proxyWidth = 96,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(playerSheetShape(8.dp, 8.dp, 8.dp, 8.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title.replace("+", " "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.podcastTitle?.let { podcastTitle ->
                    Text(
                        text = podcastTitle.replace("+", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
