package cx.aswin.boxcast.feature.onboarding

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.network.model.toPodcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSuggestionsScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onToggleSubscription: (String) -> Unit,
    onToggleRowSubscriptions: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val isLoading = (uiState.isLoadingPodcasts && uiState.aiCurriculumRows.isEmpty()) || (uiState.aiCurriculumRows.isEmpty() && uiState.genreChartsPodcasts.isEmpty() && uiState.onboardingError == null)
    val isError = uiState.onboardingError != null && uiState.aiCurriculumRows.isEmpty() && uiState.genreChartsPodcasts.isEmpty()

    val listState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 50)
        }
    }

    val hasCharts = uiState.genreChartsPodcasts.isNotEmpty()
    var selectedCategoryIndex by remember(hasCharts) { mutableIntStateOf(if (hasCharts && uiState.aiCurriculumRows.isNotEmpty()) 1 else 0) }
    
    val activeRow = if (hasCharts) {
        uiState.aiCurriculumRows.getOrNull(selectedCategoryIndex - 1)
    } else {
        uiState.aiCurriculumRows.getOrNull(selectedCategoryIndex)
    }
    
    val heroPodcasts = remember(uiState.aiCurriculumRows) {
        uiState.aiCurriculumRows
            .take(4)
            .mapNotNull { row -> 
                row.podcasts.firstOrNull()?.toPodcast()?.let { podcast ->
                    podcast to row.rowTitle
                }
            }
    }

    val tabsCount = uiState.aiCurriculumRows.size + (if (hasCharts) 1 else 0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.08f),
                            secondaryColor.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(x = 1000f, y = -100f),
                        radius = 1200f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = 0.05f),
                            primaryColor.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(x = -200f, y = 1800f),
                        radius = 1200f
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showTopBarTitle && !isLoading && !isError,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
                        ) {
                            Text(
                                text = "Your Curations",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(40.dp)
                                .expressiveClickable(shape = androidx.compose.foundation.shape.CircleShape) {
                                    onBack()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                if (!isLoading && !isError) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            val buttonColor = MaterialTheme.colorScheme.primary
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(
                                        color = if (uiState.isCompleting) buttonColor.copy(alpha = 0.5f) else buttonColor,
                                        shape = RoundedCornerShape(28.dp)
                                    )
                                    .then(
                                        if (!uiState.isCompleting) {
                                            Modifier.expressiveClickable(shape = RoundedCornerShape(28.dp)) { onFinish() }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (uiState.isCompleting) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                     val text = if (uiState.reachedSuggestionsViaSearchFlow) {
                                         val recommendedIds = uiState.aiCurriculumRows.flatMap { it.podcasts }.map { it.id.toString() }.toSet()
                                         val selectedRecommendationsCount = uiState.subscribedPodcastIds.count { it in recommendedIds }
                                         if (selectedRecommendationsCount > 0) {
                                             "Subscribe & Start (+${selectedRecommendationsCount} recommended)"
                                         } else {
                                             "Start without subscribing"
                                         }
                                     } else {
                                         if (uiState.subscribedPodcastIds.isNotEmpty()) {
                                             "Subscribe & Start (${uiState.subscribedPodcastIds.size})"
                                         } else {
                                             "Start without subscribing"
                                         }
                                     }
                                     Text(
                                         text = text,
                                         fontWeight = FontWeight.Bold,
                                         fontSize = 16.sp,
                                         color = MaterialTheme.colorScheme.onPrimary
                                     )
                                     Spacer(modifier = Modifier.width(8.dp))
                                     Icon(
                                         imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                         contentDescription = null,
                                         tint = MaterialTheme.colorScheme.onPrimary,
                                         modifier = Modifier.size(18.dp)
                                     )
                                }
                            }
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BoxCastLoader.Expressive(size = 80.dp)
                        Text(
                            text = when {
                                uiState.reachedSuggestionsViaOpmlFlow -> {
                                    "Your OPML shows are subscribed!\nGathering new shows inspired by your library..."
                                }
                                uiState.reachedSuggestionsViaSearchFlow -> {
                                    "Subscribed to ${uiState.selectedPodcasts.size} shows!\nFinding similar shows you might love..."
                                }
                                else -> {
                                    "Synthesizing your feed..."
                                }
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = uiState.onboardingError ?: "Failed to generate recommendations",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                            Text(
                                text = "Designed for You",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Based on your preferences, we curated a custom podcast catalog to get you started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (heroPodcasts.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(heroPodcasts.size) { index ->
                                    val (hero, category) = heroPodcasts[index]
                                    val isSubscribed = hero.id in uiState.subscribedPodcastIds
                                    HeroPodcastCard(
                                        podcast = hero,
                                        categoryName = category,
                                        isSubscribed = isSubscribed,
                                        onToggleSubscription = onToggleSubscription,
                                        modifier = Modifier.width(312.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.aiCurriculumRows.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Curated Collections",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)
                                )
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(tabsCount) { index ->
                                        val isSelected = index == selectedCategoryIndex
                                        val isChartsTab = hasCharts && index == 0
                                        
                                        val title = if (isChartsTab) {
                                            "Top Hits"
                                        } else {
                                            val rowIdx = if (hasCharts) index - 1 else index
                                            uiState.aiCurriculumRows[rowIdx].rowTitle
                                        }
                                        
                                        val containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f).compositeOver(MaterialTheme.colorScheme.surface)
                                        }
                                        
                                        val contentColor = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }

                                        val iconColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(containerColor)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(24.dp)
                                                )
                                                .clickable {
                                                    selectedCategoryIndex = index
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isChartsTab) {
                                                         Icons.AutoMirrored.Rounded.TrendingUp
                                                    } else {
                                                        val iconIndex = if (hasCharts) index - 1 else index
                                                        when (iconIndex % 4) {
                                                            0 -> Icons.Rounded.AutoAwesome
                                                            1 -> Icons.Rounded.Star
                                                            2 -> Icons.Rounded.Bookmark
                                                            else -> Icons.Rounded.Grain
                                                        }
                                                    },
                                                    contentDescription = null,
                                                    tint = iconColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = title,
                                                    color = contentColor,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val isChartsTab = hasCharts && selectedCategoryIndex == 0

                        if (isChartsTab) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 16.dp, bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "Top Hits in ${uiState.selectedGenres.joinToString(", ")}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    // Region Segmented Control
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
                                            "gb" to "UK",
                                            "fr" to "France"
                                        )
                                        regions.forEach { (code, label) ->
                                            val isSelected = uiState.currentRegion == code
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                        else Color.Transparent
                                                    )
                                                    .clickable { onRegionChange(code) }
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

                            if (uiState.isLoadingPodcasts) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BoxCastLoader.Expressive(size = 48.dp)
                                    }
                                }
                            } else if (uiState.genreChartsPodcasts.isNotEmpty()) {
                                items(uiState.genreChartsPodcasts) { podcast ->
                                    val isSubscribed = podcast.id in uiState.subscribedPodcastIds
                                    SuggestedPodcastRowItem(
                                        podcast = podcast,
                                        isSubscribed = isSubscribed,
                                        onToggleSubscription = onToggleSubscription,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .height(100.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No trending podcasts found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else if (activeRow != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 8.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${activeRow.podcasts.size} Recommendations",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    val allSelected = remember(activeRow.podcasts, uiState.subscribedPodcastIds) {
                                        activeRow.podcasts.isNotEmpty() && activeRow.podcasts.all { it.id.toString() in uiState.subscribedPodcastIds }
                                    }
                                    
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .expressiveClickable {
                                                onToggleRowSubscriptions(activeRow.rowTitle)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (allSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (allSelected) "Deselect Group" else "Select Group",
                                            color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (activeRow.podcasts.isNotEmpty()) {
                                items(activeRow.podcasts) { podcastDto ->
                                    val podcast = podcastDto.toPodcast()
                                    val isSubscribed = podcast.id in uiState.subscribedPodcastIds
                                    SuggestedPodcastRowItem(
                                        podcast = podcast,
                                        isSubscribed = isSubscribed,
                                        onToggleSubscription = onToggleSubscription,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .height(100.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No suggestions found in this category.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun HeroPodcastCard(
    podcast: Podcast,
    categoryName: String,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val cardBgColor = if (isSubscribed) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f).compositeOver(MaterialTheme.colorScheme.surface)
    }
    
    val cardBorder = if (isSubscribed) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    val cardModifier = if (expanded) {
        modifier.expressiveClickable(shape = RoundedCornerShape(24.dp)) {
            expanded = false
        }
    } else {
        modifier
            .height(390.dp) // Enforce a uniform height when collapsed
            .expressiveClickable(shape = RoundedCornerShape(24.dp)) {
                expanded = true
            }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = cardModifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(if (expanded) Modifier.wrapContentHeight() else Modifier.fillMaxHeight())
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(120.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 240,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AI TOP PICK",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = categoryName.uppercase(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }
            
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 20.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                text = podcast.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            
            val rawDescription = podcast.description?.stripHtml()
            val description = if (!rawDescription.isNullOrBlank()) {
                rawDescription
            } else {
                "Explore episodes, discussions, and topics from ${podcast.title} by ${podcast.artist}."
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .animateContentSize()
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 18.sp,
                        fontSize = 13.sp
                    ),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                )
                Text(
                    text = if (expanded) "Show less" else "Read more",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            val buttonBgColor = if (isSubscribed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
            val buttonTextColor = if (isSubscribed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
            val buttonText = if (isSubscribed) "Selected" else "Select Show"
            val buttonIcon = if (isSubscribed) Icons.Rounded.CheckCircle else Icons.Rounded.Add
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(buttonBgColor, RoundedCornerShape(24.dp))
                    .expressiveClickable(shape = RoundedCornerShape(24.dp)) {
                        onToggleSubscription(podcast.id)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = buttonIcon,
                    contentDescription = null,
                    tint = buttonTextColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonText,
                    color = buttonTextColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun SuggestedPodcastRowItem(
    podcast: Podcast,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val cardBgColor = if (isSubscribed) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val cardBorder = if (isSubscribed) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 160,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (podcast.genre.isNotBlank() && podcast.genre != "Podcast") {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = podcast.genre.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val rawDescription = podcast.description?.stripHtml()
                val description = if (!rawDescription.isNullOrBlank()) {
                    rawDescription
                } else {
                    "Explore episodes, discussions, and topics from ${podcast.title} by ${podcast.artist}."
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .animateContentSize()
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 15.sp,
                            fontSize = 12.sp
                        ),
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (expanded) "Show less" else "Read more",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(40.dp)
                    .expressiveClickable(shape = androidx.compose.foundation.shape.CircleShape) {
                        onToggleSubscription(podcast.id)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = if (isSubscribed) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSubscribed) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getCategoryIcon(title: String): ImageVector {
    val lower = title.lowercase()
    return when {
        lower.contains("crime") || lower.contains("murder") || lower.contains("detective") || lower.contains("mystery") || lower.contains("thriller") || lower.contains("spooky") || lower.contains("horror") || lower.contains("investigat") -> Icons.Rounded.Fingerprint
        lower.contains("tech") || lower.contains("computer") || lower.contains("digital") || lower.contains("ai") || lower.contains("innovation") || lower.contains("future") -> Icons.Rounded.Computer
        lower.contains("comedy") || lower.contains("funny") || lower.contains("laugh") || lower.contains("humor") || lower.contains("joke") || lower.contains("conversation") || lower.contains("talk") || lower.contains("chat") -> Icons.Rounded.SentimentVerySatisfied
        lower.contains("news") || lower.contains("daily") || lower.contains("politics") || lower.contains("world") || lower.contains("current") -> Icons.Rounded.Newspaper
        lower.contains("business") || lower.contains("money") || lower.contains("finance") || lower.contains("work") || lower.contains("career") || lower.contains("startup") || lower.contains("investing") || lower.contains("investment") -> Icons.Rounded.Work
        lower.contains("sports") || lower.contains("game") || lower.contains("ball") || lower.contains("football") || lower.contains("basketball") || lower.contains("match") -> Icons.Rounded.EmojiEvents
        lower.contains("health") || lower.contains("mind") || lower.contains("body") || lower.contains("meditation") || lower.contains("sleep") || lower.contains("relax") || lower.contains("yoga") || lower.contains("wellness") || lower.contains("heart") -> Icons.Rounded.MonitorHeart
        lower.contains("history") || lower.contains("ancient") || lower.contains("past") || lower.contains("museum") || lower.contains("heritage") -> Icons.Rounded.AccountBalance
        lower.contains("arts") || lower.contains("design") || lower.contains("paint") || lower.contains("creative") || lower.contains("culture") -> Icons.Rounded.Palette
        lower.contains("science") || lower.contains("physics") || lower.contains("bio") || lower.contains("space") || lower.contains("lab") || lower.contains("research") -> Icons.Rounded.Science
        lower.contains("fiction") || lower.contains("story") || lower.contains("stories") || lower.contains("drama") || lower.contains("book") || lower.contains("read") || lower.contains("novel") -> Icons.Rounded.AutoStories
        lower.contains("music") || lower.contains("song") || lower.contains("audio") || lower.contains("rhythm") || lower.contains("instrument") -> Icons.Rounded.MusicNote
        lower.contains("religion") || lower.contains("spirit") || lower.contains("faith") || lower.contains("god") || lower.contains("soul") -> Icons.Rounded.SelfImprovement
        lower.contains("kids") || lower.contains("family") || lower.contains("parent") || lower.contains("child") -> Icons.Rounded.ChildCare
        lower.contains("leisure") || lower.contains("weekend") || lower.contains("chill") || lower.contains("hobby") -> Icons.Rounded.Weekend
        lower.contains("government") || lower.contains("law") || lower.contains("court") || lower.contains("gavel") -> Icons.Rounded.Gavel
        else -> Icons.Rounded.AutoAwesome
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val CondensedGoogleSans = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        Font(
            cx.aswin.boxcast.core.designsystem.R.font.google_sans_variable,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(700),
                FontVariation.Setting("wdth", 75f)
            )
        )
    )
} else {
    FontFamily.Default
}

private fun String.stripHtml(): String {
    val withoutTags = this.replace(Regex("<[^>]*>"), "")
    return withoutTags
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#8217;", "'")
        .replace("&#8216;", "'")
        .replace("&#8220;", "\"")
        .replace("&#8221;", "\"")
        .replace("&nbsp;", " ")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .trim()
}
