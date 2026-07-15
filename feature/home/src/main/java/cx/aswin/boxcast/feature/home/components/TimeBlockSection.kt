package cx.aswin.boxcast.feature.home.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.home.CuratedTimeBlock
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily


/**
 * Emits the time-based curated block into a [LazyStaggeredGridScope]. The header and each vibe
 * rail become individual full-line items so a single rail (a cheap horizontal LazyRow) composes
 * as it scrolls into view, instead of composing every rail atomically — which caused a large
 * frame whenever the block entered or re-entered the viewport near the bottom of the feed.
 */
fun LazyStaggeredGridScope.timeBlockItems(
    data: CuratedTimeBlock,
    onCuratedEpisodeClick: (Episode, Podcast, String, Int) -> Unit,
    onImpression: (String, List<String>) -> Unit = { _, _ -> },
    onSeeAllClick: () -> Unit = {},
    leadingContent: LazyStaggeredGridScope.() -> Unit = {},
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "time_block_header", contentType = "time_block_header") {
        LaunchedEffect(data.title) {
            onImpression(data.title, data.sections.map { it.category })
        }
        TimeBlockHeader(data = data, onSeeAllClick = onSeeAllClick)
    }
    leadingContent()
    data.sections.forEachIndexed { index, section ->
        item(
            span = StaggeredGridItemSpan.FullLine,
            key = "time_block_rail_${section.category}",
            contentType = "time_block_rail"
        ) {
            TimeBlockRail(
                section = section,
                index = index,
                onCuratedEpisodeClick = onCuratedEpisodeClick
            )
        }
    }
}

@Composable
private fun TimeBlockHeader(
    data: CuratedTimeBlock,
    onSeeAllClick: () -> Unit
) {
    val themeColor = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            AnimatedTimeBlockIcon(
                title = data.title,
                themeColor = themeColor,
                fallbackIcon = data.icon
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = SectionHeaderFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                data.subtitle.takeIf(String::isNotBlank)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
private fun TimeBlockRail(
    section: cx.aswin.boxcast.feature.home.CuratedSectionData,
    index: Int,
    onCuratedEpisodeClick: (Episode, Podcast, String, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                // Grid verticalItemSpacing already adds ~12dp between items, so trim the
                // internal top padding to keep the original vertical rhythm.
                top = if (index == 0) 4.dp else 16.dp,
                bottom = 12.dp
            )
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val distinctPodcasts = remember(section.podcasts) { section.podcasts.distinctBy { it.id } }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                count = distinctPodcasts.size,
                key = { i -> distinctPodcasts[i].id }
            ) { i ->
                val podcast = distinctPodcasts[i]
                val episode = podcast.latestEpisode

                if (episode != null) {
                    CuratedEpisodeCard(
                        podcast = podcast,
                        episode = episode,
                        onClick = {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackCuratedCardTapped(
                                podcastId = podcast.id,
                                podcastName = podcast.title,
                                vibeId = section.category,
                                positionIndex = i
                            )
                            onCuratedEpisodeClick(episode, podcast, section.category, i)
                        },
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedTimeBlockIcon(title: String, themeColor: Color, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    val icon = when (title) {
        "Good Morning", "Afternoon Break" -> Icons.Rounded.WbSunny
        "Evening Unwind" -> Icons.Rounded.WbSunny
        "Late Night Listen" -> Icons.Rounded.NightsStay
        else -> fallbackIcon
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = themeColor,
        modifier = Modifier.size(24.dp)
    )
}
