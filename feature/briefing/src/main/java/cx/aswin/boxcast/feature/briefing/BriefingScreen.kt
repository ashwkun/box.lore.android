package cx.aswin.boxcast.feature.briefing

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import cx.aswin.boxcast.core.designsystem.components.ExpressivePlayButton
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Briefing
import java.net.URI

// Color extraction helper
private fun extractDominantColor(bitmap: android.graphics.Bitmap): Color {
    val palette = Palette.from(bitmap).generate()
    val vibrant = palette.vibrantSwatch?.rgb
    val muted = palette.mutedSwatch?.rgb
    val dominant = palette.dominantSwatch?.rgb
    val colorInt = vibrant ?: muted ?: dominant ?: 0xFF6200EE.toInt()
    return Color(colorInt)
}

@Composable
fun BriefingRoute(
    podcastRepository: cx.aswin.boxcast.core.data.PodcastRepository,
    playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialRegion: String? = null,
    bottomContentPadding: Dp = 0.dp
) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel: BriefingViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return BriefingViewModel(
                    application = application,
                    podcastRepository = podcastRepository,
                    playbackRepository = playbackRepository,
                    initialRegion = initialRegion
                ) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    BriefingScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRegionSelect = viewModel::selectRegion,
        onPlayPauseClick = viewModel::togglePlayPause,
        bottomContentPadding = bottomContentPadding,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingScreen(
    uiState: BriefingUiState,
    onBackClick: () -> Unit,
    onRegionSelect: (String) -> Unit,
    onPlayPauseClick: (Briefing) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val currentRegion = when (uiState) {
        is BriefingUiState.Loading -> "in"
        is BriefingUiState.Success -> uiState.selectedRegion
        is BriefingUiState.Error -> uiState.selectedRegion
    }

    var expanded by remember { mutableStateOf(false) }

    // Shared scroll state (hoisted so header can react to content scroll)
    val scrollState = rememberScrollState()

    // Header animation (matches EpisodeInfoScreen pattern)
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedHeaderHeight = 64.dp + statusBarHeight
    val morphThreshold = with(density) { 180.dp.toPx() }
    val scrollFraction = (scrollState.value.toFloat() / morphThreshold).coerceIn(0f, 1f)

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val headerColor by animateColorAsState(
        targetValue = surfaceColor.copy(alpha = scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headerColor"
    )
    // Title fades in only when header is mostly collapsed
    val titleAlpha = if (scrollFraction > 0.8f) (scrollFraction - 0.8f) / 0.2f else 0f

    // Key only on the state TYPE so Crossfade only animates on Loading→Success→Error
    // transitions, NOT on every playback position update (~200ms).
    val stateKey = when (uiState) {
        is BriefingUiState.Loading -> "loading"
        is BriefingUiState.Success -> "success"
        is BriefingUiState.Error -> "error"
    }

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = stateKey,
            label = "BriefingScreenStateCrossfade",
            modifier = Modifier.fillMaxSize()
        ) { key ->
            when (key) {
                "loading" -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        BoxCastLoader.Expressive(size = 64.dp)
                    }
                }
                "success" -> {
                    val successState = uiState as? BriefingUiState.Success ?: return@Crossfade

                    var extractedColor by remember { mutableStateOf(Color.Transparent) }
                    val accentColor by animateColorAsState(
                        targetValue = if (extractedColor != Color.Transparent) extractedColor else MaterialTheme.colorScheme.primary,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "accent_color"
                    )

                    val painter = rememberAsyncImagePainter(
                        model = remember(successState.briefing.coverUrl) {
                            ImageRequest.Builder(context)
                                .data(successState.briefing.coverUrl)
                                .allowHardware(false)
                                .build()
                        }
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

                    val progress = if (successState.duration > 0) successState.currentPosition.toFloat() / successState.duration else 0f
                    val remainingSeconds = if (successState.duration > 0) (successState.duration - successState.currentPosition) / 1000 else 0L
                    val timeText = formatRemaining(remainingSeconds)

                    BriefingContent(
                        briefing = successState.briefing,
                        isPlaying = successState.isPlaying,
                        isResume = successState.currentPosition > 0,
                        progress = progress,
                        timeText = timeText,
                        accentColor = accentColor,
                        onPlayPauseClick = {
                            if (successState.isPlaying) {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingPauseClicked(
                                    region = successState.briefing.region,
                                    date = successState.briefing.date,
                                    source = "briefing_detail"
                                )
                            } else {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingPlayClicked(
                                    region = successState.briefing.region,
                                    date = successState.briefing.date,
                                    source = "briefing_detail"
                                )
                            }
                            onPlayPauseClick(successState.briefing)
                        },
                        scrollState = scrollState,
                        contentTopPadding = collapsedHeaderHeight,
                        bottomContentPadding = bottomContentPadding
                    )
                }
                "error" -> {
                    val errorState = uiState as? BriefingUiState.Error ?: return@Crossfade
                    ErrorContent(
                        message = errorState.message,
                        onRetry = { onRegionSelect(errorState.selectedRegion) }
                    )
                }
            }
        }

        // FLOATING HEADER OVERLAY — transparent → opaque on scroll (matches EpisodeInfoScreen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(collapsedHeaderHeight)
                .background(headerColor)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Go back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Daily Briefing",
                    fontFamily = SectionHeaderFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .alpha(titleAlpha)
                )

                val currentRegionLabel = when (currentRegion.lowercase()) {
                    "in" -> "India"
                    "us" -> "United States"
                    "uk" -> "United Kingdom"
                    else -> "Global"
                }

                Box(modifier = Modifier.padding(end = 12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(36.dp)
                            .expressiveClickable(
                                shape = RoundedCornerShape(12.dp),
                                onClick = { expanded = true }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = currentRegionLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Select Region",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.width(160.dp)
                    ) {
                        val regions = listOf(
                            "in" to "India",
                            "us" to "United States",
                            "uk" to "United Kingdom",
                            "global" to "Global"
                        )
                        regions.forEach { (code, label) ->
                            val isSelected = currentRegion == code
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    onRegionSelect(code)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BriefingContent(
    briefing: Briefing,
    isPlaying: Boolean,
    isResume: Boolean,
    progress: Float,
    timeText: String?,
    accentColor: Color,
    onPlayPauseClick: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    contentTopPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var transcriptExpanded by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val scrollOffset = scrollState.value.toFloat()
    val morphThreshold = with(density) { 180.dp.toPx() }
    val scrollFraction = (scrollOffset / morphThreshold).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Blurred Background Header (Top section blending into background)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentTopPadding + 240.dp)
                .graphicsLayer {
                    translationY = -scrollOffset * 0.5f
                    alpha = 1f - scrollFraction
                }
        ) {
            OptimizedImage(
                url = briefing.coverUrl,
                proxyWidth = 200,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.5f)
                    .blur(50.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Space for the floating header overlay
            Spacer(modifier = Modifier.height(contentTopPadding + 16.dp))

            // Cover Art Card
            Card(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .size(240.dp)
                    .shadow(12.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                OptimizedImage(
                    url = briefing.coverUrl,
                    proxyWidth = 520,
                    contentDescription = briefing.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = briefing.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            val regionName = when (briefing.region.lowercase()) {
                "in" -> "India"
                "us" -> "United States"
                "uk" -> "United Kingdom"
                else -> "Global"
            }
            Text(
                text = "$regionName • ${briefing.date} • 3 min listen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Row: Prominent Play Button (reusing standard design system ExpressivePlayButton)
            ExpressivePlayButton(
                onClick = onPlayPauseClick,
                isPlaying = isPlaying,
                isResume = isResume,
                accentColor = accentColor,
                progress = progress,
                timeText = timeText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Transcript Panel
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transcript",
                            fontFamily = SectionHeaderFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "AI-generated text brief",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val paragraphs = remember(briefing.script) {
                        briefing.script.split("\n\n").filter { it.isNotBlank() }
                    }
                    val isLongTranscript = paragraphs.size > 2

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column {
                            val visibleParagraphs = if (transcriptExpanded || !isLongTranscript) {
                                paragraphs
                            } else {
                                paragraphs.take(2)
                            }

                            visibleParagraphs.forEachIndexed { index, paragraph ->
                                Text(
                                    text = paragraph.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (index < visibleParagraphs.size - 1) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }

                            if (!transcriptExpanded && isLongTranscript) {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        if (!transcriptExpanded && isLongTranscript) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surfaceContainer
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    if (isLongTranscript) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .expressiveClickable(shape = RoundedCornerShape(8.dp)) {
                                    transcriptExpanded = !transcriptExpanded
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (transcriptExpanded) "Show less" else "Read full transcript",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = accentColor.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (transcriptExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = accentColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sources Panel
            if (briefing.sources.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "References & Sources",
                        fontFamily = SectionHeaderFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val uriHandler = LocalUriHandler.current
                        briefing.sources.forEach { source ->
                            val cleanDomain = remember(source.url) { getDomainName(source.url) }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .expressiveClickable(
                                        shape = RoundedCornerShape(12.dp),
                                        onClick = { uriHandler.openUri(source.url) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = source.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = cleanDomain,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Open link",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp + bottomContentPadding))
        }
    }
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.expressiveClickable(onClick = onRetry)
        ) {
            Text(
                text = "Retry",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

private fun formatRemaining(totalSeconds: Long): String? {
    if (totalSeconds <= 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
}

private fun getDomainName(url: String): String {
    return try {
        val uri = URI(url)
        val domain = uri.host ?: ""
        if (domain.startsWith("www.")) domain.substring(4) else domain
    } catch (e: Exception) {
        url
    }
}
