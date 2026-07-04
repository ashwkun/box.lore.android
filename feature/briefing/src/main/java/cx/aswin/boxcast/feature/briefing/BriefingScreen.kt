package cx.aswin.boxcast.feature.briefing

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.ExpressivePlayButton
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Briefing
import cx.aswin.boxcast.core.model.Episode
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
    queueManager: cx.aswin.boxcast.core.data.QueueManager,
    onBackClick: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeedbackClick: () -> Unit,
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
                    queueManager = queueManager,
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
        onEpisodeClick = onEpisodeClick,
        onFeedbackClick = onFeedbackClick,
        initialRegion = initialRegion,
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
    onPlayPauseClick: (Briefing, Long?) -> Unit,
    onSeekTo: (Long) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeedbackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialRegion: String? = null,
    bottomContentPadding: Dp = 0.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val currentRegion = when (uiState) {
        is BriefingUiState.Loading -> "in"
        is BriefingUiState.Success -> uiState.selectedRegion
        is BriefingUiState.Error -> uiState.selectedRegion
    }

    // Shared scroll state (hoisted so header can react to content scroll)
    val scrollState = rememberScrollState()

    // Header animation (matches EpisodeInfoScreen pattern)
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedHeaderHeight = 64.dp + statusBarHeight
    val morphThreshold = with(density) { 180.dp.toPx() }
    val scrollFraction = (scrollState.value.toFloat() / morphThreshold).coerceIn(0f, 1f)

    // Header background: transparent → surfaceContainer on scroll
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val headerColor by animateColorAsState(
        targetValue = surfaceColor.copy(alpha = scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headerColor"
    )
    // Title fades in only when header is nearly collapsed
    val titleAlpha = if (scrollFraction > 0.6f) (scrollFraction - 0.6f) / 0.4f else 0f

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
                        BoxLoreLoader.Expressive(size = 64.dp)
                    }
                }
                "success" -> {
                    val successState = uiState as? BriefingUiState.Success ?: return@Crossfade

                    LaunchedEffect(successState.briefing.region, successState.briefing.date) {
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingScreenViewed(
                            region = successState.briefing.region,
                            date = successState.briefing.date,
                            source = initialRegion?.let { "notification" } ?: "home_banner"
                        )
                    }

                    var extractedColor by remember { mutableStateOf(Color.Transparent) }
                    val accentColor by animateColorAsState(
                        targetValue = if (extractedColor != Color.Transparent) extractedColor else MaterialTheme.colorScheme.primary,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "accent_color"
                    )

                    val coverResId = remember(successState.briefing.region) {
                        when (successState.briefing.region.lowercase()) {
                            "in", "ind" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_india
                            "uk", "gb" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_uk
                            "us", "usa" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_usa
                            else -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_global
                        }
                    }
                    val painter = rememberAsyncImagePainter(
                        model = remember(coverResId) {
                            ImageRequest.Builder(context)
                                .data(coverResId)
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

                    var showSourcesBottomSheet by remember { mutableStateOf(false) }

                    BriefingContent(
                        briefing = successState.briefing,
                        chapters = successState.chapters,
                        isPlaying = successState.isPlaying,
                        isResume = successState.currentPosition > 0,
                        progress = progress,
                        timeText = timeText,
                        accentColor = accentColor,
                        currentPositionMs = successState.currentPosition,
                        durationMs = successState.duration,
                        currentRegion = successState.selectedRegion,
                        onRegionSelect = onRegionSelect,
                        onShowSources = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingSourcesSheetOpened(
                                region = successState.briefing.region,
                                date = successState.briefing.date,
                                sourcesCount = successState.briefing.sources.size
                            )
                            showSourcesBottomSheet = true 
                        },
                        onPlayPauseClick = { initialPositionMs ->
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
                            onPlayPauseClick(successState.briefing, initialPositionMs)
                        },
                        onSeekTo = onSeekTo,
                        onEpisodeClick = onEpisodeClick,
                        onFeedbackClick = onFeedbackClick,
                        scrollState = scrollState,
                        contentTopPadding = collapsedHeaderHeight,
                        bottomContentPadding = bottomContentPadding
                    )

                    if (showSourcesBottomSheet) {
                        val uriHandler = LocalUriHandler.current
                        ModalBottomSheet(
                            onDismissRequest = { showSourcesBottomSheet = false },
                            shape = MaterialTheme.shapes.extraLarge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = "References & Sources",
                                    fontFamily = SectionHeaderFontFamily,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                androidx.compose.foundation.lazy.LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(successState.briefing.sources) { source ->
                                        val cleanDomain = remember(source.url) { getDomainName(source.url) }
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .expressiveClickable(
                                                    shape = RoundedCornerShape(16.dp),
                                                    onClick = { 
                                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingSourceClicked(
                                                            region = successState.briefing.region,
                                                            date = successState.briefing.date,
                                                            sourceTitle = source.title,
                                                            sourceUrl = source.url
                                                        )
                                                        uriHandler.openUri(source.url)
                                                    }
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        modifier = Modifier.padding(bottom = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = cleanDomain.uppercase(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = source.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Rounded.Link,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp).padding(start = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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

        // FLOATING HEADER OVERLAY — adapts on scroll like other screens
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(collapsedHeaderHeight)
                .background(headerColor)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            // Centered Title — fades in on scroll
            Image(
                painter = painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.ic_boxlore_brief_logo),
                contentDescription = "The Boxlore Brief",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .height(40.dp)
                    .graphicsLayer { alpha = titleAlpha }
            )

            // Back button on the left
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Go back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(8.dp)
                )
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
    durationMs: Long,
    currentRegion: String,
    onRegionSelect: (String) -> Unit,
    onShowSources: () -> Unit,
    onPlayPauseClick: (Long?) -> Unit,
    onSeekTo: (Long) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeedbackClick: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    contentTopPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val morphThreshold = with(density) { 180.dp.toPx() }
    val scrollFraction = (scrollState.value.toFloat() / morphThreshold).coerceIn(0f, 1f)

    val pagerState = rememberPagerState(pageCount = { chapters.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    var userClickedPage by remember { mutableStateOf<Int?>(null) }

    // 1. Playback -> UI Paging Sync
    // Find the currently active chapter index based on playback position
    val activeChapterIndex = remember(chapters, currentPositionMs) {
        val index = chapters.indexOfLast { currentPositionMs >= it.startTime * 1000 }
        if (index != -1) index else 0
    }

    LaunchedEffect(activeChapterIndex) {
        if (!isDragged) {
            val clickedPage = userClickedPage
            if (clickedPage != null) {
                if (activeChapterIndex == clickedPage) {
                    userClickedPage = null
                }
            } else if (activeChapterIndex >= 0 && activeChapterIndex < chapters.size) {
                pagerState.animateScrollToPage(activeChapterIndex)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val chapter = chapters.getOrNull(pagerState.currentPage)
        if (chapter != null && (isDragged || userClickedPage != null)) {
            val method = if (isDragged) "swipe" else "click"
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingChapterSwiped(
                region = briefing.region,
                date = briefing.date,
                chapterIndex = pagerState.currentPage,
                chapterTitle = chapter.title,
                method = method
            )
        }
    }

    val paragraphs = remember(briefing.script) {
        val raw = briefing.script.split("\n\n").filter { it.isNotBlank() }
        raw.mapIndexed { index, paragraph ->
            var text = paragraph.trim()
            if (index == 0) {
                val greetingPrefixes = listOf(
                    "This is the boxlore brief for",
                    "This is the boxcast brief for",
                    "Welcome to the boxlore brief for",
                    "Welcome to the boxcast brief for",
                    "Welcome to the daily brief for"
                )
                for (prefix in greetingPrefixes) {
                    if (text.startsWith(prefix, ignoreCase = true)) {
                        val periodIndex = text.indexOf('.')
                        if (periodIndex != -1 && periodIndex < 120) {
                            text = text.substring(periodIndex + 1).trim()
                        }
                        break
                    }
                }
            }
            if (index == raw.lastIndex) {
                val outroSubstrings = listOf(
                    "That's your boxlore brief. See you tomorrow.",
                    "That's your boxcast brief. See you tomorrow.",
                    "That's your boxlore brief. See you tomorrow",
                    "That's your boxcast brief. See you tomorrow",
                    "See you tomorrow.",
                    "See you tomorrow"
                )
                for (outro in outroSubstrings) {
                    val outroIndex = text.indexOf(outro, ignoreCase = true)
                    if (outroIndex != -1) {
                        text = text.substring(0, outroIndex).trim()
                        break
                    }
                }
                val lastBoxloreIndex = text.lastIndexOf("boxlore brief", ignoreCase = true)
                val lastBoxcastIndex = text.lastIndexOf("boxcast brief", ignoreCase = true)
                val lastIndex = lastBoxloreIndex.coerceAtLeast(lastBoxcastIndex)
                if (lastIndex != -1 && text.length - lastIndex < 100) {
                    val lastPeriod = text.lastIndexOf('.', text.length - 2)
                    if (lastPeriod != -1) {
                        text = text.substring(0, lastPeriod + 1).trim()
                    }
                }
            }
            text
        }.filter { it.isNotBlank() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        // Blurred Background Header (only at the top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentTopPadding + 240.dp)
                .clipToBounds()
                .graphicsLayer {
                    translationY = -scrollState.value * 0.5f
                    alpha = 1f - scrollFraction
                }
        ) {
            val coverResId = remember(briefing.region) {
                when (briefing.region.lowercase()) {
                    "in", "ind" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_india
                    "uk", "gb" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_uk
                    "us", "usa" -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_usa
                    else -> cx.aswin.boxcast.core.designsystem.R.drawable.daily_briefing_global
                }
            }
            OptimizedImage(
                url = "android.resource://${context.packageName}/$coverResId",
                proxyWidth = 600,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                    .alpha(0.55f),
                contentScale = ContentScale.Crop
            )
            // Gradient overlay to blend into the solid background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.7f to MaterialTheme.colorScheme.background,
                                1.0f to MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(top = contentTopPadding, bottom = bottomContentPadding + 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Page title (visible in body, fades into header on scroll)
            Image(
                painter = painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.ic_boxlore_brief_logo),
                contentDescription = "The Boxlore Brief",
                colorFilter = ColorFilter.tint(accentColor),
                modifier = Modifier
                    .height(72.dp)
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle details (Show formatted Date and Duration)
            val displayDate = remember(briefing.date) {
                try {
                    val date = java.time.LocalDate.parse(briefing.date)
                    date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                } catch (e: Exception) {
                    briefing.date
                }
            }

            val durationMin = remember(durationMs) {
                val totalSeconds = durationMs / 1000
                val mins = (totalSeconds + 30) / 60
                if (mins > 0) mins else 3
            }

            Text(
                text = "$displayDate • $durationMin min listen".uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title of current briefing
            Text(
                text = briefing.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Region Selector Chips Row
            val regions = listOf(
                "in" to "India",
                "us" to "US",
                "uk" to "UK",
                "global" to "Global"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                regions.forEach { (code, label) ->
                    val isSelected = currentRegion == code
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = isSelected,
                        onClick = {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingRegionChanged(
                                previousRegion = currentRegion,
                                newRegion = code,
                                date = briefing.date
                            )
                            onRegionSelect(code)
                        },
                        label = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.Transparent,
                            selectedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Prominent Play Briefing Button
            ExpressivePlayButton(
                onClick = { onPlayPauseClick(null) },
                isPlaying = isPlaying,
                isResume = isResume,
                accentColor = accentColor,
                progress = progress,
                timeText = timeText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Swipable cards pager
            if (chapters.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                ) { page ->
                    val chapter = chapters[page]
                    val paragraph = paragraphs.getOrNull(page) ?: ""
                    val isThisCardActive = activeChapterIndex == page && isPlaying

                    Card(
                       shape = RoundedCornerShape(24.dp),
                       colors = CardDefaults.cardColors(
                           containerColor = if (isThisCardActive) MaterialTheme.colorScheme.primaryContainer
                                           else MaterialTheme.colorScheme.surfaceContainerHigh
                       ),
                       border = BorderStroke(
                           1.dp,
                           if (isThisCardActive) accentColor
                           else MaterialTheme.colorScheme.outlineVariant
                       ),
                       modifier = Modifier
                           .fillMaxWidth()
                           .fillMaxHeight()
                           .shadow(6.dp, RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Card Header: STORY X OF Y & Time Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = "STORY ${page + 1} OF ${chapters.size}".uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Text(
                                        text = formatChapterTime(chapter.startTime.toLong()),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isThisCardActive) accentColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Story Headline
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Story Paragraph (Scrollable inside the card)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = paragraph.trim(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 22.sp,
                                            color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )


                                    }
                                }
                            }

                            // Related Episodes inline (compact row)
                            val recs = chapter.relatedEpisodes
                            if (!recs.isNullOrEmpty()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    thickness = 0.8.dp,
                                    color = if (isThisCardActive) Color.White.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Podcasts,
                                            contentDescription = null,
                                            tint = if (isThisCardActive) Color.White.copy(alpha = 0.6f)
                                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "LISTEN DEEPER",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                letterSpacing = 1.2.sp,
                                                fontSize = 9.sp
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isThisCardActive) Color.White.copy(alpha = 0.7f)
                                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(recs) { episode ->
                                            CompactEpisodeChip(
                                                episode = episode,
                                                isActiveCard = isThisCardActive,
                                                accentColor = accentColor,
                                                onClick = {
                                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingRelatedEpisodeClicked(
                                                        region = briefing.region,
                                                        date = briefing.date,
                                                        chapterIndex = page,
                                                        episodeId = episode.id,
                                                        episodeTitle = episode.title,
                                                        podcastId = episode.podcastId ?: "",
                                                        podcastTitle = episode.podcastTitle ?: ""
                                                    )
                                                    onEpisodeClick(episode)
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Card prominent play button
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isThisCardActive) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .expressiveClickable(
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = {
                                            if (isThisCardActive) {
                                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingStoryPauseClicked(
                                                    region = briefing.region,
                                                    date = briefing.date,
                                                    chapterIndex = page,
                                                    chapterTitle = chapter.title,
                                                    startTimeSeconds = chapter.startTime.toLong()
                                                )
                                                onPlayPauseClick(null)
                                            } else {
                                                userClickedPage = page
                                                scope.launch {
                                                    pagerState.animateScrollToPage(page)
                                                }
                                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingStoryPlayClicked(
                                                    region = briefing.region,
                                                    date = briefing.date,
                                                    chapterIndex = page,
                                                    chapterTitle = chapter.title,
                                                    startTimeSeconds = chapter.startTime.toLong()
                                                )
                                                onPlayPauseClick(chapter.startTime.toLong() * 1000L)
                                            }
                                        }
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (isThisCardActive) Icons.Rounded.Pause
                                                      else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isThisCardActive) MaterialTheme.colorScheme.primaryContainer
                                               else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isThisCardActive) "PAUSE STORY" else "PLAY THIS STORY",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isThisCardActive) MaterialTheme.colorScheme.primaryContainer
                                               else MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback if chapters are empty
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading script...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Page indicators dots
            if (chapters.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(chapters.size) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        val color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                                .size(if (isSelected) 8.dp else 6.dp)
                        )
                    }
                }
            }



            Spacer(modifier = Modifier.height(20.dp))

            // Sources — compact button opening bottom sheet
            if (briefing.sources.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .expressiveClickable(
                            shape = RoundedCornerShape(16.dp),
                            onClick = onShowSources
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${briefing.sources.size} Sources",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Disclaimer & Feedback",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "This daily briefing contains AI-generated summary content and audio narration. We do not claim ownership of the underlying news stories, which are compiled and attributed to the original sources listed above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveClickable(
                            shape = RoundedCornerShape(10.dp),
                            onClick = onFeedbackClick
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Report an Issue / Send Feedback",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
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

@Composable
fun VerticalRecommendedEpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        ),
        modifier = modifier
            .width(160.dp)
            .expressiveClickable(
                shape = RoundedCornerShape(16.dp),
                onClick = onClick
            )
    ) {
        Column {
            // Artwork with play overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val imageUrl = episode.imageUrl?.takeIf { it.isNotEmpty() }
                    ?: episode.podcastImageUrl?.takeIf { it.isNotEmpty() }

                OptimizedImage(
                    url = imageUrl,
                    proxyWidth = 320,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient scrim at bottom of artwork
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )


            }

            // Text content
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                val podTitle = episode.podcastTitle
                if (!podTitle.isNullOrEmpty()) {
                    Text(
                        text = podTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val infoText = buildString {
                    if (episode.duration > 0) {
                        append("${episode.duration / 60} min")
                    }
                    if (episode.publishedDate > 0) {
                        if (isNotEmpty()) append(" • ")
                        try {
                            val instant = java.time.Instant.ofEpochSecond(episode.publishedDate)
                            val date = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).toLocalDate()
                            append(date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")))
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                if (infoText.isNotEmpty()) {
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactEpisodeChip(
    episode: Episode,
    isActiveCard: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActiveCard) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            0.5.dp,
            if (isActiveCard) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .width(260.dp)
            .expressiveClickable(
                shape = RoundedCornerShape(16.dp),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val imageUrl = episode.imageUrl?.takeIf { it.isNotEmpty() }
                ?: episode.podcastImageUrl?.takeIf { it.isNotEmpty() }
            OptimizedImage(
                url = imageUrl,
                proxyWidth = 120,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    color = if (isActiveCard) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val podTitle = episode.podcastTitle
                if (!podTitle.isNullOrEmpty()) {
                    Text(
                        text = podTitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isActiveCard) accentColor.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val infoText = buildString {
                    if (episode.duration > 0) {
                        append("${episode.duration / 60} min")
                    }
                    if (episode.publishedDate > 0) {
                        if (isNotEmpty()) append(" • ")
                        try {
                            val instant = java.time.Instant.ofEpochSecond(episode.publishedDate)
                            val date = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).toLocalDate()
                            append(date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")))
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                if (infoText.isNotEmpty()) {
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (isActiveCard) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

