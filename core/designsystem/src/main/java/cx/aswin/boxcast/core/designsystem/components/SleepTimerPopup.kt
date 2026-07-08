package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import kotlinx.coroutines.delay

/** A single selectable duration option in the sleep timer popup. 999 means "End of episode". */
data class SleepTimerOption(val label: String, val minutes: Int)

val DefaultSleepTimerOptions = listOf(
    SleepTimerOption("15m", 15),
    SleepTimerOption("30m", 30),
    SleepTimerOption("45m", 45),
    SleepTimerOption("1h", 60),
    SleepTimerOption("2h", 120),
    SleepTimerOption("End of episode", 999)
)

/**
 * Dynamic-island style popup for the late-night sleep timer nudge. Springs in from the
 * top-center with options visible up front (no tap-to-reveal). Auto-hides after ~8s of
 * inactivity, and shows a brief confirmation before dismissing once an option is picked.
 */
@Composable
fun SleepTimerPopup(
    visible: Boolean,
    modifier: Modifier = Modifier,
    options: List<SleepTimerOption> = DefaultSleepTimerOptions,
    autoHideMillis: Long = 8_000L,
    onSelectDuration: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var isConfirming by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (!visible) {
            isConfirming = false
        }
    }

    LaunchedEffect(visible, isConfirming) {
        if (visible && !isConfirming) {
            delay(autoHideMillis)
            onDismiss()
        }
    }

    LaunchedEffect(isConfirming) {
        if (isConfirming) {
            delay(1_800L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) +
            scaleIn(
                initialScale = 0.82f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f),
                animationSpec = ExpressiveMotion.FormalSpring
            ),
        exit = fadeOut(animationSpec = tween(180)) +
            scaleOut(
                targetScale = 0.88f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            ),
        modifier = modifier
    ) {
        val islandColor = Color(0xFF161618)
        val islandBorder = Color.White.copy(alpha = 0.10f)
        val onIsland = Color.White
        val onIslandMuted = Color.White.copy(alpha = 0.62f)

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = islandColor,
            contentColor = onIsland,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, islandBorder),
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            if (isConfirming) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = onIsland,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sleep timer set. Good night!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = onIsland
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bedtime,
                                    contentDescription = null,
                                    tint = onIsland,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Late night? Set a sleep timer",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = onIsland
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .expressiveClickable(shape = CircleShape, onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = onIslandMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { option ->
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                    .expressiveClickable(shape = RoundedCornerShape(14.dp)) {
                                        onSelectDuration(option.minutes)
                                        isConfirming = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = onIsland,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
