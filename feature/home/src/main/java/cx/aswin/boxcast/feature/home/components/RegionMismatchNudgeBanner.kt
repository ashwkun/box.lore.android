package cx.aswin.boxcast.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cx.aswin.boxcast.core.designsystem.components.RegionNudgeBanner

/**
 * Thin wrapper around the shared RegionNudgeBanner for backward compatibility.
 */
@Composable
fun RegionMismatchNudgeBanner(
    systemRegion: String,
    activeRegion: String,
    onSwitchRegion: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    RegionNudgeBanner(
        systemRegion = systemRegion,
        activeRegion = activeRegion,
        onSwitchRegion = onSwitchRegion,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
