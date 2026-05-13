package cx.aswin.boxcast.feature.home.components

import cx.aswin.boxcast.core.designsystem.components.optimizedImageUrl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.EpisodeStatus
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.designsystem.components.AnimatedShapesFallback
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import androidx.compose.foundation.layout.fillMaxHeight

/**
 * Merged "Your Shows" Section (formerly LibrarySection + LatestSection)
 *
 * Layout:
 * 1. Header: Icon + "Your Shows" + Arrow (styled like OnTheRise)
 * 2. Part A: Small podcast cover grid (max 2 rows, 4 per row) + "View Library" button
 * 3. Part B: Scrollable row of new episode cards (linked to EpisodeInfo)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YourShowsSection(
    subscribedPodcasts: List<Podcast>,
    suggestedPodcasts: List<Podcast> = emptyList(),
    latestEpisodes: List<Podcast>, // Podcasts with latestEpisode populated
    unplayedEpisodeCount: Int = 0,
    onPodcastClick: (Podcast) -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit, // Navigate to EpisodeInfo
    onViewLibrary: () -> Unit,
    onViewAllLatest: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (subscribedPodcasts.isEmpty() && latestEpisodes.isEmpty()) return

    Column(modifier = modifier) {
        // --- Header (Styled like Master Header) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Bookmarks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Your Shows",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = SectionHeaderFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    letterSpacing = (-0.5).sp
                )
            }

            FilledTonalIconButton(
                onClick = onViewLibrary,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "View Library",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- Part A: Subscribed Shows Grid ---
        val combinedPodcasts = subscribedPodcasts + suggestedPodcasts
        if (combinedPodcasts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 0.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val totalColumns = 5

                if (subscribedPodcasts.size > 10) {
                    val row1Items = subscribedPodcasts.take(totalColumns)
                    val row2Items = subscribedPodcasts.drop(totalColumns).take(2)
                    val remainingCount = subscribedPodcasts.size - 7

                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row1Items.forEach { podcast ->
                            Box(modifier = Modifier.weight(1f)) {
                               ResponsivePodcastCover(podcast = podcast, onClick = { onPodcastClick(podcast) })
                            }
                        }
                    }

                    // Row 2 (2 items + More Button)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row2Items.forEach { podcast ->
                            Box(modifier = Modifier.weight(1f)) {
                                ResponsivePodcastCover(podcast = podcast, onClick = { onPodcastClick(podcast) })
                            }
                        }
                        
                        Box(modifier = Modifier.weight(3f).fillMaxHeight()) {
                            androidx.compose.material3.FilledTonalButton(
                                onClick = onViewLibrary,
                                modifier = Modifier.fillMaxSize(),
                                shape = MaterialTheme.shapes.small,
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                // Let's use a simpler approach for the button content.
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (remainingCount > 0) "+$remainingCount" else "More",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // <= 10 subscriptions: fill up to 2 rows of 5, no "More" button needed
                    val chunked = combinedPodcasts.chunked(totalColumns)
                    chunked.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { podcast ->
                                val isSuggested = suggestedPodcasts.any { it.id == podcast.id }
                                Box(modifier = Modifier.weight(1f)) {
                                    ResponsivePodcastCover(podcast = podcast, isSuggested = isSuggested, onClick = { onPodcastClick(podcast) })
                                }
                            }
                            // Fill remaining empty slots in the row
                            repeat(totalColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Part B: Latest Episodes Rail ---
        val combinedLatestEpisodes = latestEpisodes + suggestedPodcasts.filter { it.latestEpisode != null }
        if (combinedLatestEpisodes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Latest Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (unplayedEpisodeCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text("$unplayedEpisodeCount")
                        }
                    }
                }

                if (onViewAllLatest != null) {
                    FilledTonalIconButton(
                        onClick = onViewAllLatest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "View all latest episodes",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(combinedLatestEpisodes, key = { "${it.id}_${it.latestEpisode?.id}" }) { podcast ->
                    val episode = podcast.latestEpisode
                    if (episode != null) {
                        val isSuggested = suggestedPodcasts.any { it.id == podcast.id }
                        NewEpisodeCard(
                            episode = episode,
                            podcast = podcast,
                            isSuggested = isSuggested,
                            onClick = { onEpisodeClick(episode, podcast) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Responsive podcast cover for weighted grids.
 */
