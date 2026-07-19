package cx.aswin.boxlore.feature.library.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.LibraryUiState
import cx.aswin.boxlore.feature.library.LibraryViewModel
import cx.aswin.boxlore.feature.library.ShowSortOrder
import kotlinx.coroutines.launch

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
