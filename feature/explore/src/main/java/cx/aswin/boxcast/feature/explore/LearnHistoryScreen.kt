package cx.aswin.boxcast.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxcast.core.data.toEpisode
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnHistoryScreen(
    viewModel: LearnHistoryViewModel,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onEpisodeClick: (Episode) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear card history?") },
            text = { Text("This removes your swiped and queued cards from history. It does not affect listening history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Card History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (uiState is LearnHistoryUiState.Success &&
                    (uiState as LearnHistoryUiState.Success).entries.isNotEmpty()
                ) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Rounded.ClearAll, contentDescription = "Clear history")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        when (val state = uiState) {
            is LearnHistoryUiState.Loading -> Unit
            is LearnHistoryUiState.Success -> {
                if (state.entries.isEmpty()) {
                    LearnHistoryEmptyState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(bottom = bottomContentPadding)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = bottomContentPadding + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.entries, key = { it.episodeId }) { entry ->
                            LearnHistoryRow(
                                entry = entry,
                                onClick = { onEpisodeClick(entry.toDailyCuriosityDto().episode.toEpisode()) },
                                onRestore = { viewModel.restoreEntry(entry.episodeId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnHistoryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No cards yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Episodes you dismiss or queue on Learn show up here so you can find them again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LearnHistoryRow(
    entry: LearnHistoryEntry,
    onClick: () -> Unit,
    onRestore: () -> Unit
) {
    val imageUrl = entry.imageUrl ?: entry.feedImage
    val actionLabel = when (entry.action) {
        LearnHistoryAction.DISMISS -> "Dismissed"
        LearnHistoryAction.QUEUE -> "Queued"
    }
    val dateLabel = remember(entry.dismissedAtMs) {
        if (entry.dismissedAtMs <= 0L) {
            ""
        } else {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(entry.dismissedAtMs))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OptimizedImage(
                url = imageUrl.orEmpty(),
                proxyWidth = 160,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.episodeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.podcastTitle.isNullOrBlank()) {
                    Text(
                        text = entry.podcastTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (entry.question.isNotBlank()) {
                    Text(
                        text = entry.question,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOf(actionLabel, dateLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Rounded.Undo, contentDescription = "Restore card")
            }
        }
    }
}
