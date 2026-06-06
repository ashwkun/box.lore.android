package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.theme.m3Shimmer
import cx.aswin.boxcast.feature.home.CuratedTimeBlock
import androidx.compose.runtime.LaunchedEffect
import cx.aswin.boxcast.core.data.analytics.AnalyticsHelper

@Suppress("UNUSED_PARAMETER")
@Composable
fun ForYouSection(
    recommendations: List<Episode>,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    onPlayEpisode: (Episode, Podcast) -> Unit,
    timeBlock: CuratedTimeBlock?,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (recommendations.isNotEmpty()) {
        LaunchedEffect(recommendations) {
            AnalyticsHelper.trackHomeRecommendationsImpression(
                recommendationsCount = recommendations.size,
                episodeIds = recommendations.map { it.id },
                timeBlockTitle = timeBlock?.title
            )
        }
    }

    val items = recommendations.take(9)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Curated For You Main Header ---
        ForYouHeader(onSeeAllClick = onSeeAllClick)

        if (recommendations.isEmpty()) {
            // --- Skeletal Shimmer Loader for Recommendations ---
            val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

            // Hero Skeleton
            ForYouHeroSkeleton(baseColor = baseColor, highlightColor = highlightColor)

            // 4 uniform rows of 2 skeletons each
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ForYouBentoSkeleton(baseColor = baseColor, highlightColor = highlightColor, modifier = Modifier.weight(1f))
                    ForYouBentoSkeleton(baseColor = baseColor, highlightColor = highlightColor, modifier = Modifier.weight(1f))
                }
            }
        } else {
            // --- Uniform Grid Layout ---

            // Item 1 (Hero Card - index 0)
            if (items.isNotEmpty()) {
                val ep = items[0]
                val parentPodcast = Podcast(
                    id = ep.podcastId ?: "",
                    title = ep.podcastTitle ?: "Podcast",
                    artist = "",
                    imageUrl = ep.podcastImageUrl?.takeIf { it.isNotBlank() } ?: ep.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                    description = "",
                    genre = ep.podcastGenre ?: "Podcast"
                )
                ForYouHeroCard(
                    episode = ep,
                    parentPodcast = parentPodcast,
                    onClick = {
                        AnalyticsHelper.trackHomeRecommendationCardTapped(
                            episodeId = ep.id,
                            episodeTitle = ep.title,
                            podcastId = parentPodcast.id,
                            podcastName = parentPodcast.title,
                            positionIndex = 0,
                            timeBlockTitle = timeBlock?.title
                        )
                        onEpisodeClick(ep, parentPodcast)
                    }
                )
            }

            // Remaining items in 2-column masonry layout (indices 1..8)
            val remaining = items.drop(1)
            val remainingWithIndex = remaining.mapIndexed { index, ep -> ep to (index + 1) }
            val leftColumn = remainingWithIndex.filterIndexed { index, _ -> index % 2 == 0 }
            val rightColumn = remainingWithIndex.filterIndexed { index, _ -> index % 2 == 1 }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Left column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    leftColumn.forEach { (ep, originalIndex) ->
                        val parentPodcast = Podcast(
                            id = ep.podcastId ?: "",
                            title = ep.podcastTitle ?: "Podcast",
                            artist = "",
                            imageUrl = ep.podcastImageUrl?.takeIf { it.isNotBlank() } ?: ep.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                            description = "",
                            genre = ep.podcastGenre ?: "Podcast"
                        )
                        ForYouBentoCard(
                            episode = ep,
                            onClick = {
                                AnalyticsHelper.trackHomeRecommendationCardTapped(
                                    episodeId = ep.id,
                                    episodeTitle = ep.title,
                                    podcastId = parentPodcast.id,
                                    podcastName = parentPodcast.title,
                                    positionIndex = originalIndex,
                                    timeBlockTitle = timeBlock?.title
                                )
                                onEpisodeClick(ep, parentPodcast)
                            }
                        )
                    }
                }
                // Right column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rightColumn.forEach { (ep, originalIndex) ->
                        val parentPodcast = Podcast(
                            id = ep.podcastId ?: "",
                            title = ep.podcastTitle ?: "Podcast",
                            artist = "",
                            imageUrl = ep.podcastImageUrl?.takeIf { it.isNotBlank() } ?: ep.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                            description = "",
                            genre = ep.podcastGenre ?: "Podcast"
                        )
                        ForYouBentoCard(
                            episode = ep,
                            onClick = {
                                AnalyticsHelper.trackHomeRecommendationCardTapped(
                                    episodeId = ep.id,
                                    episodeTitle = ep.title,
                                    podcastId = parentPodcast.id,
                                    podcastName = parentPodcast.title,
                                    positionIndex = originalIndex,
                                    timeBlockTitle = timeBlock?.title
                                )
                                onEpisodeClick(ep, parentPodcast)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForYouHeroCard(
    episode: Episode,
    parentPodcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .expressiveClickable(onClick = onClick)
    ) {
        // Full-bleed artwork background
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() },
            proxyWidth = 600,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient scrim — heavier at bottom for text legibility (Option A)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.3f to Color.Black.copy(alpha = 0.15f),
                            0.6f to Color.Black.copy(alpha = 0.65f),
                            1.0f to Color.Black
                        )
                    )
                )
        )

        // Premium tag for the Hero card
        Box(
            modifier = Modifier
                .padding(14.dp)
                .align(Alignment.TopStart)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "FEATURED RECOMMENDATION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }

        // Metadata anchored to bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Podcast name
            Text(
                text = episode.podcastTitle ?: "",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Episode title (larger font since it's the hero card)
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    lineHeight = 20.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Duration & additional context
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (episode.duration > 0) {
                    val minutes = episode.duration / 60
                    Text(
                        text = "${minutes} min read/listen",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                
                val genre = episode.podcastGenre ?: parentPodcast.genre
                if (!genre.isNullOrBlank()) {
                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ForYouBentoCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.expressiveClickable(onClick = onClick)
    ) {
        Column {
            // Image container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
            ) {
                OptimizedImage(
                    url = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() },
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                )

                // Duration pill (bottom right)
                if (episode.duration > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "${episode.duration / 60}m",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Text content below image
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.podcastTitle ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ForYouHeader(
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 18.dp), // Premium breathing room above content
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Curated For You",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = SectionHeaderFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Action chevron decorator
        FilledTonalIconButton(
            onClick = onSeeAllClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "See All",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ForYouHeroSkeleton(
    baseColor: Color,
    highlightColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(20.dp))
    )
}

@Composable
private fun ForYouBentoSkeleton(
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Image area skeleton matching aspect ratio 1.25f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            )
            
            // Text area skeleton matching padding and layout
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(4.dp))
                )
                // Title line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Podcast title line
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun ForYouHorizontalBentoCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp)
            .clip(RoundedCornerShape(20.dp))
            .expressiveClickable(onClick = onClick)
    ) {
        // Full-bleed artwork background
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() },
            proxyWidth = 600,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Heavy dark gradient scrim for pristine text contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.4f to Color.Black.copy(alpha = 0.3f),
                            1.0f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Duration chip — top left
        if (episode.duration > 0) {
            val minutes = episode.duration / 60
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = "${minutes} min",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        // Genre chip — top right (or video badge if video)
        val genre = episode.podcastGenre
        if (episode.enclosureType?.startsWith("video/") == true) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(
                    modifier = Modifier.padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        } else if (!genre.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.TopEnd)
            ) {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        // Episode title — bottom aligned
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = Color.White
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ForYouHorizontalBentoSkeleton(
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp)
            .clip(RoundedCornerShape(20.dp))
            .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(20.dp))
    )
}
