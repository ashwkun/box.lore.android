package cx.aswin.boxcast.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import androidx.compose.foundation.background
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Small caption explaining why an item is in the queue, derived from the provenance
 * persisted on each queue row (contextType + contextSourceId).
 */
internal fun queueSourceLabel(episode: Episode): String? = when (episode.contextType) {
    "LORE" -> "From Lore"
    "AUTO_FILL" -> when (episode.contextSourceId) {
        "same_podcast" -> "Continuing series"
        "resume" -> "Pick up where you left off"
        "subscription" -> "From your subscriptions"
        "server_rec", "personalized_rec" -> "Recommended for you"
        "similar_episode" -> "Based on what you're playing"
        "similar_liked" -> "Based on something you liked"
        "trending" -> episode.podcastGenre
            ?.takeIf { it.isNotBlank() && it != "Podcast" }
            ?.let { "Trending in $it" } ?: "Trending now"
        else -> "Added for you"
    }
    else -> null // MANUAL and unknown rows get no label
}

/**
 * Queue bottom sheet content: header with close button + drag-to-reorder queue list.
 *
 * Indices in [onMove]/[onDragEnd] are UI list indices — the currently playing episode
 * is hidden from this sheet, so callers must map them with QueueMath.uiIndexToQueueIndex.
 */
@Composable
fun QueueSheetContent(
    queue: List<Episode>,
    currentPodcast: Podcast?,
    colorScheme: ColorScheme,
    onPlayEpisode: (Episode) -> Unit,
    onRemoveEpisode: (Episode) -> Unit,
    onClose: () -> Unit,
    onMove: (fromUiIndex: Int, toUiIndex: Int) -> Unit = { _, _ -> },
    onDragEnd: (episodeId: String, fromUiIndex: Int, toUiIndex: Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Header: "Up Next" + Close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "${queue.size} episodes",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close queue",
                    tint = colorScheme.onSurface
                )
            }
        }
        
        HorizontalDivider(
            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant
                )
            }
        } else {
            val lazyListState = rememberLazyListState()
            // Where the active drag started, so drag-end can report from -> to once.
            val dragStartIndex = remember { mutableIntStateOf(-1) }
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                onMove(from.index, to.index)
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(queue, key = { _, episode -> episode.id }) { index, episode ->
                    ReorderableItem(reorderableState, key = episode.id) { isDragging ->
                        QueueItemRow(
                            episode = episode,
                            podcast = currentPodcast,
                            colorScheme = colorScheme,
                            onClick = { onPlayEpisode(episode) },
                            onRemove = { onRemoveEpisode(episode) },
                            sourceLabel = queueSourceLabel(episode),
                            isDragging = isDragging,
                            dragHandleModifier = Modifier.draggableHandle(
                                onDragStarted = { dragStartIndex.intValue = index },
                                onDragStopped = {
                                    val from = dragStartIndex.intValue
                                    dragStartIndex.intValue = -1
                                    val to = queue.indexOfFirst { it.id == episode.id }
                                    if (from != -1 && to != -1) onDragEnd(episode.id, from, to)
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueItemRow(
    episode: Episode,
    podcast: Podcast?,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    sourceLabel: String? = null,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isDragging) colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else colorScheme.surface.copy(alpha = 0f)
            )
            .expressiveClickable { onClick() }
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.imageUrl?.takeIf { it.isNotBlank() }
                ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() }
                ?: podcast?.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title.replace("+", " "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = (episode.podcastTitle ?: podcast?.title ?: "Unknown Podcast").replace("+", " "),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sourceLabel != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Remove button
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove from queue",
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Drag-to-reorder handle
        if (dragHandleModifier != null) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "Reorder",
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = dragHandleModifier
                    .size(40.dp)
                    .padding(8.dp)
            )
        }
    }
}
