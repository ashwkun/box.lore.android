package cx.aswin.boxcast.surveys.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.posthog.surveys.PostHogDisplaySurveyTextContentType
import cx.aswin.boxcast.surveys.internal.theme.localAppearance

/** Post-submit thank-you screen shown when the survey appearance enables it. */
@Composable
internal fun ConfirmationScreen(onClose: () -> Unit) {
    val appearance = localAppearance()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = appearance.thankYouMessageHeader,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        val description = appearance.thankYouMessageDescription
        if (!description.isNullOrBlank() &&
            appearance.thankYouMessageDescriptionContentType == PostHogDisplaySurveyTextContentType.TEXT
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        BottomSection(
            label = appearance.thankYouMessageCloseButtonText,
            enabled = true,
            onClick = onClose,
        )
    }
}
