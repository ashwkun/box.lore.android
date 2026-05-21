package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage

@Composable
fun RisingCard(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .width(160.dp) // Slightly bigger
            .expressiveClickable(onClick = onClick)
    ) {
        Column {
            // Image + Overlay - Simple fallback: Podcast Image → AnimatedShapesFallback
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(160.dp)
                )
                
                // M3 Genre Chip Overlay (Top Left)
                if (podcast.genre.isNotEmpty()) {
                    androidx.compose.material3.Surface(
                        shape = MaterialTheme.shapes.small, // M3 Chip Shape (8dp)
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f), // Slightly translucent
                        modifier = Modifier
                            .padding(8.dp)
                            .height(24.dp)
                            .align(androidx.compose.ui.Alignment.TopStart)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(
                                text = podcast.genre.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Text Content
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

}
