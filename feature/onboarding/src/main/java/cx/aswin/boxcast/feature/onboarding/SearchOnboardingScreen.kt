package cx.aswin.boxcast.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import cx.aswin.boxcast.core.model.Podcast

data class SearchCategoryItem(
    val label: String,
    val categoryValue: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val containerColor: Color,
    val contentColor: Color
)

val SEARCH_CATEGORIES = listOf(
    SearchCategoryItem("Trending", null, Icons.Rounded.Whatshot, Color(0xFFE0F7FA), Color(0xFF006064)),
    SearchCategoryItem("Comedy", "Comedy", Icons.Rounded.SentimentVerySatisfied, Color(0xFFF3E5F5), Color(0xFF4A148C)),
    SearchCategoryItem("True Crime", "True Crime", Icons.Rounded.Fingerprint, Color(0xFFFFEBEE), Color(0xFF880E4F)),
    SearchCategoryItem("Tech", "Technology", Icons.Rounded.Computer, Color(0xFFE3F2FD), Color(0xFF0D47A1)),
    SearchCategoryItem("News", "News", Icons.Rounded.Newspaper, Color(0xFFE8F5E9), Color(0xFF1B5E20)),
    SearchCategoryItem("Business", "Business", Icons.Rounded.Work, Color(0xFFFFF3E0), Color(0xFFE65100))
)

@Composable
internal fun OnboardingSearchScreen(
    query: String,
    results: List<Podcast>,
    isSearching: Boolean,
    subscribedIds: Set<String>,
    selectedPodcasts: Map<String, Podcast>,
    onQueryChange: (String) -> Unit,
    onSubscribe: (Podcast) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    popularPodcasts: List<Podcast>,
    isPopularLoading: Boolean,
    selectedSearchGenre: String?,
    onGenreSelect: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Prominent search bar in top row next to back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (query.isNotEmpty()) {
                        onQueryChange("")
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                placeholder = {
                    Text(
                        text = "Search titles, hosts, or topics...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, "Clear", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }

        // Compact category row directly below search bar
        if (query.isEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SEARCH_CATEGORIES) { category ->
                    val isSelected = selectedSearchGenre == category.categoryValue
                    val containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                        color = containerColor,
                        contentColor = contentColor,
                        modifier = Modifier
                            .clickable { onGenreSelect(category.categoryValue) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
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
            query.isEmpty() -> {
                // Discovery view: Trending shows
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val titleLabel = SEARCH_CATEGORIES.find { it.categoryValue == selectedSearchGenre }?.label ?: "Trending"
                    val sectionTitle = if (selectedSearchGenre == null) "Trending Shows" else "Popular in $titleLabel"
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)
                    )

                    if (isPopularLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            BoxCastLoader.Expressive(size = 80.dp)
                        }
                    } else if (popularPodcasts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No recommendations found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val distinctPopular = remember(popularPodcasts) { popularPodcasts.distinctBy { it.id } }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(distinctPopular, key = { it.id }) { podcast ->
                                val isSubscribed = podcast.id in subscribedIds
                                PopularPodcastGridItem(
                                    podcast = podcast,
                                    isSubscribed = isSubscribed,
                                    onClick = { onSubscribe(podcast) }
                                )
                            }
                        }
                    }
                }
            }
            results.isEmpty() && !isSearching -> {
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
        
        // Done button panel at bottom with selected shows bar
        if (subscribedIds.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Selected Shows (${subscribedIds.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selectedPodcastsList = subscribedIds.mapNotNull { selectedPodcasts[it] }
                        items(selectedPodcastsList, key = { it.id }) { podcast ->
                            Box(
                                modifier = Modifier.size(56.dp)
                            ) {
                                OptimizedImage(
                                    url = podcast.imageUrl,
                                    proxyWidth = 150,
                                    contentDescription = podcast.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                )
                                // Tiny custom circular close badge on top-right (avoids IconButton target size constraint)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(16.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.error,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable { onSubscribe(podcast) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            "Done (${subscribedIds.size} selected)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularPodcastGridItem(
    podcast: Podcast,
    isSubscribed: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .expressiveClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
        ) {
            OptimizedImage(
                url = podcast.imageUrl,
                proxyWidth = 400,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            
            // Subscribed indicator overlay
            val containerColor = if (isSubscribed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            }
            val contentColor = if (isSubscribed) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = if (isSubscribed) "Selected" else "Select",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = podcast.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
        )
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
                onClick = onSubscribe,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
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
                    contentDescription = "Select",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
