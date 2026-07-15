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

/** Current values shown on [PlaybackSettingsPage]. Also used by [cx.aswin.boxcast.feature.home.settings.SettingsScreen]. */
data class PlaybackUiState(
    val skipBehavior: String,
    val hideCompletedInHome: Boolean,
    val hideCompletedInSubs: Boolean,
    val hideCompletedInShowDetails: Boolean,
)

/** Callbacks for [PlaybackSettingsPage], grouped to keep the page's parameter count small. */
data class PlaybackActions(
    val onSetSkipBehavior: (String) -> Unit,
    val onSetHideCompletedInHome: (Boolean) -> Unit,
    val onSetHideCompletedInSubs: (Boolean) -> Unit,
    val onSetHideCompletedInShowDetails: (Boolean) -> Unit,
)

@Composable
internal fun PlaybackSettingsPage(
    state: PlaybackUiState,
    actions: PlaybackActions,
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
                selected = state.skipBehavior == "just_skip",
                onClick = { actions.onSetSkipBehavior("just_skip") },
            )
            SettingsDivider()
            SettingsChoiceRow(
                title = "Mark complete and skip",
                supportingText = "Mark the current episode complete first",
                selected = state.skipBehavior == "mark_completed_skip",
                onClick = { actions.onSetSkipBehavior("mark_completed_skip") },
            )
        }

        SettingsGroup(
            title = "Hide completed episodes from",
            footer = "Completed episodes stay in your library; they are only hidden in these places.",
        ) {
            SettingsSwitchRow(
                title = "Home show episodes",
                supportingText = "When you tap a show on the Home tab",
                checked = state.hideCompletedInHome,
                onCheckedChange = actions.onSetHideCompletedInHome,
                icon = Icons.Rounded.Home,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = "Subscriptions · Latest",
                supportingText = "The Latest tab under Library → Subscriptions",
                checked = state.hideCompletedInSubs,
                onCheckedChange = actions.onSetHideCompletedInSubs,
                icon = Icons.Rounded.NewReleases,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = "Podcast pages",
                supportingText = "The full episode list on a show’s page",
                checked = state.hideCompletedInShowDetails,
                onCheckedChange = actions.onSetHideCompletedInShowDetails,
                icon = Icons.Rounded.Podcasts,
            )
        }
    }
}
