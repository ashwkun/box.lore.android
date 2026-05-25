package cx.aswin.boxcast.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ModeNight
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

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
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryHistoryViewed("library_hub_card")
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text("Listening History", fontWeight = FontWeight.Bold) 
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
                scrollBehavior = scrollBehavior
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
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp),
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

@Composable
fun ActivityCalendarStrip(
    activeDays: Set<LocalDate>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val dates = remember(today) {
        (0..13).map { today.minusDays(it.toLong()) }.reversed()
    }
    
    val listState = rememberLazyListState()
    
    val targetDate = selectedDate ?: today
    val targetIndex = remember(dates, targetDate) {
        dates.indexOf(targetDate).coerceAtLeast(0)
    }
    
    LaunchedEffect(targetIndex) {
        listState.animateScrollToItem(targetIndex)
    }
    
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(dates) { date ->
                    val isSelected = date == selectedDate
                    val isToday = date == today
                    val hasActivity = activeDays.contains(date)
                    
                    val dayOfWeek = date.dayOfWeek.getDisplayName(
                        java.time.format.TextStyle.SHORT,
                        java.util.Locale.getDefault()
                    ).take(1)
                    val dayOfMonth = date.dayOfMonth.toString()
                    
                    val containerColor = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        hasActivity -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceContainerLowest
                    }
                    
                    val contentColor = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                        hasActivity -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                    
                    val borderStroke = if (isToday && !isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else null
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = containerColor,
                        contentColor = contentColor,
                        border = borderStroke,
                        modifier = Modifier
                            .width(46.dp)
                            .aspectRatio(0.8f)
                            .clickable {
                                if (isSelected) {
                                    onDateSelected(null)
                                } else {
                                    onDateSelected(date)
                                }
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = dayOfWeek,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dayOfMonth,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (hasActivity) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        )
                                )
                            } else {
                                Spacer(modifier = Modifier.size(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryStatsCardContainer(
    gradientColors: List<Color>,
    shapes: List<Shape>,
    shapeColors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stats_card_shapes")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.extraLarge
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.linearGradient(colors = gradientColors))
        ) {
            // Floating shapes in background
            if (shapes.size >= 1 && shapeColors.size >= 1) {
                CardFloatingShape(
                    shape = shapes[0],
                    rotation = rotation,
                    color = shapeColors[0],
                    size = 140.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 20.dp, y = (-30).dp + floatOffset.dp)
                )
            }
            if (shapes.size >= 2 && shapeColors.size >= 2) {
                CardFloatingShape(
                    shape = shapes[1],
                    rotation = -rotation * 0.5f,
                    color = shapeColors[1],
                    size = 120.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-20).dp - floatOffset.dp, y = 30.dp)
                )
            }
            if (shapes.size >= 3 && shapeColors.size >= 3) {
                CardFloatingShape(
                    shape = shapes[2],
                    rotation = rotation * 0.3f,
                    color = shapeColors[2],
                    size = 100.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 30.dp, y = 20.dp + (floatOffset * 0.5f).dp)
                )
            }

            // Content container above background shapes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .zIndex(1f)
            ) {
                content()
            }
        }
    }
}

