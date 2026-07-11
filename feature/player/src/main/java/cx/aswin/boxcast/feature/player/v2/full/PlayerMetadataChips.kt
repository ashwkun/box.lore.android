package cx.aswin.boxcast.feature.player.v2.full

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.feature.player.v2.chrome.playerSheetShape
import cx.aswin.boxcast.feature.player.v2.logic.PlayerMetadataFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerMetadataChips(
    publishedDateSeconds: Long,
    durationSeconds: Int,
    currentChapter: Chapter? = null,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    val chipShape = playerSheetShape(12.dp, 12.dp, 12.dp, 12.dp)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (publishedDateSeconds > 0) {
            MetadataChip(
                label = PlayerMetadataFormat.formatRelativeDate(
                    publishedDateSeconds,
                    System.currentTimeMillis() / 1000,
                ),
                leadingIcon = {
            Icon(
                Icons.Rounded.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
                colorScheme = colorScheme,
                shape = chipShape,
            )
        }

        if (durationSeconds > 0) {
            MetadataChip(
                label = PlayerMetadataFormat.formatDurationLabel(durationSeconds),
                leadingIcon = {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
                colorScheme = colorScheme,
                shape = chipShape,
            )
        }

        currentChapter?.title?.takeIf { it.isNotBlank() }?.let { chapterTitle ->
            MetadataChip(
                label = chapterTitle,
                leadingIcon = {
            Icon(
                Icons.Rounded.Subtitles,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
                colorScheme = colorScheme,
                shape = chipShape,
                emphasized = true,
            )
        }
    }
}

@Composable
private fun MetadataChip(
    label: String,
    leadingIcon: @Composable () -> Unit,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    emphasized: Boolean = false,
) {
    val containerColor = if (emphasized) {
        colorScheme.tertiaryContainer
    } else {
        colorScheme.surfaceContainerHigh
    }
    val labelColor = if (emphasized) {
        colorScheme.onTertiaryContainer
    } else {
        colorScheme.onSurfaceVariant
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = leadingIcon,
        shape = shape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            disabledContainerColor = containerColor,
            disabledLabelColor = labelColor,
        ),
        border = null,
    )
}

@Composable
fun MetadataSurfacePill(
    label: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = playerSheetShape(12.dp, 12.dp, 12.dp, 12.dp),
        color = colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

