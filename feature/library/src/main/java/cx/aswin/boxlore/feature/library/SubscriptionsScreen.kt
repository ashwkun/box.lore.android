package cx.aswin.boxlore.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.subscriptions.ExpressiveTabSwitcher
import cx.aswin.boxlore.feature.library.subscriptions.LatestTabContent
import cx.aswin.boxlore.feature.library.subscriptions.ShowsTabContent
import kotlinx.coroutines.launch

val LocalLastSeenEpisodes = compositionLocalOf<Map<String, String>> { emptyMap() }

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
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsViewed(
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
    val hasSubscribedPodcasts = successState != null && successState.subscribedPodcasts.isNotEmpty()

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
                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsLayoutToggled(isGridView)
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
                                            val currentSort = successState.currentSort
                                            DropdownMenuItem(
                                                text = { Text("Smart Sort") },
                                                onClick = {
                                                    viewModel.setSubscriptionSort(SubscriptionSort.SmartRank)
                                                    showSortMenu = false
                                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("smart_sort", "shows")
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
                                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("recently_updated", "shows")
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
                                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("alphabetical", "shows")
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
                                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("most_listened", "shows")
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
                                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("smart_sort", "latest")
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
                                                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSortChanged("chronological", "latest")
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
                                    scoreEpisodes = viewModel::scoreLatestEpisodes,
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
