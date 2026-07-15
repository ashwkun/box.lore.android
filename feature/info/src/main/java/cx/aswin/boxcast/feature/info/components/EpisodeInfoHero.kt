package cx.aswin.boxcast.feature.info.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode

@Composable
internal fun EpisodeArtworkBackdrop(
    imageUrl: String?,
    scrollOffset: Float,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = -scrollOffset * 0.5f
                alpha = 1f - collapseFraction
            },
    ) {
        OptimizedImage(
            url = imageUrl,
            proxyWidth = 200,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f)
                .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun EpisodeInfoHero(
    episode: Episode,
    podcastTitle: String,
    accentColor: Color,
    collapseFraction: Float,
    onPodcastClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - collapseFraction * 0.18f
                translationY = -collapseFraction * 20.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(184.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp,
                ) {
                    OptimizedImage(
                        url = episode.imageUrl?.ifBlank { episode.podcastImageUrl },
                        proxyWidth = 640,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (episode.enclosureType?.startsWith("video/") == true) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 2.dp),
                        shape = ExpressiveShapes.Pill,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Video", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text(
            text = episode.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .expressiveClickable(onClick = onPodcastClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = podcastTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open podcast",
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        EpisodeMetadataChipsRow(episode)
    }
}

private data class EpisodeMetadataChip(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun EpisodeMetadataChipsRow(episode: Episode) {
    val metadata = buildList {
        if (episode.enclosureType?.startsWith("video/") == true) {
            add(EpisodeMetadataChip("Video", Icons.Rounded.Videocam))
        }
        add(EpisodeMetadataChip(formatDuration(episode.duration), Icons.Rounded.Schedule))
        formatRelativeDate(episode.publishedDate)?.let {
            add(EpisodeMetadataChip(it, Icons.Rounded.CalendarToday))
        }
        formatSeasonAndEpisode(episode)?.let {
            add(EpisodeMetadataChip(it, Icons.Rounded.Tag))
        }
        episode.episodeType
            ?.takeUnless { it.equals("full", ignoreCase = true) }
            ?.let {
                add(
                    EpisodeMetadataChip(
                        it.replaceFirstChar(Char::uppercase),
                        Icons.AutoMirrored.Rounded.Label,
                    ),
                )
            }
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(metadata) { item ->
            Surface(
                shape = ExpressiveShapes.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatRelativeDate(timestampSeconds: Long): String? {
    if (timestampSeconds <= 0L) return null
    val difference = ((System.currentTimeMillis() / 1000L) - timestampSeconds).coerceAtLeast(0L)
    return when {
        difference < 3_600L -> "${difference / 60L}m ago"
        difference < 86_400L -> "${difference / 3_600L}h ago"
        difference < 604_800L -> "${difference / 86_400L}d ago"
        difference < 2_592_000L -> "${difference / 604_800L}w ago"
        difference < 31_536_000L -> "${difference / 2_592_000L}mo ago"
        else -> "${difference / 31_536_000L}y ago"
    }
}

private fun formatSeasonAndEpisode(episode: Episode): String? = buildString {
    episode.seasonNumber?.takeIf { it > 0 }?.let { append("S$it") }
    episode.episodeNumber?.takeIf { it > 0 }?.let {
        if (isNotEmpty()) append(" ")
        append("E$it")
    }
}.ifBlank { null }
