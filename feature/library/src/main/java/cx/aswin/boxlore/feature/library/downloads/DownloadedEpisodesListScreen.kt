package cx.aswin.boxlore.feature.library.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.DownloadsSortOrder
import cx.aswin.boxlore.feature.library.ExpressiveSolarSystemEmptyState
import cx.aswin.boxlore.feature.library.LibraryUiState
import cx.aswin.boxlore.feature.library.LibraryViewModel
import cx.aswin.boxlore.feature.library.PlayAllFab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedEpisodesScreen(
    viewModel: LibraryViewModel,
    userPrefs: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    isOffline: Boolean = false,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
    onPodcastShowClick: (String, String) -> Unit, // Navigation callback
    onSettingsClick: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    isSyncing: Boolean = false,
    isPlayerActive: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleStyle = lerp(
        start = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        stop = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        fraction = scrollBehavior.state.collapsedFraction,
    )

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.trackDownloadsExit()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.onScreenResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryDownloadsViewed("library_hub_card")
    }

    val downloads = (uiState as? LibraryUiState.Success)?.downloadedEpisodes ?: emptyList()
    val currentSort by viewModel.downloadsSortOrder.collectAsStateWithLifecycle()

    val sortedGroups = remember(downloads, currentSort) {
        val grouped = downloads.groupBy { it.podcastId.ifEmpty { it.podcastName } }
        val groups = grouped.entries.map { entry ->
            val podcastId = entry.key
            val items = entry.value
            val firstItem = items.firstOrNull()
            val podcastName = firstItem?.podcastName ?: ""
            val podcastImageUrl = firstItem?.podcastImageUrl
            val totalSize = items.sumOf { it.sizeBytes }
            val latestDownloadedAt = items.maxOfOrNull { it.downloadedAt } ?: 0L
            PodcastGroup(
                podcastId = podcastId,
                podcastName = podcastName,
                podcastImageUrl = podcastImageUrl,
                episodes = items,
                totalSizeBytes = totalSize,
                latestDownloadedAt = latestDownloadedAt
            )
        }
        when (currentSort) {
            DownloadsSortOrder.RECENT -> groups.sortedByDescending { it.latestDownloadedAt }
            DownloadsSortOrder.NAME -> groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.podcastName })
            DownloadsSortOrder.SIZE -> groups.sortedByDescending { it.totalSizeBytes }
            DownloadsSortOrder.COUNT -> groups.sortedByDescending { it.episodes.size }
        }
    }

    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedPodcastIds = remember { mutableStateListOf<String>() }
    var deleteConfirmSelectedDialog by rememberSaveable { mutableStateOf(false) }

    val totalSizeBytes = remember(downloads) { downloads.sumOf { it.sizeBytes } }


    val listBottomPadding = remember(downloads, isSelectionMode, isPlayerActive) {
        if (downloads.isNotEmpty() && !isSelectionMode) {
            if (isPlayerActive) 240.dp else 160.dp
        } else {
            if (isPlayerActive) 150.dp else 80.dp
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isSelectionMode) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedPodcastIds.size} Selected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedPodcastIds.clear()
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                if (selectedPodcastIds.size == sortedGroups.size) {
                                    selectedPodcastIds.clear()
                                } else {
                                    selectedPodcastIds.clear()
                                    selectedPodcastIds.addAll(sortedGroups.map { it.podcastId })
                                }
                            }) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                            }
                            IconButton(
                                onClick = { deleteConfirmSelectedDialog = true },
                                enabled = selectedPodcastIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = if (selectedPodcastIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                } else {
                    LargeTopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Downloads",
                                    style = titleStyle,
                                )
                                if (downloads.isNotEmpty() && scrollBehavior.state.collapsedFraction < 0.4f) {
                                    Text(
                                        text = "${downloads.size} ${if (downloads.size == 1) "episode" else "episodes"} • ${formatSize(totalSizeBytes)} used",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (sortedGroups.isNotEmpty()) {
                                IconButton(onClick = { isSelectionMode = true }) {
                                    Icon(Icons.Rounded.Checklist, contentDescription = "Select podcasts")
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                }

                if (isOffline) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Offline Mode",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "No internet connection. Playing downloaded episodes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (uiState) {
                is LibraryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error loading downloads")
                    }
                }
                is LibraryUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = listBottomPadding, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Smart Downloads Dashboard Card
                        item {
                            SmartDownloadsDashboardCard(
                                userPrefs = userPrefs,
                                downloads = downloads,
                                onSettingsClick = onSettingsClick,
                                onSyncNow = onSyncNow,
                                isSyncing = isSyncing
                            )
                        }

                        if (downloads.isEmpty()) {
                            item {
                                val emptyTitle = "Buddha woulda coulda"
                                val emptyDescription = if (isOffline) {
                                    "No downloaded episodes found. Turn on Wi-Fi and charging to auto-sync your smart downloads!"
                                } else {
                                    "No downloaded episodes yet. Turn on Smart Downloads above or find podcasts to download manually."
                                }
                                ExpressiveSolarSystemEmptyState(
                                    title = emptyTitle,
                                    description = emptyDescription,
                                    icon = Icons.Rounded.CloudOff,
                                    actionText = if (isOffline) null else "Find Podcasts",
                                    modifier = Modifier,
                                    onExploreClick = onExploreClick
                                )
                            }
                        } else {
                            items(items = sortedGroups, key = { it.podcastId }) { group ->
                                val isSelected = group.podcastId in selectedPodcastIds
                                PodcastListShowCard(
                                    group = group,
                                    onClick = {
                                        onPodcastShowClick(group.podcastId, group.podcastName)
                                    },
                                    onPlayClick = {
                                        val eps = group.episodes.map { it.toEpisode() }
                                        val pod = group.episodes.first().toPodcast()
                                        viewModel.playQueue(eps, pod)
                                    },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onSelectedChange = { selected ->
                                        if (selected) {
                                            selectedPodcastIds.add(group.podcastId)
                                        } else {
                                            selectedPodcastIds.remove(group.podcastId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Play All FAB aligned to bottom right
            if (downloads.isNotEmpty() && !isSelectionMode) {
                PlayAllFab(
                    isPlayerActive = isPlayerActive,
                    onClick = {
                        val episodes = downloads.map { it.toEpisode() }
                        val dummyPodcast = Podcast(
                            id = "downloads_all",
                            title = "All Downloads",
                            artist = "",
                            imageUrl = "",
                            description = "",
                            genre = ""
                        )
                        viewModel.playQueue(episodes, dummyPodcast)
                    }
                )
            }

            if (deleteConfirmSelectedDialog) {
                AlertDialog(
                    onDismissRequest = { deleteConfirmSelectedDialog = false },
                    title = { Text("Delete downloads?") },
                    text = {
                        val selectedCount = selectedPodcastIds.size
                        Text("Are you sure you want to delete all downloaded episodes for the $selectedCount selected ${if (selectedCount == 1) "podcast" else "podcasts"}?")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val epsToDelete = sortedGroups
                                    .filter { it.podcastId in selectedPodcastIds }
                                    .flatMap { it.episodes }
                                    .map { it.episodeId }
                                viewModel.removeMultipleDownloads(epsToDelete)
                                isSelectionMode = false
                                selectedPodcastIds.clear()
                                deleteConfirmSelectedDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmSelectedDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
