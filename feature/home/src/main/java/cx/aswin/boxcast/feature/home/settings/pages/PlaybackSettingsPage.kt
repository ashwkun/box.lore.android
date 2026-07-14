package cx.aswin.boxcast.feature.home.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.Composable
import cx.aswin.boxcast.feature.home.settings.components.SettingsChoiceRow
import cx.aswin.boxcast.feature.home.settings.components.SettingsDivider
import cx.aswin.boxcast.feature.home.settings.components.SettingsGroup
import cx.aswin.boxcast.feature.home.settings.components.SettingsScaffold
import cx.aswin.boxcast.feature.home.settings.components.SettingsSwitchRow

@Composable
internal fun PlaybackSettingsPage(
    skipBehavior: String,
    onSetSkipBehavior: (String) -> Unit,
    hideCompletedInHome: Boolean,
    onSetHideCompletedInHome: (Boolean) -> Unit,
    hideCompletedInSubs: Boolean,
    onSetHideCompletedInSubs: (Boolean) -> Unit,
    hideCompletedInShowDetails: Boolean,
    onSetHideCompletedInShowDetails: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = "Playback",
        onBack = onBack,
    ) {
        SettingsGroup(
            title = "When skipping an episode",
            footer = "Applies when you skip to the next episode.",
        ) {
            SettingsChoiceRow(
                title = "Skip only",
                supportingText = "Leave the current episode unfinished",
                selected = skipBehavior == "just_skip",
                onClick = { onSetSkipBehavior("just_skip") },
            )
            SettingsDivider()
            SettingsChoiceRow(
                title = "Mark complete and skip",
                supportingText = "Mark the current episode complete first",
                selected = skipBehavior == "mark_completed_skip",
                onClick = { onSetSkipBehavior("mark_completed_skip") },
            )
        }

        SettingsGroup(
            title = "Hide completed episodes from",
            footer = "Completed episodes stay in your library; they are only hidden in these places.",
        ) {
            SettingsSwitchRow(
                title = "Home show episodes",
                supportingText = "When you tap a show on the Home tab",
                checked = hideCompletedInHome,
                onCheckedChange = onSetHideCompletedInHome,
                icon = Icons.Rounded.Home,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = "Subscriptions · Latest",
                supportingText = "The Latest tab under Library → Subscriptions",
                checked = hideCompletedInSubs,
                onCheckedChange = onSetHideCompletedInSubs,
                icon = Icons.Rounded.NewReleases,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = "Podcast pages",
                supportingText = "The full episode list on a show’s page",
                checked = hideCompletedInShowDetails,
                onCheckedChange = onSetHideCompletedInShowDetails,
                icon = Icons.Rounded.Podcasts,
            )
        }
    }
}
