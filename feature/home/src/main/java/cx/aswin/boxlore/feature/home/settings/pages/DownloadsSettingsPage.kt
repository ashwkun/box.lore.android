package cx.aswin.boxlore.feature.home.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cx.aswin.boxlore.feature.home.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.home.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.home.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.home.settings.components.SettingsScaffold

/** Stable Compose [testTag] ids for Downloads settings instrumentation. */
object SettingsDownloadsTestTags {
    const val SMART_DOWNLOADS_ROW = "settings_downloads_smart"
    const val AUTO_DOWNLOADS_ROW = "settings_downloads_auto"
}

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
                modifier = Modifier.testTag(SettingsDownloadsTestTags.SMART_DOWNLOADS_ROW),
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = "Automatic downloads",
                supportingText = "Download new episodes from selected subscriptions",
                icon = Icons.Rounded.CloudDownload,
                onClick = onAutoDownloadsClick,
                modifier = Modifier.testTag(SettingsDownloadsTestTags.AUTO_DOWNLOADS_ROW),
            )
        }
    }
}
