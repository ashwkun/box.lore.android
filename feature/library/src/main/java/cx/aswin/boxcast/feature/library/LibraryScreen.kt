package cx.aswin.boxcast.feature.library

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cx.aswin.boxcast.core.designsystem.components.optimizedImageUrl
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast

/**
 * Main Library Screen
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    entryPoint: String = "bottom_nav",
    onNavigateToLiked: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.trackHubExit()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.onScreenResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryHubViewed(entryPoint)
    }

    LibraryContent(
        uiState = uiState,
        onNavigateToLiked = {
            viewModel.hubNavigatedTo = "liked"
            onNavigateToLiked()
        },
        onNavigateToSubscriptions = {
            viewModel.hubNavigatedTo = "subscriptions"
            onNavigateToSubscriptions()
        },
        onNavigateToDownloads = {
            viewModel.hubNavigatedTo = "downloads"
            onNavigateToDownloads()
        },
        onNavigateToHistory = {
            viewModel.hubNavigatedTo = "history"
            onNavigateToHistory()
        }
    )
}

@Composable
fun LibraryContent(
    uiState: LibraryUiState,
    onNavigateToLiked: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    // Background gradient for subtle depth
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // === MAIN LIBRARY PAGE ===
            Text(
                text = "Library",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 24.dp)
            )

            // CONTENT
            when (uiState) {
                is LibraryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error loading library")
                    }
                }
                is LibraryUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        item {
                            val subscriptionImages = (uiState as LibraryUiState.Success).subscribedPodcasts.take(3).map { it.imageUrl }
                            val subShapes = listOf(
                                ExpressiveShapes.Circle,
                                ExpressiveShapes.Puffy,
                                ExpressiveShapes.Diamond // Fallback to Circle/Diamond if Squircle missing or use Diamond
                            ).map { if (it == ExpressiveShapes.Circle) ExpressiveShapes.Circle else it } // Just logic placeholder
                            // Using: Circle, Puffy, Diamond (as Squircle replacement)
                            val specificSubShapes = listOf(
                                ExpressiveShapes.Circle,
                                ExpressiveShapes.Puffy,
                                ExpressiveShapes.Diamond
                            )
                            
                            LibraryMenuCard(
                                title = "Subscriptions",
                                icon = Icons.Rounded.Add,
                                onClick = onNavigateToSubscriptions,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                images = subscriptionImages,
                                shapes = specificSubShapes
                            )
                        }

                        item {
                            val likedImages = (uiState as LibraryUiState.Success).likedEpisodes.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" }
                            val likedShapes = listOf(
                                ExpressiveShapes.Heart,
                                ExpressiveShapes.Star,
                                ExpressiveShapes.SoftBurst
                            )
                            
                            LibraryMenuCard(
                                title = "Liked Episodes",
                                icon = Icons.Rounded.Favorite,
                                onClick = onNavigateToLiked,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                images = likedImages,
                                shapes = likedShapes
                            )
                        }

                        item {
                            val downloadImages = (uiState as LibraryUiState.Success).downloadedEpisodes.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" }
                            val downloadShapes = listOf(
                                ExpressiveShapes.Hexagon,
                                ExpressiveShapes.Gem,
                                ExpressiveShapes.Cookie4
                            )
                            
                            LibraryMenuCard(
                                title = "Downloads",
                                icon = Icons.Rounded.DownloadDone,
                                onClick = onNavigateToDownloads,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                images = downloadImages,
                                shapes = downloadShapes
                            )
                        }
                        item {
                            val historyImages = (uiState as LibraryUiState.Success).recentHistory.take(3).map { it.episodeImageUrl ?: it.podcastImageUrl ?: "" }
                            val historyShapes = listOf(
                                ExpressiveShapes.Circle,
                                ExpressiveShapes.Burst,
                                ExpressiveShapes.Sunny
                            )
                            LibraryMenuCard(
                                title = "Listening History",
                                icon = Icons.Rounded.History,
                                onClick = onNavigateToHistory,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                images = historyImages,
                                shapes = historyShapes
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryMenuCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    images: List<String> = emptyList(),
    shapes: List<androidx.compose.ui.graphics.Shape> = emptyList()
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Collage Background (Right Side)
            if (images.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .fillMaxHeight()
                        .fillMaxWidth(0.5f) // Take up half the card width
                ) {
                    LibraryCardCollage(images = images, shapes = shapes)
                }
                
                // Scrim to ensure text readability if images slide under text
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    containerColor,
                                    containerColor.copy(alpha = 0.8f),
                                    containerColor.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(32.dp)
            )
        }
    }
}

@Composable
private fun LibraryCardCollage(
    images: List<String>, 
    shapes: List<androidx.compose.ui.graphics.Shape>
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // Fallback shapes if list is empty or short
        val fallbackShapes = listOf(
            ExpressiveShapes.SoftBurst,
            ExpressiveShapes.Circle,
            ExpressiveShapes.Diamond
        )
        val finalShapes = if (shapes.isNotEmpty()) shapes else fallbackShapes
        
        // Reverse order so first image is on top
        images.take(3).reversed().forEachIndexed { index, imageUrl ->
            // Calculate reverse index for correct shape assignment (0=Top, etc)
            val realIndex = images.size - 1 - index
            val shape = finalShapes.getOrElse(realIndex) { finalShapes.first() }
            
            // Dynamic offsets for "pile" effect
            val xOffset = (realIndex * 20).dp
            val yOffset = if (realIndex % 2 == 0) 10.dp else (-10).dp
            val scale = 1f - (realIndex * 0.15f)
            val rotation = if (realIndex % 2 == 0) 10f else -10f

            AsyncImage(
                model = imageUrl.optimizedImageUrl(400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .offset(x = xOffset, y = yOffset)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                    }
                    .clip(shape)
                    .border(2.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), shape)
            )
        }
    }
}

@Composable
fun ExpressiveSolarSystemEmptyState(
    title: String = "Your library is empty",
    description: String = "It's a big universe out there.\nStart exploring.",
    icon: ImageVector = Icons.Rounded.AutoAwesome,
    actionText: String = "Go Explore",
    onExploreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .padding(bottom = 150.dp, top = 32.dp), // Extra bottom padding to avoid mini player
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onExploreClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .height(56.dp)
                .expressiveClickable(onClick = onExploreClick)
        ) {
            Text(
                text = actionText, 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Unified Card Style matching ExploreScreen.kt
 * Uses OutlinedCard, specific rounded corners, variable heights.
 */
@Composable
fun LibraryPodcastCard(
    podcast: Podcast,
    isTall: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large, // Matches ExploreScreen
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onClick)
    ) {
        Column {
            // Image Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTall) 200.dp else 150.dp) // Staggered heights (Reduced)
            ) {
                AsyncImage(
                    model = podcast.imageUrl.optimizedImageUrl(400),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) // Matches Explore styling
                )

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
                                imageVector = Icons.Rounded.Videocam,
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
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Text Content with Padding
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
