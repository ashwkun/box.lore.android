package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.theme.contrastColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressivePlayButton(
    onClick: () -> Unit,
    isPlaying: Boolean,
    isResume: Boolean,
    accentColor: Color,
    isLoading: Boolean = false,
    progress: Float = 0f,
    timeText: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accentColor,
        contentColor = accentColor.contrastColor(),
        shape = CircleShape,
        modifier = modifier
            .widthIn(min = 160.dp)
            .expressiveClickable(enabled = !isLoading, isolate = true, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            PlayButtonProgressStrip(isResume = isResume, progress = progress, accentColor = accentColor)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                if (isLoading) {
                    BoxLoreLoader.CircularWavy(
                        size = 24.dp,
                        color = accentColor.contrastColor(),
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                
                Text(
                    text = getPlayButtonDisplayText(isLoading, isPlaying, isResume, timeText),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }
    }
}

@Composable
private fun PlayButtonProgressStrip(
    isResume: Boolean,
    progress: Float,
    accentColor: Color
) {
    if (isResume && progress > 0f) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = accentColor.contrastColor(alpha = 0.5f),
                trackColor = Color.Transparent,
                drawStopIndicator = {}
            )
        }
    }
}

private fun getPlayButtonDisplayText(
    isLoading: Boolean,
    isPlaying: Boolean,
    isResume: Boolean,
    timeText: String?
): String {
    return when {
        isLoading -> "Loading"
        isPlaying -> "Pause"
        isResume && timeText != null -> "Resume • $timeText"
        isResume -> "Resume"
        else -> "Play"
    }
}

