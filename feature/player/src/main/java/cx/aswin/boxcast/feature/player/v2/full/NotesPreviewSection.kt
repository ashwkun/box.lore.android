package cx.aswin.boxcast.feature.player.v2.full

import cx.aswin.boxcast.feature.player.v2.logic.NotesPreviewLogic
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun NotesPreviewSection(
    descriptionHtml: String?,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
) {
    val strippedDescription = remember(descriptionHtml) { NotesPreviewLogic.stripHtml(descriptionHtml) }
    if (strippedDescription.isBlank()) return

    var expanded by remember(descriptionHtml) { mutableStateOf(false) }
    val canExpand = NotesPreviewLogic.canExpand(strippedDescription, collapsedMaxLines)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ),
    ) {
        Text(
            text = "Notes",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        Text(
            text = strippedDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
        )

        if (canExpand) {
            Text(
                text = if (expanded) "Show less" else "Read more",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

