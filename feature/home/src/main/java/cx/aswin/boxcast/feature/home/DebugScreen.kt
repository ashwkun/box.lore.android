package cx.aswin.boxcast.feature.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.data.database.PodcastEntity
import kotlinx.coroutines.delay

/**
 * Full-screen debug tools: sleep-prompt testing (including a night-window bypass), a live
 * playback state readout, a DB inspector, and misc flag/cache resets. Reached via a long-press
 * on the Home avatar; available in all builds, matching the previous dialog's behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    playbackRepository: PlaybackRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: DebugViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return DebugViewModel(application, playbackRepository) as T
            }
        }
    )

    val playerState by playbackRepository.playerState.collectAsState()
    val skipSleepWindow by viewModel.skipSleepWindow.collectAsState()
    val history by viewModel.history.collectAsState(initial = emptyList())
    val podcasts by viewModel.podcasts.collectAsState(initial = emptyList())

    // Re-derive the night-window status periodically so the readout stays live while open.
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            nowTick = System.currentTimeMillis()
        }
    }
    val isInNightWindow = remember(nowTick) { playbackRepository.isInNightWindow() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Debug Tools") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 220.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SectionCard("Sleep Prompt Testing", Icons.Rounded.Bedtime, isCollapsible = false) {
                    SleepTestingSection(
                        state = SleepTestingState(
                            skipSleepWindow = skipSleepWindow,
                            isInNightWindow = isInNightWindow,
                            showLateNightNudge = playerState.showLateNightNudge,
                            sleepTimerEnd = playerState.sleepTimerEnd,
                            sleepAtEndOfEpisode = playerState.sleepAtEndOfEpisode
                        ),
                        actions = SleepTestingActions(
                            onToggleSkipSleepWindow = viewModel::setSkipSleepWindow,
                            onForcePromptNow = viewModel::forceSleepPromptNow,
                            onClearSleepTimer = viewModel::clearSleepTimer,
                            onResetSleepWindowGuard = viewModel::resetSleepWindowGuard
                        )
                    )
                }
            }

            item {
                SectionCard("Playback State", Icons.Rounded.PlayCircle, isCollapsible = false) {
                    PlaybackStateSection(
                        episodeTitle = playerState.currentEpisode?.title,
                        podcastTitle = playerState.currentPodcast?.title,
                        isPlaying = playerState.isPlaying,
                        isLoading = playerState.isLoading,
                        position = playerState.position,
                        duration = playerState.duration
                    )
                }
            }

            item {
                SectionCard("DB Inspector", Icons.Rounded.Storage, isCollapsible = true, initiallyExpanded = false) {
                    DbInspectorSection(
                        history = history,
                        podcasts = podcasts,
                        onDeleteHistoryItem = viewModel::deleteHistoryItem
                    )
                }
            }

            item {
                SectionCard("Flags & Cache", Icons.Rounded.CleaningServices, isCollapsible = true, initiallyExpanded = false) {
                    FlagsCacheSection(
                        onResetFeatureFlag = viewModel::resetFeatureFlag,
                        onClearDismissedCuriosities = viewModel::clearDismissedCuriosities
                    )
                }
            }
        }
    }
}

private data class SleepTestingState(
    val skipSleepWindow: Boolean,
    val isInNightWindow: Boolean,
    val showLateNightNudge: Boolean,
    val sleepTimerEnd: Long?,
    val sleepAtEndOfEpisode: Boolean
)

private data class SleepTestingActions(
    val onToggleSkipSleepWindow: (Boolean) -> Unit,
    val onForcePromptNow: () -> Unit,
    val onClearSleepTimer: () -> Unit,
    val onResetSleepWindowGuard: () -> Unit
)

@Composable
private fun StatusRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun SleepTestingSection(
    state: SleepTestingState,
    actions: SleepTestingActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Skip sleep-window restriction", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Show the prompt on every playback, any time of day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.skipSleepWindow, onCheckedChange = actions.onToggleSkipSleepWindow)
        }

        Spacer(Modifier.height(2.dp))

        StatusRow("Night window (10:30 PM – 4 AM)", if (state.isInNightWindow) "Active" else "Inactive")
        StatusRow("Prompt currently shown", if (state.showLateNightNudge) "Yes" else "No")
        StatusRow(
            "Sleep timer",
            when {
                state.sleepAtEndOfEpisode -> "End of episode"
                state.sleepTimerEnd != null -> "${((state.sleepTimerEnd - System.currentTimeMillis()) / 60000).coerceAtLeast(0)}m left"
                else -> "Off"
            }
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = actions.onForcePromptNow, modifier = Modifier.weight(1f)) {
                Text("Trigger now", maxLines = 1)
            }
            OutlinedButton(onClick = actions.onClearSleepTimer, modifier = Modifier.weight(1f)) {
                Text("Clear timer", maxLines = 1)
            }
        }
        OutlinedButton(onClick = actions.onResetSleepWindowGuard, modifier = Modifier.fillMaxWidth()) {
            Text("Reset once-per-night guard")
        }
    }
}

@Composable
private fun PlaybackStateSection(
    episodeTitle: String?,
    podcastTitle: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: Long,
    duration: Long
) {
    fun fmt(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusRow("Episode", episodeTitle ?: "None")
        StatusRow("Podcast", podcastTitle ?: "None")
        StatusRow("Playing", if (isPlaying) "Yes" else "No")
        StatusRow("Loading", if (isLoading) "Yes" else "No")
        StatusRow("Position / Duration", "${fmt(position)} / ${fmt(duration)}")
    }
}

@Composable
private fun DbInspectorSection(
    history: List<ListeningHistoryEntity>,
    podcasts: List<PodcastEntity>,
    onDeleteHistoryItem: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("History (${history.size})", "Subs (${podcasts.size})")

    Column {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (selectedTabIndex == 0) {
                history.forEach { item -> HistoryDebugCard(item, onDelete = onDeleteHistoryItem) }
            } else {
                podcasts.forEach { item -> PodcastDebugCard(item) }
            }
        }
    }
}

@Composable
private fun FlagsCacheSection(
    onResetFeatureFlag: () -> Unit,
    onClearDismissedCuriosities: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onResetFeatureFlag, modifier = Modifier.fillMaxWidth()) {
            Text("Reset feature announcement flag")
        }
        OutlinedButton(onClick = onClearDismissedCuriosities, modifier = Modifier.fillMaxWidth()) {
            Text("Clear dismissed cards")
        }
    }
}

@Composable
private fun HistoryDebugCard(item: ListeningHistoryEntity, onDelete: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.episodeTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(item.podcastName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    "${item.progressMs}/${item.durationMs}ms · dirty=${item.isDirty} · completed=${item.isCompleted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDelete(item.episodeId) }) {
                Icon(Icons.Rounded.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PodcastDebugCard(item: PodcastEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(item.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                "Subscribed=${item.isSubscribed} · lastRefreshed=${item.lastRefreshed}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
