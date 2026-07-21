package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

@Composable
fun CuratedEpisodeCard(
    podcast: Podcast,
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFeedback: ((RecommendationFeedbackAction) -> Unit)? = null,
) {
    val isNew =
        episode.publishedDate > 0L &&
            (System.currentTimeMillis() / 1000L - episode.publishedDate) < 2 * 24 * 60 * 60L
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FeedMediaCard(
            imageUrl = (episode.imageUrl ?: "").ifEmpty { podcast.imageUrl },
            title = episode.title,
            subtitle = podcast.title,
            onClick = onClick,
            onLongClick =
                if (onFeedback != null) {
                    { menuExpanded = true }
                } else {
                    null
                },
            imageBadge = {
                if (isNew) {
                    Box(
                        modifier =
                            Modifier
                                .padding(6.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.primary)
                                .align(Alignment.TopStart),
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 8.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            },
            imageOverlay = {
                if (episode.duration > 0) {
                    Box(
                        modifier =
                            Modifier
                                .padding(6.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .align(Alignment.BottomEnd),
                    ) {
                        Text(
                            text = formatDuration(episode.duration),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            },
        )
        if (onFeedback != null) {
            RecommendationFeedbackMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onMoreLikeThis = { onFeedback(RecommendationFeedbackAction.MORE_LIKE_THIS) },
                onNotForMe = { onFeedback(RecommendationFeedbackAction.NOT_FOR_ME) },
                onHideShow = { onFeedback(RecommendationFeedbackAction.HIDE_SHOW) },
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val minutes = seconds / 60
    return "${minutes}m"
}
