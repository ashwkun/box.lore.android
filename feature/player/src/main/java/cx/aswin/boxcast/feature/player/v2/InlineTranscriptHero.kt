package cx.aswin.boxcast.feature.player.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.data.TranscriptSegment
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.feature.player.TranscriptView
import kotlinx.coroutines.flow.Flow

data class InlineTranscriptContent(
    val transcript: List<TranscriptSegment>,
    val positionFlow: Flow<Long>,
    val transcriptUrl: String?,
    val artworkUrl: String?
)

data class InlineTranscriptActions(
    val onSyncEnabledChange: (Boolean) -> Unit,
    val onSeek: (Long) -> Unit,
    val onShowArtwork: () -> Unit,
    val onFullscreen: () -> Unit
)

@Composable
fun InlineTranscriptHero(
    content: InlineTranscriptContent,
    colorScheme: ColorScheme,
    isSyncEnabled: Boolean,
    actions: InlineTranscriptActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = actions.onShowArtwork,
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerHigh,
                contentColor = colorScheme.onSurface
            ) {
                OptimizedImage(
                    url = content.artworkUrl,
                    proxyWidth = 160,
                    contentDescription = "Show artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = "Transcript",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(onClick = actions.onFullscreen) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = "Open fullscreen transcript"
                )
            }
        }

        TranscriptView(
            transcript = content.transcript,
            positionFlow = content.positionFlow,
            colorScheme = colorScheme,
            onSeek = actions.onSeek,
            isSyncEnabled = isSyncEnabled,
            onSyncEnabledChange = actions.onSyncEnabledChange,
            transcriptUrl = content.transcriptUrl,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
