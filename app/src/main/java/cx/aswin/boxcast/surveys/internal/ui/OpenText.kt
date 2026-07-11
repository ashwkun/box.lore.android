package cx.aswin.boxcast.surveys.internal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.surveys.internal.theme.localAppearance

/** Multiline free-text answer field styled to match engagement sheets. */
@Composable
internal fun OpenText(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val appearance = localAppearance()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .restoreFocusOnReentry(),
        placeholder = {
            androidx.compose.material3.Text(
                text = appearance.placeholder,
                color = appearance.placeholderTextColor,
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = appearance.inputTextColor),
        shape = RoundedCornerShape(16.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = appearance.inputBackgroundColor,
                unfocusedContainerColor = appearance.inputBackgroundColor,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = appearance.borderColor,
                cursorColor = appearance.inputTextColor,
            ),
    )
}
