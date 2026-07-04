package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast

@Composable
fun CuratedEpisodeCard(
    podcast: Podcast,
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isNew = episode.publishedDate > 0L &&
            (System.currentTimeMillis() / 1000L - episode.publishedDate) < 2 * 24 * 60 * 60L

    FeedMediaCard(
        imageUrl = (episode.imageUrl ?: "").ifEmpty { podcast.imageUrl },
        title = episode.title,
        subtitle = podcast.title,
        onClick = onClick,
        modifier = modifier,
        imageBadge = {
            if (isNew) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "NEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        },
        imageOverlay = {
            if (episode.duration > 0) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = formatDuration(episode.duration),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    )
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val m = seconds / 60
    return "${m}m"
}
