package cx.aswin.boxcast.feature.player.v2

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import cx.aswin.boxcast.core.data.DownloadRepository
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import cx.aswin.boxcast.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxcast.core.designsystem.theme.LocalSharedTransitionScope
import cx.aswin.boxcast.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxcast.core.designsystem.theme.generateBrandColorScheme
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.player.extractSeedColor
import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry
import cx.aswin.boxcast.feature.player.v2.chrome.playerSheetShape
import cx.aswin.boxcast.feature.player.v2.full.FullPlayerV2
import cx.aswin.boxcast.feature.player.v2.mini.MiniPlayerV2
import cx.aswin.boxcast.feature.player.v2.mini.SwipeableMiniPlayerV2
import cx.aswin.boxcast.feature.player.v2.logic.PlayerSheetDragLogic
import cx.aswin.boxcast.feature.player.v2.motion.PlayerSheetMotionController
import cx.aswin.boxcast.feature.player.v2.motion.rememberPlayerSheetVisualState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerSheetState { COLLAPSED, EXPANDED }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerSheetV2(
    playbackRepository: PlaybackRepository,
    downloadRepository: DownloadRepository,
    userPrefs: UserPreferencesRepository,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = PlayerChromeGeometry.MiniPlayerHorizontalInset,
    expandTrigger: Long = 0L,
    onEpisodeInfoClick: (Episode) -> Unit = {},
    onPodcastInfoClick: (Podcast) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by playbackRepository.playerState.collectAsState()
    val episode = state.currentEpisode ?: return
    val podcast = state.currentPodcast

    var isFullscreenVideo by rememberSaveable(inputs = arrayOf(episode.id)) { mutableStateOf(false) }

    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val window = (context as? android.app.Activity)?.window

    val hasSeenSwipeDismissTip by userPrefs.hasSeenSwipeDismissTip.collectAsState(initial = true)
    val hasSeenTitleTapTip by userPrefs.hasSeenTitleTapTip.collectAsState(initial = true)
    val hasSeenSwipeMinimizeTip by userPrefs.hasSeenSwipeMinimizeTip.collectAsState(initial = true)

    val surfaceStyle = LocalSurfaceStyle.current
    val effectiveDarkTheme = LocalEffectiveDarkTheme.current

    SideEffect {
        window?.let { win ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
            insetsController.isAppearanceLightStatusBars = !effectiveDarkTheme
            insetsController.isAppearanceLightNavigationBars = !effectiveDarkTheme
        }
    }

    var extractedColorScheme by remember { mutableStateOf<ColorScheme?>(null) }
    val colorScheme = extractedColorScheme ?: MaterialTheme.colorScheme

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(episode.imageUrl)
            .size(Size(100, 100))
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build(),
    )

    LaunchedEffect(episode.imageUrl, painter.state, effectiveDarkTheme, surfaceStyle) {
        val painterState = painter.state
        if (painterState is AsyncImagePainter.State.Success) {
            val bitmap = (painterState.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val seedColor = extractSeedColor(bitmap)
                extractedColorScheme = generateBrandColorScheme(seedColor, effectiveDarkTheme, surfaceStyle)
            }
        }
    }

    var currentSheetContentState by remember { mutableStateOf(PlayerSheetState.COLLAPSED) }
    val playerContentExpansionFraction = remember { Animatable(0f) }
    val sheetAnimationMutex = remember { MutatorMutex() }
    val visualOvershootScaleY = remember { Animatable(1f) }

    val miniPlayerHeightPx = remember(density) {
        with(density) { PlayerChromeGeometry.MiniPlayerHeight.toPx() }
    }
    val sheetExpandedTargetY = 0f
    val sheetAnimationSpec = remember {
        tween<Float>(durationMillis = PlayerChromeGeometry.SheetAnimationDurationMs, easing = FastOutSlowInEasing)
    }

    val initialY = if (currentSheetContentState == PlayerSheetState.EXPANDED) sheetExpandedTargetY else sheetCollapsedTargetY
    val currentSheetTranslationY = remember { Animatable(initialY) }

    LaunchedEffect(sheetCollapsedTargetY) {
        if (currentSheetContentState == PlayerSheetState.COLLAPSED && !currentSheetTranslationY.isRunning) {
            currentSheetTranslationY.snapTo(sheetCollapsedTargetY)
        }
    }

    val motionController = remember {
        PlayerSheetMotionController(
            translationY = currentSheetTranslationY,
            expansionFraction = playerContentExpansionFraction,
            mutex = sheetAnimationMutex,
            defaultAnimationSpec = sheetAnimationSpec,
        )
    }

    val visualState = rememberPlayerSheetVisualState(
        expansionFraction = playerContentExpansionFraction.value,
        horizontalPaddingCollapsed = collapsedStateHorizontalPadding,
        containerHeight = containerHeight,
    )

    suspend fun animatePlayerSheet(
        targetExpanded: Boolean,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = sheetAnimationSpec,
        initialVelocity: Float = 0f,
    ) {
        motionController.animateTo(
            targetExpanded = targetExpanded,
            collapsedY = sheetCollapsedTargetY,
            animationSpec = animationSpec,
            initialVelocity = initialVelocity,
        )
    }

    LaunchedEffect(expandTrigger) {
        if (expandTrigger > 0L) {
            currentSheetContentState = PlayerSheetState.EXPANDED
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.startSession(
                podcast?.id, episode.id, podcast?.title, episode.title,
            )
            animatePlayerSheet(targetExpanded = true)
        }
    }

    BackHandler(enabled = currentSheetContentState == PlayerSheetState.EXPANDED && !isFullscreenVideo) {
        scope.launch {
            launch {
                val currentFraction = playerContentExpansionFraction.value
                visualOvershootScaleY.snapTo(lerp(1.0f, 0.97f, currentFraction))
                visualOvershootScaleY.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessVeryLow,
                    ),
                )
            }
            launch { animatePlayerSheet(targetExpanded = false) }
            currentSheetContentState = PlayerSheetState.COLLAPSED
            cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.endSession()
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    var accumulatedDragYSinceStart by remember { mutableFloatStateOf(0f) }

    val sheetShape by remember {
        derivedStateOf {
            playerSheetShape(
                topStart = visualState.topCornerRadius,
                topEnd = visualState.topCornerRadius,
                bottomStart = visualState.bottomCornerRadius,
                bottomEnd = visualState.bottomCornerRadius,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, currentSheetTranslationY.value.roundToInt()) }
            .graphicsLayer { clip = false }
            .height(visualState.playerContentAreaHeight),
    ) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { clip = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = visualState.horizontalPadding)
                    .height(visualState.playerContentAreaHeight)
                    .graphicsLayer {
                        scaleY = visualOvershootScaleY.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .shadow(elevation = visualState.elevation, shape = sheetShape, clip = false)
                    .background(color = colorScheme.primaryContainer, shape = sheetShape)
                    .clip(sheetShape)
                    .pointerInput(isFullscreenVideo) {
                        if (isFullscreenVideo) return@pointerInput
                        var initialFractionOnDragStart = 0f
                        var initialYOnDragStart = 0f

                        detectVerticalDragGestures(
                            onDragStart = {
                                scope.launch { motionController.stop() }
                                isDragging = true
                                velocityTracker.resetTracking()
                                initialFractionOnDragStart = playerContentExpansionFraction.value
                                initialYOnDragStart = currentSheetTranslationY.value
                                accumulatedDragYSinceStart = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDragYSinceStart += dragAmount
                                scope.launch {
                                    val newY = PlayerSheetDragLogic.clampDragTranslationY(
                                        proposedY = currentSheetTranslationY.value + dragAmount,
                                        expandedY = sheetExpandedTargetY,
                                        collapsedY = sheetCollapsedTargetY,
                                        miniPlayerHeightPx = miniPlayerHeightPx,
                                    )
                                    currentSheetTranslationY.snapTo(newY)
                                    val newFraction = PlayerSheetDragLogic.expansionFractionFromDrag(
                                        initialFraction = initialFractionOnDragStart,
                                        initialTranslationY = initialYOnDragStart,
                                        newTranslationY = newY,
                                        collapsedY = sheetCollapsedTargetY,
                                        expandedY = sheetExpandedTargetY,
                                    )
                                    playerContentExpansionFraction.snapTo(newFraction)
                                }
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            },
                            onDragEnd = {
                                isDragging = false
                                val verticalVelocity = velocityTracker.calculateVelocity().y
                                val currentFraction = playerContentExpansionFraction.value
                                val minDragThresholdPx = with(density) { 5.dp.toPx() }

                                val targetState = PlayerSheetDragLogic.resolveTargetState(
                                    accumulatedDragY = accumulatedDragYSinceStart,
                                    verticalVelocity = verticalVelocity,
                                    expansionFraction = currentFraction,
                                    minDragThresholdPx = minDragThresholdPx,
                                )

                                scope.launch {
                                    if (targetState == PlayerSheetState.EXPANDED) {
                                        if (currentSheetContentState != PlayerSheetState.EXPANDED) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                                "expanded", podcast?.id, episode.id, podcast?.title, episode.title,
                                            )
                                        }
                                        launch { animatePlayerSheet(targetExpanded = true) }
                                        currentSheetContentState = PlayerSheetState.EXPANDED
                                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.startSession(
                                            podcast?.id, episode.id, podcast?.title, episode.title,
                                        )
                                    } else {
                                        launch {
                                            visualOvershootScaleY.snapTo(lerp(1.0f, 0.97f, currentFraction))
                                            visualOvershootScaleY.animateTo(
                                                1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessVeryLow,
                                                ),
                                            )
                                        }
                                        launch {
                                            animatePlayerSheet(
                                                targetExpanded = false,
                                                animationSpec = spring(
                                                    dampingRatio = lerp(
                                                        Spring.DampingRatioNoBouncy,
                                                        Spring.DampingRatioLowBouncy,
                                                        currentFraction,
                                                    ),
                                                    stiffness = Spring.StiffnessLow,
                                                ),
                                                initialVelocity = verticalVelocity,
                                            )
                                        }
                                        currentSheetContentState = PlayerSheetState.COLLAPSED
                                        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.endSession()
                                    }
                                }
                                accumulatedDragYSinceStart = 0f
                            },
                        )
                    }
                    .clickable(
                        enabled = !isFullscreenVideo,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                    "expanded", podcast?.id, episode.id, podcast?.title, episode.title,
                                )
                                launch { animatePlayerSheet(targetExpanded = true) }
                                currentSheetContentState = PlayerSheetState.EXPANDED
                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.startSession(
                                    podcast?.id, episode.id, podcast?.title, episode.title,
                                )
                            } else {
                                launch {
                                    val currentFraction = playerContentExpansionFraction.value
                                    visualOvershootScaleY.snapTo(lerp(1.0f, 0.97f, currentFraction))
                                    visualOvershootScaleY.animateTo(
                                        1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessVeryLow,
                                        ),
                                    )
                                }
                                launch { animatePlayerSheet(targetExpanded = false) }
                                currentSheetContentState = PlayerSheetState.COLLAPSED
                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.endSession()
                            }
                        }
                    },
            ) {
                Crossfade(
                    targetState = colorScheme,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "playerColorScheme",
                ) { scheme ->
                    SharedTransitionLayout {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                SwipeableMiniPlayerV2(
                                    isPlaying = state.isPlaying,
                                    onDismiss = {
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                            "dismissed", podcast?.id, episode.id, podcast?.title, episode.title,
                                        )
                                        playbackRepository.clearSession()
                                    },
                                    backgroundColor = scheme.primaryContainer,
                                    showSwipeTip = !hasSeenSwipeDismissTip && !state.isPlaying,
                                    onSwipeTipDismissed = { scope.launch { userPrefs.markSwipeDismissTipSeen() } },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .graphicsLayer { alpha = visualState.miniAlpha }
                                        .zIndex(if (playerContentExpansionFraction.value < 0.5f) 1f else 0f),
                                ) {
                                    MiniPlayerV2(
                                        episode = episode,
                                        podcastTitle = podcast?.title ?: "",
                                        podcastImageUrl = podcast?.imageUrl,
                                        isPlaying = state.isPlaying,
                                        isLoading = state.isLoading,
                                        position = state.position,
                                        duration = state.duration,
                                        colorScheme = scheme,
                                        onPlayPause = {
                                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                                "play_pause", podcast?.id, episode.id, podcast?.title, episode.title,
                                            )
                                            if (state.isPlaying) playbackRepository.pause()
                                            else playbackRepository.resume(
                                                android.os.Bundle().apply { putString("entry_point", "resume_mini_player") },
                                            )
                                        },
                                        onPrevious = {
                                            playbackRepository.skipBackward()
                                        },
                                        onNext = {
                                            playbackRepository.skipForward()
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                val isExpanded = playerContentExpansionFraction.value >= 0.5f

                                Box(
                                    modifier = Modifier
                                        .height(containerHeight)
                                        .graphicsLayer {
                                            alpha = maxOf(visualState.fullPlayerAlpha, if (currentSheetContentState == PlayerSheetState.COLLAPSED) 0.001f else 0f)
                                            translationY = visualState.fullPlayerTranslationY
                                        }
                                        .zIndex(if (isExpanded) 1f else 0f)
                                        .offset {
                                            if (playerContentExpansionFraction.value <= 0.01f) {
                                                IntOffset(0, 10000)
                                            } else {
                                                IntOffset.Zero
                                            }
                                        },
                                ) {
                                    FullPlayerV2(
                                        playbackRepository = playbackRepository,
                                        downloadRepository = downloadRepository,
                                        colorScheme = scheme,
                                        isFullscreenVideo = isFullscreenVideo,
                                        onFullscreenVideoChange = { isFullscreenVideo = it },
                                        onCollapse = {
                                            scope.launch {
                                                launch {
                                                    val currentFraction = playerContentExpansionFraction.value
                                                    visualOvershootScaleY.snapTo(lerp(1.0f, 0.97f, currentFraction))
                                                    visualOvershootScaleY.animateTo(
                                                        1f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessVeryLow,
                                                        ),
                                                    )
                                                }
                                                launch { animatePlayerSheet(targetExpanded = false) }
                                                currentSheetContentState = PlayerSheetState.COLLAPSED
                                                cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.endSession()
                                            }
                                        },
                                        onEpisodeInfoClick = onEpisodeInfoClick,
                                        onPodcastInfoClick = onPodcastInfoClick,
                                        showSwipeMinimizeTip = !hasSeenSwipeMinimizeTip,
                                        onSwipeMinimizeTipDismissed = { scope.launch { userPrefs.markSwipeMinimizeTipSeen() } },
                                        showTitleTip = !hasSeenTitleTapTip,
                                        onTitleTipDismissed = { scope.launch { userPrefs.markTitleTapTipSeen() } },
                                        isExpanded = isExpanded,
                                        prewarmOnly = currentSheetContentState == PlayerSheetState.COLLAPSED,
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

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
