package cx.aswin.boxlore.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerHeight
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerNavGap
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHeight
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.library.history.DateHeaderRow
import cx.aswin.boxlore.feature.library.history.HistoryActivityGraph
import cx.aswin.boxlore.feature.library.history.HistoryDayChips
import cx.aswin.boxlore.feature.library.history.HistoryInsightCarousel
import cx.aswin.boxlore.feature.library.history.HistoryPeriodSelector
import cx.aswin.boxlore.feature.library.history.HistoryStatusFilterSelector
import cx.aswin.boxlore.feature.library.history.HistoryTimelineItem
import cx.aswin.boxlore.feature.library.history.HistoryTimeOfDayGraph
import cx.aswin.boxlore.feature.library.history.ListeningTimeCard
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (ListeningHistoryItem) -> Unit,
    onExploreClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showTrackingNotice by viewModel.showTrackingNotice.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleStyle =
        lerp(
            start = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            stop = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            fraction = scrollBehavior.state.collapsedFraction,
        )
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val itemRemovedMessage = stringResource(R.string.history_item_removed)
    val undoLabel = stringResource(R.string.history_undo)

    DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryUiEvent.ShowUndoDelete -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = itemRemovedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoRemoval(event.removal)
                    }
                }
                HistoryUiEvent.HistoryCleared -> Unit
            }
        }
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = titleStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (uiState is HistoryUiState.Success) {
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.history_clear_all),
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_clear_all)) },
                                    onClick = {
                                        showOverflow = false
                                        showClearDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.ClearAll, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val bottomInset =
            AppMiniPlayerHeight + AppMiniPlayerNavGap + AppNavigationBarHeight +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        when (val state = uiState) {
            HistoryUiState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            HistoryUiState.Empty -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.history_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.history_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(onClick = onExploreClick) {
                        Text(stringResource(R.string.history_empty_explore))
                    }
                }
            }
            is HistoryUiState.Success -> {
                val success = state.state
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = bottomInset + 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        HistoryPeriodSelector(
                            selected = success.selectedPeriod,
                            onSelect = viewModel::setPeriod,
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ListeningTimeCard(insights = success.insights)
                            HistoryInsightCarousel(insights = success.insights)
                        }
                    }
                    item {
                        HistoryActivityGraph(dailyActivity = success.insights.dailyActivity)
                    }
                    item {
                        HistoryTimeOfDayGraph(insights = success.insights)
                    }
                    item {
                        HistoryDayChips(
                            activeDays = success.activeDays,
                            selectedDate = success.selectedFilterDate,
                            onSelectDate = viewModel::setFilterDate,
                            onPickOlderDate = { showDatePicker = true },
                        )
                    }
                    item {
                        HistoryStatusFilterSelector(
                            selected = success.selectedHistoryFilter,
                            onSelect = viewModel::setHistoryFilter,
                        )
                    }
                    if (success.timelineEmpty) {
                        item {
                            Text(
                                text = stringResource(R.string.history_timeline_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    } else {
                        success.groupedHistory.forEach { (date, itemsForDate) ->
                            item(key = "header-$date") {
                                DateHeaderRow(
                                    date = date,
                                    isExpanded = date in success.expandedDates,
                                    onClick = { viewModel.toggleDateExpansion(date) },
                                )
                            }
                            item(key = "group-$date") {
                                AnimatedVisibility(
                                    visible = date in success.expandedDates,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        itemsForDate.forEach { item ->
                                            key(item.episodeId) {
                                                HistoryTimelineItem(
                                                    item = item,
                                                    onClick = {
                                                        viewModel.episodesClickedCount++
                                                        onEpisodeClick(item)
                                                    },
                                                    onDelete = {
                                                        viewModel.removeHistoryItem(item.episodeId)
                                                    },
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

    if (showTrackingNotice) {
        AlertDialog(
            onDismissRequest = viewModel::dismissTrackingNotice,
            title = { Text(stringResource(R.string.history_tracking_notice_title)) },
            text = { Text(stringResource(R.string.history_tracking_notice_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissTrackingNotice) {
                    Text(stringResource(R.string.history_tracking_notice_confirm))
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllHistory()
                    },
                ) {
                    Text(stringResource(R.string.history_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date =
                                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            viewModel.setFilterDate(date)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.history_pick_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
