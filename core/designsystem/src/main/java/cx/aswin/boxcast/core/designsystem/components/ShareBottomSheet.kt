package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.ShareLinkBuilder
import cx.aswin.boxcast.core.model.ShareTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    id: String,
    type: String, // "podcast" or "episode"
    title: String,
    subtitle: String,
    imageUrl: String? = null,
    onDismissRequest: () -> Unit,
    durationMs: Long = 0L,
    currentPositionMs: Long = 0L,
    showTimestampOption: Boolean = false,
    onShare: (
        id: String,
        type: String,
        timestampMs: Long?,
        target: ShareTarget
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var includeTimestamp by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Share the good stuff",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "A polished boxlore card is ready to send.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!imageUrl.isNullOrBlank()) {
                        OptimizedImage(
                            url = imageUrl,
                            proxyWidth = 160,
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (type == "podcast") "PODCAST" else "EPISODE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (type == "episode" && showTimestampOption && currentPositionMs > 1000L) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeTimestamp,
                            onCheckedChange = { includeTimestamp = it },
                            colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                        )
                        Text(
                            text = "Start at ${formatTime(currentPositionMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShareActionTile(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy",
                    onClick = {
                        val finalLink = ShareLinkBuilder.build(
                            id = id,
                            type = type,
                            timestampMs = currentPositionMs.takeIf {
                                includeTimestamp && showTimestampOption
                            }
                        )
                        val clipboard = context.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("boxlore link", finalLink)
                        )
                        android.widget.Toast.makeText(
                            context,
                            "Link copied",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f)
                )
                ShareActionTile(
                    icon = Icons.Rounded.ChatBubble,
                    label = "Send",
                    onClick = {
                        val tMs = if (includeTimestamp && showTimestampOption) currentPositionMs else null
                        onShare(id, type, tMs, ShareTarget.MESSAGE)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                    emphasized = true
                )
                ShareActionTile(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Story",
                    onClick = {
                        val tMs = if (includeTimestamp && showTimestampOption) currentPositionMs else null
                        onShare(id, type, tMs, ShareTarget.INSTAGRAM_STORY)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Story copies the link so you can add it with Instagram’s Link sticker.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShareActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val containerColor = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (emphasized) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier.expressiveClickable(
            shape = MaterialTheme.shapes.extraLarge,
            onClick = onClick
        ),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.12f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
