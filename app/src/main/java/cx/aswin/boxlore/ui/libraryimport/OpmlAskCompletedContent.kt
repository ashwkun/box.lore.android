package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.Podcast

private val AskCompletedCorner = RoundedCornerShape(24.dp)

@Composable
internal fun AskCompletedContent(
    state: OpmlImportState.AskCompleted,
    onSelectionChanged: (Set<String>) -> Unit,
    onConfirmCompleted: () -> Unit,
    onSkipCompleted: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
    ) {
        AskCompletedHeader()
        Spacer(modifier = Modifier.height(16.dp))
        AskCompletedBulkActions(
            importedPodcasts = state.importedPodcasts,
            onSelectionChanged = onSelectionChanged,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AskCompletedPodcastList(
            state = state,
            onSelectionChanged = onSelectionChanged,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.height(20.dp))
        AskCompletedFooterActions(
            selectedCount = state.selectedIds.size,
            onConfirmCompleted = onConfirmCompleted,
            onSkipCompleted = onSkipCompleted,
        )
    }
}

@Composable
private fun AskCompletedHeader() {
    Text(
        text = "Start fresh?",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Mark past episodes as played on selected shows so your queue stays focused on new releases.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AskCompletedBulkActions(
    importedPodcasts: List<Podcast>,
    onSelectionChanged: (Set<String>) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip(
            onClick = { onSelectionChanged(importedPodcasts.map { it.id }.toSet()) },
            label = { Text("Select all") },
            icon = {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
            },
            shape = RoundedCornerShape(50),
        )
        SuggestionChip(
            onClick = { onSelectionChanged(emptySet()) },
            label = { Text("Deselect all") },
            icon = {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
            },
            shape = RoundedCornerShape(50),
        )
    }
}

@Composable
private fun AskCompletedPodcastList(
    state: OpmlImportState.AskCompleted,
    onSelectionChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AskCompletedCorner,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(state.importedPodcasts, key = { it.id }) { podcast ->
                AskCompletedPodcastRow(
                    podcast = podcast,
                    selectedIds = state.selectedIds,
                    onSelectionChanged = onSelectionChanged,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
            }
        }
    }
}

@Composable
private fun AskCompletedPodcastRow(
    podcast: Podcast,
    selectedIds: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
) {
    val isChecked = podcast.id in selectedIds
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onSelectionChanged(selectedIds.withIdSelected(podcast.id, !isChecked))
                },
        headlineContent = {
            AskCompletedPodcastTitle(title = podcast.title)
        },
        supportingContent = {
            AskCompletedPodcastArtist(artist = podcast.artist)
        },
        leadingContent = {
            AskCompletedPodcastImage(podcast = podcast)
        },
        trailingContent = {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { checked ->
                    onSelectionChanged(selectedIds.withIdSelected(podcast.id, checked))
                },
            )
        },
        colors =
            ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
    )
}

private fun Set<String>.withIdSelected(
    id: String,
    selected: Boolean,
): Set<String> = if (selected) this + id else this - id

@Composable
private fun AskCompletedPodcastTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AskCompletedPodcastArtist(artist: String) {
    Text(
        text = artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AskCompletedPodcastImage(podcast: Podcast) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            proxyWidth = 150,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun AskCompletedFooterActions(
    selectedCount: Int,
    onConfirmCompleted: () -> Unit,
    onSkipCompleted: () -> Unit,
) {
    Button(
        onClick = onConfirmCompleted,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = "Mark selected ($selectedCount) as played",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    TextButton(
        onClick = onSkipCompleted,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp),
    ) {
        Text(
            text = "Keep all unplayed",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
