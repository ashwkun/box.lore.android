package cx.aswin.boxcast.feature.home.components

import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.model.Podcast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background

/**
 * Hero Grid Card — Progressive Density Layout
 *
 * Supports 2, 3, or 4 items in a single hero card:
 * - 2 items: Stacked horizontally (top/bottom split)
 * - 3 items: 2 on top row + 1 wide spanning bottom
 * - 4 items: 2×2 grid
 */
@Composable
fun HeroGridCard(
    items: List<Podcast>,
    title: String,
    onPlayClick: (Podcast) -> Unit,
    onDetailsClick: (Podcast) -> Unit,
    currentPlayingPodcastId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            RowHeader(title = title)

            // Adaptive Grid Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                val displayItems = items.take(4)
                when (displayItems.size) {
                    2 -> StackedLayout(displayItems, onPlayClick)
                    3 -> TwoOneLayout(displayItems, onPlayClick)
                    4 -> GridLayout2x2(displayItems, onPlayClick)
                    else -> if (displayItems.isNotEmpty()) {
                        // Fallback: single item fills the space
                        GridCell(displayItems[0], onPlayClick, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// --- Layouts ---

/**
 * 2 items: Stacked top/bottom with horizontal divider.
 * Each item gets ~50% of the card height.
 */
@Composable
private fun StackedLayout(
    items: List<Podcast>,
    onPlayClick: (Podcast) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        GridCell(items[0], onPlayClick, modifier = Modifier.weight(1f).fillMaxWidth())
        GridCell(items[1], onPlayClick, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/**
 * 3 items: 2 on top row, 1 spanning the full bottom.
 * Top row gets ~50% height, bottom gets ~50%.
 */
@Composable
private fun TwoOneLayout(
    items: List<Podcast>,
    onPlayClick: (Podcast) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            GridCell(items[0], onPlayClick, modifier = Modifier.weight(1f).fillMaxHeight())
            GridCell(items[1], onPlayClick, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        GridCell(items[2], onPlayClick, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/**
 * 4 items: Classic 2×2 grid.
 */
@Composable
private fun GridLayout2x2(
    items: List<Podcast>,
    onPlayClick: (Podcast) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            GridCell(items[0], onPlayClick, modifier = Modifier.weight(1f).fillMaxHeight())
            GridCell(items[1], onPlayClick, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            GridCell(items[2], onPlayClick, modifier = Modifier.weight(1f).fillMaxHeight())
            GridCell(items[3], onPlayClick, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// --- Shared Components ---

@Composable
private fun RowHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GridCell(
    podcast: Podcast,
    onPlayClick: (Podcast) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPodcast by androidx.compose.runtime.rememberUpdatedState(podcast)
    val currentOnPlayClick by androidx.compose.runtime.rememberUpdatedState(onPlayClick)

    Surface(
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .expressiveClickable {
                currentOnPlayClick(currentPodcast)
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OptimizedImage(
                url = podcast.imageUrl.ifEmpty { podcast.fallbackImageUrl.orEmpty() },
                proxyWidth = 400,
                contentDescription = podcast.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Strong gradient overlay — covers bottom 60% of cell for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.95f)
                            ),
                            startY = 0f
                        )
                    )
            )

            // Bottom content: Title + progress
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val episodeTitle = podcast.latestEpisode?.title
                val primaryText = episodeTitle ?: podcast.title

                // Episode title (primary)
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                // Podcast title (secondary)
                if (episodeTitle != null) {
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }

                // Progress bar for resume sessions
                if (podcast.resumeProgress != null && podcast.resumeProgress!! > 0f) {
                    ProgressBar(
                        progress = podcast.resumeProgress!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.3f),
    indicatorColor: Color = MaterialTheme.colorScheme.inversePrimary
) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(indicatorColor)
        )
    }
}
