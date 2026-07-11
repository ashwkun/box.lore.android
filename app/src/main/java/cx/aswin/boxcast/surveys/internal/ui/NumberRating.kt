package cx.aswin.boxcast.surveys.internal.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.posthog.surveys.PostHogDisplayRatingQuestion
import cx.aswin.boxcast.surveys.internal.theme.ResolvedSurveyAppearance
import cx.aswin.boxcast.surveys.internal.theme.localAppearance

/** Connected 0–10 (or custom-range) rating bar with scale endpoint labels. */
@Composable
internal fun NumberRating(
    question: PostHogDisplayRatingQuestion,
    selectedValue: Int?,
    onSelect: (Int?) -> Unit,
) {
    val appearance = localAppearance()
    val values = (question.scaleLowerBound..question.scaleUpperBound).toList()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(appearance.ratingButtonColor)
                    .border(1.dp, appearance.borderColor, RoundedCornerShape(16.dp)),
        ) {
            values.forEachIndexed { index, value ->
                RatingSegment(
                    value = value,
                    isSelected = selectedValue == value,
                    appearance = appearance,
                    drawBorderRight = index < values.lastIndex,
                    onSelect = onSelect,
                )
            }
        }

        RatingScaleLabels(
            lower = question.lowerBoundLabel,
            upper = question.upperBoundLabel,
            appearance = appearance,
        )
    }
}

@Composable
private fun RowScope.RatingSegment(
    value: Int,
    isSelected: Boolean,
    appearance: ResolvedSurveyAppearance,
    drawBorderRight: Boolean,
    onSelect: (Int?) -> Unit,
) {
    val targetBg =
        if (isSelected) appearance.ratingButtonActiveColor else Color.Transparent
    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        label = "rating-segment-bg",
    )
    val textColor =
        if (isSelected) {
            appearance.ratingButtonActiveTextColor
        } else {
            appearance.textColor.copy(alpha = 0.55f)
        }

    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(animatedBg)
                .selectable(
                    selected = isSelected,
                    onClick = { onSelect(if (isSelected) null else value) },
                    role = Role.RadioButton,
                )
                .drawBehind {
                    if (drawBorderRight) {
                        val strokeWidthPx = 1.dp.toPx()
                        drawLine(
                            color = appearance.borderColor,
                            start = Offset(size.width - strokeWidthPx / 2f, 8.dp.toPx()),
                            end =
                                Offset(
                                    size.width - strokeWidthPx / 2f,
                                    size.height - 8.dp.toPx(),
                                ),
                            strokeWidth = strokeWidthPx,
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toString(),
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun RatingScaleLabels(
    lower: String,
    upper: String,
    appearance: ResolvedSurveyAppearance,
) {
    if (lower.isBlank() && upper.isBlank()) return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
    ) {
        Text(
            text = lower,
            style = MaterialTheme.typography.bodySmall,
            color = appearance.descriptionTextColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = upper,
            style = MaterialTheme.typography.bodySmall,
            color = appearance.descriptionTextColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
