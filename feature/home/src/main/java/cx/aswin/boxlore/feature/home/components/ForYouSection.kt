package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.StableEpisodeList

/**
 * Emits the "For You" section directly into a [LazyStaggeredGridScope] instead of composing
 * the whole section as one heavy full-line item. The hero card stays full-line, while the
 * remaining masonry cards become individual 1-span items so only the cards actually scrolling
 * into view are composed each frame (removes the atomic ~9-card compose spike).
 */
fun LazyStaggeredGridScope.forYouItems(
    recommendations: StableEpisodeList,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    discoveryContextTitle: String,
    showTasteHeader: Boolean = true,
    isFallback: Boolean = true,
) {
    val items = recommendations.list.take(9)

    if (showTasteHeader) {
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_header", contentType = "for_you_header") {
            HomeChildSectionHeader(
                title = if (isFallback) "Popular in your Region" else "Based on Your Taste",
                subtitle =
                    if (isFallback) {
                        "Popular picks from listeners near you"
                    } else {
                        "Picked from your listening patterns"
                    },
                icon = Icons.Rounded.AutoAwesome,
            )
        }
    }

    if (items.isEmpty()) {
        // Skeleton: hero + a handful of grid skeletons, each an individual staggered item.
        item(span = StaggeredGridItemSpan.FullLine, key = "for_you_hero_skeleton", contentType = "for_you_hero_skeleton") {
            val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            ForYouHeroSkeleton(baseColor = baseColor, highlightColor = highlightColor)
        }
        items(8, key = { "for_you_skel_$it" }, contentType = { "for_you_skel" }) {
            GridSkeletonItem()
        }
        return
    }

    // Hero card (index 0) — full width. Also hosts the impression analytics effect.
    item(span = StaggeredGridItemSpan.FullLine, key = "for_you_hero", contentType = "for_you_hero") {
        LaunchedEffect(recommendations.list, discoveryContextTitle) {
            AnalyticsHelper.trackHomeRecommendationsImpression(
                recommendationsCount = recommendations.list.size,
                episodeIds = recommendations.list.map { it.id },
                timeBlockTitle = discoveryContextTitle,
            )
        }
        val ep = items[0]
        val parentPodcast =
            Podcast(
                id = ep.podcastId ?: "",
                title = ep.podcastTitle ?: "Podcast",
                artist = "",
                imageUrl = ep.podcastImageUrl?.takeIf { it.isNotBlank() } ?: ep.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                description = "",
                genre = ep.podcastGenre ?: "Podcast",
            )
        ForYouHeroCard(
            episode = ep,
            parentPodcast = parentPodcast,
            isFallback = isFallback,
            onClick = {
                AnalyticsHelper.trackHomeRecommendationCardTapped(
                    episodeId = ep.id,
                    episodeTitle = ep.title,
                    podcastId = parentPodcast.id,
                    podcastName = parentPodcast.title,
                    positionIndex = 0,
                    timeBlockTitle = discoveryContextTitle,
                )
                onEpisodeClick(ep, parentPodcast)
            },
        )
    }

    // Remaining items become individual staggered (masonry) cards.
    val remaining = items.drop(1)
    itemsIndexed(
        remaining,
        key = { _, ep -> "for_you_${ep.id}" },
        contentType = { _, _ -> "for_you_card" },
    ) { index, ep ->
        val originalIndex = index + 1
        val parentPodcast =
            Podcast(
                id = ep.podcastId ?: "",
                title = ep.podcastTitle ?: "Podcast",
                artist = "",
                imageUrl = ep.podcastImageUrl?.takeIf { it.isNotBlank() } ?: ep.imageUrl?.takeIf { it.isNotBlank() } ?: "",
                description = "",
                genre = ep.podcastGenre ?: "Podcast",
            )
        CuratedEpisodeCard(
            podcast = parentPodcast,
            episode = ep,
            onClick = {
                AnalyticsHelper.trackHomeRecommendationCardTapped(
                    episodeId = ep.id,
                    episodeTitle = ep.title,
                    podcastId = parentPodcast.id,
                    podcastName = parentPodcast.title,
                    positionIndex = originalIndex,
                    timeBlockTitle = discoveryContextTitle,
                )
                onEpisodeClick(ep, parentPodcast)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ForYouHeroCard(
    episode: Episode,
    parentPodcast: Podcast,
    isFallback: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .expressiveClickable(shape = RoundedCornerShape(20.dp), onClick = onClick),
    ) {
        // Full-bleed artwork background
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() },
            proxyWidth = 600,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Dark gradient scrim — heavier at bottom for text legibility (Option A)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Transparent,
                                    0.3f to Color.Black.copy(alpha = 0.15f),
                                    0.6f to Color.Black.copy(alpha = 0.65f),
                                    1.0f to Color.Black,
                                ),
                        ),
                    ),
        )

        // Premium tag for the Hero card
        Box(
            modifier =
                Modifier
                    .padding(14.dp)
                    .align(Alignment.TopStart)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = if (isFallback) "POPULAR IN YOUR REGION" else "FEATURED RECOMMENDATION",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                )
            }
        }

        // Metadata anchored to bottom
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Podcast name
            Text(
                text = episode.podcastTitle ?: "",
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Episode title (larger font since it's the hero card)
            Text(
                text = episode.title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Duration & additional context
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (episode.duration > 0) {
                    val minutes = episode.duration / 60
                    Text(
                        text = "$minutes min read/listen",
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                    )
                }

                val genre = episode.podcastGenre ?: parentPodcast.genre
                if (!genre.isNullOrBlank()) {
                    Text(
                        text = "•",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                    )
                    Text(
                        text = genre,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForYouHeroSkeleton(
    baseColor: Color,
    highlightColor: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(20.dp)),
    )
}

@Composable
private fun ForYouHorizontalBentoCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(20.dp))
                .expressiveClickable(onClick = onClick),
    ) {
        // Full-bleed artwork background
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() },
            proxyWidth = 600,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Heavy dark gradient scrim for pristine text contrast
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Transparent,
                                    0.4f to Color.Black.copy(alpha = 0.3f),
                                    1.0f to Color.Black.copy(alpha = 0.85f),
                                ),
                        ),
                    ),
        )

        // Duration chip — top left
        if (episode.duration > 0) {
            val minutes = episode.duration / 60
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier =
                    Modifier
                        .padding(10.dp)
                        .align(Alignment.TopStart),
            ) {
                Text(
                    text = "$minutes min",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }

        // Genre chip — top right (or video badge if video)
        val genre = episode.podcastGenre
        if (episode.enclosureType?.startsWith("video/") == true) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier =
                    Modifier
                        .padding(10.dp)
                        .align(Alignment.TopEnd),
            ) {
                Box(
                    modifier = Modifier.padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        } else if (!genre.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier =
                    Modifier
                        .padding(10.dp)
                        .align(Alignment.TopEnd),
            ) {
                Text(
                    text = genre,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }

        // Episode title — bottom aligned
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = episode.title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = Color.White,
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ForYouHorizontalBentoSkeleton(
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(20.dp))
                .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(20.dp)),
    )
}
