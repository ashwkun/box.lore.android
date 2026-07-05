package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.data.database.PodcastEntity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDbInspectorDialog(
    history: List<ListeningHistoryEntity>,
    podcasts: List<PodcastEntity>,
    onDeleteHistoryItem: (String) -> Unit,
    onResetFeatureFlag: () -> Unit,
    onResetSleepNudge: () -> Unit,
    onClearSleepTimer: () -> Unit,
    onClearDismissedCuriosities: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column {
                var selectedTabIndex by remember { mutableIntStateOf(0) }
                val tabs = listOf("History (${history.size})", "Subs (${podcasts.size})")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Debug Tools", style = MaterialTheme.typography.titleMedium)
                        androidx.compose.material3.IconButton(onClick = onDismissRequest) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                                contentDescription = "Close"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.OutlinedButton(onClick = onClearSleepTimer) {
                            Text("Clear Sleep Timer")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = onResetSleepNudge) {
                            Text("Clear Sleep Nudge")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = onResetFeatureFlag) {
                            Text("Reset Dialog Flag")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = onClearDismissedCuriosities) {
                            Text("Clear Dismissed Cards")
                        }
                    }
                }

                PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    if (selectedTabIndex == 0) {
                        items(history) { item ->
                            HistoryDebugCard(item, onDelete = onDeleteHistoryItem)
                        }
                    } else {
                        items(podcasts) { item ->
                            PodcastDebugCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDebugCard(item: ListeningHistoryEntity, onDelete: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ep Title: ${item.episodeTitle}", style = MaterialTheme.typography.titleSmall)
                Text("Pod Name: ${item.podcastName}", style = MaterialTheme.typography.bodySmall)
                Text("Ep ID: ${item.episodeId}", style = MaterialTheme.typography.labelSmall)
                Text("Pod ID: ${item.podcastId}", style = MaterialTheme.typography.labelSmall)
                Text("Img: ${item.episodeImageUrl}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text("Prog: ${item.progressMs}/${item.durationMs}ms", style = MaterialTheme.typography.labelSmall)
                Text("LastPlayed: ${item.lastPlayedAt}", style = MaterialTheme.typography.labelSmall)
                Text("Dirty: ${item.isDirty} | Completed: ${item.isCompleted}", style = MaterialTheme.typography.labelSmall)
            }
            androidx.compose.material3.IconButton(onClick = { onDelete(item.episodeId) }) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PodcastDebugCard(item: PodcastEntity) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Title: ${item.title}", style = MaterialTheme.typography.titleSmall)
            Text("Author: ${item.author}", style = MaterialTheme.typography.bodySmall)
            Text("ID: ${item.podcastId}", style = MaterialTheme.typography.labelSmall)
            Text("Img: ${item.imageUrl}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text("Desc: ${item.description?.take(50)}...", style = MaterialTheme.typography.labelSmall)
            Text("Subscribed: ${item.isSubscribed}", style = MaterialTheme.typography.labelSmall)
            Text("LastRefreshed: ${item.lastRefreshed}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
