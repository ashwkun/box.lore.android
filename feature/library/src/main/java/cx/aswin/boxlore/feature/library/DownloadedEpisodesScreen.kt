package cx.aswin.boxlore.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cx.aswin.boxlore.core.data.database.DownloadedEpisodeEntity
import kotlinx.coroutines.launch
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

// Helpers to map DownloadedEpisodeEntity to domain models
fun DownloadedEpisodeEntity.toEpisode() = Episode(
    id = episodeId,
    title = episodeTitle,
    description = episodeDescription ?: "",
    audioUrl = localFilePath,
    imageUrl = episodeImageUrl,
    podcastImageUrl = podcastImageUrl,
    duration = (durationMs / 1000).toInt(),
    publishedDate = publishedDate
)

fun DownloadedEpisodeEntity.toPodcast() = Podcast(
    id = podcastId,
    title = podcastName,
    artist = "",
    imageUrl = podcastImageUrl ?: "",
    description = "",
    genre = ""
)

private data class PodcastGroup(
    val podcastId: String,
    val podcastName: String,
    val podcastImageUrl: String?,
    val episodes: List<DownloadedEpisodeEntity>,
    val totalSizeBytes: Long,
    val latestDownloadedAt: Long
)

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedEpisodesScreen(
    viewModel: LibraryViewModel,
    userPrefs: cx.aswin.boxlore.core.data.UserPreferencesRepository,
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
        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackLibraryDownloadsViewed("library_hub_card")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedShowEpisodesScreen(
    viewModel: LibraryViewModel,
    podcastId: String,
    podcastTitle: String,
    onBack: () -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    isPlayerActive: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads = (uiState as? LibraryUiState.Success)?.downloadedEpisodes ?: emptyList()

    // Retrieve episodes belonging to this show
    val showEpisodes = remember(downloads, podcastId, podcastTitle) {
        downloads.filter { 
            (podcastId.isNotEmpty() && it.podcastId == podcastId) || 
            (podcastTitle.isNotEmpty() && it.podcastName == podcastTitle) 
        }
    }

    // Auto navigate back if all downloads are removed
    LaunchedEffect(uiState, showEpisodes) {
        if (uiState is LibraryUiState.Success && showEpisodes.isEmpty()) {
            onBack()
        }
    }

    val currentSort by viewModel.showSortOrder.collectAsStateWithLifecycle()
    val sortedEpisodes = remember(showEpisodes, currentSort) {
        when (currentSort) {
            ShowSortOrder.NEWEST -> showEpisodes.sortedByDescending { it.downloadedAt }
            ShowSortOrder.OLDEST -> showEpisodes.sortedBy { it.downloadedAt }
            ShowSortOrder.LARGEST -> showEpisodes.sortedByDescending { it.sizeBytes }
        }
    }

    val totalSizeBytes = remember(showEpisodes) { showEpisodes.sumOf { it.sizeBytes } }

    val listState = rememberLazyListState()
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else 1000f
        }
    }
    val density = LocalDensity.current
    val morphThreshold = with(density) { 140.dp.toPx() }
    val scrollFraction = (scrollOffset / morphThreshold).coerceIn(0f, 1f)
    val titleAlpha = if (scrollFraction > 0.8f) (scrollFraction - 0.8f) / 0.2f else 0f

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedHeaderHeight = 64.dp + statusBarHeight

    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedEpisodeIds = remember { mutableStateListOf<String>() }
    var deleteConfirmSelectedDialog by remember { mutableStateOf(false) }
    var deleteConfirmShowDialog by remember { mutableStateOf(false) }
    var swipeToDeleteEpisode by remember { mutableStateOf<DownloadedEpisodeEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (showEpisodes.isNotEmpty()) {
            val firstItem = showEpisodes.first()
            
            // Blurred Background Header matching PodcastInfoScreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedHeaderHeight + 200.dp)
                    .graphicsLayer {
                        translationY = -scrollOffset * 0.5f
                        alpha = (1f - scrollFraction).coerceIn(0f, 1f)
                    }
            ) {
                AsyncImage(
                    model = firstItem.podcastImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.18f)
                        .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = scrollFraction))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedEpisodeIds.clear()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedEpisodeIds.size} Selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (selectedEpisodeIds.size == showEpisodes.size) {
                                selectedEpisodeIds.clear()
                            } else {
                                selectedEpisodeIds.clear()
                                selectedEpisodeIds.addAll(showEpisodes.map { it.episodeId })
                            }
                        }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(
                            onClick = { deleteConfirmSelectedDialog = true },
                            enabled = selectedEpisodeIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (selectedEpisodeIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = podcastTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = titleAlpha }
                        )
                        if (showEpisodes.isNotEmpty()) {
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(Icons.Rounded.Checklist, contentDescription = "Select episodes")
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (showEpisodes.isNotEmpty()) {
                    val firstItem = showEpisodes.first()

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = if (isPlayerActive) 150.dp else 80.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Compact, Space-Efficient Left-Aligned Header Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = firstItem.podcastImageUrl,
                                    contentDescription = podcastTitle,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentScale = ContentScale.Crop
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = podcastTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${showEpisodes.size} ${if (showEpisodes.size == 1) "episode" else "episodes"} • ${formatSize(totalSizeBytes)} saved",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Play All Pill
                                        Row(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .expressiveClickable(
                                                    shape = RoundedCornerShape(50),
                                                    onClick = {
                                                        val episodes = showEpisodes.map { it.toEpisode() }
                                                        val podcast = firstItem.toPodcast()
                                                        viewModel.playQueue(episodes, podcast)
                                                    }
                                                )
                                                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                                                .padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Play All",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }

                                        // Delete All Pill
                                        Row(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .expressiveClickable(
                                                    shape = RoundedCornerShape(50),
                                                    onClick = { deleteConfirmShowDialog = true }
                                                )
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(50))
                                                .background(Color.Transparent)
                                                .padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Delete All",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }

                        // Episode List with Swipe to Delete
                        items(items = sortedEpisodes, key = { it.episodeId }) { download ->
                            val isSelected = selectedEpisodeIds.contains(download.episodeId)
                            var showMenu by remember { mutableStateOf(false) }

                            val dismissScope = rememberCoroutineScope()
                            val dismissState = rememberSwipeToDismissBoxState()

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                onDismiss = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        swipeToDeleteEpisode = download
                                    }
                                    // Keep the row; confirm dialog owns deletion (same as former veto).
                                    dismissScope.launch { dismissState.reset() }
                                },
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .expressiveClickable(
                                                shape = RoundedCornerShape(12.dp),
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        if (selectedEpisodeIds.contains(download.episodeId)) {
                                                            selectedEpisodeIds.remove(download.episodeId)
                                                        } else {
                                                            selectedEpisodeIds.add(download.episodeId)
                                                        }
                                                    } else {
                                                        val episode = download.toEpisode()
                                                        val podcast = download.toPodcast()
                                                        viewModel.genericEpisodesClickedCount++
                                                        onEpisodeClick(episode, podcast)
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        if (isSelectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    if (isSelected) {
                                                        selectedEpisodeIds.remove(download.episodeId)
                                                    } else {
                                                        selectedEpisodeIds.add(download.episodeId)
                                                    }
                                                }
                                            )
                                        }

                                        // Cover art with download status overlay badges
                                        Box(modifier = Modifier.size(46.dp)) {
                                            AsyncImage(
                                                model = download.episodeImageUrl ?: download.podcastImageUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )

                                            when (download.status) {
                                                DownloadedEpisodeEntity.STATUS_DOWNLOADING -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.4f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                DownloadedEpisodeEntity.STATUS_QUEUED -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.4f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Queue,
                                                            contentDescription = "Queued",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                DownloadedEpisodeEntity.STATUS_FAILED -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.4f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Info,
                                                            contentDescription = "Failed",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = download.episodeTitle,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            val relativeDate = formatRelativeDate(download.publishedDate)
                                            val durationText = if (download.durationMs > 0) "${(download.durationMs / 1000) / 60}m" else null
                                            val sizeText = formatSize(download.sizeBytes)
                                            val subText = listOfNotNull(
                                                relativeDate.takeIf { it.isNotEmpty() },
                                                durationText,
                                                sizeText
                                            ).joinToString(" • ")
                                            
                                            Text(
                                                text = subText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            // Linear progress loader under text if actively downloading
                                            if (download.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.dp)
                                                        .clip(RoundedCornerShape(100.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                                )
                                            }
                                        }

                                        if (!isSelectionMode) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.playQueue(listOf(download.toEpisode()), download.toPodcast())
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayArrow,
                                                        contentDescription = "Play episode",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Box {
                                                    IconButton(
                                                        onClick = { showMenu = true },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.MoreVert,
                                                            contentDescription = "Options",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = showMenu,
                                                        onDismissRequest = { showMenu = false },
                                                        shape = RoundedCornerShape(20.dp),
                                                        offset = DpOffset(x = (-12).dp, y = 4.dp)
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Play") },
                                                            onClick = {
                                                                showMenu = false
                                                                viewModel.playQueue(listOf(download.toEpisode()), download.toPodcast())
                                                            },
                                                            leadingIcon = {
                                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Play Next") },
                                                            onClick = {
                                                                showMenu = false
                                                                viewModel.addToQueueNext(download.toEpisode(), download.toPodcast())
                                                            },
                                                            leadingIcon = {
                                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Add to Queue") },
                                                            onClick = {
                                                                showMenu = false
                                                                viewModel.addToQueue(download.toEpisode(), download.toPodcast())
                                                            },
                                                            leadingIcon = {
                                                                Icon(Icons.Rounded.Queue, contentDescription = null)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Remove Download") },
                                                            onClick = {
                                                                showMenu = false
                                                                viewModel.removeDownload(download.episodeId)
                                                            },
                                                            leadingIcon = {
                                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog for swipe-to-delete confirmation
        if (swipeToDeleteEpisode != null) {
            val episode = swipeToDeleteEpisode!!
            AlertDialog(
                onDismissRequest = {
                    swipeToDeleteEpisode = null
                },
                title = { Text("Delete Download?") },
                text = { Text("Remove downloaded episode '${episode.episodeTitle}'?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeDownload(episode.episodeId)
                        swipeToDeleteEpisode = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        swipeToDeleteEpisode = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog for deleting selected
        if (deleteConfirmSelectedDialog) {
            AlertDialog(
                onDismissRequest = { deleteConfirmSelectedDialog = false },
                title = { Text("Delete Downloads?") },
                text = { Text("Remove ${selectedEpisodeIds.size} selected downloaded episodes?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeMultipleDownloads(selectedEpisodeIds.toList())
                        selectedEpisodeIds.clear()
                        isSelectionMode = false
                        deleteConfirmSelectedDialog = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmSelectedDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog for deleting whole show
        if (deleteConfirmShowDialog) {
            AlertDialog(
                onDismissRequest = { deleteConfirmShowDialog = false },
                title = { Text("Delete Show Downloads?") },
                text = { Text("Remove all downloaded episodes for '$podcastTitle'?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeMultipleDownloads(showEpisodes.map { it.episodeId })
                        deleteConfirmShowDialog = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmShowDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PodcastListShowCard(
    group: PodcastGroup,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {}
) {
    val title = group.podcastName
    val imageUrl = group.podcastImageUrl
    val downloadCount = group.episodes.size
    val totalSizeBytes = group.totalSizeBytes
    val latestDownloadedAt = group.latestDownloadedAt
    Row(
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectedChange(!isSelected)
                    } else {
                        onClick()
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectedChange
            )
        }

        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp)
                )
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$downloadCount ${if (downloadCount == 1) "episode" else "episodes"} • ${formatSize(totalSizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val latestRelative = formatRelativeDate(latestDownloadedAt / 1000L)
            if (latestRelative.isNotEmpty()) {
                Text(
                    text = "Latest: $latestRelative",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        if (!isSelectionMode) {
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play Show Downloads",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatRelativeDate(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSeconds
    return when {
        diff < 0 -> "Just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        diff < 2592000 -> "${diff / 604800}w ago"
        diff < 31536000 -> "${diff / 2592000}mo ago"
        else -> "${diff / 31536000}y ago"
    }
}

@Composable
fun SquigglyProgressLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 3.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squiggly")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val path = Path()

        // Fits ~7 full waves for a tighter, denser squiggly waveform
        val wavelength = width / 7.0f
        // Amplitude is 25% of the height to keep it clean and bounded
        val amplitude = height * 0.25f

        path.moveTo(0f, midY)
        for (x in 0..width.toInt()) {
            val angle = (x.toFloat() / wavelength) * (2f * Math.PI.toFloat()) - phaseShift
            val y = midY + amplitude * kotlin.math.sin(angle)
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun SmartDownloadsDashboardCard(
    userPrefs: cx.aswin.boxlore.core.data.UserPreferencesRepository,
    downloads: List<cx.aswin.boxlore.core.data.database.DownloadedEpisodeEntity>,
    onSettingsClick: () -> Unit,
    onSyncNow: () -> Unit,
    isSyncing: Boolean = false
) {
    val isEnabled by userPrefs.smartDownloadsEnabledStream.collectAsState(initial = false)
    val maxEpisodes by userPrefs.smartDownloadsMaxEpisodesStream.collectAsState(initial = 10)
    val storageBudget by userPrefs.smartDownloadsStorageBudgetStream.collectAsState(initial = 250L)
    val lastSyncTime by userPrefs.smartDownloadsLastSyncTimeStream.collectAsState(initial = 0L)
    
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "rotation"
    )

    // Count how many completed smart downloaded episodes we currently have
    val smartDownloadedCount = remember(downloads) {
        downloads.count { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
    }
    
    // Count how many smart downloads are currently in-progress (downloading)
    val smartDownloadingCount = remember(downloads) {
        downloads.count { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING }
    }
    
    // Calculate total storage used by completed smart downloads in MB
    val smartDownloadedSizeMb = remember(downloads) {
        downloads.filter { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }.sumOf { it.sizeBytes } / (1024 * 1024)
    }

    if (!isEnabled) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .expressiveClickable(
                    shape = RoundedCornerShape(16.dp),
                    onClick = onSettingsClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Enable Smart Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        return
    }

    val statusText = remember(smartDownloadingCount, isSyncing) {
        if (smartDownloadingCount > 0) {
            "Syncing candidates"
        } else if (isSyncing) {
            "Checking for updates"
        } else {
            "Offline queue ready"
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row (Clickable to collapse/expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveClickable(
                        shape = RoundedCornerShape(12.dp),
                        onClick = { isExpanded = !isExpanded }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Downloads",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isExpanded) {
                                "Automated offline library"
                            } else {
                                "$statusText • $smartDownloadedCount/$maxEpisodes eps (${smartDownloadedSizeMb} MB)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!isExpanded && (smartDownloadingCount > 0 || isSyncing)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (!isExpanded && (smartDownloadingCount > 0 || isSyncing)) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isExpanded) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Smart Downloads Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (isSyncing || smartDownloadingCount > 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(rotationZ = rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded body content
            if (isExpanded) {
                // Sync Status & Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (smartDownloadingCount > 0 || isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Last sync: " + if (lastSyncTime <= 0L) "Never" else {
                                val diff = System.currentTimeMillis() - lastSyncTime
                                when {
                                    diff < 60_000 -> "Just now"
                                    diff < 3600_000 -> "${diff / 60_000}m ago"
                                    diff < 86400_000 -> "${diff / 3600_000}h ago"
                                    else -> java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTime))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.expressiveClickable(
                            shape = RoundedCornerShape(100.dp),
                            onClick = onSyncNow,
                            enabled = !isSyncing
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSyncing) "Syncing" else "Sync Now",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Squiggly Wave Progress Loader (Displays during active network requests or sync tasks)
                if (isSyncing || smartDownloadingCount > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SquigglyProgressLoader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = if (smartDownloadingCount > 0) "$smartDownloadingCount episode(s) downloading in background" else "Syncing with cloud endpoints...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Linear Progress Metrics matching limits & storage budget
                val countProgress = ((smartDownloadedCount + smartDownloadingCount).toFloat() / maxEpisodes.toFloat()).coerceIn(0f, 1f)
                val storageProgress = if (storageBudget <= 0L) 0f else {
                    (smartDownloadedSizeMb.toFloat() / storageBudget.toFloat()).coerceIn(0f, 1f)
                }
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Count Progress
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mixtape Episodes ($smartDownloadedCount / $maxEpisodes)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(countProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { countProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(100.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        )
                    }

                    // Storage Limits Progress
                    if (storageBudget > 0L) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Storage Budget ($smartDownloadedSizeMb MB / $storageBudget MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(storageProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { storageProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(100.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Storage Used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$smartDownloadedSizeMb MB (Unlimited)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
