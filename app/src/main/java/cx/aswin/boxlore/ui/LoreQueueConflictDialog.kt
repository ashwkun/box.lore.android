package cx.aswin.boxlore.ui

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.launch

/** Conflict dialog when starting a Lore queue over an existing normal queue. */
@Composable
fun LoreQueueConflictDialog(
    pendingLoreEpisode: Episode,
    onDismiss: () -> Unit,
    onConfirmStart: suspend (Episode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = {
            AnalyticsHelper.trackLoreQueueConflictResult(pendingLoreEpisode.id, "cancelled")
            onDismiss()
        },
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
        },
        title = {
            Text(
                text = "Start a Lore queue?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "This starts a fresh Lore queue and clears your current queue. " +
                    "To keep it, open the episode and use Add to Queue instead.",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    AnalyticsHelper.trackLoreQueueConflictResult(
                        pendingLoreEpisode.id,
                        "start_lore_queue",
                    )
                    scope.launch {
                        try {
                            onConfirmStart(pendingLoreEpisode)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to start Lore queue", e)
                        }
                    }
                },
                shape = CircleShape,
            ) {
                Text("Start Lore queue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    AnalyticsHelper.trackLoreQueueConflictResult(pendingLoreEpisode.id, "cancelled")
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("Keep current queue")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
