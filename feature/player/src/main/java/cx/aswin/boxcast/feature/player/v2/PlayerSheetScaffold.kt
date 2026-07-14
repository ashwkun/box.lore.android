package cx.aswin.boxcast.feature.player.v2

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxcast.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxcast.feature.player.v2.logic.calculatePlayerSheetGeometry
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayerSheetValue { Collapsed, Expanded }

data class PlayerSheetLayout(
    val collapsedTargetY: Float,
    val containerHeight: Dp,
    val collapsedHorizontalPadding: Dp = 12.dp,
    val expandTrigger: Long = 0L
)

data class PlayerSheetActions(
    val onEpisodeInfoClick: (cx.aswin.boxcast.core.model.Episode) -> Unit = {},
    val onPodcastInfoClick: (cx.aswin.boxcast.core.model.Podcast) -> Unit = {}
)

/**
 * v2 player sheet: an [AnchoredDraggableState]-driven bottom sheet that morphs between
 * a mini bar and the immersive full player. Springs everywhere, velocity-aware settling,
 * nested-scroll handoff with the full player content, and predictive-back collapse.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheetScaffold(
    playbackRepository: PlaybackRepository,
    downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    userPrefs: UserPreferencesRepository,
    layout: PlayerSheetLayout,
    actions: PlayerSheetActions = PlayerSheetActions(),
    modifier: Modifier = Modifier
) {
    val stablePlayerState = remember(playbackRepository) {
        playbackRepository.playerState
            .map { it.copy(position = 0L, bufferedPosition = 0L) }
            .distinctUntilChanged()
    }
    val positionFlow = remember(playbackRepository) {
        playbackRepository.playerState
            .map { it.position }
            .distinctUntilChanged()
    }
    val state by stablePlayerState.collectAsStateWithLifecycle(
        initialValue = cx.aswin.boxcast.core.data.PlayerState()
    )
    val episode = state.currentEpisode
    val podcast = state.currentPodcast

    if (episode == null) return

    var isFullscreenVideo by rememberSaveable(inputs = arrayOf(episode.id)) { mutableStateOf(false) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerStateHolder = rememberSaveableStateHolder()
    val haptics = LocalHapticFeedback.current
    val window = (context as? android.app.Activity)?.window
    val hasSeenSwipeDismissTip by userPrefs.hasSeenSwipeDismissTip.collectAsStateWithLifecycle(initialValue = true)
    val hasSeenSwipeMinimizeTip by userPrefs.hasSeenSwipeMinimizeTip.collectAsStateWithLifecycle(initialValue = true)
    val effectiveDarkTheme = LocalEffectiveDarkTheme.current
    PlayerSheetSystemBars(window, effectiveDarkTheme)
    val colorScheme = rememberPlayerColorScheme(episode.imageUrl)
    val sheetState = remember(density) {
        AnchoredDraggableState(
            initialValue = PlayerSheetValue.Collapsed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 180.dp.toPx() } },
            // Soft expressive settle — gesture gates below keep mini controls tappable
            // while this runs (don't stiffen the spring to "fix" dead taps).
            snapAnimationSpec = ExpressiveMotion.SpatialLargeSpring,
            decayAnimationSpec = exponentialDecay()
        )
    }
    ConfigurePlayerSheetAnchors(sheetState, layout.collapsedTargetY)
    val geometry = rememberPlayerSheetGeometry(sheetState, layout)
    val isExpanded = sheetState.currentValue == PlayerSheetValue.Expanded
    PlayerSheetSettledEffects(sheetState, haptics, episode, podcast)
    PlayerSheetExternalExpansion(sheetState, layout.expandTrigger)
    PlayerSheetPredictiveBack(
        enabled = isExpanded && !isFullscreenVideo,
        sheetState = sheetState,
        collapsedTargetY = layout.collapsedTargetY
    )
    val sheetNestedScrollConnection = rememberPlayerSheetNestedScrollConnection(sheetState)
    PlayerSheetSurface(
        geometry = geometry,
        content = PlayerSheetContentState(
            playerState = state,
            episode = episode,
            podcast = podcast,
            colorScheme = colorScheme,
            isFullscreenVideo = isFullscreenVideo,
            hasSeenSwipeDismissTip = hasSeenSwipeDismissTip,
            hasSeenSwipeMinimizeTip = hasSeenSwipeMinimizeTip
        ),
        resources = PlayerSheetResources(
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            userPrefs = userPrefs,
            stateHolder = playerStateHolder,
            scope = scope,
            haptics = haptics,
            flows = PlayerSheetFlows(
                nestedScrollConnection = sheetNestedScrollConnection,
                position = positionFlow
            )
        ),
        callbacks = PlayerSheetCallbacks(
            onExpand = { requestSheetExpansion(sheetState, geometry.sheetOffset, scope) },
            onCollapse = {
                requestSheetCollapse(sheetState, geometry.sheetOffset, layout.collapsedTargetY, scope)
            },
            onFullscreenVideoChange = { isFullscreenVideo = it },
            onEpisodeInfoClick = actions.onEpisodeInfoClick,
            onPodcastInfoClick = actions.onPodcastInfoClick
        ),
        sheetState = sheetState,
        containerHeight = layout.containerHeight,
        modifier = modifier
    )
}

private data class PlayerSheetGeometry(
    val sheetOffset: Float,
    val expansionFraction: Float,
    val sheetHeight: Dp,
    val topCornerRadius: Dp,
    val bottomCornerRadius: Dp,
    val horizontalPadding: Dp,
    val sheetElevation: Dp,
    val miniAlpha: Float,
    val fullAlpha: Float,
    val fullTranslationY: Float
)

private data class PlayerSheetContentState(
    val playerState: cx.aswin.boxcast.core.data.PlayerState,
    val episode: cx.aswin.boxcast.core.model.Episode,
    val podcast: cx.aswin.boxcast.core.model.Podcast?,
    val colorScheme: ColorScheme,
    val isFullscreenVideo: Boolean,
    val hasSeenSwipeDismissTip: Boolean,
    val hasSeenSwipeMinimizeTip: Boolean
)

private data class PlayerSheetResources(
    val playbackRepository: PlaybackRepository,
    val downloadRepository: cx.aswin.boxcast.core.data.DownloadRepository,
    val userPrefs: UserPreferencesRepository,
    val stateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    val scope: kotlinx.coroutines.CoroutineScope,
    val haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    val flows: PlayerSheetFlows
)

private data class PlayerSheetFlows(
    val nestedScrollConnection: NestedScrollConnection,
    val position: Flow<Long>
)

private data class PlayerSheetCallbacks(
    val onExpand: () -> Unit,
    val onCollapse: () -> Unit,
    val onFullscreenVideoChange: (Boolean) -> Unit,
    val onEpisodeInfoClick: (cx.aswin.boxcast.core.model.Episode) -> Unit,
    val onPodcastInfoClick: (cx.aswin.boxcast.core.model.Podcast) -> Unit
)

@Composable
private fun rememberPlayerSheetGeometry(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    layout: PlayerSheetLayout
): PlayerSheetGeometry {
    val density = LocalDensity.current
    val sheetOffset by remember(sheetState, layout.collapsedTargetY) {
        derivedStateOf {
            val raw = sheetState.offset
            if (raw.isNaN()) layout.collapsedTargetY else raw.coerceIn(0f, layout.collapsedTargetY)
        }
    }
    val fullEntranceOffsetPx = remember(density) { with(density) { 24.dp.toPx() } }
    val values = calculatePlayerSheetGeometry(
        sheetOffset = sheetOffset,
        collapsedTargetY = layout.collapsedTargetY,
        containerHeight = layout.containerHeight,
        collapsedHorizontalPadding = layout.collapsedHorizontalPadding,
        fullEntranceOffsetPx = fullEntranceOffsetPx
    )
    return PlayerSheetGeometry(
        sheetOffset = sheetOffset,
        expansionFraction = values.expansionFraction,
        sheetHeight = values.sheetHeight,
        topCornerRadius = values.topCornerRadius,
        bottomCornerRadius = values.bottomCornerRadius,
        horizontalPadding = values.horizontalPadding,
        sheetElevation = values.sheetElevation,
        miniAlpha = values.miniAlpha,
        fullAlpha = values.fullAlpha,
        fullTranslationY = values.fullTranslationY
    )
}

private fun requestSheetExpansion(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    sheetOffset: Float,
    scope: kotlinx.coroutines.CoroutineScope
) {
    // Allow interrupting an in-flight soft settle so taps aren't ignored for ~1–2s.
    if (sheetOffset <= 0.5f) return
    scope.launch { sheetState.animateTo(PlayerSheetValue.Expanded) }
}

private fun requestSheetCollapse(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    sheetOffset: Float,
    collapsedTargetY: Float,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (sheetOffset >= collapsedTargetY - 0.5f) return
    scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) }
}

@Composable
private fun PlayerSheetSurface(
    geometry: PlayerSheetGeometry,
    content: PlayerSheetContentState,
    resources: PlayerSheetResources,
    callbacks: PlayerSheetCallbacks,
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    containerHeight: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(
        topStart = geometry.topCornerRadius,
        topEnd = geometry.topCornerRadius,
        bottomStart = geometry.bottomCornerRadius,
        bottomEnd = geometry.bottomCornerRadius
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, geometry.sheetOffset.roundToInt()) }
            .graphicsLayer { clip = false }
            .height(geometry.sheetHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.horizontalPadding)
                .height(geometry.sheetHeight)
                .shadow(elevation = geometry.sheetElevation, shape = shape, clip = false)
                .background(color = miniSheetColor(content.colorScheme), shape = shape)
                .clip(shape)
                .anchoredDraggable(
                    state = sheetState,
                    orientation = Orientation.Vertical,
                    enabled = !content.isFullscreenVideo,
                    // Never steal taps while settling: default true-during-animation
                    // ate mini controls for ~1–2s. Touch-slop still allows swipe-up
                    // expand from the collapsed mini player.
                    startDragImmediately = false,
                )
                .clickable(
                    enabled = geometry.expansionFraction < 0.5f &&
                        !content.isFullscreenVideo,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    resources.haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    callbacks.onExpand()
                }
        ) {
            PlayerSheetLayers(geometry, content, resources, callbacks, containerHeight)
        }
    }
}

@Composable
private fun PlayerSheetLayers(
    geometry: PlayerSheetGeometry,
    content: PlayerSheetContentState,
    resources: PlayerSheetResources,
    callbacks: PlayerSheetCallbacks,
    containerHeight: Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MiniPlayerLayer(
            visible = geometry.expansionFraction < 0.999f,
            geometry = geometry,
            content = content,
            resources = resources
        )
        FullPlayerLayer(
            visible = geometry.expansionFraction > 0.001f,
            geometry = geometry,
            content = content,
            resources = resources,
            callbacks = callbacks,
            containerHeight = containerHeight
        )
    }
}

@Composable
private fun MiniPlayerLayer(
    visible: Boolean,
    geometry: PlayerSheetGeometry,
    content: PlayerSheetContentState,
    resources: PlayerSheetResources
) {
    if (!visible) return
    val position by resources.flows.position.collectAsStateWithLifecycle(initialValue = 0L)
    resources.stateHolder.SaveableStateProvider("miniPlayer") {
        MiniPlayerV2(
            content = MiniPlayerContent(
                episode = content.episode,
                podcastTitle = content.podcast?.title ?: "",
                podcastImageUrl = content.podcast?.imageUrl,
                isPlaying = content.playerState.isPlaying,
                isLoading = content.playerState.isLoading,
                position = position,
                duration = content.playerState.duration
            ),
            colors = MiniPlayerColors(
                colorScheme = content.colorScheme,
                backgroundColor = miniSheetColor(content.colorScheme)
            ),
            actions = miniPlayerActions(content, resources),
            swipeTip = MiniPlayerSwipeTip(
                visible = !content.hasSeenSwipeDismissTip && !content.playerState.isPlaying,
                onDismissed = {
                    resources.scope.launch { resources.userPrefs.markSwipeDismissTipSeen() }
                }
            ),
            modifier = Modifier
                .height(MiniPlayerHeight)
                .fillMaxWidth()
                .graphicsLayer { alpha = geometry.miniAlpha }
                .zIndex(if (geometry.expansionFraction < 0.5f) 1f else 0f)
        )
    }
}

private fun miniPlayerActions(
    content: PlayerSheetContentState,
    resources: PlayerSheetResources
): MiniPlayerActions = MiniPlayerActions(
    onPlayPause = {
        trackMiniPlayerAction("play_pause", content)
        if (content.playerState.isPlaying) {
            resources.playbackRepository.pause()
        } else {
            resources.playbackRepository.resume(
                android.os.Bundle().apply {
                    putString("entry_point", "resume_mini_player")
                }
            )
        }
    },
    onReplay = {
        trackMiniPlayerAction("previous", content)
        resources.playbackRepository.skipBackward()
    },
    onForward = {
        trackMiniPlayerAction("next", content)
        resources.playbackRepository.skipForward()
    },
    onDismiss = {
        trackMiniPlayerAction("dismissed", content)
        resources.playbackRepository.clearSession()
    }
)

private fun trackMiniPlayerAction(action: String, content: PlayerSheetContentState) {
    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
        action,
        content.podcast?.id,
        content.episode.id,
        content.podcast?.title,
        content.episode.title
    )
}

@Composable
private fun FullPlayerLayer(
    visible: Boolean,
    geometry: PlayerSheetGeometry,
    content: PlayerSheetContentState,
    resources: PlayerSheetResources,
    callbacks: PlayerSheetCallbacks,
    containerHeight: Dp
) {
    if (!visible) return
    resources.stateHolder.SaveableStateProvider("fullPlayer") {
        Box(
            modifier = Modifier
                .height(containerHeight)
                .graphicsLayer {
                    alpha = geometry.fullAlpha
                    translationY = geometry.fullTranslationY
                }
                .zIndex(if (geometry.expansionFraction >= 0.5f) 1f else 0f)
                .offset {
                    if (geometry.expansionFraction <= 0.01f) IntOffset(0, 10000) else IntOffset.Zero
                }
        ) {
            FullPlayerV2(
                dependencies = FullPlayerDependencies(
                    playbackRepository = resources.playbackRepository,
                    downloadRepository = resources.downloadRepository
                ),
                display = FullPlayerDisplay(
                    colorScheme = content.colorScheme,
                    isFullscreenVideo = content.isFullscreenVideo,
                    sheetNestedScrollConnection = resources.flows.nestedScrollConnection,
                    isExpanded = geometry.expansionFraction >= 0.5f,
                    showSwipeMinimizeTip = !content.hasSeenSwipeMinimizeTip
                ),
                actions = FullPlayerActions(
                    onFullscreenVideoChange = callbacks.onFullscreenVideoChange,
                    onCollapse = callbacks.onCollapse,
                    onEpisodeInfoClick = callbacks.onEpisodeInfoClick,
                    onPodcastInfoClick = callbacks.onPodcastInfoClick,
                    onSwipeMinimizeTipDismissed = {
                        resources.scope.launch { resources.userPrefs.markSwipeMinimizeTipSeen() }
                    }
                )
            )
        }
    }
}

@Composable
private fun PlayerSheetSystemBars(window: android.view.Window?, effectiveDarkTheme: Boolean) {
    SideEffect {
        window?.let { currentWindow ->
            val controller = androidx.core.view.WindowCompat.getInsetsController(
                currentWindow,
                currentWindow.decorView
            )
            controller.isAppearanceLightStatusBars = !effectiveDarkTheme
            controller.isAppearanceLightNavigationBars = !effectiveDarkTheme
        }
    }
}

@Composable
private fun ConfigurePlayerSheetAnchors(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    collapsedTargetY: Float
) {
    LaunchedEffect(collapsedTargetY) {
        sheetState.updateAnchors(
            DraggableAnchors {
                PlayerSheetValue.Collapsed at collapsedTargetY
                PlayerSheetValue.Expanded at 0f
            }
        )
    }
}

@Composable
private fun PlayerSheetSettledEffects(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    episode: cx.aswin.boxcast.core.model.Episode,
    podcast: cx.aswin.boxcast.core.model.Podcast?
) {
    LaunchedEffect(sheetState, episode.id) {
        var previous = sheetState.settledValue
        snapshotFlow { sheetState.settledValue }.collect { value ->
            if (value == previous) return@collect
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            updatePlayerSession(value, episode, podcast)
            previous = value
        }
    }
}

private fun updatePlayerSession(
    value: PlayerSheetValue,
    episode: cx.aswin.boxcast.core.model.Episode,
    podcast: cx.aswin.boxcast.core.model.Podcast?
) {
    if (value == PlayerSheetValue.Expanded) {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
            "expanded",
            podcast?.id,
            episode.id,
            podcast?.title,
            episode.title
        )
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.startSession(
            podcast?.id,
            episode.id,
            podcast?.title,
            episode.title
        )
    } else {
        cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.endSession()
    }
}

@Composable
private fun PlayerSheetExternalExpansion(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    expandTrigger: Long
) {
    LaunchedEffect(expandTrigger) {
        if (expandTrigger > 0L) {
            sheetState.animateTo(PlayerSheetValue.Expanded)
        }
    }
}

@Composable
private fun PlayerSheetPredictiveBack(
    enabled: Boolean,
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    collapsedTargetY: Float
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect { backEvent ->
                val target = collapsedTargetY * 0.2f * backEvent.progress
                sheetState.dispatchRawDelta(target - sheetState.requireOffset())
            }
            sheetState.animateTo(PlayerSheetValue.Collapsed)
        } catch (exception: CancellationException) {
            withContext(NonCancellable) {
                sheetState.animateTo(PlayerSheetValue.Expanded)
            }
            throw exception
        }
    }
}

@Composable
private fun rememberPlayerSheetNestedScrollConnection(
    sheetState: AnchoredDraggableState<PlayerSheetValue>
): NestedScrollConnection = remember(sheetState) {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            return consumePlayerSheetPreScroll(sheetState, available)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return consumePlayerSheetPostScroll(sheetState, available, source)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return settlePlayerSheetPreFling(sheetState, available)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            return settlePlayerSheetPostFling(sheetState, available)
        }
    }
}

private fun consumePlayerSheetPreScroll(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    available: Offset
): Offset {
    val delta = available.y
    return if (delta < 0 && sheetState.requireOffset() > 0f) {
        Offset(0f, sheetState.dispatchRawDelta(delta))
    } else {
        Offset.Zero
    }
}

private fun consumePlayerSheetPostScroll(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    available: Offset,
    source: NestedScrollSource
): Offset {
    if (source != NestedScrollSource.UserInput) return Offset.Zero
    return Offset(0f, sheetState.dispatchRawDelta(available.y))
}

private suspend fun settlePlayerSheetPreFling(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    available: Velocity
): Velocity {
    if (available.y >= 0 || sheetState.requireOffset() <= 0f) return Velocity.Zero
    // animateTo (not settle-with-velocity): fling kick + underdamped spring overshoots
    // past the mini anchor, then the visual clamp makes the rebound look like a
    // disconnected bounce against travel direction.
    sheetState.animateTo(resolvePlayerSheetSettleTarget(sheetState, available.y))
    return available
}

private suspend fun settlePlayerSheetPostFling(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    available: Velocity
): Velocity {
    // Always snap to an anchor after nested-scroll user input — including
    // zero-velocity releases from slow taps/drags. Skipping settle left the sheet
    // mid-anchor with currentValue still Expanded while UI already looked collapsed.
    if (sheetState.isAnimationRunning) return Velocity.Zero
    sheetState.animateTo(resolvePlayerSheetSettleTarget(sheetState, available.y))
    return available
}

/**
 * Pick the settle anchor from fling direction when decisive, otherwise nearest.
 * Motion itself is critically damped [ExpressiveMotion.SpatialLargeSpring] via animateTo.
 */
private fun resolvePlayerSheetSettleTarget(
    sheetState: AnchoredDraggableState<PlayerSheetValue>,
    velocityY: Float
): PlayerSheetValue {
    // Decisive fling (~velocity threshold used by AnchoredDraggableState).
    val decisiveFling = 1200f
    when {
        velocityY > decisiveFling -> return PlayerSheetValue.Collapsed
        velocityY < -decisiveFling -> return PlayerSheetValue.Expanded
    }
    val offset = sheetState.requireOffset()
    val collapsed = sheetState.anchors.positionOf(PlayerSheetValue.Collapsed)
    val expanded = sheetState.anchors.positionOf(PlayerSheetValue.Expanded)
    val midpoint = (collapsed + expanded) * 0.5f
    return if (offset >= midpoint) PlayerSheetValue.Collapsed else PlayerSheetValue.Expanded
}
