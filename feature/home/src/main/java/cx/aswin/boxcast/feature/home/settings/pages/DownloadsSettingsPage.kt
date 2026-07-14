package cx.aswin.boxcast.feature.home.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.runtime.Composable
import cx.aswin.boxcast.feature.home.settings.components.SettingsDivider
import cx.aswin.boxcast.feature.home.settings.components.SettingsGroup
import cx.aswin.boxcast.feature.home.settings.components.SettingsNavigationRow
import cx.aswin.boxcast.feature.home.settings.components.SettingsScaffold

@Composable
internal fun DownloadsSettingsPage(
    onSmartDownloadsClick: () -> Unit,
    onAutoDownloadsClick: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = "Downloads",
        onBack = onBack,
    ) {
        SettingsGroup(
            title = "Keep episodes offline",
            footer = "Choose how boxlore grabs episodes for you in the background.",
        ) {
            SettingsNavigationRow(
                title = "Smart downloads",
                supportingText = "Keep a rotating set of episodes offline based on listening",
                icon = Icons.Rounded.AutoAwesome,
                onClick = onSmartDownloadsClick,
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = "Automatic downloads",
                supportingText = "Download new episodes from selected subscriptions",
                icon = Icons.Rounded.CloudDownload,
                onClick = onAutoDownloadsClick,
            )
        }
    }
}
