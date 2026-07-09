package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import cx.aswin.boxcast.core.model.SleepTimerConstants

/** A single selectable duration option in the sleep timer popup. 999 means "End of episode". */
data class SleepTimerOption(val label: String, val minutes: Int)

enum class SleepTimerPopupDismissReason {
    Manual,
    Timeout,
    Confirmation
}

val DefaultSleepTimerOptions = listOf(
    SleepTimerOption("30m", 30),
    SleepTimerOption("45m", 45),
    SleepTimerOption("1h", 60),
    SleepTimerOption("2h", 120),
    SleepTimerOption("End of episode", SleepTimerConstants.END_OF_EPISODE_MINUTES)
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
    onDismiss: (SleepTimerPopupDismissReason) -> Unit
) {
    var isConfirming by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            isConfirming = false
        }
    }

    LaunchedEffect(visible, isConfirming) {
        if (visible && !isConfirming) {
            delay(autoHideMillis)
            onDismiss(SleepTimerPopupDismissReason.Timeout)
        }
    }

    LaunchedEffect(isConfirming) {
        if (isConfirming) {
            delay(1_800L)
            onDismiss(SleepTimerPopupDismissReason.Confirmation)
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
        SleepTimerSurface(
            isConfirming = isConfirming,
            palette = SleepTimerPopupPalette(),
            durationOptions = options.filterNot { it.isEndOfEpisode },
            endOfEpisodeOption = options.firstOrNull { it.isEndOfEpisode },
            onSelectDuration = { minutes ->
                onSelectDuration(minutes)
                isConfirming = true
            },
            onDismiss = onDismiss
        )
    }
}

private val SleepTimerOption.isEndOfEpisode: Boolean
    get() = minutes == SleepTimerConstants.END_OF_EPISODE_MINUTES

private data class SleepTimerPopupPalette(
    val islandColor: Color = Color(0xFF161618),
    val islandBorder: Color = Color.White.copy(alpha = 0.14f),
    val onIsland: Color = Color.White,
    val onIslandMuted: Color = Color.White.copy(alpha = 0.62f)
)

@Composable
private fun SleepTimerSurface(
    isConfirming: Boolean,
    palette: SleepTimerPopupPalette,
    durationOptions: List<SleepTimerOption>,
    endOfEpisodeOption: SleepTimerOption?,
    onSelectDuration: (Int) -> Unit,
    onDismiss: (SleepTimerPopupDismissReason) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = palette.islandColor,
        contentColor = palette.onIsland,
        shadowElevation = 20.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.islandBorder),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
    ) {
        if (isConfirming) {
            SleepTimerConfirmation(palette)
        } else {
            SleepTimerOptionsContent(
                palette = palette,
                durationOptions = durationOptions,
                endOfEpisodeOption = endOfEpisodeOption,
                onSelectDuration = onSelectDuration,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun SleepTimerConfirmation(palette: SleepTimerPopupPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Bedtime,
            contentDescription = null,
            tint = palette.onIsland,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Sleep timer set. Good night!",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = palette.onIsland
        )
    }
}

@Composable
private fun SleepTimerOptionsContent(
    palette: SleepTimerPopupPalette,
    durationOptions: List<SleepTimerOption>,
    endOfEpisodeOption: SleepTimerOption?,
    onSelectDuration: (Int) -> Unit,
    onDismiss: (SleepTimerPopupDismissReason) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        SleepTimerPopupHeader(palette, onDismiss)
        Spacer(modifier = Modifier.height(16.dp))
        DurationOptionsRow(
            options = durationOptions,
            palette = palette,
            onSelectDuration = onSelectDuration
        )
        EndOfEpisodeOption(
            option = endOfEpisodeOption,
            palette = palette,
            onSelectDuration = onSelectDuration
        )
    }
}

@Composable
private fun SleepTimerPopupHeader(
    palette: SleepTimerPopupPalette,
    onDismiss: (SleepTimerPopupDismissReason) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = palette.onIsland,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Late night listening?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onIsland
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Set a sleep timer so episodes don't keep playing overnight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onIslandMuted
                )
            }
        }

        Box(
            modifier = Modifier
                .size(30.dp)
                .expressiveClickable(shape = CircleShape) {
                    onDismiss(SleepTimerPopupDismissReason.Manual)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = palette.onIslandMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DurationOptionsRow(
    options: List<SleepTimerOption>,
    palette: SleepTimerPopupPalette,
    onSelectDuration: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SleepTimerOptionChip(
                label = option.label,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.labelMedium,
                palette = palette,
                onClick = { onSelectDuration(option.minutes) }
            )
        }
    }
}

@Composable
private fun EndOfEpisodeOption(
    option: SleepTimerOption?,
    palette: SleepTimerPopupPalette,
    onSelectDuration: (Int) -> Unit
) {
    if (option == null) return

    Spacer(modifier = Modifier.height(10.dp))
    SleepTimerOptionChip(
        label = option.label,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.labelLarge,
        palette = palette,
        onClick = { onSelectDuration(option.minutes) }
    )
}

@Composable
private fun SleepTimerOptionChip(
    label: String,
    modifier: Modifier,
    textStyle: androidx.compose.ui.text.TextStyle,
    palette: SleepTimerPopupPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .expressiveClickable(shape = RoundedCornerShape(16.dp), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = palette.onIsland,
            maxLines = 1
        )
    }
}
