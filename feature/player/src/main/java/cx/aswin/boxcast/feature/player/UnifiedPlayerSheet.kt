package cx.aswin.boxcast.feature.player


import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import cx.aswin.boxcast.core.designsystem.theme.generateBrandColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Constants matching PixelPlayer
val MiniPlayerHeight = 64.dp
private val MiniPlayerBottomSpacer = 8.dp
private const val ANIMATION_DURATION_MS = 255

enum class PlayerSheetState { COLLAPSED, EXPANDED }

/**
 * Unified Player Sheet - Refactored Structure
 */
@Composable
fun UnifiedPlayerSheet(
    playbackRepository: PlaybackRepository,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    userPrefs: UserPreferencesRepository,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    expandTrigger: Long = 0L, // New param: timestamp to force expansion
    onEpisodeInfoClick: (cx.aswin.boxcast.core.model.Episode) -> Unit = {},
    onPodcastInfoClick: (cx.aswin.boxcast.core.model.Podcast) -> Unit = {},
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val state by playbackRepository.playerState.collectAsState()
    val episode = state.currentEpisode
    val podcast = state.currentPodcast
    
    // Don't render if no episode
    if (episode == null) return
    
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val window = (context as? android.app.Activity)?.window

    // Tooltip states
    val hasSeenSwipeDismissTip by userPrefs.hasSeenSwipeDismissTip.collectAsState(initial = true)
    val hasSeenTitleTapTip by userPrefs.hasSeenTitleTapTip.collectAsState(initial = true)
    val hasSeenSwipeMinimizeTip by userPrefs.hasSeenSwipeMinimizeTip.collectAsState(initial = true)

    SideEffect {
        window?.let { win ->
             val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
             
             // Light/Dark icons based on theme
             insetsController.isAppearanceLightStatusBars = !isDarkTheme
             insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
    // Color extraction state
    var extractedColorScheme by remember { mutableStateOf<ColorScheme?>(null) }
    val colorScheme = extractedColorScheme ?: MaterialTheme.colorScheme
    
    // Load and extract colors from album art
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(episode.imageUrl)
            .size(Size(100, 100))
            .allowHardware(false) // Required for Palette
            .build()
    )
    
    LaunchedEffect(episode.imageUrl, painter.state, isDarkTheme) {
        val painterState = painter.state
        if (painterState is AsyncImagePainter.State.Success) {
            val bitmap = (painterState.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val seedColor = extractSeedColor(bitmap)
                extractedColorScheme = generateBrandColorScheme(seedColor, isDarkTheme)
            }
        }
    }
    
    // Sheet state (internal)
    var currentSheetContentState by remember { mutableStateOf(PlayerSheetState.COLLAPSED) }
    
    // Core expansion fraction (0f = collapsed, 1f = expanded)
    val playerContentExpansionFraction = remember { Animatable(0f) }
    val sheetAnimationMutex = remember { MutatorMutex() }
    
    // External Expansion Trigger

    
    // Visual overshoot for bounce effect
    val visualOvershootScaleY = remember { Animatable(1f) }
    
    // Screen dimensions

    val miniPlayerHeightPx = remember(density) { with(density) { MiniPlayerHeight.toPx() } }
    val sheetExpandedTargetY = 0f
    
    // Animation spec
    val sheetAnimationSpec = remember {
        tween<Float>(durationMillis = ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)
    }
    
    // Sheet translation Y
    val initialY = if (currentSheetContentState == PlayerSheetState.COLLAPSED) sheetCollapsedTargetY else sheetExpandedTargetY
    val currentSheetTranslationY = remember { Animatable(initialY) }
    
    // Update translation when bounds change
    LaunchedEffect(sheetCollapsedTargetY) {
        if (currentSheetContentState == PlayerSheetState.COLLAPSED && !currentSheetTranslationY.isRunning) {
            currentSheetTranslationY.snapTo(sheetCollapsedTargetY)
        }
    }
    
    // Derived values using lerp
    val playerContentAreaHeightDp by remember {
        derivedStateOf {
            lerp(MiniPlayerHeight, containerHeight, playerContentExpansionFraction.value)
        }
    }    
    // Mini player corner radius (matching PixelPlayer - rounded square/squircle)
    val overallSheetTopCornerRadius by remember {
        derivedStateOf {
            val collapsedCorner = 32.dp  // Squircle-like corners when collapsed
            val expandedCorner = 0.dp
            lerp(collapsedCorner, expandedCorner, playerContentExpansionFraction.value)
        }
    }
    
    val playerContentBottomRadius by remember {
        derivedStateOf {
            lerp(32.dp, 0.dp, playerContentExpansionFraction.value)
        }
    }
    
    val currentHorizontalPadding by remember {
        derivedStateOf {
            lerp(collapsedStateHorizontalPadding, 0.dp, playerContentExpansionFraction.value)
        }
    }
    
    val playerAreaElevation by remember {
        derivedStateOf {
            lerp(3.dp, 16.dp, playerContentExpansionFraction.value)
        }
    }
    
    // Mini player alpha
    val miniAlpha by remember {
        derivedStateOf {
            (1f - playerContentExpansionFraction.value * 2f).coerceIn(0f, 1f)
        }
    }
    
    // Full player alpha
    val fullPlayerContentAlpha by remember {
        derivedStateOf {
            ((playerContentExpansionFraction.value - 0.25f).coerceIn(0f, 0.75f) / 0.75f)
        }
    }
    
    val initialFullPlayerOffsetY = remember(density) { with(density) { 24.dp.toPx() } }
    val fullPlayerTranslationY by remember {
        derivedStateOf {
            lerp(initialFullPlayerOffsetY, 0f, fullPlayerContentAlpha)
        }
    }
    
    // Animate player sheet function
    suspend fun animatePlayerSheet(
        targetExpanded: Boolean,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = sheetAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        val targetFraction = if (targetExpanded) 1f else 0f
        val targetY = if (targetExpanded) sheetExpandedTargetY else sheetCollapsedTargetY
        val velocityScale = (sheetCollapsedTargetY - sheetExpandedTargetY).coerceAtLeast(1f)
        
        sheetAnimationMutex.mutate {
            coroutineScope {
                launch {
                    currentSheetTranslationY.animateTo(
                        targetValue = targetY,
                        initialVelocity = initialVelocity,
                        animationSpec = animationSpec
                    )
                }
                launch {
                    playerContentExpansionFraction.animateTo(
                        targetValue = targetFraction,
                        initialVelocity = initialVelocity / velocityScale,
                        animationSpec = animationSpec
                    )
                }
            }
        }
    }

    // External Expansion Trigger - Must be after animatePlayerSheet declaration
    LaunchedEffect(expandTrigger) {
        if (expandTrigger > 0L) {
            // Force Expand
            currentSheetContentState = PlayerSheetState.EXPANDED
            animatePlayerSheet(targetExpanded = true)
        }
    }
    
    // Back handler
    BackHandler(enabled = currentSheetContentState == PlayerSheetState.EXPANDED) {
        scope.launch {
            launch {
                val currentFraction = playerContentExpansionFraction.value
                val initialSquash = lerp(1.0f, 0.97f, currentFraction)
                visualOvershootScaleY.snapTo(initialSquash)
                visualOvershootScaleY.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessVeryLow
                    )
                )
            }
            launch { animatePlayerSheet(targetExpanded = false) }
            currentSheetContentState = PlayerSheetState.COLLAPSED
        }
    }
    
    // Drag state
    var isDragging by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    var accumulatedDragYSinceStart by remember { mutableFloatStateOf(0f) }
    
    // The sheet Surface
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, currentSheetTranslationY.value.roundToInt()) }
            .height(playerContentAreaHeightDp), // Fix: Height animates so it doesn't cover navbar
        shadowElevation = 0.dp,
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Player content area with drag handling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = currentHorizontalPadding)
                    .height(playerContentAreaHeightDp)
                    .graphicsLayer {
                        scaleY = visualOvershootScaleY.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .shadow(
                        elevation = playerAreaElevation,
                        shape = RoundedCornerShape(
                            topStart = overallSheetTopCornerRadius,
                            topEnd = overallSheetTopCornerRadius,
                            bottomStart = playerContentBottomRadius,
                            bottomEnd = playerContentBottomRadius
                        ),
                        clip = false
                    )
                    .background(
                        color = colorScheme.primaryContainer,
                        shape = RoundedCornerShape(
                            topStart = overallSheetTopCornerRadius,
                            topEnd = overallSheetTopCornerRadius,
                            bottomStart = playerContentBottomRadius,
                            bottomEnd = playerContentBottomRadius
                        )
                    )
                    .clipToBounds()
                    .pointerInput(Unit) {
                        var initialFractionOnDragStart = 0f
                        var initialYOnDragStart = 0f
                        
                        detectVerticalDragGestures(
                            onDragStart = {
                                scope.launch {
                                    currentSheetTranslationY.stop()
                                    playerContentExpansionFraction.stop()
                                }
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
                                    val newY = (currentSheetTranslationY.value + dragAmount)
                                        .coerceIn(
                                            sheetExpandedTargetY - miniPlayerHeightPx * 0.2f,
                                            sheetCollapsedTargetY + miniPlayerHeightPx * 0.2f
                                        )
                                    currentSheetTranslationY.snapTo(newY)
                                    
                                    val denom = (sheetCollapsedTargetY - sheetExpandedTargetY).coerceAtLeast(1f)
                                    val dragRatio = (initialYOnDragStart - newY) / denom
                                    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
                                    playerContentExpansionFraction.snapTo(newFraction)
                                }
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            },
                            onDragEnd = {
                                isDragging = false
                                
                                val verticalVelocity = velocityTracker.calculateVelocity().y
                                val currentFraction = playerContentExpansionFraction.value
                                val minDragThresholdPx = with(density) { 5.dp.toPx() }
                                val velocityThreshold = 150f
                                
                                val targetState = when {
                                    abs(accumulatedDragYSinceStart) > minDragThresholdPx ->
                                        if (accumulatedDragYSinceStart < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                    abs(verticalVelocity) > velocityThreshold ->
                                        if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                    else ->
                                        if (currentFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                }
                                
                                scope.launch {
                                    if (targetState == PlayerSheetState.EXPANDED) {
                                        launch { animatePlayerSheet(targetExpanded = true) }
                                        currentSheetContentState = PlayerSheetState.EXPANDED
                                    } else {
                                        val dynamicDamping = lerp(
                                            Spring.DampingRatioNoBouncy,
                                            Spring.DampingRatioLowBouncy,
                                            currentFraction
                                        )
                                        launch {
                                            val initialSquash = lerp(1.0f, 0.97f, currentFraction)
                                            visualOvershootScaleY.snapTo(initialSquash)
                                            visualOvershootScaleY.animateTo(
                                                1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessVeryLow
                                                )
                                            )
                                        }
                                        launch {
                                            animatePlayerSheet(
                                                targetExpanded = false,
                                                animationSpec = spring(
                                                    dampingRatio = dynamicDamping,
                                                    stiffness = Spring.StiffnessLow
                                                ),
                                                initialVelocity = verticalVelocity
                                            )
                                        }
                                        currentSheetContentState = PlayerSheetState.COLLAPSED
                                    }
                                }
                                accumulatedDragYSinceStart = 0f
                            }
                        )
                    }
                    .clickable(
                        enabled = true,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        scope.launch {
                            if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                launch { animatePlayerSheet(targetExpanded = true) }
                                currentSheetContentState = PlayerSheetState.EXPANDED
                            } else {
                                launch {
                                    val currentFraction = playerContentExpansionFraction.value
                                    val initialSquash = lerp(1.0f, 0.97f, currentFraction)
                                    visualOvershootScaleY.snapTo(initialSquash)
                                    visualOvershootScaleY.animateTo(
                                        1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessVeryLow
                                        )
                                    )
                                }
                                launch { animatePlayerSheet(targetExpanded = false) }
                                currentSheetContentState = PlayerSheetState.COLLAPSED
                            }
                        }
                    }
            ) {
                // Color scheme crossfade like PixelPlayer
                Crossfade(
                    targetState = colorScheme,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "playerColorScheme"
                ) { scheme ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // MINI PLAYER with swipe-to-dismiss
                        SwipeableMiniPlayer(
                            isPlaying = state.isPlaying,
                            onDismiss = { playbackRepository.clearSession() },
                            backgroundColor = scheme.primaryContainer,
                            showSwipeTip = !hasSeenSwipeDismissTip && !state.isPlaying,
                            onSwipeTipDismissed = { scope.launch { userPrefs.markSwipeDismissTipSeen() } },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer { alpha = miniAlpha }
                                .zIndex(if (playerContentExpansionFraction.value < 0.5f) 1f else 0f)
                        ) {
                            MiniPlayerContent(
                                episode = episode,
                                podcastTitle = podcast?.title ?: "",
                                podcastImageUrl = podcast?.imageUrl,
                                isPlaying = state.isPlaying,
                                isLoading = state.isLoading,
                                position = state.position,
                                duration = state.duration,
                                colorScheme = scheme,
                                onPlayPause = {
                                    if (state.isPlaying) playbackRepository.pause()
                                    else playbackRepository.resume()
                                },
                                onPrevious = { playbackRepository.skipBackward() },
                                onNext = { playbackRepository.skipForward() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        // FULL PLAYER
                        Box(
                            modifier = Modifier
                                .height(containerHeight) // Fix: Prevent layout thrashing by keeping fixed height
                                .graphicsLayer {
                                    alpha = fullPlayerContentAlpha
                                    translationY = fullPlayerTranslationY
                                }
                                .zIndex(if (playerContentExpansionFraction.value >= 0.5f) 1f else 0f)
                                .offset { if (playerContentExpansionFraction.value <= 0.01f) IntOffset(0, 10000) else IntOffset.Zero }
                        ) {
                            FullPlayerContent(
                                playbackRepository = playbackRepository,
                                downloadRepository = downloadRepository,
                                isDarkTheme = isDarkTheme,
                                colorScheme = scheme,
                                onCollapse = {
                                    scope.launch {
                                        launch {
                                            val currentFraction = playerContentExpansionFraction.value
                                            val initialSquash = lerp(1.0f, 0.97f, currentFraction)
                                            visualOvershootScaleY.snapTo(initialSquash)
                                            visualOvershootScaleY.animateTo(
                                                1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessVeryLow
                                                )
                                            )
                                        }
                                        launch { animatePlayerSheet(targetExpanded = false) }
                                        currentSheetContentState = PlayerSheetState.COLLAPSED
                                    }
                                },
                                onEpisodeInfoClick = onEpisodeInfoClick,
                                onPodcastInfoClick = onPodcastInfoClick,
                                showSwipeMinimizeTip = !hasSeenSwipeMinimizeTip,
                                onSwipeMinimizeTipDismissed = { scope.launch { userPrefs.markSwipeMinimizeTipSeen() } },
                                showTitleTip = !hasSeenTitleTapTip,
                                onTitleTipDismissed = { scope.launch { userPrefs.markTitleTapTipSeen() } },
                                isExpanded = playerContentExpansionFraction.value >= 0.5f
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * Swipeable wrapper for mini player - only swipeable when paused
 * Player slides to reveal dismiss pill behind it
 */

