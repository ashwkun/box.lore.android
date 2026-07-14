package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Single-select connected button group for content region, shared by explore
 * and settings. Equal-width [ToggleButton]s fill the row using connected shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RegionSegmentedSelector(
    activeRegion: String,
    onSwitchRegion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Middle chips default to a flatter press morph; use a rounded press shape
    // so every region (including India / UK) animates the same way on click.
    val roundedPressShape = RoundedCornerShape(12.dp)
    val checkedShape = ButtonGroupDefaults.connectedButtonCheckedShape

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        REGIONS.forEachIndexed { index, (code, label) ->
            val selected = activeRegion.matchesRegion(code)
            ToggleButton(
                checked = selected,
                onCheckedChange = { checked ->
                    if (checked) {
                        onSwitchRegion(code)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(
                        pressedShape = roundedPressShape,
                        checkedShape = checkedShape,
                    )
                    REGIONS.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(
                        pressedShape = roundedPressShape,
                        checkedShape = checkedShape,
                    )
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes(
                        pressedShape = roundedPressShape,
                        checkedShape = checkedShape,
                    )
                },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val REGIONS = listOf(
    "us" to "USA",
    "in" to "India",
    "gb" to "UK",
    "fr" to "France",
)

/** Display label for a stored region code (e.g. charts header chip). */
fun regionDisplayLabel(code: String): String {
    val normalized = code.trim().lowercase()
    return when {
        normalized == "in" || normalized == "ind" -> "India"
        normalized == "gb" || normalized == "uk" -> "UK"
        normalized == "fr" -> "France"
        else -> "USA"
    }
}

private fun String.matchesRegion(region: String): Boolean {
    val normalized = lowercase()
    return when (region) {
        "gb" -> normalized == "gb" || normalized == "uk"
        "in" -> normalized == "in" || normalized == "ind"
        else -> normalized == region
    }
}
