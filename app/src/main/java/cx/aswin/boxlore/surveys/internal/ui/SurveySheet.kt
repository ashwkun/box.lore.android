package cx.aswin.boxlore.surveys.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.posthog.surveys.PostHogDisplayOpenQuestion
import com.posthog.surveys.PostHogDisplayRatingQuestion
import com.posthog.surveys.PostHogDisplaySurvey
import com.posthog.surveys.PostHogDisplaySurveyQuestion
import com.posthog.surveys.PostHogDisplaySurveyRatingType
import com.posthog.surveys.PostHogNextSurveyQuestion
import com.posthog.surveys.PostHogSurveyResponse
import cx.aswin.boxlore.core.designsystem.components.EngagementBottomSheetScaffold
import cx.aswin.boxlore.surveys.internal.theme.LocalSurveyAppearance
import cx.aswin.boxlore.surveys.internal.theme.localAppearance
import cx.aswin.boxlore.surveys.internal.theme.resolveAppearance
import kotlinx.coroutines.launch

private sealed interface SurveySubmitStep {
    data object Dismiss : SurveySubmitStep

    data object ShowConfirmation : SurveySubmitStep

    data class NextQuestion(val index: Int) : SurveySubmitStep
}

/** Determines the next UI step after a survey answer is submitted. */
private fun surveySubmitStep(
    next: PostHogNextSurveyQuestion?,
    displayThankYouMessage: Boolean,
): SurveySubmitStep =
    when {
        next == null -> SurveySubmitStep.Dismiss
        next.isSurveyCompleted ->
            if (displayThankYouMessage) {
                SurveySubmitStep.ShowConfirmation
            } else {
                SurveySubmitStep.Dismiss
            }
        else -> SurveySubmitStep.NextQuestion(next.questionIndex)
    }

/**
 * Root NPS survey bottom sheet: question flow, rating capture, and optional thank-you screen.
 *
 * @param onFirstRatingSubmitted Fired once when the user submits their first numeric rating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SurveySheet(
    survey: PostHogDisplaySurvey,
    onSurveyShown: () -> Unit,
    onSubmit: (questionIndex: Int, response: PostHogSurveyResponse) -> PostHogNextSurveyQuestion?,
    onClose: () -> Unit,
    onFirstRatingSubmitted: (Int?) -> Unit = {},
) {
    val appearance = survey.appearance.resolveAppearance()
    val coroutineScope = rememberCoroutineScope()
    val hostSaveableRegistry = LocalSaveableStateRegistry.current

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )

    var currentQuestionIndex by rememberSaveable { mutableStateOf(0) }
    var showingConfirmation by rememberSaveable { mutableStateOf(false) }
    var hasReportedFirstRating by rememberSaveable { mutableStateOf(false) }
    val question = survey.questions.getOrNull(currentQuestionIndex)

    LaunchedEffect(survey.id) {
        onSurveyShown()
    }

    if (question == null && !showingConfirmation) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val dismissSheet: () -> Unit = {
        coroutineScope.launch {
            sheetState.hide()
            onClose()
        }
    }

    val onSubmitResponse: (PostHogSurveyResponse) -> Unit = { response ->
        if (!hasReportedFirstRating && response is PostHogSurveyResponse.Rating) {
            hasReportedFirstRating = true
            onFirstRatingSubmitted(response.rating)
        }
        val next = onSubmit(currentQuestionIndex, response)
        when (val step = surveySubmitStep(next, appearance.displayThankYouMessage)) {
            SurveySubmitStep.Dismiss -> dismissSheet()
            SurveySubmitStep.ShowConfirmation -> showingConfirmation = true
            is SurveySubmitStep.NextQuestion -> currentQuestionIndex = step.index
        }
    }

    EngagementBottomSheetScaffold(
        onDismissRequest = onClose,
        sheetState = sheetState,
        onCloseClick = dismissSheet,
    ) {
        CompositionLocalProvider(
            LocalSurveyAppearance provides appearance,
            LocalSaveableStateRegistry provides hostSaveableRegistry,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showingConfirmation || question == null) {
                    ConfirmationScreen(onClose = dismissSheet)
                } else {
                    SurveyQuestionBody(
                        question = question,
                        onSubmit = onSubmitResponse,
                        onClose = dismissSheet,
                    )
                }
            }
        }
    }
}

@Composable
private fun SurveyQuestionBody(
    question: PostHogDisplaySurveyQuestion,
    onSubmit: (PostHogSurveyResponse) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(56.dp),
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    QuestionHeader(question)

    Spacer(modifier = Modifier.height(28.dp))

    when (question) {
        is PostHogDisplayRatingQuestion -> RatingQuestionBody(question, onSubmit, onClose)
        is PostHogDisplayOpenQuestion -> OpenTextQuestionBody(question, onSubmit)
        else ->
            UnsupportedQuestionPlaceholder(
                buttonLabel = question.buttonText ?: "Close",
                onClose = onClose,
            )
    }
}

@Composable
private fun RatingQuestionBody(
    question: PostHogDisplayRatingQuestion,
    onSubmit: (PostHogSurveyResponse) -> Unit,
    onClose: () -> Unit,
) {
    var rating by rememberSaveable(question.id) { mutableStateOf<Int?>(null) }
    val canSubmit = question.isOptional || rating != null

    if (question.ratingType == PostHogDisplaySurveyRatingType.EMOJI) {
        UnsupportedQuestionPlaceholder(
            buttonLabel = question.buttonText ?: localAppearance().submitButtonText,
            onClose = { onSubmit(PostHogSurveyResponse.Rating(rating)) },
        )
    } else {
        NumberRating(
            question = question,
            selectedValue = rating,
            onSelect = { rating = it },
        )
        Spacer(modifier = Modifier.height(28.dp))
        BottomSection(
            label = question.buttonText ?: localAppearance().submitButtonText,
            enabled = canSubmit,
            onClick = { onSubmit(PostHogSurveyResponse.Rating(rating)) },
        )
    }
}

@Composable
private fun OpenTextQuestionBody(
    question: PostHogDisplayOpenQuestion,
    onSubmit: (PostHogSurveyResponse) -> Unit,
) {
    var text by rememberSaveable(question.id) { mutableStateOf("") }
    val canSubmit = question.isOptional || text.isNotBlank()

    OpenText(value = text, onValueChange = { text = it })
    Spacer(modifier = Modifier.height(28.dp))
    BottomSection(
        label = question.buttonText ?: localAppearance().submitButtonText,
        enabled = canSubmit,
        onClick = {
            val trimmed = text.trim()
            onSubmit(PostHogSurveyResponse.Text(if (trimmed.isEmpty()) null else trimmed))
        },
    )
}

@Composable
private fun UnsupportedQuestionPlaceholder(
    buttonLabel: String,
    onClose: () -> Unit,
) {
    val appearance = localAppearance()
    androidx.compose.material3.Text(
        text =
            "This question type is not yet supported in the app survey UI. " +
                "Please update the app to respond to this survey.",
        style = MaterialTheme.typography.bodyMedium,
        color = appearance.textColor,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(28.dp))
    BottomSection(label = buttonLabel, enabled = true, onClick = onClose)
}
