package cx.aswin.boxcast.feature.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SimplePlayerControls(
    playbackSpeed: Float,
    sleepTimerEnd: Long?,
    duration: Long,
    colorScheme: ColorScheme,
    onSpeedChange: (Float) -> Unit,
    onSleepClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(PlayerControlMode.None) }

    // Animate weights to ensure full width expansion
    val speedWeight by animateFloatAsState(
        targetValue = when (mode) {
            PlayerControlMode.Speed -> 10f
            PlayerControlMode.Sleep -> 0.001f
            PlayerControlMode.None -> 1f
        },
        label = "speedWeight"
    )
    val sleepWeight by animateFloatAsState(
        targetValue = when (mode) {
            PlayerControlMode.Sleep -> 10f
            PlayerControlMode.Speed -> 0.001f
            PlayerControlMode.None -> 1f
        },
        label = "sleepWeight"
    )

    // Unified Container Pill
    Surface(
        color = colorScheme.primary.copy(alpha = 0.15f), // Force Primary tint
        shape = CircleShape,
        modifier = modifier.height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. SPEED SECTION
            Box(
                modifier = Modifier
                    .weight(speedWeight)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                SpeedSectionContent(
                    mode = mode,
                    playbackSpeed = playbackSpeed,
                    colorScheme = colorScheme,
                    onModeChange = { mode = it },
                    onSpeedChange = onSpeedChange
                )
            }

            // Divider (Only visible when None)
            AnimatedVisibility(
                visible = mode == PlayerControlMode.None,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(colorScheme.primary.copy(alpha = 0.3f))
                )
            }

            // 2. SLEEP SECTION
            Box(
                modifier = Modifier
                    .weight(sleepWeight)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                SleepSectionContent(
                    mode = mode,
                    sleepTimerEnd = sleepTimerEnd,
                    colorScheme = colorScheme,
                    onModeChange = { mode = it },
                    onSleepClick = onSleepClick
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SpeedSectionContent(
    mode: PlayerControlMode,
    playbackSpeed: Float,
    colorScheme: ColorScheme,
    onModeChange: (PlayerControlMode) -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.8f))) togetherWith
            (fadeOut(animationSpec = tween(90)))
        },
        label = "SpeedMode"
    ) { currentMode ->
        when (currentMode) {
            PlayerControlMode.None -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .expressiveClickable(onClick = { onModeChange(PlayerControlMode.Speed) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${playbackSpeed}x",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }
            }
            PlayerControlMode.Speed -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { onModeChange(PlayerControlMode.None) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                    }

                    val options = listOf(0.5f, 0.8f, 1.0f, 1.25f, 1.5f)

                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(options) { speed ->
                            val isSelected = speed == playbackSpeed
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        onSpeedChange(speed)
                                        onModeChange(PlayerControlMode.None)
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${speed}x",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            PlayerControlMode.Sleep -> {
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SleepSectionContent(
    mode: PlayerControlMode,
    sleepTimerEnd: Long?,
    colorScheme: ColorScheme,
    onModeChange: (PlayerControlMode) -> Unit,
    onSleepClick: (Int) -> Unit
) {
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                    scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.8f))) togetherWith
            (fadeOut(animationSpec = tween(90)))
        },
        label = "SleepMode"
    ) { currentMode ->
        when (currentMode) {
            PlayerControlMode.None -> {
                var remainingTime by remember { mutableStateOf("") }
                LaunchedEffect(sleepTimerEnd) {
                    if (sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()) {
                        while (isActive) {
                            val left = sleepTimerEnd - System.currentTimeMillis()
                            if (left <= 0) {
                                remainingTime = ""
                                break
                            }
                            remainingTime = cx.aswin.boxcast.feature.player.formatTime(left)
                            delay(1000)
                        }
                    } else {
                        remainingTime = ""
                    }
                }

                val isTimerActive = sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .expressiveClickable(onClick = { onModeChange(PlayerControlMode.Sleep) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.NightsStay,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTimerActive && remainingTime.isNotEmpty()) remainingTime else "Sleep",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }
            }
            PlayerControlMode.Sleep -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val options = listOf(0 to "Off", 15 to "15m", 30 to "30m", 60 to "1 hr", 120 to "2 hr", 999 to "End")

                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(options) { (mins, label) ->
                            val isTimerActive = sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()
                            val isSelected = if (mins == 0) !isTimerActive else false

                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) colorScheme.primary else Color.Transparent)
                                    .clickable {
                                        onSleepClick(mins)
                                        onModeChange(PlayerControlMode.None)
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.primary
                                )
                            }
                        }
                    }

                    IconButton(onClick = { onModeChange(PlayerControlMode.None) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            PlayerControlMode.Speed -> {
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}


private enum class PlayerControlMode {
    None, Speed, Sleep
}
