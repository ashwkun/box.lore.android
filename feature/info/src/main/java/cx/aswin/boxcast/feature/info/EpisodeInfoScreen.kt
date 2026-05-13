package cx.aswin.boxcast.feature.info

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import cx.aswin.boxcast.core.designsystem.component.HtmlText
import cx.aswin.boxcast.core.designsystem.components.AnimatedShapesFallback
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.theme.m3Shimmer
import cx.aswin.boxcast.core.designsystem.components.ControlStyle
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlinx.coroutines.delay

// Color extraction helper
private fun extractDominantColor(bitmap: android.graphics.Bitmap): Color {
    val palette = Palette.from(bitmap).generate()
    val vibrant = palette.vibrantSwatch?.rgb
    val muted = palette.mutedSwatch?.rgb
    val dominant = palette.dominantSwatch?.rgb
    val colorInt = vibrant ?: muted ?: dominant ?: 0xFF6200EE.toInt()
    return Color(colorInt)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EpisodeInfoScreen(
    episodeId: String,
    episodeTitle: String,
    episodeDescription: String,
    episodeImageUrl: String,
    episodeAudioUrl: String,
    episodeDuration: Int,
    podcastId: String,
    podcastTitle: String,
    viewModel: EpisodeInfoViewModel,
    onBack: () -> Unit,
    onPodcastClick: (String) -> Unit,
    onEpisodeClick: (cx.aswin.boxcast.core.model.Episode) -> Unit,
    onPlay: () -> Unit,
    entryPointContext: android.os.Bundle? = null,
    showMarkPlayedTip: Boolean = false,
    onMarkPlayedTipDismissed: () -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val likedEpisodeIds by viewModel.likedEpisodeIds.collectAsState()
    val completedEpisodeIds by viewModel.completedEpisodeIds.collectAsState()
    val queuedEpisodeIds by viewModel.queuedEpisodeIds.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val density = LocalDensity.current

    // Dynamic color extraction
    var extractedColor by remember { mutableStateOf(Color.Transparent) }
    val accentColor by animateColorAsState(
        targetValue = if (extractedColor != Color.Transparent) extractedColor else MaterialTheme.colorScheme.primary,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "accent_color"
    )

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
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
            viewModel.trackScreenExit() // Fallback if disposed directly
        }
    }

    LaunchedEffect(episodeId) {
        viewModel.loadEpisode(
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            episodeDescription = episodeDescription,
            episodeImageUrl = episodeImageUrl,
            episodeAudioUrl = episodeAudioUrl,
            episodeDuration = episodeDuration,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            entryPointContext = entryPointContext
        )
    }

    // Download State
    val isDownloaded by viewModel.isDownloaded(episodeId).collectAsState(initial = false)
    val isDownloading by viewModel.isDownloading(episodeId).collectAsState(initial = false)

    // Scroll-driven animation state
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else 1000f // Fully collapsed
        }
    }
    
    val morphThreshold = with(density) { 180.dp.toPx() }
    val scrollFraction = (scrollOffset / morphThreshold).coerceIn(0f, 1f)

    // Header dimensions
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedHeaderHeight = 64.dp + statusBarHeight

    // Header background: transparent → surfaceContainer
    // NOTE: Don't lerp from Color.Transparent - it has RGB=0,0,0 causing black flash
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val headerColor by animateColorAsState(
        targetValue = surfaceColor.copy(alpha = scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headerColor"
    )

    // Title animation - header only fade-in
    val titleSizeStart = MaterialTheme.typography.titleLarge.fontSize
    val titleSizeEnd = MaterialTheme.typography.titleMedium.fontSize
    val titleFontSize = androidx.compose.ui.unit.lerp(titleSizeStart, titleSizeEnd, scrollFraction)
    
    // Y position fixed in header
    val headerTitleYPx = with(density) { (statusBarHeight + 18.dp).toPx() }
    val titleTranslationY by animateFloatAsState(
        targetValue = headerTitleYPx,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.85f),
        label = "titleY"
    )
    
    // MaxLines: 1 when in header
    val titleMaxLines = 1
    // Fade in only when header collapses
    val titleAlpha = if (scrollFraction > 0.8f) (scrollFraction - 0.8f) / 0.2f else 0f
    
    // Horizontal padding in header
    val titleHorizontalPadding by animateDpAsState(
        targetValue = 56.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "titlePadding"
    )

    when (val state = uiState) {
        is EpisodeInfoUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                BoxCastLoader.Expressive(size = 80.dp)
            }
        }
        is EpisodeInfoUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Failed to load episode", color = MaterialTheme.colorScheme.error)
            }
        }
        is EpisodeInfoUiState.Success -> {
            // Color extraction
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(state.episode.podcastImageUrl?.ifEmpty { state.episode.imageUrl?.ifEmpty { null } })
                    .allowHardware(false)
                    .build()
            )
            LaunchedEffect(painter.state) {
                val painterState = painter.state
                if (painterState is AsyncImagePainter.State.Success) {
                    val bitmap = (painterState.result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        extractedColor = extractDominantColor(bitmap)
                    }
                }
            }

            Box(modifier = modifier.fillMaxSize()) {
                // Blurred Background Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(collapsedHeaderHeight + 240.dp)
                        .alpha(1f - scrollFraction)
                ) {
                    OptimizedImage(
                        url = state.episode.imageUrl?.ifEmpty { state.episode.podcastImageUrl },
                        proxyWidth = 200,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.5f)
                            .blur(50.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient overlay to blend into the background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                }
                // Content List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = collapsedHeaderHeight + 16.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomContentPadding + 160.dp // Extra for miniplayer
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // HERO SECTION (Artwork + Title + Podcast Link + Metadata)
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Artwork
                            Surface(
                                modifier = Modifier.size(180.dp),
                                shape = MaterialTheme.shapes.extraLarge, // Match PodcastInfoScreen
                                shadowElevation = 8.dp
                            ) {
                                OptimizedImage(
                                    url = state.episode.imageUrl?.ifEmpty { null },
                                    proxyWidth = 600, // 180dp * ~3x density
                                    contentDescription = state.episode.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Episode Title
                            Text(
                                text = state.episode.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            // Podcast Title (clickable)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .expressiveClickable { 
                                        viewModel.onPodcastLinkClicked()
                                        onPodcastClick(state.podcastId) 
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = state.podcastTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false) // shrink text, never push > off screen
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Go to podcast",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Metadata Row (Chips matching PodcastInfoScreen)
                            fun formatDuration(seconds: Int): String {
                                val hours = seconds / 3600
                                val minutes = (seconds % 3600) / 60
                                return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                            }
                            
                            fun formatRelativeDate(timestampSeconds: Long): String {
                                if (timestampSeconds == 0L) return ""
                                val now = System.currentTimeMillis() / 1000
                                val diff = now - timestampSeconds
                                return when {
                                    diff < 3600 -> "${diff / 60}m ago"
                                    diff < 86400 -> "${diff / 3600}h ago"
                                    diff < 604800 -> "${diff / 86400}d ago"
                                    diff < 2592000 -> "${diff / 604800}w ago"
                                    diff < 31536000 -> "${diff / 2592000}mo ago"
                                    else -> "${diff / 31536000}y ago"
                                }
                            }

                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Duration Pill
                                item {
                                    Surface(
                                        shape = cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes.Pill,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = formatDuration(episodeDuration),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                // Date Pill
                                val dateText = formatRelativeDate(state.episode.publishedDate)
                                if (dateText.isNotEmpty()) {
                                    item {
                                        Surface(
                                            shape = cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes.Pill,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                text = dateText,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

                                // Season/Episode Pill
                                val season = state.episode.seasonNumber
                                val episode = state.episode.episodeNumber
                                val seLabel = buildString {
                                    if (season != null && season > 0) {
                                        append("S$season ")
                                    }
                                    if (episode != null && episode > 0) {
                                        append("E$episode")
                                    }
                                }.trim()
                                if (seLabel.isNotEmpty()) {
                                    item {
                                        Surface(
                                            shape = cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes.Pill,
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                        ) {
                                            Text(
                                                text = seLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

                                // Type Pill
                                if (state.episode.episodeType != null && state.episode.episodeType != "full") {
                                    item {
                                        Surface(
                                            shape = cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes.Pill,
                                            color = MaterialTheme.colorScheme.errorContainer,
                                        ) {
                                            Text(
                                                text = state.episode.episodeType!!.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ACTION ROW (Play Button + Progress) - Flat design
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 16.dp)
                        ) {
                            // Prepare Progress Data
                            val progress = if (state.durationMs > 0) (state.resumePositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
                            val remainingSeconds = if (state.durationMs > 0) (state.durationMs - state.resumePositionMs) / 1000 else 0
                            
                            fun formatRemaining(totalSeconds: Long): String? {
                                if (totalSeconds <= 0) return null
                                val hours = totalSeconds / 3600
                                val minutes = (totalSeconds % 3600) / 60
                                return if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
                            }

                            val isPlaying = state.isPlaying
                            val isLiked = likedEpisodeIds.contains(state.episode.id)
                            val isCompleted = completedEpisodeIds.contains(state.episode.id)

                            // Single Row Layout
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                // Action Buttons Row (Tonal Squircles)
                                cx.aswin.boxcast.core.designsystem.components.AdvancedPlayerControls(
                                    isLiked = isLiked,
                                    isDownloaded = isDownloaded, 
                                    isDownloading = isDownloading,
                                    colorScheme = MaterialTheme.colorScheme,
                                    onLikeClick = { viewModel.onToggleLike(state.episode) },
                                    onDownloadClick = { viewModel.toggleDownload(state.episode) },
                                    onQueueClick = { viewModel.toggleQueue() },
                                    style = cx.aswin.boxcast.core.designsystem.components.ControlStyle.TonalSquircle,
                                    overrideColor = accentColor, // Enforce accent color
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    showAddQueueIcon = true,
                                    isQueued = queuedEpisodeIds.contains(state.episode.id),
                                    showShareButton = false,
                                    isPlayed = isCompleted,
                                    onMarkPlayedClick = { viewModel.onToggleCompletion() },
                                    controlSize = 40.dp
                                )
                                
                                // Play Button (Integrated Pill) — fills remaining space
                                cx.aswin.boxcast.core.designsystem.components.ExpressivePlayButton(
                                    onClick = { viewModel.onMainActionClick(entryPointContext) },
                                    isPlaying = isPlaying, 
                                    isResume = state.resumePositionMs > 0,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    progress = progress,
                                    timeText = formatRemaining(remainingSeconds),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .weight(1f) // fill remaining space — no clipping
                                )
                            }
                        }
                    }

                    // One-time mark-played tooltip
                    if (showMarkPlayedTip) {
                        item {
                            var tipVisible by remember { mutableStateOf(true) }
                            
                            LaunchedEffect(Unit) {
                                delay(4000)
                                tipVisible = false
                                onMarkPlayedTipDismissed()
                            }
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = tipVisible,
                                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) + 
                                        androidx.compose.animation.slideInVertically(initialOffsetY = { -it/2 }),
                                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(500))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp, bottom = 8.dp), // Align with the controls on the left
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shadowElevation = 4.dp
                                    ) {
                                        Text(
                                            text = "Tap to mark played ↑",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // DESCRIPTION CARD -> Styled
                    if (state.episode.description.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "About this episode",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    HtmlText(
                                        text = state.episode.description,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = 24.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // UNIFIED "MORE FROM PODCAST" SECTION -> Card
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                // Clickable header - "More from Podcast" with arrow
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .expressiveClickable { 
                                            viewModel.onPodcastLinkClicked()
                                            onPodcastClick(state.podcastId) 
                                        }
                                        .padding(horizontal = 24.dp)
                                        .padding(bottom = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "More from ${state.podcastTitle}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Go to podcast",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                // Horizontal episodes row
                                val relatedListState = rememberLazyListState()
                                LaunchedEffect(relatedListState.isScrollInProgress) {
                                    if (relatedListState.isScrollInProgress) {
                                        viewModel.onRelatedEpisodesScrolled()
                                    }
                                }
                                LazyRow(
                                    state = relatedListState,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    if (state.relatedEpisodesLoading) {
                                        // Skeleton loaders
                                        items(4) {
                                            val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                            
                                            Column(
                                                modifier = Modifier.width(120.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                // Skeleton artwork with shimmer
                                                Box(
                                                    modifier = Modifier
                                                        .size(120.dp)
                                                        .clip(MaterialTheme.shapes.medium)
                                                        .background(baseColor)
                                                        .m3Shimmer(baseColor, highlightColor)
                                                )
                                                
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                // Skeleton text with shimmer
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(14.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(baseColor)
                                                        .m3Shimmer(baseColor, highlightColor)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.7f)
                                                        .height(14.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(baseColor)
                                                        .m3Shimmer(baseColor, highlightColor)
                                                )
                                            }
                                        }
                                    } else if (state.relatedEpisodes.isNotEmpty()) {
                                        // Actual episodes - ElevatedCard style like RisingCard
                                        items(state.relatedEpisodes) { episode ->
                                            androidx.compose.material3.ElevatedCard(
                                                shape = MaterialTheme.shapes.large,
                                                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                                ),
                                                modifier = Modifier
                                                    .width(140.dp)
                                                    .expressiveClickable { 
                                                        viewModel.onRelatedEpisodeClicked()
                                                        onEpisodeClick(episode) 
                                                    }
                                            ) {
                                                Column {
                                                    // Episode Artwork
                                                    OptimizedImage(
                                                        url = episode.imageUrl?.ifEmpty { state.episode.podcastImageUrl },
                                                        proxyWidth = 300, // 140dp thumbnails
                                                        contentDescription = episode.title,
                                                        modifier = Modifier
                                                            .size(140.dp)
                                                            .clip(MaterialTheme.shapes.medium),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    
                                                    // Title in card footer - minLines for even sizing
                                                    Text(
                                                        text = episode.title,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        minLines = 3,
                                                        maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // No episodes message
                                        item {
                                            Text(
                                                text = "No other episodes available",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                // HEADER OVERLAY (Back button + animated background)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(collapsedHeaderHeight)
                        .background(headerColor)
                        .statusBarsPadding()
                ) {
                    // Back Button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // FLOATING TITLE - physically moves from body to header
                Text(
                    text = episodeTitle,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = titleHorizontalPadding)
                        .graphicsLayer { 
                            translationY = titleTranslationY
                            alpha = titleAlpha 
                        }
                )
            }
        }
    }
