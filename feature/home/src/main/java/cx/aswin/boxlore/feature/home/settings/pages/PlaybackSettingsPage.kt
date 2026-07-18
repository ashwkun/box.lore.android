package cx.aswin.boxlore.feature.home.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.runtime.Composable
import cx.aswin.boxlore.feature.home.settings.components.SettingsChoiceRow
import cx.aswin.boxlore.feature.home.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.home.settings.components.SettingsDurationSliderIcon
import cx.aswin.boxlore.feature.home.settings.components.SettingsDurationSliderRow
import cx.aswin.boxlore.feature.home.settings.components.SettingsDurationSliderValue
import cx.aswin.boxlore.feature.home.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.home.settings.components.SettingsInfoTip
import cx.aswin.boxlore.feature.home.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.home.settings.components.SettingsSwitchRow

/** Current values shown on [PlaybackSettingsPage]. Also used by [cx.aswin.boxlore.feature.home.settings.SettingsScreen]. */
data class PlaybackUiState(
    val skipBehavior: String,
    val skipBeginningMs: Long,
    val skipEndingMs: Long,
    val seekBackwardMs: Long,
    val seekForwardMs: Long,
    val hideCompletedInHome: Boolean,
    val hideCompletedInSubs: Boolean,
    val hideCompletedInShowDetails: Boolean,
)

/** Callbacks for [PlaybackSettingsPage], grouped to keep the page's parameter count small. */
data class PlaybackActions(
    val onSetSkipBehavior: (String) -> Unit,
    val onSetSkipBeginningMs: (Long) -> Unit,
    val onSetSkipEndingMs: (Long) -> Unit,
    val onSetSeekBackwardMs: (Long) -> Unit,
    val onSetSeekForwardMs: (Long) -> Unit,
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
            title = "Automatic episode trimming",
        ) {
            SettingsDurationSliderRow(
                title = "Skip beginning",
                supportingText = "Jump past intros or opening ads on a fresh start",
                value =
                    SettingsDurationSliderValue(
                        seconds = (state.skipBeginningMs / 1_000L).toInt(),
                        range = 0..300,
                        stepSeconds = 5,
                    ),
                onValueCommitted = { actions.onSetSkipBeginningMs(it * 1_000L) },
                icon = SettingsDurationSliderIcon(Icons.Rounded.FastForward),
            )
            SettingsDivider()
            SettingsDurationSliderRow(
                title = "Skip ending",
                supportingText = "Finish naturally before credits or closing ads",
                value =
                    SettingsDurationSliderValue(
                        seconds = (state.skipEndingMs / 1_000L).toInt(),
                        range = 0..300,
                        stepSeconds = 5,
                    ),
                onValueCommitted = { actions.onSetSkipEndingMs(it * 1_000L) },
                icon = SettingsDurationSliderIcon(Icons.AutoMirrored.Rounded.LastPage),
            )
        }
        SettingsInfoTip(
            text = "Set different beginning and ending skip times for an individual podcast from the ⋮ menu on its podcast info page. Resume positions always take priority.",
        )

        SettingsGroup(
            title = "Seek controls",
            footer = "Used by the player, transcript, notification, headset, and Android Auto controls.",
        ) {
            SettingsDurationSliderRow(
                title = "Seek backward",
                value =
                    SettingsDurationSliderValue(
                        seconds = (state.seekBackwardMs / 1_000L).toInt(),
                        range = 5..120,
                        stepSeconds = 5,
                    ),
                onValueCommitted = { actions.onSetSeekBackwardMs(it * 1_000L) },
                icon = SettingsDurationSliderIcon(Icons.Rounded.Replay),
            )
            SettingsDivider()
            SettingsDurationSliderRow(
                title = "Seek forward",
                value =
                    SettingsDurationSliderValue(
                        seconds = (state.seekForwardMs / 1_000L).toInt(),
                        range = 5..120,
                        stepSeconds = 5,
                    ),
                onValueCommitted = { actions.onSetSeekForwardMs(it * 1_000L) },
                icon =
                    SettingsDurationSliderIcon(
                        image = Icons.Rounded.Replay,
                        mirrored = true,
                    ),
            )
        }

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
