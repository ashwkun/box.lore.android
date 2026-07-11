package cx.aswin.boxcast.feature.player.v2.mini

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry
import cx.aswin.boxcast.feature.player.v2.chrome.playerSheetShape
import cx.aswin.boxcast.feature.player.v2.logic.MiniPlayerDismissLogic
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipeable wrapper for mini player V2 — only swipeable when paused.
 * Player slides to reveal a dismiss pill behind it.
 */
@Composable
fun SwipeableMiniPlayerV2(
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    backgroundColor: Color,
    showSwipeTip: Boolean = false,
    onSwipeTipDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val offsetX = remember { Animatable(0f) }
    var showConfirmPill by remember { mutableStateOf(false) }
    var swipeDirection by remember { mutableStateOf(0) }
    var autoHideJob by remember { mutableStateOf<Job?>(null) }

    val dismissThreshold = with(density) { 100.dp.toPx() }
    val playerShape = playerSheetShape(
        topStart = PlayerChromeGeometry.MiniPlayerCollapsedCorner,
        topEnd = PlayerChromeGeometry.MiniPlayerCollapsedCorner,
        bottomStart = PlayerChromeGeometry.MiniPlayerDockedBottomCorner,
        bottomEnd = PlayerChromeGeometry.MiniPlayerDockedBottomCorner,
    )

    Box(modifier = modifier) {
        DismissPillV2(
            visible = showConfirmPill,
            swipeDirection = swipeDirection,
            onDismiss = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                autoHideJob?.cancel()
                onDismiss()
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .background(color = backgroundColor, shape = playerShape)
                .clip(playerShape)
                .pointerInput(isPlaying) {
                    if (isPlaying) return@pointerInput

                    detectHorizontalDragGestures(
                        onDragStart = {
                            autoHideJob?.cancel()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (MiniPlayerDismissLogic.shouldDismissOnDragEnd(offsetX.value, dismissThreshold)) {
                                    swipeDirection = MiniPlayerDismissLogic.swipeDirection(offsetX.value)
                                    showConfirmPill = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                    offsetX.animateTo(
                                        targetValue = MiniPlayerDismissLogic.revealSnapTargetPx(
                                            swipeDirection,
                                            dismissThreshold,
                                        ),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )

                                    autoHideJob = coroutineScope.launch {
                                        delay(3000)
                                        showConfirmPill = false
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow,
                                            ),
                                        )
                                    }
                                } else {
                                    showConfirmPill = false
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                showConfirmPill = false
                                offsetX.animateTo(0f, spring())
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount)
                                val nextShowPill = MiniPlayerDismissLogic.shouldShowConfirmPillWhileDragging(
                                    offsetXPx = offsetX.value,
                                    dismissThresholdPx = dismissThreshold,
                                    currentlyShowing = showConfirmPill,
                                )
                                if (showConfirmPill && !nextShowPill) {
                                    autoHideJob?.cancel()
                                }
                                showConfirmPill = nextShowPill
                                if (showConfirmPill) {
                                    swipeDirection = MiniPlayerDismissLogic.swipeDirection(offsetX.value)
                                }
                            }
                        },
                    )
                },
        ) {
            content()
        }

        if (showSwipeTip && !isPlaying) {
            var tipVisible by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(4000)
                tipVisible = false
                onSwipeTipDismissed()
            }

            AnimatedVisibility(
                visible = tipVisible,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(500)),
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "← Swipe to dismiss →",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                playerSheetShape(12.dp, 12.dp, 12.dp, 12.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DismissPillV2(
    visible: Boolean,
    swipeDirection: Int,
    onDismiss: () -> Unit,
) {
    val pillShape = playerSheetShape(24.dp, 24.dp, 24.dp, 24.dp)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(0f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (swipeDirection > 0) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.errorContainer, pillShape)
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Dismiss",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
