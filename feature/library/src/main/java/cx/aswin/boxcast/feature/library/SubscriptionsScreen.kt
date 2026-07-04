package cx.aswin.boxcast.feature.library

import cx.aswin.boxcast.core.designsystem.components.optimizedImageUrl
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxcast.core.data.PodcastScoring
import cx.aswin.boxcast.core.data.toScorable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.runtime.saveable.rememberSaveable
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.EpisodeStatus
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.isLatestEpisodeNew
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val LocalLastSeenEpisodes = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * Unified Subscriptions screen with M3 Expressive tab switcher.
 * Tab 0: "Shows" — subscription podcast list with play chips
 * Tab 1: "Latest" — vertical list of latest episodes from all subscriptions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onPodcastClick: (String) -> Unit,
    onExploreClick: () -> Unit,
    onPlayEpisode: ((Episode, Podcast) -> Unit)? = null,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)? = null,
    onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)? = null,
    isPlayerActive: Boolean = false,
    initialTab: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = initialTab) { 2 }
    val lastSeenEpisodes by viewModel.lastSeenEpisodes.collectAsStateWithLifecycle()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLastSeenEpisodes provides lastSeenEpisodes
    ) {
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val useSmartRank by viewModel.useSmartRank.collectAsStateWithLifecycle()
    val hideCompletedInSubs by viewModel.hideCompletedInSubs.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = isSearchActive) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    val isScrolled = scrollBehavior.state.overlappedFraction > 0.01f || scrollBehavior.state.collapsedFraction > 0.01f

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (isSearchActive) {
                    viewModel.subDidSearch = true
                    viewModel.subFinalSearchQuery = searchQuery
                }
                viewModel.trackSubscriptionsExit()
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
        val initialTabName = if (initialTab == 0) "shows" else "latest"
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsViewed(
            sourceEntryPoint = "library_hub_card", // From main Library screen
            initialTab = initialTabName
        )
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != initialTab) {
            viewModel.subTabSwitchesCount++
        }
    }
    val headerBgColor by animateColorAsState(
        targetValue = if (isScrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "headerBg"
    )

    val successState = uiState as? LibraryUiState.Success
    val hasSubscribedPodcasts = successState?.subscribedPodcasts?.isNotEmpty() == true

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBgColor)
            ) {
                if (isSearchActive) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text("Search shows...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        navigationIcon = {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close Search")
                            }
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                }
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Subscriptions",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (pagerState.currentPage == 0 && hasSubscribedPodcasts) {
                                IconButton(onClick = {
                                    isGridView = !isGridView
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsLayoutToggled(isGridView)
                                }) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = if (isGridView) "List View" else "Grid View"
                                    )
                                }
                            }
                            if (hasSubscribedPodcasts) {
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        shape = RoundedCornerShape(20.dp),
                                        offset = DpOffset(x = (-12).dp, y = 4.dp)
                                    ) {
                                        if (pagerState.currentPage == 0) {
                                            val currentSort = successState?.currentSort ?: SubscriptionSort.SmartRank
                                            DropdownMenuItem(
                                                text = { Text("Smart Sort") },
                                                onClick = {
                                                    viewModel.setSubscriptionSort(SubscriptionSort.SmartRank)
                                                    showSortMenu = false
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("smart_sort", "shows")
                                                },
                                                trailingIcon = {
                                                    if (currentSort == SubscriptionSort.SmartRank) {
                                                        Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Recently Updated") },
                                                onClick = {
                                                    viewModel.setSubscriptionSort(SubscriptionSort.RecentlyUpdated)
                                                    showSortMenu = false
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("recently_updated", "shows")
                                                },
                                                trailingIcon = {
                                                    if (currentSort == SubscriptionSort.RecentlyUpdated) {
                                                        Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("A-Z") },
                                                onClick = {
                                                    viewModel.setSubscriptionSort(SubscriptionSort.Alphabetical)
                                                    showSortMenu = false
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("alphabetical", "shows")
                                                },
                                                trailingIcon = {
                                                    if (currentSort == SubscriptionSort.Alphabetical) {
                                                        Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Most Listened") },
                                                onClick = {
                                                    viewModel.setSubscriptionSort(SubscriptionSort.MostListened)
                                                    showSortMenu = false
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("most_listened", "shows")
                                                },
                                                trailingIcon = {
                                                    if (currentSort == SubscriptionSort.MostListened) {
                                                        Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                                    }
                                                }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Smart Sort") },
                                                onClick = {
                                                    viewModel.setUseSmartRank(true)
                                                    showSortMenu = false
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("smart_sort", "latest")
                                                },
                                                trailingIcon = {
                                                    if (useSmartRank) {
                                                        Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Chronological") },
                                                onClick = {
                                                    viewModel.setUseSmartRank(false)
                                                    showSortMenu = false
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("chronological", "latest")
                                                },
                                                trailingIcon = {
                                                    if (!useSmartRank) {
                                                        Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }

                // Expressive Tab Switcher
                val latestCount = successState?.subscribedPodcasts?.count { it.latestEpisode != null } ?: 0

                ExpressiveTabSwitcher(
                    tabs = listOf("Shows", "New Episodes"),
                    selectedIndex = pagerState.currentPage,
                    badge = if (latestCount > 0) mapOf(1 to latestCount) else emptyMap(),
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).imePadding()) {
            when (uiState) {
                is LibraryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error loading subscriptions")
                    }
                }
                is LibraryUiState.Success -> {
                    val allPodcasts = (uiState as LibraryUiState.Success).subscribedPodcasts
                    val podcasts = if (searchQuery.isBlank()) allPodcasts else {
                        allPodcasts.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> ShowsTabContent(
                                podcasts = podcasts,
                                onExploreClick = onExploreClick,
                                onPodcastClick = {
                                    viewModel.subPodcastsClickedCount++
                                    onPodcastClick(it)
                                },
                                isGridView = isGridView
                            )
                            1 -> {
                                val latestPodcasts = if (hideCompletedInSubs) {
                                    podcasts.filter { it.episodeStatus != EpisodeStatus.COMPLETED }
                                } else {
                                    podcasts
                                }
                                LatestTabContent(
                                    podcasts = latestPodcasts,
                                    allHistory = (uiState as LibraryUiState.Success).allHistory,
                                    useSmartRank = useSmartRank,
                                    onExploreClick = onExploreClick,
                                    onEpisodeClick = { ep, pod, entry ->
                                        viewModel.subEpisodesClickedCount++
                                        onEpisodeClick?.invoke(ep, pod, entry)
                                    },
                                    onPlayEpisode = onPlayEpisode,
                                    onPlayEpisodes = onPlayEpisodes,
                                    isPlayerActive = isPlayerActive
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

// ─── M3 Expressive Tab Switcher ─────────────────────────────────────────────

@Composable
private fun ExpressiveTabSwitcher(
    tabs: List<String>,
    selectedIndex: Int,
    badge: Map<Int, Int> = emptyMap(),
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Transparent)
            .padding(2.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = 0.6f,
                stiffness = 400f
            ),
            label = "indicatorOffset"
        )
        
        // Bouncy Sliding Indicator (More compact)
        Surface(
            modifier = Modifier
                .width(tabWidth)
                .height(36.dp)
                .offset(x = indicatorOffset),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {}
        
        // Tab Content
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                TabItemContent(
                    index = index,
                    label = label,
                    isSelected = index == selectedIndex,
                    badgeCount = badge[index],
                    onTabSelected = onTabSelected
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabItemContent(
    index: Int,
    label: String,
    isSelected: Boolean,
    badgeCount: Int?,
    onTabSelected: (Int) -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabText"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onTabSelected(index) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
            if (badgeCount != null && badgeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Badge(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                  else MaterialTheme.colorScheme.surface
                ) {
                    Text("$badgeCount")
                }
            }
        }
    }
}

// ─── Tab Contents ────────────────────────────────────────────────────────────

@Composable
private fun SubscriptionSortChips(
    currentSort: SubscriptionSort,
    onSortChange: (SubscriptionSort) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = currentSort == SubscriptionSort.SmartRank,
                onClick = { onSortChange(SubscriptionSort.SmartRank) },
                label = { Text("Smart Rank") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentSort == SubscriptionSort.SmartRank,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        item {
            FilterChip(
                selected = currentSort == SubscriptionSort.RecentlyUpdated,
                onClick = { onSortChange(SubscriptionSort.RecentlyUpdated) },
                label = { Text("Recently Updated") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentSort == SubscriptionSort.RecentlyUpdated,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        item {
            FilterChip(
                selected = currentSort == SubscriptionSort.Alphabetical,
                onClick = { onSortChange(SubscriptionSort.Alphabetical) },
                label = { Text("A-Z") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentSort == SubscriptionSort.Alphabetical,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        item {
            FilterChip(
                selected = currentSort == SubscriptionSort.MostListened,
                onClick = { onSortChange(SubscriptionSort.MostListened) },
                label = { Text("Most Listened") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentSort == SubscriptionSort.MostListened,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun SubscriptionGenreChips(
    selectedGenre: String,
    onGenreChange: (String) -> Unit,
    distinctGenres: List<String>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedGenre == "All",
                onClick = { onGenreChange("All") },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedGenre == "All",
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        items(distinctGenres) { genre ->
            FilterChip(
                selected = selectedGenre == genre,
                onClick = { onGenreChange(genre) },
                label = { Text(genre) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedGenre == genre,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ShowsTabContent(
    podcasts: List<Podcast>,
    onExploreClick: () -> Unit,
    onPodcastClick: (String) -> Unit,
    isGridView: Boolean
) {
    if (podcasts.isEmpty()) {
        ExpressiveSolarSystemEmptyState(
            title = "No Subscriptions Yet",
            description = "Follow your favorite podcasts to see them here.",
            actionText = "Find Podcasts",
            onExploreClick = onExploreClick
        )
    } else {
        val distinctGenres = remember(podcasts) { extractDistinctGenres(podcasts) }
        var selectedGenre by rememberSaveable { mutableStateOf("All") }
        val filteredPodcasts = remember(podcasts, selectedGenre) { filterPodcastsByGenre(podcasts, selectedGenre) }
        val distinctPodcasts = remember(filteredPodcasts) { filteredPodcasts.distinctBy { it.id } }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        SubscriptionGenreChips(
                            selectedGenre = selectedGenre,
                            onGenreChange = {
                                selectedGenre = it
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(it, "shows")
                            },
                            distinctGenres = distinctGenres
                        )
                    }
                }

                items(items = distinctPodcasts, key = { it.id }) { podcast ->
                    val lastSeenEpisodes = LocalLastSeenEpisodes.current
                    SubscriptionGridCard(
                        podcast = podcast,
                        lastSeenId = lastSeenEpisodes[podcast.id],
                        onClick = { onPodcastClick(podcast.id) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        SubscriptionGenreChips(
                            selectedGenre = selectedGenre,
                            onGenreChange = {
                                selectedGenre = it
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(it, "shows")
                            },
                            distinctGenres = distinctGenres,
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        )
                    }
                }

                items(items = distinctPodcasts, key = { it.id }) { podcast ->
                    SubscriptionListRow(
                        podcast = podcast,
                        onClick = { onPodcastClick(podcast.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LatestTabContent(
    podcasts: List<Podcast>,
    allHistory: List<ListeningHistoryEntity>,
    useSmartRank: Boolean,
    onExploreClick: () -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    onPlayEpisode: ((Episode, Podcast) -> Unit)?,
    onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)? = null,
    isPlayerActive: Boolean = false
) {
    val episodePodcasts = remember(podcasts) {
        podcasts.filter { it.latestEpisode != null }
    }

    if (episodePodcasts.isEmpty()) {
        ExpressiveSolarSystemEmptyState(
            title = "No New Episodes",
            description = "You're all caught up! Explore for more content.",
            actionText = "Discover Shows",
            onExploreClick = onExploreClick
        )
    } else {
        val distinctGenres = remember(episodePodcasts) {
            episodePodcasts.flatMap { pod ->
                pod.genre.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.equals("podcast", ignoreCase = true) }
                    .map { genre ->
                        genre.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
            }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        var selectedGenre by remember { mutableStateOf("All") }

        val filteredEpisodePodcasts = remember(episodePodcasts, selectedGenre) {
            if (selectedGenre == "All") {
                episodePodcasts
            } else {
                episodePodcasts.filter { pod ->
                    pod.genre.split(",")
                        .map { it.trim() }
                        .any { it.equals(selectedGenre, ignoreCase = true) }
                }
            }
        }

        val episodeScores = remember(filteredEpisodePodcasts, allHistory) {
            val podScoresMap = PodcastScoring.calculateScores(
                podcasts = filteredEpisodePodcasts.map { it.toScorable() },
                allHistory = allHistory
            )
            filteredEpisodePodcasts.associate { pod ->
                val latestEp = pod.latestEpisode
                val episodeRecencyBoost = if (latestEp != null) {
                    val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - latestEp.publishedDate) / 3600.0
                    500.0 / (1.0 + hoursSinceRelease.coerceAtLeast(0.0) / 24.0)
                } else {
                    0.0
                }
                val podScore = podScoresMap[pod.id] ?: 0.0
                pod.id to (podScore + episodeRecencyBoost)
            }
        }

        val displayPodcasts = remember(filteredEpisodePodcasts, useSmartRank, episodeScores) {
            if (useSmartRank) {
                filteredEpisodePodcasts.sortedByDescending { episodeScores[it.id] ?: 0.0 }
            } else {
                filteredEpisodePodcasts.sortedByDescending { it.latestEpisode!!.publishedDate }
            }
        }

        val groupedEpisodes = remember(displayPodcasts, useSmartRank) {
            if (useSmartRank) {
                emptyMap()
            } else {
                displayPodcasts.groupBy { getChronologicalHeader(it.latestEpisode!!.publishedDate) }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 240.dp, top = 4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    SubscriptionGenreChips(
                        selectedGenre = selectedGenre,
                        onGenreChange = {
                            selectedGenre = it
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(it, "latest")
                        },
                        distinctGenres = distinctGenres,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                if (useSmartRank) {
                    items(items = displayPodcasts, key = { "${it.id}_latest_smart" }) { podcast ->
                        val episode = podcast.latestEpisode!!
                        LatestEpisodeRow(
                            episode = episode,
                            podcast = podcast,
                            onClick = { onEpisodeClick?.invoke(episode, podcast, "library_latest_episodes") },
                            onPlay = if (onPlayEpisode != null) {
                                { onPlayEpisode(episode, podcast) }
                            } else null
                        )
                    }
                } else {
                    groupedEpisodes.forEach { (header, podcastsInGroup) ->
                        stickyHeader {
                            DateHeader(text = header)
                        }
                        items(items = podcastsInGroup, key = { "${it.id}_latest_chrono" }) { podcast ->
                            val episode = podcast.latestEpisode!!
                            LatestEpisodeRow(
                                episode = episode,
                                podcast = podcast,
                                onClick = { onEpisodeClick?.invoke(episode, podcast, "library_latest_episodes") },
                                onPlay = if (onPlayEpisode != null) {
                                    { onPlayEpisode(episode, podcast) }
                                } else null
                            )
                        }
                    }
                }
            }

            if (displayPodcasts.isNotEmpty()) {
                PlayAllFab(
                    isPlayerActive = isPlayerActive,
                    onClick = {
                        val episodesToPlay = displayPodcasts.map { it.latestEpisode!! }
                        val firstPodcast = displayPodcasts.firstOrNull()
                        if (firstPodcast != null && onPlayEpisodes != null) {
                            onPlayEpisodes(episodesToPlay, firstPodcast)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DateHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

private fun getChronologicalHeader(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return "Older"
    val timestampMs = timestampSeconds * 1000L
    
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestampMs }
    
    val nowDay = now.clone() as Calendar
    nowDay.set(Calendar.HOUR_OF_DAY, 0)
    nowDay.set(Calendar.MINUTE, 0)
    nowDay.set(Calendar.SECOND, 0)
    nowDay.set(Calendar.MILLISECOND, 0)
    
    val timeDay = time.clone() as Calendar
    timeDay.set(Calendar.HOUR_OF_DAY, 0)
    timeDay.set(Calendar.MINUTE, 0)
    timeDay.set(Calendar.SECOND, 0)
    timeDay.set(Calendar.MILLISECOND, 0)
    
    val diffDays = (nowDay.timeInMillis - timeDay.timeInMillis) / (24 * 60 * 60 * 1000L)
    
    return when {
        diffDays == 0L -> "Today"
        diffDays == 1L -> "Yesterday"
        diffDays < 7L -> {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestampMs))
        }
        else -> {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
        }
    }
}

// ─── Row Components ──────────────────────────────────────────────────────────

@Composable
private fun SubscriptionListRow(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptimizedImage(
            url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = podcast.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = podcast.artist.takeIf { it.isNotEmpty() } ?: "Podcast",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun LatestEpisodeRow(
    episode: Episode,
    podcast: Podcast,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val status = podcast.episodeStatus
    val progress = podcast.resumeProgress ?: 0f
    val isCompleted = status == EpisodeStatus.COMPLETED
    val isInProgress = status == EpisodeStatus.IN_PROGRESS

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode artwork with status overlay
        EpisodeRowArtwork(
            episode = episode,
            podcast = podcast,
            isCompleted = isCompleted,
            isInProgress = isInProgress,
            progress = progress
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (episode.duration > 0) {
                    val h = episode.duration / 3600
                    val m = (episode.duration % 3600) / 60
                    val displayText = if (isInProgress && progress > 0f) {
                        val remaining = ((1f - progress) * episode.duration).toInt()
                        val rh = remaining / 3600
                        val rm = (remaining % 3600) / 60
                        if (rh > 0) "${rh}h ${rm}m left" else "${rm}m left"
                    } else {
                        if (h > 0) "${h}h ${m}m" else "${m}m"
                    }
                    Text(
                        text = "• $displayText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isInProgress) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = if (isInProgress) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        // Play button
        if (onPlay != null) {
            Spacer(modifier = Modifier.width(8.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val btnColor by animateColorAsState(
                targetValue = if (isPressed) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.primaryContainer,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "btnColor"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isPressed) MaterialTheme.colorScheme.onPrimary
                             else MaterialTheme.colorScheme.primary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "iconColor"
            )

            Surface(
                shape = CircleShape,
                color = btnColor,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onPlay
                    )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play episode",
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionGridCard(
    podcast: Podcast,
    lastSeenId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestEpisodeId = podcast.latestEpisode?.id
    val latestEpisodePubDate = podcast.latestEpisode?.publishedDate ?: 0L
    
    val hasRecentNew = remember(podcast.subscribedAt, latestEpisodeId, latestEpisodePubDate, lastSeenId) {
        podcast.isLatestEpisodeNew(lastSeenId)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .expressiveClickable(onClick = onClick)
    ) {
        OptimizedImage(
            url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                )
        )

        // New episode "NEW" text chip indicator (overlapping the top right corner) with a slow shimmer effect
        if (hasRecentNew) {
            NewEpisodeBadge()
        }
    }
}

@Composable
private fun EpisodeRowArtwork(
    episode: Episode,
    podcast: Podcast,
    isCompleted: Boolean,
    isInProgress: Boolean,
    progress: Float
) {
    Box(modifier = Modifier.size(64.dp)) {
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotEmpty() }
                ?: podcast.imageUrl.takeIf { it.isNotEmpty() }
                ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = episode.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        if (isCompleted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Played",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        if (isInProgress && progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                drawStopIndicator = {}
            )
        }
    }
}

private fun extractDistinctGenres(podcasts: List<Podcast>): List<String> {
    return podcasts.flatMap { pod ->
        pod.genre.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("podcast", ignoreCase = true) }
            .map { genre ->
                genre.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
}

private fun filterPodcastsByGenre(podcasts: List<Podcast>, selectedGenre: String): List<Podcast> {
    if (selectedGenre == "All") return podcasts
    return podcasts.filter { pod ->
        pod.genre.split(",")
            .map { it.trim() }
            .any { it.equals(selectedGenre, ignoreCase = true) }
    }
}
