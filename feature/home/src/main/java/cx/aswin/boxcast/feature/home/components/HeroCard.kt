package cx.aswin.boxcast.feature.home.components

import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import cx.aswin.boxcast.feature.home.HeroType
import cx.aswin.boxcast.feature.home.SmartHeroItem
import cx.aswin.boxcast.core.designsystem.components.AnimatedShapesFallback
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage

@Composable
fun HeroCard(
    item: SmartHeroItem,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    onToggleSubscription: () -> Unit,
    currentPlayingPodcastId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        // Remove standard onClick to avoid double ripple/no-bounce
        shape = MaterialTheme.shapes.extraLarge, 
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge)
            .expressiveClickable(onClick = onArrowClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image Logic: Episode Art -> Podcast Art -> Shapes
            val episodeImage = item.podcast.latestEpisode?.imageUrl?.takeIf { it.isNotEmpty() }
            val fallbackImage = item.podcast.fallbackImageUrl?.takeIf { it.isNotEmpty() }
            val podcastImage = item.podcast.imageUrl.takeIf { it.isNotEmpty() }
            
            // Best available image: Episode -> Podcast -> Fallback
            val bestImageUrl = episodeImage ?: podcastImage ?: fallbackImage
            
            OptimizedImage(
                url = bestImageUrl,
                proxyWidth = 800, // Full-width hero needs high res
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Strong Gradient Overlay for text visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.55f), // Stronger middle
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.92f)
                            ),
                            startY = 0f
                        )
                    )
            )
            // Content - Distributed Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP: Context Badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = item.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                // BOTTOM: Text + Button
                Column {
                // Context-Aware Typography
                val isDiscovery = item.type == HeroType.SPOTLIGHT
                val displayDesc = item.description ?: "Start Listening"
                
                if (isDiscovery) {
                    // SPOTLIGHT: Show Title prominent, Episode secondary
                    Text(
                        text = item.podcast.title.replace("+", " "),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = item.podcast.artist.replace("+", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayDesc.replace("+", " "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                } else {
                    // RESUME / JUMP_BACK_IN: Episode prominent, Show secondary
                    Text(
                        text = displayDesc.replace("+", " "),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.podcast.title.replace("+", " ")} • ${item.podcast.artist.replace("+", " ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
             
                Spacer(modifier = Modifier.height(16.dp))
                
                // Material 3 Split Button (Two Connected Rounded Sections)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leading Button (Play/Resume/Pause) - Rounded Left, Flat Right
                    val isCurrentPodcast = currentPlayingPodcastId == item.podcast.id
                    val showPause = isCurrentPodcast && isPlaying
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(
                            topStart = 24.dp,
                            bottomStart = 24.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .width(160.dp)
                            .expressiveClickable(isolate = true, onClick = onClick)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (showPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showPause) "Pause" else if (item.type == HeroType.RESUME || isCurrentPodcast) "Resume" else "Play",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    // Trailing Button (Details) - Flat Left, Rounded Right
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            bottomStart = 8.dp,
                            topEnd = 24.dp,
                            bottomEnd = 24.dp
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .expressiveClickable(isolate = true, onClick = onArrowClick)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Details",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                } // End of bottom Column
            }
        }
    }
}
