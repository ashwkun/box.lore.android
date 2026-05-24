package cx.aswin.boxcast.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import cx.aswin.boxcast.core.designsystem.components.AnimatedShapesFallback
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.components.LogRecomposition
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.designsystem.components.RegionNudgeBanner

/**
 * Main Explore Screen Entry Point
 */
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    entryPoint: String = "bottom_nav",
    onPodcastClick: (String, String, String?, Int?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showRegionNudge by viewModel.showRegionNudge.collectAsStateWithLifecycle()
    val activeRegionCode by viewModel.activeRegionCode.collectAsStateWithLifecycle()
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackExploreScreenViewed(entryPoint)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.trackScreenExit()
                androidx.lifecycle.Lifecycle.Event.ON_START -> viewModel.onScreenResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.trackScreenExit()
        }
    }

    ExploreContent(
        uiState = uiState,
        showRegionNudge = showRegionNudge,
        activeRegionCode = activeRegionCode,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onPodcastClick = { id, entryPoint, filter, index ->
            viewModel.trackPodcastClicked(index ?: 0)
            onPodcastClick(id, entryPoint, filter, index)
        },
        onVibeSelected = viewModel::onVibeSelected,
        onClearVibe = viewModel::clearVibe,
        onSwitchRegion = viewModel::switchRegion,
        onDismissNudge = viewModel::dismissExploreRegionNudge,
        onLoadMore = { viewModel.loadMoreTrending() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreContent(
    uiState: ExploreUiState,
    showRegionNudge: Boolean = false,
    activeRegionCode: String = "us",
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onPodcastClick: (String, String, String?, Int?) -> Unit,
    onVibeSelected: (String, String) -> Unit,
    onClearVibe: () -> Unit,
    onSwitchRegion: (String) -> Unit = {},
    onDismissNudge: () -> Unit = {},
    onLoadMore: () -> Unit = {}
) {
    LogRecomposition(name = "ExploreContent")

    // Handle error/loading states
    when (uiState) {
        is ExploreUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                BoxCastLoader.Expressive(size = 100.dp)
            }
            return
        }
        is ExploreUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${uiState.message}")
            }
            return
        }
        is ExploreUiState.Success -> { /* Continue */ }
    }

    val state = uiState as ExploreUiState.Success
    val displayList = if (state.isSearching) state.searchResults else state.trending
    
    // Genre expansion state
    var isGenreExpanded by rememberSaveable { mutableStateOf(false) }

    // Scroll handling: Collapse genre cloud on scroll
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If scrolling down (content moving up) and expanded, collapse it
                if (available.y < -5f && isGenreExpanded) {
                    isGenreExpanded = false
                }
                return Offset.Zero
            }
        }
    }

    var searchActive by rememberSaveable { mutableStateOf(false) }
    
    val isPrompting = searchActive && state.searchQuery.isEmpty() && state.currentVibe == null
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Box layout to hold everything
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Column layout to keep search bar fixed at top
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .nestedScroll(nestedScrollConnection)
        ) {
        // FIXED HEADER: Search + Genre Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp)
                .animateContentSize() // Smooth resize when cloud collapses/expands
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search Bar (Always Visible)
            DockedSearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChanged,
                onSearch = { 
                    searchActive = false 
                    focusManager.clearFocus()
                },
                active = false,
                onActiveChange = { searchActive = it },
                placeholder = { Text("Search podcasts...") },
                leadingIcon = { 
                    if (searchActive || state.searchQuery.isNotEmpty() || state.currentVibe != null) {
                        IconButton(onClick = {
                            searchActive = false
                            onSearchQueryChanged("")
                            onClearVibe()
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null) 
                    }
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) { }
            
            // Expandable Genre Cloud
            if (!state.isSearching && !isPrompting) {
                Spacer(modifier = Modifier.height(8.dp))
                ExploreGenreSelector(
                    selectedCategory = state.currentCategory,
                    onCategorySelected = onCategorySelected
                )
            }
            
            // Bottom spacing (always visible - prevents cut-off look)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // SCROLLABLE CONTENT: Grid
        val gridState = rememberLazyStaggeredGridState()

        // Infinite scroll trigger using snapshotFlow to avoid rapid-fire recomposition loops
        androidx.compose.runtime.LaunchedEffect(gridState) {
            androidx.compose.runtime.snapshotFlow {
                val layoutInfo = gridState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisible >= totalItems - 6
            }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd) onLoadMore()
            }
        }

        val distinctVibes = remember(state.suggestedVibes) { state.suggestedVibes.distinctBy { it.first } }

        val rawGridItems = if (!state.isSearching && displayList.isNotEmpty() && state.currentVibe == null) displayList.drop(1) else displayList
        val gridItems = remember(rawGridItems) {
            rawGridItems.distinctBy { podcast ->
                val titleKey = podcast.title.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
                val artistKey = podcast.artist.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
                if (titleKey.isNotEmpty() && artistKey.isNotEmpty()) {
                    "$titleKey|$artistKey"
                } else {
                    podcast.id
                }
            }
        }

        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 180.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 16.dp,
            modifier = Modifier.weight(1f)
        ) {
            if (showRegionNudge && !state.isSearching && state.currentVibe == null && !isPrompting) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    RegionNudgeBanner(
                        systemRegion = "",
                        activeRegion = activeRegionCode,
                        onSwitchRegion = onSwitchRegion,
                        onDismiss = onDismissNudge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            if (isPrompting && state.suggestedVibes.isNotEmpty()) {
                // Section Header
                item(span = StaggeredGridItemSpan.FullLine) {
                    ExploreSectionHeader(title = "Suggested for You")
                }
                items(distinctVibes, key = { "vibe_${it.first}" }) { vibe ->
                    ExploreVibeCard(vibe = vibe, onClick = { 
                        searchActive = false
                        onVibeSelected(vibe.first, vibe.second) 
                    })
                }
            } else {
                // Unified Section Header
                item(span = StaggeredGridItemSpan.FullLine) {
                    if (state.currentVibe != null) {
                        CuratedVibeHeader(title = state.currentVibe)
                    } else if (state.isSearching) {
                        ExploreSearchHeader()
                    } else {
                        val headerTitle = if (state.currentCategory == "All") {
                            "Featured Podcasts"
                        } else {
                            "Trending in ${state.currentCategory}"
                        }
                        ExploreSectionHeader(title = headerTitle)
                    }
                }

                // Sleek glowing progress indicator for search background fetching
                if (state.isLoading && (state.isSearching || state.currentVibe != null) && displayList.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    }
                }

                // Featured Hero Card (P1 only, when not searching and has content)
                if (!state.isSearching && displayList.isNotEmpty() && !state.isLoading && state.currentVibe == null) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        ExploreHeroCard(
                            podcast = displayList[0],
                            onClick = { onPodcastClick(displayList[0].id, "explore_hero", state.currentCategory, 0) },
                            showGenreChip = state.currentCategory == "All"
                        )
                    }
                }
    
                // Content
                val showContent = displayList.isNotEmpty()
                val showSkeletons = state.isLoading && (displayList.isEmpty() || !state.isSearching && state.currentVibe == null)
                val showEmptyState = !state.isLoading && displayList.isEmpty() && (state.isSearching || state.currentVibe != null)

                if (showSkeletons) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val loaderSize = if (state.isSearching) 100.dp else 80.dp
                            BoxCastLoader.Expressive(size = loaderSize)
                        }
                    }
                } else if (showEmptyState) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        ExploreEmptyState()
                    }
                } else if (showContent) {
                    val showGenreChip = state.currentCategory == "All" && state.currentVibe == null
                    itemsIndexed(gridItems, key = { _, it -> "grid_${it.id}" }) { index, podcast ->
                        val cardHeight = 160.dp
                        
                        val entryPointStr = when {
                            state.currentVibe != null -> "explore_vibe"
                            state.isSearching -> "explore_search"
                            else -> "explore_grid"
                        }
                        
                        // Offset index by 1 if there is a hero card (not searching, no vibe)
                        val actualIndex = if (!state.isSearching && state.currentVibe == null) index + 1 else index
                        
                        ExplorePodcastCard(
                            podcast = podcast,
                            cardHeight = cardHeight,
                            showGenreChip = showGenreChip,
                            onClick = { onPodcastClick(podcast.id, entryPointStr, state.currentCategory, actualIndex) }
                        )
                    }

                    // Append background loading shape morphing loader at the end of local matches
                    if (state.isLoading && (state.isSearching || state.currentVibe != null)) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxCastLoader.Expressive(size = 48.dp)
                            }
                        }
                    }

                    // Loading indicator for pagination
                    if (state.isLoadingMore) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxCastLoader.Expressive(size = 40.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ============================================================================
// COMPONENTS
// ============================================================================

/**
 * Expandable Genre Cloud for Explore
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExploreGenreSelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    // Dynamic list construction
    val displayGenres = remember(selectedCategory) {
        val topGenres = EXPLORE_GENRES.take(5)
        if (selectedCategory != "All") {
            val selectedGenre = EXPLORE_GENRES.find { it.value == selectedCategory }
            if (selectedGenre != null) {
                // Selected + (Top 5 - Selected)
                listOf(selectedGenre) + (topGenres - selectedGenre)
            } else {
                topGenres
            }
        } else {
            topGenres
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    androidx.compose.runtime.LaunchedEffect(selectedCategory) {
        listState.animateScrollToItem(0)
    }

    // Top horizontal list (Subset)
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. "All" (Always first)
        item {
            FilterChip(
                selected = selectedCategory == "All",
                onClick = { onCategorySelected("All") },
                label = { Text("All", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedCategory == "All")
            )
        }

        // 2. Dynamic Subset
        items(displayGenres, key = { it.value }) { genre ->
            FilterChip(
                selected = selectedCategory == genre.value,
                onClick = { onCategorySelected(genre.value) },
                label = { Text(genre.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedCategory == genre.value)
            )
        }

        // 3. Expand Button
        item {
            FilterChip(
                selected = showSheet,
                onClick = { showSheet = true },
                label = { Text("More Genres") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Expand",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = Color.Transparent
                )
            )
        }    }

    // Full Genre Sheet
    if (showSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp) // Nav bar padding
            ) {
                Text(
                    text = "Browse Genres",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "All" in sheet
                    FilterChip(
                        selected = selectedCategory == "All",
                        onClick = { 
                            onCategorySelected("All")
                            showSheet = false 
                        },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                             enabled = true,
                             selected = selectedCategory == "All",
                             borderColor = Color.Transparent
                        )
                    )

                    EXPLORE_GENRES.forEach { genre ->
                        val isSelected = selectedCategory == genre.value
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                onCategorySelected(genre.value)
                                showSheet = false 
                            },
                            label = { Text(genre.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                 enabled = true,
                                 selected = isSelected,
                                 borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}


/**
 * M3 Horizontal Hero Card — image left, text right on surface
 */
@Composable
private fun ExploreHeroCard(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showGenreChip: Boolean = false
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            // Left: Square artwork
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight()
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Genre Badge overlay on image
                if (showGenreChip && podcast.genre.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = podcast.genre.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Video Badge overlay on image
                if (podcast.medium == "video") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.Videocam,
                                contentDescription = "Video Podcast",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "VIDEO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Right: Text content on surface
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!podcast.description.isNullOrEmpty()) {
                    val strippedDesc = remember(podcast.description) {
                        android.text.Html.fromHtml(podcast.description ?: "", android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    }
                    if (strippedDesc.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strippedDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section header matching HomeScreen's DiscoverSection header
 */
@Composable
private fun ExploreSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = SectionHeaderFontFamily
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
/**
 * Dedicated Material 3 Search Header
 */
@Composable
private fun ExploreSearchHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Search Results",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SectionHeaderFontFamily
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}/**
 * Podcast card matching HomeScreen's PodcastCard exactly
 */
@Composable
fun ExplorePodcastCard(
    podcast: Podcast,
    cardHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showGenreChip: Boolean = false
) {
    LogRecomposition(name = "ExplorePodcastCard")
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.expressiveClickable(onClick = onClick)
    ) {
        Column {
            // Image Container with optional Genre Chip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                )
                
                // Genre Chip (only shown when showGenreChip is true)
                if (showGenreChip && podcast.genre.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = podcast.genre.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Video Badge overlay on image
                if (podcast.medium == "video") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.Videocam,
                                contentDescription = "Video Podcast",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "VIDEO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Text content below image
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Composable
private fun ExploreEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cute Illustration / Icon Header
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Podcasts Found",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Please try searching for the exact word or name of the podcast you are looking for, and double-check spacing and spelling.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}@Composable
fun ExploreVibeCard(
    vibe: Pair<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine dynamic colors based on string hash for variety
    val hash = vibe.first.hashCode()
    val containerColor = when (hash % 3) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (hash % 3) {
        0 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = vibe.second,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun CuratedVibeHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Curated: $title",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = SectionHeaderFontFamily
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Data Handling (Synced with GenreSelector.kt and OnboardingScreen.kt)
private data class ExploreGenreItem(val label: String, val value: String, val icon: ImageVector)

private val EXPLORE_GENRES = listOf(
    ExploreGenreItem("News", "News", Icons.Rounded.Newspaper),
    ExploreGenreItem("Tech", "Technology", Icons.Rounded.Computer),
    ExploreGenreItem("Business", "Business", Icons.Rounded.Work),
    ExploreGenreItem("Comedy", "Comedy", Icons.Rounded.EmojiEvents),
    ExploreGenreItem("True Crime", "True Crime", Icons.Rounded.Search),
    ExploreGenreItem("Sports", "Sports", Icons.Rounded.SportsBaseball),
    ExploreGenreItem("Health", "Health", Icons.Rounded.FavoriteBorder),
    ExploreGenreItem("History", "History", Icons.Rounded.AccountBalance),
    ExploreGenreItem("Arts", "Arts", Icons.Rounded.Palette),
    ExploreGenreItem("Society", "Society & Culture", Icons.Rounded.Person),
    ExploreGenreItem("Education", "Education", Icons.Rounded.School),
    ExploreGenreItem("Science", "Science", Icons.Rounded.Science),
    ExploreGenreItem("TV & Film", "TV & Film", Icons.Rounded.Movie),
    ExploreGenreItem("Fiction", "Fiction", Icons.Rounded.AutoStories),
    ExploreGenreItem("Music", "Music", Icons.Rounded.MusicNote),
    ExploreGenreItem("Religion", "Religion & Spirituality", Icons.Rounded.Star),
    ExploreGenreItem("Family", "Kids & Family", Icons.Rounded.Face),
    ExploreGenreItem("Leisure", "Leisure", Icons.Rounded.Weekend),
    ExploreGenreItem("Govt", "Government", Icons.Rounded.Gavel)
)


