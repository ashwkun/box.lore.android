package cx.aswin.boxcast.feature.player.v2

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import cx.aswin.boxcast.core.designsystem.theme.LocalEffectiveDarkTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    val sheetCollapsedTargetY = layout.collapsedTargetY
    val containerHeight = layout.containerHeight
    val collapsedStateHorizontalPadding = layout.collapsedHorizontalPadding
    val expandTrigger = layout.expandTrigger
    val onEpisodeInfoClick = actions.onEpisodeInfoClick
    val onPodcastInfoClick = actions.onPodcastInfoClick
    val state by playbackRepository.playerState.collectAsStateWithLifecycle()
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

    // Tooltips
    val hasSeenSwipeDismissTip by userPrefs.hasSeenSwipeDismissTip.collectAsStateWithLifecycle(initialValue = true)
    val hasSeenSwipeMinimizeTip by userPrefs.hasSeenSwipeMinimizeTip.collectAsStateWithLifecycle(initialValue = true)

    val effectiveDarkTheme = LocalEffectiveDarkTheme.current
    PlayerSheetSystemBars(window, effectiveDarkTheme)

    // Artwork-seeded color scheme
    val colorScheme = rememberPlayerColorScheme(episode.imageUrl)

    // ------------------------------------------------------------------
    // Anchored draggable sheet state
    // ------------------------------------------------------------------

    val sheetState = remember(density) {
        AnchoredDraggableState(
            initialValue = PlayerSheetValue.Collapsed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 180.dp.toPx() } },
            snapAnimationSpec = spring(
                dampingRatio = 1f,
                stiffness = 75f
            ),
            decayAnimationSpec = exponentialDecay()
        )
    }

    ConfigurePlayerSheetAnchors(sheetState, sheetCollapsedTargetY)

    val sheetOffset by remember(sheetState, sheetCollapsedTargetY) {
        derivedStateOf {
            val raw = sheetState.offset
            if (raw.isNaN()) sheetCollapsedTargetY else raw.coerceIn(0f, sheetCollapsedTargetY)
        }
    }
    val expansionFraction by remember(sheetCollapsedTargetY) {
        derivedStateOf {
            if (sheetCollapsedTargetY <= 0f) 0f
            else (1f - sheetOffset / sheetCollapsedTargetY).coerceIn(0f, 1f)
        }
    }
    val isExpanded = sheetState.currentValue == PlayerSheetValue.Expanded

    fun expandSheet() {
        if (sheetState.isAnimationRunning || sheetOffset <= 0.5f) return
        scope.launch { sheetState.animateTo(PlayerSheetValue.Expanded) }
    }

    fun collapseSheet() {
        if (sheetState.isAnimationRunning || sheetOffset >= sheetCollapsedTargetY - 0.5f) return
        scope.launch { sheetState.animateTo(PlayerSheetValue.Collapsed) }
    }

    PlayerSheetSettledEffects(sheetState, haptics, episode, podcast)
    PlayerSheetExternalExpansion(sheetState, expandTrigger)
    PlayerSheetPredictiveBack(
        enabled = isExpanded && !isFullscreenVideo,
        sheetState = sheetState,
        collapsedTargetY = sheetCollapsedTargetY
    )

    // Nested-scroll handoff: the full player's inner scroll drives the sheet when
    // pulling down from the top or when the sheet sits between anchors.
    val sheetNestedScrollConnection = rememberPlayerSheetNestedScrollConnection(sheetState)

    // ------------------------------------------------------------------
    // Geometry derived from the expansion fraction
    // ------------------------------------------------------------------

    val sheetHeight by remember(containerHeight) {
        derivedStateOf { lerp(MiniPlayerHeight, containerHeight, expansionFraction) }
    }
    val topCornerRadius by remember {
        derivedStateOf { lerp(26.dp, 0.dp, expansionFraction) }
    }
    val bottomCornerRadius by remember {
        derivedStateOf { lerp(14.dp, 0.dp, expansionFraction) }
    }
    val horizontalPadding by remember(collapsedStateHorizontalPadding) {
        derivedStateOf { lerp(collapsedStateHorizontalPadding, 0.dp, expansionFraction) }
    }
    val sheetElevation by remember {
        derivedStateOf { lerp(3.dp, 16.dp, expansionFraction) }
    }
    val miniAlpha by remember {
        derivedStateOf { (1f - expansionFraction * 2f).coerceIn(0f, 1f) }
    }
    val fullAlpha by remember {
        derivedStateOf { ((expansionFraction - 0.25f).coerceIn(0f, 0.75f) / 0.75f) }
    }
    val fullEntranceOffsetPx = remember(density) { with(density) { 24.dp.toPx() } }
    val fullTranslationY by remember {
        derivedStateOf { lerp(fullEntranceOffsetPx, 0f, fullAlpha) }
    }

    // ------------------------------------------------------------------
    // Sheet UI
    // ------------------------------------------------------------------

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, sheetOffset.roundToInt()) }
            .graphicsLayer { clip = false }
            .height(sheetHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .height(sheetHeight)
                .shadow(
                    elevation = sheetElevation,
                    shape = RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    ),
                    clip = false
                )
                .background(
                    color = miniSheetColor(colorScheme),
                    shape = RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    )
                )
                .clip(
                    RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    )
                )
                .anchoredDraggable(
                    state = sheetState,
                    orientation = Orientation.Vertical,
                    enabled = !isFullscreenVideo
                )
                .clickable(
                    enabled = !isExpanded && !isFullscreenVideo,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expandSheet()
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (expansionFraction < 0.999f) {
                    playerStateHolder.SaveableStateProvider("miniPlayer") {
                        // MINI PLAYER
                        MiniPlayerV2(
                            content = MiniPlayerContent(
                                episode = episode,
                                podcastTitle = podcast?.title ?: "",
                                podcastImageUrl = podcast?.imageUrl,
                                isPlaying = state.isPlaying,
                                isLoading = state.isLoading,
                                position = state.position,
                                duration = state.duration
                            ),
                            colors = MiniPlayerColors(
                                colorScheme = colorScheme,
                                backgroundColor = miniSheetColor(colorScheme)
                            ),
                            actions = MiniPlayerActions(
                                onPlayPause = {
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                        "play_pause", podcast?.id, episode.id, podcast?.title, episode.title
                                    )
                                    if (state.isPlaying) {
                                        playbackRepository.pause()
                                    } else {
                                        playbackRepository.resume(
                                            android.os.Bundle().apply {
                                                putString("entry_point", "resume_mini_player")
                                            }
                                        )
                                    }
                                },
                                onReplay = {
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                        "previous", podcast?.id, episode.id, podcast?.title, episode.title
                                    )
                                    playbackRepository.skipBackward()
                                },
                                onForward = {
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                        "next", podcast?.id, episode.id, podcast?.title, episode.title
                                    )
                                    playbackRepository.skipForward()
                                },
                                onDismiss = {
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackMiniPlayerInteraction(
                                        "dismissed", podcast?.id, episode.id, podcast?.title, episode.title
                                    )
                                    playbackRepository.clearSession()
                                }
                            ),
                            swipeTip = MiniPlayerSwipeTip(
                                visible = !hasSeenSwipeDismissTip && !state.isPlaying,
                                onDismissed = { scope.launch { userPrefs.markSwipeDismissTipSeen() } }
                            ),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .height(MiniPlayerHeight)
                                .fillMaxWidth()
                                .graphicsLayer { alpha = miniAlpha }
                                .zIndex(if (expansionFraction < 0.5f) 1f else 0f)
                        )
                    }
                }

                if (expansionFraction > 0.001f) {
                    playerStateHolder.SaveableStateProvider("fullPlayer") {
                        // FULL PLAYER
                    Box(
                        modifier = Modifier
                            .height(containerHeight) // Fixed height prevents layout thrash while morphing
                            .graphicsLayer {
                                alpha = fullAlpha
                                translationY = fullTranslationY
                            }
                            .zIndex(if (expansionFraction >= 0.5f) 1f else 0f)
                            .offset {
                                if (expansionFraction <= 0.01f) IntOffset(0, 10000) else IntOffset.Zero
                            }
                    ) {
                        FullPlayerV2(
                            dependencies = FullPlayerDependencies(
                                playbackRepository = playbackRepository,
                                downloadRepository = downloadRepository
                            ),
                            display = FullPlayerDisplay(
                                colorScheme = colorScheme,
                                isFullscreenVideo = isFullscreenVideo,
                                sheetNestedScrollConnection = sheetNestedScrollConnection,
                                isExpanded = expansionFraction >= 0.5f,
                                showSwipeMinimizeTip = !hasSeenSwipeMinimizeTip
                            ),
                            actions = FullPlayerActions(
                                onFullscreenVideoChange = { isFullscreenVideo = it },
                                onCollapse = { collapseSheet() },
                                onEpisodeInfoClick = onEpisodeInfoClick,
                                onPodcastInfoClick = onPodcastInfoClick,
                                onSwipeMinimizeTipDismissed = {
                                    scope.launch { userPrefs.markSwipeMinimizeTipSeen() }
                                }
                            )
                        )
                    }
                    }
                }
            }
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
        if (expandTrigger > 0L && !sheetState.isAnimationRunning) {
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
            sheetState.animateTo(PlayerSheetValue.Expanded)
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
            val delta = available.y
            return if (delta < 0 && sheetState.requireOffset() > 0f) {
                Offset(0f, sheetState.dispatchRawDelta(delta))
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            return Offset(0f, sheetState.dispatchRawDelta(available.y))
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (available.y >= 0 || sheetState.requireOffset() <= 0f) return Velocity.Zero
            sheetState.settle(available.y)
            return available
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y == 0f || sheetState.isAnimationRunning) return Velocity.Zero
            sheetState.settle(available.y)
            return available
        }
    }
}
