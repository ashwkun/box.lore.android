package cx.aswin.boxcast.feature.library

import cx.aswin.boxcast.core.designsystem.components.optimizedImageUrl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch

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
    initialTab: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = initialTab) { 2 }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
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
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Close Search")
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
                    LargeTopAppBar(
                        title = {
                            Text(
                                text = "Subscriptions",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }

                // Expressive Tab Switcher
                val latestCount = when (uiState) {
                    is LibraryUiState.Success -> (uiState as LibraryUiState.Success).subscribedPodcasts
                        .count { it.latestEpisode != null }
                    else -> 0
                }

                ExpressiveTabSwitcher(
                    tabs = listOf("Shows", "New Episodes"),
                    selectedIndex = pagerState.currentPage,
                    badge = if (latestCount > 0) mapOf(1 to latestCount) else emptyMap(),
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                                onPlayEpisode = onPlayEpisode
                            )
                            1 -> LatestTabContent(
                                podcasts = podcasts,
                                onExploreClick = onExploreClick,
                                onEpisodeClick = { ep, pod, entry ->
                                    viewModel.subEpisodesClickedCount++
                                    onEpisodeClick?.invoke(ep, pod, entry)
                                },
                                onPlayEpisode = onPlayEpisode
                            )
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
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = 400f
            ),
            label = "indicatorOffset"
        )
        
        // Bouncy Sliding Indicator
        Surface(
            modifier = Modifier
                .width(tabWidth)
                .height(48.dp) // Fixed height to match row below
                .offset(x = indicatorOffset),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {}
        
        // Tab Content
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                
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
                        .height(48.dp)
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
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                        val badgeCount = badge[index]
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
        }
    }
}

// ─── Tab Contents ────────────────────────────────────────────────────────────

@Composable
private fun ShowsTabContent(
    podcasts: List<Podcast>,
    onExploreClick: () -> Unit,
    onPodcastClick: (String) -> Unit,
    onPlayEpisode: ((Episode, Podcast) -> Unit)?
) {
    if (podcasts.isEmpty()) {
        ExpressiveSolarSystemEmptyState(
            title = "No Subscriptions Yet",
            description = "Follow your favorite podcasts to see them here.",
            actionText = "Find Podcasts",
            onExploreClick = onExploreClick
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items = podcasts, key = { it.id }) { podcast ->
                SubscriptionListRow(
                    podcast = podcast,
                    onClick = { onPodcastClick(podcast.id) },
                    onPlayLatest = if (onPlayEpisode != null && podcast.latestEpisode != null) {
                        { onPlayEpisode(podcast.latestEpisode!!, podcast) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun LatestTabContent(
    podcasts: List<Podcast>,
    onExploreClick: () -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    onPlayEpisode: ((Episode, Podcast) -> Unit)?
) {
    val episodePodcasts = podcasts
        .filter { it.latestEpisode != null }
        .sortedWith(
            compareBy<Podcast> { podcast ->
                when (podcast.episodeStatus) {
                    cx.aswin.boxcast.core.model.EpisodeStatus.UNPLAYED, null -> 0
                    cx.aswin.boxcast.core.model.EpisodeStatus.IN_PROGRESS -> 1
                    cx.aswin.boxcast.core.model.EpisodeStatus.COMPLETED -> 2
                }
            }.thenByDescending { it.latestEpisode?.publishedDate ?: 0L }
        )

    if (episodePodcasts.isEmpty()) {
        ExpressiveSolarSystemEmptyState(
            title = "No New Episodes",
            description = "You're all caught up! Explore for more content.",
            actionText = "Discover Shows",
            onExploreClick = onExploreClick
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 180.dp, top = 4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items = episodePodcasts, key = { "${it.id}_latest" }) { podcast ->
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

// ─── Row Components ──────────────────────────────────────────────────────────

@Composable
private fun SubscriptionListRow(
    podcast: Podcast,
    onClick: () -> Unit,
    onPlayLatest: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = (podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl)?.optimizedImageUrl(400),
            contentDescription = podcast.title,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = podcast.artist.takeIf { it.isNotEmpty() } ?: "Podcast",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val latestEpisode = podcast.latestEpisode
            if (latestEpisode != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LatestEpisodeChip(
                    episodeTitle = latestEpisode.title,
                    isPlayable = onPlayLatest != null,
                    onPlay = onPlayLatest
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LatestEpisodeChip(
    episodeTitle: String,
    isPlayable: Boolean,
    onPlay: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val chipColor by animateColorAsState(
        targetValue = if (isPressed)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipColor"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = chipColor,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(
                    if (isPlayable && onPlay != null)
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onPlay
                        )
                    else Modifier
                )
                .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlayable) "Play latest episode" else null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        Box(modifier = Modifier.size(64.dp)) {
            AsyncImage(
                model = (episode.imageUrl?.takeIf { it.isNotEmpty() }
                    ?: podcast.imageUrl.takeIf { it.isNotEmpty() }
                    ?: podcast.fallbackImageUrl)?.optimizedImageUrl(400),
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            when {
                status == EpisodeStatus.UNPLAYED -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                isCompleted -> {
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
