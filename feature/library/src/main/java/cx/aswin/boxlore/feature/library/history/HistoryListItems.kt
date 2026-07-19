package cx.aswin.boxlore.feature.library.history

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.library.HistoryFilter
import cx.aswin.boxlore.feature.library.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryStatusFilterSelector(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            HistoryFilter.ALL to stringResource(R.string.history_filter_all),
            HistoryFilter.IN_PROGRESS to stringResource(R.string.history_filter_in_progress),
            HistoryFilter.COMPLETED to stringResource(R.string.history_filter_completed),
        )
    val roundedPressShape = RoundedCornerShape(12.dp)
    val checkedShape = ButtonGroupDefaults.connectedButtonCheckedShape

    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, (filter, label) ->
            ToggleButton(
                checked = selected == filter,
                onCheckedChange = { checked -> if (checked) onSelect(filter) },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 ->
                            ButtonGroupDefaults.connectedLeadingButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                        options.lastIndex ->
                            ButtonGroupDefaults.connectedTrailingButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                        else ->
                            ButtonGroupDefaults.connectedMiddleButtonShapes(
                                pressedShape = roundedPressShape,
                                checkedShape = checkedShape,
                            )
                    },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun DateHeaderRow(
    date: LocalDate,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val dateText =
        when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> date.format(formatter)
        }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "Caret Rotation",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.rotate(rotationAngle),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun HistoryTimelineItem(
    item: ListeningHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onDelete()
                    true
                } else {
                    false
                }
            },
        )
    val progress =
        if (item.durationMs > 0) {
            (item.progressMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val status =
        if (item.isCompleted) {
            stringResource(R.string.history_status_completed)
        } else {
            stringResource(R.string.history_status_in_progress)
        }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = item.episodeTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = "${item.podcastName} · $status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!item.isCompleted && item.durationMs > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            leadingContent = {
                OptimizedImage(
                    url = item.episodeImageUrl ?: item.podcastImageUrl,
                    proxyWidth = 112,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                )
            },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
