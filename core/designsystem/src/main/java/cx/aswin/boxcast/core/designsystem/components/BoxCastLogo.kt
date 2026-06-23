package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable BOXCAST logo rendering the vector drawable path data converted from the SVG.
 */
@Composable
fun BoxCastLogo(
    modifier: Modifier = Modifier,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = 24.sp // Kept for backward compatibility
) {
    Icon(
        painter = painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.ic_boxlore_logo),
        contentDescription = "BOXLORE",
        tint = textColor,
        modifier = modifier
            .height(20.dp)
            .aspectRatio(805f / 110f)
    )
}
