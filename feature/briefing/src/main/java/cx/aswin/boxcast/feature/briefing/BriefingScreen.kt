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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
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
        onSeekTo = viewModel::seekTo,
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
    onSeekTo: (Long) -> Unit,
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
                        chapters = successState.chapters,
                        isPlaying = successState.isPlaying,
                        isResume = successState.currentPosition > 0,
                        progress = progress,
                        timeText = timeText,
                        accentColor = accentColor,
                        currentPositionMs = successState.currentPosition,
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
                        onSeekTo = onSeekTo,
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
                    text = "The Boxcast Brief",
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


@Composable
fun BriefingContent(
    briefing: Briefing,
    chapters: List<cx.aswin.boxcast.core.model.Chapter>,
    isPlaying: Boolean,
    isResume: Boolean,
    progress: Float,
    timeText: String?,
    accentColor: Color,
    currentPositionMs: Long,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    contentTopPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
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
                    .alpha(0.35f)
                    .blur(40.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
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

            // Cover Art Card - Retain standard rounded corners & elevation as per M3
            Card(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .size(240.dp)
                    .shadow(8.dp, RoundedCornerShape(28.dp))
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

            // Custom Show Branding Tag
            Text(
                text = "THE BOXCAST BRIEF",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 2.sp
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Title
            Text(
                text = briefing.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle Details (Show Region and formatted Date)
            val regionName = when (briefing.region.lowercase()) {
                "in" -> "India"
                "us" -> "United States"
                "uk" -> "United Kingdom"
                else -> "Global"
            }
            val displayDate = remember(briefing.date) {
                try {
                    val date = java.time.LocalDate.parse(briefing.date)
                    date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                } catch (e: Exception) {
                    briefing.date
                }
            }
            Text(
                text = "$regionName • $displayDate • 3 min listen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Row: Prominent Play Button (reusing design system ExpressivePlayButton)
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

            Spacer(modifier = Modifier.height(32.dp))

            // Interactive Chapter Timeline
            val paragraphs = remember(briefing.script) {
                briefing.script.split("\n\n").filter { it.isNotBlank() }
            }

            if (chapters.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Stories in this briefing",
                        fontFamily = SectionHeaderFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    chapters.forEachIndexed { index, chapter ->
                        val isActive = currentPositionMs >= chapter.startTime * 1000 &&
                                (index == chapters.size - 1 || currentPositionMs < chapters[index + 1].startTime * 1000)

                        val paragraph = paragraphs.getOrNull(index) ?: ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            // Left Timeline bar
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(36.dp)
                            ) {
                                // Dynamic indicator dot
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            if (isActive) accentColor 
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )
                                )
                                // Vertical line connecting steps
                                if (index < chapters.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .weight(1f)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    )
                                }
                            }

                            // Interactive Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                                    else MaterialTheme.colorScheme.surfaceContainer
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isActive) accentColor.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 16.dp)
                                    .expressiveClickable(
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = {
                                            if (currentPositionMs == 0L) {
                                                onPlayPauseClick()
                                            }
                                            onSeekTo(chapter.startTime.toLong() * 1000L)
                                        }
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatChapterTime(chapter.startTime.toLong()),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isActive) accentColor
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (paragraph.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = paragraph.trim(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 22.sp,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = if (isActive) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Fallback to plain paragraphs if chapters aren't loaded yet
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

                        Spacer(modifier = Modifier.height(16.dp))

                        paragraphs.forEachIndexed { index, paragraph ->
                            Text(
                                text = paragraph.trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (index < paragraphs.size - 1) {
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // References & Sources Panel (Horizontal Carousel lazy row)
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

                    val uriHandler = LocalUriHandler.current
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(briefing.sources) { source ->
                            val cleanDomain = remember(source.url) { getDomainName(source.url) }
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(115.dp)
                                    .expressiveClickable(
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = { uriHandler.openUri(source.url) }
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Styled domain badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        Text(
                                            text = cleanDomain.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    // Article Title
                                    Text(
                                        text = source.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(top = 4.dp)
                                    )

                                    // Link indicator
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
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

private fun formatChapterTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
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
