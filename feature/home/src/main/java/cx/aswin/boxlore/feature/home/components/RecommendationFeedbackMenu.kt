package cx.aswin.boxlore.feature.home.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Accessible recommendation feedback actions (long-press / overflow menu).
 */
@Composable
fun RecommendationFeedbackMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onMoreLikeThis: () -> Unit,
    onNotForMe: () -> Unit,
    onHideShow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text("More like this") },
            onClick = {
                onDismiss()
                onMoreLikeThis()
            },
        )
        DropdownMenuItem(
            text = { Text("Not for me") },
            onClick = {
                onDismiss()
                onNotForMe()
            },
        )
        DropdownMenuItem(
            text = { Text("Hide this show") },
            onClick = {
                onDismiss()
                onHideShow()
            },
        )
    }
}
