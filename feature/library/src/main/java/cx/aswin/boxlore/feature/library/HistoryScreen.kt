package cx.aswin.boxlore.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerHeight
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerNavGap
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHeight
import cx.aswin.boxlore.feature.library.history.ActivityCalendarStrip
import cx.aswin.boxlore.feature.library.history.CalendarInsightBanner
import cx.aswin.boxlore.feature.library.history.DateHeaderRow
import cx.aswin.boxlore.feature.library.history.HabitsStatsCard
import cx.aswin.boxlore.feature.library.history.OverviewStatsCard
import cx.aswin.boxlore.feature.library.history.SwipeToDeleteHistoryItem
import cx.aswin.boxlore.feature.library.history.TopShowStatsCard

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (ListeningHistoryEntity) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleStyle = lerp(
        start = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        stop = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        fraction = scrollBehavior.state.collapsedFraction,
    )
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.trackScreenExit()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.onScreenResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHistoryViewed("library_hub_card")
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Listening History",
                        style = titleStyle,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is HistoryUiState.Success) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Rounded.ClearAll, contentDescription = "Clear All")
                        }

                        if (showClearDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearDialog = false },
                                title = { Text("Clear History") },
                                text = { Text("Are you sure you want to clear your entire listening history? This cannot be undone.") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showClearDialog = false
                                            viewModel.clearAllHistory()
                                        }
                                    ) {
                                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is HistoryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is HistoryUiState.Empty -> {
                    ExpressiveSolarSystemEmptyState(
                        title = "No Listening History",
                        description = "Jump into a podcast and your history will magically appear here.",
                        icon = Icons.Rounded.History,
                        actionText = "Go Explore",
                        onExploreClick = onBack
                    )
                }
                is HistoryUiState.Success -> {
                    val pagerState = rememberPagerState(initialPage = 0) { 3 }
                    val systemNavBarHeight = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                    // Clear navbar + mini-player chrome so the last items can scroll past.
                    val listBottomPadding =
                        AppNavigationBarHeight +
                            AppMiniPlayerHeight +
                            AppMiniPlayerNavGap +
                            systemNavBarHeight +
                            24.dp
                    LazyColumn(
                        contentPadding = PaddingValues(
                            bottom = listBottomPadding,
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Stats Card Carousel
                        item {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) { page ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                ) {
                                    when (page) {
                                        0 -> OverviewStatsCard(stats = state.stats)
                                        1 -> HabitsStatsCard(stats = state.stats)
                                        2 -> TopShowStatsCard(stats = state.stats)
                                    }
                                }
                            }
                            
                            // Pager indicator
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(3) { iteration ->
                                    val color = if (pagerState.currentPage == iteration) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .size(6.dp)
                                    )
                                }
                            }
                        }

                        // Activity Calendar Strip
                        item {
                            ActivityCalendarStrip(
                                activeDays = state.stats.activeDays,
                                selectedDate = state.selectedFilterDate,
                                onDateSelected = { viewModel.setFilterDate(it) }
                            )
                        }

                        // Contextual Calendar Insights Banner
                        item {
                            CalendarInsightBanner(
                                stats = state.stats,
                                selectedDate = state.selectedFilterDate,
                                groupedHistory = state.groupedHistory,
                                onClearFilter = { viewModel.setFilterDate(null) }
                            )
                        }

                        // Status Filter Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = state.selectedHistoryFilter == HistoryFilter.ALL,
                                    onClick = { viewModel.setHistoryFilter(HistoryFilter.ALL) },
                                    label = { Text("All") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = state.selectedHistoryFilter == HistoryFilter.ALL,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                                FilterChip(
                                    selected = state.selectedHistoryFilter == HistoryFilter.IN_PROGRESS,
                                    onClick = { viewModel.setHistoryFilter(HistoryFilter.IN_PROGRESS) },
                                    label = { Text("In Progress") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = state.selectedHistoryFilter == HistoryFilter.IN_PROGRESS,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                                FilterChip(
                                    selected = state.selectedHistoryFilter == HistoryFilter.COMPLETED,
                                    onClick = { viewModel.setHistoryFilter(HistoryFilter.COMPLETED) },
                                    label = { Text("Completed") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = state.selectedHistoryFilter == HistoryFilter.COMPLETED,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                        }

                        // Empty Filtered State
                        if (state.groupedHistory.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val emptyTitle = when {
                                            state.selectedFilterDate != null -> "No activity on this day"
                                            state.selectedHistoryFilter == HistoryFilter.IN_PROGRESS -> "No episodes in progress"
                                            state.selectedHistoryFilter == HistoryFilter.COMPLETED -> "No completed episodes"
                                            else -> "No listening history found"
                                        }
                                        val emptyDesc = when {
                                            state.selectedFilterDate != null -> "Select another date or reset the filter."
                                            state.selectedHistoryFilter == HistoryFilter.IN_PROGRESS -> "Episodes you start listening to will appear here."
                                            state.selectedHistoryFilter == HistoryFilter.COMPLETED -> "Episodes you finish listening to will appear here."
                                            else -> "Start playing episodes to build your history."
                                        }
                                        Text(
                                            emptyTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            emptyDesc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Chronological Timeline List
                        state.groupedHistory.forEach { (date, episodes) ->
                            val isExpanded = state.expandedDates.contains(date)

                            stickyHeader(key = date.toString()) {
                                DateHeaderRow(
                                    date = date,
                                    isExpanded = isExpanded,
                                    onClick = { viewModel.toggleDateExpansion(date) }
                                )
                            }

                            item(key = "anim_${date.toString()}") {
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(animationSpec = tween(400)),
                                    exit = shrinkVertically(animationSpec = tween(400))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        episodes.forEach { entity ->
                                            key(entity.episodeId) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(IntrinsicSize.Min),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    // Timeline vertical connector line
                                                    Box(
                                                        modifier = Modifier
                                                            .width(24.dp)
                                                            .fillMaxHeight(),
                                                        contentAlignment = Alignment.TopCenter
                                                    ) {
                                                        VerticalDivider(
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                                            modifier = Modifier
                                                                .width(2.dp)
                                                                .fillMaxHeight()
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(top = 28.dp)
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.secondary)
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(bottom = 8.dp)
                                                    ) {
                                                        SwipeToDeleteHistoryItem(
                                                            entity = entity,
                                                            onDelete = { viewModel.removeHistoryItem(entity.episodeId) },
                                                            onClick = {
                                                                viewModel.episodesClickedCount++
                                                                onEpisodeClick(entity)
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
    }
}
