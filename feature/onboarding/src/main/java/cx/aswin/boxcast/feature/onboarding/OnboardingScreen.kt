package cx.aswin.boxcast.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import cx.aswin.boxcast.core.designsystem.components.LogRecomposition
import cx.aswin.boxcast.core.model.Podcast

// Genre data matching GenreSelector.kt
data class GenreItem(val label: String, val value: String, val icon: ImageVector)

val ONBOARDING_GENRES = listOf(
    GenreItem("News", "News", Icons.Rounded.Newspaper),
    GenreItem("Tech", "Technology", Icons.Rounded.Computer),
    GenreItem("Business", "Business", Icons.Rounded.Work),
    GenreItem("Comedy", "Comedy", Icons.Rounded.SentimentVerySatisfied),
    GenreItem("True Crime", "True Crime", Icons.Rounded.Fingerprint),
    GenreItem("Sports", "Sports", Icons.Rounded.EmojiEvents),
    GenreItem("Health", "Health", Icons.Rounded.MonitorHeart),
    GenreItem("History", "History", Icons.Rounded.AccountBalance),
    GenreItem("Arts", "Arts", Icons.Rounded.Palette),
    GenreItem("Society", "Society & Culture", Icons.Rounded.Groups),
    GenreItem("Education", "Education", Icons.Rounded.School),
    GenreItem("Science", "Science", Icons.Rounded.Science),
    GenreItem("TV & Film", "TV & Film", Icons.Rounded.Movie),
    GenreItem("Fiction", "Fiction", Icons.Rounded.AutoStories),
    GenreItem("Music", "Music", Icons.Rounded.MusicNote),
    GenreItem("Religion", "Religion & Spirituality", Icons.Rounded.SelfImprovement),
    GenreItem("Family", "Kids & Family", Icons.Rounded.ChildCare),
    GenreItem("Leisure", "Leisure", Icons.Rounded.Weekend),
    GenreItem("Govt", "Government", Icons.Rounded.Gavel)
)

