package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Long-press feedback actions available on Home Taste / Because You Like / mission cards. */
enum class RecommendationFeedbackAction {
    MORE_LIKE_THIS,
    NOT_FOR_ME,
    HIDE_SHOW,
}

/**
 * Accessible long-press feedback menu wired to [cx.aswin.boxlore.core.ranking.RankingFeedbackRepository]
 * via the owning ViewModel. Each row's visible label doubles as its TalkBack accessible name, so no
 * extra `contentDescription` is needed on the leading icons.
 */
@Composable
fun RecommendationFeedbackMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (RecommendationFeedbackAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text("More like this") },
            leadingIcon = { Icon(Icons.Rounded.ThumbUp, contentDescription = null) },
            onClick = {
                onDismiss()
                onAction(RecommendationFeedbackAction.MORE_LIKE_THIS)
            },
        )
        DropdownMenuItem(
            text = { Text("Not for me") },
            leadingIcon = { Icon(Icons.Rounded.ThumbDown, contentDescription = null) },
            onClick = {
                onDismiss()
                onAction(RecommendationFeedbackAction.NOT_FOR_ME)
            },
        )
        DropdownMenuItem(
            text = { Text("Hide this show") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDismiss()
                onAction(RecommendationFeedbackAction.HIDE_SHOW)
            },
        )
    }
}