@Composable
private fun ResponsivePodcastCover(
    podcast: Podcast,
    isSuggested: Boolean = false,
    onClick: () -> Unit
) {
    val grayscaleFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .expressiveClickable(shape = MaterialTheme.shapes.small, onClick = onClick)
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            proxyWidth = 250, // Grid items ~60dp * 3x + margin
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            colorFilter = if (isSuggested) grayscaleFilter else null,
            modifier = Modifier.fillMaxSize()
        )
        if (isSuggested) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Suggested",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * Small podcast cover (Fixed 56dp) - Deprecated for new grid
 */
@Composable
private fun SmallPodcastCover(
    podcast: Podcast,
    onClick: () -> Unit
) {
    OptimizedImage(
        url = podcast.imageUrl,
        proxyWidth = 150, // 56dp * 3x density
        contentDescription = podcast.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.small)
            .expressiveClickable(onClick = onClick)
    )
}

/**
 * New Episode Card for the scrollable rail.
 * Smart visual states: Unplayed (dot badge), In Progress (progress strip), Completed (checkmark + dimmed).
 * Styles reuse the same M3 Expressive patterns from PodcastInfoScreen.
 */
@Composable
private fun NewEpisodeCard(
    episode: Episode,
    podcast: Podcast,
    isSuggested: Boolean = false,
    onClick: () -> Unit
) {
    val status = podcast.episodeStatus
    val progress = podcast.resumeProgress ?: 0f
    val isCompleted = status == EpisodeStatus.COMPLETED
    val isInProgress = status == EpisodeStatus.IN_PROGRESS

    // Completed cards are slightly dimmed to de-emphasize
    val cardAlpha = if (isCompleted) 0.72f else 1f
    val grayscaleFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    androidx.compose.material3.OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer { alpha = cardAlpha }
            .expressiveClickable(onClick = onClick)
    ) {
        Column {
            // Image area with overlays
            Box {
                OptimizedImage(
                    url = episode.imageUrl?.takeIf { it.isNotEmpty() } ?: podcast.imageUrl.takeIf { it.isNotEmpty() },
                    proxyWidth = 300, // 140dp cards
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (isSuggested) grayscaleFilter else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                )

                // Completed: Checkmark badge at top-right (same style as PodcastInfoScreen)
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Played",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Unplayed: Subtle dot badge at top-right
                if (status == EpisodeStatus.UNPLAYED) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }

                // In Progress: Thin progress strip at bottom of image (same pattern as ExpressivePlayButton)
                if (isInProgress && progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            drawStopIndicator = {}
                        )
                    }
                }

                // Time left Pill overlay
                if (isInProgress && episode.duration > 0) {
                    val remainingSeconds = ((1f - progress) * episode.duration).toInt()
                    val h = remainingSeconds / 3600
                    val m = (remainingSeconds % 3600) / 60
                    val timeLeft = if (h > 0) "${h}h ${m}m" else "${m}m"
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$timeLeft left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (isSuggested) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Suggested",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.padding(10.dp)) {
                // Episode title
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Subtitle: podcast name
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Keep backward compatibility - deprecated, use YourShowsSection
@Deprecated("Use YourShowsSection instead", ReplaceWith("YourShowsSection"))
@Composable
fun LibrarySection(
    podcasts: List<Podcast>,
    onPodcastClick: (Podcast) -> Unit,
    modifier: Modifier = Modifier
) {
    YourShowsSection(
        subscribedPodcasts = podcasts,
        latestEpisodes = emptyList(),
        onPodcastClick = onPodcastClick,
        onEpisodeClick = { _, _ -> },
        onViewLibrary = {},
        modifier = modifier
    )
}
