package cx.aswin.boxlore.feature.explore

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
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

    LoreHaloBackground(accentColor = MaterialTheme.colorScheme.primary) {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = cx.aswin.boxlore.core.designsystem.R.drawable.logo_lore
                        ),
                        contentDescription = "Lore",
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .align(Alignment.Center)
                    )
                    if (uiState is LearnHistoryUiState.Success &&
                        (uiState as LearnHistoryUiState.Success).entries.isNotEmpty()
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(Icons.Rounded.ClearAll, contentDescription = "Clear history")
                            }
                        }
                    }
                }
            },
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { innerPadding ->
            when (val state = uiState) {
                is LearnHistoryUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(bottom = bottomContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader.Expressive(size = 64.dp)
                    }
                }
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
                            item {
                                Column(
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        end = 4.dp,
                                        bottom = 8.dp
                                    )
                                ) {
                                    Text(
                                        text = "Your Lore history",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Questions you dismissed or queued, ready to rediscover.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(state.entries, key = { it.episodeId }) { entry ->
                                LearnHistoryRow(
                                    entry = entry,
                                    onClick = {
                                        onEpisodeClick(entry.toLearnCuriosityCard().toEpisode())
                                    },
                                    onRestore = { viewModel.restoreEntry(entry.episodeId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnHistoryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.Cookie6,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "No Lore to revisit yet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Questions you dismiss or queue will wait here, ready for another look.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            OptimizedImage(
                url = imageUrl.orEmpty(),
                proxyWidth = 160,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.question.ifBlank { entry.episodeTitle },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (entry.question.isNotBlank()) {
                    Text(
                        text = entry.episodeTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!entry.podcastTitle.isNullOrBlank()) {
                    Text(
                        text = entry.podcastTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = when (entry.action) {
                        LearnHistoryAction.DISMISS -> MaterialTheme.colorScheme.errorContainer
                        LearnHistoryAction.QUEUE -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = listOf(actionLabel, dateLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.FilledTonalIconButton(onClick = onRestore) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Restore card")
            }
        }
    }
}
