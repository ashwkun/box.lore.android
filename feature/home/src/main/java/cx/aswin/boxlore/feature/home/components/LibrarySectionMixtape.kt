package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast

internal fun formatRelativeDate(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSeconds
    return when {
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        diff < 2592000 -> "${diff / 604800}w ago"
        diff < 31536000 -> "${diff / 2592000}mo ago"
        else -> "${diff / 31536000}y ago"
    }
}

@Composable
internal fun MixtapeSelectorCover(
    isSelected: Boolean,
    isAnyPodcastSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(60.dp),
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 0.95f, label = "scale")
    val alpha by animateFloatAsState(
        targetValue =
            if (isSelected) {
                1f
            } else if (isAnyPodcastSelected) {
                0.6f
            } else {
                1f
            },
        label = "alpha",
    )
    val cornerRadius by animateDpAsState(targetValue = if (isSelected) 16.dp else 12.dp, label = "cornerRadius")
    val borderStrokeWidth by animateDpAsState(targetValue = if (isSelected) 3.dp else 0.dp, label = "borderStrokeWidth")

    Box(
        modifier =
            modifier
                .scale(scale),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .expressiveClickable(
                        shape = RoundedCornerShape(cornerRadius),
                        onClick = onClick,
                    ).clip(RoundedCornerShape(cornerRadius)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .alpha(alpha),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "For You",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .border(borderStrokeWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius)),
                )
            }
        }
    }
}

@Composable
internal fun MixtapeEpisodeCard(
    episode: Episode,
    podcast: Podcast,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    overrideStatus: EpisodeStatus? = null,
    overrideProgress: Float? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
) {
    val status = overrideStatus ?: if (podcast.latestEpisode?.id == episode.id) podcast.episodeStatus else EpisodeStatus.UNPLAYED
    val progress = overrideProgress ?: if (podcast.latestEpisode?.id == episode.id) (podcast.resumeProgress ?: 0f) else 0f
    val isInProgress = status == EpisodeStatus.IN_PROGRESS
    val isCompleted = status == EpisodeStatus.COMPLETED
    val isCurrentPlaying = currentPlayingEpisodeId == episode.id && isPlaying

    Card(
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier =
            modifier
                .width(280.dp)
                .height(96.dp)
                .expressiveClickable(shape = RoundedCornerShape(16.dp), onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Left: Cover art with download & played badges
                Box(
                    modifier =
                        Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(10.dp)),
                ) {
                    OptimizedImage(
                        url = (
                            episode.imageUrl?.takeIf { it.isNotEmpty() } ?: podcast.imageUrl.takeIf { it.isNotEmpty() }
                                ?: podcast.fallbackImageUrl
                        ),
                        proxyWidth = 120,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (isCompleted) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(12.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Played",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(8.dp),
                            )
                        }
                    }

                    if (isDownloaded) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(2.dp)
                                    .size(12.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                    .border(0.5.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DownloadDone,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(8.dp),
                            )
                        }
                    }
                }

                // Center: Info Column (Titles + Metadata)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = podcast.title,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = episode.title,
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                lineHeight = 15.sp,
                            ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isNew =
                            status == EpisodeStatus.UNPLAYED &&
                                podcast.subscribedAt > 0L &&
                                episode.publishedDate > (podcast.subscribedAt / 1000L - 7 * 24 * 3600L)
                        if (isNew) {
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 4.dp, vertical = 0.5.dp),
                            ) {
                                Text(
                                    text = "NEW",
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 7.sp,
                                            letterSpacing = 0.5.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        val relativeDate = formatRelativeDate(episode.publishedDate)
                        if (relativeDate.isNotEmpty()) {
                            val prefix = if (isNew) "• " else ""
                            Text(
                                text = "$prefix$relativeDate",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                        }

                        if (episode.duration > 0) {
                            val h = episode.duration / 3600
                            val m = (episode.duration % 3600) / 60
                            val timeText =
                                if (isInProgress && progress > 0f) {
                                    val remaining = ((1f - progress) * episode.duration).toInt()
                                    val rm = (remaining % 3600) / 60
                                    "${rm}m left"
                                } else {
                                    if (h > 0) "${h}h ${m}m" else "${m}m"
                                }
                            Text(
                                text = timeText,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color =
                                    if (isInProgress) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                            )
                        }
                    }
                }

                // Right: Tonal circle play button
                Surface(
                    onClick = onPlay,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector =
                                if (isCurrentPlaying) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                            contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Absolute Bottom: Progress Bar spanning full card width
            if (isInProgress && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}
