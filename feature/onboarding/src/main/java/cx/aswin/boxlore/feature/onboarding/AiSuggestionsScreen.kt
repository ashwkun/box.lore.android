package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.network.model.toPodcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSuggestionsScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onToggleSubscription: (String) -> Unit,
    onToggleRowSubscriptions: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val isLoading =
        (uiState.isLoadingPodcasts && uiState.aiCurriculumRows.isEmpty()) ||
            (uiState.aiCurriculumRows.isEmpty() && uiState.genreChartsPodcasts.isEmpty() && uiState.onboardingError == null)
    val isError = uiState.onboardingError != null && uiState.aiCurriculumRows.isEmpty() && uiState.genreChartsPodcasts.isEmpty()

    val listState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 50)
        }
    }

    val hasCharts = uiState.genreChartsPodcasts.isNotEmpty()
    var selectedCategoryIndex by remember(hasCharts) { mutableIntStateOf(if (hasCharts && uiState.aiCurriculumRows.isNotEmpty()) 1 else 0) }

    val activeRow =
        if (hasCharts) {
            uiState.aiCurriculumRows.getOrNull(selectedCategoryIndex - 1)
        } else {
            uiState.aiCurriculumRows.getOrNull(selectedCategoryIndex)
        }

    val heroPodcasts =
        remember(uiState.aiCurriculumRows) {
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
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors =
                                listOf(
                                    primaryColor.copy(alpha = 0.08f),
                                    secondaryColor.copy(alpha = 0.03f),
                                    Color.Transparent,
                                ),
                            center =
                                androidx.compose.ui.geometry
                                    .Offset(x = 1000f, y = -100f),
                            radius = 1200f,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors =
                                listOf(
                                    tertiaryColor.copy(alpha = 0.05f),
                                    primaryColor.copy(alpha = 0.02f),
                                    Color.Transparent,
                                ),
                            center =
                                androidx.compose.ui.geometry
                                    .Offset(x = -200f, y = 1800f),
                            radius = 1200f,
                        ),
                    ),
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
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                        ) {
                            Text(
                                text = "Your Curations",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier =
                                Modifier
                                    .padding(start = 8.dp)
                                    .size(40.dp)
                                    .expressiveClickable(shape = androidx.compose.foundation.shape.CircleShape) {
                                        onBack()
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                )
            },
            bottomBar = {
                if (!isLoading && !isError) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                        ) {
                            val buttonColor = MaterialTheme.colorScheme.primary
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .background(
                                            color = if (uiState.isCompleting) buttonColor.copy(alpha = 0.5f) else buttonColor,
                                            shape = RoundedCornerShape(28.dp),
                                        ).then(
                                            if (!uiState.isCompleting) {
                                                Modifier.expressiveClickable(shape = RoundedCornerShape(28.dp)) { onFinish() }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                if (uiState.isCompleting) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                } else {
                                    val text =
                                        if (uiState.reachedSuggestionsViaSearchFlow) {
                                            val recommendedIds =
                                                uiState.aiCurriculumRows
                                                    .flatMap { it.podcasts }
                                                    .map { it.id.toString() }
                                                    .toSet()
                                            val selectedRecommendationsCount = uiState.subscribedPodcastIds.count { it in recommendedIds }
                                            if (selectedRecommendationsCount > 0) {
                                                "Subscribe & Start (+$selectedRecommendationsCount recommended)"
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
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            if (isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BoxLoreLoader.Expressive(size = 80.dp)
                        Text(
                            text =
                                when {
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
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (isError) {
                val onboardingError = requireNotNull(uiState.onboardingError)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp),
                        )
                        Text(
                            text = onboardingError,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onRetry,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
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
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                            Text(
                                text = "Designed for You",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Based on your preferences, we curated a custom podcast catalog to get you started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (heroPodcasts.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(heroPodcasts.size) { index ->
                                    val (hero, category) = heroPodcasts[index]
                                    val isSubscribed = hero.id in uiState.subscribedPodcastIds
                                    HeroPodcastCard(
                                        podcast = hero,
                                        categoryName = category,
                                        isSubscribed = isSubscribed,
                                        onToggleSubscription = onToggleSubscription,
                                        modifier = Modifier.width(312.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.aiCurriculumRows.isNotEmpty()) {
                        item {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = "Curated Collections",
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items(tabsCount) { index ->
                                        val isSelected = index == selectedCategoryIndex
                                        val isChartsTab = hasCharts && index == 0

                                        val title =
                                            if (isChartsTab) {
                                                "Top Hits"
                                            } else {
                                                val rowIdx = if (hasCharts) index - 1 else index
                                                uiState.aiCurriculumRows[rowIdx].rowTitle
                                            }

                                        val containerColor =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                                    .copy(
                                                        alpha = 0.2f,
                                                    ).compositeOver(MaterialTheme.colorScheme.surface)
                                            }

                                        val contentColor =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }

                                        val iconColor =
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }

                                        Box(
                                            modifier =
                                                Modifier
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .background(containerColor)
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color =
                                                            if (isSelected) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.outlineVariant
                                                                    .copy(
                                                                        alpha = 0.3f,
                                                                    )
                                                            },
                                                        shape = RoundedCornerShape(24.dp),
                                                    ).clickable {
                                                        selectedCategoryIndex = index
                                                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(
                                                    imageVector =
                                                        if (isChartsTab) {
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
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Text(
                                                    text = title,
                                                    color = contentColor,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
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
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .padding(top = 16.dp, bottom = 8.dp),
                                ) {
                                    Text(
                                        text = "Top Hits in ${uiState.selectedGenres.joinToString(", ")}",
                                        style =
                                            MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 18.sp,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )

                                    // Region Segmented Control
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        val regions =
                                            listOf(
                                                "us" to "USA",
                                                "in" to "India",
                                                "gb" to "UK",
                                                "fr" to "France",
                                            )
                                        regions.forEach { (code, label) ->
                                            val isSelected =
                                                when (code) {
                                                    "gb" -> uiState.currentRegion == "gb" || uiState.currentRegion == "uk"
                                                    "in" -> uiState.currentRegion == "in" || uiState.currentRegion == "ind"
                                                    else -> uiState.currentRegion == code
                                                }
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isSelected) {
                                                                MaterialTheme.colorScheme.primaryContainer
                                                            } else {
                                                                Color.Transparent
                                                            },
                                                        ).clickable { onRegionChange(code) }
                                                        .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color =
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (uiState.isLoadingPodcasts) {
                                item {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 40.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BoxLoreLoader.Expressive(size = 48.dp)
                                    }
                                }
                            } else if (uiState.genreChartsPodcasts.isNotEmpty()) {
                                items(uiState.genreChartsPodcasts) { podcast ->
                                    val isSubscribed = podcast.id in uiState.subscribedPodcastIds
                                    SuggestedPodcastRowItem(
                                        podcast = podcast,
                                        isSubscribed = isSubscribed,
                                        onToggleSubscription = onToggleSubscription,
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp)
                                                .height(100.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                                    RoundedCornerShape(12.dp),
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "No trending podcasts found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else if (activeRow != null) {
                            item {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .padding(top = 8.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${activeRow.podcasts.size} Recommendations",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))

                                    val allSelected =
                                        remember(activeRow.podcasts, uiState.subscribedPodcastIds) {
                                            activeRow.podcasts.isNotEmpty() &&
                                                activeRow.podcasts.all { it.id.toString() in uiState.subscribedPodcastIds }
                                        }

                                    Row(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .expressiveClickable {
                                                    onToggleRowSubscriptions(activeRow.rowTitle)
                                                }.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (allSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (allSelected) "Deselect Group" else "Select Group",
                                            color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
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
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp)
                                                .height(100.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                                    RoundedCornerShape(12.dp),
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "No suggestions found in this category.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
