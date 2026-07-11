package cx.aswin.boxcast.surveys.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.posthog.surveys.PostHogDisplaySurveyQuestion
import com.posthog.surveys.PostHogDisplaySurveyTextContentType
import cx.aswin.boxcast.surveys.internal.theme.localAppearance

/** Centered question title and optional description for a survey step. */
@Composable
internal fun QuestionHeader(question: PostHogDisplaySurveyQuestion) {
    val appearance = localAppearance()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = question.question,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = appearance.questionTextColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val description = question.questionDescription
        if (!description.isNullOrBlank() &&
            question.questionDescriptionContentType == PostHogDisplaySurveyTextContentType.TEXT
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = appearance.descriptionTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