@Composable
fun OverviewStatsCard(stats: DetailedHistoryStats) {
    val darkTheme = isSystemInDarkTheme()
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    )
    val shapeColors = if (darkTheme) {
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
        )
    }
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    val infiniteTransition = rememberInfiniteTransition(label = "streak_flame")
    val flameColor by infiniteTransition.animateColor(
        initialValue = Color.White,
        targetValue = Color(0xFFFFF176), // Light amber/yellow
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_color"
    )

    HistoryStatsCardContainer(
        gradientColors = gradientColors,
        shapes = listOf(ExpressiveShapes.Sunny, ExpressiveShapes.Flower, ExpressiveShapes.Burst),
        shapeColors = shapeColors
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Listening Time & Streak Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening Time",
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val hours = TimeUnit.MILLISECONDS.toHours(stats.totalListeningMs)
                    val mins = TimeUnit.MILLISECONDS.toMinutes(stats.totalListeningMs) % 60
                    Text(
                        text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = contentColor
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (stats.listeningStreakDays > 0) {
                    val streakBgBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF3D00),
                            Color(0xFFFF9100)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .background(brush = streakBgBrush, shape = RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Whatshot,
                                contentDescription = "Streak",
                                tint = flameColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${stats.listeningStreakDays}-Day Streak",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(
                                color = contentColor.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "0-Day Streak",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = contentColor.copy(alpha = 0.25f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Bottom Section: 3-column metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverviewMetricColumn(
                    icon = Icons.Rounded.CheckCircle,
                    iconColor = Color(0xFF00E676), // Vibrant Neon Green
                    label = "Completed",
                    value = "${stats.completedEpisodesCount}",
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricColumn(
                    icon = Icons.Rounded.Pending,
                    iconColor = Color(0xFFFFD600), // Vibrant Yellow
                    label = "In Progress",
                    value = "${stats.inProgressEpisodesCount}",
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricColumn(
                    icon = Icons.Rounded.Favorite,
                    iconColor = Color(0xFFFF1744), // Vibrant Pink-Red
                    label = "Liked",
                    value = "${stats.likedEpisodesCount}",
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun OverviewMetricColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.95f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
fun HabitsStatsCard(stats: DetailedHistoryStats) {
    val darkTheme = isSystemInDarkTheme()
    val gradientColors = listOf(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    )
    val shapeColors = if (darkTheme) {
        listOf(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
        )
    }
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer

    val vibeIcon = when (stats.peakListeningVibe) {
        "Morning Ritual" -> Icons.Rounded.LightMode
        "Midday Flow" -> Icons.Rounded.Bolt
        "Evening Unwind" -> Icons.Rounded.ModeNight
        "Night Owl" -> Icons.Rounded.Bedtime
        else -> Icons.Rounded.AccessTime
    }

    val peakHourText = if (stats.peakListeningHour >= 0) {
        val hour = stats.peakListeningHour
        val ampm = if (hour >= 12) "PM" else "AM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        "$hour12 $ampm peak"
    } else {
        "No activity"
    }

    HistoryStatsCardContainer(
        gradientColors = gradientColors,
        shapes = listOf(ExpressiveShapes.Flower, ExpressiveShapes.Cookie12, ExpressiveShapes.Burst),
        shapeColors = shapeColors
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Vibe & Peak Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening Vibe",
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stats.peakListeningVibe,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = contentColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, contentColor.copy(alpha = 0.40f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = vibeIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = peakHourText,
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Middle Section: Distribution Graph spanning full width, filling remaining vertical space
            val barValues = remember(stats.hourlyDistribution) {
                FloatArray(12) { i ->
                    stats.hourlyDistribution.getOrElse(i * 2) { 0f } + stats.hourlyDistribution.getOrElse(i * 2 + 1) { 0f }
                }
            }
            val maxVal = remember(barValues) { barValues.maxOrNull() ?: 1f }.let { if (it == 0f) 1f else it }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    barValues.forEachIndexed { index, value ->
                        val normalizedHeight = (value / maxVal).coerceIn(0.12f, 1f)
                        val isPeak = index == stats.peakListeningHour / 2
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .fillMaxHeight(normalizedHeight)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (isPeak) contentColor
                                    else contentColor.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("12 AM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text("6 AM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text("12 PM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text("6 PM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text("11 PM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TopShowStatsCard(stats: DetailedHistoryStats) {
    val darkTheme = isSystemInDarkTheme()
    val gradientColors = listOf(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
    )
    val shapeColors = if (darkTheme) {
        listOf(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        )
    }
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    HistoryStatsCardContainer(
        gradientColors = gradientColors,
        shapes = listOf(ExpressiveShapes.Burst, ExpressiveShapes.SoftBurst, ExpressiveShapes.Diamond),
        shapeColors = shapeColors
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = contentColor.copy(alpha = 0.1f),
                border = BorderStroke(1.5.dp, contentColor.copy(alpha = 0.2f)),
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (stats.topPodcastImageUrl != null) {
                    OptimizedImage(
                        url = stats.topPodcastImageUrl,
                        proxyWidth = 200,
                        contentDescription = stats.topPodcastName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Top Podcast",
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stats.topPodcastName ?: "No podcast found",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor
                )

                if (stats.topPodcastName != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = contentColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, contentColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            val playsText = if (stats.topPodcastPlayCount == 1) "1 play" else "${stats.topPodcastPlayCount} plays"
                            Text(
                                text = playsText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = contentColor
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Listen to podcasts to see your top show!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun StatsMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DateHeaderRow(
    date: LocalDate,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val dateText = when (date) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> date.format(formatter)
    }
    
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "Caret Rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .rotate(rotationAngle)
                .size(24.dp)
        )
    }
}

@Composable
fun SwipeToDeleteHistoryItem(
    entity: ListeningHistoryEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    val offsetX = remember { Animatable(0f) }
    var showDeletePill by remember { mutableStateOf(false) }
    var autoHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    val dismissThreshold = with(density) { 80.dp.toPx() }
    
    Box(modifier = Modifier.fillMaxWidth()) {
        if (showDeletePill) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(0f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            autoHideJob?.cancel()
                            onDelete()
                        }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Delete",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            autoHideJob?.cancel()
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < -dismissThreshold) {
                                    showDeletePill = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    
                                    offsetX.animateTo(
                                        targetValue = -dismissThreshold * 1.5f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    
                                    autoHideJob = coroutineScope.launch {
                                        delay(3000)
                                        showDeletePill = false
                                        offsetX.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                } else {
                                    showDeletePill = false
                                    offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                showDeletePill = false
                                offsetX.animateTo(0f, spring())
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceAtMost(0f)
                                offsetX.snapTo(newOffset)
                                
                                if (kotlin.math.abs(offsetX.value) > dismissThreshold * 0.5f && !showDeletePill) {
                                    showDeletePill = true
                                }
                                if (showDeletePill && kotlin.math.abs(offsetX.value) < dismissThreshold * 0.3f) {
                                    showDeletePill = false
                                    autoHideJob?.cancel()
                                }
                            }
                        }
                    )
                }
                .expressiveClickable(shape = MaterialTheme.shapes.medium, onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    OptimizedImage(
                        url = entity.episodeImageUrl ?: entity.podcastImageUrl,
                        proxyWidth = 150,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (entity.durationMs > 0) {
                        val progress = (entity.progressMs.toFloat() / entity.durationMs.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.episodeTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val playProgressInfo = remember(entity.progressMs, entity.durationMs, entity.isCompleted) {
                        val isComplete = entity.isCompleted || (entity.durationMs > 0 && entity.progressMs > entity.durationMs * 0.9f)
                        if (isComplete) {
                            "Completed"
                        } else if (entity.durationMs > 0) {
                            val remainingMs = entity.durationMs - entity.progressMs
                            val remainingMins = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
                            if (remainingMins > 0) "$remainingMins mins left" else "Almost done"
                        } else {
                            "In Progress"
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = entity.podcastName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Surface(
                            color = if (entity.isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = playProgressInfo,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (entity.isCompleted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun CardFloatingShape(
    shape: Shape,
    rotation: Float,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
            }
            .background(color = color, shape = shape)
    )
}

@Composable
fun CompactMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun CalendarInsightBanner(
    stats: DetailedHistoryStats,
    selectedDate: LocalDate?,
    groupedHistory: Map<LocalDate, List<ListeningHistoryEntity>>,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val isFiltered = selectedDate != null
    
    val containerColor = if (isFiltered) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    }
    
    val contentColor = if (isFiltered) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    
    val borderStroke = BorderStroke(
        width = 1.dp,
        color = if (isFiltered) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        }
    )

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Crossfade(
            targetState = selectedDate,
            label = "insight_banner_transition"
        ) { targetDate ->
            val hasFilter = targetDate != null
            val icon = if (hasFilter) {
                Icons.Rounded.PlayArrow
            } else {
                val last14Days = remember(today) { (0..13).map { today.minusDays(it.toLong()) } }
                val activeCount = last14Days.count { stats.activeDays.contains(it) }
                if (activeCount >= 5) Icons.Rounded.Whatshot else Icons.Rounded.Bolt
            }
            
            val iconColor = if (hasFilter) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                val descText = if (hasFilter) {
                    val dateStr = when (targetDate) {
                        today -> "Today"
                        today.minusDays(1) -> "Yesterday"
                        else -> targetDate!!.format(DateTimeFormatter.ofPattern("MMMM d"))
                    }
                    val episodes = groupedHistory[targetDate] ?: emptyList()
                    var dailyMs = 0L
                    episodes.forEach { entity ->
                        val isComplete = entity.isCompleted || (entity.durationMs > 0 && entity.progressMs > entity.durationMs * 0.9f)
                        dailyMs += if (isComplete && entity.durationMs > 0) entity.durationMs else entity.progressMs
                    }
                    val hours = TimeUnit.MILLISECONDS.toHours(dailyMs)
                    val mins = TimeUnit.MILLISECONDS.toMinutes(dailyMs) % 60
                    val durationStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    val epCount = episodes.size
                    "On $dateStr, you played $epCount ${if (epCount == 1) "episode" else "episodes"} for a total of $durationStr."
                } else {
                    val last14Days = remember(today) { (0..13).map { today.minusDays(it.toLong()) } }
                    val activeCount = last14Days.count { stats.activeDays.contains(it) }
                    when {
                        activeCount == 14 -> "Perfect fortnight! You listened every day for the last 14 days."
                        activeCount >= 10 -> "Incredible consistency! You listened on $activeCount of the last 14 days."
                        activeCount >= 5 -> "Great habit! You listened on $activeCount of the last 14 days."
                        activeCount >= 1 -> "You listened on $activeCount of the last 14 days recently. Keep it up!"
                        else -> "No listening history in the last 14 days. Start listening today!"
                    }
                }

                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                
                if (hasFilter) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClearFilter,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear filter",
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
