package cx.aswin.boxcast.surveys.internal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.surveys.internal.theme.localAppearance

/** Primary submit button for survey and confirmation screens. */
@Composable
internal fun BottomSection(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val appearance = localAppearance()
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = appearance.submitButtonColor,
                contentColor = appearance.submitButtonTextColor,
            ),
    ) {
        Text(text = label)
    }
}
