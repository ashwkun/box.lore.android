package cx.aswin.boxlore.ui.announcement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository.Announcement
import cx.aswin.boxlore.core.analytics.AnalyticsHelper

private val ActionShape = RoundedCornerShape(16.dp)
private val DialogShape = RoundedCornerShape(28.dp)
private val ImageShape = RoundedCornerShape(16.dp)
private val ActionHeight = 52.dp

@Composable
fun InAppAnnouncementDialog(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onAction: (route: String) -> Unit,
) {
    val hasImage = !announcement.imageUrl.isNullOrBlank()
    val hasAction = announcement.showActionInApp && !announcement.route.isNullOrBlank()
    val style = remember(announcement.category) { announcementLayoutStyle(announcement.category) }

    LaunchedEffect(announcement.timestamp, announcement.title) {
        AnalyticsHelper.trackInAppAnnouncementViewed(
            category = announcement.category,
            hasImage = hasImage,
            hasAction = hasAction,
        )
    }

    val dismissExplicitly = {
        AnalyticsHelper.trackInAppAnnouncementDismissed(
            category = announcement.category,
            hasImage = hasImage,
            hasAction = hasAction,
        )
        onDismiss()
    }

    Dialog(
        onDismissRequest = { /* outside / back do not dismiss */ },
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        AnnouncementDialogCard(
            announcement = announcement,
            style = style,
            hasImage = hasImage,
            hasAction = hasAction,
            onDismissExplicitly = dismissExplicitly,
            onAction = onAction,
        )
    }
}

@Composable
private fun AnnouncementDialogCard(
    announcement: Announcement,
    style: AnnouncementLayoutStyle,
    hasImage: Boolean,
    hasAction: Boolean,
    onDismissExplicitly: () -> Unit,
    onAction: (route: String) -> Unit,
) {
    val surfaceBorder =
        if (style.useErrorChip) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
        } else {
            null
        }

    Surface(
        shape = DialogShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        border = surfaceBorder,
        modifier =
            Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 560.dp)
                .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Box {
            IconButton(
                onClick = onDismissExplicitly,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(modifier = Modifier.padding(20.dp)) {
                AnnouncementCategoryChip(
                    category = announcement.category,
                    style = style,
                )
                AnnouncementScrollBody(
                    announcement = announcement,
                    style = style,
                    hasImage = hasImage,
                )
                Spacer(modifier = Modifier.height(20.dp))
                AnnouncementActions(
                    announcement = announcement,
                    style = style,
                    hasImage = hasImage,
                    hasAction = hasAction,
                    onDismissExplicitly = onDismissExplicitly,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun AnnouncementCategoryChip(
    category: String,
    style: AnnouncementLayoutStyle,
) {
    val chipContainer = chipContainerColor(style)
    val chipContent = chipContentColor(style)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = 36.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = category,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = chipContainer,
                    labelColor = chipContent,
                    leadingIconContentColor = chipContent,
                ),
            border = null,
        )
    }
}

@Composable
private fun chipContainerColor(style: AnnouncementLayoutStyle): Color =
    when {
        style.useErrorChip -> MaterialTheme.colorScheme.errorContainer
        style.useTertiaryChip -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

@Composable
private fun chipContentColor(style: AnnouncementLayoutStyle): Color =
    when {
        style.useErrorChip -> MaterialTheme.colorScheme.onErrorContainer
        style.useTertiaryChip -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

@Composable
private fun ColumnScope.AnnouncementScrollBody(
    announcement: Announcement,
    style: AnnouncementLayoutStyle,
    hasImage: Boolean,
) {
    Column(
        modifier =
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
    ) {
        if (hasImage) {
            AsyncImage(
                model = announcement.imageUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(style.imageHeight)
                        .clip(ImageShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(if (style.emphasizeImage) 12.dp else 16.dp))
        }

        Text(
            text = announcement.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight =
                if (style.layout == AnnouncementLayout.Tip) {
                    FontWeight.Medium
                } else {
                    FontWeight.SemiBold
                },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        AnnouncementBodyBlocks(
            body = announcement.body,
            style = style,
        )
    }
}

@Composable
private fun AnnouncementBodyBlocks(
    body: String,
    style: AnnouncementLayoutStyle,
) {
    val blocks = remember(body) { parseBodyToBlocks(body) }
    val bodySpacing = if (style.emphasizeBullets) 8.dp else 10.dp
    val bulletColor =
        if (style.useErrorChip) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    val bulletTextStyle =
        if (style.emphasizeBullets) {
            MaterialTheme.typography.bodyLarge
        } else {
            MaterialTheme.typography.bodyMedium
        }

    Column(verticalArrangement = Arrangement.spacedBy(bodySpacing)) {
        blocks.forEach { block ->
            AnnouncementBodyBlockRow(
                block = block,
                bulletColor = bulletColor,
                bulletTextStyle = bulletTextStyle,
            )
        }
    }
}

@Composable
private fun AnnouncementBodyBlockRow(
    block: BodyBlock,
    bulletColor: Color,
    bulletTextStyle: androidx.compose.ui.text.TextStyle,
) {
    when {
        block.isSpacer -> Spacer(modifier = Modifier.height(6.dp))
        block.isBullet -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = bulletColor,
                    modifier = Modifier.padding(start = 4.dp, end = 10.dp),
                )
                Text(
                    text = block.text,
                    style = bulletTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        else -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AnnouncementActions(
    announcement: Announcement,
    style: AnnouncementLayoutStyle,
    hasImage: Boolean,
    hasAction: Boolean,
    onDismissExplicitly: () -> Unit,
    onAction: (route: String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasAction) {
            val route = announcement.route.orEmpty()
            val buttonText = announcement.actionLabel?.takeIf { it.isNotBlank() } ?: "View"
            val containerColor =
                if (style.useErrorChip) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            Button(
                onClick = {
                    AnalyticsHelper.trackInAppAnnouncementAction(
                        category = announcement.category,
                        hasImage = hasImage,
                        actionLabel = buttonText,
                    )
                    onAction(route)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ActionHeight),
                shape = ActionShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                    ),
            ) {
                Text(
                    text = buttonText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        OutlinedButton(
            onClick = onDismissExplicitly,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ActionHeight),
            shape = ActionShape,
        ) {
            Text(
                text = "Dismiss",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