// ============================================================
// GENRE PICKER
// ============================================================

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onImportJson: (android.net.Uri) -> Unit = {},
    onImportOpml: (android.net.Uri) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val handleComplete = {
        viewModel.completeOnboarding(onComplete)
    }

    // Main content with animated transitions
    AnimatedContent(
        targetState = uiState.currentStep,
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
        },
        label = "onboarding_step"
    ) { step ->
        when (step) {
            OnboardingStep.GENRES -> {
                // Analytics: Fire onboarding_started when genre screen loads
                LaunchedEffect(Unit) { viewModel.onGenreScreenViewed() }
                GenrePickerScreen(
                    selectedGenres = uiState.selectedGenres,
                    onToggleGenre = viewModel::toggleGenre,
                    onContinue = viewModel::continueToRecommendations,
                    onSearch = viewModel::navigateToSearch,
                    onSkip = { viewModel.skipOnboarding(onComplete) },
                    onImportJson = onImportJson,
                    onImportOpml = onImportOpml,
                    onImportSheetOpened = { cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackImportSheetOpened(viewModel.getGenreScreenTimeSpent()) }
                )
            }
            OnboardingStep.PODCASTS -> {
                LaunchedEffect(Unit) {
                    viewModel.onPodcastScreenViewed()
                }
                PodcastPicksScreen(
                    podcasts = uiState.recommendedPodcasts,
                    subscribedIds = uiState.subscribedPodcastIds,
                    currentRegion = uiState.currentRegion,
                    isLoading = uiState.isLoadingPodcasts,
                    onToggleSubscription = viewModel::togglePodcastSubscription,
                    onRegionChange = viewModel::setRegion,
                    onSearch = viewModel::navigateToSearch,
                    onBack = viewModel::navigateBackFromPodcasts,
                    onDone = handleComplete,
                    onSkip = { viewModel.skipOnboarding(onComplete) },
                    onDidScroll = viewModel::onPodcastScreenScrolled
                )
            }
            OnboardingStep.SEARCH -> {
                OnboardingSearchScreen(
                    query = uiState.searchQuery,
                    results = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    subscribedIds = uiState.subscribedPodcastIds,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSubscribe = viewModel::subscribeFromSearch,
                    onBack = viewModel::navigateBackFromSearch,
                    onDone = handleComplete
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun GenrePickerScreen(
    selectedGenres: Set<String>,
    onToggleGenre: (String) -> Unit,
    onContinue: () -> Unit,
    onSearch: () -> Unit,
    onSkip: () -> Unit,
    onImportJson: (android.net.Uri) -> Unit = {},
    onImportOpml: (android.net.Uri) -> Unit = {},
    onImportSheetOpened: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val importJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportJson(it) } }
    )
    val importOpmlLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportOpml(it) } }
    )
    
    var showImportBottomSheet by remember { mutableStateOf(false) }

    // Analytics: Track when import bottom sheet is opened
    LaunchedEffect(showImportBottomSheet) {
        if (showImportBottomSheet) onImportSheetOpened()
    }

    if (showImportBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Text(
                    text = "Import Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    onClick = {
                        showImportBottomSheet = false
                        importJsonLauncher.launch(arrayOf("application/json"))
                    }
                ) {
                    ListItem(
                        headlineContent = { Text("boxcast Backup (.json)") },
                        supportingContent = { Text("Restore a perfect backup of subscriptions and liked episodes") },
                        leadingContent = { Icon(Icons.Rounded.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    onClick = {
                        showImportBottomSheet = false
                        importOpmlLauncher.launch(arrayOf("*/*"))
                    }
                ) {
                    ListItem(
                        headlineContent = { Text("Other App Backup (.opml)") },
                        supportingContent = { Text("Migrate subscriptions from Apple Podcasts, Spotify, etc.") },
                        leadingContent = { Icon(Icons.Rounded.ImportExport, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Can we get to know you better?",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = selectedGenres.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            if (selectedGenres.isEmpty()) "Pick at least 1"
                            else "Continue (${selectedGenres.size} selected)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
    
                    TextButton(
                        onClick = onSearch,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Search for your favorite podcasts instead",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    TextButton(onClick = onSkip) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Pick the topics you enjoy",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Import Library Option
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .expressiveClickable { showImportBottomSheet = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Already have a library?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Import OPML or Backup",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ONBOARDING_GENRES.forEach { genre ->
                    val isSelected = genre.value in selectedGenres
                    GenreChip(
                        genre = genre,
                        isSelected = isSelected,
                        onClick = { onToggleGenre(genre.value) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastPicksScreen(
    podcasts: List<Podcast>,
    subscribedIds: Set<String>,
    currentRegion: String,
    isLoading: Boolean,
    onToggleSubscription: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onDidScroll: () -> Unit
) {
    LogRecomposition(name = "PodcastPicksScreen")
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            onDidScroll()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Here are some picks for you",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onDone,
                        enabled = subscribedIds.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            if (subscribedIds.isEmpty()) "Pick at least 1" else "Subscribe & Start (${subscribedIds.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TextButton(
                        onClick = onSearch,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Search for more",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    TextButton(onClick = onSkip) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                BoxCastLoader.Expressive()
            }
        } else {
            val distinctPodcasts = remember(podcasts) { podcasts.distinctBy { it.id } }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = "Tap to subscribe — you can always change later",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // Premium Region Segmented Control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val regions = listOf(
                                "us" to "USA",
                                "in" to "India",
                                "gb" to "UK"
                            )
                            regions.forEach { (code, label) ->
                                val isSelected = currentRegion == code
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .expressiveClickable { onRegionChange(code) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                items(distinctPodcasts, key = { it.id }) { podcast ->
                    PodcastPickCard(
                        podcast = podcast,
                        isSubscribed = podcast.id in subscribedIds,
                        onToggle = { onToggleSubscription(podcast.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastPickCard(
    podcast: Podcast,
    isSubscribed: Boolean,
    onToggle: () -> Unit
) {
    LogRecomposition(name = "PodcastPickCard")
    val containerColor = if (isSubscribed)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surfaceContainerHigh
    
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.extraLarge, // Back to standard rounded shape
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onToggle)
    ) {
        Column {
            Box {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                )
                
                // Subscribe badge
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSubscribed,
                    enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                shape = androidx.compose.foundation.shape.CircleShape // Standard circle
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Subscribed",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    podcast.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GenreChip(
    genre: GenreItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh
    
    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface
    
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .height(64.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp), // Reduced padding (from 24dp)
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp) // Reduced spacing (from 12dp)
        ) {
            Icon(
                genre.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp), // Reduced icon size (from 28dp)
                tint = if (isSelected) MaterialTheme.colorScheme.primary else contentColor
            )
            Text(
                genre.label,
                style = MaterialTheme.typography.titleMedium, // Reduced text size (from TitleLarge)
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================
// SEARCH
// ============================================================

@Composable
private fun OnboardingSearchScreen(
    query: String,
    results: List<Podcast>,
    isSearching: Boolean,
    subscribedIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onSubscribe: (Podcast) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search podcasts...") },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, "Clear", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }
        
        when {
            isSearching -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    BoxCastLoader.Expressive(size = 100.dp)
                }
            }
            query.length >= 2 && results.isEmpty() && !isSearching -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Podcasts Found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please try searching for the exact word or name of the podcast you are looking for, and double-check spacing and spelling.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            results.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Search for your favorite podcasts\nand subscribe right here",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                val distinctResults = remember(results) { results.distinctBy { it.id } }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(distinctResults, key = { it.id }) { podcast ->
                        SearchResultRow(
                            podcast = podcast,
                            isSubscribed = podcast.id in subscribedIds,
                            onSubscribe = { onSubscribe(podcast) }
                        )
                    }
                }
            }
        }
        
        // Done button at bottom
        if (subscribedIds.isNotEmpty()) {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(56.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(
                    "Done (${subscribedIds.size} subscribed)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    podcast: Podcast,
    isSubscribed: Boolean,
    onSubscribe: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .expressiveClickable(onClick = onSubscribe)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            proxyWidth = 400,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                podcast.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                podcast.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (isSubscribed) {
            FilledIconButton(
                onClick = {},
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Subscribed",
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            OutlinedIconButton(
                onClick = onSubscribe,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Subscribe",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
